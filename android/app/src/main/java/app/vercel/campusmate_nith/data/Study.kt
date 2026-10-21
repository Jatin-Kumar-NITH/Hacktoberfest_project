package app.vercel.campusmate_nith.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Pomodoro lengths and whether study mode switches on Do Not Disturb, remembered between sessions. */
@Serializable
data class StudyPrefs(
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
    val rounds: Int = 4,
    val dnd: Boolean = true,
)

/** What a study session is for: typed by hand, or a subject / session from the study planner. */
data class StudyTarget(val subject: String, val task: String = "", val planKey: String = "", val rounds: Int? = null)

/** The running study session. Kept on the phone so the timer survives the app closing or the phone restarting. */
@Serializable
data class StudyState(
    val subject: String,
    val task: String = "",
    /** The planner session this is for ([planKey]), or "" for a free session. */
    val planKey: String = "",
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
    val rounds: Int = 4,
    /** "focus", "short" or "long". */
    val phase: String = "focus",
    /** Pomodoros finished so far. */
    val done: Int = 0,
    /** Length of the current phase, in millis. */
    val phaseMs: Long = 0,
    /** When the current phase ends (epoch millis), while running. */
    val endsAt: Long = 0,
    /** Millis left in the phase while paused, or -1 while running. */
    val pausedLeft: Long = -1,
    /** Focus time finished so far, in millis (whole and skipped pomodoros). */
    val focusMs: Long = 0,
    val startedAt: Long = 0,
    /** Study mode switched Do Not Disturb on, so it switches it off again at the end. */
    val dndByUs: Boolean = false,
) {
    val paused get() = pausedLeft >= 0
    fun left(now: Long): Long = if (paused) pausedLeft else (endsAt - now).coerceAtLeast(0)
    val focusing get() = phase == "focus"
    /** The pomodoro being worked on, or the last one finished during a break (1-based). */
    val round get() = if (focusing) done + 1 else done
}

/** One finished (or stopped) study session. */
@Serializable
data class StudyLog(
    val at: Long,
    val date: String,
    val subject: String,
    val task: String = "",
    val planKey: String = "",
    val minutes: Int,
    val pomodoros: Int,
)

/** Identifies one session of the saved study plan, so study time can be recorded against it. */
fun planKey(date: String, s: PlanSession) = "$date|${s.time}|${s.subject}"

/** Plan sessions you can study for (not breaks or the exam itself). */
fun studyable(s: PlanSession) = s.type !in setOf("break", "exam")

private val CLOCK = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*([ap])?\.?\s*m?\.?""", RegexOption.IGNORE_CASE)

/** Length in minutes of a plan time like "6:00-7:30 PM", "6 PM - 7:30 PM" or "18:00-19:30", or null. */
fun sessionMinutes(time: String): Int? {
    val parts = time.split(Regex("""\s*(?:-|–|—|\bto\b)\s*""", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    fun read(s: String): Triple<Int, Int, Char?>? {
        val m = CLOCK.find(s) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (h > 23 || min > 59) return null
        return Triple(h, min, m.groupValues[3].lowercase().firstOrNull())
    }
    val a = read(parts[0]) ?: return null
    val b = read(parts[1]) ?: return null
    fun mins(t: Triple<Int, Int, Char?>, meridiem: Char?): Int {
        var h = t.first
        when (meridiem) {
            'p' -> if (h < 12) h += 12
            'a' -> if (h == 12) h = 0
        }
        return h * 60 + t.second
    }
    val end = mins(b, b.third)
    // "6:00-7:30 PM": the start shares the end's am/pm unless that would put it after the end ("11:00-1:00 PM")
    var start = mins(a, a.third ?: b.third)
    if (a.third == null && b.third != null && start > end) start = mins(a, null)
    var d = end - start
    if (d <= 0) d += if (a.third == null && b.third == null && d > -12 * 60) 12 * 60 else 24 * 60
    return d.takeIf { it in 5..12 * 60 }
}

/** How many pomodoros fit a plan session of [minutes] (at least one). */
fun pomodorosFor(minutes: Int?, p: StudyPrefs): Int? =
    minutes?.let { (it.toDouble() / (p.focusMin + p.shortMin)).roundToInt().coerceIn(1, 8) }
