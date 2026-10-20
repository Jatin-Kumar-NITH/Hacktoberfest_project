"""WhatsApp group intake: a bot posts section group messages here, Gemma sorts them into notices,
assignments and exam updates, and an admin approves each one before students see it.

How it fits together:
  bot  -> POST /api/bot/whatsapp (Bearer WHATSAPP_BOT_TOKEN)   messages are saved, deduplicated and
                                                               classified; nothing is published yet
  admin -> #whatsapp page: map each group to a year + section, then approve (optionally edited),
           reject or re-run each suggestion. Approving creates a normal post (notice / assignment)
           or changes that year's exam schedule, so the site, the app and its notifications pick it up.

The bot itself (which reads the groups) is separate; this module only needs it to send JSON.
"""
import base64
import binascii
import hmac
import json
import os
import re
import uuid
from datetime import date, datetime, timedelta, timezone

from flask import Blueprint, jsonify, request

import accounts
from accounts import SECTIONS, admin_required, current_user, db, now_iso

bp = Blueprint("intake", __name__)

YEARS = ("1", "2", "3", "4")
KINDS = ("notice", "assignment", "exam", "none")
STATUSES = ("pending", "ignored", "approved", "rejected", "failed")
MAX_BATCH = 10            # messages per request: one Gemma call has to finish inside a serverless request
MAX_TEXT = 6000
MAX_MEDIA = 5 * 1024 * 1024
ISO = "%Y-%m-%dT%H:%M:%SZ"  # same format as accounts.now_iso()
# chatter that is never worth an AI call
SMALL_TALK = re.compile(r"^(ok(ay)?|k+|thanks?( you)?|thx|ty|yes|no|done|noted|sure|hmm+|haha+|lol|👍|🙏|✅|\W+)$", re.I)

_hooks = {}


def init_app(app, ask, campus):
    """ask(contents) -> (json, model) is app.ask_gemma; campus() -> dict is app.load_campus."""
    _hooks.update(ask=ask, campus=campus)
    app.register_blueprint(bp)


# ------------------------------------------------------------------ helpers

def _txt(v, n):
    return re.sub(r"\s+", " ", str(v or "")).strip()[:n]


def _iso_date(v):
    v = str(v or "")
    return v if re.fullmatch(r"\d{4}-\d{2}-\d{2}", v) else None


def _sent_at(v):
    """Bot timestamps arrive as unix seconds or ISO text; store ISO (UTC)."""
    try:
        if isinstance(v, (int, float)) or str(v).isdigit():
            return datetime.fromtimestamp(float(v), timezone.utc).strftime(ISO)
        return datetime.fromisoformat(str(v).replace("Z", "+00:00")).astimezone(timezone.utc).strftime(ISO)
    except (ValueError, OSError, OverflowError):
        return now_iso()


def bot_token():
    return os.getenv("WHATSAPP_BOT_TOKEN", "").strip()


def _group(gid):
    return db().execute("SELECT * FROM wa_groups WHERE id = ?", (gid,)).fetchone()


def group_to_dict(r):
    return {"id": r["id"], "name": r["name"], "year": r["year"], "section": r["section"],
            "enabled": bool(r["enabled"]), "last_seen_at": r["last_seen_at"]}


def item_to_dict(r):
    return {"id": r["id"], "group_id": r["group_id"], "group_name": r["group_name"], "sender": r["sender"],
            "sent_at": r["sent_at"], "text": r["text"], "media": r["media_type"], "pdf": f"/files/{r['pdf']}" if r["pdf"] else None,
            "status": r["status"], "kind": r["kind"], "suggestion": json.loads(r["suggestion"] or "{}"),
            "year": r["year"], "section": r["section"], "error": r["error"],
            "result": r["result"], "reviewed_by": r["reviewed_by"], "reviewed_at": r["reviewed_at"], "created_at": r["created_at"]}


def _item_row(iid):
    return db().execute("""SELECT m.*, g.name AS group_name, g.year, g.section FROM wa_messages m
                           JOIN wa_groups g ON g.id = m.group_id WHERE m.id = ?""", (iid,)).fetchone()


# ------------------------------------------------------------------ AI extraction

EXTRACT_PROMPT = """You read messages from an official WhatsApp group of one class section at NIT Hamirpur
(an Indian engineering college). The group is for {audience}. Today's date is {today}.

For EACH message below, decide if it tells students something they must know or do, and if so what it is:
- "assignment": homework, lab file, report, quiz/tutorial sheet to submit, with a due date if one is given
- "exam": a test, quiz, mid-sem or end-sem exam being announced, rescheduled or cancelled
- "notice": any other official information (class cancelled or moved, extra class, deadline, fee, form, event, holiday)
- "none": chatter, greetings, questions, replies, jokes, memes, forwards unrelated to classes

Write titles and text in clear English even if the message is in Hindi or Hinglish. Never invent facts:
use null for anything the message doesn't say. Resolve words like "tomorrow" or "Monday" to real dates
using the message's sent time.

Reply with ONLY one JSON object, no markdown fences, in exactly this structure:
{{
  "results": [
    {{
      "index": 0,
      "kind": "notice | assignment | exam | none",
      "confidence": 0.0,
      "title": "short title, e.g. MA-111 tutorial sheet 3 due Friday",
      "body": "the useful content in 1-4 plain sentences",
      "code": "subject code like MA-111 if stated, else null",
      "due": "YYYY-MM-DD for an assignment, else null",
      "marks": null,
      "exam": {{"change": "new | reschedule | cancel", "code": "subject code or name", "name": "subject name or null",
               "date": "YYYY-MM-DD or null", "time": "e.g. 10:00 AM - 11:00 AM, or null", "venue": "room or null"}},
      "sections": ["section letters only if the message limits it to some sections, else empty"]
    }}
  ]
}}
"exam" is null unless kind is "exam". Give one result per message, in the same order.

Messages:
{messages}
"""


def _clean_suggestion(r):
    kind = r.get("kind") if r.get("kind") in KINDS else "none"
    try:
        conf = max(0.0, min(1.0, float(r.get("confidence") or 0)))
    except (TypeError, ValueError):
        conf = 0.0
    out = {"kind": kind, "confidence": round(conf, 2), "title": _txt(r.get("title"), 150),
           "body": str(r.get("body") or "").strip()[:4000], "code": _txt(r.get("code"), 40) or None,
           "due": _iso_date(r.get("due")), "marks": r.get("marks") if isinstance(r.get("marks"), int) else None,
           "sections": [s for s in (r.get("sections") or []) if s in SECTIONS], "exam": None}
    ex = r.get("exam")
    if kind == "exam" and isinstance(ex, dict):
        out["exam"] = {"change": ex.get("change") if ex.get("change") in ("new", "reschedule", "cancel") else "new",
                       "code": _txt(ex.get("code"), 60), "name": _txt(ex.get("name"), 160),
                       "date": _iso_date(ex.get("date")), "time": _txt(ex.get("time"), 40), "venue": _txt(ex.get("venue"), 60)}
    return out


def extract(rows):
    """Classify saved message rows with one Gemma call and store the suggestions. Returns how many failed."""
    from google.genai import types

    if not rows:
        return 0
    g = _group(rows[0]["group_id"])
    audience = (f"year {g['year']}" if g and g["year"] else "an unknown year") + \
               (f", section {g['section']}" if g and g["section"] else "")
    parts, lines = [], []
    for i, r in enumerate(rows):
        media = ""
        if r["media_type"] == "image" and r["image"]:
            parts.append(types.Part.from_bytes(data=bytes(r["image"]), mime_type="image/jpeg"))
            media = f" [image #{len(parts)} attached above]"
        elif r["media_type"] == "pdf":
            media = " [a PDF document was attached]"
        lines.append(f"[{i}] sent {r['sent_at']} by {r['sender'] or 'unknown'}{media}:\n{r['text'] or '(no text)'}")
    prompt = EXTRACT_PROMPT.format(audience=audience, today=date.today().strftime("%d %B %Y"),
                                   messages="\n\n".join(lines))
    try:
        raw, _model = _hooks["ask"](parts + [prompt])
        results = {r.get("index"): r for r in raw.get("results", []) if isinstance(r, dict)}
    except Exception as e:
        err = str(e)[:300]
        for r in rows:
            db().execute("UPDATE wa_messages SET status = 'failed', error = ? WHERE id = ?", (err, r["id"]))
        db().commit()
        return len(rows)
    failed = 0
    for i, r in enumerate(rows):
        if i not in results:
            failed += 1
            db().execute("UPDATE wa_messages SET status = 'failed', error = ? WHERE id = ?", ("No AI result for this message.", r["id"]))
            continue
        s = _clean_suggestion(results[i])
        status = "ignored" if s["kind"] == "none" else "pending"
        db().execute("UPDATE wa_messages SET status = ?, kind = ?, suggestion = ?, error = NULL WHERE id = ?",
                     (status, s["kind"], json.dumps(s, ensure_ascii=False), r["id"]))
    db().commit()
    return failed


# ------------------------------------------------------------------ the bot's endpoint

def _decode_media(m):
    """(media_type, image_jpeg_bytes, pdf_bytes) from a message's optional {"media": {"mime", "data"}}."""
    media = m.get("media")
    if not isinstance(media, dict) or not media.get("data"):
        return None, None, None
    try:
        data = base64.b64decode(media["data"], validate=True)
    except (binascii.Error, ValueError, TypeError):
        return None, None, None
    if len(data) > MAX_MEDIA:
        return None, None, None
    mime = str(media.get("mime") or "")
    if mime == "application/pdf" and data[:5] == b"%PDF-":
        return "pdf", None, data
    if mime.startswith("image/"):
        import io
        from PIL import Image, ImageOps
        try:
            img = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
            img.thumbnail((1600, 1600))
            buf = io.BytesIO()
            img.save(buf, "JPEG", quality=85)
            return "image", buf.getvalue(), None
        except Exception:
            return None, None, None
    return None, None, None


@bp.post("/api/bot/whatsapp")
def bot_messages():
    """Body: {"group": {"id", "name"}, "messages": [{"id", "sender", "sent_at", "text", "media"?}]}.
    "media" is optional: {"mime": "image/jpeg" | "application/pdf", "data": base64}. Up to 10 messages."""
    token = bot_token()
    if not token:
        return jsonify(error="WhatsApp intake is off. Set WHATSAPP_BOT_TOKEN on the server."), 503
    given = request.headers.get("Authorization", "").removeprefix("Bearer ").strip()
    if not hmac.compare_digest(given.encode(), token.encode()):
        return jsonify(error="Bad bot token."), 401
    data = request.get_json(silent=True) or {}
    grp = data.get("group") or {}
    gid, gname = _txt(grp.get("id"), 120), _txt(grp.get("name"), 120)
    msgs = data.get("messages") or []
    if not gid or not isinstance(msgs, list):
        return jsonify(error="Send group.id and a messages list."), 400
    if len(msgs) > MAX_BATCH:
        return jsonify(error=f"Send at most {MAX_BATCH} messages per request."), 400

    stamp = now_iso()
    # chatter and rejected messages are kept for a month so admins can undo, then removed
    month_ago = (datetime.now(timezone.utc) - timedelta(days=30)).strftime(ISO)
    db().execute("DELETE FROM wa_messages WHERE status IN ('ignored', 'rejected') AND created_at < ?", (month_ago,))
    g = _group(gid)
    if g:
        db().execute("UPDATE wa_groups SET name = ?, last_seen_at = ? WHERE id = ?", (gname or g["name"], stamp, gid))
    else:
        # new groups start switched off until an admin maps them to a year and section
        db().execute("INSERT INTO wa_groups (id, name, enabled, last_seen_at, created_at) VALUES (?,?,0,?,?)",
                     (gid, gname or gid, stamp, stamp))
    db().commit()
    g = _group(gid)

    saved, dupes, to_extract = 0, 0, []
    for m in msgs:
        if not isinstance(m, dict):
            continue
        mid = _txt(m.get("id"), 200)
        text = str(m.get("text") or "").strip()[:MAX_TEXT]
        if not mid:
            continue
        if db().execute("SELECT 1 FROM wa_messages WHERE group_id = ? AND msg_id = ?", (gid, mid)).fetchone():
            dupes += 1
            continue
        media_type, image, pdf_bytes = _decode_media(m)
        if not text and not media_type:
            continue
        pdf_name = None
        if pdf_bytes:
            pdf_name = f"{uuid.uuid4().hex}.pdf"
            db().execute("INSERT INTO files (name, data, created_at) VALUES (?,?,?)", (pdf_name, pdf_bytes, stamp))
        chatter = not media_type and (len(text) < 12 or SMALL_TALK.match(text))
        status = "ignored" if chatter or not g["enabled"] else "new"
        cur = db().execute("""INSERT INTO wa_messages (group_id, msg_id, sender, sent_at, text, media_type, image, pdf,
                              status, kind, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                           (gid, mid, _txt(m.get("sender"), 80), _sent_at(m.get("sent_at")), text, media_type,
                            image, pdf_name, status, "none" if chatter else None, stamp))
        saved += 1
        if status == "new":
            to_extract.append(cur.lastrowid)
    db().commit()

    failed = 0
    if to_extract:
        marks = ",".join("?" * len(to_extract))
        failed = extract(db().execute(f"SELECT * FROM wa_messages WHERE id IN ({marks}) ORDER BY sent_at, id", to_extract).fetchall())
    return jsonify(ok=True, saved=saved, duplicates=dupes, classified=len(to_extract) - failed, failed=failed,
                   group_enabled=bool(g["enabled"]))


# ------------------------------------------------------------------ admin review

@bp.get("/api/whatsapp/groups")
@admin_required
def api_groups():
    rows = db().execute("SELECT * FROM wa_groups ORDER BY name").fetchall()
    return jsonify(groups=[group_to_dict(r) for r in rows], bot_ready=bool(bot_token()))


@bp.post("/api/whatsapp/groups/<path:gid>")
@admin_required
def api_group_edit(gid):
    if not _group(gid):
        return jsonify(error="Group not found."), 404
    data = request.get_json(silent=True) or {}
    year = str(data.get("year") or "")
    section = str(data.get("section") or "")
    year = year if year in YEARS else None
    section = section if section in SECTIONS else None
    enabled = bool(data.get("enabled")) and bool(year)
    if data.get("enabled") and not year:
        return jsonify(error="Pick the group's year before switching it on."), 400
    db().execute("UPDATE wa_groups SET year = ?, section = ?, enabled = ? WHERE id = ?", (year, section, int(enabled), gid))
    db().commit()
    return jsonify(ok=True, group=group_to_dict(_group(gid)))


@bp.get("/api/whatsapp/items")
@admin_required
def api_items():
    status = request.args.get("status", "pending")
    sql = """SELECT m.id, m.group_id, m.msg_id, m.sender, m.sent_at, m.text, m.media_type, m.pdf, m.status, m.kind,
                    m.suggestion, m.error, m.result, m.reviewed_by, m.reviewed_at, m.created_at,
                    g.name AS group_name, g.year, g.section
             FROM wa_messages m JOIN wa_groups g ON g.id = m.group_id"""
    args = []
    if status in STATUSES:
        sql += " WHERE m.status = ?"
        args.append(status)
    rows = db().execute(sql + " ORDER BY m.sent_at DESC, m.id DESC LIMIT 200", args).fetchall()
    counts = {r["status"]: r["n"] for r in db().execute("SELECT status, COUNT(*) AS n FROM wa_messages GROUP BY status").fetchall()}
    return jsonify(items=[item_to_dict(r) for r in rows], counts=counts)


@bp.post("/api/whatsapp/items/<int:iid>/retry")
@admin_required
def api_item_retry(iid):
    row = db().execute("SELECT * FROM wa_messages WHERE id = ?", (iid,)).fetchone()
    if not row:
        return jsonify(error="Message not found."), 404
    if row["status"] == "approved":
        return jsonify(error="This one is already published."), 400
    failed = extract([row])
    if failed:
        return jsonify(error="The AI couldn't read it: " + (_item_row(iid)["error"] or "")), 502
    return jsonify(ok=True, item=item_to_dict(_item_row(iid)))


@bp.post("/api/whatsapp/items/<int:iid>/reject")
@admin_required
def api_item_reject(iid):
    row = _item_row(iid)
    if not row:
        return jsonify(error="Message not found."), 404
    if row["status"] == "approved":
        return jsonify(error="This one is already published; delete the post instead."), 400
    db().execute("UPDATE wa_messages SET status = 'rejected', reviewed_by = ?, reviewed_at = ? WHERE id = ?",
                 (current_user()["name"], now_iso(), iid))
    db().commit()
    return jsonify(ok=True)


@bp.post("/api/whatsapp/items/<int:iid>/approve")
@admin_required
def api_item_approve(iid):
    """Publish a message. The body may override the AI's suggestion: kind, title, body, code, due, marks,
    year, sections, and for exams exam = {change, code, name, date, time, venue}."""
    row = _item_row(iid)
    if not row:
        return jsonify(error="Message not found."), 404
    if row["status"] == "approved":
        return jsonify(error="This one is already published."), 400
    s = {**json.loads(row["suggestion"] or "{}"), **(request.get_json(silent=True) or {})}
    kind = s.get("kind")
    if kind not in ("notice", "assignment", "exam"):
        return jsonify(error="Choose notice, assignment or exam."), 400
    year = str(s.get("year") or row["year"] or "")
    sections = [x for x in (s.get("sections") or []) if x in SECTIONS] or ([row["section"]] if row["section"] else ["all"])

    if kind == "exam":
        if year not in YEARS:
            return jsonify(error="Choose the year whose exam schedule this changes."), 400
        result = _apply_exam(year, sections, s.get("exam") or {})
        if isinstance(result, tuple):
            return jsonify(error=result[0]), 400
    else:
        title = _txt(s.get("title"), 150)
        if len(title) < 3:
            return jsonify(error="Please enter a title (at least 3 characters)."), 400
        body = str(s.get("body") or "").strip()[:7800]
        body = (body + "\n\n" if body else "") + f"(Shared in the {row['group_name']} WhatsApp group)"
        due = _iso_date(s.get("due")) if kind == "assignment" else None
        marks = s.get("marks") if kind == "assignment" and isinstance(s.get("marks"), int) and 0 <= s["marks"] <= 1000 else None
        code = re.sub(r"[^A-Za-z0-9 &\-]", "", str(s.get("code") or "")).strip()[:40] or None
        years = [int(year)] if year in YEARS else ["all"]
        cur = db().execute("""INSERT INTO posts (kind, title, body, code, due, marks, years, sections, pdf, author_id, created_at)
                              VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                           (kind, title, body, code, due, marks, json.dumps(years), json.dumps(sections), row["pdf"],
                            current_user()["id"], now_iso()))
        result = f"post:{cur.lastrowid}"
    db().execute("""UPDATE wa_messages SET status = 'approved', kind = ?, result = ?, reviewed_by = ?, reviewed_at = ?
                    WHERE id = ?""", (kind, result, current_user()["name"], now_iso(), iid))
    db().commit()
    return jsonify(ok=True, result=result)


def _norm_code(v):
    return re.sub(r"[^A-Z0-9]", "", str(v or "").upper())


def _apply_exam(year, sections, ex):
    """Add, move or cancel an exam in that year's schedule (saved like an admin's edit). Returns a result
    string, or (error,)."""
    change = ex.get("change") if ex.get("change") in ("new", "reschedule", "cancel") else "new"
    code = _txt(ex.get("code"), 60)
    if not code:
        return ("Enter the exam's subject.",)
    d = _iso_date(ex.get("date"))
    if change != "cancel" and not d:
        return ("Choose the exam date.",)
    current = _hooks["campus"]()["exams"]["years"].get(year, {})
    rows = [dict(r) for r in current.get("rows", [])]
    hits = [r for r in rows if _norm_code(r["code"]) == _norm_code(code)
            and ("all" in sections or "all" in r.get("sections", ["all"]) or set(sections) & set(r.get("sections", [])))]
    if change == "cancel":
        if not hits:
            return ("No exam for that subject and section is in the schedule.",)
        rows = [r for r in rows if r not in hits]
        verb = "cancelled"
    elif change == "reschedule" and hits:
        for r in hits:
            r["date"] = d
            for k in ("time", "venue"):
                if _txt(ex.get(k), 60):
                    r[k] = _txt(ex.get(k), 60)
        verb = "moved"
    else:
        rows.append({"date": d, "time": _txt(ex.get("time"), 40), "code": code, "name": _txt(ex.get("name"), 160),
                     "sections": sections, "branches": ["all"], "venue": _txt(ex.get("venue"), 60)})
        verb = "added"
    rows.sort(key=lambda r: (r["date"], r.get("time", "")))
    accounts.set_setting(f"exams:{year}", {"name": current.get("name", ""), "rows": rows})
    return f"exam:{year}:{verb}"
