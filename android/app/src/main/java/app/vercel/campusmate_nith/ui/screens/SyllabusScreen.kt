@file:OptIn(ExperimentalLayoutApi::class)

package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.Section
import app.vercel.campusmate_nith.data.SyllabusYear
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.clean
import app.vercel.campusmate_nith.data.forStudent
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.AiButton
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.CodeTag
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PdfButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import app.vercel.campusmate_nith.ui.DeleteAction
import app.vercel.campusmate_nith.ui.EditField
import app.vercel.campusmate_nith.ui.ItemEditorDialog

@Composable
fun SyllabusScreen(vm: AppViewModel, nav: Nav) {
    val sec = vm.mySection
    val syl = vm.syllabus()
    val codes = sec.classes.map { it.code }.distinct().sorted()
    val others = syl.subjects.keys.filter { it !in codes }.sorted()
    var showOthers by remember { mutableStateOf(false) }
    val mats = vm.campus.posts.filter { it.kind == "material" && forStudent(it, vm.year, vm.section) }
    Screen("Syllabus", vm, onBack = { nav.back() }) {
        item { Muted("${YEAR_NAMES[vm.year]}, ${sec.name}: subjects in your timetable.") }
        item { Alert(if (syl.official) "✓ ${syl.title}. ${syl.note}" else "📋 ${syl.note.ifEmpty { "The syllabus for this year hasn't been added yet." }}", if (syl.official) AlertKind.Ok else AlertKind.Warn) }
        if (syl.documents.isNotEmpty()) item { Actions { syl.documents.forEach { PdfButton(vm, it.pdf, it.title) } } }
        if (mats.isNotEmpty()) {
            item { SectionTitle("Study material from admins") }
            items(mats, key = { it.id }) { m ->
                var editing by remember { mutableStateOf(false) }
                if (editing) ItemEditorDialog(
                    vm, "Edit study material",
                    listOf(
                        EditField("title", "Title", m.title, max = 150),
                        EditField("code", "Subject code", m.code.orEmpty(), max = 40),
                        EditField("body", "Description (optional)", m.body, kind = "textarea", max = 8000),
                    ),
                    pdfLabel = "Replace PDF (optional)", onDismiss = { editing = false },
                ) { fields, pdf -> vm.api.editPost(m.db_id, fields, pdf) }
                CmCard {
                    m.code?.let { CodeTag(it) }
                    Text(m.title, fontWeight = FontWeight.SemiBold)
                    if (m.body.isNotEmpty()) Text(m.body, fontSize = 14.sp, color = P.ink2)
                    PubLine(m.published_by, m.author_role, m.published_at, "")
                    Actions {
                        m.pdf?.let { PdfButton(vm, it, "Open PDF") }
                        if (canDelete(vm, m.db_id, m.author_role, m.author_id)) {
                            SmallButton("✎ Edit", { editing = true })
                            DeletePostButton(vm, m.db_id)
                        }
                    }
                }
            }
        }
        if (codes.isEmpty()) item {
            CmCard {
                EmptyLine(if (syl.subjects.isNotEmpty()) "Add this section's timetable to see its subjects here."
                else "The syllabus for this year hasn't been added yet. Admins can publish syllabus PDFs as Study material.")
            }
        }
        items(codes, key = { it }) { SubjectCard(vm, nav, it, sec, syl) }
        if (others.isNotEmpty()) {
            item { SmallButton(if (showOthers) "Hide other subjects" else "Other subjects this year (${others.size})", { showOthers = !showOthers }) }
            if (showOthers) items(others, key = { "o$it" }) { SubjectCard(vm, nav, it, null, syl) }
        }
    }
}

@Composable
private fun SubjectCard(vm: AppViewModel, nav: Nav, code: String, sec: Section?, syl: SyllabusYear) {
    val sub = syl.subjects[code]
    val cls = sec?.classes?.filter { it.code == code }.orEmpty()
    val mine = cls.filter { it.group == null || vm.group.isEmpty() || it.group == vm.group }
    var showText by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    if (editing && sub != null) ItemEditorDialog(
        vm, "Edit $code syllabus",
        listOf(
            EditField("name", "Subject name", sub.name, max = 150),
            EditField("text", "Syllabus text", sub.text, kind = "textarea", max = 20000),
            EditField("note", "Note (optional)", sub.note.orEmpty(), max = 300),
        ),
        pdfLabel = "Replace PDF (optional)", onDismiss = { editing = false },
    ) { fields, pdf -> vm.api.editSubject(vm.year, code, fields, pdf) }
    CmCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CodeTag(code)
            sub?.semester?.let { Tag("Semester ${it.clean()}") }
        }
        Text(sub?.name ?: code, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sub?.L != null) Tag("L-P: ${sub.L.clean()}-${sub.P?.clean() ?: "0"} · ${sub.credits?.clean() ?: "?"} credits")
            sub?.type?.let { Tag(it) }
            listOf("Lecture", "Lab", "Tutorial", "Studio").forEach { k ->
                val h = mine.filter { it.type == k }.sumOf { it.end - it.start }
                if (h > 0) Tag("$k: $h h/week")
            }
        }
        val fac = cls.flatMap { it.faculty.split(Regex(",\\s*")) }.filter { it.isNotEmpty() }.distinct()
        if (fac.isNotEmpty()) Muted("Faculty (from timetable): ${fac.joinToString(", ")}", size = 13)
        if (sub == null) {
            EmptyLine(syl.missing[code] ?: "Syllabus not added yet.")
            return@CmCard
        }
        sub.note?.let { Muted(it, size = 13) }
        Actions {
            sub.pdf?.let { PdfButton(vm, it, "Original PDF" + if (syl.official && sub.pages.isNotEmpty()) " (p. ${sub.pages.joinToString(", ")})" else "") }
            SmallButton(if (showText) "Hide text" else "📝 Read as text", { showText = !showText })
            AiButton("Summarise", {
                vm.pendingQuestion = "Summarise the syllabus of $code${if (sub.name != code) " " + sub.name else ""} unit by unit, in simple words."
                nav.go("ask")
            })
            if (vm.isAdmin) {
                SmallButton("✎ Edit", { editing = true })
                DeleteAction(vm) { vm.api.deleteSubject(vm.year, code) }
            }
        }
        if (showText) Text(sub.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = P.ink2)
    }
}
