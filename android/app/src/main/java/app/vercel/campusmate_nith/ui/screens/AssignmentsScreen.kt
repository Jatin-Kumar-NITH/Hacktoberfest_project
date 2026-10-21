@file:OptIn(ExperimentalLayoutApi::class)

package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.assignmentItems
import app.vercel.campusmate_nith.data.daysUntil
import app.vercel.campusmate_nith.data.daysUntilLabel
import app.vercel.campusmate_nith.data.dmy
import app.vercel.campusmate_nith.data.forStudent
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.AiButton
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.CodeTag
import app.vercel.campusmate_nith.ui.DeleteAction
import app.vercel.campusmate_nith.ui.EditField
import app.vercel.campusmate_nith.ui.ItemEditorDialog
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PdfButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag

@Composable
fun AssignmentsScreen(vm: AppViewModel, nav: Nav) {
    var filter by remember { mutableStateOf("pending") }
    val mine = assignmentItems(vm.campus).filter { forStudent(it, vm.year, vm.section) }
        .sortedWith(compareBy<app.vercel.campusmate_nith.data.AssignmentItem> { it.due ?: "9999" }.thenByDescending { it.posted })
    val list = mine.filter { filter == "all" || (filter == "done") == (vm.assignmentsDone[it.id] == true) }
    Screen("Assignments", vm, onBack = { nav.back() }) {
        if (vm.campus.assignments.note.isNotEmpty()) item { Alert("📚 ${vm.campus.assignments.note}", AlertKind.Ok) }
        item { Muted("Published for ${YEAR_NAMES[vm.year]}, Section ${vm.section}.") }
        item { ChipRow(listOf("pending" to "Pending", "done" to "Submitted", "all" to "All"), filter) { filter = it } }
        if (list.isEmpty()) item { CmCard { EmptyLine(if (filter == "pending") "Nothing pending. 🎉" else "Nothing here yet.") } }
        items(list, key = { it.id }) { a ->
            var editing by remember { mutableStateOf(false) }
            val builtin = a.dbId == null
            if (editing) ItemEditorDialog(
                vm, "Edit assignment",
                listOfNotNull(
                    EditField("title", "Title", a.title, max = 150),
                    EditField("code", "Subject code", if (a.code == "General") "" else a.code, max = 40),
                    EditField("body", "Description / instructions",
                        if (builtin || a.details != "See the attached PDF.") a.details else "", kind = "textarea", max = 8000),
                    EditField("due", "Due date", a.due.orEmpty(), kind = "date"),
                    EditField("marks", "Marks", a.marks?.toString().orEmpty(), kind = "number"),
                    if (builtin) EditField("submit", "How to submit", a.submit, max = 300) else null,
                ),
                pdfLabel = if (a.pdf != null) "Replace PDF (optional)" else "Attach PDF (optional)",
                onDismiss = { editing = false },
            ) { fields, pdf -> if (builtin) vm.api.editAssignment(a.id, fields, pdf) else vm.api.editPost(a.dbId!!, fields, pdf) }
            val done = vm.assignmentsDone[a.id] == true
            val n = a.due?.let { daysUntil(it) }
            val soon = !done && n != null && n in 0..3
            CmCard(border = if (soon) P.red.copy(alpha = 0.5f) else null) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CodeTag(a.code)
                            if (a.official) Tag("Real assignment", P.green, P.greenSoft) else Tag("Sample")
                        }
                        vm.subjectName(a.code).takeIf { it.isNotEmpty() }?.let { Muted(it, size = 12) }
                        Text(a.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                done -> "Submitted"
                                n == null -> "No due date"
                                n < 0 -> "Overdue"
                                else -> daysUntilLabel(a.due.orEmpty())
                            },
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (done) P.green else if (soon || (n != null && n < 0)) P.red else P.ink,
                        )
                    }
                }
                Muted(listOfNotNull(a.due?.let { "Due ${dmy(it)}" }, a.marks?.let { "$it marks" }, "Posted ${dmy(a.posted)}").joinToString(" · "), size = 12)
                Text(a.details, fontSize = 14.sp, color = P.ink2)
                if (a.topics.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.topics.forEach { Tag(it) }
                }
                Text("📤 ${a.submit}", fontSize = 13.sp, color = P.ink2)
                PubLine(a.publishedBy, a.authorRole, a.publishedAt, if (a.official) "Course instructor (PDF provided by the student)" else "Zenkai (sample)")
                Actions {
                    a.pdf?.let { PdfButton(vm, it, "Open assignment PDF") }
                    SmallButton(if (done) "Mark as not submitted" else "✓ Mark as submitted", { vm.toggleAssignment(a.id) })
                    AiButton("How do I start?", {
                        vm.pendingQuestion = "How should I start \"${a.title}\" (${a.code})? Which syllabus topics does it need and what should I revise first?"
                        nav.go("ask")
                    })
                    // built-in assignments from the data files: any admin; published ones follow the post rule
                    if (vm.isAdmin && (builtin || canDelete(vm, a.dbId, a.authorRole, a.authorId))) {
                        SmallButton("✎ Edit", { editing = true })
                        DeleteAction(vm) { if (builtin) vm.api.deleteAssignment(a.id) else vm.api.deletePost(a.dbId!!) }
                    }
                }
            }
        }
    }
}
