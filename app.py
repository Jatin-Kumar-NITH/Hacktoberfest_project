"""CampusMate AI - your campus life, organized by AI (Gemma 4 via the Gemini API)."""

import io
import json
import os
import re
import time
from datetime import date, datetime, timedelta
from pathlib import Path

from dotenv import load_dotenv
from flask import Flask, jsonify, render_template, request, send_from_directory

import accounts
from accounts import admin_required, current_user, login_required, public_user
from PIL import Image, ImageOps

# .env next to app.py first; also read a .env in the parent folder (values already set win)
load_dotenv(Path(__file__).resolve().parent / ".env")
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

BASE_DIR = Path(__file__).parent
CACHE_DIR = accounts.runtime_dir() / "cache"   # writable location (/tmp on Vercel)
CACHE_DIR.mkdir(parents=True, exist_ok=True)

MODELS = [
    m.strip()
    for m in os.getenv("GEMMA_MODELS", "gemma-4-26b-a4b-it,gemma-4-31b-it").split(",")
    if m.strip()
]

app = Flask(__name__)
app.json.sort_keys = False  # keep mess menu days and meals in order
app.config["MAX_CONTENT_LENGTH"] = 12 * 1024 * 1024  # 12 MB (PDF uploads)
accounts.init_app(app)

_client = None


# ------------------------------------------------------------------ AI core

def get_client():
    """Create the Gemini API client once. The key stays on the server."""
    global _client
    key = os.getenv("GEMINI_API_KEY", "").strip()
    if not key or key == "your-key-here":
        raise RuntimeError("MISSING_KEY")
    if _client is None:
        from google import genai
        _client = genai.Client(api_key=key)
    return _client


def parse_json(raw):
    """Pull the JSON object out of the model reply, even if wrapped in ``` fences."""
    raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", (raw or "").strip())
    start, end = raw.find("{"), raw.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("BAD_JSON")
    return json.loads(raw[start:end + 1])


def ask_gemma(contents):
    """Send contents to each model in turn; return (parsed_json, model_name)."""
    from google.genai import types

    last_error = None
    for model in MODELS:
        try:
            resp = get_client().models.generate_content(
                model=model,
                contents=contents,
                config=types.GenerateContentConfig(temperature=0.2),
            )
            return parse_json(resp.text), model
        except RuntimeError:
            raise
        except Exception as e:
            print(f"[CampusMate] {model} failed: {str(e)[:200]}")
            last_error = e
    raise last_error


def friendly_error(e):
    msg = str(e)
    if "MISSING_KEY" in msg:
        return "No API key found. Add GEMINI_API_KEY=... to the .env file and restart the server."
    if "429" in msg or "RESOURCE_EXHAUSTED" in msg:
        return "The free Gemma quota is used up for the moment. Wait about a minute and try again."
    if "API key" in msg or "401" in msg or "403" in msg or "PERMISSION" in msg:
        return "The API key was rejected. Check GEMINI_API_KEY in your .env file."
    if "BAD_JSON" in msg or isinstance(e, json.JSONDecodeError):
        return "The AI reply could not be read. Please try again."
    if "500" in msg or "503" in msg or "INTERNAL" in msg or "UNAVAILABLE" in msg:
        return "Google's AI service is busy right now. Please try again in a few seconds."
    return "The AI service failed: " + msg[:200]


def _list(v):
    return v if isinstance(v, list) else []


def cache_file(kind, sample_id, language):
    return CACHE_DIR / f"{kind}__{sample_id}__{language.replace(' ', '_')}.json"


# ------------------------------------------------------------------ Notice Explainer

NOTICE_PROMPT = """You are CampusMate AI, helping Indian college and hostel students understand notices.
Today's date is {today}.

Read the notice {source}

Reply with ONLY one JSON object, no markdown fences, no other text, in exactly this structure:
{{
  "title": "short name, e.g. Hostel fee payment notice",
  "issued_by": "office or person who issued it, or null",
  "audience": "who must act on this, e.g. All first-year hostel residents, or null if unclear",
  "summary": "2-3 very simple sentences: what this is and what the student must do",
  "urgency": "high | medium | low",
  "urgency_reason": "one short sentence",
  "deadlines": [{{"date": "as written in the notice", "date_iso": "YYYY-MM-DD or null if not certain", "what": "what must be done or what happens", "source_text": "exact words copied from the notice", "certain": true}}],
  "required_documents": ["documents or items the student must bring or submit"],
  "checklist": [{{"step": "one clear action", "why": "short reason", "priority": "high | medium | low", "deadline": "date or null"}}],
  "key_facts": [{{"label": "e.g. Fee amount, Venue, Time", "value": "...", "source_text": "exact words from the notice"}}],
  "missing_or_unclear": ["important information the notice does NOT give or states ambiguously, e.g. no venue, no time, year not given"],
  "hindi": {hindi_block}
}}

Strict rules:
- Use ONLY information in the notice. Never invent dates, amounts, venues, names or rules.
- If something is missing or ambiguous, leave it out or use null, and explain it in "missing_or_unclear".
- "source_text" must be copied word-for-word so the student can verify it.
- Set "certain": false for any deadline you had to interpret (e.g. "next Monday", year not stated).
- Write in simple English. Order the checklist by what to do first.
"""

HINDI_BLOCK = '{"summary": "the summary in simple Hindi (Devanagari)", "checklist": ["each checklist step in simple Hindi"]}'


def normalize_notice(d):
    out = {
        "title": d.get("title") or "Notice",
        "issued_by": d.get("issued_by"),
        "audience": d.get("audience"),
        "summary": d.get("summary") or "",
        "urgency": d.get("urgency") if d.get("urgency") in ("high", "medium", "low") else "medium",
        "urgency_reason": d.get("urgency_reason") or "",
        "deadlines": [x for x in _list(d.get("deadlines")) if isinstance(x, dict)],
        "required_documents": [str(x) for x in _list(d.get("required_documents"))],
        "checklist": [x for x in _list(d.get("checklist")) if isinstance(x, dict)],
        "key_facts": [x for x in _list(d.get("key_facts")) if isinstance(x, dict)],
        "missing_or_unclear": [str(x) for x in _list(d.get("missing_or_unclear"))],
        "hindi": d.get("hindi") if isinstance(d.get("hindi"), dict) else None,
    }
    for c in out["checklist"]:
        if c.get("priority") not in ("high", "medium", "low"):
            c["priority"] = "medium"
    for dl in out["deadlines"]:
        iso = dl.get("date_iso")
        if not (isinstance(iso, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", iso)):
            dl["date_iso"] = None
    return out


def prepare_image(file_storage):
    img = ImageOps.exif_transpose(Image.open(file_storage.stream)).convert("RGB")
    img.thumbnail((1600, 1600))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=88)
    return buf.getvalue()


@app.post("/api/notice")
@login_required
def explain_notice():
    from google.genai import types

    text = (request.form.get("text") or "").strip()
    if len(text) > 15000:
        return jsonify(error="That notice is very long. Please paste at most about 15,000 characters."), 400
    hindi = request.form.get("hindi") == "1"
    sample_id = request.form.get("sample_id") or ""
    if not re.fullmatch(r"[a-z0-9_-]{1,40}", sample_id):
        sample_id = ""

    image_bytes = None
    upload = request.files.get("file")
    if upload and upload.filename:
        try:
            image_bytes = prepare_image(upload)
        except Exception:
            return jsonify(error="Could not read that image. Please use JPG, PNG or WEBP."), 400

    if not text and not image_bytes:
        return jsonify(error="Paste the notice text or upload a photo of it first."), 400

    if image_bytes and text:
        source = "in the image, plus this text:\n---\n" + text + "\n---"
    elif image_bytes:
        source = "in the image."
    else:
        source = "below:\n---\n" + text + "\n---"
    prompt = NOTICE_PROMPT.format(
        today=date.today().strftime("%d %B %Y"),
        source=source,
        hindi_block=HINDI_BLOCK if hindi else "null",
    )
    contents = ([types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg")] if image_bytes else []) + [prompt]

    lang = "hi" if hindi else "en"
    started = time.time()
    try:
        raw, model = ask_gemma(contents)
        result = normalize_notice(raw)
    except Exception as e:
        cf = cache_file("notice", sample_id, lang) if sample_id else None
        if cf and cf.exists():
            saved = json.loads(cf.read_text(encoding="utf-8"))
            return jsonify(result=saved["result"], model=saved["model"], cached=True,
                           cached_at=saved["saved_at"], notice=friendly_error(e))
        return jsonify(error=friendly_error(e)), 502

    if sample_id:
        cache_file("notice", sample_id, lang).write_text(json.dumps(
            {"result": result, "model": model, "saved_at": datetime.now().strftime("%d %b %Y, %H:%M")},
            ensure_ascii=False, indent=2), encoding="utf-8")

    return jsonify(result=result, model=model, cached=False, seconds=round(time.time() - started, 1))


# ------------------------------------------------------------------ pages

def load(name):
    return json.loads((BASE_DIR / "data" / name).read_text(encoding="utf-8"))


def load_campus():
    """All campus data the website shows: JSON files in data/, plus edits admins made on the site."""
    campus = {
        "timetables": {str(y): load(f"year{y}_timetable.json") for y in (1, 2, 3, 4)},
        "notices": load("notices.json"),
        "assignments": load("assignments.json"),
        "exams": load("exams.json"),
        "syllabus": load("syllabus.json"),
        "hostel": load("hostel.json"),
        "calendar": load("academic_calendar.json"),
    }
    ov = accounts.get_setting("calendar")
    if ov:
        campus["calendar"] = {**campus["calendar"], **ov["value"], "edited_by": ov["updated_by"], "edited_at": ov["updated_at"]}
    for y in ("1", "2", "3", "4"):
        ov = accounts.get_setting(f"timetable:{y}")
        if ov:
            campus["timetables"][y] = {**ov["value"], "edited_by": ov["updated_by"], "edited_at": ov["updated_at"]}
        ov = accounts.get_setting(f"exams:{y}")
        if ov:
            campus["exams"]["years"][y] = {**ov["value"], "edited_by": ov["updated_by"], "edited_at": ov["updated_at"]}
    ov = accounts.get_setting("assignments")
    if ov:
        edits, gone = ov["value"].get("edits", {}), set(ov["value"].get("deleted", []))
        campus["assignments"]["assignments"] = [{**a, **edits.get(a["id"], {})}
                                                for a in campus["assignments"]["assignments"] if a["id"] not in gone]
    for y, syl in campus["syllabus"]["years"].items():
        ov = accounts.get_setting(f"syllabus:{y}")
        if ov:
            edits, gone = ov["value"].get("edits", {}), set(ov["value"].get("deleted", []))
            syl["subjects"] = {c: {**sub, **edits.get(c, {})} for c, sub in syl["subjects"].items() if c not in gone}
    # menu:<hostel> is the "All years" menu; menu:<hostel>:<year> is a year's own menu and wins for that year
    # a saved menu with no dishes in it is ignored, so it can't hide the menu in data/hostel.json
    for h in ("boys", "girls"):
        hostel = campus["hostel"]["hostels"][h]
        ov = accounts.get_setting(f"menu:{h}")
        if ov and _has_dishes(ov["value"]):
            hostel.update(ov["value"])
            hostel.update(edited_by=ov["updated_by"], edited_at=ov["updated_at"])
        # data/hostel.json can hold a year's own menu too (first year's mess menus live there)
        hostel["years"] = hostel.get("years") or {}
        for y in ("1", "2", "3", "4"):
            ov = accounts.get_setting(f"menu:{h}:{y}")
            if ov and _has_dishes(ov["value"]):
                hostel["years"][y] = {**ov["value"], "edited_by": ov["updated_by"], "edited_at": ov["updated_at"]}
    return campus


def _has_dishes(menu):
    return any(v for day in (menu.get("mess_menu") or {}).values() for v in day.values())


def menu_for_year(hostel, year):
    """The menu students of `year` see: that year's own menu, else the All years menu."""
    return hostel.get("years", {}).get(str(year)) or hostel


# ------------------------------------------------------------------ admin editing (timetables, exams, menus)

DAYS6 = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
DAYS7 = DAYS6 + ["Sunday"]
MEALS = ["Breakfast", "Lunch", "Snacks", "Dinner"]
CLASS_TYPES = ("Lecture", "Lab", "Tutorial", "Studio", "Free")
SECTION_IDS = list("ABCDEFGHIJ") + ["Z"]


def _txt(v, n):
    return re.sub(r"\s+", " ", str(v or "")).strip()[:n]


@app.post("/api/edit/timetable")
@admin_required
def edit_timetable():
    data = request.get_json(silent=True) or {}
    year = str(data.get("year"))
    if year not in ("1", "2", "3", "4"):
        return jsonify(error="Unknown year."), 400
    tt = load_campus()["timetables"][year]
    tt = {k: v for k, v in tt.items() if k not in ("edited_by", "edited_at")}
    sec = next((x for x in tt["sections"] if x["id"] == data.get("section")), None)
    if not sec:
        return jsonify(error="Unknown section."), 400
    if "classroom" in data:
        sec["classroom"] = _txt(data.get("classroom"), 40)
    classes = []
    for i, c in enumerate(data.get("classes") or [], 1):
        if not isinstance(c, dict):
            continue
        try:
            start, end = int(c.get("start")), int(c.get("end"))
        except (TypeError, ValueError):
            return jsonify(error=f"Row {i}: choose a start and end time."), 400
        code = _txt(c.get("code"), 40)
        if not code:
            return jsonify(error=f"Row {i}: enter the subject code or name."), 400
        if c.get("day") not in DAYS6:
            return jsonify(error=f"Row {i}: choose a day."), 400
        if not (7 <= start < end <= 21):
            return jsonify(error=f"Row {i}: the end time must be after the start time."), 400
        group = c.get("group") if c.get("group") in ("Gr.1", "Gr.2", "Gr.3", "Gr.4") else None
        ctype = c.get("type") if c.get("type") in CLASS_TYPES else "Lecture"
        classes.append({"day": c["day"], "start": start, "end": end, "code": code,
                        "label": f"{code}-{group}" if group else code, "faculty": _txt(c.get("faculty"), 80),
                        "group": group, "type": ctype, "room": _txt(c.get("room"), 40) or sec.get("classroom", "")})
    if len(classes) > 150:
        return jsonify(error="Too many classes for one section."), 400
    sec["classes"] = classes
    if year != "1" or tt.get("source") == "Not added yet":
        tt["source"] = "Added on CampusMate by admins"
        tt["note"] = "Timetable maintained by CampusMate admins."
    accounts.set_setting(f"timetable:{year}", tt)
    return jsonify(ok=True, timetable=load_campus()["timetables"][year])


@app.post("/api/edit/exams")
@admin_required
def edit_exams():
    data = request.get_json(silent=True) or {}
    year = str(data.get("year"))
    if year not in ("1", "2", "3", "4"):
        return jsonify(error="Unknown year."), 400
    rows = []
    for i, r in enumerate(data.get("rows") or [], 1):
        if not isinstance(r, dict):
            continue
        d = str(r.get("date") or "")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", d):
            return jsonify(error=f"Row {i}: choose a date."), 400
        code = _txt(r.get("code"), 60)
        if not code:
            return jsonify(error=f"Row {i}: enter the subject."), 400
        secs = [x for x in (r.get("sections") or []) if x in SECTION_IDS] or ["all"]
        rows.append({"date": d, "time": _txt(r.get("time"), 40), "code": code, "sections": secs,
                     "venue": _txt(r.get("venue"), 60)})
    if len(rows) > 80:
        return jsonify(error="Too many exam rows."), 400
    rows.sort(key=lambda r: (r["date"], r["time"]))
    accounts.set_setting(f"exams:{year}", {"name": _txt(data.get("name"), 120), "rows": rows})
    return jsonify(ok=True, exams=load_campus()["exams"]["years"][year])


@app.post("/api/edit/menu")
@admin_required
def edit_menu():
    """Save a hostel's menu for "all" years (the fallback) or for one year (1-4).
    {"clear": true} on a year removes its own menu so it goes back to the All years one."""
    data = request.get_json(silent=True) or {}
    hostel = data.get("hostel")
    if hostel not in ("boys", "girls"):
        return jsonify(error="Unknown hostel."), 400
    year = str(data.get("year") or "all")
    if year not in ("all", "1", "2", "3", "4"):
        return jsonify(error="Unknown year."), 400
    key = f"menu:{hostel}" if year == "all" else f"menu:{hostel}:{year}"
    if data.get("clear"):
        if year == "all":
            return jsonify(error="The All years menu can't be removed, only edited."), 400
        accounts.delete_setting(key)
    else:
        menu_in, times_in = data.get("mess_menu") or {}, data.get("mess_timings") or {}
        menu = {d: {m: _txt((menu_in.get(d) or {}).get(m), 200) for m in MEALS} for d in DAYS7}
        timings = {m: _txt(times_in.get(m), 60) for m in MEALS}
        # day_timings: times for the days that differ from mess_timings (e.g. weekend breakfast).
        # Editors that don't send it keep the ones the menu has now.
        if "day_timings" in data:
            days_in = data.get("day_timings") or {}
        else:
            current = load_campus()["hostel"]["hostels"][hostel]
            days_in = current.get("day_timings") if year == "all" else menu_for_year(current, year).get("day_timings")
            days_in = days_in or {}
        day_timings = {d: t for d in DAYS7
                       if (t := {m: _txt((days_in.get(d) or {}).get(m), 60) for m in MEALS if (days_in.get(d) or {}).get(m)})}
        accounts.set_setting(key, {"mess_menu": menu, "mess_timings": timings, "day_timings": day_timings})
    return jsonify(ok=True, hostel=load_campus()["hostel"]["hostels"][hostel])


# ------------------------------------------------------------------ built-in assignments & syllabus (admins edit or delete)
#
# Assignments and syllabus subjects that ship in data/*.json can't be changed in place (read-only on Vercel),
# so edits and deletions are stored as overrides in settings and applied by load_campus().
# Published assignments and study material are posts; they use /api/posts/<id>/edit and /delete.

def _overrides(key):
    ov = accounts.get_setting(key)
    v = ov["value"] if ov else {}
    return {"edits": v.get("edits", {}), "deleted": v.get("deleted", [])}


def _pdf_field(old_pdf):
    """New /files/ URL if a PDF was uploaded (the old uploaded one is removed), the old value if not,
    or False if the upload isn't a PDF."""
    name = accounts.save_pdf_upload()
    if name is False:
        return False
    if not name:
        return old_pdf
    accounts.db().commit()
    accounts.delete_file(old_pdf)
    return f"/files/{name}"


def _builtin_assignment(aid):
    return next((a for a in load_campus()["assignments"]["assignments"] if a["id"] == aid), None)


@app.post("/api/edit/assignment/<aid>")
@admin_required
def edit_builtin_assignment(aid):
    a = _builtin_assignment(aid)
    if not a:
        return jsonify(error="Assignment not found."), 404
    f = request.form
    title = _txt(f.get("title"), 150)
    if len(title) < 3:
        return jsonify(error="Please enter a title (at least 3 characters)."), 400
    due = f.get("due", "").strip() or None
    if due and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", due):
        return jsonify(error="Due date is not valid."), 400
    marks = f.get("marks", "").strip()
    pdf = _pdf_field(a.get("pdf"))
    if pdf is False:
        return jsonify(error="Only PDF files can be uploaded."), 400
    ov = _overrides("assignments")
    ov["edits"][aid] = {
        "title": title, "code": re.sub(r"[^A-Za-z0-9 &\-]", "", f.get("code", "")).strip()[:40] or "General",
        "details": f.get("body", "").strip()[:8000] or "See the attached PDF.", "due": due,
        "marks": int(marks) if marks.isdigit() and int(marks) <= 1000 else None,
        "submit": _txt(f.get("submit"), 300) or a.get("submit", ""), "pdf": pdf,
    }
    accounts.set_setting("assignments", ov)
    return jsonify(ok=True)


@app.post("/api/edit/assignment/<aid>/delete")
@admin_required
def delete_builtin_assignment(aid):
    a = _builtin_assignment(aid)
    if not a:
        return jsonify(error="Assignment not found."), 404
    ov = _overrides("assignments")
    old = ov["edits"].pop(aid, {})
    ov["deleted"].append(aid)
    accounts.set_setting("assignments", ov)
    accounts.delete_file(old.get("pdf"))
    return jsonify(ok=True)


def _builtin_subject(year, code):
    if year not in ("1", "2", "3", "4"):
        return None
    return load_campus()["syllabus"]["years"].get(year, {}).get("subjects", {}).get(code)


@app.post("/api/edit/syllabus/<year>/<code>")
@admin_required
def edit_syllabus_subject(year, code):
    sub = _builtin_subject(year, code)
    if not sub:
        return jsonify(error="Subject not found."), 404
    f = request.form
    name = _txt(f.get("name"), 150)
    text = f.get("text", "").strip()[:20000]
    if len(name) < 2:
        return jsonify(error="Please enter the subject name."), 400
    if not text:
        return jsonify(error="Please enter the syllabus text."), 400
    pdf = _pdf_field(sub.get("pdf"))
    if pdf is False:
        return jsonify(error="Only PDF files can be uploaded."), 400
    ov = _overrides(f"syllabus:{year}")
    ov["edits"][code] = {"name": name, "text": text, "note": _txt(f.get("note"), 300) or None, "pdf": pdf}
    if pdf != sub.get("pdf"):
        ov["edits"][code]["pages"] = []  # page numbers referred to the old PDF
    accounts.set_setting(f"syllabus:{year}", ov)
    return jsonify(ok=True)


@app.post("/api/edit/syllabus/<year>/<code>/delete")
@admin_required
def delete_syllabus_subject(year, code):
    if not _builtin_subject(year, code):
        return jsonify(error="Subject not found."), 404
    ov = _overrides(f"syllabus:{year}")
    old = ov["edits"].pop(code, {})
    ov["deleted"].append(code)
    accounts.set_setting(f"syllabus:{year}", ov)
    accounts.delete_file(old.get("pdf"))
    return jsonify(ok=True)


# ------------------------------------------------------------------ academic calendar (everyone views, admins edit)

EVENT_TYPES = ("holiday", "exam", "event", "semester")
ISO_DATE = re.compile(r"\d{4}-\d{2}-\d{2}")


def _iso(v):
    v = str(v or "")
    if not ISO_DATE.fullmatch(v):
        return None
    try:
        date.fromisoformat(v)
    except ValueError:
        return None
    return v


def _cal_years(v):
    """Years a calendar entry applies to: ["all"] or a sorted list of 1-4 (entries saved before this were for all years)."""
    years = sorted({int(y) for y in (v if isinstance(v, list) else []) if str(y) in ("1", "2", "3", "4")})
    return years if years and len(years) < 4 else ["all"]


@app.post("/api/edit/calendar")
@admin_required
def edit_calendar():
    data = request.get_json(silent=True) or {}
    events = []
    for i, e in enumerate(data.get("events") or [], 1):
        if not isinstance(e, dict):
            continue
        start = _iso(e.get("date"))
        if not start:
            return jsonify(error=f"Row {i}: choose a date."), 400
        end = _iso(e.get("end")) or start
        if end < start:
            return jsonify(error=f"Row {i}: the end date is before the start date."), 400
        if (date.fromisoformat(end) - date.fromisoformat(start)).days > 366:
            return jsonify(error=f"Row {i}: an entry can't be longer than a year."), 400
        title = _txt(e.get("title"), 120)
        if not title:
            return jsonify(error=f"Row {i}: enter a title, e.g. Diwali."), 400
        etype = e.get("type") if e.get("type") in EVENT_TYPES else "holiday"
        events.append({"date": start, "end": end, "title": title, "type": etype, "note": _txt(e.get("note"), 200),
                       "years": _cal_years(e.get("years"))})
    if len(events) > 300:
        return jsonify(error="Too many calendar entries (300 max)."), 400
    events.sort(key=lambda e: (e["date"], e["end"], e["title"]))
    name = _txt(data.get("name"), 120) or "Academic Calendar"
    accounts.set_setting("calendar", {"name": name, "events": events})
    return jsonify(ok=True, calendar=load_campus()["calendar"])


# ------------------------------------------------------------------ self attendance (private to each student)

ATT_STATUSES = ("present", "absent", "cancelled")


def clean_attendance(data):
    """Validate the attendance document the browser sends; raise ValueError with a friendly message."""
    try:
        target = int(data.get("target") or 75)
    except (TypeError, ValueError):
        target = 75
    target = min(max(target, 1), 100)
    records, seen = [], set()
    for r in (data.get("records") or [])[:5000]:
        if not isinstance(r, dict):
            continue
        d = _iso(r.get("date"))
        code = _txt(r.get("code"), 40)
        if not d or not code or r.get("status") not in ATT_STATUSES:
            continue
        slot = _txt(r.get("slot"), 20) or "extra"
        rid = _txt(r.get("id"), 80) or f"{d}|{code}|{slot}"
        if rid in seen:
            continue
        seen.add(rid)
        records.append({"id": rid, "date": d, "code": code, "slot": slot,
                        "type": _txt(r.get("type"), 20), "status": r["status"]})
    if len(records) > 4000:
        raise ValueError("Too many attendance entries.")
    base = {}
    for code, b in list((data.get("base") or {}).items())[:60]:
        code = _txt(code, 40)
        if not code or not isinstance(b, dict):
            continue
        try:
            attended, total = int(b.get("attended") or 0), int(b.get("total") or 0)
        except (TypeError, ValueError):
            raise ValueError(f"{code}: starting counts must be whole numbers.")
        if not (0 <= attended <= total <= 1000):
            raise ValueError(f"{code}: classes attended can't be more than classes held.")
        if total:
            base[code] = {"attended": attended, "total": total}
    extra = []
    for code in (data.get("subjects") or [])[:40]:
        code = _txt(code, 40)
        if code and code not in extra:
            extra.append(code)
    return {"target": target, "records": records, "base": base, "subjects": extra}


@app.get("/api/attendance")
@login_required
def get_attendance():
    saved = accounts.get_user_data(current_user()["id"], "attendance")
    if not saved:
        return jsonify(attendance={"target": 75, "records": [], "base": {}, "subjects": []}, updated_at=None)
    return jsonify(attendance=saved["value"], updated_at=saved["updated_at"])


@app.post("/api/attendance")
@login_required
def save_attendance():
    try:
        doc = clean_attendance(request.get_json(silent=True) or {})
    except ValueError as e:
        return jsonify(error=str(e)), 400
    stamp = accounts.set_user_data(current_user()["id"], "attendance", doc)
    return jsonify(ok=True, attendance=doc, updated_at=stamp)


def for_student(item, year, section):
    years = item.get("years") or ([item["year"]] if "year" in item else ["all"])
    sections = item.get("sections") or ["all"]
    year_ok = "all" in years or year in years or str(year) in [str(y) for y in years]
    return year_ok and ("all" in sections or section in sections)


@app.get("/")
def index():
    # Check if a student is currently logged in
    user = public_user(current_user())
    
    if not user:
        # If not logged in, show the new Google Login landing page
        return render_template("landing.html", google_client_id=accounts.google_client_id())
    
    # If logged in, load the campus data and show the student dashboard
    campus = load_campus()
    campus["posts"] = accounts.all_posts()
    return render_template("index.html", samples=load("sample_notices.json"), campus=campus,
                           me=user, models=MODELS)
# ------------------------------------------------------------------ JSON API for the Android app

@app.get("/api/config")
def api_config():
    # public: the Android app needs the web client ID to ask Google for an ID token
    return jsonify(google_client_id=accounts.google_client_id())


@app.get("/api/me")
@login_required
def api_me():
    return jsonify(user=public_user(current_user()))


@app.get("/api/campus")
@login_required
def api_campus():
    """Everything the dashboard page embeds, for the native app."""
    campus = load_campus()
    campus["posts"] = accounts.all_posts()
    return jsonify(campus=campus, samples=load("sample_notices.json"), me=public_user(current_user()), models=MODELS)


# ------------------------------------------------------------------ Study Planner

PLAN_PROMPT = """You are CampusMate AI, a realistic study coach for Indian college students.

Make a day-by-day study timetable for these exact dates: {dates}
The student can study about {hours} hours per day, starting around {start}.

Subjects:
{subjects}

Rules:
- Plan ONLY for the dates listed. On a subject's exam date, schedule only light revision of THAT subject before the exam, and mark the exam itself as a session with type "exam".
- Do not schedule a subject after its exam date.
- Give more time to harder subjects and to exams that come sooner.
- The last 1-2 days before each exam must include revision of that subject.
- Add a short break (type "break") after every 60-90 minutes of study. Breaks do not count toward study hours.
- Keep each day's study time close to {hours} hours. Never more than {hours} + 1.
- Use specific topics from the student's list in "task". If the topic list is empty, write general tasks like "Revise unit notes".
- If there is clearly not enough time to cover everything, say so in "warnings".

Reply with ONLY one JSON object, no markdown fences, in exactly this structure:
{{
  "overview": "2 short sentences describing the strategy",
  "warnings": ["honest warnings, or empty list"],
  "days": [
    {{"date": "YYYY-MM-DD", "focus": "short focus for the day",
      "sessions": [{{"time": "e.g. 6:00-7:30 PM", "subject": "subject name or Break", "task": "specific task", "type": "study | revision | practice | break | exam"}}]}}
  ],
  "tips": ["2-3 short practical tips"]
}}
"""

PLAN_TYPES = ("study", "revision", "practice", "break", "exam")


@app.post("/api/plan")
@login_required
def make_plan():
    data = request.get_json(silent=True) or {}
    try:
        hours = float(data.get("hours") or 0)
    except (TypeError, ValueError):
        hours = 0
    if not 1 <= hours <= 14:
        return jsonify(error="Enter study hours per day between 1 and 14."), 400
    start = str(data.get("start") or "18:00")[:5]

    today = date.today()
    subjects, lines = [], []
    for s in (data.get("subjects") or [])[:8]:
        name = str(s.get("name") or "").strip()[:60]
        if not name:
            continue
        try:
            exam = date.fromisoformat(str(s.get("exam_date")))
        except ValueError:
            return jsonify(error=f"Please pick a valid exam date for {name}."), 400
        if exam < today:
            return jsonify(error=f"The exam date for {name} is in the past."), 400
        topics = str(s.get("topics") or "").strip()[:600]
        level = s.get("difficulty") if s.get("difficulty") in ("easy", "medium", "hard") else "medium"
        subjects.append(exam)
        lines.append(f"- {name}: exam on {exam.isoformat()} ({exam.strftime('%A')}), difficulty {level}, "
                     f"topics: {topics or 'not given'}")
    if not lines:
        return jsonify(error="Add at least one subject with an exam date."), 400

    last = max(subjects)
    n_days = min((last - today).days + 1, 14)
    dates = [(today + timedelta(days=i)) for i in range(n_days)]
    date_list = ", ".join(f"{d.isoformat()} ({d.strftime('%a')})" for d in dates)
    truncated = (last - today).days + 1 > 14

    prompt = PLAN_PROMPT.format(dates=date_list, hours=hours, start=start, subjects="\n".join(lines))
    sample = data.get("sample_id") == "demo"
    started = time.time()
    try:
        raw, model = ask_gemma([prompt])
    except Exception as e:
        cf = cache_file("plan", "demo", "en")
        if sample and cf.exists():
            saved = json.loads(cf.read_text(encoding="utf-8"))
            return jsonify(result=saved["result"], model=saved["model"], cached=True,
                           cached_at=saved["saved_at"], notice=friendly_error(e))
        return jsonify(error=friendly_error(e)), 502

    valid_dates = {d.isoformat() for d in dates}
    days = []
    for d in _list(raw.get("days")):
        if not isinstance(d, dict) or d.get("date") not in valid_dates:
            continue
        sessions = [x for x in _list(d.get("sessions")) if isinstance(x, dict)]
        for x in sessions:
            if x.get("type") not in PLAN_TYPES:
                x["type"] = "study"
        days.append({"date": d["date"], "focus": d.get("focus") or "", "sessions": sessions})
    days.sort(key=lambda d: d["date"])
    if not days:
        return jsonify(error="The AI did not return a usable timetable. Please try again."), 502

    warnings = [str(x) for x in _list(raw.get("warnings"))]
    if truncated:
        warnings.insert(0, "Your last exam is more than 2 weeks away, so this plan covers the next 14 days. Generate a new plan later for the rest.")
    result = {"overview": raw.get("overview") or "", "warnings": warnings,
              "days": days, "tips": [str(x) for x in _list(raw.get("tips"))]}

    if sample:
        cache_file("plan", "demo", "en").write_text(json.dumps(
            {"result": result, "model": model, "saved_at": datetime.now().strftime("%d %b %Y, %H:%M")},
            ensure_ascii=False, indent=2), encoding="utf-8")
    return jsonify(result=result, model=model, cached=False, seconds=round(time.time() - started, 1))


# ------------------------------------------------------------------ Ask CampusMate

ASK_PROMPT = """You are CampusMate AI, a friendly assistant for a college student in {section}.
Today is {today}. Tomorrow is {tomorrow}.

Answer the student's question using ONLY the campus data below. Rules:
- If the answer is not in the data, say so honestly and suggest who to ask (e.g. the block warden or the department office). Never invent times, dates, rooms, rules, phone numbers or menus.
- Use the student's timetable for questions about their classes. Sunday has no classes, and neither does any day marked as a holiday in the academic calendar.
- For questions about holidays, breaks or important dates, use the academic calendar. For questions about the student's own attendance, use "The student's self-tracked attendance" (it is their own record, not the official college record).
- Subjects are often written as codes (e.g. MA-111). Use the codes as given; do not guess full subject names that are not in the data.
- Consider assignments, the exam schedule, syllabus outlines and notices. Mention a notice or assignment by its title.
- If you use SAMPLE data (assignments, exams, syllabus, hostel), you may answer from it, but do not claim it is official.
- Be short and practical. Use simple English, or Hindi if the student writes in Hindi.

Reply with ONLY one JSON object, no markdown fences:
{{"answer": "1-3 short sentences", "points": ["optional short bullet points, e.g. each class or each deadline"], "sources": ["which parts of the data you used, e.g. Timetable, Assignments, Exam schedule, Syllabus, Notice: <title>, Mess menu, Your study plan"], "found": true}}
Set "found": false if the data does not contain the answer.

===== CAMPUS DATA =====
{context}
===== END OF DATA =====
{history}
Student's question: {question}
"""


def build_ask_context(year, section_id, group, client):
    campus = load_campus()
    tt = campus["timetables"].get(str(year)) or campus["timetables"]["1"]
    sec = next((s for s in tt["sections"] if s["id"] == section_id), tt["sections"][0])
    hostel = campus["hostel"]
    y = tt["year"]

    parts = [f"## Student: Year {y}, {sec['name']}" + (f", group {group}" if group else "") +
             (f" (branches: {sec['branches']})" if sec.get("branches") else ""),
             f"## Class timetable – Year {y}, {sec['name']} – {tt['title']} ({'official' if tt['official'] else 'SAMPLE data'})",
             "Lunch 13:00–14:00. Group labs (Gr.1/Gr.2/Gr.3) apply only to that group. " + tt.get("note", "")]
    for day in ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]:
        items = sorted((c for c in sec["classes"] if c["day"] == day), key=lambda c: c["start"])
        items = [c for c in items if not group or not c["group"] or c["group"] == group]
        parts.append(f"{day}: " + ("; ".join(
            f"{c['start']}:00–{c['end']}:00 {c['label']} ({c['type']}, {c['room']}{', ' + c['faculty'] if c['faculty'] else ''})"
            for c in items) or "no classes"))
    if tt.get("classrooms"):
        parts.append("Classroom locations: " + "; ".join(f"{k}: {v}" for k, v in tt["classrooms"].items()))

    parts.append("\n## Assignments published for this student")
    for a in campus["assignments"]["assignments"]:
        if for_student(a, y, sec["id"]):
            due = f"due {a['due']}" if a.get("due") else "due date not stated"
            kind = "real assignment PDF" if a.get("official") else "sample"
            topics = f" Topics: {', '.join(a['topics'])}." if a.get("topics") else ""
            parts.append(f"- {a['code']}: {a['title']} ({kind}, posted {a['posted']}, {due}"
                         f"{', ' + str(a['marks']) + ' marks' if a.get('marks') else ''}). {a['details']}{topics} Submit: {a['submit']}")

    ex = campus["exams"]["years"].get(str(y))
    if ex:
        parts.append(f"\n## Exam schedule: {ex['name']} (sample data, not the official date sheet)")
        parts += [f"- {r['date']} {r['time']}: {r['code']} ({r['venue']})" for r in ex["rows"] if for_student(r, y, sec["id"])]
        parts.append("Exam rules: " + " ".join(campus["exams"]["rules"]))

    codes = sorted({c["code"] for c in sec["classes"]})
    syl = campus["syllabus"]["years"].get(str(y), {})
    subjects = syl.get("subjects", {})
    parts.append(f"\n## Syllabus for this section's subjects ({'official curriculum' if syl.get('official') else 'SAMPLE syllabus'})")
    for code in codes:
        sub = subjects.get(code)
        if sub:
            parts.append(f"### {code}: {sub['name']}\n{sub['text'][:650]}")
        else:
            parts.append(f"- {code}: {syl.get('missing', {}).get(code, 'syllabus not added yet')}")

    posts = [p for p in accounts.all_posts() if for_student(p, y, sec["id"])]
    if posts:
        parts.append("\n## Recently published by admins on CampusMate (official uploads)")
        for p in posts[:15]:
            extra = (f", subject {p['code']}" if p["code"] else "") + (f", due {p['due']}" if p["due"] else "")
            parts.append(f"- [{p['kind']}] {p['title']} (published {p['published_at']} by {p['published_by']}{extra}): {p['body'][:600]}")

    cal = campus["calendar"]
    today_iso = date.today().isoformat()
    upcoming = [e for e in cal.get("events", []) if e["end"] >= today_iso and for_student(e, y, sec["id"])][:30]
    parts.append(f"\n## Academic calendar: {cal.get('name', 'Academic Calendar')} (confirmed by CampusMate admins; only dates for this student's year)")
    if upcoming:
        for e in upcoming:
            when = e["date"] if e["end"] == e["date"] else f"{e['date']} to {e['end']}"
            parts.append(f"- {when}: {e['title']} ({e['type']}){' – ' + e['note'] if e.get('note') else ''}")
    else:
        parts.append("No upcoming holidays or events have been added yet.")

    att = client.get("attendance") if isinstance(client.get("attendance"), dict) else None
    if att and _list(att.get("subjects")):
        parts.append(f"\n## The student's self-tracked attendance (target {str(att.get('target'))[:3]}%)")
        for a in _list(att.get("subjects"))[:30]:
            if isinstance(a, dict):
                parts.append(f"- {str(a.get('code'))[:40]}: attended {str(a.get('attended'))[:5]} of {str(a.get('total'))[:5]} classes"
                             f" ({str(a.get('percent'))[:6]}%). {str(a.get('advice'))[:120]}")

    parts.append("\n## Notices for this student")
    for n in campus["notices"]["notices"]:
        if for_student(n, y, sec["id"]):
            parts.append(f"### Notice: {n['title']} (dated {n['date']}, from {n['from']})\n{n['text'][:1100]}")

    for hid, hostel in campus["hostel"]["hostels"].items():
        h = menu_for_year(hostel, y)
        if any(v for day in h["mess_menu"].values() for v in day.values()):
            parts.append(f"\n## {hostel['name']} mess menu (Year {y})")
            timings = "; ".join(f"{k} {v}" for k, v in h["mess_timings"].items() if v)
            if timings:
                parts.append("Mess timings: " + timings)
            for day, t in (h.get("day_timings") or {}).items():
                parts.append(f"Mess timings on {day}: " + "; ".join(f"{k} {v}" for k, v in t.items()))
            for day, meals in h["mess_menu"].items():
                items = "; ".join(f"{k}: {v}" for k, v in meals.items() if v)
                if items:
                    parts.append(f"{day}: {items}")
        else:
            parts.append(f"\n## {hostel['name']} mess menu: not added yet")

    deadlines = [d for d in _list(client.get("deadlines")) if isinstance(d, dict)][:20]
    if deadlines:
        parts.append("\n## Deadlines the student saved from explained notices")
        parts += [f"- {str(d.get('date'))[:20]}: {str(d.get('what'))[:150]} (from: {str(d.get('from'))[:80]})" for d in deadlines]
    plan = [d for d in _list(client.get("plan")) if isinstance(d, dict)][:14]
    if plan:
        parts.append("\n## The student's study plan")
        for d in plan:
            sessions = "; ".join(f"{str(s.get('time'))[:20]} {str(s.get('subject'))[:60]} – {str(s.get('task'))[:80]}"
                                 for s in _list(d.get("sessions"))[:8] if isinstance(s, dict))
            parts.append(f"{str(d.get('date'))[:10]}: {sessions}")
    label = f"Year {y}, {sec['name']}" + (f" ({group})" if group else "")
    return label, "\n".join(parts)


@app.post("/api/ask")
@login_required
def ask():
    data = request.get_json(silent=True) or {}
    question = str(data.get("question") or "").strip()
    if not question:
        return jsonify(error="Type a question first."), 400
    if len(question) > 500:
        return jsonify(error="Please keep your question under 500 characters."), 400

    try:
        year = int(data.get("year") or 1)
    except (TypeError, ValueError):
        year = 1
    group = str(data.get("group") or "")
    if not re.fullmatch(r"Gr\.\d", group):
        group = ""
    label, context = build_ask_context(year, str(data.get("section") or ""), group, data.get("client") or {})
    history = ""
    turns = [t for t in _list(data.get("history")) if isinstance(t, dict)][-4:]
    if turns:
        history = "\nEarlier in this chat:\n" + "\n".join(
            f"Student: {str(t.get('q'))[:300]}\nCampusMate: {str(t.get('a'))[:400]}" for t in turns) + "\n"

    today = date.today()
    prompt = ASK_PROMPT.format(
        section=label, context=context, history=history, question=question,
        today=today.strftime("%A, %d %B %Y"), tomorrow=(today + timedelta(days=1)).strftime("%A, %d %B %Y"),
    )
    started = time.time()
    try:
        raw, model = ask_gemma([prompt])
    except Exception as e:
        return jsonify(error=friendly_error(e)), 502
    return jsonify(
        answer=str(raw.get("answer") or "Sorry, I couldn't form an answer. Please try rephrasing."),
        points=[str(x) for x in _list(raw.get("points"))][:12],
        sources=[str(x) for x in _list(raw.get("sources"))][:6],
        found=raw.get("found") is not False,
        model=model, seconds=round(time.time() - started, 1),
    )


# ------------------------------------------------------------------ Results (from nithresults.xyz)

RESULTS_URL = os.getenv("RESULTS_URL", "https://www.nithresults.xyz/api/query")
RESULTS_TTL = 3 * 3600          # keep a looked-up result for 3 hours, then fetch fresh
RESULTS_PER_USER = 30           # lookups per user per 10 minutes (cached ones are free)
GRADE_POINTS = {"A": 10, "AB": 9, "B": 8, "BC": 7, "C": 6, "CD": 5, "D": 4, "F": 0}
_results_cache = {}             # query -> (fetched_at, students)
_results_hits = {}              # user id -> [timestamps]


class ResultsDown(Exception):
    pass


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def shape_student(raw):
    """Turn one nithresults.xyz student record into what the Results page shows, with SGPA/CGPA."""
    info = raw.get("student_info") or {}
    semesters, total_pts, total_cr = [], 0.0, 0.0
    for sem in _list(raw.get("semesters")):
        name = str(sem.get("semester_name") or "")
        m = re.search(r"S0*(\d+)", name)
        subjects, pts, cr = [], 0.0, 0.0
        for s in _list(sem.get("subjects")):
            credit = _num(s.get("credit")) or 0
            grade = str(s.get("grade") or "").strip().upper()
            gp = GRADE_POINTS.get(grade)
            if gp is None and _num(s.get("marks")) is not None and credit:
                gp = _num(s.get("marks")) / credit   # unknown grade letter: points = marks / credit
            if gp is not None and credit:
                pts += gp * credit
                cr += credit
            subjects.append({"code": str(s.get("code") or ""), "subject": str(s.get("subject") or ""),
                             "credit": credit, "grade": grade,
                             "points": round(gp * credit, 2) if gp is not None else None})
        total_pts += pts
        total_cr += cr
        semesters.append({
            "number": int(m.group(1)) if m else len(semesters) + 1,
            "name": f"Semester {int(m.group(1))}" if m else (name or f"Semester {len(semesters) + 1}"),
            "subjects": subjects,
            "credits": cr,
            "sgpa": round(pts / cr, 2) if cr else None,
            "cgpa": round(total_pts / total_cr, 2) if total_cr else None,
        })
    return {
        "name": str(info.get("student_name") or "").title(),
        "roll": str(info.get("roll_number") or "").upper(),
        "father": str(info.get("father_name") or "").title(),
        "year": raw.get("year"),
        "semesters": semesters,
        "credits": total_cr,
        "cgpa": round(total_pts / total_cr, 2) if total_cr else None,
    }


def fetch_results(query):
    import requests
    try:
        r = requests.post(RESULTS_URL, params={"query": query}, timeout=12,
                          headers={"User-Agent": "CampusMate/1.0 (NITH campus app)", "Accept": "application/json"})
        r.raise_for_status()
        data = r.json()
    except Exception as e:
        raise ResultsDown(str(e)) from e
    if not isinstance(data, dict) or not isinstance(data.get("students"), list):
        raise ResultsDown("unexpected reply")
    return [shape_student(s) for s in data["students"] if isinstance(s, dict)]


@app.get("/api/results")
@login_required
def results_lookup():
    q = re.sub(r"\s+", "", str(request.args.get("q") or "")).lower()
    if not re.fullmatch(r"[a-z0-9]{5,15}", q):
        return jsonify(error="Enter a full roll number, like 25BCS050."), 400
    now = time.time()
    cached = _results_cache.get(q)
    if cached and now - cached[0] < RESULTS_TTL:
        return jsonify(students=cached[1], fetched_at=cached[0], cached=True)

    uid = current_user()["id"]
    hits = [t for t in _results_hits.get(uid, []) if now - t < 600]
    if len(hits) >= RESULTS_PER_USER:
        return jsonify(error="You've looked up a lot of results in a short time. Please wait a few minutes."), 429
    _results_hits[uid] = hits + [now]

    try:
        students = fetch_results(q)
    except ResultsDown:
        if cached:  # source is down: show the last copy we have rather than nothing
            return jsonify(students=cached[1], fetched_at=cached[0], cached=True, stale=True)
        return jsonify(error="The results site isn't responding right now. Please try again in a little while."), 502
    if len(_results_cache) > 2000:
        _results_cache.clear()
    _results_cache[q] = (now, students)
    return jsonify(students=students, fetched_at=now, cached=False)


# ------------------------------------------------------------------ Installable app (PWA + Android)

ANDROID_PACKAGE = "app.vercel.campusmate_nith"


@app.get("/manifest.webmanifest")
def web_manifest():
    return send_from_directory(app.static_folder, "manifest.webmanifest",
                               mimetype="application/manifest+json")


@app.get("/sw.js")
def service_worker():
    # Served from the root so the worker controls the whole site.
    resp = send_from_directory(Path(app.static_folder) / "js", "sw.js", mimetype="text/javascript")
    resp.headers["Cache-Control"] = "no-cache"
    return resp


@app.get("/offline")
def offline():
    return render_template("offline.html")


@app.get("/.well-known/assetlinks.json")
def asset_links():
    # Lets the Android app open the site full screen (no browser bar). Set
    # ANDROID_CERT_SHA256 to the app's signing key fingerprint(s), comma separated.
    prints = [f.strip().upper() for f in os.getenv("ANDROID_CERT_SHA256", "").split(",") if f.strip()]
    return jsonify([{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {"namespace": "android_app", "package_name": ANDROID_PACKAGE,
                   "sha256_cert_fingerprints": prints},
    }])


@app.errorhandler(413)
def too_large(_):
    return jsonify(error="That file is too big. Please keep uploads under 12 MB."), 413


if __name__ == "__main__":
    app.run(debug=True, port=5000)
