package app.vercel.campusmate_nith.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.background.Notify
import app.vercel.campusmate_nith.background.TodayWidget
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.branchOptions
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.launch

// ------------------------------------------------------------ first-run setup

private enum class Step { Welcome, Year, Section, Group, Branch, Hostel, Alerts, Theme }

/**
 * Asks a new student for their details one question at a time, then hands over to the tutorial.
 * Everything is saved to the same settings the app already uses, so it can be changed later in More.
 */
@Composable
fun SetupScreen(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CampusMateApplication
    var year by rememberSaveable { mutableIntStateOf(vm.year) }
    var section by rememberSaveable { mutableStateOf(vm.section) }
    var group by rememberSaveable { mutableStateOf(vm.group) }
    var branch by rememberSaveable { mutableStateOf(vm.examBranch) }

    val sections = vm.timetable(year).sections
    val sec = sections.firstOrNull { it.id == section } ?: sections.firstOrNull()
    val branches = vm.campus.exams.years[year.toString()]?.branchOptions().orEmpty()
    val hostels = vm.campus.hostel.hostels

    // only ask what this year's data has choices for
    val steps = buildList {
        add(Step.Welcome); add(Step.Year); add(Step.Section)
        if (sec != null && sec.groups.isNotEmpty()) add(Step.Group)
        if (branches.isNotEmpty()) add(Step.Branch)
        if (hostels.size > 1) add(Step.Hostel)
        add(Step.Alerts); add(Step.Theme)
    }
    var stepName by rememberSaveable { mutableStateOf(Step.Welcome.name) }
    val step = Step.valueOf(stepName).takeIf { it in steps } ?: steps.last { it.ordinal <= Step.valueOf(stepName).ordinal }
    val index = steps.indexOf(step)
    var forward by remember { mutableStateOf(true) }

    fun finish() {
        vm.store.askedNotifications = true   // the Alerts step already covered it
        vm.finishSetup(year, sec?.id ?: section, group, branch.takeIf { it in branches }.orEmpty())
        onDone()
    }
    fun next() = if (index == steps.lastIndex) finish() else { forward = true; stepName = steps[index + 1].name }
    fun back() { forward = false; stepName = steps[index - 1].name }
    BackHandler(enabled = index > 0) { back() }

    var allowed by remember { mutableStateOf(Notify.allowed(ctx)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = Notify.allowed(ctx)
    }

    Column(Modifier.fillMaxSize().background(P.bg).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // progress through the questions (the welcome page doesn't count)
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index > 0) {
                TextButton(onClick = { back() }) { Text("Back", color = P.accent) }
                Spacer(Modifier.width(8.dp))
                val progress by animateFloatAsState(index / (steps.size - 1f), label = "progress")
                LinearProgressIndicator(
                    progress = { progress }, color = P.accent, trackColor = P.fill2,
                    modifier = Modifier.weight(1f).height(6.dp).background(P.fill2, CircleShape),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Muted("$index of ${steps.size - 1}", size = 13)
            }
        }
        AnimatedContent(
            targetState = step, modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            label = "setup",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (s) {
                    Step.Welcome -> {
                        Spacer(Modifier.height(32.dp))
                        Badge(Icons.Outlined.School, P.accent, P.accentSoft, big = true)
                        Spacer(Modifier.height(8.dp))
                        Question("Welcome${vm.user?.name?.let { ", ${it.substringBefore(' ')}" } ?: ""} 👋")
                        Muted("A few quick questions so Zenkai shows your own timetable, notices, exams and mess menu. It takes under a minute.", size = 16)
                        Spacer(Modifier.height(8.dp))
                        Muted("You can change any answer later in More.", size = 14)
                    }
                    Step.Year -> {
                        Question("Which year are you in?")
                        (1..4).forEach { y ->
                            Option(YEAR_NAMES[y] ?: "$y", null, year == y) {
                                if (y != year) { year = y; section = vm.sectionOf(y, section).id; group = ""; branch = "" }
                            }
                        }
                    }
                    Step.Section -> {
                        Question("Your section?")
                        Muted("This picks the timetable and section-only notices you see.")
                        when {
                            sections.isEmpty() && vm.refreshing -> Loading("Loading the timetables…")
                            sections.isEmpty() -> Muted("The timetable for ${YEAR_NAMES[year]} hasn't been added yet. You can pick your section later.")
                            else -> sections.forEach { sc ->
                                Option("Section ${sc.id}", listOf(sc.name.takeIf { it != "Section ${sc.id}" }.orEmpty(), sc.branches).filter { it.isNotEmpty() }.joinToString(" · "), sec?.id == sc.id) {
                                    if (sc.id != section) { section = sc.id; group = "" }
                                }
                            }
                        }
                    }
                    Step.Group -> {
                        Question("Your lab group?")
                        Muted("Labs are split by roll number. Pick All groups if you're not sure.")
                        Option("All groups", "Show every group's labs", group.isEmpty()) { group = "" }
                        sec?.groups.orEmpty().forEach { g ->
                            Option(g.id, g.rolls.takeIf { it.isNotEmpty() }?.let { "Roll numbers $it" }, group == g.id) { group = g.id }
                        }
                    }
                    Step.Branch -> {
                        Question("Your branch?")
                        Muted("Used to show only your papers on the exam datesheet.")
                        Option("Show all branches", null, branch.isEmpty()) { branch = "" }
                        ChipRow(branches.map { it to it }, branch) { branch = it }
                    }
                    Step.Hostel -> {
                        Question("Which hostel's mess?")
                        Muted("Home, the mess menu and the widget show this hostel's meals.")
                        hostels.forEach { (id, h) -> Option(h.name.ifEmpty { id }, null, vm.hostel == id) { vm.chooseHostel(id) } }
                    }
                    Step.Alerts -> {
                        Question("Stay in the loop?")
                        Muted("Zenkai can let you know when something new is posted for your class.")
                        ToggleOption(Icons.Outlined.Campaign, "New notices, assignments and exams", "Checked about once an hour", vm.notifyNew) { vm.changeNotifyNew(it) }
                        ToggleOption(Icons.Outlined.Notifications, "Class reminders", "A heads-up before each class", vm.notifyClasses) { vm.changeNotifyClasses(it) }
                        if (vm.notifyClasses) {
                            Muted("Remind me", size = 13)
                            ChipRow(listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), vm.classLead) { vm.changeClassLead(it) }
                        }
                    }
                    Step.Theme -> {
                        Question("Pick a look")
                        Muted("You can switch any time in Settings.")
                        listOf("system" to "Match phone", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                            Option(label, if (mode == "system") "Follows your phone's dark mode" else null, app.themeMode == mode) { vm.setTheme(mode) }
                        }
                    }
                }
            }
        }
        val wantsAlerts = step == Step.Alerts && (vm.notifyNew || vm.notifyClasses) && !allowed && Build.VERSION.SDK_INT >= 33
        PrimaryButton(
            when {
                step == Step.Welcome -> "Let's go"
                wantsAlerts -> "Allow notifications"
                index == steps.lastIndex -> "Finish"
                else -> "Continue"
            },
            {
                if (wantsAlerts) {
                    vm.store.askedNotifications = true
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    next()
                } else next()
            },
        )
        if (wantsAlerts) {
            TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Not now", color = P.muted) }
        }
    }
}

@Composable
private fun Question(text: String) =
    Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 34.sp)

/** A big tappable answer, like an iOS list row with a check. */
@Composable
private fun Option(title: String, hint: String?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().background(if (selected) P.accentSoft else P.card, shape)
            .border(1.5.dp, if (selected) P.accent else P.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (selected) P.accent else P.ink)
            if (!hint.isNullOrEmpty()) Muted(hint, size = 13)
        }
        Box(
            Modifier.size(22.dp).background(if (selected) P.accent else Color.Transparent, CircleShape)
                .border(1.5.dp, if (selected) P.accent else P.fill2, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun ToggleOption(icon: ImageVector, title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(P.card, RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Badge(icon, P.accent, P.accentSoft)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Muted(hint, size = 12)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = P.accent))
    }
}

@Composable
private fun Badge(icon: ImageVector, fg: Color, bg: Color, big: Boolean = false) {
    val s = if (big) 72.dp else 40.dp
    Box(Modifier.size(s).background(bg, RoundedCornerShape(if (big) 22.dp else 12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(if (big) 38.dp else 22.dp))
    }
}

// ------------------------------------------------------------ tutorial

private data class Tip(val icon: ImageVector, val text: String)
private data class Page(val icon: ImageVector, val title: String, val body: String, val tips: List<Tip>, val tint: @Composable () -> Pair<Color, Color>)

private val PAGES = listOf(
    Page(
        Icons.Outlined.Home, "Your day at a glance",
        "Home shows what matters today, for your class.",
        listOf(
            Tip(Icons.Outlined.CalendarViewWeek, "Your next class and the rest of today"),
            Tip(Icons.Outlined.Assignment, "Assignments due soon and the latest notices"),
            Tip(Icons.Outlined.Restaurant, "Today's mess menu for your hostel"),
        ),
    ) { P.accent to P.accentSoft },
    Page(
        Icons.Outlined.CalendarViewWeek, "Timetable",
        "Your section's whole week, with your lab group's labs.",
        listOf(
            Tip(Icons.Outlined.Science, "Lectures, tutorials and labs in different colours"),
            Tip(Icons.Outlined.Notifications, "Reminders before each class, if you turned them on"),
        ),
    ) { P.indigo to P.accentSoft },
    Page(
        Icons.Outlined.Campaign, "Notices",
        "Everything posted for your year, plus notices just for your section.",
        listOf(
            Tip(Icons.Outlined.AutoAwesome, "Tap Explain to get deadlines and a checklist from any notice"),
            Tip(Icons.Outlined.Description, "Open attached PDFs right in the app"),
        ),
    ) { P.orange to P.orangeSoft },
    Page(
        Icons.AutoMirrored.Outlined.Chat, "Chat",
        "Talk with everyone on campus, or in private channels.",
        listOf(
            Tip(Icons.Outlined.Groups, "Campus chat is open to every student"),
            Tip(Icons.Outlined.Lock, "Private channels for your own group"),
        ),
    ) { P.green to P.greenSoft },
    Page(
        Icons.Outlined.GridView, "More",
        "Everything else lives under More.",
        listOf(
            Tip(Icons.Outlined.EventNote, "Exams, assignments, academic calendar and syllabus"),
            Tip(Icons.Outlined.CheckCircle, "Track your attendance and plan for 75%"),
            Tip(Icons.Outlined.Grade, "Results, mess menu, college and hostel updates"),
            Tip(Icons.Outlined.AutoAwesome, "Ask Zenkai anything about your campus"),
        ),
    ) { P.accent to P.accentSoft },
    Page(
        Icons.Outlined.Widgets, "Add the widget",
        "See today's classes and your next meal without opening the app.",
        listOf(
            Tip(Icons.Outlined.Settings, "Change your class, theme and alerts any time in More › Settings"),
        ),
    ) { P.gold to P.goldSoft },
)

/** A short swipeable tour of the main screens. Skip is on every page. */
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Column(Modifier.fillMaxSize().background(P.bg).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted("Quick tour", size = 14, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text("Skip", color = P.accent, fontWeight = FontWeight.SemiBold) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val page = PAGES[i]
            val (fg, bg) = page.tint()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(132.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(page.icon, null, tint = fg, modifier = Modifier.size(60.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(page.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center)
                Text(page.body, color = P.muted, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(4.dp))
                TipCard(page.tips, fg, bg)
                if (i == PAGES.lastIndex) {
                    val manager = AppWidgetManager.getInstance(ctx)
                    if (manager.isRequestPinAppWidgetSupported) {
                        SmallButton("Add widget to home screen", {
                            manager.requestPinAppWidget(ComponentName(ctx, TodayWidget::class.java), null, null)
                        })
                    } else {
                        Muted("Long-press your home screen, choose Widgets, then Zenkai.", size = 13)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (on) 22.dp else 8.dp)
                        .background(if (on) P.accent else P.fill2, CircleShape),
                )
            }
        }
        PrimaryButton(if (last) "Start using Zenkai" else "Next", {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        })
    }
}

@Composable
private fun TipCard(tips: List<Tip>, fg: Color, bg: Color) {
    Column(Modifier.fillMaxWidth().background(P.card, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
        tips.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = P.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Badge(t.icon, fg, bg)
                Text(t.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
