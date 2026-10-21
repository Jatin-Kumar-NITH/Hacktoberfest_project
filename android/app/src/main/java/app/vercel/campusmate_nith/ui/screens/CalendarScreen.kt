package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.AcademicCalendar
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.CAL_TYPES
import app.vercel.campusmate_nith.data.CalEvent
import app.vercel.campusmate_nith.data.eventsOn
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.data.holidayOn
import app.vercel.campusmate_nith.data.json
import app.vercel.campusmate_nith.data.parseDate
import app.vercel.campusmate_nith.data.rangeLabel
import app.vercel.campusmate_nith.data.spanDays
import app.vercel.campusmate_nith.data.todayIso
import app.vercel.campusmate_nith.data.whenLabel
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.DateBadge
import app.vercel.campusmate_nith.ui.DateField
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.ListRow
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import app.vercel.campusmate_nith.ui.soonIso
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import app.vercel.campusmate_nith.data.YEAR_NAMES
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun calColor(type: String): Pair<Color, Color> = when (type) {
    "holiday" -> P.red to P.redSoft
    "exam" -> P.orange to P.orangeSoft
    "semester" -> P.indigo to P.accentSoft
    else -> P.accent to P.accentSoft
}

@Composable
fun CalTag(type: String) {
    val (fg, bg) = calColor(type)
    Tag(CAL_TYPES[type] ?: type, fg, bg)
}

@Composable
fun CalendarScreen(vm: AppViewModel, nav: Nav) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        CalendarEditor(vm, vm.campus.calendar, onClose = { editing = false })
        return
    }
    val all = vm.campus.calendar
    val cal = vm.myCalendar   // only this year's entries and the all-years ones
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("upcoming") }
    val today = todayIso()

    Screen("Academic calendar", vm, onBack = { nav.back() }) {
        item {
            Column {
                Text(cal.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Muted("Confirmed holidays and important dates for ${YEAR_NAMES[vm.year]}")
                cal.edited_by?.let { Muted("Last edited by $it · ${fmtTime(cal.edited_at)}", size = 12) }
            }
        }
        item { Alert("📅 ${cal.note ?: "Holidays and important dates are confirmed by Zenkai admins."}", AlertKind.Ok) }
        if (vm.isAdmin) item { SmallButton("Edit academic calendar", { editing = true }) }
        item {
            val left = cal.events.filter { it.type == "holiday" && it.end.ifEmpty { it.date } >= today }
            val now = holidayOn(cal, today)
            CmCard {
                if (cal.events.isEmpty()) EmptyLine(
                    (if (all.events.isNotEmpty()) "No dates for ${YEAR_NAMES[vm.year]} yet. " else "No dates added yet. ") +
                        if (vm.isAdmin) "Use “Edit academic calendar” to add holidays." else "Admins will add confirmed holidays here.",
                )
                else {
                    now?.let { Text("🎉 Today: ${it.title} · holiday, no classes", color = P.red, fontWeight = FontWeight.SemiBold) }
                    left.firstOrNull { it.date > today }?.let { Text("Next holiday: ${it.title} · ${rangeLabel(it)} · ${whenLabel(it)}", fontWeight = FontWeight.SemiBold) }
                    Muted("${left.size} ${if (left.size == 1) "holiday" else "holidays"} left this calendar")
                }
            }
        }
        item {
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                }
                MonthGrid(cal, month, selected) { selected = if (selected == it) null else it }
                SmallButton("Today", { month = YearMonth.now(); selected = today })
            }
        }
        selected?.let { iso ->
            item {
                val d = parseDate(iso)!!
                val evs = eventsOn(cal, iso)
                CmCard {
                    Text(d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)), fontWeight = FontWeight.SemiBold)
                    if (evs.isEmpty()) EmptyLine(if (d.dayOfWeek.value == 7) "Sunday · no holidays or events added." else "No holidays or events on this day.")
                    evs.forEach { e ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CalTag(e.type)
                            Column {
                                Text(e.title, fontWeight = FontWeight.SemiBold)
                                Muted("${rangeLabel(e)}${if (spanDays(e) > 1) " · ${spanDays(e)} days" else ""}", size = 12)
                                if (e.note.isNotEmpty()) Muted(e.note, size = 13)
                            }
                        }
                    }
                }
            }
        }
        item { ChipRow(listOf("upcoming" to "Upcoming", "holiday" to "Holidays", "all" to "All"), filter) { filter = it } }
        val list = cal.events.filter { filter == "all" || (if (filter == "holiday") it.type == "holiday" else it.end.ifEmpty { it.date } >= today) }
        if (list.isEmpty()) item { CmCard { EmptyLine(if (filter == "upcoming") "No upcoming dates added yet." else "Nothing added yet.") } }
        items(list) { e ->
            CmCard(onClick = { month = YearMonth.from(parseDate(e.date) ?: LocalDate.now()); selected = e.date }) {
                ListRow({ DateBadge(e.date, e.type == "holiday" && soonIso(e.date)) }, e.title,
                    "${rangeLabel(e)}${if (spanDays(e) > 1) " · ${spanDays(e)} days" else ""} · ${whenLabel(e)}" + if (e.note.isNotEmpty()) "\n${e.note}" else "",
                    trailing = { CalTag(e.type) })
            }
        }
    }
}

@Composable
private fun MonthGrid(cal: AcademicCalendar, month: YearMonth, selected: String?, onPick: (String) -> Unit) {
    val today = todayIso()
    val lead = month.atDay(1).dayOfWeek.value - 1   // Monday first
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = P.muted, fontSize = 12.sp) } }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (d != null) {
                            val iso = d.toString()
                            val evs = eventsOn(cal, iso)
                            val hol = evs.any { it.type == "holiday" }
                            val shape = RoundedCornerShape(10.dp)
                            Column(
                                Modifier.fillMaxWidth().aspectRatio(1f)
                                    .background(if (hol) P.redSoft else if (iso == today) P.accentSoft else Color.Transparent, shape)
                                    .then(if (iso == selected) Modifier.border(1.5.dp, P.ink, shape) else Modifier)
                                    .clickable { onPick(iso) }.padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text(d.dayOfMonth.toString(), fontSize = 14.sp,
                                    fontWeight = if (iso == today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (hol || d.dayOfWeek.value == 7) P.red else P.ink)
                                if (evs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    evs.take(3).forEach { e -> Box(Modifier.padding(top = 2.dp).background(calColor(e.type).first, RoundedCornerShape(50)).padding(2.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class CalEdit(e: CalEvent) {
    var date by mutableStateOf(e.date)
    var end by mutableStateOf(if (e.end != e.date) e.end else "")
    var title by mutableStateOf(e.title)
    var type by mutableStateOf(e.type)
    var note by mutableStateOf(e.note)
    /** "all", or the years (1-4) this entry is for. */
    var years by mutableStateOf(e.yearList.toSet())
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEditor(vm: AppViewModel, cal: AcademicCalendar, onClose: () -> Unit) {
    var name by remember { mutableStateOf(cal.name) }
    val rows = remember { mutableStateListOf<CalEdit>().apply { cal.events.ifEmpty { listOf(CalEvent()) }.forEach { add(CalEdit(it)) } } }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Screen("Edit academic calendar", vm, onBack = onClose) {
        item { Field(name, { name = it }, "Calendar name", maxLength = 120) }
        items(rows.size) { i ->
            val r = rows[i]
            CmCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Date", r.date, { r.date = it })
                    DateField("Ends (optional)", r.end, { r.end = it }, allowClear = true)
                }
                Field(r.title, { r.title = it }, "Title, e.g. Diwali", maxLength = 120)
                Dropdown("Type", CAL_TYPES.entries.map { it.key to it.value }, r.type, { r.type = it })
                Field(r.note, { r.note = it }, "Note (optional)", maxLength = 200)
                Muted("For", size = 13)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = "all" in r.years, onClick = { r.years = setOf("all") }, label = { Text("All years") })
                    (1..4).forEach { y ->
                        val on = y.toString() in r.years
                        FilterChip(selected = on, label = { Text(YEAR_NAMES[y] ?: "$y") }, onClick = {
                            val next = (r.years - "all").let { if (on) it - y.toString() else it + y.toString() }
                            // none or all four ticked means everyone, like the website
                            r.years = if (next.isEmpty() || next.size == 4) setOf("all") else next
                        })
                    }
                }
                SmallButton("Remove", { rows.removeAt(i) }, danger = true)
            }
        }
        item { SmallButton("+ Add a date", { rows.add(CalEdit(CalEvent())) }) }
        error?.let { item { Alert(it, AlertKind.Error) } }
        item {
            PrimaryButton("Save calendar", {
                scope.launch {
                    saving = true; error = null
                    try {
                        val body = buildJsonObject {
                            put("name", JsonPrimitive(name))
                            put("events", JsonArray(rows.filter { it.date.isNotEmpty() || it.title.isNotBlank() }.map { r ->
                                buildJsonObject {
                                    put("date", JsonPrimitive(r.date)); put("end", JsonPrimitive(r.end)); put("title", JsonPrimitive(r.title))
                                    put("type", JsonPrimitive(r.type)); put("note", JsonPrimitive(r.note))
                                    put("years", JsonArray(r.years.sorted().map { JsonPrimitive(it) }))
                                }
                            }))
                        }
                        val res = vm.api.edit("/api/edit/calendar", body.toString())
                        val saved = json.decodeFromJsonElement(AcademicCalendar.serializer(), res["calendar"]!!)
                        vm.replaceCampus(vm.campus.copy(calendar = saved))
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
