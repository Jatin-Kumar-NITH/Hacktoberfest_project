@file:OptIn(ExperimentalLayoutApi::class)

package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vercel.campusmate_nith.data.Subject
import app.vercel.campusmate_nith.data.SyllabusUnit
import app.vercel.campusmate_nith.data.SyllabusYear
import app.vercel.campusmate_nith.data.clean
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.SmallButton

/** Topics picked for one planner subject. Topic keys are "unit|part|topic" indexes into the subject's outline. */
data class TopicPick(val code: String, val units: Set<Int>, val topics: Set<String>)

/** Syllabus subjects that have a unit/topic outline. */
fun outlined(syl: SyllabusYear) = syl.subjects.filter { it.value.outline.isNotEmpty() }

/** The syllabus subject a planner subject's name refers to: its code ("MA-111", "ma 111") or its full name. */
fun sylMatch(syl: SyllabusYear, text: String): String? {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return null
    Regex("""\b([a-z]{2,3})[\s-]?(\d{3})\b""").find(t)?.let { m ->
        val code = "${m.groupValues[1].uppercase()}-${m.groupValues[2]}"
        if (code in syl.subjects) return code
    }
    return outlined(syl).entries.firstOrNull { (_, s) -> s.name.length > 4 && (t.contains(s.name.lowercase()) || t == s.name.lowercase()) }?.key
}

private fun keysOf(u: SyllabusUnit) = u.parts.flatMapIndexed { pi, p -> p.topics.indices.map { "${u.n}|$pi|$it" } }

/**
 * "Unit 1 – Heading: topic, topic | Unit 3 – ..." for the planner, the same text as the website's picker.
 * Long picks are shortened (whole sub-units, then whole units) to fit the server's 2000 characters.
 */
fun pickedTopicsText(sub: Subject, pick: TopicPick): String {
    val units = sub.outline.filter { it.n in pick.units }
    fun build(level: Int) = units.mapNotNull { u ->
        val parts = u.parts.mapIndexed { pi, p -> p to p.topics.filterIndexed { ti, _ -> "${u.n}|$pi|$ti" in pick.topics } }.filter { it.second.isNotEmpty() }
        if (parts.isEmpty()) return@mapNotNull null
        val whole = parts.size == u.parts.size && parts.all { (p, sel) -> sel.size == p.topics.size }
        if (level >= 2 && whole) "${u.label} ${u.title} (whole unit)"
        else "${u.label} – " + parts.joinToString("; ") { (p, sel) ->
            if (level >= 1 && sel.size == p.topics.size) "${p.title} (all)" else "${p.title}: ${sel.joinToString(", ")}"
        }
    }.joinToString(" | ")
    for (level in 0..2) build(level).let { if (it.length <= 1900 || level == 2) return it.take(2000) }
    return ""
}

/**
 * The planner's "Choose topics" dialog: subject (only when [startCode] is null), then units, then sub-units and topics.
 * [onDone] gets the subject code and the pick.
 */
@Composable
fun TopicPickerDialog(vm: AppViewModel, startCode: String?, previous: TopicPick?, onDismiss: () -> Unit, onDone: (String, TopicPick) -> Unit) {
    val syl = vm.syllabus()
    var code by remember { mutableStateOf(startCode) }
    var withSubject by remember { mutableStateOf(startCode == null) }
    var step by remember { mutableStateOf(if (startCode == null) 0 else 1) }
    val units = remember { (previous?.units ?: emptySet()).toList().toMutableStateList() }
    val topics = remember { (previous?.topics ?: emptySet()).toList().toMutableStateList() }
    val list = rememberLazyListState()
    LaunchedEffect(step) { list.scrollToItem(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp)
                .background(P.card, RoundedCornerShape(20.dp)),
        ) {
            val sub = code?.let { syl.subjects[it] }
            val total = if (withSubject) 3 else 2
            val stepNo = if (withSubject) step + 1 else step
            // header
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("STEP $stepNo OF $total · ${listOf("SUBJECT", "UNITS", "TOPICS")[step]}", color = P.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (sub != null && step > 0) "$code · ${sub.name}" else "Choose a subject", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Muted(
                        when (step) {
                            0 -> "Which subject is this exam for? Subjects in your timetable come first."
                            1 -> "Pick the units this exam covers. You can narrow down the topics next."
                            else -> "Everything in your units is ticked. Untick what you have already finished or that isn't in this exam."
                        }, size = 13,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = P.muted) }
            }
            HorizontalDivider(color = P.line)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    step == 0 -> {
                        val mine = vm.mySection.classes.map { it.code }.toSet()
                        val subjects = outlined(syl).entries.sortedWith(compareBy({ it.key !in mine }, { it.key }))
                        items(subjects, key = { it.key }) { (c, s) ->
                            PickRow(selected = false, onClick = { code = c; units.clear(); topics.clear(); step = 1 }) {
                                PickText(c, s.name, if (c in mine) "In your timetable" else "Semester ${s.semester?.clean() ?: "?"}")
                            }
                        }
                    }
                    step == 1 && sub != null -> items(sub.outline, key = { it.n }) { u ->
                        val on = u.n in units
                        val toggle: () -> Unit = {
                            if (on) { units.remove(u.n); topics.removeAll { it.startsWith("${u.n}|") } }
                            else { units.add(u.n); keysOf(u).forEach { if (it !in topics) topics.add(it) } }
                        }
                        PickRow(selected = on, onClick = toggle) {
                            Checkbox(on, { toggle() }, colors = CheckboxDefaults.colors(checkedColor = P.accent))
                            val n = u.parts.sumOf { it.topics.size }
                            val hours = u.hours.removeSuffix("L").toIntOrNull()?.let { "$it lecture hours · " } ?: ""
                            PickText(u.label, u.title, "$hours$n topic${if (n == 1) "" else "s"}")
                        }
                    }
                    sub != null -> sub.outline.filter { it.n in units }.forEach { u ->
                        item(key = "u${u.n}") {
                            Text("${u.label} · ${u.title}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        u.parts.forEachIndexed { pi, p ->
                            item(key = "u${u.n}p$pi") {
                                val keys = p.topics.indices.map { "${u.n}|$pi|$it" }
                                val picked = keys.count { it in topics }
                                val state = when (picked) { keys.size -> ToggleableState.On; 0 -> ToggleableState.Off; else -> ToggleableState.Indeterminate }
                                val all: () -> Unit = { if (picked == keys.size) topics.removeAll(keys) else keys.forEach { if (it !in topics) topics.add(it) } }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth().clickable { all() }, verticalAlignment = Alignment.CenterVertically) {
                                        TriStateCheckbox(state, { all() }, colors = CheckboxDefaults.colors(checkedColor = P.accent))
                                        // a unit with a single sub-unit of the same name: the row is just "All topics"
                                        Text(if (u.parts.size == 1 && p.title == u.title) "All topics" else p.title,
                                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Muted("$picked/${keys.size}", size = 12)
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                        p.topics.forEachIndexed { ti, t ->
                                            val k = keys[ti]
                                            val tick: @Composable () -> Unit = { Text("✓", color = P.accent, fontSize = 13.sp) }
                                            FilterChip(
                                                selected = k in topics, onClick = { if (k in topics) topics.remove(k) else topics.add(k) },
                                                label = { Text(t, fontSize = 13.sp) },
                                                leadingIcon = if (k in topics) tick else null,
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = P.accentSoft, selectedLabelColor = P.accent),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = P.line)
            // footer
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    0 -> SmallButton("Cancel", onDismiss)
                    1 -> {
                        SmallButton(if (withSubject) "← Subject" else "Other subject", { withSubject = true; step = 0 })
                        SmallButton("Select all", {
                            sub?.outline?.forEach { u -> if (u.n !in units) { units.add(u.n); keysOf(u).forEach { if (it !in topics) topics.add(it) } } }
                        })
                    }
                    else -> SmallButton("← Units", { step = 1 })
                }
                Spacer(Modifier.weight(1f))
                if (step == 1) PrimaryButton("Next →", { step = 2 }, enabled = units.isNotEmpty(), modifier = Modifier)
                if (step == 2) PrimaryButton("Use these topics", {
                    val c = code ?: return@PrimaryButton
                    // a topic left over from a unit that was unticked doesn't count
                    val kept = topics.filter { it.substringBefore("|").toInt() in units }.toSet()
                    onDone(c, TopicPick(c, units.filter { n -> kept.any { it.startsWith("$n|") } }.toSet(), kept))
                }, enabled = topics.isNotEmpty(), modifier = Modifier)
            }
        }
    }
}

@Composable
private fun PickRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().background(if (selected) P.accentSoft else P.card, shape)
            .border(1.dp, if (selected) P.accent else P.line, shape).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun PickText(label: String, title: String, meta: String) {
    Column(Modifier.padding(start = 4.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = P.muted)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        Text(meta, fontSize = 12.sp, color = P.muted)
    }
}
