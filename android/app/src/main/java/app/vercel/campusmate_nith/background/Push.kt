package app.vercel.campusmate_nith.background

import android.content.Context
import android.util.Log
import app.vercel.campusmate_nith.CampusMateApplication
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Instant notifications through Firebase Cloud Messaging.
 *
 * The server (push.py) sends a content-free ping to a topic when something is posted:
 * "campus" (notices, assignments, exams), "news-college" / "news-hostel", "chat" and "channel-<id>".
 * The app then fetches what's new over its signed-in connection and shows the notification itself,
 * exactly like the hourly check does. Without Firebase set up on the server, only the hourly check runs.
 */
object Push {
    private const val TAG = "CampusMatePush"

    /** Starts Firebase from the saved config. False when the server hasn't set Firebase up. */
    fun start(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        val c = (context.applicationContext as CampusMateApplication).store.firebaseConfig ?: return false
        if (c.app_id.isEmpty() || c.api_key.isEmpty()) return false
        return runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(c.project_id).setGcmSenderId(c.sender_id)
                    .setApplicationId(c.app_id).setApiKey(c.api_key)
                    .build(),
            )
            true
        }.onFailure { Log.w(TAG, "Couldn't start Firebase", it) }.getOrDefault(false)
    }

    /** Gets the server's Firebase settings and subscribes to the topics your settings and channels call for. */
    suspend fun sync(app: CampusMateApplication) = withContext(Dispatchers.IO) {
        val store = app.store
        if (store.campusCache == null) return@withContext   // signed out
        try {
            // a network failure keeps the saved config; a server without Firebase clears it
            runCatching { app.api.appConfig() }.getOrNull()?.let { store.firebaseConfig = it.firebase }
            if (!start(app)) return@withContext
            val want = mutableSetOf("campus", "news-college", "news-hostel")
            if (store.notifyCampusChat) want += "chat"
            if (store.notifyChat) {
                val channels = runCatching { app.api.channels().channels }.getOrNull()
                if (channels != null) {
                    store.channelNames = channels.associate { it.id.toString() to it.name }
                    want += channels.map { "channel-${it.id}" }
                } else {
                    want += store.pushTopics.filter { it.startsWith("channel-") }   // offline: keep what we had
                }
            }
            val fm = FirebaseMessaging.getInstance()
            // subscribing again is harmless and repairs anything lost (a new token, a reinstall)
            want.forEach { Tasks.await(fm.subscribeToTopic(it), 20, TimeUnit.SECONDS) }
            (store.pushTopics - want).forEach { Tasks.await(fm.unsubscribeFromTopic(it), 20, TimeUnit.SECONDS) }
            store.pushTopics = want
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't update push topics", e)
        }
    }

    /** On sign-out: drops this phone's push token, which removes every topic with it. */
    suspend fun stop(app: CampusMateApplication) = withContext(Dispatchers.IO) {
        if (FirebaseApp.getApps(app).isNotEmpty()) {
            runCatching { Tasks.await(FirebaseMessaging.getInstance().deleteToken(), 20, TimeUnit.SECONDS) }
        }
        app.store.pushTopics = emptySet()
    }

    /** What a ping asks for. Runs on the push service's background thread. */
    suspend fun handle(app: CampusMateApplication, data: Map<String, String>) {
        val store = app.store
        if (store.campusCache == null) return
        try {
            when (data["kind"]) {
                "campus" -> SyncWorker.syncCampus(app)
                "news" -> news(app, data["section"] ?: return)
                "chat" -> chat(app, data["channel"]?.toIntOrNull())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // ran out of time; the hourly check will catch up
        } catch (e: Exception) {
            Log.w(TAG, "Push sync failed", e)   // offline or unreadable data: same as a missed hourly check
        }
    }

    private suspend fun news(app: CampusMateApplication, section: String) {
        val store = app.store
        if (section != "college" && section != "hostel") return
        val me = store.campusCache?.me?.id
        // hostel news for the student's own year and hostel, like the Hostel updates page
        val items = app.api.news(section, store.year.toString(), store.hostel).updates
        val seen = store.newsSeen(section)
        store.setNewsSeen(section, items.map { it.id.toString() }.toSet())
        if (seen == null || !store.notifyNew) return
        val fresh = items.filter { it.id.toString() !in seen && it.author_id != me }
        if (fresh.isEmpty()) return
        val route = "$section-updates"
        val label = if (section == "hostel") "hostel update" else "college update"
        if (fresh.size == 1) {
            val u = fresh[0]
            Notify.post(app, 3000 + u.id, Notify.UPDATES, "New $label: ${u.title.ifEmpty { u.author_name }}", u.body, route)
        } else {
            Notify.post(
                app, if (section == "hostel") 3001 else 3002, Notify.UPDATES, "${fresh.size} new ${label}s",
                fresh.joinToString(", ") { it.title.ifEmpty { it.body.take(40) } }, route,
                lines = fresh.map { it.title.ifEmpty { it.body.take(60) } },
            )
        }
    }

    private suspend fun chat(app: CampusMateApplication, channel: Int?) {
        val store = app.store
        if (if (channel == null) !store.notifyCampusChat else !store.notifyChat) return
        val key = channel?.toString() ?: "campus"
        val seen = store.chatSeen(key)
        val r = app.api.chat(seen ?: 0, channel)
        store.setChatSeen(key, r.latest)
        if (seen == null || app.openChat == key) return   // first check, or you're reading it right now
        val me = store.campusCache?.me?.id
        val fresh = r.messages.filter { it.id > seen && it.user_id != me }
        if (fresh.isEmpty()) return
        val title = if (channel == null) "Campus chat" else "🔒 ${store.channelNames[key] ?: "Private channel"}"
        val lines = fresh.map { "${it.name}: ${it.body}" }
        Notify.post(
            app, 2000 + (channel ?: 0), Notify.CHAT,
            if (fresh.size == 1) title else "$title · ${fresh.size} new messages",
            lines.last(), "chat", lines = if (fresh.size > 1) lines.takeLast(6) else emptyList(),
        )
    }
}

/** Receives Firebase pings and new push tokens. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as CampusMateApplication
        // a high-priority ping gives the app about 10 seconds to do its work
        runBlocking { withTimeoutOrNull(9_000) { Push.handle(app, message.data) } }
    }

    override fun onNewToken(token: String) {
        val app = application as CampusMateApplication
        app.background { Push.sync(app) }
    }
}
