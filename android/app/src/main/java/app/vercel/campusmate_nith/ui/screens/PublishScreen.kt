@file:OptIn(ExperimentalLayoutApi::class)

package app.vercel.campusmate_nith.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.SECTION_IDS
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.stringOrNull
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.DateField
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.PdfButton
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KINDS = listOf("notice" to "Notice", "announcement" to "Announcement", "assignment" to "Assignment", "material" to "Study material")

@Composable
fun PublishScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("notice") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf("") }
    val years = remember { mutableStateListOf<String>() }
    val sections = remember { mutableStateListOf<String>() }
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pdf = uri
        pdfName = uri?.let { u ->
            ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } ?: "document.pdf"
    }

    Screen("Publish", vm, onBack = { nav.back() }) {
        if (!vm.isAdmin) { item { Alert("Only admins can publish.", AlertKind.Warn) }; return@Screen }
        item {
            CmCard {
                SectionTitle("What are you publishing?")
                ChipRow(KINDS, kind) { kind = it }
                Field(title, { title = it }, "Title", maxLength = 150)
                Field(body, { body = it }, when (kind) {
                    "notice" -> "Notice text"; "announcement" -> "Announcement text"; "assignment" -> "Description / instructions"; else -> "Description (optional)"
                }, minLines = 4, maxLength = 8000)
                Field(code, { code = it }, "Subject code (optional), e.g. MA-111", maxLength = 40)
                if (kind == "assignment") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Due date", due, { due = it }, allowClear = true)
                    Field(marks, { marks = it.filter(Char::isDigit) }, "Marks", Modifier.weight(1f), number = true, maxLength = 4)
                }
                Muted("Who is it for? Leave empty for everyone.", size = 13)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { y ->
                        val v = y.toString()
                        FilterChip(selected = v in years, onClick = { if (v in years) years.remove(v) else years.add(v) }, label = { Text(YEAR_NAMES[y] ?: v) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SECTION_IDS.forEach { s ->
                        FilterChip(selected = s in sections, onClick = { if (s in sections) sections.remove(s) else sections.add(s) }, label = { Text(s) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (pdf == null) (if (kind == "material") "Attach PDF (required)" else "Attach PDF (optional)") else "📄 $pdfName ✕", {
                        if (pdf == null) picker.launch(arrayOf("application/pdf")) else pdf = null
                    })
                }
                error?.let { Alert(it, AlertKind.Error) }
                done?.let { Alert(it, AlertKind.Ok) }
                PrimaryButton("Publish", {
                    scope.launch {
                        busy = true; error = null; done = null
                        try {
                            val fields = mutableListOf("kind" to kind, "title" to title, "body" to body, "code" to code)
                            if (kind == "assignment") { fields += "due" to due; fields += "marks" to marks }
                            years.forEach { fields += "years" to it }
                            sections.forEach { fields += "sections" to it }
                            val file = pdf?.let { u -> withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }?.let { pdfName to it } }
                            vm.api.publish(fields, file)
                            done = "Published ✓"
                            title = ""; body = ""; code = ""; due = ""; marks = ""; pdf = null; years.clear(); sections.clear()
                            vm.refresh()
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }, loading = busy)
            }
        }
        val posts = vm.campus.posts
        item { SectionTitle("Published (${posts.size})") }
        if (posts.isEmpty()) item { CmCard { EmptyLine("Nothing published yet.") } }
        items(posts, key = { it.id }) { p ->
            CmCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag(p.kind) }
                Text(p.title, fontWeight = FontWeight.SemiBold)
                val ys = p.yearList; val ss = p.sectionList
                Muted("${if ("all" in ys) "All years" else "Year " + ys.joinToString(", ")} · ${if ("all" in ss) "all sections" else "Sec " + ss.joinToString(", ")}" +
                    (p.code?.let { " · $it" } ?: "") + (p.due?.let { " · due $it" } ?: ""), size = 13)
                PubLine(p.published_by, p.author_role, p.published_at, "")
                Actions {
                    p.pdf?.let { PdfButton(vm, it) }
                    if (canDelete(vm, p.db_id, p.author_role)) DeletePostButton(vm, p.db_id)
                }
            }
        }
    }
}
