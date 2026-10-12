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
    val isAdmin get() = role == "admin" || role == "owner"
    val isOwner get() = role == "owner"
}

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
data class ExamFile(val note: String = "", val years: Map<String, ExamYear> = emptyMap(), val rules: List<String> = emptyList())

@Serializable
data class ExamYear(
    val name: String = "",
    val rows: List<ExamRow> = emptyList(),
    val edited_by: String? = null,
    val edited_at: String? = null,
)

@Serializable
data class ExamRow(
    val date: String = "",
    val time: String = "",
    val code: String = "",
    val sections: List<String> = listOf("all"),
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
)

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
data class ChatResponse(val messages: List<ChatMessage> = emptyList(), val latest: Int = 0, val banned: Ban? = null)

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
