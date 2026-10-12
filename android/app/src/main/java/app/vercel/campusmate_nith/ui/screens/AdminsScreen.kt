package app.vercel.campusmate_nith.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.item
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
import app.vercel.campusmate_nith.data.AccountRow
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.ui.AiButton
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.RoleTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.launch

@Composable
fun AdminsScreen(vm: AppViewModel, nav: Nav) {
    val users = remember { mutableStateListOf<AccountRow>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    suspend fun load() {
        try {
            val u = vm.api.users(); users.clear(); users.addAll(u); error = null
        } catch (e: ApiException) {
            error = e.message
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Screen("Manage admins", vm, onBack = { nav.back() }) {
        if (vm.user?.isOwner != true) { item { Alert("Only the owner can manage admins.", AlertKind.Warn) }; return@Screen }
        val admins = users.count { it.role != "user" }
        item { Muted("${users.size} accounts · $admins owner/admin · ${users.size - admins} students") }
        item { Field(q, { q = it }, "Search by name or email") }
        if (loading) item { Loading("Loading accounts…") }
        error?.let { item { Alert(it, AlertKind.Error) } }
        items(users.filter { "${it.name} ${it.email}".contains(q, ignoreCase = true) }, key = { it.id }) { u ->
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontWeight = FontWeight.SemiBold)
                        Muted(u.email, size = 13)
                    }
                    RoleTag(u.role)
                }
                Muted("Joined ${fmtTime(u.created_at)} · last login ${u.last_login_at?.let { fmtTime(it) } ?: "—"}", size = 12)
                if (u.chat_banned != 0) Text("🚫 Banned from chat", color = P.red)
                if (u.role == "owner") Muted("You", size = 13)
                else {
                    val make = u.role == "user"
                    val act: () -> Unit = {
                        scope.launch {
                            try { vm.api.setRole(u.id, if (make) "admin" else "user"); load() }
                            catch (e: ApiException) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
                        }
                    }
                    if (make) AiButton("Make admin", act) else SmallButton("Remove admin", act, danger = true)
                }
            }
        }
    }
}
