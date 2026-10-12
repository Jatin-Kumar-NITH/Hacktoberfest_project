package app.vercel.campusmate_nith.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.vercel.campusmate_nith.data.Ban
import app.vercel.campusmate_nith.data.ChatMessage
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.RoleTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: AppViewModel, nav: Nav) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var latest by remember { mutableIntStateOf(0) }
    var banned by remember { mutableStateOf<Ban?>(null) }
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("Messages are visible to all logged-in users. Admins can remove messages.") }
    var sending by remember { mutableStateOf(false) }
    val bans = remember { mutableStateListOf<Ban>() }
    var showBans by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val me = vm.user

    suspend fun poll() {
        try {
            val r = vm.api.chat(latest)
            banned = r.banned
            val atBottom = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: true
            val fresh = r.messages.filter { m -> messages.none { it.id == m.id } }
            messages.addAll(fresh)
            fresh.maxOfOrNull { it.id }?.let { latest = maxOf(latest, it) }
            if (fresh.isNotEmpty() && (atBottom || latest == r.latest)) list.scrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        } catch (_: ApiException) { /* offline: try again next tick */ }
    }

    suspend fun loadBans() {
        if (!vm.isAdmin) return
        runCatching { vm.api.bans() }.getOrNull()?.let { bans.clear(); bans.addAll(it) }
    }

    LaunchedEffect(Unit) {
        loadBans()
        while (true) { poll(); delay(3000) }
    }

    fun send() {
        val body = input.trim()
        if (body.isEmpty() || sending) return
        scope.launch {
            sending = true
            try {
                vm.api.sendChat(body)
                input = ""; hint = "Messages are visible to all logged-in users. Admins can remove messages."
                poll()
                list.scrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
            } catch (e: ApiException) {
                e.banned?.let { banned = it }
                hint = e.message ?: "Couldn't send."
            } finally {
                sending = false
            }
        }
    }

    Screen("Campus chat", vm, state = list, actions = {
        if (vm.isAdmin) SmallButton("Bans (${bans.size})", { showBans = !showBans })
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().background(P.card).imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Muted(hint, size = 11)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { if (it.length <= 500) input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Message everyone…") }, shape = RoundedCornerShape(24.dp), maxLines = 4, enabled = banned == null,
                )
                IconButton(onClick = { send() }, enabled = input.isNotBlank() && banned == null && !sending) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }) {
        banned?.let { b ->
            item { Alert("🚫 You are banned from the campus chat${if (b.permanent) "" else " until " + fmtTime(b.until)}${b.by?.let { " (by $it)" } ?: ""}. You can still read messages.", AlertKind.Error) }
        }
        if (showBans) item {
            CmCard {
                SectionTitle("Banned from chat")
                if (bans.isEmpty()) EmptyLine("Nobody is banned.")
                bans.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(b.name, fontWeight = FontWeight.SemiBold); RoleTag(b.role) }
                            Muted("${if (b.permanent) "Permanent" else "Until " + fmtTime(b.until)} · banned by ${b.by ?: "admin"}", size = 12)
                        }
                        SmallButton("Unban", { scope.launch { runCatching { vm.api.unban(b.id) }; loadBans() } })
                    }
                }
            }
        }
        if (messages.isEmpty()) item { CmCard { EmptyLine("No messages yet. Say hello! 👋") } }
        items(messages, key = { it.id }) { m ->
            ChatBubble(vm, m, mine = m.user_id == me?.id,
                onDelete = { scope.launch { runCatching { vm.api.deleteChat(m.id) }; messages.remove(m) } },
                onBan = { hours ->
                    scope.launch {
                        try { vm.api.ban(m.user_id, hours); Toast.makeText(ctx, "${m.name} is banned from chat.", Toast.LENGTH_SHORT).show(); loadBans() }
                        catch (e: ApiException) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
                    }
                })
        }
    }
}

@Composable
private fun ChatBubble(vm: AppViewModel, m: ChatMessage, mine: Boolean, onDelete: () -> Unit, onBan: (Int) -> Unit) {
    val me = vm.user
    val canBan = vm.isAdmin && !mine && m.role != "owner" && (m.role != "admin" || me?.isOwner == true)
    var banPick by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(24) }
    val staff = m.role != "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(if (staff) P.goldSoft else if (mine) P.accentSoft else P.card, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (staff) P.gold else P.ink)
                RoleTag(m.role)
            }
            Text(m.body, fontSize = 15.sp)
            Muted(fmtTime(m.created_at), size = 11)
            if (vm.isAdmin) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Remove", onDelete, danger = true)
                if (canBan) SmallButton("Ban", { banPick = !banPick }, danger = true)
            }
            if (banPick) {
                Dropdown("Ban for", listOf(1 to "1 hour", 24 to "1 day", 168 to "7 days", 0 to "Permanently"), hours, { hours = it })
                SmallButton("Ban ${m.name}", { banPick = false; onBan(hours) }, danger = true)
            }
        }
    }
}
