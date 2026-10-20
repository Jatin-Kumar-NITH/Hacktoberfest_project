package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.MEALS
import app.vercel.campusmate_nith.data.attStats
import app.vercel.campusmate_nith.data.boardItems
import app.vercel.campusmate_nith.data.dayClasses
import app.vercel.campusmate_nith.data.dayName
import app.vercel.campusmate_nith.data.daysUntil
import app.vercel.campusmate_nith.data.daysUntilLabel
import app.vercel.campusmate_nith.data.dmy
import app.vercel.campusmate_nith.data.fmtPct
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.data.forStudent
import app.vercel.campusmate_nith.data.holidayOn
import app.vercel.campusmate_nith.data.isSectionSpecific
import app.vercel.campusmate_nith.data.rangeLabel
import app.vercel.campusmate_nith.data.today
import app.vercel.campusmate_nith.data.todayIso
import app.vercel.campusmate_nith.data.todayName
import app.vercel.campusmate_nith.data.whenLabel
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.CodeTag
import app.vercel.campusmate_nith.ui.DateBadge
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.ListRow
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.UpdateBanner
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import app.vercel.campusmate_nith.ui.soonIso
import java.time.LocalTime

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav) {
    val hour = LocalTime.now().hour
    val greeting = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"
    val c = vm.campus
    val sec = vm.mySection
    val tIso = todayIso()
    Screen("CampusMate", vm, actions = {
        IconButton(onClick = { nav.go("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
        IconButton(onClick = { nav.go("profile") }) { Icon(Icons.Outlined.AccountCircle, "Your class") }
    }) {
        item {
            Column {
                Text("$greeting 👋", style = MaterialTheme.typography.headlineMedium)
                Muted(vm.myLabel)
            }
        }
        item { UpdateBanner(vm) }

        // ---- ask bar
        item {
            CmCard(onClick = { nav.go("ask") }) {
                Text("✦ Ask CampusMate", fontWeight = FontWeight.SemiBold, color = P.accent)
                Muted("“What classes do I have tomorrow?” “When is my next exam?”")
            }
        }

        // ---- classes (today, or the next day that has some)
        item {
            val hol = holidayOn(vm.myCalendar, tIso)
            var label = "Today's classes"
            var classes = if (hol != null) emptyList() else dayClasses(sec, todayName(), vm.group)
            if (classes.isEmpty()) {
                for (i in 1..21) {
                    val d = today().plusDays(i.toLong())
                    if (holidayOn(vm.myCalendar, d.toString()) != null) continue
                    val found = dayClasses(sec, dayName(d), vm.group)
                    if (found.isNotEmpty()) {
                        classes = found
                        label = "${if (hol != null) "🎉 Holiday today: ${hol.title}" else "No classes today 🎉"} · " +
                            "${if (i == 1) "Tomorrow" else "${dayName(d)}, ${dmy(d.toString())}"}'s classes"
                        break
                    }
                }
            }
            CmCard(onClick = { nav.tab("timetable") }) {
                SectionTitle(label)
                if (classes.isEmpty()) EmptyLine("Your section's timetable hasn't been added yet.")
                classes.forEach { cl ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.vercel.campusmate_nith.data.hh(cl.start), color = P.muted, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cl.label, fontWeight = FontWeight.SemiBold)
                                vm.subjectName(cl.code).takeIf { it.isNotEmpty() }?.let { Muted(it, size = 13) }
                            }
                            Muted(listOf(cl.type, cl.room, cl.faculty).filter { it.isNotEmpty() }.joinToString(" · "), size = 12)
                        }
                    }
                }
            }
        }

        // ---- due soon: assignments + next exam
        item {
            val pending = app.vercel.campusmate_nith.data.assignmentItems(c)
                .filter { forStudent(it, vm.year, vm.section) && vm.assignmentsDone[it.id] != true && (it.due == null || daysUntil(it.due) >= 0) }
                .sortedWith(compareBy({ it.due ?: "9999" }, { it.posted })).take(3)
            val nextExam = c.exams.years[vm.year.toString()]?.rows
                ?.filter { forStudent(it, vm.section, vm.myExamBranch()) && daysUntil(it.date) >= 0 }?.minByOrNull { it.date }
            CmCard {
                SectionTitle("Due soon")
                if (pending.isEmpty() && nextExam == null) EmptyLine("Nothing due. 🎉")
                pending.forEach { a ->
                    ListRow({ DateBadge(a.due ?: "", soonIso(a.due), if (a.due == null) "📄" else null) }, "${a.code}: ${a.title}",
                        if (a.due != null) "Assignment · due ${daysUntilLabel(a.due)}" else "Assignment · due date not stated",
                        onClick = { nav.go("assignments") })
                }
                nextExam?.let { e ->
                    ListRow({ DateBadge(e.date, true) }, "Exam: ${e.code}", "${e.time} · ${daysUntilLabel(e.date)}", onClick = { nav.go("exams") })
                }
            }
        }

        // ---- notices for me
        item {
            val mine = boardItems(c).filter { forStudent(it, vm.year, vm.section) }.take(4)
            CmCard {
                SectionTitle("Latest notices") { SmallButton("All", { nav.tab("board") }) }
                if (mine.isEmpty()) EmptyLine("No notices yet.")
                mine.forEach { n ->
                    val specific = n.isSectionSpecific()
                    ListRow({ Tag(if (specific) vm.section else "All", if (specific) P.accent else P.muted, if (specific) P.accentSoft else P.fill) },
                        n.title, "${n.from} · ${if (specific) "your section" else "everyone"}",
                        onClick = { vm.pendingExplain = n.id to n.text; nav.go("explain") })
                }
            }
        }

        // ---- announcements
        item {
            val ann = c.posts.filter { it.kind == "announcement" && forStudent(it, vm.year, vm.section) }.take(3)
            if (ann.isNotEmpty()) CmCard {
                SectionTitle("Announcements")
                ann.forEach { a ->
                    ListRow({ DateBadge(app.vercel.campusmate_nith.data.localDate(a.published_at)) }, a.title, "${a.published_by} · ${fmtTime(a.published_at)}")
                }
            }
        }

        // ---- attendance
        item {
            val stats = vm.attSubjects().map { attStats(vm.attendance, it, vm.myCalendar) }.filter { it.total > 0 }
            CmCard(onClick = { nav.go("attendance") }) {
                SectionTitle("My attendance")
                if (stats.isEmpty()) EmptyLine("Not tracking yet. Mark today's classes as present or absent to see your attendance here.")
                else {
                    val a = stats.sumOf { it.attended }; val t = stats.sumOf { it.total }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(fmtPct(a * 100.0 / t), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Muted("overall · $a of $t classes · target ${vm.attendance.target}%", size = 13)
                    }
                    stats.sortedBy { it.pct }.take(3).forEach { s ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CodeTag(s.code)
                            Muted(s.advice, Modifier.weight(1f), size = 12)
                            Text(fmtPct(s.pct), fontWeight = FontWeight.SemiBold, color = attColor(s.pct, vm.attendance.target))
                        }
                    }
                }
            }
        }

        // ---- today's study plan
        item {
            val day = vm.plan?.result?.days?.firstOrNull { it.date == tIso }
            CmCard(onClick = { nav.go("planner") }) {
                SectionTitle("Today's study plan")
                if (day == null) EmptyLine(if (vm.plan != null) "Nothing scheduled for today in your saved plan." else "No plan yet. The Study Planner builds one from your exam dates.")
                else {
                    if (day.focus.isNotEmpty()) Muted(day.focus)
                    day.sessions.forEach { SessionRow(it) }
                }
            }
        }

        // ---- mess menu
        item {
            val h = vm.myHostel().forYear(vm.year)
            CmCard(onClick = { nav.go("mess") }) {
                SectionTitle("Today's menu · ${h.name}")
                val meals = h.mess_menu[todayName()].orEmpty()
                if (MEALS.none { !meals[it].isNullOrEmpty() }) EmptyLine("Menu not added yet.")
                MEALS.forEach { m ->
                    val items = meals[m]
                    if (!items.isNullOrEmpty()) Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(m, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            h.mess_timings[m]?.takeIf { it.isNotEmpty() }?.let { Muted(it, size = 12) }
                        }
                        Text(items, fontSize = 14.sp, color = P.ink2)
                    }
                }
            }
        }

        // ---- holidays
        item {
            val hols = vm.myCalendar.events.filter { it.type == "holiday" && it.end.ifEmpty { it.date } >= tIso }.take(3)
            CmCard {
                SectionTitle("Upcoming holidays") { SmallButton("Calendar", { nav.go("calendar") }) }
                if (hols.isEmpty()) EmptyLine("No upcoming holidays added yet.")
                hols.forEach { e -> ListRow({ DateBadge(e.date, soonIso(e.date)) }, e.title, "${rangeLabel(e)} · ${whenLabel(e)}", onClick = { nav.go("calendar") }) }
            }
        }

        // ---- explained notices (on this phone)
        item {
            CmCard {
                SectionTitle("Explained notices") { if (vm.history.isNotEmpty()) SmallButton("Clear", { vm.clearHistory() }) }
                if (vm.history.isEmpty()) EmptyLine("Notices you explain with AI appear here with their checklists.")
                vm.history.take(5).forEach { h ->
                    val done = h.done.count { it }
                    ListRow({ UrgencyDot(h.result.urgency) }, h.result.title, "${fmtTime(h.at)} · $done/${h.done.size} tasks done",
                        onClick = { vm.openNotice = h.id; nav.go("explain") })
                }
            }
        }
    }
}
