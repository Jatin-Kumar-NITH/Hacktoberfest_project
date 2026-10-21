package app.vercel.campusmate_nith.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.NoticeEntry
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.PriorityTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

@Composable
fun ExplainScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sampleId by remember { mutableStateOf("") }
    var hindi by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var shownId by remember { mutableStateOf<String?>(null) }
    var liveLine by remember { mutableStateOf("") }
    var cachedNote by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { image = it }

    fun explain() {
        if (text.isBlank() && image == null) { error = "Please paste a notice or pick a photo first."; return }
        scope.launch {
            loading = true; error = null
            try {
                val bytes = image?.let { uri -> withContext(Dispatchers.IO) { jpegBytes(ctx, uri) } }
                val r = vm.api.explainNotice(text.trim(), hindi, bytes, sampleId)
                val res = r.result ?: throw ApiException(r.error ?: "Something went wrong.")
                val entry = NoticeEntry(System.currentTimeMillis().toString(36), Instant.now().toString(), res, r.model, res.checklist.map { false })
                vm.addHistory(entry)
                shownId = entry.id
                cachedNote = if (r.cached) "Live AI unavailable right now (${r.notice}) Showing a saved result from an earlier live Gemma run on ${r.cached_at}." else null
                liveLine = if (r.cached) "Saved result · ${r.model}" else "Live result · ${r.model} · ${r.seconds ?: ""}s"
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                loading = false
            }
        }
    }

    // opened from the board ("Explain with AI") or from history on the home screen
    LaunchedEffect(vm.pendingExplain, vm.openNotice) {
        vm.pendingExplain?.let { (id, t) ->
            vm.pendingExplain = null
            text = t; sampleId = id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.lowercase().take(40); image = null
            explain()
        }
        vm.openNotice?.let { shownId = it; vm.openNotice = null; liveLine = ""; cachedNote = null }
    }

    val entry = vm.history.firstOrNull { it.id == shownId }
    Screen("Notice explainer", vm, onBack = { nav.back() }) {
        item { Muted("Paste a notice or upload a photo. Zenkai pulls out deadlines, documents and a step-by-step checklist, using only what the notice says.") }
        if (vm.samples.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Muted("Try a sample notice", size = 13)
                ChipRow(vm.samples.map { it.id to it.title.ifEmpty { it.id } }, sampleId) { id ->
                    vm.samples.firstOrNull { it.id == id }?.let { text = it.text; sampleId = it.id; image = null }
                }
            }
        }
        item {
            CmCard {
                Field(text, { text = it; sampleId = "" }, "Notice text", minLines = 5, maxLength = 15000)
                Muted("${text.length} characters", size = 12)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallButton(if (image == null) "📷 Upload a photo" else "Photo added ✕", {
                        if (image == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else image = null
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = hindi, onCheckedChange = { hindi = it })
                    Text("Also explain in Hindi (हिंदी)", fontSize = 14.sp)
                }
                PrimaryButton("✦ Explain this notice", { explain() }, loading = loading)
            }
        }
        if (loading) item { Loading("Gemma is reading the notice…") }
        error?.let { item { Alert(it, AlertKind.Error) } }
        if (entry != null && !loading) noticeResult(vm, entry, cachedNote, liveLine.ifEmpty { "From your history · ${entry.model} · ${fmtTime(entry.at)}" })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.noticeResult(vm: AppViewModel, entry: NoticeEntry, cachedNote: String?, foot: String) {
    val r = entry.result
    cachedNote?.let { item { Alert(it, AlertKind.Warn) } }
    item {
        CmCard {
            val (fg, bg) = urgencyColors(r.urgency, app.vercel.campusmate_nith.ui.P)
            Tag("${r.urgency.uppercase()} PRIORITY", fg, bg)
            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Muted(listOfNotNull(r.issued_by?.let { "From: $it" }, r.audience?.let { "For: $it" }).joinToString("  ·  ").ifEmpty { "Issuer and audience not stated" }, size = 13)
            Text(r.summary, fontSize = 15.sp)
            if (r.urgency_reason.isNotEmpty()) Muted(r.urgency_reason, size = 13)
        }
    }
    r.hindi?.let { h ->
        item {
            CmCard {
                SectionTitle("हिंदी में")
                Text(h.summary, fontSize = 15.sp)
                h.checklist.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 14.sp) }
            }
        }
    }
    if (r.deadlines.isNotEmpty()) item {
        CmCard {
            SectionTitle("Deadlines")
            r.deadlines.forEach { d ->
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.date ?: "Date unclear", fontWeight = FontWeight.SemiBold, color = P.red)
                        if (d.certain == false) Tag("verify", P.orange, P.orangeSoft)
                    }
                    Text(d.what.orEmpty(), fontSize = 14.sp)
                    d.source_text?.let { Muted("“$it”", size = 12) }
                }
            }
        }
    }
    if (r.checklist.isNotEmpty()) item {
        CmCard {
            SectionTitle("Checklist") { Muted("${entry.done.count { it }}/${entry.done.size} done", size = 13) }
            r.checklist.forEachIndexed { i, c ->
                val done = entry.done.getOrElse(i) { false }
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = done, onCheckedChange = { vm.setDone(entry.id, i, it) })
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.step, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false),
                                textDecoration = if (done) TextDecoration.LineThrough else null, color = if (done) P.muted else P.ink)
                            PriorityTag(c.priority)
                        }
                        val why = listOfNotNull(c.why, c.deadline?.let { "By $it" }).joinToString(" · ")
                        if (why.isNotEmpty()) Muted(why, size = 12)
                    }
                }
            }
        }
    }
    if (r.required_documents.isNotEmpty()) item {
        CmCard {
            SectionTitle("Documents to bring")
            r.required_documents.forEach { Text("• $it", fontSize = 14.sp) }
        }
    }
    if (r.key_facts.isNotEmpty()) item {
        CmCard {
            SectionTitle("Key facts")
            r.key_facts.forEach { f ->
                Column {
                    Muted(f.label, size = 12)
                    Text(f.value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    f.source_text?.let { Text("“$it”", fontSize = 12.sp, color = P.muted, fontStyle = FontStyle.Italic) }
                }
            }
        }
    }
    if (r.missing_or_unclear.isNotEmpty()) item {
        CmCard {
            SectionTitle("Missing or unclear")
            r.missing_or_unclear.forEach { Text("• $it", fontSize = 14.sp, color = P.orange) }
        }
    }
    item { Muted(foot, size = 12) }
}

/** Photo → JPEG, shrunk like the server does, so uploads stay small on mobile data. */
private fun jpegBytes(ctx: android.content.Context, uri: Uri): ByteArray {
    val src = ImageDecoder.createSource(ctx.contentResolver, uri)
    val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
        val s = info.size
        val scale = minOf(1f, 1600f / maxOf(s.width, s.height))
        dec.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, out); out.toByteArray() }
}
