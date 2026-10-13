package app.vercel.campusmate_nith.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.Person
import app.vercel.campusmate_nith.ui.AiButton
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.RoleTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.launch

/** Admins choose which students can post College and Hostel updates, like the website's Journalists page. */
@Composable
fun JournalistsScreen(vm: AppViewModel, nav: Nav) {
    val people = remember { mutableStateListOf<Person>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<Int?>(null) }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    suspend fun load() {
        try {
            val p = vm.api.people(); people.clear(); people.addAll(p); error = null
        } catch (e: ApiException) {
            error = e.message
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Screen("Journalists", vm, onBack = { nav.back() }) {
        if (!vm.isAdmin) { item { Alert("Only admins can choose journalists.", AlertKind.Warn) }; return@Screen }
        val count = people.count { it.role == "journalist" }
        item { Muted("Journalists can post College and Hostel updates. $count journalist${if (count == 1) "" else "s"} right now.") }
        item { Field(q, { q = it }, "Search by name or email") }
        if (loading) item { Loading("Loading people…") }
        error?.let { item { Alert(it, AlertKind.Error) } }
        // journalists first, then students; staff can already post so they are left out
        val shown = people.filter { it.role in setOf("journalist", "user") && "${it.name} ${it.email}".contains(q, ignoreCase = true) }
            .sortedBy { if (it.role == "journalist") 0 else 1 }
        items(shown, key = { it.id }) { u ->
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontWeight = FontWeight.SemiBold)
                        Muted(u.email, size = 13)
                    }
                    RoleTag(u.role)
                }
                val on = u.role != "journalist"
                val toggle = {
                    scope.launch {
                        busy = u.id
                        try { vm.api.setJournalist(u.id, on); load() }
                        catch (e: ApiException) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
                        finally { busy = null }
                    }
                    Unit
                }
                if (on) AiButton("Make journalist", toggle, enabled = busy != u.id)
                else SmallButton("Remove journalist", toggle, danger = true, enabled = busy != u.id)
            }
        }
    }
}
