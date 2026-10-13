package app.vercel.campusmate_nith.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.NewsItem
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.ConfirmDelete
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.RoleTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import kotlinx.coroutines.launch

private val NEWS_YEAR_LABEL = listOf("all" to "All years", "1" to "1st Year", "2" to "2nd Year", "3" to "3rd Year", "4" to "4th Year")
private val NEWS_HOSTEL_LABEL = listOf("both" to "Boys & girls", "boys" to "Boys' hostel", "girls" to "Girls' hostel")

/** College updates and Hostel updates, like the website's two pages. [section] is "college" or "hostel". */
@Composable
fun UpdatesScreen(vm: AppViewModel, nav: Nav, section: String) {
    val hostelPage = section == "hostel"
    val items = remember { mutableStateListOf<NewsItem>() }
    var canPost by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // the hostel page's filters, remembered like the website's cm_hn_year / cm_hn_hostel
    var year by remember { mutableStateOf(vm.store.newsYear.ifEmpty { vm.year.toString() }) }
    var hostel by remember { mutableStateOf(vm.store.newsHostel.ifEmpty { vm.hostel }) }
    var editing by remember { mutableStateOf<NewsItem?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(year, hostel, reload) {
        try {
            val r = vm.api.news(section, year, hostel)
            items.clear(); items.addAll(r.updates); canPost = r.can_post; error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "Could not load updates."
        } finally {
            loading = false
        }
    }

    Screen(if (hostelPage) "Hostel updates" else "College updates", vm, onBack = { nav.back() }) {
        item {
            Muted(
                if (hostelPage) "News from the hostels: water, electricity, rules, events and more."
                else "News from around the college, posted by the campus journalists."
            )
        }
        if (hostelPage) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChipRow(NEWS_YEAR_LABEL.drop(1), year) { year = it; vm.store.newsYear = it }
                    ChipRow(NEWS_HOSTEL_LABEL.drop(1), hostel) { hostel = it; vm.store.newsHostel = it }
                }
            }
        }
        if (canPost) item { NewsForm(vm, section, year, hostel) { reload++ } }
        error?.let { item { Alert(it, AlertKind.Error) } }
        if (loading) item { Loading("Loading updates…") }
        else if (items.isEmpty() && error == null) {
            item { EmptyLine(if (hostelPage) "No hostel updates for this year and hostel yet." else "No college updates yet.") }
        }
        items(items, key = { it.id }) { u ->
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted(fmtTime(u.created_at), size = 12, modifier = Modifier.weight(1f))
                    if (hostelPage) {
                        Tag(NEWS_YEAR_LABEL.toMap()[u.year] ?: u.year)
                        Tag(NEWS_HOSTEL_LABEL.toMap()[u.hostel] ?: u.hostel)
                    }
                }
                if (u.title.isNotEmpty()) Text(u.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(u.body, color = P.ink2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted("Posted by", size = 12)
                    Text(u.author_name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    RoleTag(u.author_role)
                }
                u.updated_at?.let { Muted("Edited ${fmtTime(it)}", size = 12) }
                if (u.can_edit) {
                    Actions {
                        SmallButton("✎ Edit", { editing = u })
                        ConfirmDelete("🗑 Delete") {
                            scope.launch {
                                try { vm.api.deleteNews(u.id); reload++ }
                                catch (e: ApiException) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { u -> NewsEditor(vm, hostelPage, u, onDismiss = { editing = null }) { editing = null; reload++ } }
}

/** "Post an update", shown to journalists and staff. */
@Composable
private fun NewsForm(vm: AppViewModel, section: String, pickedYear: String, pickedHostel: String, onPosted: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var year by remember(pickedYear) { mutableStateOf(pickedYear) }
    var hostel by remember(pickedHostel) { mutableStateOf(pickedHostel) }
    var posting by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    CmCard {
        SectionTitle("Post an update")
        if (section == "hostel") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("For", NEWS_YEAR_LABEL, year, { year = it })
                Dropdown("Hostel", NEWS_HOSTEL_LABEL, hostel, { hostel = it })
            }
        }
        Field(title, { title = it }, "Headline (optional)", maxLength = 120)
        Field(body, { body = it }, "What's the update?", minLines = 4, maxLength = 3000)
        msg?.let { Muted(it, size = 13) }
        PrimaryButton("Post update", {
            scope.launch {
                posting = true; msg = null
                try {
                    vm.api.postNews(section, title.trim(), body.trim(), year, hostel)
                    title = ""; body = ""; msg = "Posted."
                    onPosted()
                } catch (e: ApiException) {
                    msg = e.message ?: "Could not post."
                } finally {
                    posting = false
                }
            }
        }, enabled = body.isNotBlank(), loading = posting)
    }
}

@Composable
private fun NewsEditor(vm: AppViewModel, hostelPage: Boolean, u: NewsItem, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by remember { mutableStateOf(u.title) }
    var body by remember { mutableStateOf(u.body) }
    var year by remember { mutableStateOf(u.year) }
    var hostel by remember { mutableStateOf(u.hostel) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit update") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hostelPage) {
                    Dropdown("For", NEWS_YEAR_LABEL, year, { year = it })
                    Dropdown("Hostel", NEWS_HOSTEL_LABEL, hostel, { hostel = it })
                }
                Field(title, { title = it }, "Headline (optional)", maxLength = 120)
                Field(body, { body = it }, "Update", minLines = 4, maxLength = 3000)
                error?.let { Alert(it, AlertKind.Error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && body.isNotBlank(), onClick = {
                scope.launch {
                    saving = true; error = null
                    try { vm.api.editNews(u.id, title.trim(), body.trim(), year, hostel); onSaved() }
                    catch (e: ApiException) { error = e.message ?: "Could not save." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
