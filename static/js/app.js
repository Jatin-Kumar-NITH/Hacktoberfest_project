// CampusMate AI front end. AI text is always inserted with textContent, never innerHTML.

const $ = (id) => document.getElementById(id);
const ME = JSON.parse(document.getElementById("me-data").textContent);
const IS_ADMIN = ME.role === "admin" || ME.role === "owner";
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
const ROLE_LABEL = { owner: "Owner", admin: "Admin", user: "Student" };
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
  const valid = ["dashboard", "board", "timetable", "assignments", "exams", "syllabus", "notice", "planner", "hostel", "chat", "publish", "admins", "about"].filter((v) => $(`page-${v}`));
  const p = valid.includes(page) ? page : "dashboard";
  valid.forEach((v) => ($(`page-${v}`).hidden = v !== p));
  document.querySelectorAll(".nav a").forEach((a) => a.classList.toggle("active", a.dataset.page === p));
  if (p === "dashboard") renderDashboard();
  if (p === "board") renderBoard();
  if (p === "timetable") renderTimetable();
  if (p === "assignments") renderAssignments();
  if (p === "exams") renderExams();
  if (p === "syllabus") renderSyllabus();
  if (p === "hostel") renderHostel();
  if (p === "chat") startChat(); else stopChat();
  if (p === "publish") renderPublish();
  if (p === "admins") loadUsers();
  window.scrollTo(0, 0);
}
window.addEventListener("hashchange", route);

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

// ========================================================== HOSTEL MESS MENUS (boys / girls)
const CAMPUS = JSON.parse($("campus-data").textContent);
const HOSTEL = CAMPUS.hostel;
const TODAY_NAME = DAY_NAMES[new Date().getDay()];
const WEEK7 = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const MEALS = ["Breakfast", "Lunch", "Snacks", "Dinner"];
let myHostel = store.get("cm_hostel", "boys");
if (!HOSTEL.hostels[myHostel]) myHostel = "boys";
let menuEditing = false;
const menuIsEmpty = (h) => !WEEK7.some((d) => MEALS.some((m) => (h.mess_menu[d] || {})[m]));
const editedLine = (o) => (o && o.edited_by ? `Last edited by ${o.edited_by} · ${fmtTime(o.edited_at)}` : "");

function renderHostel() {
  const h = HOSTEL.hostels[myHostel];
  document.querySelectorAll("[data-hostel]").forEach((b) => b.classList.toggle("active", b.dataset.hostel === myHostel));
  $("mnTitle").textContent = `${h.name} · weekly menu`;
  $("mnToday").textContent = `Today is ${TODAY_NAME}`;
  $("mnEditBar").hidden = !IS_ADMIN || menuEditing;
  $("mnEdited").textContent = editedLine(h);
  $("mnActions").hidden = !menuEditing;
  $("mnErr").hidden = true;
  const empty = menuIsEmpty(h);
  $("mnEmpty").hidden = !empty || menuEditing;
  const table = $("mnTable"); table.replaceChildren();
  table.hidden = empty && !menuEditing;
  const head = el("tr"); head.appendChild(el("th", "", "Day"));
  MEALS.forEach((m) => {
    const th = el("th", "", m);
    if (menuEditing) {
      const t = el("input"); t.type = "text"; t.placeholder = "Time, e.g. 7:30–9:30 AM"; t.value = h.mess_timings[m] || ""; t.dataset.timing = m; t.maxLength = 40;
      th.appendChild(t);
    } else if (h.mess_timings[m]) th.appendChild(el("small", "", h.mess_timings[m]));
    head.appendChild(th);
  });
  const thead = el("thead"); thead.appendChild(head); table.appendChild(thead);
  const body = el("tbody");
  WEEK7.forEach((d) => {
    const tr = el("tr", d === TODAY_NAME ? "today" : "");
    tr.appendChild(el("th", "", d === TODAY_NAME ? `${d} · today` : d));
    MEALS.forEach((m) => {
      const td = el("td");
      const val = (h.mess_menu[d] || {})[m] || "";
      if (menuEditing) {
        const t = el("textarea"); t.rows = 2; t.maxLength = 200; t.value = val; t.dataset.day = d; t.dataset.meal = m;
        td.appendChild(t);
      } else td.textContent = val || "—";
      tr.appendChild(td);
    });
    body.appendChild(tr);
  });
  table.appendChild(body);
}
document.querySelectorAll("[data-hostel]").forEach((b) => b.addEventListener("click", () => {
  myHostel = b.dataset.hostel; store.set("cm_hostel", myHostel); menuEditing = false; renderHostel();
}));
$("mnEditBtn").addEventListener("click", () => { menuEditing = true; renderHostel(); });
$("mnCancel").addEventListener("click", () => { menuEditing = false; renderHostel(); });
$("mnSave").addEventListener("click", async () => {
  const mess_menu = {}, mess_timings = {};
  WEEK7.forEach((d) => (mess_menu[d] = {}));
  document.querySelectorAll("#mnTable textarea").forEach((t) => (mess_menu[t.dataset.day][t.dataset.meal] = t.value));
  document.querySelectorAll("#mnTable input[data-timing]").forEach((t) => (mess_timings[t.dataset.timing] = t.value));
  $("mnSave").disabled = true;
  try {
    const res = await fetch("/api/edit/menu", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ hostel: myHostel, mess_menu, mess_timings }) });
    const data = await res.json().catch(() => ({ error: "Save failed." }));
    if (!res.ok) throw new Error(data.error);
    HOSTEL.hostels[myHostel] = data.hostel; menuEditing = false; renderHostel();
  } catch (err) { $("mnErr").textContent = err.message; $("mnErr").hidden = false; }
  finally { $("mnSave").disabled = false; }
});

function renderMenuMini(target, h, dayName) {
  const box = $(target); box.replaceChildren();
  if (menuIsEmpty(h)) { box.appendChild(el("p", "empty-line", "Menu not added yet.")); return; }
  MEALS.forEach((meal) => {
    const items = (h.mess_menu[dayName] || {})[meal];
    if (!items) return;
    const row = el("div", "meal");
    const name = el("div", "meal-name", meal);
    if (h.mess_timings[meal]) name.appendChild(el("small", "", h.mess_timings[meal]));
    row.append(name, el("div", "", items));
    box.appendChild(row);
  });
}

function renderDashHostel() {
  const h = HOSTEL.hostels[myHostel];
  $("dashMenuTitle").textContent = `Today's menu · ${h.name}`;
  renderMenuMini("dashMenu", h, TODAY_NAME);
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
  submit: "Follow the instructions in the assignment.", pdf: p.pdf, published_at: p.published_at, published_by: p.published_by, author_role: p.author_role }));
const NOTICES = CAMPUS.notices.notices.concat(POSTS.filter((p) => p.kind === "notice" || p.kind === "announcement").map((p) => ({
  id: p.id, db_id: p.db_id, kind: p.kind, date: localDate(p.published_at), title: p.title, from: p.published_by, years: p.years, sections: p.sections,
  text: p.body, pdf: p.pdf, published_at: p.published_at, published_by: p.published_by, author_role: p.author_role })))
  .sort((a, b) => (b.published_at || b.date).localeCompare(a.published_at || a.date));
const canDelete = (p) => IS_ADMIN && p.db_id !== undefined && (ME.role === "owner" || p.author_role !== "owner");
function deleteButton(p, label = "Delete") {
  const b = el("button", "ghost small danger", `🗑 ${label}`);
  b.addEventListener("click", async () => {
    if (b.dataset.confirm !== "1") { b.dataset.confirm = "1"; b.textContent = "Click again to delete"; return; }
    b.disabled = true;
    const res = await fetch(`/api/posts/${p.db_id}/delete`, { method: "POST" });
    if (res.ok) { location.reload(); } else { const d = await res.json().catch(() => ({})); b.textContent = d.error || "Could not delete"; }
  });
  return b;
}
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
let ttYear = myYear, ttSection = mySection;

function fillPickers() {
  $("myYear").value = String(myYear);
  $("mySection").replaceChildren(...TT(myYear).sections.map((s) => { const o = el("option", "", s.id); o.value = s.id; return o; }));
  $("mySection").value = mySection;
  const groups = secOf(myYear, mySection).groups || [];
  $("groupPick").hidden = groups.length === 0;
  if (!groups.find((g) => g.id === myGroup)) myGroup = "";
  const opts = [el("option", "", "All groups")]; opts[0].value = "";
  groups.forEach((g) => { const o = el("option", "", `${g.id} (${g.rolls})`); o.value = g.id; opts.push(o); });
  $("myGroup").replaceChildren(...opts);
  $("myGroup").value = myGroup;
}
function saveProfile() {
  store.set("cm_profile", { year: myYear, section: mySection, group: myGroup });
  ttYear = myYear; ttSection = mySection; askHistory = [];
  fillPickers(); route();
}
$("myYear").addEventListener("change", (e) => { myYear = Number(e.target.value); mySection = secOf(myYear, mySection).id; myGroup = ""; saveProfile(); });
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
  if (sec.groups && sec.groups.length) add("Groups", sec.groups.map((g) => `${g.id}: ${g.rolls}`).join("   "));

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
    if (canDelete(a)) actions.appendChild(deleteButton(a));
    card.appendChild(actions);
    box.appendChild(card);
  });
}

// ---------- exams
const myExams = () => {
  const ex = CAMPUS.exams.years[String(myYear)];
  return ex ? ex.rows.filter((r) => forStudent(r, myYear, mySection)).sort((a, b) => a.date.localeCompare(b.date)) : [];
};
function renderExams() {
  const ex = CAMPUS.exams.years[String(myYear)];
  $("exSub").textContent = `${ex ? ex.name : "No exams scheduled"} · ${YEAR_NAMES[myYear]}, Section ${mySection}`;
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
    const nm = subjName(myYear, r.code);
    if (nm) codeTd.appendChild(el("span", "ex-name", nm));
    tr.appendChild(codeTd);
    tr.appendChild(el("td", "", r.venue));
    tr.appendChild(el("td", "ex-left", daysUntilLabel(r.date)));
    body.appendChild(tr);
  });
  $("exRules").replaceChildren(...CAMPUS.exams.rules.map((x) => el("li", "", x)));
}
function exRow(r = {}) {
  const tr = el("tr");
  const inp = (cls, type, val, ph, max) => { const td = el("td"); const i = el("input", cls); i.type = type; i.value = val || ""; if (ph) i.placeholder = ph; if (max) i.maxLength = max; td.appendChild(i); tr.appendChild(td); };
  inp("e-date", "date", r.date);
  inp("e-time", "text", r.time, "9:30–11:00 AM", 40);
  inp("e-code", "text", r.code, "e.g. MA-111", 60);
  inp("e-secs", "text", (r.sections || []).filter((x) => x !== "all").join(", "), "all", 40);
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
      sections: tr.querySelector(".e-secs").value.toUpperCase().split(/[\s,]+/).filter(Boolean), venue: tr.querySelector(".e-venue").value,
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
  resetSubjects(rows.map((r) => ({ name: subjName(myYear, r.code) ? `${r.code} ${subjName(myYear, r.code)}` : r.code,
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
    return card;
  }
  if (sub.note) card.appendChild(el("p", "sy-fac", sub.note));
  const actions = el("div", "sy-actions");
  actions.appendChild(pdfLink(sub.pdf, `📄 Original PDF${syl.official ? ` (p. ${sub.pages.join(", ")})` : ""}`));
  const toggle = el("button", "ghost small", "📝 Read as text");
  const pre = el("pre", "sy-text", sub.text);
  pre.hidden = true;
  toggle.addEventListener("click", () => { pre.hidden = !pre.hidden; toggle.textContent = pre.hidden ? "📝 Read as text" : "Hide text"; });
  const ai = el("button", "ai-btn", "✦ Summarise");
  ai.addEventListener("click", () => { location.hash = "#dashboard"; setTimeout(() => askQuestion(`Summarise the syllabus of ${code}${sub.name !== code ? " " + sub.name : ""} unit by unit, in simple words.`), 60); });
  actions.append(toggle, ai);
  card.appendChild(actions);
  card.appendChild(pre);
  return card;
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
    if (canDelete(m)) act.appendChild(deleteButton(m));
    card.appendChild(act);
    return card;
  }));
  const codes = [...new Set(sec.classes.map((c) => c.code))].sort();
  $("syGrid").replaceChildren(...(codes.length ? codes.map((c) => subjectCard(c, sec, syl))
    : [el("p", "empty-line card", Object.keys(syl.subjects).length ? "Add this section's timetable to see its subjects here."
        : "The syllabus for this year hasn't been added yet. Admins can publish syllabus PDFs as Study material.")]));
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
  const todayIdx = new Date().getDay();
  let label = "Today's classes", classes = dayClasses(sec, DAY_NAMES[todayIdx], myGroup);
  if (!classes.length) {
    for (let i = 1; i <= 7; i++) {
      const name = DAY_NAMES[(todayIdx + i) % 7];
      const c = dayClasses(sec, name, myGroup);
      if (c.length) { classes = c; label = `No classes today 🎉 · ${name}'s classes`; break; }
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

// ========================================================== ASK CAMPUSMATE
let askHistory = [];

function clientContext() {
  const deadlines = [];
  history.forEach((h) => h.result.deadlines.forEach((d) => deadlines.push({ date: d.date_iso || d.date, what: d.what, from: h.result.title })));
  const plan = store.get("cm_plan", null);
  return { deadlines: deadlines.slice(0, 20), plan: plan ? plan.result.days : [] };
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
      body: JSON.stringify({ question: q, year: myYear, section: mySection, group: myGroup, client: clientContext(), history: askHistory }),
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
function chatMsg(m) {
  const mine = m.user_id === ME.id;
  const wrap = el("div", `chat-msg role-${m.role}${mine ? " mine" : ""}`);
  if (m.role !== "user") wrap.appendChild(el("div", "chat-banner", m.role === "owner" ? "👑 Owner" : "🛡 Admin"));
  const head = el("div", "chat-head");
  head.appendChild(el("strong", `chat-name role-${m.role}`, m.name));
  head.appendChild(el("span", `role-tag role-${m.role}`, ROLE_LABEL[m.role]));
  head.appendChild(el("span", "chat-time", fmtTime(m.created_at)));
  const canBan = IS_ADMIN && m.user_id !== ME.id && m.role !== "owner" && (m.role !== "admin" || ME.role === "owner");
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
  if (IS_ADMIN) {
    const del = el("button", "chat-del", "Remove"); del.title = "Remove this message";
    del.addEventListener("click", async () => { await fetch(`/api/chat/${m.id}/delete`, { method: "POST" }); wrap.remove(); });
    head.appendChild(del);
  }
  wrap.append(head, el("div", "chat-body", m.body));
  return wrap;
}
async function pollChat() {
  try {
    const res = await fetch(`/api/chat?after=${chatLatest}`);
    const data = await res.json();
    if (!res.ok) return;
    showBanned(data.banned);
    const log = $("chatLog");
    const atBottom = log.scrollHeight - log.scrollTop - log.clientHeight < 60;
    data.messages.forEach((m) => { log.appendChild(chatMsg(m)); chatLatest = Math.max(chatLatest, m.id); });
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
function startChat() { loadBans(); if (!chatTimer) { pollChat(); chatTimer = setInterval(pollChat, 3000); } }
function stopChat() { clearInterval(chatTimer); chatTimer = null; }
$("chatForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const body = $("chatInput").value.trim();
  if (!body) return;
  $("chatSend").disabled = true;
  try {
    const res = await fetch("/api/chat", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ body }) });
    const data = await res.json();
    if (!res.ok) { if (data.banned) showBanned(data.banned); throw new Error(data.error); }
    $("chatInput").value = ""; $("chatHint").textContent = "Messages are visible to all logged-in users. Admins can remove messages.";
    await pollChat(); $("chatLog").scrollTop = $("chatLog").scrollHeight;
  } catch (err) { $("chatHint").textContent = err.message; }
  finally { $("chatSend").disabled = false; }
});

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
    if (ME.role === "owner" || p.author_role !== "owner") {
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

// ========================================================== MANAGE ADMINS (owner)
let USERS = [];
async function loadUsers() {
  const res = await fetch("/api/users");
  if (!res.ok) return;
  const data = await res.json();
  USERS = data.users;
  $("smtpStatus").hidden = false;
  $("smtpStatus").className = `alert ${data.smtp ? "ok" : "warn"}`;
  $("smtpStatus").textContent = data.smtp
    ? "✓ Email OTP is on: login and sign-up codes are sent by email."
    : "✉️ Email OTP is not set up yet, so codes are printed in the server terminal. To email them, stop the server and run: python setup_email.py";
  renderUsers();
}
function renderUsers() {
  const q = ($("userSearch").value || "").toLowerCase();
  const body = $("userBody"); body.replaceChildren();
  const admins = USERS.filter((u) => u.role !== "user").length;
  $("userCount").textContent = `${USERS.length} account${USERS.length === 1 ? "" : "s"} · ${admins} owner/admin · ${USERS.length - admins} student${USERS.length - admins === 1 ? "" : "s"}`;
  USERS.filter((u) => `${u.name} ${u.email}`.toLowerCase().includes(q)).forEach((u) => {
    const tr = el("tr");
    const who = el("td"); who.append(el("strong", "", u.name), el("div", "muted", u.email)); tr.appendChild(who);
    const rt = el("td"); rt.appendChild(el("span", `role-tag role-${u.role}`, ROLE_LABEL[u.role])); tr.appendChild(rt);
    tr.appendChild(el("td", "muted", fmtTime(u.created_at)));
    tr.appendChild(el("td", "muted", u.last_login_at ? fmtTime(u.last_login_at) : "—"));
    tr.appendChild(el("td", u.chat_banned ? "banned-cell" : "muted", u.chat_banned ? "🚫 Banned" : "OK"));
    const act = el("td");
    if (u.role !== "owner") {
      const make = u.role === "user";
      const b = el("button", make ? "ai-btn" : "ghost small danger", make ? "Make admin" : "Remove admin");
      b.addEventListener("click", async () => {
        const res = await fetch(`/api/users/${u.id}/role`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ role: make ? "admin" : "user" }) });
        if (res.ok) loadUsers();
      });
      act.appendChild(b);
    } else act.appendChild(el("span", "muted", "You"));
    tr.appendChild(act);
    body.appendChild(tr);
  });
}
if ($("userSearch")) $("userSearch").addEventListener("input", renderUsers);
document.querySelectorAll(".ask-chip").forEach((c) => c.addEventListener("click", () => askQuestion(c.textContent)));

route();
