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
10. [Google Sign-In](#google-sign-in)
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

### 📅 Academic Calendar
- One calendar for **everyone**: a month view (holidays highlighted, Sundays in red, today outlined) and a list of upcoming dates with countdowns. Tap any day to see what's on it.
- **Year-wise:** each entry is for all years or only for the years ticked (e.g. a holiday just for 1st year). Students see the entries for the year picked in the sidebar plus the all-years ones.
- Entry types: **Holiday**, **Exam**, **Event** and **Semester** (e.g. classes start / end). Entries can span several days (e.g. a Diwali break) and carry a short note.
- **Only admins and the owner can add, edit or remove dates** from the site. The page shows *Last edited by (name) · time*. It starts empty until confirmed holidays are added.
- Holidays flow through the app: the dashboard skips holidays when showing "today's classes", My Attendance shows no regular classes on a holiday for your year (and classes marked on it before the holiday was added stop counting), and Ask CampusMate can answer *"When is the next holiday?"*.

### ✓ My Attendance
- Every student tracks their **own** attendance for each subject in their timetable. It's private and **saved to their account**, so it follows them across devices.
- Pick a date (today by default): your classes for that day appear with **Present / Absent / Cancelled** buttons. Cancelled classes don't count. Add **extra or rescheduled classes**, or subjects not in the timetable (e.g. NCC).
- **Starting count** lets you enter classes held before you began tracking.
- Each subject card shows the percentage, a progress bar with your target marked, and plain advice: *"You can miss 2 more classes and stay at 75%"* or *"Attend the next 4 classes in a row to reach 75%"*. The target is adjustable (60–90%).
- The dashboard shows your overall attendance and your weakest subjects; Ask CampusMate can answer *"In which subjects is my attendance low?"*.
- This is a personal tracker, not the official record kept by teachers.

### ❒ Syllabus
- The **official first-year curriculum (NEP-2020)**: the full PDF, the teaching scheme (page 6) and the evaluation scheme (page 28).
- One card for each subject in your timetable, showing the real course name, semester, L-P and credits, weekly hours, and faculty. Each card has:
  - **📄 Original PDF**: just that subject's page, cut from the curriculum
  - **📝 Read as text**: the full syllabus as text
  - **✦ Summarise**: the AI explains it unit by unit
- An expandable "Other courses this year" list with the other semester's subjects.
- Other years show "not added yet". Admins can publish syllabus PDFs under Study Material.

### 📄 Study Material
- Its own page in the menu, listing the notes and PDFs admins publish as **Study material / PDF**.
- Only shows material for the student's year and section (none ticked when publishing = everyone).
- Subject chips (e.g. MA-111) filter the list when there is material for more than one subject.
- Admins get **Edit** and **Delete** on each item and a button that opens the Publish page with Study material selected.

### ✦ Notice Explainer
- Paste a notice or upload a photo. Gemma 4 returns the title, issuer, audience, a summary, urgency, **deadlines with the exact source words**, required documents, a **checklist**, key facts and a **"Missing or unclear"** list, plus a Hindi version.
- Results are saved to your history and their deadlines appear on the Dashboard.

### ◷ Study Planner
- Enter subjects, exam dates, topics, difficulty and daily hours. Gemma 4 builds a **day-by-day timetable (up to 14 days)** with revision before each exam and regular breaks, and warns you honestly if there isn't enough time.

### ⌂ Mess Menu (Boys' and Girls' hostels)
- **Separate weekly menus** for the Boys' Hostel and the Girls' Hostel. Students pick their hostel once (remembered), and today's row is highlighted.
- **Admins edit the menu and meal timings from the site.** The menu shows *Last edited by (name) · time*.
- The Dashboard shows today's menu for your hostel. Ask CampusMate can answer "What's for dinner today?"

### 📰 College Updates and 🛏 Hostel Updates
- Short text news posts that everyone signed in can read. **Journalists**, admins, co-owners and the owner can post.
- **Hostel Updates** are tagged with a year (1st to 4th, or all years) and a hostel (Boys, Girls or both). Readers pick a year and Boys/Girls to see the updates meant for them; updates for all years or both hostels show under every filter.
- The author of an update and admins+ can edit or delete it (only the owner can touch the owner's or a co-owner's updates). Edited updates show when they were edited. All of this is checked on the server.

### 💬 Campus Chat
- A public chat for all logged-in users. It refreshes every 3 seconds.
- Every message shows the sender, their role tag and the time.
- **Admin and owner messages have coloured banners and name tags.** Admins can remove messages.
- **Chat bans:** admins can ban a student from the chat for 1 hour, 1 day, 7 days or permanently. The owner and co-owners can ban an admin, only the owner can ban a co-owner, and the owner can't be banned. A banned user can still read but can't post. A "Banned from chat" list with **Unban** is shown to admins.
- **Private channels:** admins and the owner can create private channels and choose who is in them. Only members can see a channel, its member list and its messages (checked on the server; anyone else gets "not found"). Admins in a channel can add or remove members, remove messages or delete the channel, and any member can leave. Chat bans apply in private channels too.

### ⬆ Publish (admins) and 🛡 Manage Admins (owner and co-owners)
- Admins publish **notices, announcements, assignments and study-material PDFs**, each targeted by year and section. Everything shows *Published by* and the *publishing time*.
- Assignments, study material and syllabus subjects can be **edited** (title, text, due date, marks, and replacing the PDF) or **deleted** right on the Assignments and Syllabus pages. This includes the built-in MA-111 assignments and curriculum subjects; those edits are saved in the database, so the JSON files stay unchanged.
- Anything published by mistake can be **deleted** from the Notice Board, Assignments, Syllabus or Publish pages. Admins can delete any post except the owner's or a co-owner's; co-owners can delete any post except the owner's or another co-owner's; the owner can delete everything.
- The **owner** and **co-owners** can give or remove admin access.
- **Co-owners** have the same powers as the owner everywhere else (publishing, calendar edits, private channels, chat moderation), but only the owner can make or remove a co-owner, and a co-owner can never ban, remove or demote the owner. Co-owners show a gold "Co-owner" tag.

### Also
- **Sign in with Google**, and stay logged in for 30 days.
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
 Landing + Google button ─── /api/login (ID token) ─▶ accounts.py ── SQLite (instance/) 
 Dashboard, Timetable, Notice Board,                  │  users, posts, chat           
 Assignments, Exams, Syllabus, Hostel ◀── page + JSON ┤
 Ask CampusMate ───────────── /api/ask ─────────────▶ app.py ── builds grounded context ──▶ Gemini API
 Notice Explainer ─────────── /api/notice ──────────▶        ── strict JSON prompts     ──▶ (Gemma 4)
 Study Planner ────────────── /api/plan ───────────▶         
 Campus Chat ─────────────── /api/chat (poll 3s) ──▶ accounts.py
 Publish / Manage Admins ──── /api/posts, /api/users ▶ (admin / owner only)
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
├── accounts.py             # Google login, roles, admin publishing, uploads, campus chat, bans
├── db.py                   # Database layer: SQLite locally, Postgres when DATABASE_URL is set
├── requirements.txt
├── .env.example            # Template for your secrets (copy to .env)
├── .gitignore              # Keeps .env, instance/, venv/ out of Git
├── LICENSE                 # MIT (code only, see License section)
├── templates/
│   ├── index.html          # The single-page app (all sections)
│   └── landing.html        # Landing page with the Google sign-in button
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
│   ├── academic_calendar.json # Academic calendar defaults (holidays edited from the site)
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

Open **http://127.0.0.1:5000**. The terminal prints a startup block showing the database path, the owner email it loaded and whether Google login is on.

> **Windows tip:** run `python app.py` from the terminal *after* activating `venv`. The prompt should start with `(venv)`. If PowerShell refuses to activate it, run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

### Deploying to Vercel

The repo includes `vercel.json`. In Vercel → *Project → Settings → Environment Variables*, add `GEMINI_API_KEY`, `GOOGLE_CLIENT_ID`, `OWNER_EMAIL`, `OWNER_NAME` and a long random `SECRET_KEY`. Add your Vercel URL to the Google client's *Authorized JavaScript origins*.

**Connect a permanent database (required on Vercel).** Vercel's disk is temporary, so without a database, accounts, chat, posts and edits disappear when the app restarts.
1. In your Vercel project open **Storage → Create Database → Neon (Postgres)** (free plan) and connect it to the project. Vercel adds `DATABASE_URL` (or `POSTGRES_URL`) automatically. Any other Postgres works too: set `DATABASE_URL=postgresql://…`.
2. **Redeploy.** Tables are created automatically on first start; the startup log says `Database: Postgres (DATABASE_URL) - permanent`.

Uploaded PDFs are stored in the database too. Vercel limits a single request to about 4.5 MB, so keep uploads under that size there.

---

## Configuration (`.env`)

| Variable | Required | What it does |
|---|---|---|
| `GEMINI_API_KEY` | ✅ | Your Google AI Studio key, used to call Gemma 4. |
| `GEMMA_MODELS` | – | Models to try, in order. Default: `gemma-4-26b-a4b-it,gemma-4-31b-it` |
| `GOOGLE_CLIENT_ID` | ✅ | OAuth client ID for Google Sign-In (see [Google Sign-In](#google-sign-in)). Login is disabled without it. |
| `OWNER_EMAIL` | ✅ | Google account email of the site owner. Whoever signs in with it is always the **owner**. |
| `OWNER_NAME` | – | Display name for the owner. |
| `SECRET_KEY` | – (✅ on Vercel) | Signs login cookies. If empty, a random one is generated in `instance/secret_key`. |
| `DATABASE_URL` | ✅ on Vercel | Postgres connection string (e.g. from Neon). If set, all data (accounts, posts, PDFs, chat, edits) is stored there permanently. If empty, a local SQLite file is used. `POSTGRES_URL` also works. |
| `CAMPUSMATE_DATA_DIR` | – | Where the SQLite file and cache are written. Default `instance/`; on Vercel `/tmp/campusmate`. |

`.env` is listed in `.gitignore`, so **your keys are never committed**. Only `.env.example` (with blank values) is in the repository.

---

## Accounts, roles and admin access

| Role | Tag | Can do |
|---|---|---|
| **Owner** | 👑 pink-gold `OWNER` | Everything admins can, plus **Manage Admins** (give or remove admin access). There is exactly one owner, set by `OWNER_EMAIL`, and the role can't be removed or transferred from inside the app. |
| **Admin** | 🛡 amber `ADMIN` | Publish notices, announcements, assignments and study-material PDFs; **edit timetables, exam schedules and mess menus**; delete posts (except the owner's); remove chat messages and ban students from chat. |
| **Journalist** | blue `JOURNALIST` | Everything a student can, plus post, edit and delete their own **College Updates** and **Hostel Updates**. Admins and above make or remove journalists from the **Journalists** page. |
| **Student** | grey `STUDENT` | View everything, use the AI features and post in the chat. Cannot publish or change content. |

- Everyone signs in with their Google account. A first sign-in creates a **student** account. Admin access can only be given by the owner from **Manage Admins**.
- Every published item and chat message shows **Published by (name + role tag)** and the **publishing time** in the viewer's local time.
- A login lasts **30 days**.

---

## Google Sign-In

Students and admins sign in with **Sign in with Google** on the landing page. The server checks Google's ID token (signature, expiry, that it was issued for this site's client ID, and that the email is verified) before creating the session.

**Setting it up:**
1. Open https://console.cloud.google.com/apis/credentials and create an **OAuth client ID** of type **Web application**.
2. Under **Authorized JavaScript origins**, add every address the site runs on, for example `http://localhost:5000`, `http://127.0.0.1:5000` and your Vercel URL.
3. Copy the client ID into `GOOGLE_CLIENT_ID` in `.env` (or Vercel's environment variables) and restart the server.

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

- **Secrets:** API keys live only in `.env` (git-ignored). The Gemini key is only used on the server.
- **Login:** no passwords are stored. Google ID tokens are verified on the server against `GOOGLE_CLIENT_ID`, and only verified emails can sign in.
- **Sessions:** signed, `HttpOnly`, `SameSite=Lax` cookies.
- **CSRF:** every state-changing API call must include a custom `X-CampusMate` header, which other websites cannot send.
- **Authorization** is checked on the server for every route (`login_required`, `admin_required`, `owner_required`). Hiding a button is never the only protection.
- **Uploads:** PDF only, checked by extension *and* the `%PDF-` file signature. Files are stored with random names outside `static/` and served only to logged-in users. Maximum 12 MB.
- **XSS:** all user and AI text is inserted with `textContent`, never `innerHTML`.
- **Rate limits:** chat messages (one per 1.5 seconds per user).

---

## Testing

During development, every feature was tested end to end with a stand-in for the AI call (so tests don't use API quota), plus browser tests with Playwright at desktop and phone widths, in light and dark mode. Live Gemma 4 access was verified with the real API key. Scenarios covered:

- **Notice Explainer:** clear deadline, multiple dates, a vague notice with missing information, a long notice, empty input, too-long input, missing API key, quota exhausted (saved fallback), model fallback from 26B to 31B.
- **Study Planner:** missing names or dates, past exam dates, hours out of range, plans longer than 14 days (trimmed with a note).
- **Ask CampusMate:** context contains only the student's year, section and group data, with no other section's notices or another group's labs.
- **Accounts:** Google token checks (missing, invalid, unverified email, missing client ID), owner promotion from `.env`, owner can't be demoted, students and admins blocked from owner actions, admin access revoked instantly.
- **Publishing:** fake PDF rejected, targeting by year and section, delete permissions, PDFs blocked for logged-out users.
- **Chat:** HTML or script in messages shown as plain text, rate limit, admin removal.
- **Timetable:** year 1 data checked against the PDF, page by page.

---

## Limitations and honest notes

- **What's included:** the official 1st-year timetable, the official 1st-year curriculum (syllabus) and the three real MA-111 assignment PDFs. Everything else (notices, exams, mess menus, other years' timetables) starts empty and is added by admins on the site. No made-up sample data is shown.
- Accounts, posts, uploaded PDFs, chat and site edits are stored in **Postgres** when `DATABASE_URL` is set (recommended, required on Vercel), otherwise in a local **SQLite** file.
- The curriculum PDF gives Hindi text for HS-103 on pages 13–14. That text couldn't be extracted cleanly, so it's available in the PDF only. B.Arch (Section Z) courses aren't in the B.Tech curriculum PDF.
- **AI can make mistakes.** Always confirm important details with the issuing office.
- The Gemini API **free tier has low rate limits** for Gemma models.
- Campus chat uses polling every 3 seconds rather than WebSockets.

## Roadmap

- Self-hosted Gemma 4 (e.g. via Ollama) so no data leaves campus
- Calendar export (.ics) for holidays, deadlines and exams
- Push or email reminders before deadlines
- More Indian languages for the Notice Explainer

---

## License

**Source code:** [MIT License](LICENSE) © 2026 Jatin Kumar. You may use, modify and distribute the code.

**Not covered by the MIT license:**
- **Gemma 4:** the model weights are released by Google under the **Apache 2.0** license. This app calls Gemma through Google's hosted **Gemini API**, whose use is governed by Google's API terms of service. CampusMate does not include or redistribute model weights.
- **Institute documents:** the timetable, curriculum and assignment PDFs in `static/docs/` (and the data transcribed from them) belong to **NIT Hamirpur** and their authors. They're included for demonstration by a student of the institute. They are not open-source and are not covered by the MIT license.
- **Python dependencies** (Flask, google-genai, python-dotenv, Pillow) are under their own open-source licenses.

## Credits

- **Built by:** Jatin Kumar (NIT Hamirpur), for Hacktoberfest 2026 & co-developed by Mitesh Kini (NIT Hampirpur)
- **AI model:** Gemma 4 by Google DeepMind, via the Gemini API
- **Data sources:** Office of Dean Academic (1st-year timetable, 2026-27), NIT Hamirpur first-year curriculum (NEP-2020), MA-111 course assignments
- Developed with help from AI coding assistants
