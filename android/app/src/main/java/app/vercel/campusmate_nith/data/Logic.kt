package app.vercel.campusmate_nith.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

// Same rules as static/js/app.js, so the app and the website always agree.

val WEEK6 = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
val WEEK7 = WEEK6 + "Sunday"
val MEALS = listOf("Breakfast", "Lunch", "Snacks", "Dinner")
val YEAR_NAMES = mapOf(1 to "1st Year", 2 to "2nd Year", 3 to "3rd Year", 4 to "4th Year")
val ROLE_LABEL = mapOf("owner" to "Owner", "coowner" to "Co-owner", "admin" to "Admin", "journalist" to "Journalist", "user" to "Student")
val CAL_TYPES = linkedMapOf("holiday" to "Holiday", "exam" to "Exam", "event" to "Event", "semester" to "Semester")
val SECTION_IDS = ('A'..'J').map { it.toString() } + "Z"

fun today(): LocalDate = LocalDate.now()
fun todayIso(): String = today().toString()
fun parseDate(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
fun dayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
fun todayName(): String = dayName(today())

fun daysUntil(iso: String): Long = parseDate(iso)?.let { ChronoUnit.DAYS.between(today(), it) } ?: 0

fun daysUntilLabel(iso: String): String {
    val n = daysUntil(iso)
    return when {
        n < 0 -> "past"
        n == 0L -> "today"
        n == 1L -> "tomorrow"
        else -> "in $n days"
    }
}

/** "12 Oct" */
fun dmy(iso: String): String = parseDate(iso)?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) ?: iso

/** "Mon, 12 Oct" */
fun niceDate(iso: String): String = parseDate(iso)?.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)) ?: iso

fun rangeLabel(e: CalEvent) = if (e.end == e.date || e.end.isEmpty()) niceDate(e.date) else "${niceDate(e.date)} – ${niceDate(e.end)}"
fun spanDays(e: CalEvent): Long {
    val start = parseDate(e.date) ?: return 1
    val end = parseDate(e.end) ?: start
    return ChronoUnit.DAYS.between(start, end) + 1
}

/** Server timestamps are UTC ISO strings; show them in the phone's time zone. */
fun fmtTime(iso: String?): String {
    if (iso.isNullOrEmpty()) return ""
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }.getOrDefault(iso)
}

fun localDate(iso: String): String = runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.getOrDefault(iso.take(10))

fun hh(h: Int): String = "${if (h > 12) h - 12 else h}:00 ${if (h >= 12) "PM" else "AM"}"
fun span(c: ClassItem) = "${hh(c.start)} – ${hh(c.end)}"

fun forStudent(item: Targeted, year: Int, section: String): Boolean {
    val ys = item.yearList
    val ss = item.sectionList
    return ("all" in ys || year.toString() in ys) && ("all" in ss || section in ss)
}

fun forStudent(row: ExamRow, section: String) = "all" in row.sections || section in row.sections

fun Targeted.isSectionSpecific() = "all" !in sectionList

fun dayClasses(sec: Section, day: String, group: String): List<ClassItem> =
    sec.classes.filter { it.day == day && (group.isEmpty() || it.group == null || it.group == group) }.sortedBy { it.start }

/** Only the entries for students of [year] (plus the all-years ones), like the website's year-wise calendar. */
fun AcademicCalendar.forYear(year: Int) = copy(events = events.filter { it.forYear(year) })

fun eventsOn(cal: AcademicCalendar, iso: String) = cal.events.filter { it.date <= iso && iso <= it.end.ifEmpty { it.date } }
fun holidayOn(cal: AcademicCalendar, iso: String) = cal.events.firstOrNull { it.type == "holiday" && it.date <= iso && iso <= it.end.ifEmpty { it.date } }

fun whenLabel(e: CalEvent): String {
    val t = todayIso()
    val end = e.end.ifEmpty { e.date }
    return when {
        e.date <= t && t <= end -> "on now"
        end < t -> "past"
        else -> daysUntilLabel(e.date)
    }
}

fun pdfPath(p: String) = if (p.startsWith("/") || p.startsWith("http")) p else "/static/$p"

/** Board item: a notice from data/notices.json or a published post. */
data class BoardItem(
    val id: String,
    val dbId: Int?,
    val kind: String,
    val date: String,
    val title: String,
    val from: String,
    override val yearList: List<String>,
    override val sectionList: List<String>,
    val text: String,
    val pdf: String?,
    val publishedAt: String?,
    val authorRole: String?,
    val authorId: Int? = null,
) : Targeted

/** Assignment from data/assignments.json or an admin's post. */
data class AssignmentItem(
    val id: String,
    val dbId: Int?,
    val code: String,
    val title: String,
    val posted: String,
    val due: String?,
    val marks: Int?,
    val official: Boolean,
    val details: String,
    val topics: List<String>,
    val submit: String,
    val pdf: String?,
    val publishedAt: String?,
    val publishedBy: String?,
    val authorRole: String?,
    override val yearList: List<String>,
    override val sectionList: List<String>,
    val authorId: Int? = null,
) : Targeted

fun boardItems(c: Campus): List<BoardItem> {
    val fromFile = c.notices.notices.map {
        BoardItem(it.id, null, "notice", it.date, it.title, it.from, it.yearList, it.sectionList, it.text, it.pdf, null, null)
    }
    val fromPosts = c.posts.filter { it.kind == "notice" || it.kind == "announcement" }.map {
        BoardItem(it.id, it.db_id, it.kind, localDate(it.published_at), it.title, it.published_by, it.yearList, it.sectionList,
            it.body, it.pdf, it.published_at, it.author_role, it.author_id)
    }
    return (fromFile + fromPosts).sortedByDescending { it.publishedAt ?: it.date }
}

fun assignmentItems(c: Campus): List<AssignmentItem> {
    val fromFile = c.assignments.assignments.map {
        AssignmentItem(it.id, null, it.code, it.title, it.posted, it.due, it.marks, it.official, it.details, it.topics, it.submit,
            it.pdf, null, null, null, it.yearList, it.sectionList)
    }
    val fromPosts = c.posts.filter { it.kind == "assignment" }.map {
        AssignmentItem(it.id, it.db_id, it.code ?: "General", it.title, localDate(it.published_at), it.due, it.marks, true,
            it.body.ifEmpty { "See the attached PDF." }, emptyList(), "Follow the instructions in the assignment.", it.pdf,
            it.published_at, it.published_by, it.author_role, it.yearList, it.sectionList, it.author_id)
    }
    return fromFile + fromPosts
}

// ---------------------------------------------------------------- attendance (same maths as the website)

data class AttStats(val code: String, val attended: Int, val total: Int, val pct: Double?, val advice: String, val ok: Boolean)

fun attStats(a: Attendance, code: String, cal: AcademicCalendar? = null): AttStats {
    val b = a.base[code] ?: AttBase()
    var attended = b.attended
    var total = b.total
    a.records.forEach { r ->
        if (r.code != code || r.status == "cancelled") return@forEach
        // a holiday has no regular classes, even if they were marked before the holiday was added
        if (cal != null && !r.slot.startsWith("x") && holidayOn(cal, r.date) != null) return@forEach
        total++
        if (r.status == "present") attended++
    }
    val t = a.target / 100.0
    val pct = if (total > 0) attended * 100.0 / total else null
    val advice = when {
        total == 0 -> "No classes marked yet."
        pct!! >= a.target -> {
            val k = floor(attended / t - total + 1e-9).toInt()
            if (k > 0) "You can miss $k more class${if (k == 1) "" else "es"} and stay at ${a.target}%."
            else "You're right at the line. Don't miss the next class."
        }
        t >= 1 -> "You can't reach 100% any more."
        else -> {
            val need = ceil((t * total - attended) / (1 - t) - 1e-9).toInt()
            "Attend the next $need class${if (need == 1) "" else "es"} in a row to reach ${a.target}%."
        }
    }
    return AttStats(code, attended, total, pct, advice, pct == null || pct >= a.target)
}

fun fmtPct(p: Double?): String = when {
    p == null -> "–"
    p == floor(p) -> "${p.toInt()}%"
    else -> String.format(Locale.ENGLISH, "%.1f%%", p)
}

fun Double.clean(): String = if (this == floor(this)) toInt().toString() else toString()

fun isSunday(d: LocalDate) = d.dayOfWeek == DayOfWeek.SUNDAY

val MONTH_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
