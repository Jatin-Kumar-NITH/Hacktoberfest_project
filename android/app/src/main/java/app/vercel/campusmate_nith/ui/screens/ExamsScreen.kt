package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.ExamRow
import app.vercel.campusmate_nith.data.ExamYear
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.branchOptions
import app.vercel.campusmate_nith.data.daysUntil
import app.vercel.campusmate_nith.data.daysUntilLabel
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.data.forStudent
import app.vercel.campusmate_nith.data.json
import app.vercel.campusmate_nith.data.niceDate
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.CodeTag
import app.vercel.campusmate_nith.ui.DateBadge
import app.vercel.campusmate_nith.ui.DateField
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
fun ExamsScreen(vm: AppViewModel, nav: Nav) {
    var editing by remember { mutableStateOf(false) }
    val ex = vm.campus.exams.years[vm.year.toString()]
    if (editing) {
        ExamEditor(vm, ex ?: ExamYear(), onClose = { editing = false })
        return
    }
    val branches = ex?.branchOptions().orEmpty()
    val branch = vm.myExamBranch(ex)
    val rows = ex?.rows.orEmpty().filter { forStudent(it, vm.section, branch) }.sortedBy { it.date }
    val upcoming = rows.filter { daysUntil(it.date) >= 0 }
    val nameOf = { r: ExamRow -> r.name.ifEmpty { vm.subjectName(r.code) } }
    Screen("Exams", vm, onBack = { nav.back() }) {
        item {
            Column {
                Text(ex?.name?.ifEmpty { null } ?: "Exam schedule", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Muted("${YEAR_NAMES[vm.year]}, ${branch.ifEmpty { "Section ${vm.section}" }}")
                ex?.edited_by?.let { Muted("Last edited by $it · ${fmtTime(ex.edited_at)}", size = 12) }
            }
        }
        if (branches.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Dropdown("Your branch", listOf("" to "All branches") + branches.map { it to it }, branch, { vm.setExamBranch(it) })
                if (branch.isEmpty()) Muted("Papers differ by branch. Pick yours to see only your exams.", size = 13)
            }
        }
        if (vm.isAdmin) item { SmallButton("Edit exam schedule", { editing = true }) }
        if (rows.isEmpty()) item { CmCard { EmptyLine("No exams have been scheduled for your year yet.") } }
        else {
            item {
                Alert(if (upcoming.isNotEmpty()) "First exam ${daysUntilLabel(upcoming[0].date)} · ${upcoming.size} papers" else "No upcoming papers.",
                    if (upcoming.isNotEmpty()) AlertKind.Info else AlertKind.Ok)
            }
            items(rows) { r ->
                CmCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateBadge(r.date, daysUntil(r.date) in 0..3)
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CodeTag(r.code) }
                            nameOf(r).takeIf { it.isNotEmpty() }?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                            r.branches.filter { it != "all" }.takeIf { branch.isEmpty() && it.isNotEmpty() }
                                ?.let { Muted(it.joinToString(", "), size = 12) }
                            Muted(listOf(niceDate(r.date), r.time, r.venue).filter { it.isNotEmpty() }.joinToString(" · "), size = 13)
                        }
                        Text(daysUntilLabel(r.date), fontSize = 13.sp, color = P.muted)
                    }
                }
            }
            if (upcoming.isNotEmpty()) item {
                PrimaryButton("✦ Plan my revision for these exams", {
                    vm.pendingPlan = upcoming.take(8).map { r ->
                        val nm = nameOf(r)
                        (if (nm.isNotEmpty()) "${r.code} $nm" else r.code) to r.date
                    }
                    nav.go("planner")
                })
            }
        }
        if (rows.isNotEmpty() && ex?.notes.orEmpty().isNotEmpty()) item {
            CmCard {
                SectionTitle("Notes for your year")
                ex?.notes.orEmpty().forEach { Text("• $it", fontSize = 14.sp, color = P.ink2) }
            }
        }
        if (vm.campus.exams.rules.isNotEmpty()) item {
            CmCard {
                SectionTitle("Exam rules")
                vm.campus.exams.rules.forEach { Text("• $it", fontSize = 14.sp, color = P.ink2) }
            }
        }
    }
}

private class ExamEdit(r: ExamRow) {
    var date by mutableStateOf(r.date)
    var time by mutableStateOf(r.time)
    var code by mutableStateOf(r.code)
    var name by mutableStateOf(r.name)
    var secs by mutableStateOf(r.sections.filter { it != "all" }.joinToString(", "))
    var branches by mutableStateOf(r.branches.filter { it != "all" }.joinToString(", "))
    var venue by mutableStateOf(r.venue)
}

@Composable
private fun ExamEditor(vm: AppViewModel, ex: ExamYear, onClose: () -> Unit) {
    var name by remember { mutableStateOf(ex.name) }
    val rows = remember { mutableStateListOf<ExamEdit>().apply { ex.rows.ifEmpty { listOf(ExamRow()) }.forEach { add(ExamEdit(it)) } } }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val year = vm.year
    Screen("Edit exams · ${YEAR_NAMES[year]}", vm, onBack = onClose) {
        item { Muted("This schedule is shared by all sections of ${YEAR_NAMES[year]}. Leave sections and branches blank for everyone.") }
        item { Field(name, { name = it }, "Schedule name, e.g. Mid-semester exams", maxLength = 120) }
        items(rows.size) { i ->
            val r = rows[i]
            CmCard {
                DateField("Date", r.date, { r.date = it })
                Field(r.time, { r.time = it }, "Time, e.g. 9:30–11:00 AM", maxLength = 40)
                Field(r.code, { r.code = it }, "Subject code, e.g. MA-111", maxLength = 60)
                Field(r.name, { r.name = it }, "Subject name", maxLength = 160)
                Field(r.secs, { r.secs = it }, "Sections (blank = all), e.g. A, B", maxLength = 40)
                Field(r.branches, { r.branches = it }, "Branches (blank = all), e.g. CSE, ECE", maxLength = 200)
                Field(r.venue, { r.venue = it }, "Venue", maxLength = 60)
                SmallButton("Remove", { rows.removeAt(i) }, danger = true)
            }
        }
        item { SmallButton("+ Add a paper", { rows.add(ExamEdit(ExamRow())) }) }
        error?.let { item { Alert(it, AlertKind.Error) } }
        item {
            PrimaryButton("Save exam schedule", {
                scope.launch {
                    saving = true; error = null
                    try {
                        val body = buildJsonObject {
                            put("year", JsonPrimitive(year)); put("name", JsonPrimitive(name))
                            put("rows", JsonArray(rows.filter { it.code.isNotBlank() || it.date.isNotEmpty() }.map { r ->
                                buildJsonObject {
                                    put("date", JsonPrimitive(r.date)); put("time", JsonPrimitive(r.time)); put("code", JsonPrimitive(r.code))
                                    put("venue", JsonPrimitive(r.venue)); put("name", JsonPrimitive(r.name))
                                    put("branches", JsonArray(r.branches.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
                                    put("sections", JsonArray(r.secs.uppercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
                                }
                            }))
                        }
                        val res = vm.api.edit("/api/edit/exams", body.toString())
                        val saved = json.decodeFromJsonElement(ExamYear.serializer(), res["exams"]!!)
                        val c = vm.campus
                        vm.replaceCampus(c.copy(exams = c.exams.copy(years = c.exams.years + (year.toString() to saved))))
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
