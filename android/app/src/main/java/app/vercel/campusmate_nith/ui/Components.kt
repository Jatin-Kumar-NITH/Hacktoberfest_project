@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vercel.campusmate_nith.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.MONTH_SHORT
import app.vercel.campusmate_nith.data.ROLE_LABEL
import app.vercel.campusmate_nith.data.daysUntil
import app.vercel.campusmate_nith.data.parseDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A screen: top bar with title and back button (when [onBack] is set), pull to refresh, scrolling list. */
@Composable
fun Screen(
    title: String,
    vm: AppViewModel,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = P.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = P.bg, scrolledContainerColor = P.bg),
            )
        },
        bottomBar = bottomBar,
    ) { pad ->
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.padding(pad)) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                vm.loadError?.let { err ->
                    val saved = if (vm.lastSync > 0) " from ${savedAt(vm.lastSync)}" else ""
                    item { Alert("Showing data saved$saved. $err", AlertKind.Warn) }
                }
                content()
            }
        }
    }
}

@Composable
fun CmCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    var m = modifier.fillMaxWidth().background(P.card, shape)
    if (border != null) m = m.border(1.5.dp, border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14) =
    Text(text, color = P.muted, fontSize = size.sp, modifier = modifier)

@Composable
fun EmptyLine(text: String) = Text(text, color = P.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

enum class AlertKind { Ok, Warn, Error, Info }

@Composable
fun Alert(text: String, kind: AlertKind = AlertKind.Info) {
    val (fg, bg) = when (kind) {
        AlertKind.Ok -> P.green to P.greenSoft
        AlertKind.Warn -> P.orange to P.orangeSoft
        AlertKind.Error -> P.red to P.redSoft
        AlertKind.Info -> P.accent to P.accentSoft
    }
    Text(text, color = fg, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp))
}

@Composable
fun Tag(text: String, fg: Color = P.muted, bg: Color = P.fill) {
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun CodeTag(code: String) = Tag(code, P.accent, P.accentSoft)

@Composable
fun RoleTag(role: String) {
    when (role) {
        "user" -> Tag(ROLE_LABEL[role] ?: "Student")
        "journalist" -> Tag(ROLE_LABEL[role] ?: "Journalist", P.accent, P.accentSoft)
        "owner", "coowner" -> Tag(ROLE_LABEL[role] ?: "Owner", P.onGold, P.gold)
        else -> Tag(ROLE_LABEL[role] ?: "Admin", P.gold, P.goldSoft)
    }
}

@Composable
fun PriorityTag(p: String) = when (p) {
    "high" -> Tag("high", P.red, P.redSoft)
    "low" -> Tag("low", P.green, P.greenSoft)
    else -> Tag("medium", P.orange, P.orangeSoft)
}

/** The little calendar square: day number over month. */
@Composable
fun DateBadge(iso: String, soon: Boolean = false, label: String? = null) {
    val d = parseDate(iso)
    Column(
        Modifier.size(46.dp).background(if (soon) P.redSoft else P.fill, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(label ?: d?.dayOfMonth?.toString() ?: "–", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (soon) P.red else P.ink)
        if (d != null && label == null) Text(MONTH_SHORT[d.monthValue - 1], fontSize = 10.sp, color = if (soon) P.red else P.muted)
    }
}

@Composable
fun ListRow(badge: @Composable () -> Unit, title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        badge()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) Muted(subtitle, size = 13)
        }
        trailing?.invoke()
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, danger: Boolean = false, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) P.red else P.ink),
        modifier = Modifier.height(34.dp),
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
fun AiButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.accentSoft, contentColor = P.accent),
        modifier = Modifier.height(34.dp),
    ) { Text("✦ $text", fontSize = 13.sp) }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(48.dp), shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.ink, contentColor = P.bg),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = P.bg) else Text(text)
    }
}

@Composable
fun PdfButton(vm: AppViewModel, path: String, label: String = "PDF") {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = { vm.openPdf(ctx, path) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp), enabled = !vm.downloading,
    ) {
        Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun Actions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Chip row for picking one option. */
@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(
                selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = P.ink, selectedLabelColor = P.bg),
            )
        }
    }
}

/** A labelled drop-down. */
@Composable
fun <T> Dropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Column(Modifier.weight(1f, fill = false)) {
                if (label.isNotEmpty()) Text(label, fontSize = 11.sp, color = P.muted)
                Text(options.firstOrNull { it.first == selected }?.second ?: "Choose", color = P.ink, fontSize = 14.sp, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = P.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false }) }
        }
    }
}

/** A button that opens a date picker. [value] and the result are ISO dates ("" = none). */
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(12.dp), modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(16.dp), tint = P.muted)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, fontSize = 11.sp, color = P.muted)
            Text(if (value.isEmpty()) "Pick a date" else app.vercel.campusmate_nith.data.niceDate(value), color = P.ink, fontSize = 14.sp)
        }
    }
    if (open) {
        val initial = parseDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (allowClear) TextButton(onClick = { onChange(""); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), minLines: Int = 1, maxLength: Int = 500, number: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= maxLength) onChange(it) }, label = { Text(label) },
        modifier = modifier, minLines = minLines, shape = RoundedCornerShape(12.dp),
        keyboardOptions = if (number) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            else androidx.compose.foundation.text.KeyboardOptions.Default,
    )
}

@Composable
fun Loading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(8.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Muted(text)
    }
}

/** Two-tap delete, like the website's "Click again to delete". */
@Composable
fun ConfirmDelete(label: String = "Delete", onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    SmallButton(if (armed) "Tap again to delete" else label, { if (armed) { armed = false; onConfirm() } else armed = true }, danger = true)
}

@Composable
fun Dot(color: Color, size: Int = 8) = Box(Modifier.size(size.dp).background(color, CircleShape))

fun soonIso(iso: String?) = iso != null && daysUntil(iso) in 0..3

@Composable
fun CenterText(text: String) = Text(text, textAlign = TextAlign.Center, color = P.muted, modifier = Modifier.fillMaxWidth().padding(24.dp))

@Composable
fun todayLocal(): LocalDate = remember { LocalDate.now() }

/** "today 4:05 PM" or "12 Oct, 4:05 PM" for a saved-at time. */
fun savedAt(millis: Long): String {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val time = t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH))
    return if (t.toLocalDate() == java.time.LocalDate.now()) "today $time"
    else t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) + ", $time"
}

/** Banner shown when a newer version of the app is published. */
@Composable
fun UpdateBanner(vm: AppViewModel) {
    val v = vm.update ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    CmCard(border = P.accent.copy(alpha = 0.4f), onClick = {
        runCatching {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(vm.updateUrl())))
        }
    }) {
        Text("A new version of CampusMate is ready", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Muted("Version ${v.versionName}. Tap to download it, then open the file to install.", size = 13)
    }
}

/** Opens the phone's share sheet with some text. */
fun shareText(ctx: android.content.Context, subject: String, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Share").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
