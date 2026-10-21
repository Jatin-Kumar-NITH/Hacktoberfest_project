package app.vercel.campusmate_nith

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vercel.campusmate_nith.background.ClassReminders
import app.vercel.campusmate_nith.background.Notify
import app.vercel.campusmate_nith.background.StudyTimer
import app.vercel.campusmate_nith.background.SyncWorker
import app.vercel.campusmate_nith.background.TodayWidget
import app.vercel.campusmate_nith.data.Api
import app.vercel.campusmate_nith.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CampusMateApplication : Application() {
    lateinit var api: Api
        private set
    lateinit var store: Store
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** "system", "light" or "dark", from Settings. */
    var themeMode by mutableStateOf("system")

    /** A screen a notification, shortcut or the widget asked to open. */
    var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate() {
        super.onCreate()
        api = Api(this)
        store = Store(this)
        themeMode = store.theme
        StudyTimer.load(store)
        Notify.createChannels(this)
        SyncWorker.schedule(this)
        background {
            ClassReminders.schedule(this@CampusMateApplication, store.campusCache?.campus)
            TodayWidget.refresh(this@CampusMateApplication)
        }
    }

    /** Runs off the main thread (parsing the saved campus data can take a moment). */
    fun background(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}
