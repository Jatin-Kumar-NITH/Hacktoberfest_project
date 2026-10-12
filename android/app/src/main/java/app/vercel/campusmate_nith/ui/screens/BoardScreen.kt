package app.vercel.campusmate_nith.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.BoardItem
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.data.boardItems
import app.vercel.campusmate_nith.data.dmy
import app.vercel.campusmate_nith.data.forStudent
import app.vercel.campusmate_nith.data.isSectionSpecific
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.AiButton
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.ConfirmDelete
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PdfButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.Tag
import kotlinx.coroutines.launch

@Composable
fun BoardScreen(vm: AppViewModel, nav: Nav) {
    val mine = boardItems(vm.campus).filter { forStudent(it, vm.year, vm.section) }
    val all = mine.filter { !it.isSectionSpecific() }
    val sec = mine.filter { it.isSectionSpecific() }
    Screen("Notice board", vm) {
        if (vm.campus.notices.note.isNotEmpty()) item { Alert("📋 ${vm.campus.notices.note}", AlertKind.Warn) }
        item { SectionTitle("For everyone in ${YEAR_NAMES[vm.year]} (${all.size})") }
        if (all.isEmpty()) item { CmCard { EmptyLine("No notices yet.") } }
        items(all, key = { "a" + it.id }) { NoticeCard(vm, nav, it) }
        item { SectionTitle("Only for Section ${vm.section} (${sec.size})") }
        if (sec.isEmpty()) item { CmCard { EmptyLine("No notices only for your section right now.") } }
        items(sec, key = { "s" + it.id }) { NoticeCard(vm, nav, it) }
    }
}

/** Admins can delete posts; only the owner can delete the owner's posts. */
fun canDelete(vm: AppViewModel, dbId: Int?, authorRole: String?) =
    vm.isAdmin && dbId != null && (vm.user?.isOwner == true || authorRole != "owner")

@Composable
fun DeletePostButton(vm: AppViewModel, dbId: Int) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    ConfirmDelete {
        scope.launch {
            try {
                vm.api.deletePost(dbId); vm.refresh()
            } catch (e: ApiException) {
                Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeCard(vm: AppViewModel, nav: Nav, n: BoardItem) {
    var open by remember { mutableStateOf(false) }
    CmCard(border = if (n.publishedAt != null) P.gold.copy(alpha = 0.35f) else null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(dmy(n.date), size = 12)
            Muted(n.from, size = 12)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (n.kind == "announcement") Tag("Announcement", P.accent, P.accentSoft)
        }
        Text(n.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(if ("all" in n.yearList) "All years" else n.yearList.joinToString(", ") { YEAR_NAMES[it.toIntOrNull()] ?: it })
            if (n.isSectionSpecific()) n.sectionList.forEach { s -> if (s == vm.section) Tag("Sec $s", P.accent, P.accentSoft) else Tag("Sec $s") }
        }
        val flat = n.text.replace(Regex("\\s+"), " ")
        Text(if (open || flat.length <= 170) n.text else flat.take(170) + "…", fontSize = 14.sp, color = P.ink2)
        PubLine(n.from, n.authorRole, n.publishedAt, n.from)
        Actions {
            n.pdf?.let { PdfButton(vm, it) }
            if (flat.length > 170) SmallButton(if (open) "Show less" else "Read full notice", { open = !open })
            AiButton("Explain with AI", { vm.pendingExplain = n.id to n.text; nav.go("explain") })
            if (canDelete(vm, n.dbId, n.authorRole)) DeletePostButton(vm, n.dbId!!)
        }
    }
}
