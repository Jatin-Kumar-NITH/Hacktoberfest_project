package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.vercel.campusmate_nith.data.ClassItem
import app.vercel.campusmate_nith.data.Timetable
import app.vercel.campusmate_nith.data.WEEK6
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.data.hh
import app.vercel.campusmate_nith.data.json
import app.vercel.campusmate_nith.data.span
import app.vercel.campusmate_nith.data.todayName
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Composable
fun typeColor(t: String) = when (t) {
    "Lab" -> P.green
    "Tutorial" -> P.orange
    "Studio" -> P.indigo
    "Free" -> P.muted
    else -> P.accent
}

@Composable
fun TimetableScreen(vm: AppViewModel, nav: Nav) {
    var ttYear by remember { mutableIntStateOf(vm.year) }
    var ttSection by remember { mutableStateOf(vm.section) }
    var day by remember { mutableStateOf(todayName().let { if (it in WEEK6) it else "Monday" }) }
    var editing by remember { mutableStateOf(false) }
    val tt = vm.timetable(ttYear)
    val sec = vm.sectionOf(ttYear, ttSection)
    val viewingMine = ttYear == vm.year && sec.id == vm.section

    if (editing) {
        TimetableEditor(vm, ttYear, sec.id, onClose = { editing = false })
        return
    }

    Screen("Timetable", vm) {
        item {
            ChipRow((1..4).map { it to (YEAR_NAMES[it] ?: "$it") }, ttYear) { ttYear = it; ttSection = vm.sectionOf(it, ttSection).id }
        }
        item {
            ChipRow(tt.sections.map { it.id to (it.id + if (ttYear == vm.year && it.id == vm.section) " ★" else "") }, sec.id) { ttSection = it }
        }
        item {
            Alert(if (tt.official) "✓ Official timetable. ${tt.note}" else "📋 ${tt.note.ifEmpty { tt.source }}", if (tt.official) AlertKind.Ok else AlertKind.Warn)
        }
        item {
            CmCard {
                Text(tt.title, fontWeight = FontWeight.SemiBold)
                Muted(tt.source, size = 12)
                Muted("${sec.name}${if (sec.branches.isNotEmpty()) " · ${sec.branches}" else ""}", size = 13)
                if (sec.classroom.isNotEmpty()) Muted("Classroom ${sec.classroom}${tt.classrooms[sec.classroom]?.let { " · $it" } ?: ""}", size = 13)
                if (sec.groups.isNotEmpty()) Muted("Groups: " + sec.groups.joinToString("   ") { "${it.id}: ${it.rolls}" }, size = 13)
                if (sec.groups.isNotEmpty()) Muted(if (viewingMine && vm.group.isNotEmpty()) "Faded labs are for other groups (you are ${vm.group})." else "Pick your lab group under More › Your class to highlight your labs.", size = 12)
                tt.edited_by?.let { Muted("Last edited by $it · ${fmtTime(tt.edited_at)}", size = 12) }
                if (vm.isAdmin) SmallButton("Edit this section's timetable", { editing = true })
            }
        }
        item {
            val days = WEEK6.filter { d -> sec.classes.any { it.day == d } }.ifEmpty { WEEK6.take(5) }
            ChipRow(days.map { it to it.take(3) + if (it == todayName()) " •" else "" }, if (day in days) day else days.first()) { day = it }
        }
        val shown = sec.classes.filter { it.day == day }.sortedBy { it.start }
        if (sec.classes.isEmpty()) item {
            CmCard { EmptyLine(if (vm.isAdmin) "No classes added for this section yet. Use “Edit this section's timetable” to add them." else "The timetable for this section hasn't been added yet.") }
        } else if (shown.isEmpty()) item { CmCard { EmptyLine("No classes on $day.") } }
        items(shown) { c ->
            val other = viewingMine && vm.group.isNotEmpty() && c.group != null && c.group != vm.group
            val mine = viewingMine && vm.group.isNotEmpty() && c.group == vm.group
            ClassCard(vm, ttYear, c, faded = other, highlight = mine, classroom = sec.classroom)
        }
    }
}

@Composable
private fun ClassCard(vm: AppViewModel, year: Int, c: ClassItem, faded: Boolean, highlight: Boolean, classroom: String) {
    val color = typeColor(c.type)
    Row(
        Modifier.fillMaxWidth().background(P.card, RoundedCornerShape(16.dp))
            .then(if (highlight) Modifier.border(1.5.dp, color, RoundedCornerShape(16.dp)) else Modifier)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(76.dp)) {
            Text(hh(c.start), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (faded) P.muted else P.ink)
            Muted(hh(c.end), size = 12)
        }
        Column(Modifier.width(4.dp).background(if (faded) P.line else color, RoundedCornerShape(2.dp)).padding(vertical = 18.dp)) {}
        Column(Modifier.weight(1f)) {
            Text(c.label, fontWeight = FontWeight.SemiBold, color = if (faded) P.muted else P.ink)
            vm.subjectName(c.code, year).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = if (faded) P.muted else P.ink2) }
            Muted(listOf(c.type, if (c.room != classroom) c.room else "", c.faculty).filter { it.isNotEmpty() }.joinToString(" · "), size = 12)
        }
    }
}

// ---------------------------------------------------------------- admin editor

private class Row0(c: ClassItem) {
    var day by mutableStateOf(c.day.ifEmpty { "Monday" })
    var start by mutableIntStateOf(c.start)
    var end by mutableIntStateOf(c.end)
    var code by mutableStateOf(c.code)
    var type by mutableStateOf(c.type)
    var group by mutableStateOf(c.group ?: "")
    var faculty by mutableStateOf(c.faculty)
    var room by mutableStateOf(c.room)
}

private val HOURS = (7..21).toList()

@Composable
private fun TimetableEditor(vm: AppViewModel, year: Int, sectionId: String, onClose: () -> Unit) {
    val sec = vm.sectionOf(year, sectionId)
    var classroom by remember { mutableStateOf(sec.classroom) }
    val rows = remember {
        mutableStateListOf<Row0>().apply {
            sec.classes.sortedWith(compareBy({ WEEK6.indexOf(it.day) }, { it.start })).ifEmpty { listOf(ClassItem()) }.forEach { add(Row0(it)) }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Screen("Edit · ${YEAR_NAMES[year]}, ${sec.name}", vm, onBack = onClose) {
        item { Field(classroom, { classroom = it }, "Classroom", maxLength = 40) }
        items(rows.size) { i ->
            val r = rows[i]
            CmCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Day", WEEK6.map { it to it.take(3) }, r.day, { r.day = it })
                    Dropdown("Start", HOURS.map { it to hh(it) }, r.start, { r.start = it })
                    Dropdown("End", HOURS.drop(1).map { it to hh(it) }, r.end, { r.end = it })
                }
                Field(r.code, { r.code = it }, "Subject code, e.g. MA-111", maxLength = 40)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Type", listOf("Lecture", "Lab", "Tutorial", "Studio", "Free").map { it to it }, r.type, { r.type = it })
                    Dropdown("Group", listOf("" to "All", "Gr.1" to "Gr.1", "Gr.2" to "Gr.2", "Gr.3" to "Gr.3", "Gr.4" to "Gr.4"), r.group, { r.group = it })
                }
                Field(r.faculty, { r.faculty = it }, "Faculty", maxLength = 80)
                Field(r.room, { r.room = it }, "Room (blank = classroom)", maxLength = 40)
                SmallButton("Remove this class", { rows.removeAt(i) }, danger = true)
            }
        }
        item { SmallButton("+ Add a class", { rows.add(Row0(ClassItem(day = rows.lastOrNull()?.day ?: "Monday"))) }) }
        error?.let { item { Alert(it, AlertKind.Error) } }
        item {
            PrimaryButton("Save timetable", {
                scope.launch {
                    saving = true; error = null
                    try {
                        val body = buildJsonObject {
                            put("year", JsonPrimitive(year)); put("section", JsonPrimitive(sec.id)); put("classroom", JsonPrimitive(classroom))
                            put("classes", JsonArray(rows.filter { it.code.isNotBlank() }.map { r ->
                                buildJsonObject {
                                    put("day", JsonPrimitive(r.day)); put("start", JsonPrimitive(r.start)); put("end", JsonPrimitive(r.end))
                                    put("code", JsonPrimitive(r.code)); put("type", JsonPrimitive(r.type)); put("group", JsonPrimitive(r.group))
                                    put("faculty", JsonPrimitive(r.faculty)); put("room", JsonPrimitive(r.room))
                                }
                            }))
                        }
                        val res = vm.api.edit("/api/edit/timetable", body.toString())
                        val tt = json.decodeFromJsonElement(Timetable.serializer(), res["timetable"]!!)
                        vm.replaceCampus(vm.campus.copy(timetables = vm.campus.timetables + (year.toString() to tt)))
                        onClose()
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        saving = false
                    }
                }
            }, loading = saving)
        }
        item { SmallButton("Cancel", onClose) }
    }
}
