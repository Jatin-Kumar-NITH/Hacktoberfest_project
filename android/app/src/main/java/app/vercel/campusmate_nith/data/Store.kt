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

    /** Roll numbers looked up on the Results page, newest first (like the website's cm_results_recent). */
    var recentRolls: List<String>
        get() = read("results_recent", ListSerializer(String.serializer()), emptyList())
        set(v) = write("results_recent", ListSerializer(String.serializer()), v.take(6))

    /** Year and hostel picked on Hostel updates ("" follows the profile). */
    var newsYear: String
        get() = prefs.getString("news_year", "") ?: ""
        set(v) = prefs.edit().putString("news_year", v).apply()
    var newsHostel: String
        get() = prefs.getString("news_hostel", "") ?: ""
        set(v) = prefs.edit().putString("news_hostel", v).apply()

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

    /** When the campus data was last fetched (epoch millis), shown when the app is offline. */
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) = prefs.edit().putLong("last_sync", v).apply()

    // ------------------------------------------------------------ settings

    /** "system", "light" or "dark". */
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(v) = prefs.edit().putString("theme", v).apply()
    var notifyNew: Boolean
        get() = prefs.getBoolean("notify_new", true)
        set(v) = prefs.edit().putBoolean("notify_new", v).apply()
    var notifyClasses: Boolean
        get() = prefs.getBoolean("notify_classes", true)
        set(v) = prefs.edit().putBoolean("notify_classes", v).apply()
    /** Minutes before a class that its reminder goes off. */
    var classLead: Int
        get() = prefs.getInt("class_lead", 10)
        set(v) = prefs.edit().putInt("class_lead", v).apply()
    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(v) = prefs.edit().putBoolean("asked_notifications", v).apply()

    /** Notices, assignments and exams already seen, so only new ones notify. Null until the first sync. */
    var seenIds: Set<String>?
        get() = if (prefs.contains("seen_ids")) prefs.getStringSet("seen_ids", emptySet())!!.toSet() else null
        set(v) {
            if (v == null) prefs.edit().remove("seen_ids").apply() else prefs.edit().putStringSet("seen_ids", v).apply()
        }

    fun clearAccount() {
        prefs.edit().remove("campus_cache").remove("seen_ids").remove("last_sync").apply()
    }

    /** "Clear data on this phone": everything except the sign-in cookie and settings. */
    fun clearLocal() {
        prefs.edit().remove("notice_history").remove("plan").remove("assign_done").apply()
    }
}
