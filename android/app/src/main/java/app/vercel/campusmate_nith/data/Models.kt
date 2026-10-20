package app.vercel.campusmate_nith.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Shapes of the JSON the Flask server sends (see load_campus() in app.py).
// Every field has a default so a missing key never crashes the app.

@Serializable
data class User(val id: Int = 0, val name: String = "", val email: String = "", val role: String = "user") {
    /** Admin, co-owner or owner: publishes, edits campus data, moderates chat, makes channels. */
    val isAdmin get() = role in setOf("admin", "coowner", "owner")
    /** Owner or co-owner: manages admins. */
    val isOwner get() = role == "owner" || role == "coowner"
    /** Only the real owner can give or take co-owner. */
    val isTrueOwner get() = role == "owner"
    /** Journalists and staff can post College and Hostel Updates. */
    val canPostNews get() = role in setOf("journalist", "admin", "coowner", "owner")
}

/** Same ranks as accounts.py. */
val RANK = mapOf("owner" to 4, "coowner" to 3, "admin" to 2, "journalist" to 1, "user" to 0)

/** Whether someone with [actor] role may act against someone with [target] role. */
fun outranks(actor: String, target: String) = actor == "owner" || (RANK[actor] ?: 0) > (RANK[target] ?: 0)

@Serializable
data class CampusResponse(
    val campus: Campus = Campus(),
    val samples: List<SampleNotice> = emptyList(),
    val me: User? = null,
    val models: List<String> = emptyList(),
)

@Serializable
data class SampleNotice(val id: String = "", val title: String = "", val text: String = "")

@Serializable
data class Campus(
    val timetables: Map<String, Timetable> = emptyMap(),
    val notices: NoticeFile = NoticeFile(),
    val assignments: AssignmentFile = AssignmentFile(),
    val exams: ExamFile = ExamFile(),
    val syllabus: SyllabusFile = SyllabusFile(),
    val hostel: HostelFile = HostelFile(),
    val calendar: AcademicCalendar = AcademicCalendar(),
    val posts: List<Post> = emptyList(),
)

@Serializable
data class Timetable(
    val year: Int = 1,
    val title: String = "",
    val source: String = "",
    val official: Boolean = false,
    val note: String = "",
    val classrooms: Map<String, String> = emptyMap(),
    val sections: List<Section> = emptyList(),
    val edited_by: String? = null,
    val edited_at: String? = null,
)

@Serializable
data class Section(
    val id: String = "",
    val name: String = "",
    val classroom: String = "",
    val branches: String = "",
    val groups: List<Group> = emptyList(),
    val classes: List<ClassItem> = emptyList(),
)

@Serializable
data class Group(val id: String = "", val rolls: String = "")

@Serializable
data class ClassItem(
    val day: String = "",
    val start: Int = 9,
    val end: Int = 10,
    val code: String = "",
    val label: String = "",
    val faculty: String = "",
    val group: String? = null,
    val type: String = "Lecture",
    val room: String = "",
)

/** Something shown to some years and sections only (notices, assignments, posts, exam rows). */
interface Targeted {
    val yearList: List<String>
    val sectionList: List<String>
}

private fun List<JsonElement>.asStrings() = map { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }

@Serializable
data class NoticeFile(val note: String = "", val notices: List<Notice> = emptyList())

@Serializable
data class Notice(
    val id: String = "",
    val date: String = "",
    val title: String = "",
    val from: String = "",
    val years: List<JsonElement>? = null,
    val year: Int? = null,
    val sections: List<String>? = null,
    val text: String = "",
    val pdf: String? = null,
) : Targeted {
    override val yearList get() = years?.asStrings() ?: year?.let { listOf(it.toString()) } ?: listOf("all")
    override val sectionList get() = sections ?: listOf("all")
}

@Serializable
data class AssignmentFile(val note: String = "", val assignments: List<Assignment> = emptyList())

@Serializable
data class Assignment(
    val id: String = "",
    val year: Int? = null,
    val years: List<JsonElement>? = null,
    val sections: List<String>? = null,
    val code: String = "",
    val title: String = "",
    val posted: String = "",
    val due: String? = null,
    val marks: Int? = null,
    val official: Boolean = false,
    val details: String = "",
    val topics: List<String> = emptyList(),
    val submit: String = "",
    val pdf: String? = null,
) : Targeted {
    override val yearList get() = years?.asStrings() ?: year?.let { listOf(it.toString()) } ?: listOf("all")
    override val sectionList get() = sections ?: listOf("all")
}

@Serializable
data class ExamFile(
    val note: String = "",
    val official: Boolean = false,
    val years: Map<String, ExamYear> = emptyMap(),
    val rules: List<String> = emptyList(),
)

@Serializable
data class ExamYear(
    val name: String = "",
    val notes: List<String> = emptyList(),
    val rows: List<ExamRow> = emptyList(),
    val edited_by: String? = null,
    val edited_at: String? = null,
)

@Serializable
data class ExamRow(
    val date: String = "",
    val time: String = "",
    val code: String = "",
    /** Subject name as printed on the date sheet. */
    val name: String = "",
    val sections: List<String> = listOf("all"),
    /** Branches the paper is for, e.g. "CSE" ("all" = everyone in the year). */
    val branches: List<String> = listOf("all"),
    val venue: String = "",
)

@Serializable
data class SyllabusFile(val note: String = "", val years: Map<String, SyllabusYear> = emptyMap())

@Serializable
data class SyllabusYear(
    val official: Boolean = false,
    val title: String = "",
    val source: String = "",
    val note: String = "",
    val documents: List<Doc> = emptyList(),
    val missing: Map<String, String> = emptyMap(),
    val subjects: Map<String, Subject> = emptyMap(),
)

@Serializable
data class Doc(val title: String = "", val pdf: String = "")

@Serializable
data class Subject(
    val name: String = "",
    val type: String? = null,
    val contact: String? = null,
    val semester: Double? = null,
    val L: Double? = null,
    val P: Double? = null,
    val credits: Double? = null,
    val pdf: String? = null,
    val pages: List<Int> = emptyList(),
    val text: String = "",
    val note: String? = null,
)

@Serializable
data class HostelFile(val note: String = "", val hostels: Map<String, Hostel> = emptyMap())

@Serializable
data class Hostel(
    val name: String = "",
    val mess_menu: Map<String, Map<String, String>> = emptyMap(),
    val mess_timings: Map<String, String> = emptyMap(),
    /** Times for the days that differ from [mess_timings], e.g. weekend breakfast. */
    val day_timings: Map<String, Map<String, String>> = emptyMap(),
    val edited_by: String? = null,
    val edited_at: String? = null,
    /** A year's own menu, which wins over the all-years one for that year. */
    val years: Map<String, HostelMenu> = emptyMap(),
) {
    /** The menu students of [year] see: their year's own menu, else the all-years one. */
    fun forYear(year: Int): Hostel = years[year.toString()]?.let {
        copy(mess_menu = it.mess_menu, mess_timings = it.mess_timings, day_timings = it.day_timings, edited_by = it.edited_by, edited_at = it.edited_at)
    } ?: this

    /** When [meal] is served on [day]: that day's own time, else the usual one ("" if none). */
    fun timing(day: String, meal: String): String = day_timings[day]?.get(meal)?.ifEmpty { null } ?: mess_timings[meal].orEmpty()
}

@Serializable
data class HostelMenu(
    val mess_menu: Map<String, Map<String, String>> = emptyMap(),
    val mess_timings: Map<String, String> = emptyMap(),
    val day_timings: Map<String, Map<String, String>> = emptyMap(),
    val edited_by: String? = null,
    val edited_at: String? = null,
)

@Serializable
data class AcademicCalendar(
    val name: String = "Academic Calendar",
    val note: String? = null,
    val events: List<CalEvent> = emptyList(),
    val edited_by: String? = null,
    val edited_at: String? = null,
)

@Serializable
data class CalEvent(
    val date: String = "",
    val end: String = "",
    val title: String = "",
    val type: String = "holiday",
    val note: String = "",
    /** ["all"] or some of 1-4; entries saved before years existed have none and are for everyone. */
    val years: List<JsonElement>? = null,
) {
    fun forYear(year: Int): Boolean = years?.asStrings()?.let { it.isEmpty() || "all" in it || year.toString() in it } ?: true
    val yearList: List<String> get() = years?.asStrings()?.ifEmpty { null } ?: listOf("all")
}

@Serializable
data class Post(
    val id: String = "",
    val db_id: Int = 0,
    val kind: String = "notice",
    val title: String = "",
    val body: String = "",
    val code: String? = null,
    val due: String? = null,
    val marks: Int? = null,
    val years: List<JsonElement> = emptyList(),
    val sections: List<String> = listOf("all"),
    val pdf: String? = null,
    val published_at: String = "",
    val published_by: String = "",
    val author_role: String = "admin",
    val author_id: Int = 0,
) : Targeted {
    override val yearList get() = years.asStrings().ifEmpty { listOf("all") }
    override val sectionList get() = sections.ifEmpty { listOf("all") }
}

// ---------------------------------------------------------------- AI features

@Serializable
data class NoticeResult(
    val title: String = "Notice",
    val issued_by: String? = null,
    val audience: String? = null,
    val summary: String = "",
    val urgency: String = "medium",
    val urgency_reason: String = "",
    val deadlines: List<Deadline> = emptyList(),
    val required_documents: List<String> = emptyList(),
    val checklist: List<CheckItem> = emptyList(),
    val key_facts: List<KeyFact> = emptyList(),
    val missing_or_unclear: List<String> = emptyList(),
    val hindi: HindiBlock? = null,
)

@Serializable
data class Deadline(
    val date: String? = null,
    val date_iso: String? = null,
    val what: String? = null,
    val source_text: String? = null,
    val certain: Boolean? = null,
)

@Serializable
data class CheckItem(val step: String = "", val why: String? = null, val priority: String = "medium", val deadline: String? = null)

@Serializable
data class KeyFact(val label: String = "", val value: String = "", val source_text: String? = null)

@Serializable
data class HindiBlock(val summary: String = "", val checklist: List<String> = emptyList())

@Serializable
data class NoticeResponse(
    val result: NoticeResult? = null,
    val model: String = "",
    val cached: Boolean = false,
    val cached_at: String? = null,
    val notice: String? = null,
    val seconds: Double? = null,
    val error: String? = null,
)

/** One explained notice, kept on the phone like the website keeps it in the browser. */
@Serializable
data class NoticeEntry(val id: String, val at: String, val result: NoticeResult, val model: String, val done: List<Boolean>)

@Serializable
data class PlanSubject(val name: String, val exam_date: String, val topics: String, val difficulty: String)

@Serializable
data class PlanRequest(val subjects: List<PlanSubject>, val hours: Double, val start: String, val sample_id: String = "")

@Serializable
data class PlanResult(
    val overview: String = "",
    val warnings: List<String> = emptyList(),
    val days: List<PlanDay> = emptyList(),
    val tips: List<String> = emptyList(),
)

@Serializable
data class PlanDay(val date: String = "", val focus: String = "", val sessions: List<PlanSession> = emptyList())

@Serializable
data class PlanSession(val time: String = "", val subject: String = "", val task: String = "", val type: String = "study")

@Serializable
data class PlanResponse(
    val result: PlanResult? = null,
    val model: String = "",
    val cached: Boolean = false,
    val cached_at: String? = null,
    val notice: String? = null,
    val seconds: Double? = null,
    val error: String? = null,
)

@Serializable
data class SavedPlan(val result: PlanResult, val model: String, val at: String)

@Serializable
data class AskTurn(val q: String, val a: String)

@Serializable
data class AskResponse(
    val answer: String = "",
    val points: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val found: Boolean = true,
    val model: String = "",
    val seconds: Double? = null,
    val error: String? = null,
)

// ---------------------------------------------------------------- attendance

@Serializable
data class Attendance(
    val target: Int = 75,
    val records: List<AttRecord> = emptyList(),
    val base: Map<String, AttBase> = emptyMap(),
    val subjects: List<String> = emptyList(),
)

@Serializable
data class AttRecord(val id: String, val date: String, val code: String, val slot: String, val type: String = "", val status: String)

@Serializable
data class AttBase(val attended: Int = 0, val total: Int = 0)

@Serializable
data class AttendanceResponse(val attendance: Attendance = Attendance(), val updated_at: String? = null, val error: String? = null)

// ---------------------------------------------------------------- chat and admin

@Serializable
data class ChatMessage(
    val id: Int = 0,
    val body: String = "",
    val created_at: String = "",
    val user_id: Int = 0,
    val name: String = "",
    val role: String = "user",
)

@Serializable
data class Ban(
    val id: Int = 0,
    val name: String = "",
    val role: String = "user",
    val until: String? = null,
    val permanent: Boolean = false,
    val by: String? = null,
    val message: String? = null,
)

@Serializable
data class ChatResponse(
    val messages: List<ChatMessage> = emptyList(),
    val latest: Int = 0,
    val banned: Ban? = null,
    /** Ids of the latest messages, so messages deleted by someone else can be dropped. */
    val ids: List<Int>? = null,
)

@Serializable
data class BansResponse(val bans: List<Ban> = emptyList())

@Serializable
data class AccountRow(
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
    val role: String = "user",
    val created_at: String = "",
    val last_login_at: String? = null,
    val chat_banned: Int = 0,
)

@Serializable
data class UsersResponse(val users: List<AccountRow> = emptyList())

/** Error body every endpoint uses: {"error": "..."} */
@Serializable
data class ErrorBody(val error: String? = null, val banned: Ban? = null)

fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull ?: runCatching { jsonPrimitive.contentOrNull }.getOrNull()

/** The newest build CI published, so the app can offer an update. */
@Serializable
data class AppVersion(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apk: String = "",
    val debugApk: String = "",
)

// ---------------------------------------------------------------- college & hostel updates

@Serializable
data class NewsItem(
    val id: Int = 0,
    val section: String = "college",
    val title: String = "",
    val body: String = "",
    val year: String = "all",
    val hostel: String = "both",
    val created_at: String = "",
    val updated_at: String? = null,
    val author_id: Int = 0,
    val author_name: String = "",
    val author_role: String = "user",
    val can_edit: Boolean = false,
)

@Serializable
data class NewsResponse(val updates: List<NewsItem> = emptyList(), val can_post: Boolean = false)

// ---------------------------------------------------------------- private chat channels

@Serializable
data class Channel(
    val id: Int = 0,
    val name: String = "",
    val created_at: String = "",
    val created_by_name: String = "",
    val member_count: Int = 0,
    val latest: Int = 0,
)

@Serializable
data class ChannelsResponse(val channels: List<Channel> = emptyList(), val can_create: Boolean = false)

@Serializable
data class Person(val id: Int = 0, val name: String = "", val email: String = "", val role: String = "user")

@Serializable
data class ChannelBasic(val id: Int = 0, val name: String = "")

@Serializable
data class ChannelInfo(val channel: ChannelBasic = ChannelBasic(), val members: List<Person> = emptyList(), val can_manage: Boolean = false)

@Serializable
data class PeopleResponse(val people: List<Person> = emptyList())

// ---------------------------------------------------------------- results (nithresults.xyz via /api/results)

@Serializable
data class ResultSubject(
    val code: String = "",
    val subject: String = "",
    val credit: Double = 0.0,
    val grade: String = "",
    val points: Double? = null,
)

@Serializable
data class ResultSemester(
    val number: Int = 0,
    val name: String = "",
    val subjects: List<ResultSubject> = emptyList(),
    val credits: Double = 0.0,
    val sgpa: Double? = null,
    val cgpa: Double? = null,
)

@Serializable
data class ResultStudent(
    val name: String = "",
    val roll: String = "",
    val father: String = "",
    val year: JsonElement? = null,
    val semesters: List<ResultSemester> = emptyList(),
    val credits: Double = 0.0,
    val cgpa: Double? = null,
)

@Serializable
data class ResultsResponse(
    val students: List<ResultStudent> = emptyList(),
    val fetched_at: Double = 0.0,
    val cached: Boolean = false,
    val stale: Boolean = false,
)
