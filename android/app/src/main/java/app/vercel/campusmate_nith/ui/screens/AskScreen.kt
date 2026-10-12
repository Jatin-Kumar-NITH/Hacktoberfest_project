@file:OptIn(ExperimentalLayoutApi::class)

package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.AskTurn
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.AskEntry
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.Tag
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val SUGGESTIONS = listOf(
    "What classes do I have tomorrow?",
    "When is my next exam?",
    "Which assignments are due this week?",
    "What's for dinner today?",
    "When is the next holiday?",
    "Can I skip a class and stay above 75%?",
)

@Composable
fun AskScreen(vm: AppViewModel, nav: Nav) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    fun ask(q0: String) {
        val q = q0.trim()
        if (q.isEmpty() || busy) return
        input = ""
        vm.askLog.add(AskEntry("user", q))
        scope.launch {
            busy = true
            list.animateScrollToItem(vm.askLog.size + 1)
            try {
                val body = buildJsonObject {
                    put("question", JsonPrimitive(q)); put("year", JsonPrimitive(vm.year)); put("section", JsonPrimitive(vm.section))
                    put("group", JsonPrimitive(vm.group)); put("client", vm.clientContext())
                    put("history", JsonArray(vm.askHistory.map { buildJsonObject { put("q", JsonPrimitive(it.q)); put("a", JsonPrimitive(it.a)) } }))
                }
                val r = vm.api.ask(body)
                if (r.error != null) throw Exception(r.error)
                vm.askLog.add(AskEntry("bot", r.answer, r.points, r.sources, r.found, "Gemma 4 · ${r.model} · ${r.seconds ?: ""}s"))
                vm.askHistory = (vm.askHistory + AskTurn(q, (listOf(r.answer) + r.points).joinToString(" "))).takeLast(4)
            } catch (e: Exception) {
                vm.askLog.add(AskEntry("err", e.message ?: "Something went wrong."))
            } finally {
                busy = false
                list.animateScrollToItem(vm.askLog.size + 1)
            }
        }
    }

    LaunchedEffect(vm.pendingQuestion) {
        vm.pendingQuestion?.let { vm.pendingQuestion = null; ask(it) }
    }

    Screen("Ask CampusMate", vm, onBack = { nav.back() }, state = list, bottomBar = {
        Row(
            Modifier.fillMaxWidth().background(P.card).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input, onValueChange = { if (it.length <= 500) input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about classes, exams, notices…") }, shape = RoundedCornerShape(24.dp), maxLines = 4,
            )
            IconButton(onClick = { ask(input) }, enabled = input.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Filled.Send, "Ask") }
        }
    }) {
        item { Muted("Answers come only from your campus data: timetable, notices, assignments, exams, syllabus, mess menu and your own saved plan and attendance. ${vm.myLabel}") }
        if (vm.askLog.isEmpty()) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { s -> AssistChip(onClick = { ask(s) }, label = { Text(s, fontSize = 13.sp) }) }
            }
        }
        itemsIndexed(vm.askLog) { _, m -> Bubble(m) }
        if (busy) item { Loading("CampusMate is checking your timetable, notices and hostel info…") }
    }
}

@Composable
private fun Bubble(m: AskEntry) {
    val mine = m.kind == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp).background(
                when { mine -> P.accent; m.kind == "err" -> P.redSoft; !m.found -> P.orangeSoft; else -> P.card },
                RoundedCornerShape(18.dp),
            ).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(m.text, color = when { mine -> androidx.compose.ui.graphics.Color.White; m.kind == "err" -> P.red; else -> P.ink }, fontSize = 15.sp)
            m.points.forEach { Text("• $it", fontSize = 14.sp, color = P.ink2) }
            if (m.sources.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                m.sources.forEach { Tag(it) }
            }
            if (m.meta.isNotEmpty()) Muted(m.meta, size = 11)
        }
    }
}
