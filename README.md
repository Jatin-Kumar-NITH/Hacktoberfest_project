# CampusMate AI 🎓✦

**Your campus life, organized by AI.** CampusMate AI is an open-source student portal that puts a college student's timetable, notices, assignments, exam schedule and syllabus in one place, and lets them **ask an AI (Google Gemma 4) about any of it in plain language**.

Built at a Hacktoberfest 2026 hackathon (theme: *best open-source AI project / best use of Gemma 4*) using the real first-year timetable, curriculum and assignments of NIT Hamirpur.

---

## Table of contents

1. [The problem](#the-problem)
2. [What CampusMate does](#what-campusmate-does)
3. [Features in detail](#features-in-detail)
4. [How the AI works](#how-the-ai-works)
5. [Architecture](#architecture)
6. [Project structure](#project-structure)
7. [Setup (Windows, macOS, Linux)](#setup)
8. [Configuration (`.env`)](#configuration-env)
9. [Accounts, roles and admin access](#accounts-roles-and-admin-access)
10. [Email OTP](#email-otp)
11. [Updating the campus data](#updating-the-campus-data)
12. [Security](#security)
13. [Testing](#testing)
14. [Limitations and honest notes](#limitations-and-honest-notes)
15. [Roadmap](#roadmap)
16. [License](#license)
17. [Credits](#credits)

---

## The problem

Students get information from too many places: a timetable PDF, notice-board photos, WhatsApp forwards, assignment PDFs and a long curriculum document. Notices are long, formal and easy to misread. Missing one line can mean a late fine, a cancelled hostel room or a blocked admit card. Different sections, groups and years see different information, which makes it even harder to know *what applies to me*.

## What CampusMate does

- Shows **only what applies to you**: pick your **year → section → lab group** once, and every page filters itself.
- Turns any notice into a **plain-language summary, deadlines, documents and a checklist** with Gemma 4, in English and Hindi.
- Lets you **ask questions** like *"What classes do I have tomorrow?"*, *"Which assignments are pending?"* or *"What's in Unit 3 of MA-111?"* and answers **only from your campus data**, citing its sources.
- Gives **admins** a simple way to publish notices, assignments and PDFs, and gives **everyone** a public campus chat.

---

## Features in detail

### 🏠 Dashboard
- **Ask CampusMate**: a chat box powered by Gemma 4 that answers from your timetable, notices, assignments, exam schedule, syllabus, hostel information, your saved deadlines and your study plan. Each answer shows **source tags** (for example *Timetable*, *Notice: …*). If the answer isn't in the data, it says so instead of guessing. It remembers the last 4 exchanges for follow-up questions and replies in Hindi if you ask in Hindi.
- Today's classes for your section and group (on holidays it shows the next working day's classes).
- **Due soon**: upcoming assignments and your next exam.
- New notices for you, upcoming deadlines from explained notices, recent AI results, today's study plan, hostel announcements and today's mess menu.

### 📢 Notice Board
- Two columns: **for everyone in your year** and **for your section only**.
- Every notice shows **who published it and when** (in the viewer's local time).
- **✦ Explain with AI** sends any notice to the Notice Explainer in one click.

### 🗓 Timetable
- The **official 1st-year odd-semester timetable (2026-27)**, transcribed from the Office of Dean Academic PDF: all 11 sections (A–J, Z) and 277 class entries.
- Lecture, lab, tutorial and studio sessions are colour-coded. Two-hour practicals span two columns. Group labs (Gr.1/Gr.2/Gr.3) are listed in parallel, and **labs for other groups fade out** when you pick your group.
- Section details: branches, classroom location, and roll-number ranges for each group.
- Tabs for years 1–4 and every section. Years 2–4 start empty and show "not added yet".
- **Admins can edit any section's timetable from the site**: add, change or remove classes, set the time, type, group, faculty and room. The page shows *Last edited by (name) · time*.

### ✎ Assignments
- The **real MA-111 assignment PDFs** (Units 1, 2 and 3) are published to the sections that take MA-111 (A–J). Each one has a written description of what's inside, topic chips and an **Open PDF** button.
- The PDFs don't state due dates, so the app says **"Due date not stated"** rather than inventing one.
- Admins and the owner can **delete** a published assignment directly from its card.
- Pending / Submitted / All filters, and a **Mark as submitted** button (saved in your browser).
- **✦ How do I start?** asks the AI which syllabus topics the assignment needs.
- Admins can publish new assignments with a due date, marks and a PDF.

### ⚑ Exam Schedule
- Exam papers for your year and section with countdowns.
- **Make a study plan for these exams** fills the Study Planner automatically.
- **Admins enter and edit the exam schedule from the site** (date, time, subject, sections, venue). It starts empty until published.

### ❒ Syllabus
- The **official first-year curriculum (NEP-2020)**: the full PDF, the teaching scheme (page 6) and the evaluation scheme (page 28).
- One card for each subject in your timetable, showing the real course name, semester, L-P and credits, weekly hours, and faculty. Each card has:
  - **📄 Original PDF**: just that subject's page, cut from the curriculum
  - **📝 Read as text**: the full syllabus as text
  - **✦ Summarise**: the AI explains it unit by unit
- An expandable "Other courses this year" list with the other semester's subjects.
- Study material PDFs published by admins appear here too.
- Other years show "not added yet". Admins can publish syllabus PDFs as Study material.

### ✦ Notice Explainer
- Paste a notice or upload a photo. Gemma 4 returns the title, issuer, audience, a summary, urgency, **deadlines with the exact source words**, required documents, a **checklist**, key facts and a **"Missing or unclear"** list, plus a Hindi version.
- Results are saved to your history and their deadlines appear on the Dashboard.

### ◷ Study Planner
- Enter subjects, exam dates, topics, difficulty and daily hours. Gemma 4 builds a **day-by-day timetable (up to 14 days)** with revision before each exam and regular breaks, and warns you honestly if there isn't enough time.

### ⌂ Mess Menu (Boys' and Girls' hostels)
- **Separate weekly menus** for the Boys' Hostel and the Girls' Hostel. Students pick their hostel once (remembered), and today's row is highlighted.
- **Admins edit the menu and meal timings from the site.** The menu shows *Last edited by (name) · time*.
- The Dashboard shows today's menu for your hostel. Ask CampusMate can answer "What's for dinner today?"

### 💬 Campus Chat
- A public chat for all logged-in users. It refreshes every 3 seconds.
- Every message shows the sender, their role tag and the time.
- **Admin and owner messages have coloured banners and name tags.** Admins can remove messages.
- **Chat bans:** admins can ban a student from the chat for 1 hour, 1 day, 7 days or permanently. Only the owner can ban an admin, and the owner can't be banned. A banned user can still read but can't post. A "Banned from chat" list with **Unban** is shown to admins.

### ⬆ Publish (admins) and 🛡 Manage Admins (owner)
- Admins publish **notices, announcements, assignments and study-material PDFs**, each targeted by year and section. Everything shows *Published by* and the *publishing time*.
- Anything published by mistake can be **deleted** from the Notice Board, Assignments, Syllabus or Publish pages. Admins can delete any post except the owner's; the owner can delete everything.
- Only the **owner** can give or remove admin access.

### Also
- **Login with email OTP**, "stay logged in for 30 days", and password strength rules.
- **Dark mode** with a toggle; it follows your system setting by default.
- **Responsive**: works on phones.

---

## How the AI works

| | |
|---|---|
| **Model** | **Gemma 4**, Google's open model: `gemma-4-26b-a4b-it` first, with `gemma-4-31b-it` as automatic fallback |
| **Access** | Google's hosted **Gemini API** (free tier via a Google AI Studio key) using the `google-genai` Python SDK |
| **Where it runs** | Server side only. The API key never reaches the browser. |

**Grounded answers, not guesses.** For *Ask CampusMate*, the server builds a context containing only the data relevant to the student: their section's timetable (filtered to their lab group), the notices for their year and section, their assignments and exams, syllabus text for their subjects, admin uploads, hostel data, and their own saved deadlines and study plan. Gemma is instructed to answer **only from that context**, list its sources, and set `found: false` when the answer isn't there.

**Structured output.** Every AI feature asks Gemma for a strict JSON object, for example `summary`, `deadlines[]`, `checklist[]` and `missing_or_unclear[]`. The server parses it robustly, even if it's wrapped in Markdown fences, and validates every field before the page shows it.

**Honest by design.**
- Every extracted deadline and fact carries the **exact source words** from the notice so students can verify it.
- Uncertain dates are marked *verify*.
- If the free quota runs out, notices explained before (from the Notice Board) can fall back to a result **saved from an earlier live run**, and the page clearly labels it as saved.
- Friendly error messages cover a missing key, a rejected key, the quota limit, and service errors.

---

## Architecture

```
 Browser (HTML / CSS / vanilla JS)                     Flask server (Python)                  External
 ───────────────────────────────────                  ─────────────────────────              ─────────
 Login + OTP page  ───────── /api/login, /api/otp ──▶ accounts.py ── SQLite (instance/) 
 Dashboard, Timetable, Notice Board,                  │  users, posts, chat, OTP codes
 Assignments, Exams, Syllabus, Hostel ◀── page + JSON ┤
 Ask CampusMate ───────────── /api/ask ─────────────▶ app.py ── builds grounded context ──▶ Gemini API
 Notice Explainer ─────────── /api/notice ──────────▶        ── strict JSON prompts     ──▶ (Gemma 4)
 Study Planner ────────────── /api/plan ───────────▶         
 Campus Chat ─────────────── /api/chat (poll 3s) ──▶ accounts.py
 Publish / Manage Admins ──── /api/posts, /api/users ▶ (admin / owner only)          SMTP ──▶ Gmail (OTP email)
                                                      data/*.json + static/docs/*.pdf
```

- **No build step and no front-end framework.** Plain HTML, CSS and JavaScript make it easy to read and modify.
- **Campus data lives in JSON files** (`data/`). Admin uploads, accounts and chat live in **SQLite** (`instance/campusmate.db`).
- Per-student preferences (year, section, group, theme, checklist ticks, study plan) are saved in the browser's `localStorage`.

---

## Project structure

```
campusmate/
├── app.py                  # Flask app: pages, Notice Explainer, Study Planner, Ask CampusMate, Gemma calls
├── accounts.py             # Login, OTP, roles, admin publishing, uploads, campus chat (SQLite)
├── setup_email.py          # One-time Gmail setup for OTP emails (sends a test email)
├── requirements.txt
├── .env.example            # Template for your secrets (copy to .env)
├── .gitignore              # Keeps .env, instance/, venv/ out of Git
├── LICENSE                 # MIT (code only, see License section)
├── templates/
│   ├── index.html          # The single-page app (all sections)
│   └── login.html          # Login / sign-up / OTP
├── static/
│   ├── css/style.css       # Light + dark theme
│   ├── js/app.js           # All front-end logic
│   └── docs/               # Assignment PDFs and per-subject syllabus PDFs
├── data/
│   ├── year1_timetable.json   # Official 1st-year timetable (transcribed)
│   ├── year2..4_timetable.json# Empty, added from the site
│   ├── syllabus.json          # Curriculum per subject (PDF link + text)
│   ├── assignments.json       # Assignments (real MA-111 PDFs)
│   ├── exams.json             # Exam schedule defaults (edited from the site)
│   ├── notices.json           # Built-in notices (empty; admins publish from the site)
│   ├── hostel.json            # Boys'/Girls' hostel menus (edited from the site)
│   └── sample_notices.json    # Optional demo notices for the Notice Explainer (empty)
├── tools/
│   ├── build_year1.py      # Rebuilds year1_timetable.json from the transcription
│   └── build_docs.py       # Splits the curriculum PDF per subject, extracts text, builds syllabus/assignments
└── instance/               # Created at runtime (NOT in Git): database, uploads, secret key
```

---

## Setup

**Requirements:** Python 3.10 or newer, and a free Gemini API key from **https://aistudio.google.com/apikey**. No GPU is needed.

```bash
# 1. Get the code
git clone https://github.com/Jatin-Kumar-NITH/Hacktoberfest_project.git
cd Hacktoberfest_project

# 2. Create a virtual environment
python -m venv venv
# Windows:
venv\Scripts\activate
# macOS / Linux:
source venv/bin/activate

# 3. Install dependencies
pip install -r requirements.txt

# 4. Create your .env from the template, then edit it
# Windows:
copy .env.example .env
# macOS / Linux:
cp .env.example .env

# 5. Run
python app.py
```

Open **http://127.0.0.1:5000**. The terminal prints a startup block showing the database path, the owner email it loaded and whether email OTP is on.

> **Windows tip:** run `python app.py` from the terminal *after* activating `venv`. The prompt should start with `(venv)`. If PowerShell refuses to activate it, run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

### Deploying to Vercel (demo)

The repo includes `vercel.json`. In Vercel → *Project → Settings → Environment Variables*, add `GEMINI_API_KEY`, `OWNER_EMAIL`, `OWNER_PASSWORD`, `OWNER_NAME` and a long random `SECRET_KEY`, then redeploy.

> ⚠️ Vercel runs the app as serverless functions with a **temporary** filesystem, so CampusMate stores its SQLite database in `/tmp` there. **Accounts other than the owner, chat messages and admin uploads can disappear** when Vercel starts a fresh instance. Use Vercel for demos. For permanent data, run it on a server with a persistent disk, or move the database to a hosted one (roadmap). Without SMTP settings, OTP codes appear in Vercel's **Logs** tab.

---

## Configuration (`.env`)

| Variable | Required | What it does |
|---|---|---|
| `GEMINI_API_KEY` | ✅ | Your Google AI Studio key, used to call Gemma 4. |
| `GEMMA_MODELS` | – | Models to try, in order. Default: `gemma-4-26b-a4b-it,gemma-4-31b-it` |
| `OWNER_EMAIL` | ✅ | Email of the site owner. This account is always the **owner**. |
| `OWNER_PASSWORD` | ✅ (first run) | Creates the owner account on first start if it doesn't exist. Must meet the password rules. |
| `OWNER_NAME` | – | Display name for the owner. |
| `SECRET_KEY` | – (✅ on Vercel) | Signs login cookies. If empty, a random one is generated in `instance/secret_key`. |
| `CAMPUSMATE_DATA_DIR` | – | Where the database, uploads and cache are written. Default `instance/`; on Vercel `/tmp/campusmate`. |
| `SMTP_HOST` / `SMTP_PORT` | – | Mail server, `smtp.gmail.com` with `587` (STARTTLS) or `465` (SSL). |
| `SMTP_USER` / `SMTP_PASSWORD` | – | Gmail address and **App Password** for sending OTP codes. |
| `SMTP_FROM` | – | Sender shown on OTP emails. |

`.env` is listed in `.gitignore`, so **your keys and passwords are never committed**. Only `.env.example` (with blank values) is in the repository.

---

## Accounts, roles and admin access

| Role | Tag | Can do |
|---|---|---|
| **Owner** | 👑 pink-gold `OWNER` | Everything admins can, plus **Manage Admins** (give or remove admin access). There is exactly one owner, set by `OWNER_EMAIL`, and the role can't be removed or transferred from inside the app. |
| **Admin** | 🛡 amber `ADMIN` | Publish notices, announcements, assignments and study-material PDFs; **edit timetables, exam schedules and mess menus**; delete posts (except the owner's); remove chat messages and ban students from chat. |
| **Student** | grey `STUDENT` | View everything, use the AI features and post in the chat. Cannot publish or change content. |

- New sign-ups are always **students**. Admin access can only be given by the owner from **Manage Admins**.
- Every published item and chat message shows **Published by (name + role tag)** and the **publishing time** in the viewer's local time.
- Passwords must have **8+ characters with an uppercase letter, a lowercase letter, a number and a symbol**.
- "Keep me logged in" keeps the session for **30 days**.

---

## Email OTP

Login and sign-up both require a **6-digit one-time code** after the password step:
- valid for **10 minutes**, single use, with at most **5 attempts**
- a new code can be requested after **60 seconds**
- a new account is only created after its email is confirmed

**Turning on email delivery (Gmail):**
1. Turn on **2-Step Verification** on the sending Google account: https://myaccount.google.com/security
2. Create an **App Password**: https://myaccount.google.com/apppasswords
3. Run `python setup_email.py`. It asks for the address and App Password, tries port 587 and then 465, **sends a test email**, and saves the settings to `.env` only if the test succeeds.
4. Restart the server.

**Without email settings** (or on a network that blocks email), codes are **printed in the server terminal**, and the login page tells the user where to find them. This is fine for local demos. Configure SMTP for real use.

---

## Updating the campus data

All campus content is plain JSON in `data/`. Edit it and restart the server.

- **Timetables:** each section has `classes`, a list of `{day, start, end, code, label, faculty, group, type, room}`. Year 1 is generated by `tools/build_year1.py` from a line-by-line transcription of the official PDF.
- **Notices:** `data/notices.json`. Each notice has `years` (e.g. `[1]` or `["all"]`) and `sections` (e.g. `["A","B"]` or `["all"]`).
- **Assignments, exams and syllabus:** same `years` / `sections` targeting. `tools/build_docs.py` rebuilds the syllabus and assignment library from the source PDFs in `tools/source/`.
- **Hostel menus:** edit them on the Mess Menu page, or set defaults in `data/hostel.json` (`hostels.boys` / `hostels.girls`).
- **Day-to-day updates** don't need any file editing: admins use **Publish** for notices, assignments and PDFs, and the **✎ Edit** buttons on the Timetable, Exam Schedule and Mess Menu pages. Site edits are stored in the database and take priority over the JSON files.

---

## Security

- **Secrets:** API keys, the owner password and the Gmail App Password live only in `.env` (git-ignored). The Gemini key is only used on the server.
- **Passwords:** stored as **scrypt hashes** (Werkzeug). Strength rules are enforced on the server.
- **Brute-force protection:** 5 failed logins per email and IP lock that combination for 10 minutes. OTP codes allow 5 attempts.
- **OTP codes:** random 6 digits, stored only as an HMAC-SHA256 hash, compared in constant time, expiring after 10 minutes, single use.
- **Sessions:** signed, `HttpOnly`, `SameSite=Lax` cookies.
- **CSRF:** every state-changing API call must include a custom `X-CampusMate` header, which other websites cannot send.
- **Authorization** is checked on the server for every route (`login_required`, `admin_required`, `owner_required`). Hiding a button is never the only protection.
- **Uploads:** PDF only, checked by extension *and* the `%PDF-` file signature. Files are stored with random names outside `static/` and served only to logged-in users. Maximum 12 MB.
- **XSS:** all user and AI text is inserted with `textContent`, never `innerHTML`.
- **Rate limits:** chat messages (one per 1.5 seconds per user) and OTP resends.

---

## Testing

During development, every feature was tested end to end with a stand-in for the AI call (so tests don't use API quota), plus browser tests with Playwright at desktop and phone widths, in light and dark mode. Live Gemma 4 access was verified with the real API key. Scenarios covered:

- **Notice Explainer:** clear deadline, multiple dates, a vague notice with missing information, a long notice, empty input, too-long input, missing API key, quota exhausted (saved fallback), model fallback from 26B to 31B.
- **Study Planner:** missing names or dates, past exam dates, hours out of range, plans longer than 14 days (trimmed with a note).
- **Ask CampusMate:** context contains only the student's year, section and group data, with no other section's notices or another group's labs.
- **Accounts:** password rules, duplicate email, wrong password, login lockout, OTP wrong / expired / reused / too many tries / resend cooldown, owner promotion from `.env`, owner can't be demoted, students and admins blocked from owner actions, admin access revoked instantly.
- **Publishing:** fake PDF rejected, targeting by year and section, delete permissions, PDFs blocked for logged-out users.
- **Chat:** HTML or script in messages shown as plain text, rate limit, admin removal.
- **Timetable:** year 1 data checked against the PDF, page by page.

---

## Limitations and honest notes

- **What's included:** the official 1st-year timetable, the official 1st-year curriculum (syllabus) and the three real MA-111 assignment PDFs. Everything else (notices, exams, mess menus, other years' timetables) starts empty and is added by admins on the site. No made-up sample data is shown.
- Site edits (timetables, exams, menus), accounts, posts and chat are stored in SQLite. On Vercel that database is temporary and can reset; use a server with a persistent disk or a hosted database for permanent data.
- The curriculum PDF gives Hindi text for HS-103 on pages 13–14. That text couldn't be extracted cleanly, so it's available in the PDF only. B.Arch (Section Z) courses aren't in the B.Tech curriculum PDF.
- **AI can make mistakes.** Always confirm important details with the issuing office.
- The Gemini API **free tier has low rate limits** for Gemma models.
- Campus chat uses polling every 3 seconds rather than WebSockets.

## Roadmap

- Self-hosted Gemma 4 (e.g. via Ollama) so no data leaves campus
- Calendar export (.ics) for deadlines and exams
- Push or email reminders before deadlines
- More Indian languages for the Notice Explainer
- Change-password and forgot-password screens

---

## License

**Source code:** [MIT License](LICENSE) © 2026 Jatin Kumar. You may use, modify and distribute the code.

**Not covered by the MIT license:**
- **Gemma 4:** the model weights are released by Google under the **Apache 2.0** license. This app calls Gemma through Google's hosted **Gemini API**, whose use is governed by Google's API terms of service. CampusMate does not include or redistribute model weights.
- **Institute documents:** the timetable, curriculum and assignment PDFs in `static/docs/` (and the data transcribed from them) belong to **NIT Hamirpur** and their authors. They're included for demonstration by a student of the institute. They are not open-source and are not covered by the MIT license.
- **Python dependencies** (Flask, google-genai, python-dotenv, Pillow) are under their own open-source licenses.

## Credits

- **Built by:** Jatin Kumar (NIT Hamirpur), for Hacktoberfest 2026
- **AI model:** Gemma 4 by Google DeepMind, via the Gemini API
- **Data sources:** Office of Dean Academic (1st-year timetable, 2026-27), NIT Hamirpur first-year curriculum (NEP-2020), MA-111 course assignments
- Developed with help from AI coding assistants
