package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.AttBase
import app.vercel.campusmate_nith.data.AttRecord
import app.vercel.campusmate_nith.data.AttStats
import app.vercel.campusmate_nith.data.attStats
import app.vercel.campusmate_nith.data.dayClasses
import app.vercel.campusmate_nith.data.dayName
import app.vercel.campusmate_nith.data.dmy
import app.vercel.campusmate_nith.data.fmtPct
import app.vercel.campusmate_nith.data.hh
import app.vercel.campusmate_nith.data.holidayOn
import app.vercel.campusmate_nith.data.niceDate
import app.vercel.campusmate_nith.data.parseDate
import app.vercel.campusmate_nith.data.span
import app.vercel.campusmate_nith.data.todayIso
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.CodeTag
import app.vercel.campusmate_nith.ui.DateField
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton

@Composable
fun AttendanceScreen(vm: AppViewModel, nav: Nav) {
    var date by remember { mutableStateOf(todayIso()) }
    var newSubject by remember { mutableStateOf("") }
    var extraCode by remember { mutableStateOf("") }
    val att = vm.attendance
    val d = parseDate(date)!!
    val dn = dayName(d)
    val hol = holidayOn(vm.campus.calendar, date)
    val classes = if (hol != null) emptyList() else dayClasses(vm.mySection, dn, vm.group).filter { it.type != "Free" }
    val subjects = vm.attSubjects()

    fun setMark(id: String, rec: AttRecord?, status: String) = vm.updateAttendance { a ->
        val rest = a.records.filter { it.id != id }
        a.copy(records = if (status.isEmpty() || rec == null) rest else rest + rec.copy(status = status))
    }

    Screen("My attendance", vm, onBack = { nav.back() }) {
        item { Muted("Private to you and saved to your account. This is your own record, not the official one.") }
        vm.attSaving?.let { s -> item { Muted(s, size = 12) } }
        item {
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { date = d.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$dn, ${dmy(date)}${if (date == todayIso()) " · today" else ""}", fontWeight = FontWeight.SemiBold)
                        Muted(vm.myLabel, size = 12)
                    }
                    IconButton(onClick = { date = d.plusDays(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateField("Jump to", date, { if (it.isNotEmpty()) date = it })
                    if (date != todayIso()) SmallButton("Today", { date = todayIso() })
                }
                Dropdown("Target", listOf(60, 65, 70, 75, 80, 85, 90).let { if (att.target in it) it else (it + att.target).sorted() }.map { it to "$it%" },
                    att.target, { t -> vm.updateAttendance { it.copy(target = t) } })
            }
        }
        hol?.let { item { Alert("🎉 ${it.title}: holiday in the academic calendar, so no regular classes.", AlertKind.Ok) } }
        if (vm.group.isEmpty() && classes.any { it.group != null }) item {
            Alert("Pick your lab group under More › Your class so only your own labs show here.", AlertKind.Info)
        }
        items(classes, key = { "${it.start}-${it.code}-${it.group}" }) { c ->
            val slot = "${c.start}${c.group?.let { "-$it" } ?: ""}"
            val id = "$date|${c.code}|$slot"
            val rec = att.records.firstOrNull { it.id == id }
            AttRow(c.label, listOf(c.type, vm.subjectName(c.code)).filter { it.isNotEmpty() }.joinToString(" · "), span(c), rec?.status ?: "", true) { s ->
                setMark(id, AttRecord(id, date, c.code, slot, c.type, s), s)
            }
        }
        val extras = att.records.filter { it.date == date && it.slot.startsWith("x") }
        items(extras, key = { it.id }) { r ->
            AttRow(r.code, listOf("Extra class", vm.subjectName(r.code)).filter { it.isNotEmpty() }.joinToString(" · "), "Extra", r.status, false) { s ->
                setMark(r.id, r, s)
            }
        }
        if (classes.isEmpty() && extras.isEmpty()) item {
            CmCard {
                EmptyLine(when {
                    hol != null -> "Enjoy the holiday! 🎉"
                    vm.mySection.classes.isNotEmpty() -> "No classes on $dn in your timetable."
                    else -> "Your section's timetable hasn't been added yet. You can still add subjects and extra classes below."
                })
            }
        }
        if (subjects.isNotEmpty()) item {
            CmCard {
                SectionTitle("Extra class on this day")
                val pick = extraCode.takeIf { it in subjects } ?: subjects.first()
                Dropdown("Subject", subjects.map { it to (vm.subjectName(it).takeIf { n -> n.isNotEmpty() }?.let { n -> "$it · $n" } ?: it) }, pick, { extraCode = it })
                Actions {
                    listOf("present" to "✓ Present", "absent" to "✗ Absent").forEach { (s, l) ->
                        SmallButton(l, {
                            val slot = "x" + System.currentTimeMillis().toString(36)
                            vm.updateAttendance { a -> a.copy(records = a.records + AttRecord("$date|$pick|$slot", date, pick, slot, "Extra", s)) }
                        })
                    }
                }
            }
        }
        item {
            CmCard {
                SectionTitle("Add a subject")
                Field(newSubject, { newSubject = it }, "Subject code, e.g. HS-101", maxLength = 40)
                SmallButton("Add", {
                    val code = newSubject.replace(Regex("\\s+"), " ").trim().take(40)
                    if (code.isNotEmpty()) {
                        if (code !in subjects) vm.updateAttendance { it.copy(subjects = it.subjects + code) }
                        newSubject = ""
                    }
                })
            }
        }

        // ---- summary
        val stats = subjects.map { attStats(att, it) }
        item {
            val a = stats.sumOf { it.attended }; val t = stats.sumOf { it.total }
            CmCard {
                SectionTitle("Summary")
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column { Text(fmtPct(if (t > 0) a * 100.0 / t else null), fontSize = 26.sp, fontWeight = FontWeight.Bold); Muted("overall", size = 12) }
                    Column { Text("$a / $t", fontSize = 26.sp, fontWeight = FontWeight.Bold); Muted("classes attended", size = 12) }
                    Column { Text("${stats.count { !it.ok }}", fontSize = 26.sp, fontWeight = FontWeight.Bold); Muted("below target", size = 12) }
                }
            }
        }
        if (stats.isEmpty()) item { CmCard { EmptyLine("No subjects yet. Your timetable's subjects appear here automatically, or add one above.") } }
        items(stats, key = { "s" + it.code }) { s -> SubjectCard(vm, s) }
    }
}

@Composable
private fun AttRow(title: String, sub: String, time: String, status: String, withCancel: Boolean, onPick: (String) -> Unit) {
    CmCard(border = when (status) { "present" -> P.green.copy(alpha = .5f); "absent" -> P.red.copy(alpha = .5f); else -> null }) {
        Muted(time, size = 12)
        Text(title, fontWeight = FontWeight.SemiBold)
        if (sub.isNotEmpty()) Muted(sub, size = 13)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("present" to "✓ Present", "absent" to "✗ Absent") + if (withCancel) listOf("cancelled" to "Cancelled") else emptyList()).forEach { (s, l) ->
                val color = when (s) { "present" -> P.green; "absent" -> P.red; else -> P.muted }
                FilterChip(
                    selected = status == s, onClick = { onPick(if (status == s) "" else s) }, label = { Text(l) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White),
                )
            }
        }
    }
}

@Composable
private fun SubjectCard(vm: AppViewModel, s: AttStats) {
    var panel by remember { mutableStateOf("") }
    val att = vm.attendance
    val color = attColor(s.pct, att.target)
    CmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CodeTag(s.code)
                vm.subjectName(s.code).takeIf { it.isNotEmpty() }?.let { Muted(it, size = 13) }
            }
            Text(if (s.pct == null) "Not marked" else fmtPct(s.pct), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(P.fill, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(((s.pct ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(att.target / 100f).fillMaxHeight()) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(12.dp).offset(y = (-2).dp).background(P.ink))
            }
        }
        Muted("${s.attended} of ${s.total} classes attended", size = 12)
        Text(s.advice, fontSize = 14.sp, color = P.ink2)
        Actions {
            SmallButton("History", { panel = if (panel == "hist") "" else "hist" })
            SmallButton("Starting count", { panel = if (panel == "base") "" else "base" })
            if (s.code in att.subjects && att.records.none { it.code == s.code } && att.base[s.code] == null) {
                SmallButton("Remove", { vm.updateAttendance { a -> a.copy(subjects = a.subjects - s.code) } }, danger = true)
            }
        }
        if (panel == "hist") {
            val recs = att.records.filter { it.code == s.code }.sortedWith(compareByDescending<AttRecord> { it.date }.thenByDescending { it.slot })
            val b = att.base[s.code]
            if (recs.isEmpty() && b == null) EmptyLine("Nothing marked yet.")
            recs.forEach { r ->
                val start = r.slot.takeWhile { it.isDigit() }.toIntOrNull()
                val time = if (r.slot.startsWith("x")) "Extra class" else start?.let { hh(it) } ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Muted("${niceDate(r.date)}${if (time.isNotEmpty()) " · $time" else ""}", Modifier.weight(1f), size = 13)
                    Text(r.status.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = when (r.status) { "present" -> P.green; "absent" -> P.red; else -> P.muted })
                    IconButton(onClick = { vm.updateAttendance { a -> a.copy(records = a.records.filter { it.id != r.id }) } }) { Text("×", color = P.muted) }
                }
            }
            b?.let { Muted("Starting count: ${it.attended} of ${it.total} attended", size = 13) }
        }
        if (panel == "base") {
            val b = att.base[s.code] ?: AttBase()
            var attended by remember { mutableStateOf(b.attended.toString()) }
            var held by remember { mutableStateOf(b.total.toString()) }
            var err by remember { mutableStateOf("") }
            Muted("Classes held before you started tracking here (for example from the start of the semester). They're added to your marked classes.", size = 13)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(attended, { attended = it.filter(Char::isDigit) }, "Attended", Modifier.weight(1f), number = true, maxLength = 4)
                Field(held, { held = it.filter(Char::isDigit) }, "Held", Modifier.weight(1f), number = true, maxLength = 4)
            }
            if (err.isNotEmpty()) Text(err, color = P.red, fontSize = 13.sp)
            SmallButton("Save", {
                val a = attended.toIntOrNull() ?: 0; val t = held.toIntOrNull() ?: 0
                when {
                    t > 1000 -> err = "Use whole numbers up to 1000."
                    a > t -> err = "Attended can't be more than held."
                    else -> {
                        vm.updateAttendance { x -> x.copy(base = if (t > 0) x.base + (s.code to AttBase(a, t)) else x.base - s.code) }
                        panel = ""
                    }
                }
            })
        }
    }
}
