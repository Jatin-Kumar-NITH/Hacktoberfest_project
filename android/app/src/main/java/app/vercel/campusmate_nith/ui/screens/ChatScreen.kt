package app.vercel.campusmate_nith.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.vercel.campusmate_nith.data.Channel
import app.vercel.campusmate_nith.data.ChannelInfo
import app.vercel.campusmate_nith.data.ChatMessage
import app.vercel.campusmate_nith.data.Person
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.data.outranks
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.ConfirmDelete
import app.vercel.campusmate_nith.ui.Dropdown
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.RoleTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PUBLIC_HINT = "Messages are visible to all logged-in users. Admins can remove messages."
private const val PRIVATE_HINT = "🔒 Private channel: only its members can see these messages."

@Composable
fun ChatScreen(vm: AppViewModel, nav: Nav) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var latest by remember { mutableIntStateOf(0) }
    var banned by remember { mutableStateOf<Ban?>(null) }
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(PUBLIC_HINT) }
    var sending by remember { mutableStateOf(false) }
    val bans = remember { mutableStateListOf<Ban>() }
    var showBans by remember { mutableStateOf(false) }
    // null = the public campus chat, else a private channel
    var channel by remember { mutableStateOf<Int?>(null) }
    val channels = remember { mutableStateListOf<Channel>() }
    var canCreate by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<ChannelInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val me = vm.user

    suspend fun loadChannels() {
        runCatching { vm.api.channels() }.getOrNull()?.let { r ->
            channels.clear(); channels.addAll(r.channels); canCreate = r.can_create
            if (channel != null && r.channels.none { it.id == channel }) channel = null
        }
    }

    suspend fun loadInfo() {
        val id = channel ?: run { info = null; return }
        info = try { vm.api.channel(id) } catch (e: ApiException) { if (e.status == 404) { channel = null }; null }
    }

    suspend fun poll() {
        val ch = channel
        try {
            val r = vm.api.chat(latest, ch)
            if (ch != channel) return   // switched while loading
            banned = r.banned
            // drop messages someone deleted (older ones outside the window the server sent stay)
            r.ids?.let { ids ->
                if (ids.isEmpty()) messages.clear()
                else { val keep = ids.toSet(); val oldest = ids.min(); messages.removeAll { it.id >= oldest && it.id !in keep } }
            }
            val atBottom = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: true
            val fresh = r.messages.filter { m -> m.id > latest && messages.none { it.id == m.id } }
            messages.addAll(fresh)
            fresh.maxOfOrNull { it.id }?.let { latest = maxOf(latest, it) }
            vm.store.setChatSeen(ch?.toString() ?: "campus", r.latest)   // read here, so don't notify it later
            if (fresh.isNotEmpty() && (atBottom || latest == r.latest)) list.scrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        } catch (e: ApiException) {
            if (e.status == 404 && ch != null) { channel = null; loadChannels() }   // removed from the channel
        }
    }

    suspend fun loadBans() {
        if (!vm.isAdmin) return
        runCatching { vm.api.bans() }.getOrNull()?.let { bans.clear(); bans.addAll(it) }
    }

    LaunchedEffect(Unit) { loadBans(); loadChannels() }
    // restarts whenever the channel changes
    // while a chat is on screen (and the app is in front) its messages don't also arrive as notifications
    val app = LocalContext.current.applicationContext as app.vercel.campusmate_nith.CampusMateApplication
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(channel, owner) {
        val key = channel?.toString() ?: "campus"
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) app.openChat = key
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) app.openChat = null
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); app.openChat = null }
    }
    LaunchedEffect(channel) {
        messages.clear(); latest = 0
        hint = if (channel == null) PUBLIC_HINT else PRIVATE_HINT
        loadInfo()
        var ticks = 0
        while (true) {
            poll(); delay(3000)
            if (++ticks % 10 == 0) loadChannels()   // channels you were added to show up
        }
    }

    fun send() {
        val body = input.trim()
        if (body.isEmpty() || sending) return
        scope.launch {
            sending = true
            try {
                vm.api.sendChat(body, channel)
                input = ""; hint = if (channel == null) PUBLIC_HINT else PRIVATE_HINT
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

    if (creating) CreateChannelDialog(vm, onDismiss = { creating = false }) { id ->
        creating = false
        scope.launch { loadChannels(); channel = id }
    }

    val current = channels.firstOrNull { it.id == channel }
    Screen(current?.name?.let { "🔒 $it" } ?: "Campus chat", vm, state = list, actions = {
        if (vm.isAdmin && channel == null) SmallButton("Bans (${bans.size})", { showBans = !showBans })
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().background(P.card).imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Muted(hint, size = 11)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { if (it.length <= 500) input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text(current?.let { "Message ${it.name}…" } ?: "Message everyone…") },
                    shape = RoundedCornerShape(24.dp), maxLines = 4, enabled = banned == null,
                )
                IconButton(onClick = { send() }, enabled = input.isNotBlank() && banned == null && !sending) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }) {
        if (channels.isNotEmpty() || canCreate) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = channel == null, onClick = { channel = null }, label = { Text("🌐 Campus chat") })
                channels.forEach { c -> FilterChip(selected = channel == c.id, onClick = { channel = c.id }, label = { Text("🔒 ${c.name}") }) }
                if (canCreate) FilterChip(selected = false, onClick = { creating = true }, label = { Text("＋ New private channel") })
            }
        }
        info?.let { i -> if (channel != null) item { ChannelCard(vm, i, onChanged = { scope.launch { loadInfo() } }, onGone = { scope.launch { channel = null; loadChannels() } }) } }
        banned?.let { b ->
            item { Alert("🚫 You are banned from the campus chat${if (b.permanent) "" else " until " + fmtTime(b.until)}${b.by?.let { " (by $it)" } ?: ""}. You can still read messages.", AlertKind.Error) }
        }
        if (showBans && channel == null) item {
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
            val ch = channel
            ChatBubble(vm, m, mine = m.user_id == me?.id,
                onDelete = {
                    scope.launch {
                        try { vm.api.deleteChat(m.id, ch); messages.remove(m) }
                        catch (e: ApiException) {
                            if (e.status == 404) messages.remove(m) else Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onBan = { hours ->
                    scope.launch {
                        try { vm.api.ban(m.user_id, hours); Toast.makeText(ctx, "${m.name} is banned from chat.", Toast.LENGTH_SHORT).show(); loadBans() }
                        catch (e: ApiException) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
                    }
                })
        }
    }
}

/** Same rules as can_delete_message on the server. */
private fun canDeleteMsg(vm: AppViewModel, m: ChatMessage): Boolean {
    val me = vm.user ?: return false
    if (m.user_id == me.id || me.role == "owner") return true
    return when (me.role) {
        "coowner" -> m.role != "owner"
        "admin" -> m.role !in setOf("owner", "coowner")
        else -> false
    }
}

@Composable
private fun ChatBubble(vm: AppViewModel, m: ChatMessage, mine: Boolean, onDelete: () -> Unit, onBan: (Int) -> Unit) {
    val me = vm.user
    val canBan = vm.isAdmin && !mine && m.role != "owner" && (m.role != "coowner" || me?.isTrueOwner == true) &&
        (m.role != "admin" || me?.isOwner == true)
    val canDel = canDeleteMsg(vm, m)
    var banPick by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(24) }
    var confirm by remember { mutableStateOf(false) }
    val staff = m.role !in setOf("user", "journalist")
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
            if (canDel || canBan) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canDel) SmallButton(if (confirm) "Tap again to delete" else "Delete", { if (confirm) { confirm = false; onDelete() } else confirm = true }, danger = true)
                if (canBan) SmallButton("Ban", { banPick = !banPick }, danger = true)
            }
            if (banPick) {
                Dropdown("Ban for", listOf(1 to "1 hour", 24 to "1 day", 168 to "7 days", 0 to "Permanently"), hours, { hours = it })
                SmallButton("Ban ${m.name}", { banPick = false; onBan(hours) }, danger = true)
            }
        }
    }
}

/** Members of a private channel, with the admin tools to manage it and a way to leave. */
@Composable
private fun ChannelCard(vm: AppViewModel, info: ChannelInfo, onChanged: () -> Unit, onGone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val me = vm.user
    var open by remember { mutableStateOf(false) }
    val people = remember { mutableStateListOf<Person>() }
    var pick by remember { mutableStateOf<Int?>(null) }
    val id = info.channel.id
    LaunchedEffect(open, info.can_manage) {
        if (open && info.can_manage && people.isEmpty()) runCatching { vm.api.people() }.getOrNull()?.let { people.addAll(it) }
    }
    fun act(block: suspend () -> Unit, after: () -> Unit) {
        scope.launch {
            try { block(); after() } catch (e: ApiException) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
        }
    }
    CmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.channel.name, fontWeight = FontWeight.SemiBold)
                Muted("${info.members.size} member${if (info.members.size == 1) "" else "s"}", size = 12)
            }
            SmallButton(if (open) "Hide members" else "Members", { open = !open })
        }
        if (open) {
            info.members.forEach { m ->
                HorizontalDivider(color = P.line)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (m.id == me?.id) "${m.name} (you)" else m.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            RoleTag(m.role)
                        }
                        Muted(m.email, size = 12)
                    }
                    val canRemove = info.can_manage && m.id != me?.id &&
                        (m.role !in setOf("owner", "coowner") || outranks(me?.role.orEmpty(), m.role))
                    if (canRemove) SmallButton("Remove", { act({ vm.api.removeMember(id, m.id) }, onChanged) }, danger = true)
                }
            }
            if (info.can_manage) {
                val inside = info.members.map { it.id }.toSet()
                val outside = people.filter { it.id !in inside }
                if (outside.isNotEmpty()) {
                    val sel = pick?.takeIf { p -> outside.any { it.id == p } } ?: outside.first().id
                    Dropdown("Add someone", outside.map { it.id to "${it.name} (${it.email})" }, sel, { pick = it })
                    SmallButton("Add member", { act({ vm.api.addMembers(id, listOf(sel)) }, onChanged) })
                }
                ConfirmDelete("Delete channel") { act({ vm.api.deleteChannel(id) }, onGone) }
            }
            SmallButton("Leave channel", { act({ vm.api.removeMember(id, me?.id ?: 0) }, onGone) })
        }
    }
}

/** Admins name a private channel and tick who is in it. */
@Composable
private fun CreateChannelDialog(vm: AppViewModel, onDismiss: () -> Unit, onCreated: (Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    val people = remember { mutableStateListOf<Person>() }
    val chosen = remember { mutableStateListOf<Int>() }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { people.addAll(vm.api.people().filter { it.id != vm.user?.id }) } catch (e: ApiException) { error = e.message }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New private channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Channel name", maxLength = 60)
                Field(filter, { filter = it }, "Find people")
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    val shown = people.filter { "${it.name} ${it.email}".contains(filter, ignoreCase = true) }
                    if (shown.isEmpty()) item { Muted(if (people.isEmpty()) "Nobody else has an account yet." else "Nobody matches.", size = 13) }
                    items(shown, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (p.id in chosen) chosen.remove(p.id) else chosen.add(p.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = p.id in chosen, onCheckedChange = { if (it) chosen.add(p.id) else chosen.remove(p.id) })
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(p.name, fontSize = 14.sp); RoleTag(p.role) }
                                Muted(p.email, size = 12)
                            }
                        }
                    }
                }
                error?.let { Alert(it, AlertKind.Error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true; error = null
                    try { onCreated(vm.api.createChannel(name.trim(), chosen.toList())) }
                    catch (e: ApiException) { error = e.message }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Creating…" else "Create channel") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
