package app.vercel.campusmate_nith.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vercel.campusmate_nith.data.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One input in an edit form, like the website's itemEditor fields. kind: text, textarea, date or number. */
data class EditField(val name: String, val label: String, val value: String = "", val kind: String = "text", val max: Int = 500)

/**
 * The website's inline "✎ Edit" form as a dialog. Sends the fields (and a PDF if one was picked)
 * through [onSave]; closes and refreshes the campus data when it works.
 */
@Composable
fun ItemEditorDialog(
    vm: AppViewModel,
    title: String,
    fields: List<EditField>,
    pdfLabel: String?,
    onDismiss: () -> Unit,
    onSave: suspend (List<Pair<String, String>>, Pair<String, ByteArray>?) -> Unit,
) {
    val values = remember { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.name, it.value) } } }
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pdf = uri
        pdfName = uri?.let { u ->
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        } ?: "document.pdf"
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.forEach { f ->
                    when (f.kind) {
                        "date" -> DateField(f.label, values[f.name].orEmpty(), { values[f.name] = it }, allowClear = true)
                        "textarea" -> Field(values[f.name].orEmpty(), { values[f.name] = it }, f.label, minLines = 4, maxLength = f.max)
                        "number" -> Field(values[f.name].orEmpty(), { values[f.name] = it.filter(Char::isDigit) }, f.label, maxLength = 4, number = true)
                        else -> Field(values[f.name].orEmpty(), { values[f.name] = it }, f.label, maxLength = f.max)
                    }
                }
                if (pdfLabel != null) {
                    SmallButton(if (pdf == null) pdfLabel else "📄 $pdfName ✕", {
                        if (pdf == null) picker.launch(arrayOf("application/pdf")) else { pdf = null; pdfName = "" }
                    })
                }
                error?.let { Alert(it, AlertKind.Error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true; error = null
                    try {
                        val file = pdf?.let { u ->
                            withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }?.let { pdfName to it }
                        }
                        onSave(fields.map { it.name to values[it.name].orEmpty().trim() }, file)
                        Toast.makeText(ctx, "Saved.", Toast.LENGTH_SHORT).show()
                        vm.refresh()
                        onDismiss()
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Saving…" else "Save changes") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Two-tap delete that calls [action], then refreshes. */
@Composable
fun DeleteAction(vm: AppViewModel, label: String = "Delete", action: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    ConfirmDelete(label) {
        scope.launch {
            try {
                action(); vm.refresh()
            } catch (e: ApiException) {
                Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
