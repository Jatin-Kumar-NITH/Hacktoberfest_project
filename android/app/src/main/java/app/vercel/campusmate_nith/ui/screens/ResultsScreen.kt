package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.ResultSemester
import app.vercel.campusmate_nith.data.ResultStudent
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Loading
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun gpa(v: Double?) = if (v == null) "–" else "%.2f".format(v)
private fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** Semester results by roll number, from nithresults.xyz through the Zenkai server. */
@Composable
fun ResultsScreen(vm: AppViewModel, nav: Nav) {
    var query by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(vm.store.recentRolls) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stale by remember { mutableStateOf<String?>(null) }
    var empty by remember { mutableStateOf<String?>(null) }
    val students = remember { mutableStateListOf<ResultStudent>() }
    val scope = rememberCoroutineScope()

    fun lookup(raw: String) {
        val q = raw.filterNot { it.isWhitespace() }.uppercase()
        error = null; stale = null; empty = null
        if (!Regex("^[A-Z0-9]{5,15}$").matches(q)) { error = "Enter a full roll number, like 25BCS050."; return }
        query = q
        scope.launch {
            loading = true; students.clear()
            try {
                val r = vm.api.results(q)
                if (r.students.isEmpty()) {
                    empty = "No results found for $q. Check the roll number; results of a new batch show up once they're published."
                } else {
                    recent = (listOf(q) + recent.filter { it != q }).take(6)
                    vm.store.recentRolls = recent
                    if (r.stale) {
                        val at = Instant.ofEpochMilli((r.fetched_at * 1000).toLong()).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                        stale = "The results site isn't responding, so this is the copy saved at $at."
                    }
                    students.addAll(r.students)
                }
            } catch (e: ApiException) {
                error = e.message
            } catch (e: kotlinx.serialization.SerializationException) {
                error = "Could not read the results. Please try again."
            } finally {
                loading = false
            }
        }
    }

    Screen("Results", vm, onBack = { nav.back() }) {
        item { Muted("Look up semester results, SGPA and CGPA by roll number. Results come from nithresults.xyz.") }
        item {
            CmCard {
                Field(query, { query = it }, "Roll number", maxLength = 20)
                PrimaryButton(if (loading) "Searching…" else "Search", { lookup(query) }, enabled = query.isNotBlank(), loading = loading)
                if (recent.isNotEmpty()) {
                    Muted("Recent", size = 12)
                    ChipRow(recent.map { it to it }, query) { lookup(it) }
                }
            }
        }
        error?.let { item { Alert(it, AlertKind.Error) } }
        stale?.let { item { Alert(it, AlertKind.Warn) } }
        empty?.let { item { CmCard { Muted(it) } } }
        if (loading) item { Loading("Looking up $query…") }
        students.forEach { st -> resultItems(st) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(st: ResultStudent) {
    item {
        CmCard {
            Text(st.name.ifEmpty { st.roll }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            val batch = (st.year as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() && it != "null" }
            Muted(listOfNotNull(st.roll, st.father.takeIf { it.isNotEmpty() }?.let { "Father: $it" }, batch?.let { "Batch $it" }).joinToString(" · "), size = 13)
        }
    }
    item {
        val latest = st.semesters.lastOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(gpa(st.cgpa), "CGPA", Modifier.weight(1f))
            Stat(gpa(latest?.sgpa), latest?.let { "SGPA (${it.name})" } ?: "SGPA", Modifier.weight(1f))
            Stat(num(st.credits), "Credits over ${st.semesters.size} semester${if (st.semesters.size == 1) "" else "s"}", Modifier.weight(1f))
        }
    }
    if (st.semesters.size > 1) item { SgpaBars(st.semesters) }
    st.semesters.reversed().forEachIndexed { i, s ->
        item { SemesterCard(s, startOpen = i == 0) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    CmCard(modifier) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Muted(label, size = 12)
    }
}

@Composable
private fun SgpaBars(sems: List<ResultSemester>) {
    CmCard {
        SectionTitle("SGPA by semester")
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            sems.forEach { s ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    // bar height is SGPA out of 10; the empty part sits above it
                    val frac = ((s.sgpa ?: 0.0) / 10.0).coerceIn(0.04, 0.99).toFloat()
                    Spacer(Modifier.weight(1f - frac))
                    Text(gpa(s.sgpa), fontSize = 11.sp, color = P.ink2)
                    Box(Modifier.fillMaxWidth().weight(frac).background(P.accent, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                    Text("S${s.number}", fontSize = 11.sp, color = P.muted)
                }
            }
        }
    }
}

@Composable
private fun SemesterCard(s: ResultSemester, startOpen: Boolean) {
    var open by remember { mutableStateOf(startOpen) }
    CmCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold)
                Muted("SGPA ${gpa(s.sgpa)} · CGPA ${gpa(s.cgpa)} · ${num(s.credits)} credits", size = 13)
            }
            Text(if (open) "▲" else "▼", color = P.muted, fontSize = 12.sp)
        }
        if (open) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    listOf("Code" to 80, "Subject" to 220, "Credits" to 56, "Grade" to 52, "Points" to 52).forEach { (h, w) ->
                        Text(h, Modifier.width(w.dp), fontSize = 12.sp, color = P.muted, fontWeight = FontWeight.SemiBold)
                    }
                }
                s.subjects.forEach { sub ->
                    HorizontalDivider(Modifier.width(460.dp), color = P.line)
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sub.code, Modifier.width(80.dp), fontSize = 12.sp, color = P.ink2)
                        Text(sub.subject, Modifier.width(220.dp), fontSize = 13.sp)
                        Text(num(sub.credit), Modifier.width(56.dp), fontSize = 13.sp)
                        Text(sub.grade.ifEmpty { "–" }, Modifier.width(52.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = gradeColor(sub.grade))
                        Text(sub.points?.let { num(it) } ?: "–", Modifier.width(52.dp), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun gradeColor(g: String) = when (g.uppercase()) {
    "AA", "A", "O", "A+" -> P.green
    "AB", "BB", "B", "B+" -> P.accent
    "BC", "CC", "C" -> P.orange
    "F", "FF", "I", "X" -> P.red
    else -> P.ink
}
