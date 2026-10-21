package app.vercel.campusmate_nith.ui.screens

import androidx.compose.runtime.collectAsState
import app.vercel.campusmate_nith.background.StudyTimer
import app.vercel.campusmate_nith.data.StudyTarget
import app.vercel.campusmate_nith.data.planKey
import app.vercel.campusmate_nith.data.pomodorosFor
import app.vercel.campusmate_nith.data.sessionMinutes
import app.vercel.campusmate_nith.data.studyable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.PlanRequest
import app.vercel.campusmate_nith.data.PlanSubject
import app.vercel.campusmate_nith.data.SavedPlan
import app.vercel.campusmate_nith.data.niceDate
import app.vercel.campusmate_nith.data.today
import app.vercel.campusmate_nith.data.todayIso
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.DateField
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.launch
import java.time.Instant

private class SubjectEdit(name: String = "", date: String = "", topics: String = "", difficulty: String = "medium") {
    var name by mutableStateOf(name)
    var date by mutableStateOf(date)
    var topics by mutableStateOf(topics)
    var difficulty by mutableStateOf(difficulty)
    /** Topics picked from the syllabus; [topics] holds their text and can still be edited by hand. */
    var pick by mutableStateOf<TopicPick?>(null)
}

@Composable
fun PlannerScreen(vm: AppViewModel, nav: Nav) {
    val subjects = remember { mutableStateListOf(SubjectEdit(), SubjectEdit()) }
    var hours by remember { mutableStateOf("4") }
    var start by remember { mutableStateOf("18:00") }
    var sample by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var foot by remember { mutableStateOf("Saved plan") }
    var cachedNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var pickingFor by remember { mutableStateOf<SubjectEdit?>(null) }
    val syl = vm.syllabus()
    val canPick = outlined(syl).isNotEmpty()
    val studyLog by StudyTimer.log.collectAsState()

    pickingFor?.let { s ->
        val code = sylMatch(syl, s.name)
        TopicPickerDialog(vm, code, s.pick?.takeIf { it.code == code }, onDismiss = { pickingFor = null }) { c, pick ->
            val sub = syl.subjects.getValue(c)
            if (sylMatch(syl, s.name) != c || s.name.trim().uppercase() == c) s.name = "$c ${sub.name}"
            s.pick = pick
            s.topics = pickedTopicsText(sub, pick)
            sample = false
            pickingFor = null
        }
    }

    LaunchedEffect(vm.pendingPlan) {
        vm.pendingPlan?.let { list ->
            subjects.clear(); list.forEach { (n, d) -> subjects.add(SubjectEdit(n, d)) }
            sample = false; vm.pendingPlan = null
        }
    }

    fun plus(n: Long) = today().plusDays(n).toString()

    Screen("Study planner", vm, onBack = { nav.back() }) {
        item { Muted("Add your subjects and exam dates. Gemma builds a realistic day-by-day plan with breaks and revision before each exam." +
            (if (canPick) " Pick each subject's units and topics from the syllabus, or type them." else "") +
            " Press ▶ Focus on a session to study it in study mode.") }
        items(subjects.size) { i ->
            val s = subjects[i]
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subject ${i + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (subjects.size > 1) SmallButton("Remove", { subjects.removeAt(i); sample = false }, danger = true)
                }
                Field(s.name, { s.name = it; sample = false }, "Subject, e.g. Data Structures", maxLength = 60)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Exam date", s.date, { s.date = it; sample = false })
                    Dropdown("Difficulty", listOf("easy" to "Easy", "medium" to "Medium", "hard" to "Hard"), s.difficulty, { s.difficulty = it; sample = false })
                }
                Field(s.topics, { s.topics = it; sample = false }, "Topics (comma separated)", maxLength = 2000, maxLines = 4)
                if (s.name.isNotBlank()) SmallButton("⏱ Study now", {
                    vm.pendingStudy = StudyTarget(s.name.trim(), s.topics.trim().take(300))
                    nav.go("study")
                })
                if (canPick) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val code = sylMatch(syl, s.name)
                    SmallButton(if (code != null) "📚 Choose $code topics" else "📚 Choose topics from syllabus", { pickingFor = s })
                    s.pick?.takeIf { it.code == code && it.topics.isNotEmpty() }?.let { p ->
                        Muted("${p.units.size} unit${if (p.units.size == 1) "" else "s"} · ${p.topics.size} topic${if (p.topics.size == 1) "" else "s"}", size = 12)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subjects.size < 8) SmallButton("+ Add subject", { subjects.add(SubjectEdit()) })
                SmallButton("Fill sample", {
                    subjects.clear()
                    subjects.add(SubjectEdit("Data Structures", plus(5), "Linked lists, stacks, queues, trees, graphs, sorting", "hard"))
                    subjects.add(SubjectEdit("Engineering Mathematics III", plus(7), "Laplace transform, Fourier series, PDEs", "medium"))
                    subjects.add(SubjectEdit("Digital Electronics", plus(9), "K-maps, flip-flops, counters, multiplexers", "easy"))
                    hours = "4"; start = "18:00"; sample = true
                })
            }
        }
        item {
            CmCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(hours, { hours = it.filter { c -> c.isDigit() || c == '.' } }, "Hours per day", Modifier.weight(1f), number = true, maxLength = 4)
                    Field(start, { start = it }, "Start time (HH:MM)", Modifier.weight(1f), maxLength = 5)
                }
                PrimaryButton("✦ Make my study plan", {
                    val list = subjects.map { PlanSubject(it.name.trim(), it.date, it.topics.trim(), it.difficulty) }.filter { it.name.isNotEmpty() || it.exam_date.isNotEmpty() }
                    error = when {
                        list.isEmpty() -> "Add at least one subject with its exam date, or tap Fill sample."
                        list.any { it.name.isEmpty() } -> "Every subject needs a name."
                        list.any { it.exam_date.isEmpty() } -> "Pick an exam date for ${list.first { it.exam_date.isEmpty() }.name}."
                        else -> null
                    }
                    if (error == null) scope.launch {
                        loading = true
                        try {
                            val r = vm.api.plan(PlanRequest(list, hours.toDoubleOrNull() ?: 0.0, start, if (sample) "demo" else ""))
                            val res = r.result ?: throw ApiException(r.error ?: "Something went wrong.")
                            vm.savePlan(SavedPlan(res, r.model, Instant.now().toString()))
                            cachedNote = if (r.cached) "Live AI unavailable right now (${r.notice}) Showing a saved plan from an earlier live Gemma run on ${r.cached_at}." else null
                            foot = if (r.cached) "Saved plan · ${r.model}" else "Live result · ${r.model} · ${r.seconds ?: ""}s"
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            loading = false
                        }
                    }
                }, loading = loading)
            }
        }
        if (loading) item { Loading("Gemma is planning your days…") }
        error?.let { item { Alert(it, AlertKind.Error) } }

        val plan = vm.plan?.result
        if (plan != null && plan.days.isNotEmpty() && !loading) {
            cachedNote?.let { item { Alert(it, AlertKind.Warn) } }
            item {
                CmCard {
                    SectionTitle("${niceDate(plan.days.first().date)} – ${niceDate(plan.days.last().date)}")
                    Text(plan.overview, fontSize = 14.sp, color = P.ink2)
                }
            }
            if (plan.warnings.isNotEmpty()) item { Alert(plan.warnings.joinToString("\n") { "⚠ $it" }, AlertKind.Warn) }
            items(plan.days, key = { it.date }) { d ->
                val isToday = d.date == todayIso()
                CmCard(border = if (isToday) P.accent else null) {
                    Text((if (isToday) "Today · " else "") + niceDate(d.date), fontWeight = FontWeight.SemiBold)
                    if (d.focus.isNotEmpty()) Muted(d.focus, size = 13)
                    d.sessions.forEach { p ->
                        val k = planKey(d.date, p)
                        SessionRow(p, done = studyLog.filter { it.planKey == k }, onStudy = if (studyable(p)) ({
                            vm.pendingStudy = StudyTarget(p.subject, p.task, k, pomodorosFor(sessionMinutes(p.time), vm.store.studyPrefs))
                            nav.go("study")
                        }) else null)
                    }
                }
            }
            if (plan.tips.isNotEmpty()) item {
                CmCard {
                    SectionTitle("Tips")
                    plan.tips.forEach { Text("• $it", fontSize = 14.sp) }
                }
            }
            item { Muted(foot, size = 12) }
        }
    }
}
