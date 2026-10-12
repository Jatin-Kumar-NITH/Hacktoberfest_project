package app.vercel.campusmate_nith.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/** What the website keeps in localStorage, kept on the phone instead. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("campusmate", Context.MODE_PRIVATE)

    private fun <T> read(key: String, s: KSerializer<T>, fallback: T): T =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(s, it) }.getOrNull() } ?: fallback

    private fun <T> write(key: String, s: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(s, value)).apply()
    }

    var year: Int
        get() = prefs.getInt("year", 1)
        set(v) = prefs.edit().putInt("year", v).apply()
    var section: String
        get() = prefs.getString("section", "A") ?: "A"
        set(v) = prefs.edit().putString("section", v).apply()
    var group: String
        get() = prefs.getString("group", "") ?: ""
        set(v) = prefs.edit().putString("group", v).apply()
    var hostel: String
        get() = prefs.getString("hostel", "boys") ?: "boys"
        set(v) = prefs.edit().putString("hostel", v).apply()
    var profileChosen: Boolean
        get() = prefs.getBoolean("profile_chosen", false)
        set(v) = prefs.edit().putBoolean("profile_chosen", v).apply()

    var noticeHistory: List<NoticeEntry>
        get() = read("notice_history", ListSerializer(NoticeEntry.serializer()), emptyList())
        set(v) = write("notice_history", ListSerializer(NoticeEntry.serializer()), v.take(20))

    var plan: SavedPlan?
        get() = read("plan", SavedPlan.serializer().nullable, null)
        set(v) {
            if (v == null) prefs.edit().remove("plan").apply() else write("plan", SavedPlan.serializer(), v)
        }

    var assignmentsDone: Map<String, Boolean>
        get() = read("assign_done", MapSerializer(String.serializer(), Boolean.serializer()), emptyMap())
        set(v) = write("assign_done", MapSerializer(String.serializer(), Boolean.serializer()), v)

    /** Offline copy of the account's attendance, per user (like the website's cm_attendance_<id>). */
    fun attendance(uid: Int): Attendance? = read("attendance_$uid", Attendance.serializer().nullable, null)
    fun saveAttendance(uid: Int, a: Attendance) = write("attendance_$uid", Attendance.serializer(), a)

    /** Last campus data, so the app opens instantly and works offline. */
    var campusCache: CampusResponse?
        get() = read("campus_cache", CampusResponse.serializer().nullable, null)
        set(v) {
            if (v == null) prefs.edit().remove("campus_cache").apply() else write("campus_cache", CampusResponse.serializer(), v)
        }

    fun clearAccount() {
        prefs.edit().remove("campus_cache").apply()
    }
}
