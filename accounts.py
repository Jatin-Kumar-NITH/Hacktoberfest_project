```python
"""Accounts, roles, published posts and the public campus chat (SQLite).

Roles:
  owner - the site owner (created from OWNER_EMAIL in .env). Can grant/revoke admin.
  admin - can publish notices, announcements, assignments and study material, and moderate chat.
  user  - can read everything and use the chat, but cannot publish or change content.
"""
import hashlib
import json
import os
import re
import secrets
import sqlite3
import time
import uuid
from datetime import datetime, timedelta, timezone
from functools import wraps
from pathlib import Path

from google.oauth2 import id_token
from google.auth.transport import requests as google_requests

import db as database

from flask import (Blueprint, Response, abort, current_app, g, jsonify, redirect, render_template, request,
                   send_from_directory, session, url_for)

BASE_DIR = Path(__file__).parent
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID", "YOUR_GOOGLE_CLIENT_ID_HERE.apps.googleusercontent.com")


def _writable(path):
    try:
        path.mkdir(parents=True, exist_ok=True)
        probe = path / ".write_test"
        probe.write_text("ok")
        probe.unlink()
        return True
    except OSError:
        return False


def runtime_dir():
    """Where the app may write files. Serverless hosts (Vercel) only allow writing to /tmp,
    so we test the preferred folder and fall back to the system temp folder if it is read-only."""
    import tempfile
    custom = os.getenv("CAMPUSMATE_DATA_DIR", "").strip()
    candidates = [Path(custom)] if custom else []
    if not os.getenv("VERCEL"):
        candidates.append(BASE_DIR / "instance")
    candidates.append(Path(tempfile.gettempdir()) / "campusmate")
    for c in candidates:
        if _writable(c):
            return c
    return candidates[-1]


INSTANCE = runtime_dir()          # recomputed in init_app() after .env is loaded
UPLOADS = INSTANCE / "uploads"
DB_PATH = INSTANCE / "campusmate.db"

bp = Blueprint("accounts", __name__)

ROLES = ("owner", "admin", "user")
POST_KINDS = ("notice", "announcement", "assignment", "material")
SECTIONS = list("ABCDEFGHIJ") + ["Z"]


# ------------------------------------------------------------------ database

def db():
    if "db" not in g:
        g.db = database.connect(DB_PATH)
    return g.db


@bp.teardown_app_request
def close_db(_exc):
    conn = g.pop("db", None)
    if conn is not None:
        conn.close()


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def owner_email():
    return os.getenv("OWNER_EMAIL", "").strip().lower()


def sync_owner(conn):
    """The account whose email matches OWNER_EMAIL is always the owner; nobody else is."""
    email = owner_email()
    if not email:
        return
    conn.execute("UPDATE users SET role = 'owner' WHERE email = ? AND role != 'owner'", (email,))
    conn.execute("UPDATE users SET role = 'admin' WHERE role = 'owner' AND email != ?", (email,))
    conn.commit()


def init_app(app):
    """Create folders, database tables, the secret key and the owner account."""
    global INSTANCE, UPLOADS, DB_PATH
    INSTANCE = runtime_dir()
    UPLOADS = INSTANCE / "uploads"
    DB_PATH = INSTANCE / "campusmate.db"
    INSTANCE.mkdir(parents=True, exist_ok=True)
    UPLOADS.mkdir(parents=True, exist_ok=True)

    key = os.getenv("SECRET_KEY", "").strip()
    key_file = INSTANCE / "secret_key"
    if not key and os.getenv("VERCEL"):
        # every serverless instance must share one key, or users get logged out between requests
        seed = os.getenv("GEMINI_API_KEY", "") + os.getenv("OWNER_EMAIL", "")
        key = hashlib.sha256(("campusmate:" + seed).encode()).hexdigest()
        print("[CampusMate] SECRET_KEY not set; derived one from other secrets. Set SECRET_KEY in Vercel for best security.")
    if not key:
        if not key_file.exists():
            key_file.write_text(secrets.token_hex(32))
        key = key_file.read_text().strip()
    app.secret_key = key
    app.config.update(
        SESSION_COOKIE_HTTPONLY=True,
        SESSION_COOKIE_SAMESITE="Lax",
        PERMANENT_SESSION_LIFETIME=timedelta(days=30),   # "stay logged in"
    )

    conn = database.connect(DB_PATH)
    database.create_schema(conn)
    email = os.getenv("OWNER_EMAIL", "").strip().lower()
    name = os.getenv("OWNER_NAME", "").strip() or (email.split("@")[0] if email else "Owner")
    if email:
        row = conn.execute("SELECT id, role FROM users WHERE email = ?", (email,)).fetchone()
        if row is None:
            conn.execute("INSERT INTO users (email, name, password_hash, role, created_at) VALUES (?,?,?,?,?)",
                         (email, name, "", "owner", now_iso()))
            print(f"[CampusMate] Owner account created for {email}")
        elif row[1] != "owner":
            conn.execute("UPDATE users SET role = 'owner' WHERE id = ?", (row[0],))
    conn.commit()
    sync_owner(conn)
    users = conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]
    conn.close()
    print("[CampusMate] ----------------------------------------------")
    where = "Postgres (DATABASE_URL) - permanent" if database.using_postgres() else str(DB_PATH)
    print(f"[CampusMate] Database: {where} ({users} account(s))")
    if os.getenv("VERCEL") and not database.using_postgres():
        print("[CampusMate] WARNING: on Vercel without DATABASE_URL, accounts and data are temporary. Connect a Postgres database (see README).")
    print(f"[CampusMate] Owner email: {email or 'NOT SET - add OWNER_EMAIL to .env'}")
    print("[CampusMate] Authentication: Google OAuth Active")
    print("[CampusMate] ----------------------------------------------")
    app.register_blueprint(bp)


# ------------------------------------------------------------------ helpers

def current_user():
    if "user" not in g:
        g.user = None
        uid = session.get("uid")
        if uid:
            row = db().execute("SELECT id, email, name, role, created_at FROM users WHERE id = ?", (uid,)).fetchone()
            g.user = dict(row) if row else None
            if not row:
                session.clear()
    return g.user


def public_user(u):
    return {"id": u["id"], "name": u["name"], "email": u["email"], "role": u["role"]} if u else None


def _guard(check):
    def deco(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            u = current_user()
            api = request.path.startswith("/api/") or request.path.startswith("/files/")
            if u is None:
                return (jsonify(error="Please log in first."), 401) if api else redirect("/")
            if not check(u):
                return (jsonify(error="You do not have permission to do that."), 403) if api else abort(403)
            return fn(*args, **kwargs)
        return wrapper
    return deco


login_required = _guard(lambda u: True)
admin_required = _guard(lambda u: u["role"] in ("admin", "owner"))
owner_required = _guard(lambda u: u["role"] == "owner")

@bp.before_app_request
def csrf_guard():
    """Every state-changing API call must come from our own page (custom header = CSRF protection)."""
    if request.method == "POST" and request.path.startswith("/api/") and request.headers.get("X-CampusMate") != "1":
        return jsonify(error="Request blocked (missing security header). Refresh the page."), 400


# ------------------------------------------------------------------ auth pages & API

@bp.get("/login")
def login_page():
    # Since we are using index.html as the landing page now
    return redirect("/")


@bp.post("/google_login")
def google_login():
    """Handles the token sent by Google Identity Services on the frontend."""
    data = request.get_json(silent=True) or {}
    token = data.get("credential")
    
    if not token:
        return jsonify(error="Missing Google credential."), 400
        
    try:
        id_info = id_token.verify_oauth2_token(
            token, google_requests.Request(), GOOGLE_CLIENT_ID
        )
        
        email = id_info.get("email").lower()
        name = id_info.get("name")
        
        conn = db()
        row = conn.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
        
        if row:
            uid = row["id"]
            conn.execute("UPDATE users SET last_login_at = ? WHERE id = ?", (now_iso(), uid))
        else:
            # New student/user logging in via Google
            cur = conn.execute(
                "INSERT INTO users (email, name, password_hash, role, created_at) VALUES (?,?,?,?,?)",
                (email, name, "", "user", now_iso())
            )
            uid = cur.lastrowid
            
        conn.commit()
        sync_owner(conn)
        
        # Establish Flask session
        session.clear()
        session["uid"] = uid
        session.permanent = True
        
        return jsonify(message="Login successful", name=name, email=email)
        
    except ValueError:
        return jsonify(error="Invalid Google token."), 401


@bp.post("/api/logout")
def api_logout():
    session.clear()
    return jsonify(ok=True)


@bp.get("/api/me")
@login_required
def api_me():
    return jsonify(user=public_user(current_user()))


# ------------------------------------------------------------------ owner: manage admins

@bp.get("/api/users")
@owner_required
def api_users():
    rows = db().execute("""SELECT id, name, email, role, created_at, last_login_at,
                           CASE WHEN chat_ban_until > ? THEN 1 ELSE 0 END AS chat_banned
                           FROM users ORDER BY CASE role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, name""",
                        (time.time(),)).fetchall()
    return jsonify(users=[dict(r) for r in rows])


@bp.post("/api/users/<int:uid>/role")
@owner_required
def api_set_role(uid):
    role = str((request.get_json(silent=True) or {}).get("role") or "")
    if role not in ("admin", "user"):
        return jsonify(error="Role must be admin or user."), 400
    row = db().execute("SELECT role FROM users WHERE id = ?", (uid,)).fetchone()
    if not row:
        return jsonify(error="User not found."), 404
    if row["role"] == "owner":
        return jsonify(error="The owner's role cannot be changed."), 400
    db().execute("UPDATE users SET role = ? WHERE id = ?", (role, uid))
    db().commit()
    return jsonify(ok=True)


# ------------------------------------------------------------------ site-editable data (timetables, exams, menus)

def get_setting(key):
    row = db().execute("SELECT value, updated_by, updated_at FROM settings WHERE key = ?", (key,)).fetchone()
    if not row:
        return None
    return {"value": json.loads(row["value"]), "updated_by": row["updated_by"], "updated_at": row["updated_at"]}


def set_setting(key, value):
    u = current_user()
    db().execute("""INSERT INTO settings (key, value, updated_by, updated_at) VALUES (?,?,?,?)
                    ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_by=excluded.updated_by,
                    updated_at=excluded.updated_at""", (key, json.dumps(value, ensure_ascii=False), u["name"], now_iso()))
    db().commit()


# ------------------------------------------------------------------ posts (admin publishing)

def post_to_dict(r):
    return {
        "id": f"p{r['id']}", "db_id": r["id"], "kind": r["kind"], "title": r["title"], "body": r["body"],
        "code": r["code"], "due": r["due"], "marks": r["marks"],
        "years": json.loads(r["years"]), "sections": json.loads(r["sections"]),
        "pdf": f"/files/{r['pdf']}" if r["pdf"] else None,
        "published_at": r["created_at"], "published_by": r["author_name"], "author_role": r["author_role"],
        "author_id": r["author_id"],
    }


def all_posts():
    rows = db().execute("""SELECT p.*, u.name AS author_name, u.role AS author_role FROM posts p
                           JOIN users u ON u.id = p.author_id ORDER BY p.created_at DESC""").fetchall()
    return [post_to_dict(r) for r in rows]


@bp.get("/api/posts")
@login_required
def api_posts():
    return jsonify(posts=all_posts())


@bp.post("/api/posts")
@admin_required
def api_create_post():
    f = request.form
    kind = f.get("kind", "")
    if kind not in POST_KINDS:
        return jsonify(error="Choose what you are publishing."), 400
    title = f.get("title", "").strip()[:150]
    body = f.get("body", "").strip()[:8000]
    if len(title) < 3:
        return jsonify(error="Please enter a title (at least 3 characters)."), 400
    if kind in ("notice", "announcement") and len(body) < 5:
        return jsonify(error="Please write the notice text."), 400
    code = re.sub(r"[^A-Za-z0-9 &\-]", "", f.get("code", "")).strip()[:40] or None
    due = f.get("due", "").strip() or None
    if due and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", due):
        return jsonify(error="Due date is not valid."), 400
    marks = f.get("marks", "").strip()
    marks = int(marks) if marks.isdigit() and int(marks) <= 1000 else None
    years = [y for y in f.getlist("years") if y in ("1", "2", "3", "4")]
    years = [int(y) for y in years] if years else ["all"]
    sections = [s for s in f.getlist("sections") if s in SECTIONS] or ["all"]

    pdf_name = None
    upload = request.files.get("pdf")
    if upload and upload.filename:
        head = upload.stream.read(5)
        upload.stream.seek(0)
        if not upload.filename.lower().endswith(".pdf") or head != b"%PDF-":
            return jsonify(error="Only PDF files can be uploaded."), 400
        pdf_name = f"{uuid.uuid4().hex}.pdf"
        db().execute("INSERT INTO files (name, data, created_at) VALUES (?,?,?)", (pdf_name, upload.read(), now_iso()))
    if kind == "material" and not pdf_name:
        return jsonify(error="Study material needs a PDF file."), 400

    db().execute("""INSERT INTO posts (kind, title, body, code, due, marks, years, sections, pdf, author_id, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                 (kind, title, body, code, due, marks, json.dumps(years), json.dumps(sections), pdf_name,
                  current_user()["id"], now_iso()))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/posts/<int:pid>/delete")
@admin_required
def api_delete_post(pid):
    row = db().execute("SELECT author_id, pdf FROM posts WHERE id = ?", (pid,)).fetchone()
    if not row:
        return jsonify(error="Post not found."), 404
    u = current_user()
    author = db().execute("SELECT role FROM users WHERE id = ?", (row["author_id"],)).fetchone()
    if u["role"] != "owner" and author and author["role"] == "owner":
        return jsonify(error="Only the owner can delete the owner's posts."), 403
    db().execute("DELETE FROM posts WHERE id = ?", (pid,))
    db().commit()
    if row["pdf"]:
        db().execute("DELETE FROM files WHERE name = ?", (row["pdf"],))
        db().commit()
        (UPLOADS / row["pdf"]).unlink(missing_ok=True)  # older versions stored files on disk
    return jsonify(ok=True)


@bp.get("/files/<name>")
@login_required
def uploaded_file(name):
    if not re.fullmatch(r"[0-9a-f]{32}\.pdf", name):
        abort(404)
    row = db().execute("SELECT data FROM files WHERE name = ?", (name,)).fetchone()
    if row:
        return Response(bytes(row["data"]), mimetype="application/pdf",
                        headers={"Content-Disposition": f'inline; filename="{name}"', "Cache-Control": "private, max-age=3600"})
    if (UPLOADS / name).exists():  # older versions stored files on disk
        return send_from_directory(UPLOADS, name, mimetype="application/pdf")
    abort(404)


# ------------------------------------------------------------------ public chat

_LAST_MSG = {}


def msg_to_dict(r):
    return {"id": r["id"], "body": r["body"], "created_at": r["created_at"], "user_id": r["user_id"],
            "name": r["name"], "role": r["role"]}


@bp.get("/api/chat")
@login_required
def api_chat():
    after = request.args.get("after", "0")
    after = int(after) if after.isdigit() else 0
    rows = db().execute("""SELECT m.id, m.body, m.created_at, m.user_id, u.name, u.role FROM messages m
                           JOIN users u ON u.id = m.user_id WHERE m.id > ? ORDER BY m.id DESC LIMIT 100""",
                        (after,)).fetchall()
    latest = db().execute("SELECT COALESCE(MAX(id), 0) FROM messages").fetchone()[0]
    return jsonify(messages=[msg_to_dict(r) for r in reversed(rows)], latest=latest,
                   banned=ban_status(current_user()["id"]))


@bp.post("/api/chat")
@login_required
def api_chat_send():
    body = re.sub(r"[ \t]+", " ", str((request.get_json(silent=True) or {}).get("body") or "")).strip()
    if not body:
        return jsonify(error="Type a message first."), 400
    if len(body) > 500:
        return jsonify(error="Messages can be at most 500 characters."), 400
    u = current_user()
    ban = ban_status(u["id"])
    if ban:
        return jsonify(error=ban["message"], banned=ban), 403
    if time.time() - _LAST_MSG.get(u["id"], 0) < 1.5:
        return jsonify(error="You're sending messages too fast. Wait a second."), 429
    _LAST_MSG[u["id"]] = time.time()
    db().execute("INSERT INTO messages (user_id, body, created_at) VALUES (?,?,?)", (u["id"], body, now_iso()))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/chat/<int:mid>/delete")
@admin_required
def api_chat_delete(mid):
    db().execute("DELETE FROM messages WHERE id = ?", (mid,))
    db().commit()
    return jsonify(ok=True)


# ------------------------------------------------------------------ chat bans

PERMANENT = 4102444800  # year 2100 = "permanent"


def ban_status(uid):
    row = db().execute("SELECT chat_ban_until, chat_ban_by FROM users WHERE id = ?", (uid,)).fetchone()
    if not row or not row["chat_ban_until"] or row["chat_ban_until"] < time.time():
        return None
    permanent = row["chat_ban_until"] >= PERMANENT
    until = datetime.fromtimestamp(row["chat_ban_until"], timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return {"until": None if permanent else until, "permanent": permanent, "by": row["chat_ban_by"],
            "message": "You have been banned from the campus chat" + ("." if permanent else " until the time shown.")}


def _can_ban(actor, target):
    if target["role"] == "owner":
        return "The owner cannot be banned."
    if target["role"] == "admin" and actor["role"] != "owner":
        return "Only the owner can ban an admin."
    if target["id"] == actor["id"]:
        return "You cannot ban yourself."
    return None


@bp.post("/api/chat/ban")
@admin_required
def api_chat_ban():
    data = request.get_json(silent=True) or {}
    target = db().execute("SELECT id, name, role FROM users WHERE id = ?", (data.get("user_id"),)).fetchone()
    if not target:
        return jsonify(error="User not found."), 404
    problem = _can_ban(current_user(), target)
    if problem:
        return jsonify(error=problem), 403
    hours = data.get("hours")
    until = PERMANENT if hours in (0, "0", None, "permanent") else time.time() + float(hours) * 3600
    if until != PERMANENT and not 0 < float(hours) <= 24 * 365:
        return jsonify(error="Choose a valid ban duration."), 400
    db().execute("UPDATE users SET chat_ban_until = ?, chat_ban_by = ? WHERE id = ?", (until, current_user()["name"], target["id"]))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/chat/unban")
@admin_required
def api_chat_unban():
    uid = (request.get_json(silent=True) or {}).get("user_id")
    db().execute("UPDATE users SET chat_ban_until = 0, chat_ban_by = NULL WHERE id = ?", (uid,))
    db().commit()
    return jsonify(ok=True)


@bp.get("/api/chat/bans")
@admin_required
def api_chat_bans():
    rows = db().execute("SELECT id, name, email, role, chat_ban_until, chat_ban_by FROM users WHERE chat_ban_until > ? ORDER BY name",
                        (time.time(),)).fetchall()
    return jsonify(bans=[{"id": r["id"], "name": r["name"], "role": r["role"], "by": r["chat_ban_by"],
                          "permanent": r["chat_ban_until"] >= PERMANENT,
                          "until": datetime.fromtimestamp(r["chat_ban_until"], timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
                         for r in rows])

```
