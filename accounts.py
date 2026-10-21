"""Accounts, roles, published posts, the public campus chat and private chat channels (SQLite).

Roles:
  owner   - the site owner (whoever signs in with Google as OWNER_EMAIL from .env). Can grant/revoke admin
            and co-owner.
  coowner - same powers as the owner (including granting/revoking admin), except only the owner can
            grant or revoke co-owner, and a co-owner can never ban, remove or demote the owner.
  admin - can publish notices, announcements, assignments and study material, moderate chat,
          create private chat channels and choose their members, and make someone a journalist.
  journalist - a student who can also post text updates in College Updates and Hostel Updates.
  user  - can read everything and use the chat, but cannot publish or change content.
"""
import hashlib
import json
import os
import re
import secrets
import sqlite3
import time

import db as database
import uuid
from datetime import datetime, timedelta, timezone
from functools import wraps
from pathlib import Path

from flask import (Blueprint, Response, abort, g, jsonify, redirect, request, send_from_directory, session,
                   url_for)

BASE_DIR = Path(__file__).parent


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

ROLES = ("owner", "coowner", "admin", "journalist", "user")
RANK = {"owner": 4, "coowner": 3, "admin": 2, "journalist": 1, "user": 0}
POST_KINDS = ("notice", "announcement", "assignment", "material")
SECTIONS = list("ABCDEFGHIJ") + ["Z"]
EMAIL_RE = re.compile(r"^[^@\s]{1,64}@[^@\s]{1,190}\.[A-Za-z]{2,}$")


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


# Table definitions live in db.py (SQLite locally, Postgres when DATABASE_URL is set).


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def owner_email():
    return os.getenv("OWNER_EMAIL", "").strip().lower()


def is_owner_level(u):
    """Owner or co-owner."""
    return u["role"] in ("owner", "coowner")


def outranks(actor, target_role):
    """Whether actor may act against someone with target_role (ban, remove, delete their posts).
    The owner may act against everyone; everyone else only against lower roles."""
    return actor["role"] == "owner" or RANK.get(actor["role"], 0) > RANK.get(target_role, 0)


ROLE_ORDER_SQL = "CASE {col} WHEN 'owner' THEN 0 WHEN 'coowner' THEN 1 WHEN 'admin' THEN 2 WHEN 'journalist' THEN 3 ELSE 4 END"


def sync_owner(conn):
    """The account whose email matches OWNER_EMAIL is always the owner; nobody else is."""
    email = owner_email()
    if not email:
        return
    conn.execute("UPDATE users SET role = 'owner' WHERE email = ? AND role != 'owner'", (email,))
    conn.execute("UPDATE users SET role = 'admin' WHERE role = 'owner' AND email != ?", (email,))
    conn.commit()


def init_app(app):
    """Create folders, database tables and the secret key, and keep the owner role in sync."""
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
        seed = os.getenv("GEMINI_API_KEY", "") + os.getenv("GOOGLE_CLIENT_ID", "") + os.getenv("OWNER_EMAIL", "")
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
    email = owner_email()
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
    print(f"[CampusMate] Google login: {'on' if google_client_id() else 'OFF - add GOOGLE_CLIENT_ID to .env'}")
    print("[CampusMate] ----------------------------------------------")
    app.register_blueprint(bp)
    app.register_error_handler(UploadBlocked, lambda e: (jsonify(error=e.message, blocked=True, warning=e.warning), e.status))


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
                return (jsonify(error="Please log in first."), 401) if api else redirect(url_for("accounts.login_page"))
            if not check(u):
                return (jsonify(error="You do not have permission to do that."), 403) if api else abort(403)
            return fn(*args, **kwargs)
        return wrapper
    return deco


login_required = _guard(lambda u: True)
admin_required = _guard(lambda u: u["role"] in ("admin", "coowner", "owner"))
owner_required = _guard(is_owner_level)  # owner or co-owner
journalist_required = _guard(lambda u: u["role"] in ("journalist", "admin", "coowner", "owner"))

@bp.before_app_request
def csrf_guard():
    """Every state-changing API call must come from our own page (custom header = CSRF protection)."""
    if request.method == "POST" and request.path.startswith("/api/") and request.headers.get("X-CampusMate") != "1":
        return jsonify(error="Request blocked (missing security header). Refresh the page."), 400


# ------------------------------------------------------------------ auth pages & API

@bp.get("/login")
def login_page():
    # sign-in happens on the landing page with the Google button
    return redirect("/")


def google_client_id():
    return os.getenv("GOOGLE_CLIENT_ID", "").strip()


@bp.post("/api/login")
def api_login():
    """Verifies the ID token sent by Google Identity Services on the landing page."""
    data = request.get_json(silent=True) or {}
    token = data.get("credential")
    if not token:
        return jsonify(error="Missing Google credential."), 400
    client_id = google_client_id()
    if not client_id:
        # without an audience check, a token issued to any other Google app would be accepted
        print("[CampusMate] GOOGLE_CLIENT_ID is not set; Google login is disabled.", flush=True)
        return jsonify(error="Google login is not configured on the server (GOOGLE_CLIENT_ID is missing)."), 503

    from google.auth.transport import requests as google_requests
    from google.oauth2 import id_token
    try:
        info = id_token.verify_oauth2_token(token, google_requests.Request(), client_id)
    except ValueError:
        return jsonify(error="Invalid Google token. Please try again."), 401
    except Exception as e:
        print(f"[CampusMate] Could not verify Google token: {e!r}", flush=True)
        return jsonify(error="Could not reach Google to verify the login. Please try again."), 502

    email = (info.get("email") or "").strip().lower()
    if not email or not info.get("email_verified"):
        return jsonify(error="Your Google account email is not verified."), 403
    name = (info.get("name") or email.split("@")[0]).strip()[:40]

    conn = db()
    row = conn.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
    if row:
        uid = row["id"]
        conn.execute("UPDATE users SET last_login_at = ? WHERE id = ?", (now_iso(), uid))
    else:
        cur = conn.execute("INSERT INTO users (email, name, password_hash, role, created_at, last_login_at) VALUES (?,?,?,?,?,?)",
                           (email, name, "", "user", now_iso(), now_iso()))
        uid = cur.lastrowid
    conn.commit()
    sync_owner(conn)

    session.clear()
    session["uid"] = uid
    session.permanent = True
    return jsonify(message="Login successful", name=name, email=email)


@bp.post("/api/logout")
def api_logout():
    session.clear()
    return jsonify(ok=True)


# ------------------------------------------------------------------ owner/co-owner: manage admins

@bp.get("/api/users")
@owner_required
def api_users():
    rows = db().execute("""SELECT id, name, email, role, created_at, last_login_at,
                           CASE WHEN chat_ban_until > ? THEN 1 ELSE 0 END AS chat_banned
                           FROM users ORDER BY """ + ROLE_ORDER_SQL.format(col="role") + ", name",
                        (time.time(),)).fetchall()
    return jsonify(users=[dict(r) for r in rows])


@bp.post("/api/users/<int:uid>/role")
@owner_required
def api_set_role(uid):
    role = str((request.get_json(silent=True) or {}).get("role") or "")
    if role not in ("coowner", "admin", "journalist", "user"):
        return jsonify(error="Role must be coowner, admin, journalist or user."), 400
    row = db().execute("SELECT role FROM users WHERE id = ?", (uid,)).fetchone()
    if not row:
        return jsonify(error="User not found."), 404
    if row["role"] == "owner":
        return jsonify(error="The owner's role cannot be changed."), 400
    if (role == "coowner" or row["role"] == "coowner") and current_user()["role"] != "owner":
        return jsonify(error="Only the owner can give or remove co-owner access."), 403
    db().execute("UPDATE users SET role = ? WHERE id = ?", (role, uid))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/users/<int:uid>/journalist")
@admin_required
def api_set_journalist(uid):
    """Admins and above can make a student a journalist, or turn a journalist back into a student."""
    on = bool((request.get_json(silent=True) or {}).get("journalist"))
    row = db().execute("SELECT role FROM users WHERE id = ?", (uid,)).fetchone()
    if not row:
        return jsonify(error="User not found."), 404
    if row["role"] not in ("user", "journalist"):
        return jsonify(error="Only students can be made journalists."), 400
    db().execute("UPDATE users SET role = ? WHERE id = ?", ("journalist" if on else "user", uid))
    db().commit()
    return jsonify(ok=True)


# ------------------------------------------------------------------ co-owner handover (yearly successor)
# A co-owner picks a successor from signed-in users. The successor accepts on their own account; then the
# successor becomes co-owner and the outgoing co-owner steps down to admin. A co-owner can only hand over
# their own seat. The owner sees every pending handover and can cancel one. Pending handovers are kept in
# settings as `handover:<from user id>` = {"to": <user id>, "created_at": ...}.

HANDOVER_PREFIX = "handover:"


def _pending_handovers():
    rows = db().execute("SELECT key, value FROM settings WHERE key LIKE ?", (HANDOVER_PREFIX + "%",)).fetchall()
    out = []
    for r in rows:
        try:
            from_id = int(r["key"][len(HANDOVER_PREFIX):])
            val = json.loads(r["value"])
            out.append({"from_id": from_id, "to_id": int(val["to"]), "created_at": val.get("created_at")})
        except (ValueError, KeyError, TypeError):
            continue
    return out


def _handover_dict(h, people):
    f, t = people.get(h["from_id"]), people.get(h["to_id"])
    if not f or not t:
        return None
    return {"from": public_user(f), "to": public_user(t), "created_at": h["created_at"]}


def _clear_stale_handovers():
    """Drop handovers whose sender is no longer a co-owner, or whose successor is already owner or co-owner."""
    pending = _pending_handovers()
    if not pending:
        return pending
    roles = {r["id"]: r["role"] for r in db().execute("SELECT id, role FROM users").fetchall()}
    live = []
    for h in pending:
        if roles.get(h["from_id"]) != "coowner" or roles.get(h["to_id"]) in (None, "owner", "coowner"):
            db().execute("DELETE FROM settings WHERE key = ?", (HANDOVER_PREFIX + str(h["from_id"]),))
        else:
            live.append(h)
    db().commit()
    return live


@bp.get("/api/handover")
@login_required
def api_handover():
    """Pending handovers that concern the current user: sent by them, offered to them, and (owner) all."""
    u = current_user()
    pending = _clear_stale_handovers()
    ids = {h["from_id"] for h in pending} | {h["to_id"] for h in pending}
    people = {}
    if ids:
        marks = ",".join("?" * len(ids))
        people = {r["id"]: dict(r) for r in db().execute(
            f"SELECT id, name, email, role FROM users WHERE id IN ({marks})", tuple(ids)).fetchall()}
    as_dict = lambda hs: [d for d in (_handover_dict(h, people) for h in hs) if d]
    return jsonify(
        outgoing=(as_dict([h for h in pending if h["from_id"] == u["id"]]) or [None])[0],
        incoming=as_dict([h for h in pending if h["to_id"] == u["id"]]),
        all=as_dict(pending) if u["role"] == "owner" else [],
    )


@bp.post("/api/handover")
@owner_required
def api_handover_create():
    """A co-owner chooses who takes over their co-owner seat (replaces any earlier choice)."""
    u = current_user()
    if u["role"] != "coowner":
        return jsonify(error="Only a co-owner can hand over their seat. The owner gives co-owner access directly."), 403
    try:
        to_id = int((request.get_json(silent=True) or {}).get("to"))
    except (TypeError, ValueError):
        return jsonify(error="Choose who should take over."), 400
    if to_id == u["id"]:
        return jsonify(error="You can't hand over to yourself."), 400
    row = db().execute("SELECT role FROM users WHERE id = ?", (to_id,)).fetchone()
    if not row:
        return jsonify(error="User not found. They need to sign in to CampusMate once first."), 404
    if row["role"] in ("owner", "coowner"):
        return jsonify(error="That person is already the owner or a co-owner."), 400
    set_setting(HANDOVER_PREFIX + str(u["id"]), {"to": to_id, "created_at": now_iso()})
    return jsonify(ok=True)


@bp.post("/api/handover/<int:from_id>/cancel")
@login_required
def api_handover_cancel(from_id):
    """The co-owner who sent it, the owner, or the chosen successor (declining) can cancel a handover."""
    u = current_user()
    h = next((h for h in _pending_handovers() if h["from_id"] == from_id), None)
    if not h:
        return jsonify(error="No pending handover."), 404
    if u["id"] not in (h["from_id"], h["to_id"]) and u["role"] != "owner":
        return jsonify(error="Only the owner or the people in this handover can cancel it."), 403
    delete_setting(HANDOVER_PREFIX + str(from_id))
    return jsonify(ok=True)


@bp.post("/api/handover/<int:from_id>/accept")
@login_required
def api_handover_accept(from_id):
    """The chosen successor accepts: they become co-owner and the outgoing co-owner becomes admin."""
    u = current_user()
    h = next((h for h in _clear_stale_handovers() if h["from_id"] == from_id), None)
    if not h or h["to_id"] != u["id"]:
        return jsonify(error="This handover is no longer available."), 404
    conn = db()
    # both updates are conditional so a role change in between (e.g. the owner revoked the seat) can't be undone
    cur = conn.execute("UPDATE users SET role = 'admin' WHERE id = ? AND role = 'coowner'", (from_id,))
    if cur.rowcount == 0:
        conn.rollback()
        return jsonify(error="This handover is no longer available."), 404
    conn.execute("UPDATE users SET role = 'coowner' WHERE id = ? AND role NOT IN ('owner', 'coowner')", (u["id"],))
    conn.execute("DELETE FROM settings WHERE key = ?", (HANDOVER_PREFIX + str(from_id),))
    conn.commit()
    _clear_stale_handovers()  # e.g. a second handover offered to the new co-owner
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


def delete_setting(key):
    db().execute("DELETE FROM settings WHERE key = ?", (key,))
    db().commit()


# ------------------------------------------------------------------ private per-user data (e.g. self attendance)

def get_user_data(user_id, key):
    row = db().execute("SELECT value, updated_at FROM user_data WHERE user_id = ? AND key = ?", (user_id, key)).fetchone()
    if not row:
        return None
    return {"value": json.loads(row["value"]), "updated_at": row["updated_at"]}


def set_user_data(user_id, key, value):
    stamp = now_iso()
    db().execute("""INSERT INTO user_data (user_id, key, value, updated_at) VALUES (?,?,?,?)
                    ON CONFLICT(user_id, key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at""",
                 (user_id, key, json.dumps(value, ensure_ascii=False), stamp))
    db().commit()
    return stamp


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
    if kind in ("notice", "announcement") and not body:
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

    pdf_name = save_pdf_upload()
    if pdf_name is False:
        return jsonify(error="Only PDF files can be uploaded."), 400
    if kind == "material" and not pdf_name:
        return jsonify(error="Study material needs a PDF file."), 400

    db().execute("""INSERT INTO posts (kind, title, body, code, due, marks, years, sections, pdf, author_id, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                 (kind, title, body, code, due, marks, json.dumps(years), json.dumps(sections), pdf_name,
                  current_user()["id"], now_iso()))
    db().commit()
    return jsonify(ok=True)


def save_pdf_upload(field="pdf"):
    """Store an uploaded PDF from the request. Returns its file name, None if nothing was uploaded,
    or False if the upload isn't a real PDF. The caller commits."""
    upload = request.files.get(field)
    if not upload or not upload.filename:
        return None
    head = upload.stream.read(5)
    upload.stream.seek(0)
    if not upload.filename.lower().endswith(".pdf") or head != b"%PDF-":
        return False
    data = upload.read()
    check_upload(data, "application/pdf", upload.filename)  # raises UploadBlocked; nothing is stored
    name = f"{uuid.uuid4().hex}.pdf"
    db().execute("INSERT INTO files (name, data, created_at) VALUES (?,?,?)", (name, data, now_iso()))
    return name


# ------------------------------------------------------------------ upload safety check (18+ / vulgar content)
# Every uploaded file is checked by the AI on the server before it is stored, for every role (owner too).
# A blocked file counts against the uploader (user_data `upload_strikes`). The first 3 blocked attempts
# are warnings; every attempt after that is still blocked and admins, co-owners and the owner are alerted
# (settings `upload_alert:<time in ns>`). If the AI check can't run, the upload is blocked without a strike.

scan_upload = None  # set by app.py: (bytes, mime type) -> (flagged, reason); raises if the check fails
FREE_WARNINGS = 3
ALERT_PREFIX = "upload_alert:"
KEEP_ALERTS = 100


class UploadBlocked(Exception):
    def __init__(self, message, status=400, warning=None):
        super().__init__(message)
        self.message, self.status, self.warning = message, status, warning


def check_upload(data, mime_type, filename=""):
    """Raise UploadBlocked unless the AI says the file is safe."""
    try:
        if scan_upload is None:
            raise RuntimeError("NO_SCANNER")
        flagged, reason = scan_upload(data, mime_type)
    except Exception:
        raise UploadBlocked("The safety check for uploads isn't working right now, so the file was not uploaded. "
                            "Please try again in a few minutes.", 503)
    if not flagged:
        return
    u = current_user()
    prev = get_user_data(u["id"], "upload_strikes")
    count = int((prev["value"] if prev else {}).get("count", 0)) + 1
    set_user_data(u["id"], "upload_strikes", {"count": count, "last_at": now_iso()})
    msg = "This file was blocked because it seems to contain 18+ or vulgar content."
    if count <= FREE_WARNINGS:
        msg += f" Warning {count} of {FREE_WARNINGS}."
        if count == FREE_WARNINGS:
            msg += " This is your last warning: if you try again, the admins, co-owners and owner will be told."
        raise UploadBlocked(msg, 400, warning=count)
    _add_upload_alert(u, count, filename, reason)
    raise UploadBlocked(msg + f" You already had {FREE_WARNINGS} warnings, so the admins, co-owners and owner "
                              "have been told.", 400, warning=count)


def _add_upload_alert(u, attempt, filename, reason):
    stamp = now_iso()
    set_setting(f"{ALERT_PREFIX}{time.time_ns():020d}", {
        "user": public_user(u), "attempt": attempt, "file": (filename or "")[:120], "reason": reason, "created_at": stamp})
    keys = [r["key"] for r in db().execute("SELECT key FROM settings WHERE key LIKE ? ORDER BY key DESC",
                                           (ALERT_PREFIX + "%",)).fetchall()]
    for key in keys[KEEP_ALERTS:]:
        db().execute("DELETE FROM settings WHERE key = ?", (key,))
    db().commit()


@bp.get("/api/upload-alerts")
@admin_required
def api_upload_alerts():
    """Blocked uploads by people who already had 3 warnings, newest first; `new` = not yet dismissed by this viewer."""
    seen = get_user_data(current_user()["id"], "upload_alerts_seen")
    seen = seen["value"] if seen else ""
    rows = db().execute("SELECT key, value FROM settings WHERE key LIKE ? ORDER BY key DESC LIMIT 50",
                        (ALERT_PREFIX + "%",)).fetchall()
    alerts = []
    for r in rows:
        try:
            a = json.loads(r["value"])
        except ValueError:
            continue
        a["new"] = r["key"] > seen
        alerts.append(a)
    return jsonify(alerts=alerts)


@bp.post("/api/upload-alerts/seen")
@admin_required
def api_upload_alerts_seen():
    """Dismiss the alerts shown to this viewer (other admins still see them)."""
    latest = db().execute("SELECT MAX(key) AS k FROM settings WHERE key LIKE ?", (ALERT_PREFIX + "%",)).fetchone()
    set_user_data(current_user()["id"], "upload_alerts_seen", (latest["k"] if latest else None) or "")
    return jsonify(ok=True)


def delete_file(name):
    """Remove an uploaded PDF (by its stored name or its /files/ URL). Bundled static PDFs are left alone."""
    name = (name or "").removeprefix("/files/")
    if not re.fullmatch(r"[0-9a-f]{32}\.pdf", name):
        return
    db().execute("DELETE FROM files WHERE name = ?", (name,))
    db().commit()
    (UPLOADS / name).unlink(missing_ok=True)  # older versions stored files on disk


def _post_guard(pid, verb):
    """The post row if the current admin may change it, else an error response."""
    row = db().execute("SELECT * FROM posts WHERE id = ?", (pid,)).fetchone()
    if not row:
        return None, (jsonify(error="Post not found."), 404)
    u = current_user()
    author = db().execute("SELECT id, role FROM users WHERE id = ?", (row["author_id"],)).fetchone()
    if author and author["id"] != u["id"] and author["role"] in ("owner", "coowner") and not outranks(u, author["role"]):
        return None, (jsonify(error=f"Only the owner can {verb} posts by the owner or a co-owner."), 403)
    return row, None


@bp.post("/api/posts/<int:pid>/edit")
@admin_required
def api_edit_post(pid):
    """Edit a published post's text fields; a new PDF replaces the old one. Audience is unchanged."""
    row, err = _post_guard(pid, "edit")
    if err:
        return err
    f = request.form
    title = f.get("title", "").strip()[:150]
    body = f.get("body", "").strip()[:8000]
    if len(title) < 3:
        return jsonify(error="Please enter a title (at least 3 characters)."), 400
    if row["kind"] in ("notice", "announcement") and not body:
        return jsonify(error="Please write the notice text."), 400
    code = re.sub(r"[^A-Za-z0-9 &\-]", "", f.get("code", "")).strip()[:40] or None
    due, marks = row["due"], row["marks"]
    if row["kind"] == "assignment":
        due = f.get("due", "").strip() or None
        if due and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", due):
            return jsonify(error="Due date is not valid."), 400
        marks = f.get("marks", "").strip()
        marks = int(marks) if marks.isdigit() and int(marks) <= 1000 else None
    pdf_name = save_pdf_upload()
    if pdf_name is False:
        return jsonify(error="Only PDF files can be uploaded."), 400
    db().execute("UPDATE posts SET title = ?, body = ?, code = ?, due = ?, marks = ?, pdf = ? WHERE id = ?",
                 (title, body, code, due, marks, pdf_name or row["pdf"], pid))
    db().commit()
    if pdf_name and row["pdf"]:
        delete_file(row["pdf"])
    return jsonify(ok=True)


@bp.post("/api/posts/<int:pid>/delete")
@admin_required
def api_delete_post(pid):
    row, err = _post_guard(pid, "delete")
    if err:
        return err
    db().execute("DELETE FROM posts WHERE id = ?", (pid,))
    db().commit()
    delete_file(row["pdf"])
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


# ------------------------------------------------------------------ college & hostel updates (journalists and above)
#
# Short text updates. Everyone signed in can read them. Journalists, admins, co-owners and the owner
# can post; a post's author and admins+ can edit or delete it (only the owner can touch posts by the
# owner or a co-owner, like published posts). Hostel updates carry a year and a hostel.

NEWS_SECTIONS = ("college", "hostel")
NEWS_YEARS = ("all", "1", "2", "3", "4")
NEWS_HOSTELS = ("both", "boys", "girls")


def _can_post_news(u):
    return u["role"] in ("journalist", "admin", "coowner", "owner")


def _can_edit_news(u, row):
    if row["author_id"] == u["id"]:
        return True
    if not _is_admin(u):
        return False
    return row["author_role"] not in ("owner", "coowner") or outranks(u, row["author_role"])


def _news_row(nid):
    return db().execute("""SELECT n.*, u.name AS author_name, u.role AS author_role FROM news n
                           JOIN users u ON u.id = n.author_id WHERE n.id = ?""", (nid,)).fetchone()


def news_to_dict(r, u):
    return {"id": r["id"], "section": r["section"], "title": r["title"], "body": r["body"],
            "year": r["year"], "hostel": r["hostel"], "created_at": r["created_at"], "updated_at": r["updated_at"],
            "author_id": r["author_id"], "author_name": r["author_name"], "author_role": r["author_role"],
            "can_edit": _can_edit_news(u, r)}


def _news_fields(data, section):
    """Validated (title, body, year, hostel) from a request, or an error string."""
    title = re.sub(r"\s+", " ", str(data.get("title") or "")).strip()[:120]
    body = str(data.get("body") or "").strip()[:3000]
    if len(body) < 3:
        return "Please write the update (at least 3 characters)."
    if section != "hostel":
        return title, body, "all", "both"
    year = str(data.get("year") or "all")
    hostel = str(data.get("hostel") or "both")
    if year not in NEWS_YEARS:
        return "Choose a valid year."
    if hostel not in NEWS_HOSTELS:
        return "Choose boys, girls or both hostels."
    return title, body, year, hostel


@bp.get("/api/updates/<section>")
@login_required
def api_news(section):
    """List updates, newest first. Hostel updates can be filtered with ?year=1..4 and ?hostel=boys|girls;
    an update for all years or both hostels matches every filter."""
    if section not in NEWS_SECTIONS:
        abort(404)
    sql = """SELECT n.*, u.name AS author_name, u.role AS author_role FROM news n
             JOIN users u ON u.id = n.author_id WHERE n.section = ?"""
    args = [section]
    year, hostel = request.args.get("year", ""), request.args.get("hostel", "")
    if section == "hostel" and year in NEWS_YEARS[1:]:
        sql += " AND n.year IN ('all', ?)"
        args.append(year)
    if section == "hostel" and hostel in NEWS_HOSTELS[1:]:
        sql += " AND n.hostel IN ('both', ?)"
        args.append(hostel)
    rows = db().execute(sql + " ORDER BY n.created_at DESC, n.id DESC LIMIT 200", args).fetchall()
    u = current_user()
    return jsonify(updates=[news_to_dict(r, u) for r in rows], can_post=_can_post_news(u))


@bp.post("/api/updates/<section>")
@journalist_required
def api_news_create(section):
    if section not in NEWS_SECTIONS:
        abort(404)
    fields = _news_fields(request.get_json(silent=True) or {}, section)
    if isinstance(fields, str):
        return jsonify(error=fields), 400
    title, body, year, hostel = fields
    cur = db().execute("""INSERT INTO news (section, title, body, year, hostel, author_id, created_at)
                          VALUES (?,?,?,?,?,?,?)""", (section, title, body, year, hostel, current_user()["id"], now_iso()))
    db().commit()
    return jsonify(ok=True, id=cur.lastrowid)


@bp.post("/api/updates/item/<int:nid>/edit")
@journalist_required
def api_news_edit(nid):
    row = _news_row(nid)
    if not row:
        return jsonify(error="Update not found."), 404
    if not _can_edit_news(current_user(), row):
        return jsonify(error="You can only edit your own updates."), 403
    fields = _news_fields(request.get_json(silent=True) or {}, row["section"])
    if isinstance(fields, str):
        return jsonify(error=fields), 400
    title, body, year, hostel = fields
    db().execute("UPDATE news SET title = ?, body = ?, year = ?, hostel = ?, updated_at = ? WHERE id = ?",
                 (title, body, year, hostel, now_iso(), nid))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/updates/item/<int:nid>/delete")
@journalist_required
def api_news_delete(nid):
    row = _news_row(nid)
    if not row:
        return jsonify(error="Update not found."), 404
    if not _can_edit_news(current_user(), row):
        return jsonify(error="You can only delete your own updates."), 403
    db().execute("DELETE FROM news WHERE id = ?", (nid,))
    db().commit()
    return jsonify(ok=True)


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
                           JOIN users u ON u.id = m.user_id WHERE m.channel_id IS NULL AND m.id > ?
                           ORDER BY m.id DESC LIMIT 100""", (after,)).fetchall()
    latest = db().execute("SELECT COALESCE(MAX(id), 0) FROM messages WHERE channel_id IS NULL").fetchone()[0]
    return jsonify(messages=[msg_to_dict(r) for r in reversed(rows)], latest=latest, ids=_recent_ids(None),
                   banned=ban_status(current_user()["id"]))


@bp.post("/api/chat")
@login_required
def api_chat_send():
    body = re.sub(r"[ \t]+", " ", str((request.get_json(silent=True) or {}).get("body") or "")).strip()
    return _send_message(body, None)


def _send_message(body, channel_id):
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
    db().execute("INSERT INTO messages (user_id, body, created_at, channel_id) VALUES (?,?,?,?)",
                 (u["id"], body, now_iso(), channel_id))
    db().commit()
    return jsonify(ok=True)


def can_delete_message(actor, author_id, author_role):
    """Everyone may delete their own messages. Beyond that: the owner may delete anyone's,
    a co-owner anyone's except the owner's, and an admin anyone's except the owner's and co-owners'."""
    if author_id == actor["id"] or actor["role"] == "owner":
        return True
    if actor["role"] == "coowner":
        return author_role != "owner"
    if actor["role"] == "admin":
        return author_role not in ("owner", "coowner")
    return False


def _delete_message(mid, channel_id):
    row = db().execute("SELECT m.user_id, u.role, m.channel_id FROM messages m JOIN users u ON u.id = m.user_id WHERE m.id = ?",
                       (mid,)).fetchone()
    if row and row["channel_id"] != channel_id:  # Python compare: Postgres has no "IS ?" for parameters
        row = None
    if not row:
        return jsonify(error="Message not found."), 404
    if not can_delete_message(current_user(), row["user_id"], row["role"]):
        return jsonify(error="You can't delete this message."), 403
    db().execute("DELETE FROM messages WHERE id = ?", (mid,))
    db().commit()
    return jsonify(ok=True)


def _recent_ids(channel_id):
    """Ids of the latest messages, so clients can drop messages someone else deleted."""
    if channel_id is None:
        rows = db().execute("SELECT id FROM messages WHERE channel_id IS NULL ORDER BY id DESC LIMIT 200").fetchall()
    else:
        rows = db().execute("SELECT id FROM messages WHERE channel_id = ? ORDER BY id DESC LIMIT 200", (channel_id,)).fetchall()
    return [r[0] for r in rows]


@bp.post("/api/chat/<int:mid>/delete")
@login_required
def api_chat_delete(mid):
    # private-channel messages are removed through their channel route
    return _delete_message(mid, None)


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
    if target["role"] == "coowner" and actor["role"] != "owner":
        return "Only the owner can ban a co-owner."
    if target["role"] == "admin" and not is_owner_level(actor):
        return "Only the owner or a co-owner can ban an admin."
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


# ------------------------------------------------------------------ private chat channels
#
# Admins, co-owners and the owner create private channels and choose who is in them. Only members can
# see a channel, its member list or its messages: everyone else gets a 404, as if it did not
# exist. Admins who are members manage it (add/remove people, remove messages, delete it).

def _is_admin(u):
    return u["role"] in ("admin", "coowner", "owner")


def _channel_for_member(cid):
    """The channel row if the current user is a member, else None (callers answer 404)."""
    return db().execute("""SELECT c.id, c.name, c.created_by, c.created_at FROM channels c
                           JOIN channel_members cm ON cm.channel_id = c.id
                           WHERE c.id = ? AND cm.user_id = ?""", (cid, current_user()["id"])).fetchone()


def _not_found():
    return jsonify(error="Channel not found."), 404


def _channel_members(cid):
    rows = db().execute("""SELECT u.id, u.name, u.email, u.role FROM channel_members cm JOIN users u ON u.id = cm.user_id
                           WHERE cm.channel_id = ? ORDER BY """ + ROLE_ORDER_SQL.format(col="u.role") + ", u.name",
                        (cid,)).fetchall()
    return [dict(r) for r in rows]


def _add_member(cid, uid):
    if not db().execute("SELECT 1 FROM users WHERE id = ?", (uid,)).fetchone():
        return False
    if not db().execute("SELECT 1 FROM channel_members WHERE channel_id = ? AND user_id = ?", (cid, uid)).fetchone():
        db().execute("INSERT INTO channel_members (channel_id, user_id, added_by, added_at) VALUES (?,?,?,?)",
                     (cid, uid, current_user()["name"], now_iso()))
    return True


def _user_ids(values):
    out = []
    for v in values if isinstance(values, list) else []:
        if isinstance(v, int) or (isinstance(v, str) and v.isdigit()):
            out.append(int(v))
    return out


@bp.get("/api/channels")
@login_required
def api_channels():
    rows = db().execute("""SELECT c.id, c.name, c.created_at, u.name AS created_by_name,
                           (SELECT COUNT(*) FROM channel_members x WHERE x.channel_id = c.id) AS member_count,
                           (SELECT COALESCE(MAX(m.id), 0) FROM messages m WHERE m.channel_id = c.id) AS latest
                           FROM channels c JOIN channel_members cm ON cm.channel_id = c.id
                           JOIN users u ON u.id = c.created_by
                           WHERE cm.user_id = ? ORDER BY c.name""", (current_user()["id"],)).fetchall()
    return jsonify(channels=[dict(r) for r in rows], can_create=_is_admin(current_user()))


@bp.post("/api/channels")
@admin_required
def api_channel_create():
    data = request.get_json(silent=True) or {}
    name = re.sub(r"\s+", " ", str(data.get("name") or "")).strip()[:60]
    if len(name) < 2:
        return jsonify(error="Give the channel a name (at least 2 characters)."), 400
    u = current_user()
    cur = db().execute("INSERT INTO channels (name, created_by, created_at) VALUES (?,?,?)", (name, u["id"], now_iso()))
    cid = cur.lastrowid
    _add_member(cid, u["id"])
    for uid in _user_ids(data.get("member_ids")):
        _add_member(cid, uid)
    db().commit()
    return jsonify(ok=True, id=cid)


@bp.get("/api/channels/<int:cid>")
@login_required
def api_channel(cid):
    ch = _channel_for_member(cid)
    if not ch:
        return _not_found()
    return jsonify(channel=dict(ch), members=_channel_members(cid), can_manage=_is_admin(current_user()))


@bp.get("/api/channels/<int:cid>/messages")
@login_required
def api_channel_messages(cid):
    if not _channel_for_member(cid):
        return _not_found()
    after = request.args.get("after", "0")
    after = int(after) if after.isdigit() else 0
    rows = db().execute("""SELECT m.id, m.body, m.created_at, m.user_id, u.name, u.role FROM messages m
                           JOIN users u ON u.id = m.user_id WHERE m.channel_id = ? AND m.id > ?
                           ORDER BY m.id DESC LIMIT 100""", (cid, after)).fetchall()
    latest = db().execute("SELECT COALESCE(MAX(id), 0) FROM messages WHERE channel_id = ?", (cid,)).fetchone()[0]
    return jsonify(messages=[msg_to_dict(r) for r in reversed(rows)], latest=latest, ids=_recent_ids(cid),
                   banned=ban_status(current_user()["id"]))


@bp.post("/api/channels/<int:cid>/messages")
@login_required
def api_channel_send(cid):
    if not _channel_for_member(cid):
        return _not_found()
    body = re.sub(r"[ \t]+", " ", str((request.get_json(silent=True) or {}).get("body") or "")).strip()
    return _send_message(body, cid)


@bp.post("/api/channels/<int:cid>/messages/<int:mid>/delete")
@login_required
def api_channel_message_delete(cid, mid):
    if not _channel_for_member(cid):
        return _not_found()
    return _delete_message(mid, cid)


@bp.post("/api/channels/<int:cid>/members")
@admin_required
def api_channel_add_members(cid):
    if not _channel_for_member(cid):
        return _not_found()
    ids = _user_ids((request.get_json(silent=True) or {}).get("user_ids"))
    if not ids:
        return jsonify(error="Choose who to add."), 400
    added = sum(1 for uid in ids if _add_member(cid, uid))
    db().commit()
    return jsonify(ok=True, added=added)


@bp.post("/api/channels/<int:cid>/members/<int:uid>/remove")
@login_required
def api_channel_remove_member(cid, uid):
    """Admins in the channel can remove anyone; any member can remove themselves (leave)."""
    if not _channel_for_member(cid):
        return _not_found()
    u = current_user()
    if uid != u["id"] and not _is_admin(u):
        return jsonify(error="Only admins can remove members."), 403
    target = db().execute("SELECT role FROM users WHERE id = ?", (uid,)).fetchone()
    if target and uid != u["id"] and target["role"] in ("owner", "coowner") and not outranks(u, target["role"]):
        return jsonify(error="Only the owner can remove the owner or a co-owner."), 403
    db().execute("DELETE FROM channel_members WHERE channel_id = ? AND user_id = ?", (cid, uid))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/channels/<int:cid>/delete")
@admin_required
def api_channel_delete(cid):
    if not _channel_for_member(cid):
        return _not_found()
    db().execute("DELETE FROM messages WHERE channel_id = ?", (cid,))
    db().execute("DELETE FROM channel_members WHERE channel_id = ?", (cid,))
    db().execute("DELETE FROM channels WHERE id = ?", (cid,))
    db().commit()
    return jsonify(ok=True)


@bp.get("/api/people")
@admin_required
def api_people():
    """Everyone with an account, for choosing channel members."""
    rows = db().execute("""SELECT id, name, email, role FROM users
                           ORDER BY """ + ROLE_ORDER_SQL.format(col="role") + ", name").fetchall()
    return jsonify(people=[dict(r) for r in rows])
