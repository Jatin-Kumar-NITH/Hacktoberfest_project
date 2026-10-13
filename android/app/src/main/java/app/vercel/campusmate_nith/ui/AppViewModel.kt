package app.vercel.campusmate_nith.ui

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vercel.campusmate_nith.BuildConfig
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.background.ClassReminders
import app.vercel.campusmate_nith.background.TodayWidget
import app.vercel.campusmate_nith.background.Updates
import app.vercel.campusmate_nith.data.AppVersion
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.AskTurn
import app.vercel.campusmate_nith.data.Attendance
import app.vercel.campusmate_nith.data.Campus
import app.vercel.campusmate_nith.data.CampusResponse
import app.vercel.campusmate_nith.data.Hostel
import app.vercel.campusmate_nith.data.NoticeEntry
import app.vercel.campusmate_nith.data.SavedPlan
import app.vercel.campusmate_nith.data.Section
import app.vercel.campusmate_nith.data.SignInCancelled
import app.vercel.campusmate_nith.data.SyllabusYear
import app.vercel.campusmate_nith.data.Timetable
import app.vercel.campusmate_nith.data.User
import app.vercel.campusmate_nith.data.attStats
import app.vercel.campusmate_nith.data.forYear
import app.vercel.campusmate_nith.data.googleIdToken
import app.vercel.campusmate_nith.data.pdfPath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthState { Checking, SignedOut, SignedIn }

/** Everything the screens share: the account, campus data and the student's own choices. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val cm = app as CampusMateApplication
    val api = cm.api
    val store = cm.store

    var auth by mutableStateOf(AuthState.Checking); private set
    var user by mutableStateOf<User?>(null); private set
    var campus by mutableStateOf(Campus()); private set
    var samples by mutableStateOf(emptyList<app.vercel.campusmate_nith.data.SampleNotice>()); private set
    var refreshing by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null)
    var signingIn by mutableStateOf(false); private set
    var signInError by mutableStateOf<String?>(null)
    var lastSync by mutableStateOf(store.lastSync); private set

    /** A newer build than this one, when CI has published one. */
    var update by mutableStateOf<AppVersion?>(null); private set
    var checkingUpdate by mutableStateOf(false); private set

    // settings
    var notifyNew by mutableStateOf(store.notifyNew); private set
    var notifyClasses by mutableStateOf(store.notifyClasses); private set
    var classLead by mutableStateOf(store.classLead); private set

    // the student's year / section / group, like the website's sidebar pickers
    var year by mutableStateOf(store.year); private set
    var section by mutableStateOf(store.section); private set
    var group by mutableStateOf(store.group); private set
    var hostel by mutableStateOf(store.hostel)
    var profileChosen by mutableStateOf(store.profileChosen); private set

    var history by mutableStateOf(store.noticeHistory); private set
    var plan by mutableStateOf(store.plan); private set
    var assignmentsDone by mutableStateOf(store.assignmentsDone); private set

    var attendance by mutableStateOf(Attendance()); private set
    var attSaving by mutableStateOf<String?>(null); private set
    private var attDirty = false
    private var attJob: Job? = null

    /** Ask CampusMate conversation (kept while the app is open, like the website). */
    val askLog = mutableStateListOf<AskEntry>()
    var askHistory = listOf<AskTurn>()

    /** A question another screen wants Ask CampusMate to answer next. */
    var pendingQuestion by mutableStateOf<String?>(null)
    /** Notice text the board sends to the explainer. */
    var pendingExplain by mutableStateOf<Pair<String, String>?>(null)
    /** Subjects (name to exam date) the exam page hands to the study planner. */
    var pendingPlan by mutableStateOf<List<Pair<String, String>>?>(null)
    /** An explained notice from history to show in the explainer. */
    var openNotice by mutableStateOf<String?>(null)

    init {
        store.campusCache?.let { applyCampus(it) }
        viewModelScope.launch { refresh(initial = true) }
        checkUpdate()
    }

    val isAdmin get() = user?.isAdmin == true

    fun timetable(y: Int = year): Timetable = campus.timetables[y.toString()] ?: Timetable(year = y)
    fun sectionOf(y: Int, id: String): Section =
        timetable(y).sections.let { secs -> secs.firstOrNull { it.id == id } ?: secs.firstOrNull() ?: Section(id = id, name = "Section $id") }
    val mySection get() = sectionOf(year, section)
    fun syllabus(y: Int = year): SyllabusYear = campus.syllabus.years[y.toString()] ?: SyllabusYear()
    fun subjectName(code: String, y: Int = year): String = syllabus(y).subjects[code]?.name?.takeIf { it != code }.orEmpty()
    /** The academic calendar entries for the student's year. */
    val myCalendar get() = campus.calendar.forYear(year)
    fun myHostel(): Hostel = campus.hostel.hostels[hostel] ?: campus.hostel.hostels.values.firstOrNull() ?: Hostel()
    val myLabel get() = "${app.vercel.campusmate_nith.data.YEAR_NAMES[year]} · Section $section${if (group.isNotEmpty()) " · $group" else ""}"

    private fun applyCampus(r: CampusResponse) {
        campus = r.campus
        samples = r.samples
        r.me?.let { user = it; auth = AuthState.SignedIn }
        // keep the saved section valid for this year's timetable
        val sec = sectionOf(year, section)
        if (sec.id != section) setProfile(year, sec.id, "")
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            refreshing = true
            try {
                val r = api.campus()
                applyCampus(r)
                store.campusCache = r
                store.lastSync = System.currentTimeMillis(); lastSync = store.lastSync
                loadError = null
                // the student is looking at the app, so these aren't news any more
                cm.background { Updates.takeNew(store, r.campus) }
                afterCampusChange()
                if (initial || !attDirty) loadAttendance()
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) {
                    signOutLocal()
                } else {
                    // a parsing problem must not crash the app; show it like any other load error
                    loadError = if (e is ApiException) e.message else "Couldn't read the data from CampusMate (${e.javaClass.simpleName}: ${e.message})."
                    if (user == null) auth = AuthState.SignedOut
                }
            } finally {
                refreshing = false
            }
        }
    }

    fun signIn(activity: Activity) {
        if (signingIn) return
        viewModelScope.launch {
            signingIn = true
            signInError = null
            try {
                val clientId = step("Couldn't load the sign-in settings from CampusMate") { api.config() }
                if (clientId.isEmpty()) throw ApiException("Google login is not set up on the server yet (GOOGLE_CLIENT_ID is missing).")
                val token = googleIdToken(activity, clientId)
                step("Google signed you in, but CampusMate didn't accept it") { api.login(token) }
                user = step("Signed in, but couldn't load your account") { api.me() }
                auth = AuthState.SignedIn
                refresh(initial = true)
            } catch (e: SignInCancelled) {
                // the student closed the picker
            } catch (e: Exception) {
                signInError = e.message ?: "Sign-in failed. Please try again. (${e.javaClass.simpleName})"
            } finally {
                signingIn = false
            }
        }
    }

    /** Runs one sign-in step and says which one failed, with the server's reason. */
    private suspend fun <T> step(what: String, block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        throw ApiException("$what: ${e.message}", e.status, e.banned)
    } catch (e: Exception) {
        throw ApiException("$what (${e.javaClass.simpleName}: ${e.message}).")
    }

    fun logout() {
        viewModelScope.launch {
            api.logout()
            signOutLocal()
        }
    }

    private fun signOutLocal() {
        user = null
        auth = AuthState.SignedOut
        store.clearAccount()
        campus = Campus()
        afterCampusChange()
        askLog.clear(); askHistory = emptyList()
    }

    fun setProfile(y: Int, sec: String, grp: String) {
        year = y; section = sec; group = grp
        store.year = y; store.section = sec; store.group = grp
        store.profileChosen = true; profileChosen = true
        askHistory = emptyList()
        store.seenIds = null   // a new section: start counting "new" from now
        afterCampusChange()
    }

    /** Keeps the reminders and the widget in step with the timetable and the student's section. */
    private fun afterCampusChange() {
        val c = if (auth == AuthState.SignedIn) campus else null
        cm.background {
            ClassReminders.schedule(cm, c)
            TodayWidget.refresh(cm)
        }
    }

    // ------------------------------------------------------------ settings

    fun setTheme(mode: String) {
        store.theme = mode; cm.themeMode = mode
    }

    fun changeNotifyNew(on: Boolean) {
        notifyNew = on; store.notifyNew = on
    }

    fun changeNotifyClasses(on: Boolean) {
        notifyClasses = on; store.notifyClasses = on; afterCampusChange()
    }

    fun changeClassLead(minutes: Int) {
        classLead = minutes; store.classLead = minutes; afterCampusChange()
    }

    fun clearLocalData() {
        store.clearLocal()
        history = emptyList(); plan = null; assignmentsDone = emptyMap()
    }

    /** Looks for a newer APK on the android-latest release. */
    fun checkUpdate(manual: Boolean = false) {
        if (checkingUpdate) return
        viewModelScope.launch {
            checkingUpdate = true
            val v = api.latestVersion()
            checkingUpdate = false
            val url = v?.let { if (BuildConfig.DEBUG) it.debugApk else it.apk }.orEmpty()
            update = if (v != null && v.versionCode > BuildConfig.VERSION_CODE && url.isNotEmpty()) v else null
            if (manual && update == null) {
                Toast.makeText(cm, if (v == null) "Couldn't check for updates." else "You have the latest version.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateUrl(): String = update?.let { if (BuildConfig.DEBUG) it.debugApk else it.apk }.orEmpty()

    fun chooseHostel(h: String) {
        hostel = h; store.hostel = h
    }

    // ------------------------------------------------------------ notice history (on the phone)

    fun addHistory(e: NoticeEntry) {
        history = (listOf(e) + history).take(20); store.noticeHistory = history
    }

    fun setDone(id: String, index: Int, done: Boolean) {
        history = history.map { h -> if (h.id != id) h else h.copy(done = h.done.toMutableList().also { if (index < it.size) it[index] = done }) }
        store.noticeHistory = history
    }

    fun clearHistory() {
        history = emptyList(); store.noticeHistory = history
    }

    fun savePlan(p: SavedPlan) {
        plan = p; store.plan = p
    }

    fun toggleAssignment(id: String) {
        assignmentsDone = assignmentsDone + (id to !(assignmentsDone[id] ?: false)); store.assignmentsDone = assignmentsDone
    }

    // ------------------------------------------------------------ attendance (saved to the account)

    private suspend fun loadAttendance() {
        val uid = user?.id ?: return
        store.attendance(uid)?.let { if (!attDirty) attendance = it }
        try {
            val a = api.attendance()
            if (!attDirty) {
                attendance = a; store.saveAttendance(uid, a)
            }
        } catch (e: ApiException) {
            attSaving = "Couldn't load your saved attendance (${e.message}). Showing the copy on this phone."
        }
    }

    fun updateAttendance(change: (Attendance) -> Attendance) {
        val uid = user?.id ?: return
        attendance = change(attendance)
        attDirty = true
        store.saveAttendance(uid, attendance)
        attSaving = "Saving…"
        attJob?.cancel()
        attJob = viewModelScope.launch {
            delay(500)
            attSaving = try {
                api.saveAttendance(attendance); "Saved to your account ✓"
            } catch (e: ApiException) {
                "Couldn't save to your account: ${e.message} Your marks are kept on this phone and will be saved the next time you mark something."
            }
        }
    }

    fun attSubjects(): List<String> {
        val codes = mySection.classes.filter { it.type != "Free" }.map { it.code }.distinct().sorted().toMutableList()
        (attendance.subjects + attendance.base.keys + attendance.records.map { it.code }).forEach { if (it !in codes) codes += it }
        return codes
    }

    /** What /api/ask uses to answer questions about the student's own saved things. */
    fun clientContext(): kotlinx.serialization.json.JsonObject {
        val deadlines = history.flatMap { h -> h.result.deadlines.map { Triple(it.date_iso ?: it.date, it.what, h.result.title) } }.take(20)
        val stats = attSubjects().map { attStats(attendance, it, myCalendar) }.filter { it.total > 0 }
        return kotlinx.serialization.json.buildJsonObject {
            put("deadlines", kotlinx.serialization.json.buildJsonArray {
                deadlines.forEach { (d, w, f) ->
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("date", kotlinx.serialization.json.JsonPrimitive(d)); put("what", kotlinx.serialization.json.JsonPrimitive(w))
                        put("from", kotlinx.serialization.json.JsonPrimitive(f))
                    })
                }
            })
            put("plan", plan?.let { app.vercel.campusmate_nith.data.json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(app.vercel.campusmate_nith.data.PlanDay.serializer()), it.result.days) }
                ?: kotlinx.serialization.json.JsonArray(emptyList()))
            put("attendance", kotlinx.serialization.json.buildJsonObject {
                put("target", kotlinx.serialization.json.JsonPrimitive(attendance.target))
                put("subjects", kotlinx.serialization.json.buildJsonArray {
                    stats.forEach { s ->
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("code", kotlinx.serialization.json.JsonPrimitive(s.code))
                            put("attended", kotlinx.serialization.json.JsonPrimitive(s.attended))
                            put("total", kotlinx.serialization.json.JsonPrimitive(s.total))
                            put("percent", kotlinx.serialization.json.JsonPrimitive(Math.round((s.pct ?: 0.0) * 10) / 10.0))
                            put("advice", kotlinx.serialization.json.JsonPrimitive(s.advice))
                        })
                    }
                })
            })
        }
    }

    // ------------------------------------------------------------ campus data edits made by admins

    fun replaceCampus(c: Campus) {
        campus = c
        store.campusCache = CampusResponse(campus = c, samples = samples, me = user)
        afterCampusChange()
    }

    // ------------------------------------------------------------ PDFs

    var downloading by mutableStateOf(false); private set

    fun openPdf(context: Context, path: String) {
        if (downloading) return
        viewModelScope.launch {
            downloading = true
            try {
                val file = api.downloadPdf(pdfPath(path))
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(Intent.createChooser(intent, "Open PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "No app found to open PDFs.", Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } finally {
                downloading = false
            }
        }
    }
}

/** One line in the Ask CampusMate conversation. */
data class AskEntry(
    val kind: String,           // "user", "bot", "err"
    val text: String,
    val points: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val found: Boolean = true,
    val meta: String = "",
)
