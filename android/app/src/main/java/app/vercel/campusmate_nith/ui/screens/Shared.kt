package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.PlanSession
import app.vercel.campusmate_nith.data.ROLE_LABEL
import app.vercel.campusmate_nith.data.StudyLog
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.Palette

@Composable
fun attColor(pct: Double?, target: Int): Color = when {
    pct == null -> P.muted
    pct >= target -> P.green
    pct >= target - 10 -> P.orange
    else -> P.red
}

@Composable
fun UrgencyDot(u: String) {
    val (fg, bg) = urgencyColors(u, P)
    Text(u.take(1).uppercase(), color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).size(34.dp).padding(top = 7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

fun urgencyColors(u: String, p: Palette) = when (u) {
    "high" -> p.red to p.redSoft
    "low" -> p.green to p.greenSoft
    else -> p.orange to p.orangeSoft
}

@Composable
fun sessionColor(type: String): Color = when (type) {
    "revision" -> P.indigo
    "practice" -> P.green
    "break" -> P.muted
    "exam" -> P.red
    else -> P.accent
}

/** One planner session. [onStudy] adds a button that starts study mode on it; [done] is study time already logged for it. */
@Composable
fun SessionRow(s: PlanSession, done: List<StudyLog> = emptyList(), onStudy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(4.dp).size(width = 4.dp, height = 40.dp).background(sessionColor(s.type), RoundedCornerShape(2.dp))) {}
        Column(Modifier.weight(1f)) {
            Text(s.subject, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (s.task.isNotEmpty()) Text(s.task, fontSize = 13.sp, color = P.ink2)
            if (done.isNotEmpty()) {
                val min = done.sumOf { it.minutes }
                val pomos = done.sumOf { it.pomodoros }
                Text("✓ Studied $min min" + if (pomos > 0) " · $pomos 🍅" else "", fontSize = 12.sp, color = P.green, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Muted(s.time, size = 12)
            if (onStudy != null) Text("▶ Focus", color = P.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(P.accentSoft, RoundedCornerShape(999.dp)).clickable(onClick = onStudy).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** "Published by <name> [Admin] · 12 Oct 2026, 5:30 pm" */
@Composable
fun PubLine(by: String?, role: String?, at: String?, fallback: String) {
    if (at == null) {
        Muted("Published by $fallback · from the data files", size = 12)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Muted("Published by", size = 12)
        Text(by.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (role == "user") P.ink else P.gold)
        Text(ROLE_LABEL[role] ?: "Admin", fontSize = 11.sp, color = P.gold,
            modifier = Modifier.background(P.goldSoft, RoundedCornerShape(999.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
    }
    Muted(fmtTime(at), size = 12)
}
