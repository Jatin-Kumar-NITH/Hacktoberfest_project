// CampusMate AI front end. AI text is always inserted with textContent, never innerHTML.

const $ = (id) => document.getElementById(id);
const ME = JSON.parse(document.getElementById("me-data").textContent);
const IS_ADMIN = ME.role === "admin" || ME.role === "coowner" || ME.role === "owner";
const IS_OWNER_LEVEL = ME.role === "owner" || ME.role === "coowner";
const RANK = { owner: 4, coowner: 3, admin: 2, journalist: 1, user: 0 };
// may I act against (ban, remove, delete posts of) someone with this role? mirrors outranks() on the server
const outranks = (role) => ME.role === "owner" || (RANK[ME.role] || 0) > (RANK[role] || 0);
// every POST to our API carries a custom header (CSRF protection); expired sessions go back to login
const _fetch = window.fetch.bind(window);
window.fetch = async (url, opts = {}) => {
  if ((opts.method || "GET").toUpperCase() !== "GET") opts.headers = { ...(opts.headers || {}), "X-CampusMate": "1" };
  const res = await _fetch(url, opts);
  if (res.status === 401 && String(url).startsWith("/api/") && !String(url).startsWith("/api/login")) location.href = "/login";
  return res;
};
const fmtTime = (iso) => new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
const localDate = (iso) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 10); };
const ROLE_LABEL = { owner: "Owner", coowner: "Co-owner", admin: "Admin", journalist: "Journalist", user: "Student" };
const pdfHref = (p) => (p.startsWith("/") ? p : `/static/${p}`);
const SAMPLES = JSON.parse($("samples-data").textContent);

// ---------------------------------------------------------- storage (per browser)
const store = {
  get(key, fallback) {
    try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; }
    catch { return fallback; }
  },
  set(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ }
  },
};
let history = store.get("cm_history", []); // [{id, at, result, model, done: [bool]}]
let currentId = null;

// ---------------------------------------------------------- routing
function route() {
  const page = (location.hash || "#dashboard").slice(1);
  const valid = ["dashboard", "board", "timetable", "assignments", "exams", "calendar", "attendance", "results", "syllabus", "notice", "planner", "hostel", "college-updates", "hostel-updates", "chat", "publish", "admins", "journalists", "about"].filter((v) => $(`page-${v}`));
  const p = valid.includes(page) ? page : "dashboard";
  valid.forEach((v) => ($(`page-${v}`).hidden = v !== p));
  document.querySelectorAll(".nav a").forEach((a) => a.classList.toggle("active", a.dataset.page === p));
  const activeLink = document.querySelector(`.nav a[data-page="${p}"]`);
  $("menuPage").textContent = activeLink ? activeLink.lastElementChild.textContent.trim() : "Menu";
  document.body.dataset.page = p;
  setMenu(false);
  if (p === "dashboard") renderDashboard();
  if (p === "board") renderBoard();
  if (p === "timetable") renderTimetable();
  if (p === "assignments") renderAssignments();
  if (p === "exams") renderExams();
  if (p === "calendar") renderCalendar();
  if (p === "attendance") renderAttendance();
  if (p === "results") renderResultsPage();
  if (p === "syllabus") renderSyllabus();
  if (p === "hostel") renderHostel();
  if (p === "chat") startChat(); else stopChat();
  if (p === "publish") renderPublish();
  if (p === "admins") loadUsers();
  if (p === "college-updates") loadNews("college");
  if (p === "hostel-updates") loadNews("hostel");
  if (p === "journalists") loadJournalists();
  window.scrollTo(0, 0);
}
window.addEventListener("hashchange", route);

// On phones the sidebar collapses to a top bar; the menu button opens it as a full-screen menu.
function setMenu(open) {
  document.querySelector(".sidebar").classList.toggle("open", open);
  document.body.classList.toggle("menu-open", open);
  $("menuBtn").setAttribute("aria-expanded", String(open));
  $("menuBtn").setAttribute("aria-label", open ? "Close menu" : "Open menu");
}
$("menuBtn").addEventListener("click", () => setMenu(!document.querySelector(".sidebar").classList.contains("open")));
document.querySelectorAll(".nav a").forEach((a) => a.addEventListener("click", () => setMenu(false)));
document.addEventListener("keydown", (e) => { if (e.key === "Escape") setMenu(false); });

// Editor tables stack into one card per row on phones; each cell gets its column name as a label.
document.querySelectorAll(".ed-table").forEach((table) => {
  const heads = [...table.querySelectorAll("thead th")].map((th) => th.textContent.trim());
  const label = () => table.querySelectorAll("tbody tr").forEach((tr) =>
    [...tr.children].forEach((td, i) => { if (heads[i]) td.dataset.label = heads[i]; }));
  new MutationObserver(label).observe(table.tBodies[0], { childList: true });
});

// ---------------------------------------------------------- helpers
function el(tag, cls, text) {
  const n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text !== undefined && text !== null) n.textContent = text;
  return n;
}
function toggleBlock(id, items) { $(id).hidden = !items || items.length === 0; }
const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
function todayISO() { const d = new Date(); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 10); }
function daysUntil(iso) { return Math.round((new Date(iso) - new Date(todayISO())) / 86400000); }

// ---------------------------------------------------------- notice input
let selectedFile = null;
let sampleId = "";

document.querySelectorAll(".chip[data-sample]").forEach((chip) => {
  chip.addEventListener("click", () => {
    const s = SAMPLES.find((x) => x.id === chip.dataset.sample);
    $("noticeText").value = s.text;
    sampleId = s.id;
    clearFile();
    document.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c === chip));
    updateCount();
  });
});

$("noticeText").addEventListener("input", () => {
  sampleId = "";
  document.querySelectorAll(".chip").forEach((c) => c.classList.remove("active"));
  updateCount();
});
function updateCount() { $("charCount").textContent = `${$("noticeText").value.length.toLocaleString()} characters`; }

$("noticeFile").addEventListener("change", (e) => {
  selectedFile = e.target.files[0] || null;
  $("fileLabel").textContent = selectedFile ? `${selectedFile.name} ✕` : "or upload a photo";
});
$("fileLabel").addEventListener("click", (e) => {
  if (selectedFile) { e.preventDefault(); clearFile(); }
});
function clearFile() { selectedFile = null; $("noticeFile").value = ""; $("fileLabel").textContent = "or upload a photo"; }

function showN(state) {
  $("nEmpty").hidden = state !== "empty";
  $("nLoading").hidden = state !== "loading";
  $("nResult").hidden = state !== "result";
  $("nError").hidden = state !== "error";
}

$("explainBtn").addEventListener("click", async () => {
  const text = $("noticeText").value.trim();
  if (!text && !selectedFile) {
    $("nError").textContent = "Please paste a notice or pick a sample first.";
    showN("error");
    return;
  }
  const form = new FormData();
  form.append("text", text);
  form.append("hindi", $("hindiToggle").checked ? "1" : "0");
  if (selectedFile) form.append("file", selectedFile);
  if (sampleId) form.append("sample_id", sampleId);

  $("explainBtn").disabled = true;
  showN("loading");
  try {
    const res = await fetch("/api/notice", { method: "POST", body: form });
    const data = await res.json().catch(() => ({ error: "The server sent an unreadable reply." }));
    if (!res.ok || data.error) throw new Error(data.error || "Something went wrong.");
    const entry = { id: Date.now().toString(36), at: new Date().toISOString(), result: data.result,
                    model: data.model, done: data.result.checklist.map(() => false) };
    history.unshift(entry);
    history = history.slice(0, 20);
    store.set("cm_history", history);
    renderNotice(entry, data);
    showN("result");
  } catch (err) {
    $("nError").textContent = err.message;
    showN("error");
  } finally {
    $("explainBtn").disabled = false;
  }
});

// ---------------------------------------------------------- notice result
document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    document.querySelectorAll("[data-pane]").forEach((p) => (p.hidden = p.dataset.pane !== tab.dataset.tab));
  });
});

function renderNotice(entry, live) {
  const r = entry.result;
  currentId = entry.id;

  $("nCached").hidden = !(live && live.cached);
  if (live && live.cached) {
    $("nCached").textContent = `Live AI unavailable right now (${live.notice}) Showing a saved result from an earlier live Gemma run on ${live.cached_at}.`;
  }

  $("rTitle").textContent = r.title;
  $("rMetaLine").textContent = [r.issued_by && `From: ${r.issued_by}`, r.audience && `For: ${r.audience}`]
    .filter(Boolean).join("  ·  ") || "Issuer and audience not stated";
  $("rUrgency").className = `urgency ${r.urgency}`;
  $("rUrgency").textContent = `${r.urgency.toUpperCase()} PRIORITY`;
  $("rSummary").textContent = r.summary;
  $("rUrgencyReason").textContent = r.urgency_reason;

  const tl = $("rDeadlines"); tl.replaceChildren();
  r.deadlines.forEach((d) => {
    const li = el("li");
    li.appendChild(el("span", "tl-date", d.date || "Date unclear"));
    const body = el("div");
    const what = el("div", "", d.what || "");
    if (d.certain === false) what.appendChild(el("span", "verify", "verify"));
    body.appendChild(what);
    if (d.source_text) body.appendChild(el("span", "quote", `“${d.source_text}”`));
    li.appendChild(body);
    tl.appendChild(li);
  });
  toggleBlock("bDeadlines", r.deadlines);

  const cl = $("rChecklist"); cl.replaceChildren();
  r.checklist.forEach((c, i) => {
    const li = el("li");
    const box = el("input"); box.type = "checkbox"; box.checked = !!entry.done[i];
    li.classList.toggle("done", box.checked);
    box.addEventListener("change", () => {
      li.classList.toggle("done", box.checked);
      entry.done[i] = box.checked;
      store.set("cm_history", history);
      updateProgress(entry);
    });
    const body = el("div");
    body.appendChild(el("span", "step", c.step));
    body.appendChild(el("span", `pill ${c.priority}`, c.priority));
    const why = [c.why, c.deadline && `By ${c.deadline}`].filter(Boolean).join(" · ");
    if (why) body.appendChild(el("div", "why", why));
    li.append(box, body);
    cl.appendChild(li);
  });
  toggleBlock("bChecklist", r.checklist);
  updateProgress(entry);

  const docs = $("rDocs"); docs.replaceChildren();
  r.required_documents.forEach((d) => docs.appendChild(el("li", "", d)));
  toggleBlock("bDocs", r.required_documents);

  const facts = $("rFacts"); facts.replaceChildren();
  r.key_facts.forEach((f) => {
    const tr = el("tr");
    tr.appendChild(el("td", "", f.label));
    const td = el("td", "", f.value);
    if (f.source_text) td.appendChild(el("span", "quote", `“${f.source_text}”`));
    tr.appendChild(td);
    facts.appendChild(tr);
  });
  toggleBlock("bFacts", r.key_facts);

  const miss = $("rMissing"); miss.replaceChildren();
  r.missing_or_unclear.forEach((m) => miss.appendChild(el("li", "", m)));
  toggleBlock("bMissing", r.missing_or_unclear);

  const h = r.hindi;
  $("hiTab").hidden = !h;
  if (h) {
    $("hSummary").textContent = h.summary || "";
    const ol = $("hChecklist"); ol.replaceChildren();
    (h.checklist || []).forEach((s) => ol.appendChild(el("li", "", s)));
  }
  document.querySelector('.tab[data-tab="en"]').click();

  const when = new Date(entry.at).toLocaleString();
  $("rFoot").textContent = live && live.cached
    ? `Saved result · ${entry.model}`
    : live ? `Live result · ${entry.model} · ${live.seconds}s` : `From your history · ${entry.model} · ${when}`;
}

function updateProgress(entry) {
  const total = entry.done.length, done = entry.done.filter(Boolean).length;
  $("rProgress").textContent = total ? `${done}/${total} done` : "";
}

// ---------------------------------------------------------- dashboard
function renderDashboard() {
  const hour = new Date().getHours();
  $("greeting").textContent = `${hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening"} 👋`;

  // deadlines from all explained notices, upcoming only, soonest first
  const today = todayISO();
  const upcoming = [];
  history.forEach((h) => h.result.deadlines.forEach((d) => {
    if (d.date_iso && d.date_iso >= today) upcoming.push({ ...d, from: h.result.title, id: h.id });
  }));
  upcoming.sort((a, b) => a.date_iso.localeCompare(b.date_iso));

  const list = $("dashDeadlines"); list.replaceChildren();
  upcoming.slice(0, 6).forEach((d) => {
    const li = el("li");
    const [, m, day] = d.date_iso.split("-");
    const left = daysUntil(d.date_iso);
    const badge = el("div", `date-badge${left <= 3 ? " soon" : ""}`, String(Number(day)));
    badge.appendChild(el("small", "", MONTHS[Number(m) - 1]));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", d.what));
    txt.appendChild(el("span", "", `${d.from} · ${left === 0 ? "today" : left === 1 ? "tomorrow" : `in ${left} days`}`));
    li.append(badge, txt);
    list.appendChild(li);
  });
  $("dashDeadlinesEmpty").hidden = upcoming.length > 0;

  const openTasks = history.reduce((n, h) => n + h.done.filter((x) => !x).length, 0);
  $("statDeadlines").textContent = upcoming.length;
  $("statTasks").textContent = openTasks;
  $("statNotices").textContent = history.length;

  const recent = $("dashRecent"); recent.replaceChildren();
  history.slice(0, 5).forEach((h) => {
    const li = el("li");
    li.appendChild(el("span", `urgency ${h.result.urgency}`, h.result.urgency[0].toUpperCase()));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", h.result.title));
    const done = h.done.filter(Boolean).length;
    txt.appendChild(el("span", "", `${new Date(h.at).toLocaleDateString()} · ${done}/${h.done.length} tasks done`));
    li.appendChild(txt);
    li.addEventListener("click", () => { location.hash = "#notice"; renderNotice(h, null); showN("result"); });
    recent.appendChild(li);
  });
  $("dashRecentEmpty").hidden = history.length > 0;

  renderDashPlan();
  renderDashHostel();
  renderDashSection();
  renderDashAttendance();
  renderDashHolidays();
}

$("clearHistory").addEventListener("click", () => {
  history = []; store.set("cm_history", history); renderDashboard(); showN("empty");
});

// ========================================================== STUDY PLANNER
const DAY_NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
let planSample = false;

function addDays(n) { const d = new Date(); d.setDate(d.getDate() + n); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 10); }
function niceDate(iso) { const d = new Date(iso + "T00:00:00"); return `${DAY_NAMES[d.getDay()].slice(0, 3)}, ${d.getDate()} ${MONTHS[d.getMonth()]}`; }

function subjectRow(s = {}) {
  const wrap = el("div", "subject");
  const top = el("div", "subject-top");
  const name = el("input"); name.type = "text"; name.placeholder = "Subject, e.g. Data Structures"; name.value = s.name || ""; name.className = "s-name";
  const exam = el("input"); exam.type = "date"; exam.min = todayISO(); exam.value = s.exam_date || ""; exam.className = "s-date"; exam.title = "Exam date";
  const rm = el("button", "remove", "×"); rm.title = "Remove subject";
  rm.addEventListener("click", () => { wrap.remove(); planSample = false; });
  top.append(name, exam, rm);
  const bottom = el("div", "subject-bottom");
  const topics = el("textarea"); topics.placeholder = "Topics (comma separated)"; topics.value = s.topics || ""; topics.className = "s-topics"; topics.rows = 1;
  const diff = el("select"); diff.className = "s-diff";
  ["easy", "medium", "hard"].forEach((v) => { const o = el("option", "", v[0].toUpperCase() + v.slice(1)); o.value = v; diff.appendChild(o); });
  diff.value = s.difficulty || "medium";
  bottom.append(topics, diff);
  wrap.append(top, bottom);
  wrap.addEventListener("input", () => { planSample = false; });
  return wrap;
}

function resetSubjects(list) {
  $("subjects").replaceChildren();
  list.forEach((s) => $("subjects").appendChild(subjectRow(s)));
}
resetSubjects([{}, {}]);

$("addSubject").addEventListener("click", () => {
  if ($("subjects").children.length >= 8) return;
  $("subjects").appendChild(subjectRow());
});

$("fillSample").addEventListener("click", () => {
  resetSubjects([
    { name: "Data Structures", exam_date: addDays(5), topics: "Linked lists, stacks, queues, trees, graphs, sorting", difficulty: "hard" },
    { name: "Engineering Mathematics III", exam_date: addDays(7), topics: "Laplace transform, Fourier series, PDEs", difficulty: "medium" },
    { name: "Digital Electronics", exam_date: addDays(9), topics: "K-maps, flip-flops, counters, multiplexers", difficulty: "easy" },
  ]);
  $("hours").value = 4; $("startTime").value = "18:00";
  planSample = true;
});

function showP(state) {
  $("pEmpty").hidden = state !== "empty";
  $("pLoading").hidden = state !== "loading";
  $("pResult").hidden = state !== "result";
  $("pError").hidden = state !== "error";
}

$("planBtn").addEventListener("click", async () => {
  const subjects = [...document.querySelectorAll(".subject")].map((w) => ({
    name: w.querySelector(".s-name").value.trim(),
    exam_date: w.querySelector(".s-date").value,
    topics: w.querySelector(".s-topics").value.trim(),
    difficulty: w.querySelector(".s-diff").value,
  })).filter((s) => s.name || s.exam_date);

  const problem = !subjects.length ? "Add at least one subject with its exam date, or click Fill sample."
    : subjects.find((s) => !s.name) ? "Every subject needs a name."
    : subjects.find((s) => !s.exam_date) ? `Pick an exam date for ${subjects.find((s) => !s.exam_date).name}.` : "";
  if (problem) { $("pError").textContent = problem; showP("error"); return; }

  $("planBtn").disabled = true;
  showP("loading");
  try {
    const res = await fetch("/api/plan", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ subjects, hours: $("hours").value, start: $("startTime").value, sample_id: planSample ? "demo" : "" }),
    });
    const data = await res.json().catch(() => ({ error: "The server sent an unreadable reply." }));
    if (!res.ok || data.error) throw new Error(data.error || "Something went wrong.");
    store.set("cm_plan", { result: data.result, model: data.model, at: new Date().toISOString() });
    renderPlan(data.result, data);
    showP("result");
  } catch (err) {
    $("pError").textContent = err.message;
    showP("error");
  } finally {
    $("planBtn").disabled = false;
  }
});

function sessionRow(s) {
  const row = el("div", `sess ${s.type}`);
  row.appendChild(el("div", "sess-time", s.time || ""));
  const body = el("div");
  body.appendChild(el("div", "sess-subj", s.subject || ""));
  if (s.task) body.appendChild(el("div", "sess-task", s.task));
  row.appendChild(body);
  return row;
}

function renderPlan(r, live) {
  $("pCached").hidden = !(live && live.cached);
  if (live && live.cached) $("pCached").textContent = `Live AI unavailable right now (${live.notice}) Showing a saved plan from an earlier live Gemma run on ${live.cached_at}.`;
  $("pRange").textContent = `${niceDate(r.days[0].date)} – ${niceDate(r.days[r.days.length - 1].date)}`;
  $("pOverview").textContent = r.overview;
  const w = $("pWarnings"); w.replaceChildren(); r.warnings.forEach((x) => w.appendChild(el("li", "", x)));
  toggleBlock("pWarnBox", r.warnings);
  const t = $("pTips"); t.replaceChildren(); r.tips.forEach((x) => t.appendChild(el("li", "", x)));
  toggleBlock("pTipsBox", r.tips);

  const today = todayISO();
  const days = $("pDays"); days.replaceChildren();
  r.days.forEach((d) => {
    const card = el("div", `day${d.date === today ? " today" : ""}`);
    const head = el("div", "day-head");
    head.appendChild(el("strong", "", (d.date === today ? "Today · " : "") + niceDate(d.date)));
    head.appendChild(el("span", "", d.focus));
    card.appendChild(head);
    d.sessions.forEach((s) => card.appendChild(sessionRow(s)));
    days.appendChild(card);
  });
  $("pFoot").textContent = live
    ? (live.cached ? `Saved plan · ${live.model}` : `Live result · ${live.model} · ${live.seconds}s`)
    : "Saved plan";
}

function renderDashPlan() {
  const saved = store.get("cm_plan", null);
  const box = $("dashPlan"); box.replaceChildren();
  const day = saved && saved.result.days.find((d) => d.date === todayISO());
  if (!day) {
    box.appendChild(el("p", "empty-line", saved ? "Nothing scheduled for today in your saved plan." : "No plan yet. The Study Planner builds one from your exam dates."));
    return;
  }
  if (day.focus) box.appendChild(el("p", "muted", day.focus));
  day.sessions.forEach((s) => box.appendChild(sessionRow(s)));
}

// restore the last plan on load
(() => { const saved = store.get("cm_plan", null); if (saved && saved.result.days.length) { renderPlan(saved.result, null); showP("result"); } })();

// ========================================================== HOSTEL MESS MENUS (boys / girls, per year)
// Each hostel has an "All years" menu plus optional menus of its own for years 1-4;
// a year without its own menu sees the All years one (same rule as menu_for_year in app.py).
const CAMPUS = JSON.parse($("campus-data").textContent);
const HOSTEL = CAMPUS.hostel;
const TODAY_NAME = DAY_NAMES[new Date().getDay()];
const WEEK7 = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const MEALS = ["Breakfast", "Lunch", "Snacks", "Dinner"];
let myHostel = store.get("cm_hostel", "boys");
if (!HOSTEL.hostels[myHostel]) myHostel = "boys";
let menuYear = null; // year tab picked on the menu page; null follows the year chosen in the header
let menuEditing = false;
const menuIsEmpty = (h) => !WEEK7.some((d) => MEALS.some((m) => (h.mess_menu[d] || {})[m]));
const editedLine = (o) => (o && o.edited_by ? `Last edited by ${o.edited_by} · ${fmtTime(o.edited_at)}` : "");
const ownMenu = (hostel, y) => (y === "all" ? hostel : (hostel.years || {})[String(y)]);
const menuFor = (hostel, y) => ownMenu(hostel, y) || hostel;
// a day's own time for a meal (e.g. weekend breakfast), else the usual one
const timingFor = (h, day, meal) => ((h.day_timings || {})[day] || {})[meal] || h.mess_timings[meal] || "";
const timingVaries = (h, meal) => WEEK7.some((d) => ((h.day_timings || {})[d] || {})[meal]);

function renderHostel() {
  const y = menuYear || myYear, hostel = HOSTEL.hostels[myHostel];
  const own = ownMenu(hostel, y), h = own || hostel;
  document.querySelectorAll("[data-hostel]").forEach((b) => b.classList.toggle("active", b.dataset.hostel === myHostel));
  document.querySelectorAll("[data-menu-year]").forEach((b) => b.classList.toggle("active", b.dataset.menuYear === String(y)));
  document.querySelector('[data-menu-year="all"]').hidden = !IS_ADMIN;
  $("mnTitle").textContent = `${hostel.name} · ${y === "all" ? "All years" : YEAR_NAMES[y]} · weekly menu`;
  $("mnToday").textContent = `Today is ${TODAY_NAME}`;
  $("mnSource").hidden = y === "all" || !!own;
  $("mnSource").textContent = menuEditing
    ? `${YEAR_NAMES[y]} has no menu of its own yet. Saving creates one, starting from the All years menu below.`
    : `${YEAR_NAMES[y]} uses the All years menu for this hostel.`;
  $("mnEditBar").hidden = !IS_ADMIN || menuEditing;
  $("mnEditBtn").textContent = y === "all" ? "✎ Edit the All years menu" : own ? `✎ Edit ${YEAR_NAMES[y]} menu` : `✎ Make a ${YEAR_NAMES[y]} menu`;
  $("mnEdited").textContent = editedLine(h);
  $("mnActions").hidden = !menuEditing;
  $("mnClear").hidden = !menuEditing || y === "all" || !own;
  $("mnErr").hidden = true;
  const empty = menuIsEmpty(h);
  $("mnEmpty").hidden = !empty || menuEditing;
  const table = $("mnTable"); table.replaceChildren();
  table.hidden = empty && !menuEditing;
  const head = el("tr"); head.appendChild(el("th", "", "Day"));
  MEALS.forEach((m) => {
    const th = el("th", "", m);
    if (menuEditing) {
      const t = el("input"); t.type = "text"; t.placeholder = "Time, e.g. 7:30–9:30 AM"; t.value = h.mess_timings[m] || ""; t.dataset.timing = m; t.maxLength = 60;
      th.appendChild(t);
    } else if (h.mess_timings[m] && !timingVaries(h, m)) th.appendChild(el("small", "", h.mess_timings[m]));
    head.appendChild(th);
  });
  const thead = el("thead"); thead.appendChild(head); table.appendChild(thead);
  const body = el("tbody");
  WEEK7.forEach((d) => {
    const tr = el("tr", d === TODAY_NAME ? "today" : "");
    tr.appendChild(el("th", "", d === TODAY_NAME ? `${d} · today` : d));
    MEALS.forEach((m) => {
      const td = el("td");
      const time = timingFor(h, d, m);
      td.dataset.label = !menuEditing && time ? `${m} · ${time}` : m; // shown on phones
      const val = (h.mess_menu[d] || {})[m] || "";
      if (menuEditing) {
        const t = el("textarea"); t.rows = 2; t.maxLength = 200; t.value = val; t.dataset.day = d; t.dataset.meal = m;
        td.appendChild(t);
      } else {
        td.textContent = val || "—";
        // the header can't hold a time that changes by day, so it goes in the cell (wide screens)
        if (val && time && timingVaries(h, m)) td.appendChild(el("small", "cell-time", time));
      }
      tr.appendChild(td);
    });
    body.appendChild(tr);
  });
  table.appendChild(body);
}
document.querySelectorAll("[data-hostel]").forEach((b) => b.addEventListener("click", () => {
  myHostel = b.dataset.hostel; store.set("cm_hostel", myHostel); menuEditing = false; renderHostel();
}));
document.querySelectorAll("[data-menu-year]").forEach((b) => b.addEventListener("click", () => {
  menuYear = b.dataset.menuYear === "all" ? "all" : Number(b.dataset.menuYear); menuEditing = false; renderHostel();
}));
$("mnEditBtn").addEventListener("click", () => { menuEditing = true; renderHostel(); });
$("mnCancel").addEventListener("click", () => { menuEditing = false; renderHostel(); });
async function saveMenu(payload) {
  $("mnSave").disabled = $("mnClear").disabled = true;
  try {
    const res = await fetch("/api/edit/menu", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ hostel: myHostel, year: menuYear || myYear, ...payload }) });
    const data = await res.json().catch(() => ({ error: "Save failed." }));
    if (!res.ok) throw new Error(data.error);
    HOSTEL.hostels[myHostel] = data.hostel; menuEditing = false; renderHostel();
  } catch (err) { $("mnErr").textContent = err.message; $("mnErr").hidden = false; }
  finally { $("mnSave").disabled = $("mnClear").disabled = false; }
}
$("mnSave").addEventListener("click", () => {
  const mess_menu = {}, mess_timings = {};
  WEEK7.forEach((d) => (mess_menu[d] = {}));
  document.querySelectorAll("#mnTable textarea").forEach((t) => (mess_menu[t.dataset.day][t.dataset.meal] = t.value));
  document.querySelectorAll("#mnTable input[data-timing]").forEach((t) => (mess_timings[t.dataset.timing] = t.value));
  saveMenu({ mess_menu, mess_timings });
});
$("mnClear").addEventListener("click", () => {
  const y = menuYear || myYear;
  if (!confirm(`Remove the ${YEAR_NAMES[y]} menu? ${YEAR_NAMES[y]} students will see the All years menu instead.`)) return;
  saveMenu({ clear: true });
});

function renderMenuMini(target, h, dayName) {
  const box = $(target); box.replaceChildren();
  if (menuIsEmpty(h)) { box.appendChild(el("p", "empty-line", "Menu not added yet.")); return; }
  MEALS.forEach((meal) => {
    const items = (h.mess_menu[dayName] || {})[meal];
    if (!items) return;
    const row = el("div", "meal");
    const name = el("div", "meal-name", meal);
    const time = timingFor(h, dayName, meal);
    if (time) name.appendChild(el("small", "", time));
    row.append(name, el("div", "", items));
    box.appendChild(row);
  });
}

function renderDashHostel() {
  const hostel = HOSTEL.hostels[myHostel];
  $("dashMenuTitle").textContent = `Today's menu · ${hostel.name}`;
  renderMenuMini("dashMenu", menuFor(hostel, myYear), TODAY_NAME);
  const ul = $("dashAnnouncements"); ul.replaceChildren();
  const ann = POSTS.filter((p) => p.kind === "announcement" && forStudent(p, myYear, mySection)).slice(0, 3);
  ann.forEach((a) => {
    const li = el("li");
    const d = new Date(a.published_at);
    const badge = el("div", "date-badge", String(d.getDate())); badge.appendChild(el("small", "", MONTHS[d.getMonth()]));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", a.title));
    txt.appendChild(el("span", "", `${a.published_by} · ${fmtTime(a.published_at)}`));
    li.append(badge, txt); li.style.cursor = "default";
    ul.appendChild(li);
  });
  if (!ann.length) ul.appendChild(el("li", "empty-line", "No announcements yet."));
}

// ========================================================== YEAR / SECTION / GROUP
const WEEK = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const POSTS = CAMPUS.posts || [];
POSTS.filter((p) => p.kind === "assignment").forEach((p) => CAMPUS.assignments.assignments.push({
  id: p.id, db_id: p.db_id, years: p.years, sections: p.sections, code: p.code || "General", title: p.title, posted: localDate(p.published_at),
  due: p.due, marks: p.marks, official: true, details: p.body || "See the attached PDF.", topics: [],
  submit: "Follow the instructions in the assignment.", pdf: p.pdf, published_at: p.published_at, published_by: p.published_by, author_role: p.author_role, author_id: p.author_id }));
const NOTICES = CAMPUS.notices.notices.concat(POSTS.filter((p) => p.kind === "notice" || p.kind === "announcement").map((p) => ({
  id: p.id, db_id: p.db_id, kind: p.kind, date: localDate(p.published_at), title: p.title, from: p.published_by, years: p.years, sections: p.sections,
  text: p.body, pdf: p.pdf, published_at: p.published_at, published_by: p.published_by, author_role: p.author_role, author_id: p.author_id })))
  .sort((a, b) => (b.published_at || b.date).localeCompare(a.published_at || a.date));
const canDelete = (p) => IS_ADMIN && p.db_id !== undefined && (p.author_id === ME.id || !["owner", "coowner"].includes(p.author_role) || outranks(p.author_role));
// built-in items from the data files (no db_id) are editable by every admin; published posts follow canDelete
const canManage = (p) => IS_ADMIN && (p.db_id === undefined || canDelete(p));
function deleteButton(p, label = "Delete", url = `/api/posts/${p.db_id}/delete`) {
  const b = el("button", "ghost small danger", `🗑 ${label}`);
  b.addEventListener("click", async () => {
    if (!confirm(`Delete "${p.title || p.name}"? Students will no longer see it.`)) return;
    b.disabled = true;
    const res = await fetch(url, { method: "POST" });
    if (res.ok) { location.reload(); } else { const d = await res.json().catch(() => ({})); b.disabled = false; b.textContent = d.error || "Could not delete"; }
  });
  return b;
}
// inline edit form that replaces `card`; fields: [{ name, label, type: text|textarea|date|number|pdf, value, max }]
function itemEditor(card, fields, url) {
  const form = el("form", "as-card item-edit");
  fields.forEach((f) => {
    const lab = el("label", "field");
    lab.appendChild(el("span", "label", f.label));
    let input;
    if (f.type === "textarea") { input = el("textarea"); input.rows = f.rows || 5; }
    else { input = el("input"); input.type = f.type === "pdf" ? "file" : f.type || "text"; }
    input.name = f.name;
    if (f.type === "pdf") input.accept = "application/pdf,.pdf";
    else input.value = f.value ?? "";
    if (f.max) input.maxLength = f.max;
    if (f.type === "number") { input.min = 0; input.max = 1000; }
    lab.appendChild(input);
    if (f.hint) lab.appendChild(el("span", "muted small-hint", f.hint));
    form.appendChild(lab);
  });
  const act = el("div", "notice-actions");
  const err = el("span", "muted");
  const cancel = el("button", "ghost small", "Cancel"); cancel.type = "button";
  cancel.addEventListener("click", () => form.replaceWith(card));
  const save = el("button", "primary inline", "Save changes"); save.type = "submit";
  act.append(err, cancel, save);
  form.appendChild(act);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    save.disabled = true; err.textContent = "Saving…";
    const data = new FormData(form);
    const file = form.querySelector("input[type=file]");
    if (file && !file.files[0]) data.delete(file.name);
    const res = await fetch(url, { method: "POST", body: data });
    if (res.ok) { location.reload(); return; }
    save.disabled = false; err.textContent = (await res.json().catch(() => ({}))).error || "Could not save.";
  });
  card.replaceWith(form);
  form.querySelector("input, textarea").focus();
}
function editButton(open) {
  const b = el("button", "ghost small", "✎ Edit");
  b.addEventListener("click", open);
  return b;
}
const pdfField = (has) => ({ name: "pdf", label: has ? "Replace PDF (optional)" : "Attach PDF (optional)", type: "pdf",
  hint: has ? "Leave empty to keep the current PDF." : "" });
function pubLine(item, fallbackFrom) {
  const line = el("div", "pub-line");
  if (item.published_at) {
    line.appendChild(el("span", "", "Published by "));
    line.appendChild(el("strong", "", item.published_by));
    line.appendChild(el("span", `role-tag role-${item.author_role}`, ROLE_LABEL[item.author_role] || "Admin"));
    line.appendChild(el("span", "", ` · ${fmtTime(item.published_at)}`));
  } else {
    line.appendChild(el("span", "", `Published by ${fallbackFrom || "CampusMate"} · from the data files (demo content)`));
  }
  return line;
}
const YEAR_NAMES = { 1: "1st Year", 2: "2nd Year", 3: "3rd Year", 4: "4th Year" };
const TT = (y) => CAMPUS.timetables[String(y)];
const secOf = (y, id) => TT(y).sections.find((s) => s.id === id) || TT(y).sections[0];

let profile = store.get("cm_profile", { year: 1, section: "A", group: "" });
let myYear = [1, 2, 3, 4].includes(Number(profile.year)) ? Number(profile.year) : 1;
let mySection = secOf(myYear, profile.section).id;
let myGroup = profile.group || "";
let myBranch = profile.branch || "";
let ttYear = myYear, ttSection = mySection;

function fillPickers() {
  $("myYear").value = String(myYear);
  $("mySection").replaceChildren(...TT(myYear).sections.map((s) => { const o = el("option", "", s.id); o.value = s.id; return o; }));
  $("mySection").value = mySection;
  const groups = secOf(myYear, mySection).groups || [];
  $("groupPick").hidden = groups.length === 0;
  if (!groups.find((g) => g.id === myGroup)) myGroup = "";
  const opts = [el("option", "", "All groups")]; opts[0].value = "";
  groups.forEach((g) => { const o = el("option", "", g.rolls ? `${g.id} (${g.rolls})` : g.id); o.value = g.id; opts.push(o); });
  $("myGroup").replaceChildren(...opts);
  $("myGroup").value = myGroup;
}
function saveProfile() {
  store.set("cm_profile", { year: myYear, section: mySection, group: myGroup, branch: myBranch });
  ttYear = myYear; ttSection = mySection; menuYear = null; askHistory = [];
  fillPickers(); route();
}
$("myYear").addEventListener("change", (e) => { myYear = Number(e.target.value); mySection = secOf(myYear, mySection).id; myGroup = ""; myBranch = ""; saveProfile(); });
$("mySection").addEventListener("change", (e) => { mySection = e.target.value; myGroup = ""; saveProfile(); });
$("myGroup").addEventListener("change", (e) => { myGroup = e.target.value; saveProfile(); });
fillPickers();

function forStudent(item, y, sec) {
  const years = (item.years || (item.year !== undefined ? [item.year] : ["all"])).map(String);
  const secs = item.sections || ["all"];
  return (years.includes("all") || years.includes(String(y))) && (secs.includes("all") || secs.includes(sec));
}
const isSectionSpecific = (n) => !(n.sections || ["all"]).includes("all");
const myLabel = () => `${YEAR_NAMES[myYear]} · Section ${mySection}${myGroup ? " · " + myGroup : ""}`;
const hh = (h) => `${h > 12 ? h - 12 : h}:00${h >= 12 ? " PM" : " AM"}`;
const span = (c) => `${hh(c.start)} – ${hh(c.end)}`;
const typeCls = (t) => ({ Lecture: "lecture", Lab: "lab", Tutorial: "tutorial", Studio: "studio" }[t] || "lecture");
const SYL = (y) => (CAMPUS.syllabus.years[String(y)] || { subjects: {}, missing: {}, documents: [] });
const subjName = (y, code) => { const s = SYL(y).subjects[code]; return s && s.name !== code ? s.name : ""; };
const dayClasses = (sec, day, group) => sec.classes.filter((c) => c.day === day && (!group || !c.group || c.group === group)).sort((a, b) => a.start - b.start);
function daysUntilLabel(iso) { const n = daysUntil(iso); return n < 0 ? "past" : n === 0 ? "today" : n === 1 ? "tomorrow" : `in ${n} days`; }
function dmy(iso) { const [, m, d] = iso.split("-"); return `${Number(d)} ${MONTHS[Number(m) - 1]}`; }

// ---------- notice board
function noticeCard(n) {
  const card = el("div", "notice");
  const top = el("div", "notice-top");
  top.appendChild(el("span", "notice-date", dmy(n.date)));
  top.appendChild(el("span", "notice-from", n.from));
  card.appendChild(top);
  const h = el("h3", "", n.title);
  if (n.kind === "announcement") h.prepend(el("span", "kind-badge", "Announcement"));
  card.appendChild(h);
  if (n.published_at) card.classList.add("from-admin");
  const tags = el("div", "aud-tags");
  const yrs = (n.years || ["all"]);
  tags.appendChild(el("span", "aud", yrs.includes("all") ? "All years" : yrs.map((y) => YEAR_NAMES[y]).join(", ")));
  if (isSectionSpecific(n)) n.sections.forEach((a) => tags.appendChild(el("span", `aud${a === mySection ? " mine" : ""}`, `Sec ${a}`)));
  card.appendChild(tags);
  const short = n.text.replace(/\s+/g, " ").slice(0, 170) + "…";
  const preview = el("p", "notice-preview", short);
  card.appendChild(preview);
  const actions = el("div", "notice-actions");
  const read = el("button", "ghost small", "Read full notice");
  read.addEventListener("click", () => {
    const open = preview.classList.toggle("full");
    preview.textContent = open ? n.text : short;
    read.textContent = open ? "Show less" : "Read full notice";
  });
  const ai = el("button", "ai-btn", "✦ Explain with AI");
  ai.addEventListener("click", () => explainFromBoard(n));
  if (n.pdf) { const a = el("a", "ghost small pdf-btn", "📄 PDF"); a.href = pdfHref(n.pdf); a.target = "_blank"; a.rel = "noopener"; actions.appendChild(a); }
  actions.append(read, ai);
  if (canDelete(n)) actions.appendChild(deleteButton(n));
  card.appendChild(pubLine(n, n.from));
  card.appendChild(actions);
  return card;
}

function explainFromBoard(n) {
  $("noticeText").value = n.text;
  updateCount();
  clearFile();
  document.querySelectorAll(".chip[data-sample]").forEach((c) => c.classList.remove("active"));
  location.hash = "#notice";
  sampleId = n.id;
  setTimeout(() => $("explainBtn").click(), 50);
}

function renderBoard() {
  const mine = NOTICES.filter((n) => forStudent(n, myYear, mySection));
  const all = mine.filter((n) => !isSectionSpecific(n));
  const sec = mine.filter(isSectionSpecific);
  $("boardYearName").textContent = `everyone in ${YEAR_NAMES[myYear]}`;
  $("boardSectionName").textContent = `Section ${mySection}`;
  $("countAll").textContent = all.length;
  $("countSec").textContent = sec.length;
  $("boardAll").replaceChildren(...all.map(noticeCard));
  $("boardSection").replaceChildren(...(sec.length ? sec.map(noticeCard) : [el("p", "empty-line", "No notices only for your section right now.")]));
}

// ---------- timetable
function renderTimetable() {
  const tt = TT(ttYear), sec = secOf(ttYear, ttSection);
  $("ttTitle").textContent = `${tt.title} · ${tt.source}`;
  $("ttYears").replaceChildren(...[1, 2, 3, 4].map((y) => {
    const b = el("button", `year-tab${y === ttYear ? " active" : ""}`, YEAR_NAMES[y]);
    b.addEventListener("click", () => { ttYear = y; ttSection = secOf(y, ttSection).id; renderTimetable(); });
    return b;
  }));
  $("ttTabs").replaceChildren(...tt.sections.map((s) => {
    const mine = ttYear === myYear && s.id === mySection;
    const b = el("button", `sec-tab${s.id === ttSection ? " active" : ""}`, s.id + (mine ? " ★" : ""));
    b.title = mine ? "Your section" : s.name;
    b.addEventListener("click", () => { ttSection = s.id; renderTimetable(); });
    return b;
  }));
  $("ttBadge").className = `alert ${tt.official ? "ok" : "warn"}`;
  $("ttBadge").textContent = tt.official ? `✓ Official timetable. ${tt.note}` : `📋 ${tt.note}`;

  const info = $("ttInfo"); info.replaceChildren();
  const add = (k, v) => { const d = el("div", "info-item"); d.append(el("span", "", k), el("strong", "", v)); info.appendChild(d); };
  add("Section", `${sec.name}${sec.branches ? " · " + sec.branches : ""}`);
  add("Classroom", sec.classroom + (tt.classrooms && tt.classrooms[sec.classroom] ? ` · ${tt.classrooms[sec.classroom]}` : ""));
  if (sec.groups && sec.groups.length) add("Groups", sec.groups.map((g) => g.rolls ? `${g.id}: ${g.rolls}` : g.id).join("   "));

  const viewingMine = ttYear === myYear && ttSection === mySection;
  $("ttGroupHint").textContent = sec.groups && sec.groups.length
    ? (viewingMine && myGroup ? `Faded labs are for other groups (you are ${myGroup}).` : "Pick your group in the sidebar to highlight your labs.") : "";

  $("ttEditBar").hidden = !IS_ADMIN || ttEditing;
  $("ttEdited").textContent = editedLine(tt);
  if (!sec.classes.length && !ttEditing) {
    $("ttGrid").replaceChildren(el("p", "empty-line tt-empty", IS_ADMIN
      ? "No classes added for this section yet. Use “Edit this section's timetable” to add them."
      : "The timetable for this section hasn't been added yet."));
    $("ttGrid").style.gridTemplateColumns = "1fr";
    return;
  }
  const hours = sec.classes.reduce((m, c) => [Math.min(m[0], c.start), Math.max(m[1], c.end)], [9, 17]);
  const first = hours[0], last = Math.max(hours[1], 18);
  const grid = $("ttGrid"); grid.replaceChildren();
  grid.style.gridTemplateColumns = `76px repeat(${last - first}, minmax(88px, 1fr))`;
  grid.appendChild(el("div", "tg-head", "Day"));
  for (let h = first; h < last; h++) grid.appendChild(el("div", "tg-head", `${hh(h)}`));
  const today = DAY_NAMES[new Date().getDay()];
  const days = WEEK.filter((d) => sec.classes.some((c) => c.day === d));
  days.forEach((day, r) => {
    const row = r + 2;
    const label = el("div", `tg-day${day === today ? " today" : ""}`, day.slice(0, 3));
    label.style.gridRow = row; label.style.gridColumn = 1;
    grid.appendChild(label);
    if (first <= 13 && last >= 14 && !sec.classes.some((c) => c.day === day && c.start < 14 && c.end > 13)) {
      const lunch = el("div", "tg-lunch", "Lunch");
      lunch.style.gridRow = row; lunch.style.gridColumn = `${13 - first + 2} / ${14 - first + 2}`;
      grid.appendChild(lunch);
    }
    const blocks = {};
    sec.classes.filter((c) => c.day === day).forEach((c) => { (blocks[`${c.start}-${c.end}`] ||= []).push(c); });
    Object.values(blocks).forEach((list) => {
      const b = el("div", `tg-block${day === today ? " today" : ""}`);
      b.style.gridRow = row; b.style.gridColumn = `${list[0].start - first + 2} / ${list[0].end - first + 2}`;
      list.forEach((c) => {
        const other = viewingMine && myGroup && c.group && c.group !== myGroup;
        const cell = el("div", `cls ${typeCls(c.type)}${other ? " other-group" : ""}${viewingMine && myGroup && c.group === myGroup ? " my-group" : ""}`);
        cell.appendChild(el("strong", "", c.label));
        cell.title = subjName(ttYear, c.code) || c.label;
        const meta = [c.faculty, c.room !== sec.classroom ? c.room : ""].filter(Boolean).join(" · ");
        if (meta) cell.appendChild(el("span", "", meta));
        b.appendChild(cell);
      });
      grid.appendChild(b);
    });
  });
}

// ---------- timetable editor (admins)
let ttEditing = false;
const HOUR_OPTS = Array.from({ length: 15 }, (_, i) => i + 7);   // 7..21
function selectEl(options, value, cls) {
  const sel = el("select", cls);
  options.forEach(([v, label]) => { const o = el("option", "", label); o.value = v; sel.appendChild(o); });
  sel.value = value ?? options[0][0];
  return sel;
}
function ttRow(c = {}) {
  const tr = el("tr");
  const cell = (node) => { const td = el("td"); td.appendChild(node); tr.appendChild(td); return node; };
  cell(selectEl(WEEK.map((d) => [d, d.slice(0, 3)]), c.day || "Monday", "r-day"));
  cell(selectEl(HOUR_OPTS.map((h) => [h, hh(h)]), c.start ?? 9, "r-start"));
  cell(selectEl(HOUR_OPTS.slice(1).concat([21]).filter((v, i, a) => a.indexOf(v) === i).map((h) => [h, hh(h)]), c.end ?? 10, "r-end"));
  const code = cell(el("input", "r-code")); code.type = "text"; code.maxLength = 40; code.value = c.code || ""; code.placeholder = "e.g. MA-111";
  cell(selectEl([["Lecture", "Lecture"], ["Lab", "Lab"], ["Tutorial", "Tutorial"], ["Studio", "Studio"], ["Free", "Free / other"]], c.type || "Lecture", "r-type"));
  cell(selectEl([["", "All"], ["Gr.1", "Gr.1"], ["Gr.2", "Gr.2"], ["Gr.3", "Gr.3"], ["Gr.4", "Gr.4"]], c.group || "", "r-group"));
  const fac = cell(el("input", "r-fac")); fac.type = "text"; fac.maxLength = 80; fac.value = c.faculty || "";
  const room = cell(el("input", "r-room")); room.type = "text"; room.maxLength = 40; room.value = c.room || "";
  const del = cell(el("button", "remove", "×")); del.title = "Remove this class";
  del.addEventListener("click", () => tr.remove());
  return tr;
}
function openTtEditor() {
  const sec = secOf(ttYear, ttSection);
  ttEditing = true;
  $("ttEditorTitle").textContent = `Edit timetable · ${YEAR_NAMES[ttYear]}, ${sec.name}`;
  $("ttClassroom").value = sec.classroom || "";
  const rows = $("ttRows"); rows.replaceChildren();
  const sorted = sec.classes.slice().sort((a, b) => WEEK.indexOf(a.day) - WEEK.indexOf(b.day) || a.start - b.start);
  (sorted.length ? sorted : [{}]).forEach((c) => rows.appendChild(ttRow(c)));
  $("ttErr").hidden = true; $("ttEditor").hidden = false;
  renderTimetable();
  $("ttEditor").scrollIntoView({ behavior: "smooth", block: "start" });
}
function closeTtEditor() { ttEditing = false; $("ttEditor").hidden = true; renderTimetable(); }
if ($("ttEditBtn")) {
  $("ttEditBtn").addEventListener("click", openTtEditor);
  $("ttCancel").addEventListener("click", closeTtEditor);
  $("ttAddRow").addEventListener("click", () => $("ttRows").appendChild(ttRow({ day: "Monday", start: 9, end: 10 })));
  $("ttSave").addEventListener("click", async () => {
    const classes = [...$("ttRows").children].map((tr) => ({
      day: tr.querySelector(".r-day").value, start: Number(tr.querySelector(".r-start").value), end: Number(tr.querySelector(".r-end").value),
      code: tr.querySelector(".r-code").value, type: tr.querySelector(".r-type").value, group: tr.querySelector(".r-group").value,
      faculty: tr.querySelector(".r-fac").value, room: tr.querySelector(".r-room").value,
    })).filter((c) => c.code.trim());
    $("ttSave").disabled = true; $("ttErr").hidden = true;
    try {
      const res = await fetch("/api/edit/timetable", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ year: ttYear, section: ttSection, classroom: $("ttClassroom").value, classes }) });
      const data = await res.json().catch(() => ({ error: "Save failed." }));
      if (!res.ok) throw new Error(data.error);
      CAMPUS.timetables[String(ttYear)] = data.timetable;
      closeTtEditor();
    } catch (err) { $("ttErr").textContent = err.message; $("ttErr").hidden = false; }
    finally { $("ttSave").disabled = false; }
  });
}

// ---------- assignments
let asFilter = "pending";
let doneAssign = store.get("cm_assign_done", {});
document.querySelectorAll("[data-asf]").forEach((b) => b.addEventListener("click", () => {
  asFilter = b.dataset.asf;
  document.querySelectorAll("[data-asf]").forEach((x) => x.classList.toggle("active", x === b));
  renderAssignments();
}));
const myAssignments = () => CAMPUS.assignments.assignments.filter((a) => forStudent(a, myYear, mySection))
  .sort((a, b) => (a.due || "9999").localeCompare(b.due || "9999") || b.posted.localeCompare(a.posted));

function renderAssignments() {
  $("asSub").textContent = `Published for ${YEAR_NAMES[myYear]}, Section ${mySection}.`;
  const list = myAssignments().filter((a) => asFilter === "all" || (asFilter === "done") === !!doneAssign[a.id]);
  const box = $("asList"); box.replaceChildren();
  if (!list.length) box.appendChild(el("p", "empty-line", asFilter === "pending" ? "Nothing pending. 🎉" : "Nothing here yet."));
  list.forEach((a) => {
    const done = !!doneAssign[a.id], n = a.due ? daysUntil(a.due) : null;
    const card = el("div", `as-card${done ? " done" : ""}${!done && n !== null && n <= 3 && n >= 0 ? " soon" : ""}`);
    const head = el("div", "as-head");
    const left = el("div");
    left.appendChild(el("span", "as-code", a.code));
    const nm = subjName(myYear, a.code);
    if (nm) left.appendChild(el("span", "as-name", nm));
    left.appendChild(el("span", a.official ? "src-tag real" : "src-tag", a.official ? "Real assignment" : "Sample"));
    left.appendChild(el("h3", "", a.title));
    head.appendChild(left);
    const due = el("div", "as-due");
    due.appendChild(el("strong", "", done ? "Submitted" : n === null ? "Due date not stated" : n < 0 ? "Overdue" : daysUntilLabel(a.due)));
    due.appendChild(el("span", "", [a.due ? `Due ${dmy(a.due)}` : "", a.marks ? `${a.marks} marks` : "", `Posted ${dmy(a.posted)}`].filter(Boolean).join(" · ")));
    head.appendChild(due);
    card.appendChild(head);
    card.appendChild(el("p", "as-details", a.details));
    if (a.topics && a.topics.length) {
      const t = el("div", "topic-chips"); a.topics.forEach((x) => t.appendChild(el("span", "", x))); card.appendChild(t);
    }
    card.appendChild(el("p", "as-submit", `📤 ${a.submit}`));
    card.appendChild(pubLine(a, a.official ? "Course instructor (PDF provided by the student)" : "CampusMate (sample)"));
    const actions = el("div", "notice-actions");
    if (a.pdf) {
      const pdf = el("a", "ghost small pdf-btn", "📄 Open assignment PDF");
      pdf.href = pdfHref(a.pdf); pdf.target = "_blank"; pdf.rel = "noopener";
      actions.appendChild(pdf);
    }
    const tick = el("button", "ghost small", done ? "Mark as not submitted" : "✓ Mark as submitted");
    tick.addEventListener("click", () => { doneAssign[a.id] = !done; store.set("cm_assign_done", doneAssign); renderAssignments(); });
    const ai = el("button", "ai-btn", "✦ How do I start?");
    ai.addEventListener("click", () => { location.hash = "#dashboard"; setTimeout(() => askQuestion(`How should I start "${a.title}" (${a.code})? Which syllabus topics does it need and what should I revise first?`), 60); });
    actions.append(tick, ai);
    if (canManage(a)) {
      const builtin = a.db_id === undefined;
      const base = builtin ? `/api/edit/assignment/${encodeURIComponent(a.id)}` : `/api/posts/${a.db_id}`;
      actions.appendChild(editButton(() => itemEditor(card, [
        { name: "title", label: "Title", value: a.title, max: 150 },
        { name: "code", label: "Subject code", value: a.code === "General" ? "" : a.code, max: 40 },
        { name: "body", label: "Description / instructions", type: "textarea", value: builtin || a.details !== "See the attached PDF." ? a.details : "", max: 8000 },
        { name: "due", label: "Due date", type: "date", value: a.due || "" },
        { name: "marks", label: "Marks", type: "number", value: a.marks ?? "" },
        ...(builtin ? [{ name: "submit", label: "How to submit", value: a.submit, max: 300 }] : []),
        pdfField(!!a.pdf),
      ], builtin ? base : `${base}/edit`)));
      actions.appendChild(deleteButton(a, "Delete", `${base}/delete`));
    }
    card.appendChild(actions);
    box.appendChild(card);
  });
}

// ---------- exams
// Papers differ by branch from 2nd year on; rows list the branches they're for ("all" = everyone).
const examBranches = (ex) => [...new Set((ex ? ex.rows : []).flatMap((r) => r.branches || []).filter((b) => b !== "all"))].sort();
const examBranch = () => (examBranches(CAMPUS.exams.years[String(myYear)]).includes(myBranch) ? myBranch : "");
const forBranch = (r, b) => !b || (r.branches || ["all"]).some((x) => x === "all" || x === b);
const exName = (r) => r.name || subjName(myYear, r.code);
const myExams = () => {
  const ex = CAMPUS.exams.years[String(myYear)], b = examBranch();
  return ex ? ex.rows.filter((r) => forStudent(r, myYear, mySection) && forBranch(r, b)).sort((a, b) => a.date.localeCompare(b.date)) : [];
};
function renderExams() {
  const ex = CAMPUS.exams.years[String(myYear)];
  const branches = examBranches(ex), b = examBranch();
  $("exBranchPick").hidden = !branches.length;
  const opts = [el("option", "", "All branches")]; opts[0].value = "";
  branches.forEach((x) => { const o = el("option", "", x); o.value = x; opts.push(o); });
  $("exBranch").replaceChildren(...opts); $("exBranch").value = b;
  $("exBranchHint").hidden = !branches.length || !!b;
  $("exSub").textContent = `${ex ? ex.name : "No exams scheduled"} · ${YEAR_NAMES[myYear]}, ${b || `Section ${mySection}`}`;
  $("exEditBar").hidden = !IS_ADMIN || !$("exEditor").hidden;
  $("exEdited").textContent = editedLine(ex);
  const rows0 = myExams();
  $("exTableCard").hidden = !rows0.length; $("exEmpty").hidden = !!rows0.length; $("exPlan").hidden = !rows0.length;
  if (!rows0.length) $("exSub").textContent = `${YEAR_NAMES[myYear]}, Section ${mySection}`;
  const rows = rows0, upcoming = rows.filter((r) => daysUntil(r.date) >= 0);
  $("exCount").textContent = upcoming.length ? `First exam ${daysUntilLabel(upcoming[0].date)} · ${upcoming.length} papers` : "No upcoming papers.";
  const body = $("exBody"); body.replaceChildren();
  rows.forEach((r) => {
    const tr = el("tr");
    const d = new Date(r.date + "T00:00:00");
    tr.appendChild(el("td", "ex-date", `${DAY_NAMES[d.getDay()].slice(0, 3)}, ${dmy(r.date)}`));
    tr.appendChild(el("td", "", r.time));
    const codeTd = el("td", "ex-code", r.code);
    const nm = exName(r);
    if (nm) codeTd.appendChild(el("span", "ex-name", nm));
    const br = (r.branches || []).filter((x) => x !== "all");
    if (!b && br.length) codeTd.appendChild(el("span", "ex-name", br.join(", ")));
    tr.appendChild(codeTd);
    tr.appendChild(el("td", "", r.venue));
    tr.appendChild(el("td", "ex-left", daysUntilLabel(r.date)));
    body.appendChild(tr);
  });
  $("exRules").replaceChildren(...CAMPUS.exams.rules.map((x) => el("li", "", x)));
  const notes = (ex && ex.notes) || [];
  $("exNotes").hidden = !notes.length || !rows0.length;
  $("exNotesList").replaceChildren(...notes.map((x) => el("li", "", x)));
}
$("exBranch").addEventListener("change", (e) => { myBranch = e.target.value; saveProfile(); });
function exRow(r = {}) {
  const tr = el("tr");
  const inp = (cls, type, val, ph, max) => { const td = el("td"); const i = el("input", cls); i.type = type; i.value = val || ""; if (ph) i.placeholder = ph; if (max) i.maxLength = max; td.appendChild(i); tr.appendChild(td); };
  inp("e-date", "date", r.date);
  inp("e-time", "text", r.time, "9:30–11:00 AM", 40);
  inp("e-code", "text", r.code, "e.g. MA-111", 60);
  inp("e-name", "text", r.name, "e.g. Linear Algebra & Calculus", 160);
  inp("e-secs", "text", (r.sections || []).filter((x) => x !== "all").join(", "), "all", 40);
  inp("e-branches", "text", (r.branches || []).filter((x) => x !== "all").join(", "), "all", 200);
  inp("e-venue", "text", r.venue, "e.g. As per seating plan", 60);
  const td = el("td"); const del = el("button", "remove", "×"); del.addEventListener("click", () => tr.remove()); td.appendChild(del); tr.appendChild(td);
  return tr;
}
if ($("exEditBtn")) {
  $("exEditBtn").addEventListener("click", () => {
    const ex = CAMPUS.exams.years[String(myYear)] || { name: "", rows: [] };
    $("exEditorTitle").textContent = `Edit exam schedule · ${YEAR_NAMES[myYear]} (all sections)`;
    $("exName").value = ex.name || "";
    const rows = $("exRows"); rows.replaceChildren();
    (ex.rows.length ? ex.rows : [{}]).forEach((r) => rows.appendChild(exRow(r)));
    $("exErr").hidden = true; $("exEditor").hidden = false; renderExams();
  });
  $("exAddRow").addEventListener("click", () => $("exRows").appendChild(exRow()));
  $("exCancel").addEventListener("click", () => { $("exEditor").hidden = true; renderExams(); });
  $("exSave").addEventListener("click", async () => {
    const rows = [...$("exRows").children].map((tr) => ({
      date: tr.querySelector(".e-date").value, time: tr.querySelector(".e-time").value, code: tr.querySelector(".e-code").value,
      name: tr.querySelector(".e-name").value,
      sections: tr.querySelector(".e-secs").value.toUpperCase().split(/[\s,]+/).filter(Boolean),
      branches: tr.querySelector(".e-branches").value.split(",").map((x) => x.trim()).filter(Boolean), venue: tr.querySelector(".e-venue").value,
    })).filter((r) => r.code.trim() || r.date);
    $("exSave").disabled = true; $("exErr").hidden = true;
    try {
      const res = await fetch("/api/edit/exams", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ year: myYear, name: $("exName").value, rows }) });
      const data = await res.json().catch(() => ({ error: "Save failed." }));
      if (!res.ok) throw new Error(data.error);
      CAMPUS.exams.years[String(myYear)] = data.exams; $("exEditor").hidden = true; renderExams();
    } catch (err) { $("exErr").textContent = err.message; $("exErr").hidden = false; }
    finally { $("exSave").disabled = false; }
  });
}
$("exPlan").addEventListener("click", () => {
  const rows = myExams().filter((r) => daysUntil(r.date) >= 0).slice(0, 8);
  resetSubjects(rows.map((r) => ({ name: exName(r) ? `${r.code} ${exName(r)}` : r.code,
    exam_date: r.date, difficulty: "medium", topics: "" })));
  planSample = false;
  location.hash = "#planner";
});

// ---------- syllabus
function pdfLink(rel, label) {
  const a = el("a", "ghost small pdf-btn", label);
  a.href = pdfHref(rel); a.target = "_blank"; a.rel = "noopener";
  return a;
}
function subjectCard(code, sec, syl) {
  const sub = syl.subjects[code];
  const cls = sec ? sec.classes.filter((c) => c.code === code) : [];
  const mine = cls.filter((c) => !c.group || !myGroup || c.group === myGroup);
  const hours = (t) => mine.filter((c) => t === c.type).reduce((n, c) => n + (c.end - c.start), 0);
  const card = el("div", "sy-card");
  const head = el("div", "sy-head");
  head.appendChild(el("span", "as-code", code));
  if (sub && sub.semester) head.appendChild(el("span", "sy-sem", `Semester ${sub.semester}`));
  card.appendChild(head);
  card.appendChild(el("h3", "sy-title", sub ? sub.name : code));
  const stats = el("div", "sy-stats");
  if (sub && sub.L !== undefined) stats.appendChild(el("span", "", `L-P: ${sub.L}-${sub.P} · ${sub.credits} credits`));
  if (sub && sub.type) stats.appendChild(el("span", "", sub.type));
  ["Lecture", "Lab", "Tutorial", "Studio"].forEach((k) => { const h = hours(k); if (h) stats.appendChild(el("span", "", `${k}: ${h} h/week`)); });
  card.appendChild(stats);
  const fac = [...new Set(cls.flatMap((c) => (c.faculty || "").split(/,\s*/)).filter(Boolean))];
  if (fac.length) card.appendChild(el("p", "sy-fac", `Faculty (from timetable): ${fac.join(", ")}`));
  if (!sub) {
    card.appendChild(el("p", "empty-line", (syl.missing && syl.missing[code]) || "Syllabus not added yet."));
    if (IS_ADMIN) {
      const add = el("button", "ghost small", "＋ Add syllabus");
      add.addEventListener("click", () => addSubjectEditor(card, code));
      const actions = el("div", "sy-actions"); actions.appendChild(add); card.appendChild(actions);
    }
    return card;
  }
  if (sub.note) card.appendChild(el("p", "sy-fac", sub.note));
  if (sub.added_by) card.appendChild(el("p", "sy-fac", `Added on CampusMate by ${sub.added_by}`));
  const actions = el("div", "sy-actions");
  if (sub.pdf) actions.appendChild(pdfLink(sub.pdf, `📄 ${sub.added_by ? "Syllabus PDF" : "Original PDF"}${syl.official && sub.pages && sub.pages.length ? ` (p. ${sub.pages.join(", ")})` : ""}`));
  const toggle = el("button", "ghost small", "📝 Read as text");
  const pre = el("pre", "sy-text", sub.text);
  pre.hidden = true;
  toggle.addEventListener("click", () => { pre.hidden = !pre.hidden; toggle.textContent = pre.hidden ? "📝 Read as text" : "Hide text"; });
  const ai = el("button", "ai-btn", "✦ Summarise");
  ai.addEventListener("click", () => { location.hash = "#dashboard"; setTimeout(() => askQuestion(`Summarise the syllabus of ${code}${sub.name !== code ? " " + sub.name : ""} unit by unit, in simple words.`), 60); });
  if (sub.text) actions.append(toggle, ai);
  if (IS_ADMIN) {
    const url = `/api/edit/syllabus/${myYear}/${encodeURIComponent(code)}`;
    actions.appendChild(editButton(() => itemEditor(card, [
      { name: "name", label: "Subject name", value: sub.name, max: 150 },
      { name: "text", label: "Syllabus text", type: "textarea", rows: 12, value: sub.text, max: 20000,
        hint: sub.pdf ? "Can be left empty if the PDF has the syllabus." : "" },
      { name: "note", label: "Note (optional)", value: sub.note || "", max: 300 },
      pdfField(!!sub.pdf),
    ], url)));
    actions.appendChild(deleteButton({ title: `${code} syllabus` }, "Delete", `${url}/delete`));
  }
  card.appendChild(actions);
  card.appendChild(pre);
  return card;
}
// admins add a subject that isn't in the built-in syllabus; `card` is swapped for the form until it's saved or cancelled
function addSubjectEditor(card, code = "") {
  itemEditor(card, [
    { name: "code", label: "Subject code", value: code, max: 20,
      hint: `Adds it to ${YEAR_NAMES[myYear]}. Use the code from the timetable (like CS-111) so it shows on that subject's card.` },
    { name: "name", label: "Subject name", value: "", max: 150 },
    { name: "semester", label: "Semester (1 or 2, optional)", value: "", max: 1 },
    { name: "text", label: "Syllabus text", type: "textarea", rows: 12, value: "", max: 20000,
      hint: "Units and topics. Can be left empty if you attach the syllabus PDF." },
    { name: "note", label: "Note (optional)", value: "", max: 300 },
    pdfField(false),
  ], `/api/syllabus/${myYear}/add`);
  if (code) document.querySelector(".item-edit input[name=name]").focus();
}
if (IS_ADMIN) {
  const slot = el("div");  // where the Add subject form opens; it's out of the page while the form is open
  $("syGrid").before(slot);
  $("syAdd").hidden = false;
  $("syAdd").addEventListener("click", () => { if (slot.isConnected) addSubjectEditor(slot); });
}
function renderSyllabus() {
  const sec = secOf(myYear, mySection), syl = SYL(myYear);
  $("sySub").textContent = `${YEAR_NAMES[myYear]}, ${sec.name}: subjects in your timetable.`;
  $("syBadge").className = `alert ${syl.official ? "ok" : "warn"}`;
  $("syBadge").textContent = syl.official ? `✓ ${syl.title}. ${syl.note || ""}` : `📋 ${syl.note}`;
  const docs = $("syDocs"); docs.replaceChildren();
  (syl.documents || []).forEach((d) => docs.appendChild(pdfLink(d.pdf, `📄 ${d.title}`)));
  const mats = POSTS.filter((p) => p.kind === "material" && forStudent(p, myYear, mySection));
  $("syMaterialsWrap").hidden = !mats.length;
  $("syMaterials").replaceChildren(...mats.map((m) => {
    const card = el("div", "as-card");
    const head = el("div", "as-head"); const left = el("div");
    if (m.code) left.appendChild(el("span", "as-code", m.code));
    left.appendChild(el("h3", "", m.title)); head.appendChild(left); card.appendChild(head);
    if (m.body) card.appendChild(el("p", "as-details", m.body));
    card.appendChild(pubLine(m));
    const act = el("div", "notice-actions"); act.appendChild(pdfLink(m.pdf, "📄 Open PDF"));
    if (canDelete(m)) {
      act.appendChild(editButton(() => itemEditor(card, [
        { name: "title", label: "Title", value: m.title, max: 150 },
        { name: "code", label: "Subject code", value: m.code || "", max: 40 },
        { name: "body", label: "Description (optional)", type: "textarea", value: m.body || "", max: 8000 },
        pdfField(true),
      ], `/api/posts/${m.db_id}/edit`)));
      act.appendChild(deleteButton(m));
    }
    card.appendChild(act);
    return card;
  }));
  const codes = [...new Set(sec.classes.map((c) => c.code))].sort();
  $("syGrid").replaceChildren(...(codes.length ? codes.map((c) => subjectCard(c, sec, syl))
    : [el("p", "empty-line card", Object.keys(syl.subjects).length ? "Add this section's timetable to see its subjects here."
        : `The syllabus for this year hasn't been added yet.${IS_ADMIN ? " Use Add subject to add it." : ""}`)]));
  const others = Object.keys(syl.subjects).filter((c) => !codes.includes(c)).sort();
  $("syOtherWrap").hidden = !others.length;
  $("syOtherCount").textContent = others.length;
  $("syOther").replaceChildren(...others.map((c) => subjectCard(c, null, syl)));
}

// ---------- dashboard pieces
function classChip(c) {
  const chip = el("div", `cls ${typeCls(c.type)}`);
  chip.appendChild(el("small", "", span(c)));
  chip.appendChild(el("strong", "", c.label));
  const nm = subjName(myYear, c.code);
  if (nm) chip.appendChild(el("em", "", nm));
  chip.appendChild(el("span", "", [c.type, c.room, c.faculty].filter(Boolean).join(" · ")));
  return chip;
}

function renderDashSection() {
  const sec = secOf(myYear, mySection);
  const today = todayISO(), hol = holidayOn(today);
  let label = "Today's classes", classes = hol ? [] : dayClasses(sec, DAY_NAMES[new Date().getDay()], myGroup);
  if (!classes.length) {
    for (let i = 1; i <= 21; i++) {
      const iso = shiftISO(today, i);
      if (holidayOn(iso)) continue;
      const name = DAY_NAMES[new Date(iso + "T00:00:00").getDay()];
      const c = dayClasses(sec, name, myGroup);
      if (c.length) {
        classes = c;
        label = `${hol ? `🎉 Holiday today: ${hol.title}` : "No classes today 🎉"} · ${i === 1 ? "Tomorrow" : `${name}, ${dmy(iso)}`}'s classes`;
        break;
      }
    }
  }
  $("dashClassesTitle").textContent = `${label} · ${myLabel()}`;
  $("dashClasses").replaceChildren(...classes.map(classChip));

  const mine = NOTICES.filter((n) => forStudent(n, myYear, mySection)).slice(0, 4);
  const ul = $("dashNotices"); ul.replaceChildren();
  mine.forEach((n) => {
    const li = el("li");
    const specific = isSectionSpecific(n);
    li.appendChild(el("span", `aud-dot ${specific ? "sec" : "all"}`, specific ? mySection : "All"));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", n.title));
    txt.appendChild(el("span", "", `${n.from} · ${specific ? "your section" : "everyone"}`));
    li.appendChild(txt);
    li.addEventListener("click", () => explainFromBoard(n));
    ul.appendChild(li);
  });

  const due = $("dashDue"); due.replaceChildren();
  const pending = myAssignments().filter((a) => !doneAssign[a.id] && (!a.due || daysUntil(a.due) >= 0)).slice(0, 3);
  pending.forEach((a) => {
    const li = el("li");
    const badge = a.due ? el("div", `date-badge${daysUntil(a.due) <= 3 ? " soon" : ""}`, String(Number(a.due.slice(8)))) : el("div", "date-badge", "📄");
    if (a.due) badge.appendChild(el("small", "", MONTHS[Number(a.due.slice(5, 7)) - 1]));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", `${a.code}: ${a.title}`));
    txt.appendChild(el("span", "", a.due ? `Assignment · due ${daysUntilLabel(a.due)}` : "Assignment · due date not stated"));
    li.append(badge, txt);
    li.addEventListener("click", () => { location.hash = "#assignments"; });
    due.appendChild(li);
  });
  const nextExam = myExams().find((r) => daysUntil(r.date) >= 0);
  if (nextExam) {
    const li = el("li");
    const badge = el("div", "date-badge soon", String(Number(nextExam.date.slice(8))));
    badge.appendChild(el("small", "", MONTHS[Number(nextExam.date.slice(5, 7)) - 1]));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", `Exam: ${nextExam.code}`));
    txt.appendChild(el("span", "", `${nextExam.time} · ${daysUntilLabel(nextExam.date)}`));
    li.append(badge, txt);
    li.addEventListener("click", () => { location.hash = "#exams"; });
    due.appendChild(li);
  }
  if (!due.children.length) due.appendChild(el("li", "empty-line", "Nothing due. 🎉"));
}

// ========================================================== ACADEMIC CALENDAR (everyone views, admins edit)
const CAL_TYPES = { holiday: "Holiday", exam: "Exam", event: "Event", semester: "Semester" };
const FULL_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
let CAL = CAMPUS.calendar || { name: "Academic Calendar", events: [] };
CAL.events = CAL.events || [];
let calView = todayISO().slice(0, 7), calFilter = "upcoming", calSelected = null, calEditing = false;
const isoOf = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const shiftISO = (iso, n) => { const d = new Date(iso + "T00:00:00"); d.setDate(d.getDate() + n); return isoOf(d); };
// Each entry is for ["all"] years or a list of years (1-4); entries saved before years existed are for all years.
const calYears = (e) => (e.years && e.years.length ? e.years.map(String) : ["all"]);
const calForYear = (e, y) => calYears(e).includes("all") || calYears(e).includes(String(y));
const calYearsLabel = (e) => (calYears(e).includes("all") ? "" : `${calYears(e).map((y) => YEAR_NAMES[y].replace(" Year", "")).join(", ")} Year only`);
const myCal = () => CAL.events.filter((e) => calForYear(e, myYear));
const eventsOn = (iso) => myCal().filter((e) => e.date <= iso && iso <= e.end);
const holidayOn = (iso) => myCal().find((e) => e.type === "holiday" && e.date <= iso && iso <= e.end);
const spanDays = (e) => Math.round((new Date(e.end) - new Date(e.date)) / 86400000) + 1;
const rangeLabel = (e) => (e.end === e.date ? niceDate(e.date) : `${niceDate(e.date)} – ${niceDate(e.end)}`);
function whenLabel(e) {
  const t = todayISO();
  if (e.date <= t && t <= e.end) return "on now";
  return e.end < t ? "past" : daysUntilLabel(e.date);
}

function renderCalendar() {
  $("calSub").textContent = `${CAL.name || "Academic Calendar"} · confirmed holidays and important dates for ${YEAR_NAMES[myYear]}`;
  $("calNote").textContent = `📅 ${CAL.note || "Holidays and important dates are confirmed by CampusMate admins."}`;
  $("calEditBar").hidden = !IS_ADMIN || calEditing;
  $("calEdited").textContent = editedLine(CAL);
  const today = todayISO();
  const mine = myCal(), left = mine.filter((e) => e.type === "holiday" && e.end >= today);
  const sum = $("calSummary"); sum.replaceChildren();
  const pill = (big, small, cls = "") => { const d = el("div", `cal-pill ${cls}`); d.append(el("strong", "", big), el("span", "", small)); sum.appendChild(d); };
  const now = holidayOn(today);
  if (!mine.length) pill(CAL.events.length ? `No dates for ${YEAR_NAMES[myYear]} yet` : "No dates added yet", IS_ADMIN ? "Use “Edit academic calendar” to add holidays." : "Admins will add confirmed holidays here.");
  else {
    if (now) pill(`🎉 Today: ${now.title}`, "Holiday · no classes", "hol");
    const next = left.find((e) => e.date > today);
    if (next) pill(`Next holiday: ${next.title}`, `${rangeLabel(next)} · ${daysUntilLabel(next.date)}`, "hol");
    pill(String(left.length), left.length === 1 ? "holiday left this calendar" : "holidays left this calendar");
  }
  renderCalGrid(); renderCalList(); renderCalDay();
}

function renderCalGrid() {
  const [y, m] = calView.split("-").map(Number);
  $("calMonth").textContent = `${FULL_MONTHS[m - 1]} ${y}`;
  const grid = $("calGrid"); grid.replaceChildren();
  ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].forEach((d) => grid.appendChild(el("div", "cal-dow", d)));
  const lead = (new Date(y, m - 1, 1).getDay() + 6) % 7;
  const daysIn = new Date(y, m, 0).getDate();
  const today = todayISO();
  for (let i = 0; i < lead; i++) grid.appendChild(el("div", "cal-cell blank"));
  for (let d = 1; d <= daysIn; d++) {
    const iso = `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
    const evs = eventsOn(iso);
    const hol = evs.some((e) => e.type === "holiday");
    const sun = new Date(y, m - 1, d).getDay() === 0;
    const cell = el("button", `cal-cell${hol ? " holiday" : ""}${sun ? " sunday" : ""}${iso === today ? " today" : ""}${iso === calSelected ? " selected" : ""}`);
    cell.type = "button";
    cell.appendChild(el("span", "cal-num", String(d)));
    evs.slice(0, 2).forEach((e) => cell.appendChild(el("span", `cal-ev cal-${e.type}`, e.title)));
    if (evs.length > 2) cell.appendChild(el("span", "cal-more", `+${evs.length - 2} more`));
    if (evs.length) cell.appendChild(el("span", "cal-dots", "•".repeat(Math.min(evs.length, 3))));
    cell.title = evs.map((e) => `${CAL_TYPES[e.type]}: ${e.title}`).join("\n");
    cell.addEventListener("click", () => { calSelected = calSelected === iso ? null : iso; renderCalGrid(); renderCalDay(); });
    grid.appendChild(cell);
  }
}

function renderCalDay() {
  const box = $("calDay");
  box.hidden = !calSelected;
  if (!calSelected) return;
  box.replaceChildren();
  const d = new Date(calSelected + "T00:00:00");
  box.appendChild(el("h3", "", `${DAY_NAMES[d.getDay()]}, ${d.getDate()} ${FULL_MONTHS[d.getMonth()]} ${d.getFullYear()}`));
  const evs = eventsOn(calSelected);
  if (!evs.length) box.appendChild(el("p", "empty-line", d.getDay() === 0 ? "Sunday · no holidays or events added." : "No holidays or events on this day."));
  evs.forEach((e) => {
    const row = el("div", "cal-day-row");
    row.appendChild(el("span", `tag cal-${e.type}`, CAL_TYPES[e.type]));
    const txt = el("div");
    txt.appendChild(el("strong", "", e.title));
    txt.appendChild(el("span", "muted", ` ${rangeLabel(e)}${spanDays(e) > 1 ? ` · ${spanDays(e)} days` : ""}${calYearsLabel(e) ? ` · ${calYearsLabel(e)}` : ""}`));
    if (e.note) txt.appendChild(el("p", "cal-note", e.note));
    row.appendChild(txt);
    box.appendChild(row);
  });
}

function calItem(e) {
  const li = el("li", `cal-item${e.end < todayISO() ? " past" : ""}`);
  const [, mm, dd] = e.date.split("-");
  const badge = el("div", `date-badge${e.type === "holiday" ? " soon" : ""}`, String(Number(dd)));
  badge.appendChild(el("small", "", MONTHS[Number(mm) - 1]));
  const txt = el("div", "dl-text");
  txt.appendChild(el("strong", "", e.title));
  txt.appendChild(el("span", "", `${rangeLabel(e)}${spanDays(e) > 1 ? ` · ${spanDays(e)} days` : ""} · ${whenLabel(e)}${calYearsLabel(e) ? ` · ${calYearsLabel(e)}` : ""}`));
  if (e.note) txt.appendChild(el("span", "cal-note", e.note));
  li.append(badge, txt, el("span", `tag cal-${e.type}`, CAL_TYPES[e.type]));
  li.addEventListener("click", () => { calView = e.date.slice(0, 7); calSelected = e.date; renderCalGrid(); renderCalDay(); $("calGrid").scrollIntoView({ behavior: "smooth", block: "center" }); });
  return li;
}

function renderCalList() {
  const today = todayISO();
  const list = myCal().filter((e) => calFilter === "all" || (calFilter === "holiday" ? e.type === "holiday" : e.end >= today));
  $("calList").replaceChildren(...(list.length ? list.map(calItem)
    : [el("li", "empty-line", calFilter === "upcoming" ? "No upcoming dates added yet." : "Nothing added yet.")]));
}

document.querySelectorAll("[data-calf]").forEach((b) => b.addEventListener("click", () => {
  calFilter = b.dataset.calf;
  document.querySelectorAll("[data-calf]").forEach((x) => x.classList.toggle("active", x === b));
  renderCalList();
}));
function moveMonth(n) { const [y, m] = calView.split("-").map(Number); calView = isoOf(new Date(y, m - 1 + n, 1)).slice(0, 7); renderCalGrid(); }
$("calPrev")?.addEventListener("click", () => moveMonth(-1));
$("calNext")?.addEventListener("click", () => moveMonth(1));
$("calToday")?.addEventListener("click", () => { calView = todayISO().slice(0, 7); calSelected = todayISO(); renderCalGrid(); renderCalDay(); });

// ---------- calendar editor (admins and owner)
function calRow(e = {}) {
  const tr = el("tr");
  const td = (node) => { const c = el("td"); c.appendChild(node); tr.appendChild(c); return node; };
  const inp = (cls, type, val, ph, max) => { const i = el("input", cls); i.type = type; i.value = val || ""; if (ph) i.placeholder = ph; if (max) i.maxLength = max; return td(i); };
  inp("c-date", "date", e.date);
  inp("c-end", "date", e.end && e.end !== e.date ? e.end : "");
  inp("c-title", "text", e.title, "e.g. Diwali", 120);
  td(selectEl(Object.entries(CAL_TYPES), e.type || "holiday", "c-type"));
  const yrs = td(el("div", "c-years")), on = calYears(e);
  [1, 2, 3, 4].forEach((y) => {
    const lab = el("label"), cb = el("input");
    cb.type = "checkbox"; cb.value = String(y); cb.checked = on.includes(String(y));
    lab.append(cb, document.createTextNode(YEAR_NAMES[y].replace(" Year", "")));
    yrs.appendChild(lab);
  });
  inp("c-note", "text", e.note, "e.g. Classes resume on Monday", 200);
  const del = td(el("button", "remove", "×")); del.title = "Remove"; del.addEventListener("click", () => tr.remove());
  return tr;
}
if ($("calEditBtn")) {
  $("calEditBtn").addEventListener("click", () => {
    calEditing = true;
    $("calName").value = CAL.name || "";
    $("calRows").replaceChildren(...(CAL.events.length ? CAL.events : [{}]).map(calRow));
    $("calErr").hidden = true; $("calEditor").hidden = false;
    renderCalendar();
    $("calEditor").scrollIntoView({ behavior: "smooth", block: "start" });
  });
  $("calAddRow").addEventListener("click", () => $("calRows").appendChild(calRow()));
  $("calCancel").addEventListener("click", () => { calEditing = false; $("calEditor").hidden = true; renderCalendar(); });
  $("calSave").addEventListener("click", async () => {
    const events = [...$("calRows").children].map((tr) => ({
      date: tr.querySelector(".c-date").value, end: tr.querySelector(".c-end").value, title: tr.querySelector(".c-title").value,
      type: tr.querySelector(".c-type").value, note: tr.querySelector(".c-note").value,
      years: [...tr.querySelectorAll(".c-years input:checked")].map((c) => Number(c.value)),
    })).filter((e) => e.date || e.title.trim());
    $("calSave").disabled = true; $("calErr").hidden = true;
    try {
      const res = await fetch("/api/edit/calendar", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: $("calName").value, events }) });
      const data = await res.json().catch(() => ({ error: "Save failed." }));
      if (!res.ok) throw new Error(data.error);
      CAL = data.calendar; calEditing = false; $("calEditor").hidden = true; renderCalendar();
    } catch (err) { $("calErr").textContent = err.message; $("calErr").hidden = false; }
    finally { $("calSave").disabled = false; }
  });
}

function renderDashHolidays() {
  const today = todayISO();
  const ul = $("dashHolidays"); if (!ul) return;
  ul.replaceChildren();
  const hols = myCal().filter((e) => e.type === "holiday" && e.end >= today).slice(0, 3);
  hols.forEach((e) => {
    const li = el("li");
    const [, mm, dd] = e.date.split("-");
    const badge = el("div", `date-badge${daysUntil(e.date) <= 3 ? " soon" : ""}`, String(Number(dd)));
    badge.appendChild(el("small", "", MONTHS[Number(mm) - 1]));
    const txt = el("div", "dl-text");
    txt.appendChild(el("strong", "", e.title));
    txt.appendChild(el("span", "", `${rangeLabel(e)} · ${whenLabel(e)}`));
    li.append(badge, txt);
    li.addEventListener("click", () => { calView = e.date.slice(0, 7); calSelected = e.date; location.hash = "#calendar"; });
    ul.appendChild(li);
  });
  if (!hols.length) ul.appendChild(el("li", "empty-line", "No upcoming holidays added yet."));
}

// ========================================================== MY ATTENDANCE (private, saved to the account)
// Labs have their own attendance in college. When a lab shares its course code with lectures
// (CS-111 lab, CE-101(P)), it is tracked as "<code> Lab" so it gets its own card.
const LAB = " Lab";
function attLabCodes(sec = secOf(myYear, mySection)) {
  const theory = new Set(sec.classes.filter((c) => c.type !== "Lab" && c.type !== "Free").map((c) => c.code));
  return new Set(sec.classes.filter((c) => c.type === "Lab" && theory.has(c.code)).map((c) => c.code));
}
const attCodeOf = (c, labs = attLabCodes()) => (c.type === "Lab" && labs.has(c.code) ? c.code + LAB : c.code);
const attName = (code) => (code.endsWith(LAB) && subjName(myYear, code.slice(0, -LAB.length)) ? `${subjName(myYear, code.slice(0, -LAB.length))}${LAB}` : subjName(myYear, code));
// Lab marks saved before labs were split counted under the lecture code; move them to the lab's own code.
function attSplitLabs() {
  const labs = attLabCodes();
  let moved = 0;
  ATT.records.forEach((r) => { if (r.type === "Lab" && labs.has(r.code)) { r.code += LAB; moved++; } });
  return moved;
}

const ATT_KEY = `cm_attendance_${ME.id}`;
const attEmpty = () => ({ target: 75, records: [], base: {}, subjects: [] });
const attNorm = (a) => ({ ...attEmpty(), ...(a || {}), base: (a && a.base) || {}, records: (a && a.records) || [], subjects: (a && a.subjects) || [] });
let ATT = attNorm(store.get(ATT_KEY, null));
attSplitLabs();
let attDate = todayISO(), attDirty = false, attTimer = null, attOpen = null;

async function loadAttendance() {
  if (!$("page-attendance")) return; // attendance page is not on the site right now
  try {
    const res = await fetch("/api/attendance");
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Could not load attendance.");
    if (attDirty) return; // the student already changed something; their newer copy is being saved
    ATT = attNorm(data.attendance);
    if (attSplitLabs()) saveAttendance(); else store.set(ATT_KEY, ATT);
  } catch (err) {
    $("attErr").textContent = `Couldn't load your saved attendance (${err.message}). Showing the copy in this browser.`;
    $("attErr").hidden = false;
  }
  if (!$("page-attendance").hidden) renderAttendance();
  if (!$("page-dashboard").hidden) renderDashAttendance();
}

function saveAttendance() {
  store.set(ATT_KEY, ATT);
  $("attSaved").textContent = "Saving…";
  clearTimeout(attTimer);
  attTimer = setTimeout(async () => {
    try {
      const res = await fetch("/api/attendance", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(ATT) });
      const data = await res.json().catch(() => ({ error: "Save failed." }));
      if (!res.ok) throw new Error(data.error || "Save failed.");
      $("attSaved").textContent = "Saved to your account ✓";
      $("attErr").hidden = true;
    } catch (err) {
      $("attSaved").textContent = "";
      $("attErr").textContent = `Couldn't save to your account: ${err.message} Your marks are kept in this browser and will be saved the next time you mark something.`;
      $("attErr").hidden = false;
    }
  }, 500);
}
function attChanged() { attDirty = true; saveAttendance(); renderAttendance(); }

function attSubjects() {
  const sec = secOf(myYear, mySection), labs = attLabCodes(sec);
  const codes = [...new Set(sec.classes.filter((c) => c.type !== "Free").map((c) => attCodeOf(c, labs)))].sort();
  [...ATT.subjects, ...Object.keys(ATT.base), ...ATT.records.map((r) => r.code)].forEach((c) => { if (!codes.includes(c)) codes.push(c); });
  return codes;
}

function attStats(code) {
  const b = ATT.base[code] || { attended: 0, total: 0 };
  let attended = b.attended, total = b.total;
  ATT.records.forEach((r) => {
    if (r.code !== code || r.status === "cancelled") return;
    if (!String(r.slot || "").startsWith("x") && holidayOn(r.date)) return; // a holiday has no regular classes, even if marked before it was added
    total++; if (r.status === "present") attended++;
  });
  const t = ATT.target / 100, pct = total ? (attended / total) * 100 : null;
  let advice;
  if (!total) advice = "No classes marked yet.";
  else if (pct >= ATT.target) {
    const k = Math.floor(attended / t - total + 1e-9);
    advice = k > 0 ? `You can miss ${k} more class${k === 1 ? "" : "es"} and stay at ${ATT.target}%.` : "You're right at the line. Don't miss the next class.";
  } else if (t >= 1) advice = "You can't reach 100% any more.";
  else {
    const need = Math.ceil((t * total - attended) / (1 - t) - 1e-9);
    advice = `Attend the next ${need} class${need === 1 ? "" : "es"} in a row to reach ${ATT.target}%.`;
  }
  return { code, attended, total, pct, advice, ok: pct === null || pct >= ATT.target };
}
const attLevel = (s) => (s.pct === null ? "none" : s.pct >= ATT.target ? "good" : s.pct >= ATT.target - 10 ? "warn" : "bad");
const fmtPct = (p) => (p === null ? "–" : `${Number.isInteger(p) ? p : p.toFixed(1)}%`);

function attButtons(current, onPick, withCancel = true) {
  const box = el("div", "att-btns");
  [["present", "✓ Present"], ["absent", "✗ Absent"], ...(withCancel ? [["cancelled", "Cancelled"]] : [])].forEach(([s, label]) => {
    const b = el("button", `att-btn ${s}${current === s ? " active" : ""}`, label);
    b.type = "button";
    b.addEventListener("click", () => onPick(current === s ? "" : s));
    box.appendChild(b);
  });
  return box;
}
function setMark(id, info, status) {
  ATT.records = ATT.records.filter((r) => r.id !== id);
  if (status) ATT.records.push({ id, ...info, status });
  attChanged();
}
function attRow(title, sub, time, rec, onPick, extra) {
  const li = el("li", `att-row${rec ? " " + rec.status : ""}`);
  const info = el("div", "att-info");
  info.appendChild(el("small", "", time));
  info.appendChild(el("strong", "", title));
  if (sub) info.appendChild(el("span", "", sub));
  li.append(info, attButtons(rec ? rec.status : "", onPick, !extra));
  return li;
}

function renderAttendance() {
  if (![...$("attTarget").options].some((o) => Number(o.value) === ATT.target)) {
    const o = el("option", "", `${ATT.target}%`); o.value = ATT.target; $("attTarget").appendChild(o);
  }
  $("attTarget").value = String(ATT.target);
  $("attDate").value = attDate;
  const sec = secOf(myYear, mySection);
  const dayName = DAY_NAMES[new Date(attDate + "T00:00:00").getDay()];
  const hol = holidayOn(attDate);
  $("attDayLabel").textContent = `${dayName}, ${dmy(attDate)}${attDate === todayISO() ? " · today" : ""} · ${myLabel()}`;
  $("attHoliday").hidden = !hol;
  if (hol) $("attHoliday").textContent = `🎉 ${hol.title}: holiday in the academic calendar, so no regular classes.`;
  const classes = hol ? [] : dayClasses(sec, dayName, myGroup).filter((c) => c.type !== "Free");
  $("attGroupHint").hidden = !!myGroup || !classes.some((c) => c.group);

  const ul = $("attClasses"); ul.replaceChildren();
  const labs = attLabCodes(sec);
  classes.forEach((c) => {
    const code = attCodeOf(c, labs);
    const slot = `${c.start}${c.group ? "-" + c.group : ""}`;
    const id = `${attDate}|${c.code}|${slot}`;
    const rec = ATT.records.find((r) => r.id === id);
    ul.appendChild(attRow(c.label, [c.type, attName(code)].filter(Boolean).join(" · "), span(c), rec,
      (status) => setMark(id, { date: attDate, code, slot, type: c.type }, status)));
  });
  ATT.records.filter((r) => r.date === attDate && r.slot.startsWith("x")).forEach((r) => {
    ul.appendChild(attRow(r.code, ["Extra class", attName(r.code)].filter(Boolean).join(" · "), "Extra", r,
      (status) => setMark(r.id, { date: r.date, code: r.code, slot: r.slot, type: r.type }, status), true));
  });
  if (!ul.children.length) {
    ul.appendChild(el("li", "empty-line", hol ? "Enjoy the holiday! 🎉"
      : sec.classes.length ? `No classes on ${dayName} in your timetable.`
      : "Your section's timetable hasn't been added yet. You can still add subjects and extra classes below."));
  }

  const subs = attSubjects();
  $("attExtraCode").replaceChildren(...subs.map((c) => { const o = el("option", "", attName(c) ? `${c} · ${attName(c)}` : c); o.value = c; return o; }));
  $("attExtraCode").disabled = $("attExtraP").disabled = $("attExtraA").disabled = !subs.length;
  renderAttSummary(subs);
}

function renderAttSummary(subs) {
  const stats = subs.map(attStats);
  const a = stats.reduce((n, s) => n + s.attended, 0), t = stats.reduce((n, s) => n + s.total, 0);
  $("attOverall").textContent = fmtPct(t ? (a / t) * 100 : null);
  $("attCount").textContent = `${a} / ${t}`;
  $("attLow").textContent = stats.filter((s) => !s.ok).length;
  const grid = $("attSubjects"); grid.replaceChildren();
  if (!stats.length) grid.appendChild(el("p", "empty-line card", "No subjects yet. Your timetable's subjects appear here automatically, or add one on the left."));
  stats.forEach((s) => grid.appendChild(attCard(s)));
}

function attCard(s) {
  const card = el("div", `att-card ${attLevel(s)}`);
  const head = el("div", "att-head");
  const left = el("div");
  left.appendChild(el("span", "as-code", s.code));
  const nm = attName(s.code);
  if (nm) left.appendChild(el("div", "att-name", nm));
  head.append(left, el("div", "att-pct", s.pct === null ? "Not marked" : fmtPct(s.pct)));
  card.appendChild(head);
  const bar = el("div", "att-bar");
  const fill = el("span"); fill.style.width = `${s.pct ?? 0}%`;
  const mark = el("i"); mark.style.left = `${ATT.target}%`; mark.title = `Target ${ATT.target}%`;
  bar.append(fill, mark);
  card.appendChild(bar);
  card.appendChild(el("div", "att-count", `${s.attended} of ${s.total} classes attended`));
  card.appendChild(el("p", "att-advice", s.advice));
  const actions = el("div", "notice-actions");
  const hist = el("button", "ghost small", "History");
  const base = el("button", "ghost small", "Starting count");
  actions.append(hist, base);
  if (ATT.subjects.includes(s.code) && !ATT.records.some((r) => r.code === s.code) && !ATT.base[s.code]) {
    const rm = el("button", "ghost small danger", "Remove");
    rm.addEventListener("click", () => { ATT.subjects = ATT.subjects.filter((c) => c !== s.code); attChanged(); });
    actions.appendChild(rm);
  }
  card.appendChild(actions);
  const panel = el("div", "att-panel");
  const open = (kind) => {
    attOpen = { code: s.code, kind };
    panel.hidden = false;
    (kind === "hist" ? fillHistory : fillBase)(panel, s.code);
  };
  panel.hidden = true;
  if (attOpen && attOpen.code === s.code) open(attOpen.kind);
  hist.addEventListener("click", () => { if (!panel.hidden && attOpen.kind === "hist") { panel.hidden = true; attOpen = null; } else open("hist"); });
  base.addEventListener("click", () => { if (!panel.hidden && attOpen.kind === "base") { panel.hidden = true; attOpen = null; } else open("base"); });
  card.appendChild(panel);
  return card;
}

function fillHistory(panel, code) {
  panel.replaceChildren();
  const recs = ATT.records.filter((r) => r.code === code).sort((a, b) => b.date.localeCompare(a.date) || b.slot.localeCompare(a.slot));
  const b = ATT.base[code];
  if (!recs.length && !b) { panel.appendChild(el("p", "empty-line", "Nothing marked yet.")); return; }
  const ul = el("ul", "att-hist");
  recs.forEach((r) => {
    const li = el("li", r.status);
    const start = parseInt(r.slot, 10);
    const time = r.slot.startsWith("x") ? "Extra class" : Number.isNaN(start) ? "" : hh(start);
    li.appendChild(el("span", "", `${niceDate(r.date)}${time ? " · " + time : ""}`));
    li.appendChild(el("strong", "", { present: "Present", absent: "Absent", cancelled: "Cancelled" }[r.status]));
    const del = el("button", "remove", "×"); del.title = "Delete this entry";
    del.addEventListener("click", () => { ATT.records = ATT.records.filter((x) => x.id !== r.id); attChanged(); });
    li.appendChild(del);
    ul.appendChild(li);
  });
  if (b) ul.appendChild(el("li", "base", `Starting count: ${b.attended} of ${b.total} attended`));
  panel.appendChild(ul);
}

function fillBase(panel, code) {
  panel.replaceChildren();
  const b = ATT.base[code] || { attended: 0, total: 0 };
  panel.appendChild(el("p", "muted", "Classes held before you started tracking here (for example from the start of the semester). They're added to your marked classes."));
  const row = el("div", "att-base");
  const num = (label, val) => { const l = el("label", "", label); const i = el("input"); i.type = "number"; i.min = 0; i.max = 1000; i.value = val; l.appendChild(i); row.appendChild(l); return i; };
  const att = num("Attended", b.attended), tot = num("Held", b.total);
  const save = el("button", "ghost small", "Save");
  const err = el("span", "att-base-err");
  save.addEventListener("click", () => {
    const A = Number(att.value || 0), T = Number(tot.value || 0);
    if (!Number.isInteger(A) || !Number.isInteger(T) || A < 0 || T < 0 || T > 1000) { err.textContent = "Use whole numbers."; return; }
    if (A > T) { err.textContent = "Attended can't be more than held."; return; }
    if (T) ATT.base[code] = { attended: A, total: T }; else delete ATT.base[code];
    attOpen = null;
    attChanged();
  });
  row.append(save, err);
  panel.appendChild(row);
}

$("attTarget")?.addEventListener("change", (e) => { ATT.target = Number(e.target.value) || 75; attChanged(); });
$("attDate")?.addEventListener("change", (e) => { if (e.target.value) { attDate = e.target.value; renderAttendance(); } });
$("attPrev")?.addEventListener("click", () => { attDate = shiftISO(attDate, -1); renderAttendance(); });
$("attNext")?.addEventListener("click", () => { attDate = shiftISO(attDate, 1); renderAttendance(); });
$("attToday")?.addEventListener("click", () => { attDate = todayISO(); renderAttendance(); });
function addExtra(status) {
  const code = $("attExtraCode").value;
  if (!code) return;
  const slot = `x${Date.now().toString(36)}`;
  ATT.records.push({ id: `${attDate}|${code}|${slot}`, date: attDate, code, slot, type: "Extra", status });
  attChanged();
}
$("attExtraP")?.addEventListener("click", () => addExtra("present"));
$("attExtraA")?.addEventListener("click", () => addExtra("absent"));
$("attAddSubject")?.addEventListener("click", () => {
  const code = $("attNewSubject").value.replace(/\s+/g, " ").trim().slice(0, 40);
  if (!code) return;
  if (!attSubjects().includes(code)) ATT.subjects.push(code);
  $("attNewSubject").value = "";
  attChanged();
});
$("attNewSubject")?.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); $("attAddSubject").click(); } });

function renderDashAttendance() {
  const box = $("dashAttendance"); if (!box) return;
  box.replaceChildren();
  const stats = attSubjects().map(attStats).filter((s) => s.total);
  if (!stats.length) { box.appendChild(el("p", "empty-line", "Not tracking yet. Mark today's classes as present or absent to see your attendance here.")); return; }
  const a = stats.reduce((n, s) => n + s.attended, 0), t = stats.reduce((n, s) => n + s.total, 0);
  const top = el("div", "att-dash-top");
  top.append(el("strong", "", fmtPct((a / t) * 100)), el("span", "muted", `overall · ${a} of ${t} classes · target ${ATT.target}%`));
  box.appendChild(top);
  stats.sort((x, y) => x.pct - y.pct).slice(0, 3).forEach((s) => {
    const row = el("div", `att-dash-row ${attLevel(s)}`);
    row.append(el("span", "as-code", s.code), el("span", "att-dash-adv", s.advice), el("strong", "", fmtPct(s.pct)));
    row.addEventListener("click", () => { location.hash = "#attendance"; });
    box.appendChild(row);
  });
}

loadAttendance();

// ========================================================== ASK CAMPUSMATE
let askHistory = [];

function clientContext() {
  const deadlines = [];
  history.forEach((h) => h.result.deadlines.forEach((d) => deadlines.push({ date: d.date_iso || d.date, what: d.what, from: h.result.title })));
  const plan = store.get("cm_plan", null);
  const att = attSubjects().map(attStats).filter((s) => s.total)
    .map((s) => ({ code: s.code, attended: s.attended, total: s.total, percent: Number(s.pct.toFixed(1)), advice: s.advice }));
  return { deadlines: deadlines.slice(0, 20), plan: plan ? plan.result.days : [], attendance: { target: ATT.target, subjects: att } };
}

function addMsg(cls, text) {
  const m = el("div", `msg ${cls}`, text);
  $("askLog").appendChild(m);
  $("askLog").hidden = false;
  $("askLog").scrollTop = $("askLog").scrollHeight;
  return m;
}

async function askQuestion(q) {
  q = q.trim();
  if (!q) return;
  $("askInput").value = "";
  $("askSuggest").hidden = true;
  addMsg("user", q);
  const typing = el("div", "typing", "CampusMate is checking your timetable, notices and hostel info…");
  $("askLog").appendChild(typing);
  $("askLog").scrollTop = $("askLog").scrollHeight;
  $("askBtn").disabled = true;
  try {
    const res = await fetch("/api/ask", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: q, year: myYear, section: mySection, group: myGroup, branch: examBranch(), client: clientContext(), history: askHistory }),
    });
    const data = await res.json().catch(() => ({ error: "The server sent an unreadable reply." }));
    typing.remove();
    if (!res.ok || data.error) throw new Error(data.error || "Something went wrong.");
    const m = addMsg(`bot${data.found ? "" : " notfound"}`, data.answer);
    if (data.points.length) {
      const ul = el("ul"); data.points.forEach((p) => ul.appendChild(el("li", "", p))); m.appendChild(ul);
    }
    if (data.sources.length) {
      const src = el("div", "msg-src"); data.sources.forEach((x) => src.appendChild(el("span", "", x))); m.appendChild(src);
    }
    m.appendChild(el("div", "msg-meta", `Gemma 4 · ${data.model} · ${data.seconds}s`));
    $("askLog").scrollTop = $("askLog").scrollHeight;
    askHistory.push({ q, a: [data.answer, ...data.points].join(" ") });
    askHistory = askHistory.slice(-4);
  } catch (err) {
    typing.remove();
    addMsg("err", err.message);
  } finally {
    $("askBtn").disabled = false;
  }
}

$("askForm").addEventListener("submit", (e) => { e.preventDefault(); askQuestion($("askInput").value); });

// ========================================================== ACCOUNT
$("logoutBtn").addEventListener("click", async () => { await fetch("/api/logout", { method: "POST" }); location.href = "/login"; });

// ========================================================== CAMPUS CHAT
let chatTimer = null, chatLatest = 0;
let chatChannel = null; // null = public campus chat, else a private channel id
const chatUrl = () => (chatChannel ? `/api/channels/${chatChannel}/messages` : "/api/chat");
// same rules as can_delete_message on the server: own messages always; the owner anyone's,
// a co-owner anyone's but the owner's, an admin anyone's but the owner's and co-owners'
function canDeleteMsg(m) {
  if (m.user_id === ME.id || ME.role === "owner") return true;
  if (ME.role === "coowner") return m.role !== "owner";
  if (ME.role === "admin") return !["owner", "coowner"].includes(m.role);
  return false;
}
function chatMsg(m) {
  const mine = m.user_id === ME.id;
  const wrap = el("div", `chat-msg role-${m.role}${mine ? " mine" : ""}`);
  wrap.dataset.id = m.id;
  if (m.role !== "user") wrap.appendChild(el("div", "chat-banner", ROLE_LABEL[m.role]));
  const head = el("div", "chat-head");
  head.appendChild(el("strong", `chat-name role-${m.role}`, m.name));
  head.appendChild(el("span", `role-tag role-${m.role}`, ROLE_LABEL[m.role]));
  head.appendChild(el("span", "chat-time", fmtTime(m.created_at)));
  const canBan = IS_ADMIN && m.user_id !== ME.id && m.role !== "owner" && (m.role !== "coowner" || ME.role === "owner") && (m.role !== "admin" || IS_OWNER_LEVEL);
  if (canBan) {
    const ban = el("button", "chat-del", "Ban");
    ban.addEventListener("click", () => {
      if (wrap.querySelector(".ban-pick")) return;
      const pick = el("div", "ban-pick");
      const sel = selectEl([["1", "1 hour"], ["24", "1 day"], ["168", "7 days"], ["0", "Permanently"]], "24", "");
      const ok = el("button", "ghost small danger", `Ban ${m.name}`);
      const cancel = el("button", "ghost small", "Cancel");
      ok.addEventListener("click", async () => {
        const res = await fetch("/api/chat/ban", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ user_id: m.user_id, hours: Number(sel.value) }) });
        const d = await res.json().catch(() => ({}));
        if (res.ok) { pick.replaceChildren(el("span", "muted", `${m.name} is banned from chat.`)); loadBans(); }
        else ok.textContent = d.error || "Could not ban";
      });
      cancel.addEventListener("click", () => pick.remove());
      pick.append(el("span", "muted", "Ban from chat for"), sel, ok, cancel);
      wrap.appendChild(pick);
    });
    head.appendChild(ban);
  }
  if (canDeleteMsg(m)) {
    const del = el("button", "chat-del", "Delete"); del.title = "Delete this message";
    const delUrl = chatChannel ? `/api/channels/${chatChannel}/messages/${m.id}/delete` : `/api/chat/${m.id}/delete`;
    del.addEventListener("click", async () => {
      if (!confirm(mine ? "Delete your message?" : `Delete ${m.name}'s message?`)) return;
      const res = await fetch(delUrl, { method: "POST" });
      if (res.ok || res.status === 404) { wrap.remove(); $("chatEmpty").hidden = $("chatLog").querySelectorAll(".chat-msg").length > 0; }
      else del.textContent = (await res.json().catch(() => ({}))).error || "Could not delete";
    });
    head.appendChild(del);
  }
  wrap.append(head, el("div", "chat-body", m.body));
  return wrap;
}
async function pollChat() {
  const channel = chatChannel;
  try {
    const res = await fetch(`${chatUrl()}?after=${chatLatest}`);
    const data = await res.json();
    if (channel !== chatChannel) return; // switched channels while this was loading
    if (res.status === 404 && channel) { selectChannel(null); loadChannels(); return; } // removed from the channel
    if (!res.ok) return;
    showBanned(data.banned);
    const log = $("chatLog");
    const atBottom = log.scrollHeight - log.scrollTop - log.clientHeight < 60;
    // polls can overlap on a slow server; only show messages newer than the last one shown
    // drop messages someone deleted (anything older than the window the server sent stays)
    if (data.ids && data.ids.length) {
      const keep = new Set(data.ids), oldest = Math.min(...data.ids);
      log.querySelectorAll(".chat-msg").forEach((w) => { const id = Number(w.dataset.id); if (id >= oldest && !keep.has(id)) w.remove(); });
    } else if (data.ids) log.querySelectorAll(".chat-msg").forEach((w) => w.remove());
    data.messages.filter((m) => m.id > chatLatest).forEach((m) => { log.appendChild(chatMsg(m)); chatLatest = m.id; });
    $("chatEmpty").hidden = log.querySelectorAll(".chat-msg").length > 0;
    if (data.messages.length && (atBottom || chatLatest === data.latest)) log.scrollTop = log.scrollHeight;
  } catch { /* offline: try again next tick */ }
}
function showBanned(b) {
  $("chatBanned").hidden = !b;
  $("chatInput").disabled = !!b; $("chatSend").disabled = !!b;
  if (b) $("chatBanned").textContent = `🚫 You are banned from the campus chat${b.permanent ? "" : " until " + fmtTime(b.until)}${b.by ? " (by " + b.by + ")" : ""}. You can still read messages.`;
}
async function loadBans() {
  if (!$("banList")) return;
  const res = await fetch("/api/chat/bans");
  if (!res.ok) return;
  const bans = (await res.json()).bans;
  $("banCount").textContent = bans.length;
  const ul = $("banList"); ul.replaceChildren();
  if (!bans.length) ul.appendChild(el("li", "empty-line", "Nobody is banned."));
  bans.forEach((b) => {
    const li = el("li");
    const txt = el("div");
    txt.append(el("strong", "", b.name), el("span", `role-tag role-${b.role}`, ROLE_LABEL[b.role]),
      el("div", "muted", `${b.permanent ? "Permanent" : "Until " + fmtTime(b.until)} · banned by ${b.by || "admin"}`));
    const un = el("button", "ghost small", "Unban");
    un.addEventListener("click", async () => { await fetch("/api/chat/unban", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ user_id: b.id }) }); loadBans(); });
    li.append(txt, un); ul.appendChild(li);
  });
}
let chatTicks = 0;
function startChat() {
  loadBans(); loadChannels();
  // new channels you were added to show up within ~30 seconds
  if (!chatTimer) { pollChat(); chatTimer = setInterval(() => { pollChat(); if (++chatTicks % 10 === 0) loadChannels(); }, 3000); }
}
function stopChat() { clearInterval(chatTimer); chatTimer = null; }
$("chatForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const body = $("chatInput").value.trim();
  if (!body) return;
  $("chatSend").disabled = true;
  try {
    const res = await fetch(chatUrl(), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ body }) });
    const data = await res.json();
    if (!res.ok) { if (data.banned) showBanned(data.banned); throw new Error(data.error); }
    $("chatInput").value = ""; $("chatHint").textContent = chatHintText();
    await pollChat(); $("chatLog").scrollTop = $("chatLog").scrollHeight;
  } catch (err) { $("chatHint").textContent = err.message; }
  finally { $("chatSend").disabled = false; }
});

// ---------------------------------------------------------- private channels
let channels = [], channelInfo = null, people = null;
const chatHintText = () => (chatChannel
  ? "🔒 Private channel: only its members can see these messages."
  : "Messages are visible to all logged-in users. Admins can remove messages.");
const postJson = (url, body) => fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body || {}) });

async function loadChannels() {
  const res = await fetch("/api/channels");
  if (!res.ok) return;
  const data = await res.json();
  channels = data.channels;
  if (chatChannel && !channels.some((c) => c.id === chatChannel)) { selectChannel(null); return; }
  renderChannelBar(data.can_create);
}
function renderChannelBar(canCreate) {
  const bar = $("channelBar"); bar.replaceChildren();
  const chip = (label, id) => {
    const b = el("button", `chip${chatChannel === id ? " active" : ""}`, label); b.type = "button";
    b.addEventListener("click", () => selectChannel(id));
    bar.appendChild(b);
  };
  chip("🌐 Campus chat", null);
  channels.forEach((c) => chip(`🔒 ${c.name}`, c.id));
  if (canCreate && $("channelCreate")) {
    const add = el("button", "chip", "＋ New private channel"); add.type = "button";
    add.addEventListener("click", openChannelCreate);
    bar.appendChild(add);
  }
}
function selectChannel(id) {
  chatChannel = id;
  chatLatest = 0;
  const log = $("chatLog");
  log.querySelectorAll(".chat-msg").forEach((n) => n.remove());
  $("chatEmpty").hidden = false;
  const ch = channels.find((c) => c.id === id);
  $("chatInput").placeholder = ch ? `Message ${ch.name}…` : "Message everyone…";
  $("chatHint").textContent = chatHintText();
  renderChannelBar(IS_ADMIN);
  loadChannelInfo();
  pollChat();
}
async function loadChannelInfo() {
  const box = $("channelInfo");
  if (!chatChannel) { box.hidden = true; channelInfo = null; return; }
  const id = chatChannel;
  const res = await fetch(`/api/channels/${id}`);
  if (id !== chatChannel) return;
  if (!res.ok) { selectChannel(null); loadChannels(); return; }
  channelInfo = await res.json();
  box.hidden = false;
  $("channelTitle").textContent = channelInfo.channel.name;
  $("channelCount").textContent = `${channelInfo.members.length} member${channelInfo.members.length === 1 ? "" : "s"}`;
  const ul = $("channelMembers"); ul.replaceChildren();
  channelInfo.members.forEach((m) => {
    const li = el("li"), txt = el("div");
    txt.append(el("strong", "", m.id === ME.id ? `${m.name} (you)` : m.name), el("span", `role-tag role-${m.role}`, ROLE_LABEL[m.role]),
      el("div", "muted", m.email));
    li.appendChild(txt);
    if (channelInfo.can_manage && m.id !== ME.id && (!["owner", "coowner"].includes(m.role) || outranks(m.role))) {
      const rm = el("button", "ghost small danger", "Remove");
      rm.addEventListener("click", async () => {
        const r = await postJson(`/api/channels/${id}/members/${m.id}/remove`);
        if (r.ok) loadChannelInfo(); else rm.textContent = (await r.json().catch(() => ({}))).error || "Could not remove";
      });
      li.appendChild(rm);
    }
    ul.appendChild(li);
  });
  const manage = $("channelManage"); manage.replaceChildren();
  if (channelInfo.can_manage) {
    const all = await loadPeople();
    const inside = new Set(channelInfo.members.map((m) => m.id));
    const outside = all.filter((p) => !inside.has(p.id));
    if (outside.length) {
      const sel = selectEl(outside.map((p) => [String(p.id), `${p.name} (${p.email})`]), String(outside[0].id), "");
      const add = el("button", "ghost small", "Add member"); add.type = "button";
      add.addEventListener("click", async () => {
        const r = await postJson(`/api/channels/${id}/members`, { user_ids: [Number(sel.value)] });
        if (r.ok) loadChannelInfo(); else add.textContent = (await r.json().catch(() => ({}))).error || "Could not add";
      });
      manage.append(sel, add);
    }
    const del = el("button", "ghost small danger", "Delete channel"); del.type = "button";
    del.addEventListener("click", async () => {
      if (!confirm(`Delete "${channelInfo.channel.name}" and all its messages for everyone?`)) return;
      const r = await postJson(`/api/channels/${id}/delete`);
      if (r.ok) { selectChannel(null); loadChannels(); }
    });
    manage.appendChild(del);
  }
  const leave = el("button", "ghost small", "Leave channel"); leave.type = "button";
  leave.addEventListener("click", async () => {
    if (!confirm(`Leave "${channelInfo.channel.name}"? You will need an admin to add you back.`)) return;
    const r = await postJson(`/api/channels/${id}/members/${ME.id}/remove`);
    if (r.ok) { selectChannel(null); loadChannels(); }
  });
  manage.appendChild(leave);
}
async function loadPeople() {
  if (people) return people;
  const res = await fetch("/api/people");
  people = res.ok ? (await res.json()).people : [];
  return people;
}
async function openChannelCreate() {
  const form = $("channelCreate");
  form.hidden = false;
  $("channelName").value = ""; $("channelPeopleFilter").value = ""; $("channelCreateMsg").textContent = "";
  people = null; // pick up anyone who signed up since
  const box = $("channelPeople"); box.replaceChildren();
  (await loadPeople()).filter((p) => p.id !== ME.id).forEach((p) => {
    const lab = el("label"); lab.dataset.search = `${p.name} ${p.email}`.toLowerCase();
    const cb = el("input"); cb.type = "checkbox"; cb.value = p.id;
    lab.append(cb, el("span", "", p.name), el("span", `role-tag role-${p.role}`, ROLE_LABEL[p.role]), el("span", "muted", p.email));
    box.appendChild(lab);
  });
  if (!box.children.length) box.appendChild(el("p", "muted", "Nobody else has an account yet."));
  $("channelName").focus();
}
if ($("channelCreate")) {
  $("channelPeopleFilter").addEventListener("input", (e) => {
    const q = e.target.value.trim().toLowerCase();
    $("channelPeople").querySelectorAll("label").forEach((l) => (l.hidden = q && !l.dataset.search.includes(q)));
  });
  $("channelCreateCancel").addEventListener("click", () => ($("channelCreate").hidden = true));
  $("channelCreate").addEventListener("submit", async (e) => {
    e.preventDefault();
    const member_ids = [...$("channelPeople").querySelectorAll("input:checked")].map((c) => Number(c.value));
    $("channelCreateBtn").disabled = true;
    try {
      const res = await postJson("/api/channels", { name: $("channelName").value, member_ids });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error);
      $("channelCreate").hidden = true;
      await loadChannels();
      selectChannel(data.id);
    } catch (err) { $("channelCreateMsg").textContent = err.message; }
    finally { $("channelCreateBtn").disabled = false; }
  });
}

// ========================================================== PUBLISH (admins)
let pubKind = "notice";
function setPubKind(k) {
  pubKind = k;
  document.querySelectorAll("#pubKinds .chip").forEach((c) => c.classList.toggle("active", c.dataset.kind === k));
  document.querySelectorAll(".pub-assign").forEach((x) => (x.hidden = k !== "assignment"));
  $("pubBodyLabel").textContent = { notice: "Notice text", announcement: "Announcement text", assignment: "Description / instructions", material: "Description (optional)" }[k];
  $("pubPdfLabel").textContent = k === "material" ? "PDF file (required)" : "Attach PDF (optional)";
}
function renderPublish() {
  const list = $("pubList"); list.replaceChildren();
  $("pubCount").textContent = `${POSTS.length} item${POSTS.length === 1 ? "" : "s"}`;
  if (!POSTS.length) list.appendChild(el("p", "empty-line", "Nothing published yet."));
  POSTS.forEach((p) => {
    const row = el("div", "pub-item");
    const top = el("div", "pub-item-top");
    top.appendChild(el("span", "kind-badge", p.kind));
    top.appendChild(el("strong", "", p.title));
    row.appendChild(top);
    const aud = `${p.years.includes("all") ? "All years" : "Year " + p.years.join(", ")} · ${p.sections.includes("all") ? "all sections" : "Sec " + p.sections.join(", ")}`;
    row.appendChild(el("div", "muted", aud + (p.code ? ` · ${p.code}` : "") + (p.due ? ` · due ${p.due}` : "")));
    row.appendChild(pubLine(p));
    const act = el("div", "notice-actions");
    if (p.pdf) act.appendChild(pdfLink(p.pdf, "📄 PDF"));
    if (canDelete(p)) {
      const del = el("button", "ghost small danger", "Delete");
      del.addEventListener("click", async () => {
        if (del.dataset.confirm !== "1") { del.dataset.confirm = "1"; del.textContent = "Click again to delete"; return; }
        const res = await fetch(`/api/posts/${p.db_id}/delete`, { method: "POST" });
        if (res.ok) { location.hash = "#publish"; location.reload(); }
      });
      act.appendChild(del);
    }
    row.appendChild(act);
    list.appendChild(row);
  });
}
if (IS_ADMIN && $("pubForm")) {
  setPubKind("notice");
  document.querySelectorAll("#pubKinds .chip").forEach((c) => c.addEventListener("click", () => setPubKind(c.dataset.kind)));
  $("pubForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const f = new FormData();
    f.append("kind", pubKind);
    f.append("title", $("pubTitle").value);
    f.append("body", $("pubBody").value);
    f.append("code", $("pubCode").value);
    if (pubKind === "assignment") { f.append("due", $("pubDue").value); f.append("marks", $("pubMarks").value); }
    document.querySelectorAll("#pubYears input:checked").forEach((i) => f.append("years", i.value));
    document.querySelectorAll("#pubSections input:checked").forEach((i) => f.append("sections", i.value));
    if ($("pubPdf").files[0]) f.append("pdf", $("pubPdf").files[0]);
    $("pubBtn").disabled = true; $("pubError").hidden = true;
    try {
      const res = await fetch("/api/posts", { method: "POST", body: f });
      const data = await res.json().catch(() => ({ error: "Upload failed." }));
      if (!res.ok) throw new Error(data.error);
      location.hash = "#publish"; location.reload();
    } catch (err) { $("pubError").textContent = err.message; $("pubError").hidden = false; }
    finally { $("pubBtn").disabled = false; }
  });
}

// ========================================================== MANAGE ADMINS (owner and co-owners)
let USERS = [];
async function loadUsers() {
  await loadHandover();
  const res = await fetch("/api/users");
  if (!res.ok) return;
  const data = await res.json();
  USERS = data.users;
  renderUsers();
}
function renderUsers() {
  const q = ($("userSearch").value || "").toLowerCase();
  const body = $("userBody"); body.replaceChildren();
  const admins = USERS.filter((u) => !["user", "journalist"].includes(u.role)).length;
  $("userCount").textContent = `${USERS.length} account${USERS.length === 1 ? "" : "s"} · ${admins} owner/co-owner/admin · ${USERS.length - admins} student${USERS.length - admins === 1 ? "" : "s"}`;
  USERS.filter((u) => `${u.name} ${u.email}`.toLowerCase().includes(q)).forEach((u) => {
    const tr = el("tr");
    const who = el("td"); who.append(el("strong", "", u.name), el("div", "muted", u.email)); tr.appendChild(who);
    const rt = el("td"); rt.appendChild(el("span", `role-tag role-${u.role}`, ROLE_LABEL[u.role])); tr.appendChild(rt);
    tr.appendChild(el("td", "muted", fmtTime(u.created_at)));
    tr.appendChild(el("td", "muted", u.last_login_at ? fmtTime(u.last_login_at) : "—"));
    tr.appendChild(el("td", u.chat_banned ? "banned-cell" : "muted", u.chat_banned ? "🚫 Banned" : "OK"));
    const act = el("td");
    const setRole = (label, cls, role) => {
      const b = el("button", cls, label);
      b.addEventListener("click", async () => {
        const res = await fetch(`/api/users/${u.id}/role`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ role }) });
        if (res.ok) loadUsers();
        else alert((await res.json().catch(() => ({}))).error || "Could not change the role.");
      });
      act.appendChild(b);
    };
    if (u.id === ME.id) act.appendChild(el("span", "muted", "You"));
    else if (u.role === "owner") act.appendChild(el("span", "muted", "Owner"));
    else if (u.role === "coowner") {
      if (ME.role === "owner") setRole("Remove co-owner", "ghost small danger", "admin");
      else act.appendChild(el("span", "muted", "Co-owner"));
    } else {
      if (ME.role === "coowner") {
        if (HANDOVER.outgoing && HANDOVER.outgoing.to.id === u.id) act.appendChild(el("span", "muted", "Your successor (waiting) "));
        else {
          const b = el("button", "ghost small", "Choose as successor");
          b.addEventListener("click", () => chooseSuccessor(u));
          act.appendChild(b);
        }
      }
      if (u.role === "admin") setRole("Remove admin", "ghost small danger", "user");
      else setRole("Make admin", "ai-btn", "admin");
      if (u.role === "user") setRole("Make journalist", "ghost small", "journalist");
      if (u.role === "journalist") setRole("Remove journalist", "ghost small danger", "user");
      if (ME.role === "owner") setRole("Make co-owner", "ghost small", "coowner");
    }
    tr.appendChild(act);
    body.appendChild(tr);
  });
}
if ($("userSearch")) $("userSearch").addEventListener("input", renderUsers);

// ========================================================== CO-OWNER HANDOVER (yearly successor)
// A co-owner chooses a successor; the successor accepts from a banner; the owner can cancel any handover.
let HANDOVER = { outgoing: null, incoming: [], all: [] };
async function handoverPost(url, body, failMsg) {
  const res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body || {}) });
  if (!res.ok) { alert((await res.json().catch(() => ({}))).error || failMsg); return false; }
  return true;
}
async function chooseSuccessor(u) {
  const replacing = HANDOVER.outgoing ? ` This replaces your earlier choice (${HANDOVER.outgoing.to.name}).` : "";
  if (!confirm(`Hand over your co-owner seat to ${u.name}?\n\nWhen they accept, they become co-owner and you become an admin.${replacing}`)) return;
  if (await handoverPost("/api/handover", { to: u.id }, "Could not choose a successor.")) loadUsers();
}
async function loadHandover() {
  const res = await fetch("/api/handover");
  if (!res.ok) return;
  HANDOVER = await res.json();
  renderHandoverBanner();
  renderHandoverCard();
}
function renderHandoverBanner() {
  const box = $("handoverBanner"); if (!box) return;
  box.replaceChildren();
  HANDOVER.incoming.forEach((h) => {
    const row = el("div", "handover-row");
    row.appendChild(el("span", "", `${h.from.name} chose you as the next co-owner. If you accept, you become co-owner and ${h.from.name} becomes an admin.`));
    const btns = el("div", "handover-actions");
    const yes = el("button", "ai-btn", "Accept");
    yes.addEventListener("click", async () => {
      if (!confirm("Become co-owner of CampusMate?")) return;
      if (await handoverPost(`/api/handover/${h.from.id}/accept`, {}, "Could not accept.")) location.reload();
    });
    const no = el("button", "ghost small", "Decline");
    no.addEventListener("click", async () => {
      if (await handoverPost(`/api/handover/${h.from.id}/cancel`, {}, "Could not decline.")) loadHandover();
    });
    btns.append(yes, no); row.appendChild(btns); box.appendChild(row);
  });
  box.hidden = !HANDOVER.incoming.length;
}
function renderHandoverCard() {
  const card = $("handoverCard"); if (!card) return;
  card.replaceChildren();
  const cancelBtn = (h, label) => {
    const b = el("button", "ghost small danger", label);
    b.addEventListener("click", async () => {
      if (await handoverPost(`/api/handover/${h.from.id}/cancel`, {}, "Could not cancel.")) loadUsers();
    });
    return b;
  };
  if (ME.role === "coowner") {
    card.appendChild(el("div", "card-head", "")).appendChild(el("h2", "", "Hand over your co-owner seat"));
    const h = HANDOVER.outgoing;
    if (h) {
      const p = el("p", "");
      p.append(el("span", "", "Waiting for "), el("strong", "", h.to.name), el("span", "muted", ` (${h.to.email})`),
               el("span", "", " to accept. Once they do, they become co-owner and you become an admin. "));
      card.append(p, cancelBtn(h, "Cancel handover"));
    } else {
      card.appendChild(el("p", "muted", "Co-owner changes every year. Use “Choose as successor” next to the person who should take over. They must have signed in once. When they accept, they become co-owner and you become an admin."));
    }
    card.hidden = false;
  } else if (ME.role === "owner") {
    card.appendChild(el("div", "card-head", "")).appendChild(el("h2", "", "Pending co-owner handovers"));
    if (!HANDOVER.all.length) card.appendChild(el("p", "muted", "No co-owner has chosen a successor yet. You can still give or remove co-owner access directly below."));
    HANDOVER.all.forEach((h) => {
      const p = el("p", "handover-row");
      p.append(el("span", "", `${h.from.name} → ${h.to.name} (${h.to.email}), waiting for ${h.to.name} to accept. `), cancelBtn(h, "Cancel"));
      card.appendChild(p);
    });
    card.hidden = false;
  }
}
loadHandover();

// ========================================================== JOURNALISTS (admins and above)
let PEOPLE = [];
async function loadJournalists() {
  const res = await fetch("/api/people");
  if (!res.ok) return;
  PEOPLE = (await res.json()).people;
  renderJournalists();
}
function renderJournalists() {
  const q = ($("jrSearch").value || "").toLowerCase();
  const body = $("jrBody"); body.replaceChildren();
  const count = PEOPLE.filter((u) => u.role === "journalist").length;
  $("jrCount").textContent = `${count} journalist${count === 1 ? "" : "s"}`;
  // journalists first, then students; staff can already post so they are left out
  PEOPLE.filter((u) => ["journalist", "user"].includes(u.role) && `${u.name} ${u.email}`.toLowerCase().includes(q)).forEach((u) => {
    const tr = el("tr");
    const who = el("td"); who.append(el("strong", "", u.name), el("div", "muted", u.email)); tr.appendChild(who);
    const rt = el("td"); rt.appendChild(el("span", `role-tag role-${u.role}`, ROLE_LABEL[u.role])); tr.appendChild(rt);
    const act = el("td");
    const on = u.role !== "journalist";
    const b = el("button", on ? "ai-btn" : "ghost small danger", on ? "Make journalist" : "Remove journalist");
    b.addEventListener("click", async () => {
      b.disabled = true;
      const res = await fetch(`/api/users/${u.id}/journalist`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ journalist: on }) });
      if (res.ok) loadJournalists();
      else { b.disabled = false; alert((await res.json().catch(() => ({}))).error || "Could not change the role."); }
    });
    act.appendChild(b);
    tr.appendChild(act);
    body.appendChild(tr);
  });
}
if ($("jrSearch")) $("jrSearch").addEventListener("input", renderJournalists);

// ========================================================== COLLEGE & HOSTEL UPDATES
const NEWS_YEAR_LABEL = { all: "All years", 1: "1st Year", 2: "2nd Year", 3: "3rd Year", 4: "4th Year" };
const NEWS_HOSTEL_LABEL = { both: "Boys & girls", boys: "Boys' hostel", girls: "Girls' hostel" };
let hnYear = store.get("cm_hn_year", null) || String(myYear);
let hnHostel = store.get("cm_hn_hostel", null) || myHostel;
const postJSON = (url, data) => fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });

async function loadNews(section) {
  const list = $(`newsList-${section}`);
  let url = `/api/updates/${section}`;
  if (section === "hostel") {
    document.querySelectorAll("[data-hn-year]").forEach((b) => b.classList.toggle("active", b.dataset.hnYear === hnYear));
    document.querySelectorAll("[data-hn-hostel]").forEach((b) => b.classList.toggle("active", b.dataset.hnHostel === hnHostel));
    url += `?year=${encodeURIComponent(hnYear)}&hostel=${encodeURIComponent(hnHostel)}`;
  }
  const res = await fetch(url);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) { list.replaceChildren(el("p", "empty-line", data.error || "Could not load updates.")); return; }
  const form = document.querySelector(`[data-news-form="${section}"]`);
  if (form) form.hidden = !data.can_post;
  list.replaceChildren();
  if (!data.updates.length) list.appendChild(el("p", "empty-line", section === "hostel" ? "No hostel updates for this year and hostel yet." : "No college updates yet."));
  data.updates.forEach((u) => list.appendChild(newsItem(section, u)));
}

function newsItem(section, u) {
  const card = el("article", "notice news-item");
  const top = el("div", "notice-top");
  top.appendChild(el("span", "notice-date", fmtTime(u.created_at)));
  if (section === "hostel") {
    const tags = el("span", "news-tags");
    tags.append(el("span", "kind-badge", NEWS_YEAR_LABEL[u.year]), el("span", "kind-badge", NEWS_HOSTEL_LABEL[u.hostel]));
    top.appendChild(tags);
  }
  card.appendChild(top);
  if (u.title) card.appendChild(el("h3", "", u.title));
  card.appendChild(el("p", "notice-preview news-body", u.body));
  const line = el("div", "pub-line");
  line.append(el("span", "", "Posted by "), el("strong", "", u.author_name), el("span", `role-tag role-${u.author_role}`, ROLE_LABEL[u.author_role] || "Student"));
  if (u.updated_at) line.appendChild(el("span", "", ` · edited ${fmtTime(u.updated_at)}`));
  card.appendChild(line);
  if (u.can_edit) {
    const act = el("div", "notice-actions");
    const edit = el("button", "ghost small", "✎ Edit");
    edit.addEventListener("click", () => card.replaceWith(newsEditor(section, u)));
    const del = el("button", "ghost small danger", "🗑 Delete");
    del.addEventListener("click", async () => {
      if (del.dataset.confirm !== "1") { del.dataset.confirm = "1"; del.textContent = "Click again to delete"; return; }
      del.disabled = true;
      const res = await fetch(`/api/updates/item/${u.id}/delete`, { method: "POST" });
      if (res.ok) loadNews(section);
      else { const d = await res.json().catch(() => ({})); del.textContent = d.error || "Could not delete"; }
    });
    act.append(edit, del);
    card.appendChild(act);
  }
  return card;
}

function newsEditor(section, u) {
  const card = el("form", "notice news-item news-edit");
  const title = el("input"); title.type = "text"; title.maxLength = 120; title.value = u.title; title.placeholder = "Headline (optional)";
  const body = el("textarea"); body.rows = 4; body.maxLength = 3000; body.value = u.body;
  let year, hostel;
  if (section === "hostel") {
    year = selectEl(Object.entries(NEWS_YEAR_LABEL), u.year, "");
    hostel = selectEl(Object.entries(NEWS_HOSTEL_LABEL), u.hostel, "");
    const row = el("div", "news-edit-tags"); row.append(year, hostel); card.appendChild(row);
  }
  card.append(title, body);
  const act = el("div", "notice-actions");
  const err = el("span", "muted");
  const cancel = el("button", "ghost small", "Cancel"); cancel.type = "button";
  cancel.addEventListener("click", () => card.replaceWith(newsItem(section, u)));
  const save = el("button", "primary inline", "Save"); save.type = "submit";
  act.append(err, cancel, save);
  card.appendChild(act);
  card.addEventListener("submit", async (e) => {
    e.preventDefault();
    save.disabled = true;
    const data = { title: title.value, body: body.value };
    if (section === "hostel") { data.year = year.value; data.hostel = hostel.value; }
    const res = await postJSON(`/api/updates/item/${u.id}/edit`, data);
    if (res.ok) loadNews(section);
    else { save.disabled = false; err.textContent = (await res.json().catch(() => ({}))).error || "Could not save."; }
  });
  return card;
}

document.querySelectorAll("[data-news-form]").forEach((form) => {
  const section = form.dataset.newsForm;
  if (section === "hostel") { form.elements.year.value = String(myYear); form.elements.hostel.value = myHostel; }
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = form.querySelector("button[type=submit]"), msg = form.querySelector(".news-msg");
    const data = { title: form.elements.title.value, body: form.elements.body.value };
    if (section === "hostel") { data.year = form.elements.year.value; data.hostel = form.elements.hostel.value; }
    btn.disabled = true; msg.textContent = "";
    const res = await postJSON(`/api/updates/${section}`, data);
    btn.disabled = false;
    if (res.ok) { form.elements.title.value = ""; form.elements.body.value = ""; msg.textContent = "Posted."; loadNews(section); }
    else msg.textContent = (await res.json().catch(() => ({}))).error || "Could not post.";
  });
});
document.querySelectorAll("[data-hn-year]").forEach((b) => b.addEventListener("click", () => {
  hnYear = b.dataset.hnYear; store.set("cm_hn_year", hnYear); loadNews("hostel");
}));
document.querySelectorAll("[data-hn-hostel]").forEach((b) => b.addEventListener("click", () => {
  hnHostel = b.dataset.hnHostel; store.set("cm_hn_hostel", hnHostel); loadNews("hostel");
}));
document.querySelectorAll(".ask-chip").forEach((c) => c.addEventListener("click", () => askQuestion(c.textContent)));

// ---------------------------------------------------------- results (nithresults.xyz, via our server)
const RES_KEY = "cm_results_recent";
let resRecent = store.get(RES_KEY, []);
let resLast = null;

function renderResultsPage() {
  const box = $("resRecent");
  box.replaceChildren();
  resRecent.forEach((r) => {
    const c = el("button", "chip", r);
    c.type = "button";
    c.addEventListener("click", () => { $("resQuery").value = r; lookupResult(r); });
    box.append(c);
  });
  box.hidden = resRecent.length === 0;
  if (!resLast) $("resQuery").focus();
}

const gpa = (v) => (v === null || v === undefined ? "–" : Number(v).toFixed(2));

function resultCard(st) {
  const wrap = el("div", "res-student");
  const head = el("div", "card res-head");
  const who = el("div");
  who.append(el("h2", null, st.name || st.roll), el("p", "muted", [st.roll, st.father && `Father: ${st.father}`, st.year && `Batch ${st.year}`].filter(Boolean).join(" · ")));
  head.append(who);
  wrap.append(head);

  const latest = st.semesters[st.semesters.length - 1];
  const stats = el("div", "stats");
  [[gpa(st.cgpa), "CGPA"], [gpa(latest && latest.sgpa), latest ? `SGPA (${latest.name})` : "SGPA"], [String(st.credits || 0), `Credits over ${st.semesters.length} semester${st.semesters.length === 1 ? "" : "s"}`]]
    .forEach(([n, l]) => { const s = el("div", "stat"); s.append(el("div", "stat-num", n), el("div", "stat-label", l)); stats.append(s); });
  wrap.append(stats);

  if (st.semesters.length > 1) {
    const trend = el("div", "card res-trend");
    trend.append(el("h2", null, "SGPA by semester"));
    const bars = el("div", "res-bars");
    st.semesters.forEach((s) => {
      const b = el("div", "res-bar");
      const fill = el("div", "res-bar-fill");
      fill.style.height = `${Math.max(4, ((s.sgpa || 0) / 10) * 100)}%`;
      b.append(el("span", "res-bar-val", gpa(s.sgpa)), fill, el("span", "res-bar-lab", `S${s.number}`));
      bars.append(b);
    });
    trend.append(bars);
    wrap.append(trend);
  }

  [...st.semesters].reverse().forEach((s, i) => {
    const d = el("details", "card res-sem");
    d.open = i === 0;
    const sum = el("summary");
    sum.append(el("strong", null, s.name), el("span", "muted", `SGPA ${gpa(s.sgpa)} · CGPA ${gpa(s.cgpa)} · ${s.credits} credits`));
    d.append(sum);
    const scroll = el("div", "res-table-wrap");
    const t = el("table", "res-table");
    const hr = el("tr");
    ["Code", "Subject", "Credits", "Grade", "Points"].forEach((h) => hr.append(el("th", null, h)));
    t.append(hr);
    s.subjects.forEach((sub) => {
      const tr = el("tr");
      const g = el("td"); g.append(el("span", `res-grade g-${(sub.grade || "x").toLowerCase()}`, sub.grade || "–"));
      tr.append(el("td", "res-code", sub.code), el("td", null, sub.subject), el("td", null, String(sub.credit)), g, el("td", null, sub.points === null ? "–" : String(sub.points)));
      t.append(tr);
    });
    scroll.append(t);
    d.append(scroll);
    wrap.append(d);
  });
  return wrap;
}

async function lookupResult(raw) {
  const q = String(raw || "").replace(/\s+/g, "").toUpperCase();
  $("resErr").hidden = true; $("resStale").hidden = true;
  if (!/^[A-Z0-9]{5,15}$/.test(q)) { $("resErr").textContent = "Enter a full roll number, like 25BCS050."; $("resErr").hidden = false; return; }
  const out = $("resOut"), btn = $("resGo");
  btn.disabled = true; btn.textContent = "Searching…";
  out.replaceChildren(el("p", "muted", `Looking up ${q}…`));
  try {
    const res = await fetch(`/api/results?q=${encodeURIComponent(q)}`);
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || "Could not load results.");
    out.replaceChildren();
    if (!data.students.length) {
      out.append(el("div", "card empty-line", `No results found for ${q}. Check the roll number; results of a new batch show up once they're published.`));
      return;
    }
    resLast = q;
    resRecent = [q, ...resRecent.filter((r) => r !== q)].slice(0, 6);
    store.set(RES_KEY, resRecent);
    renderResultsPage();
    if (data.stale) { $("resStale").textContent = `The results site isn't responding, so this is the copy saved at ${fmtTime(data.fetched_at * 1000)}.`; $("resStale").hidden = false; }
    data.students.forEach((st) => out.append(resultCard(st)));
  } catch (err) {
    out.replaceChildren();
    $("resErr").textContent = err.message; $("resErr").hidden = false;
  } finally {
    btn.disabled = false; btn.textContent = "Search";
  }
}

if ($("resForm")) $("resForm").addEventListener("submit", (e) => { e.preventDefault(); lookupResult($("resQuery").value); });

route();
