package app.vercel.campusmate_nith.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.background.Notify
import app.vercel.campusmate_nith.background.StudyTimer
import app.vercel.campusmate_nith.data.StudyLog
import app.vercel.campusmate_nith.data.StudyPrefs
import app.vercel.campusmate_nith.data.StudyState
import app.vercel.campusmate_nith.data.StudyTarget
import app.vercel.campusmate_nith.data.niceDate
import app.vercel.campusmate_nith.data.planKey
import app.vercel.campusmate_nith.data.pomodorosFor
import app.vercel.campusmate_nith.data.sessionMinutes
import app.vercel.campusmate_nith.data.studyable
import app.vercel.campusmate_nith.data.today
import app.vercel.campusmate_nith.data.todayIso
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.delay

@Composable
fun StudyScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val s by StudyTimer.state.collectAsState()
    val log by StudyTimer.log.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Do Not Disturb access and exact alarms are granted in Settings, so look again while the screen is open
    var dndAccess by remember { mutableStateOf(StudyTimer.dndAccess(ctx)) }
    var exact by remember { mutableStateOf(StudyTimer.exactAlarms(ctx)) }
    var notifs by remember { mutableStateOf(Notify.allowed(ctx)) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            now = System.currentTimeMillis()
            // the alarm normally ends a phase; this covers a late one while the student is watching
            StudyTimer.state.value?.let { if (!it.paused && now >= it.endsAt + 1_500) StudyTimer.phaseEnded(ctx, it.endsAt) }
            if (n++ % 4 == 0) {
                dndAccess = StudyTimer.dndAccess(ctx); exact = StudyTimer.exactAlarms(ctx); notifs = Notify.allowed(ctx)
            }
            delay(250)
        }
    }

    val saved = remember { vm.store.studyPrefs }
    val pending = vm.pendingStudy
    var subject by remember { mutableStateOf(pending?.subject.orEmpty()) }
    var task by remember { mutableStateOf(pending?.task.orEmpty()) }
    var key by remember { mutableStateOf(pending?.planKey.orEmpty()) }
    var focus by remember { mutableStateOf(saved.focusMin) }
    var short by remember { mutableStateOf(saved.shortMin) }
    var rounds by remember { mutableStateOf(pending?.rounds ?: saved.rounds) }
    var dnd by remember { mutableStateOf(saved.dnd) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        pending?.let { subject = it.subject; task = it.task; key = it.planKey; it.rounds?.let { r -> rounds = r }; vm.pendingStudy = null }
    }

    fun openSettings(action: String, pkg: Boolean) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg) i.data = Uri.parse("package:${ctx.packageName}")
        try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Screen("Study mode", vm, onBack = { nav.back() }) {
        val run = s
        if (run != null) {
            item { TimerCard(run, now) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (run.paused) "Resume" else "Pause", { if (run.paused) StudyTimer.resume(ctx) else StudyTimer.pause(ctx) }, modifier = Modifier.weight(1f))
                    SmallButton(if (run.focusing) "Skip" else "Skip break", { StudyTimer.skip(ctx) })
                    SmallButton("Stop", { StudyTimer.stop(ctx) }, danger = true)
                }
            }
            item {
                Alert(
                    when {
                        run.dndByUs -> "🔕 Do Not Disturb is on. It switches off when the session ends or you stop it. Alarms still ring."
                        !dndAccess -> "Do Not Disturb wasn't changed: Zenkai doesn't have access to it yet."
                        else -> "Do Not Disturb was left as it was."
                    },
                    if (run.dndByUs) AlertKind.Ok else AlertKind.Info,
                )
            }
            if (!exact) item { Alert("Breaks may start a minute or two late while the phone is idle. Allow alarms for Zenkai so they land on time.", AlertKind.Warn) }
        } else {
            item { Muted("Pick what you're studying and press start. The phone goes quiet (Do Not Disturb), and a Pomodoro timer runs focus blocks with short breaks. It keeps running if you leave the app.") }
            item {
                CmCard {
                    Field(subject, { subject = it; key = "" }, "Subject, e.g. Engineering Mathematics", maxLength = 80)
                    Field(task, { task = it }, "What you'll do (optional)", maxLength = 300, maxLines = 3)
                    if (key.isNotEmpty()) Muted("From your study plan · ${key.substringBefore('|').let { niceDate(it) }}, ${key.split('|').getOrElse(1) { "" }}", size = 12)
                    Muted("Focus", size = 13)
                    ChipRow(listOf(25 to "25 min", 45 to "45 min", 50 to "50 min"), focus) { focus = it }
                    Muted("Break", size = 13)
                    ChipRow(listOf(5 to "5 min", 10 to "10 min"), short) { short = it }
                    Muted("Pomodoros", size = 13)
                    ChipRow((1..8).map { it to "$it" }, rounds) { rounds = it }
                    Muted("${rounds} × $focus min = ${hm(rounds * focus)} of focus, about ${hm(rounds * focus + (rounds - 1) * short + (if (rounds > 4) 10 else 0))} in all. A 15 minute break after every 4th.", size = 12)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Turn on Do Not Disturb", fontWeight = FontWeight.SemiBold)
                            Muted("Silences calls and notifications for the session, then puts things back", size = 12)
                        }
                        Switch(dnd, { dnd = it }, colors = SwitchDefaults.colors(checkedTrackColor = P.accent))
                    }
                    if (dnd && !dndAccess) {
                        Alert("Android asks you once: find Zenkai in the list and allow Do Not Disturb access, then come back.", AlertKind.Warn)
                        SmallButton("Allow Do Not Disturb access", { openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false) })
                    }
                    if (!exact && Build.VERSION.SDK_INT >= 31) {
                        Muted("Breaks may start a little late while the phone is idle unless Zenkai may set alarms.", size = 12)
                        SmallButton("Allow alarms", { openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true) })
                    }
                    if (!notifs) Muted("Notifications are off for Zenkai, so the countdown and break alerts only show inside the app.", size = 12)
                    error?.let { Alert(it, AlertKind.Error) }
                    PrimaryButton("Start study mode", {
                        if (subject.isBlank()) { error = "What are you studying? Type a subject or pick one from today's plan."; return@PrimaryButton }
                        error = null
                        StudyTimer.start(ctx, StudyTarget(subject.trim(), task.trim(), key, rounds),
                            StudyPrefs(focusMin = focus, shortMin = short, longMin = 15, rounds = rounds, dnd = dnd))
                    })
                }
            }
        }

        // today's planner sessions, to start study mode on one and see what's done
        val day = vm.plan?.result?.days?.firstOrNull { it.date == todayIso() }
        val todays = day?.sessions?.filter { studyable(it) }.orEmpty()
        if (todays.isNotEmpty()) item {
            CmCard {
                SectionTitle("Today's plan")
                todays.forEach { p ->
                    val k = planKey(day!!.date, p)
                    val done = log.filter { it.planKey == k }
                    SessionRow(p, done = done, onStudy = if (run == null) ({
                        subject = p.subject; task = p.task; key = k
                        pomodorosFor(sessionMinutes(p.time), StudyPrefs(focusMin = focus, shortMin = short))?.let { rounds = it }
                    }) else null)
                }
            }
        }

        item { StatsCard(log) }
        val recent = log.take(12)
        if (recent.isNotEmpty()) {
            item { SectionTitle("Recent sessions", trailing = { SmallButton("Clear", { StudyTimer.clearLog(ctx) }, danger = true) }) }
            items(recent, key = { it.at }) { l ->
                CmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.subject, fontWeight = FontWeight.SemiBold)
                            Muted(listOf(niceDate(l.date), l.task.takeIf { it.isNotBlank() }, if (l.planKey.isNotEmpty()) "from plan" else null)
                                .filterNotNull().joinToString(" · "), size = 12)
                        }
                        Text("${l.minutes} min", color = P.accent, fontWeight = FontWeight.SemiBold)
                    }
                    if (l.pomodoros > 0) Text("🍅".repeat(l.pomodoros.coerceAtMost(12)), fontSize = 13.sp)
                }
            }
        }
    }
}

private fun hm(min: Int) = if (min < 60) "$min min" else "${min / 60} h${if (min % 60 > 0) " ${min % 60} min" else ""}"

@Composable
private fun phaseColor(s: StudyState): Color = when (s.phase) {
    "short" -> P.green
    "long" -> P.indigo
    else -> P.accent
}

@Composable
private fun TimerCard(s: StudyState, now: Long) {
    val color = phaseColor(s)
    val track = P.fill2
    val left = s.left(now)
    val progress = if (s.phaseMs > 0) 1f - left.toFloat() / s.phaseMs else 0f
    CmCard {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(240.dp)) {
                val w = 14.dp.toPx()
                val box = Size(size.width - w, size.height - w)
                val tl = Offset(w / 2, w / 2)
                drawArc(track, 0f, 360f, false, tl, box, style = Stroke(w))
                drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, box, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(StudyTimer.phaseLabel(s), color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(StudyTimer.clock(left), fontSize = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text(if (s.paused) "Paused" else if (s.focusing) "until a break" else "until focus", color = P.muted, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..s.rounds).forEach { i ->
                val c = when {
                    i <= s.done -> color
                    i == s.round && s.focusing -> color.copy(alpha = 0.4f)
                    else -> track
                }
                Box(Modifier.padding(horizontal = 4.dp).size(10.dp).background(c, CircleShape))
            }
        }
        Text(s.subject, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (s.task.isNotBlank()) Text(s.task, color = P.ink2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatsCard(log: List<StudyLog>) {
    val day = todayIso()
    val weekStart = today().minusDays(6).toString()
    val t = log.filter { it.date == day }
    val w = log.filter { it.date >= weekStart }
    CmCard {
        Row {
            Stat("Today", "${t.sumOf { it.minutes }} min", "${t.sumOf { it.pomodoros }} 🍅", Modifier.weight(1f))
            Stat("Last 7 days", hm(w.sumOf { it.minutes }), "${w.sumOf { it.pomodoros }} 🍅", Modifier.weight(1f))
        }
        val top = w.groupBy { it.subject }.mapValues { (_, v) -> v.sumOf { it.minutes } }.entries.sortedByDescending { it.value }.take(3)
        if (top.isNotEmpty()) Muted(top.joinToString(" · ") { "${it.key} ${hm(it.value)}" }, size = 12)
    }
}

@Composable
private fun Stat(label: String, big: String, small: String, modifier: Modifier) {
    Column(modifier) {
        Muted(label, size = 12)
        Text(big, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Muted(small, size = 12)
    }
}
