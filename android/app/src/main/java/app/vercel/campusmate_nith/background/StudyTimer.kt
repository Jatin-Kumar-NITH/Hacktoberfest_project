package app.vercel.campusmate_nith.background

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.R
import app.vercel.campusmate_nith.data.StudyLog
import app.vercel.campusmate_nith.data.StudyPrefs
import app.vercel.campusmate_nith.data.StudyState
import app.vercel.campusmate_nith.data.StudyTarget
import app.vercel.campusmate_nith.data.Store
import app.vercel.campusmate_nith.data.todayIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Study mode: a Pomodoro timer that switches Do Not Disturb on for the session and back off at the end.
 *
 * No service runs: the session lives in [Store.study], an alarm ends each phase ([StudyReceiver]),
 * and an ongoing notification counts down with the system's own chronometer.
 */
object StudyTimer {
    private const val ONGOING_ID = 3001
    private const val ALERT_ID = 3002
    private const val ALARM_REQUEST = 31

    const val ACTION_END = "app.vercel.campusmate_nith.study.END"
    const val ACTION_PAUSE = "app.vercel.campusmate_nith.study.PAUSE"
    const val ACTION_RESUME = "app.vercel.campusmate_nith.study.RESUME"
    const val ACTION_SKIP = "app.vercel.campusmate_nith.study.SKIP"
    const val ACTION_STOP = "app.vercel.campusmate_nith.study.STOP"

    private val _state = MutableStateFlow<StudyState?>(null)
    private val _log = MutableStateFlow<List<StudyLog>>(emptyList())
    /** The running session, or null. */
    val state: StateFlow<StudyState?> = _state.asStateFlow()
    /** Finished sessions, newest first. */
    val log: StateFlow<List<StudyLog>> = _log.asStateFlow()

    private fun store(ctx: Context): Store = (ctx.applicationContext as CampusMateApplication).store

    fun load(store: Store) {
        _state.value = store.study
        _log.value = store.studyLog
    }

    private fun save(ctx: Context, s: StudyState?) {
        _state.value = s
        store(ctx).study = s
    }

    // ------------------------------------------------------------ Do Not Disturb

    fun dndAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    /** Switches Do Not Disturb on (priority only: alarms and the people you allow still get through). */
    private fun dndOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return false
        // already quiet: leave the student's own setting alone, and don't undo it at the end
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
        return runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); true }.getOrDefault(false)
    }

    private fun dndOff(ctx: Context, s: StudyState) {
        if (!s.dndByUs) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        // only if it is still the mode study mode set (the student may have changed it meanwhile)
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
    }

    /** Whether phase changes land on time; without the exact-alarm permission an idle phone may run them a few minutes late. */
    fun exactAlarms(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    // ------------------------------------------------------------ session

    @Synchronized
    fun start(ctx: Context, target: StudyTarget, p: StudyPrefs) {
        _state.value?.let { finish(ctx, it, alert = false) }
        store(ctx).studyPrefs = p
        // re-create the channels so the alert channel may ring through Do Not Disturb now that access may be granted
        Notify.createChannels(ctx)
        val now = System.currentTimeMillis()
        val ms = p.focusMin * 60_000L
        val s = StudyState(
            subject = target.subject.ifBlank { "Study" }, task = target.task, planKey = target.planKey,
            focusMin = p.focusMin, shortMin = p.shortMin, longMin = p.longMin, rounds = target.rounds ?: p.rounds,
            phase = "focus", phaseMs = ms, endsAt = now + ms, startedAt = now,
            dndByUs = p.dnd && dndOn(ctx),
        )
        save(ctx, s)
        schedule(ctx, s)
        showOngoing(ctx, s)
    }

    @Synchronized
    fun pause(ctx: Context) {
        val s = _state.value?.takeIf { !it.paused } ?: return
        val p = s.copy(pausedLeft = s.left(System.currentTimeMillis()))
        save(ctx, p); schedule(ctx, p); showOngoing(ctx, p)
    }

    @Synchronized
    fun resume(ctx: Context) {
        val s = _state.value?.takeIf { it.paused } ?: return
        val r = s.copy(endsAt = System.currentTimeMillis() + s.pausedLeft, pausedLeft = -1)
        save(ctx, r); schedule(ctx, r); showOngoing(ctx, r)
    }

    /** Ends the phase now. A skipped focus counts its minutes but not as a pomodoro. */
    @Synchronized
    fun skip(ctx: Context) {
        val s = _state.value ?: return
        next(ctx, s, completed = false, alert = false)
    }

    /** Stops the session early, keeping the focus time done so far. */
    @Synchronized
    fun stop(ctx: Context) {
        val s = _state.value ?: return
        val now = System.currentTimeMillis()
        val partial = if (s.focusing) s.phaseMs - s.left(now) else 0L
        finish(ctx, s.copy(focusMs = s.focusMs + partial), alert = false)
    }

    /** A phase's alarm went off, or the open screen saw it end. [endsAt] stops a late or repeated alarm acting twice. */
    @Synchronized
    fun phaseEnded(ctx: Context, endsAt: Long) {
        val s = _state.value ?: return
        if (s.paused || s.endsAt != endsAt || System.currentTimeMillis() < s.endsAt - 1_000) return
        next(ctx, s, completed = true, alert = true)
    }

    /** After a restart: alarms are gone, so set the current one again (or move on if it was missed). */
    @Synchronized
    fun restore(ctx: Context) {
        val s = _state.value ?: return
        if (!s.paused && System.currentTimeMillis() >= s.endsAt) next(ctx, s, completed = true, alert = true)
        else { schedule(ctx, s); showOngoing(ctx, s) }
    }

    private fun next(ctx: Context, s: StudyState, completed: Boolean, alert: Boolean) {
        val now = System.currentTimeMillis()
        if (s.focusing) {
            val done = if (completed) s.done + 1 else s.done
            val focusMs = s.focusMs + if (completed) s.phaseMs else s.phaseMs - s.left(now)
            val after = s.copy(done = done, focusMs = focusMs)
            // the session's last focus (or the last that could be): done
            if (done >= s.rounds || (!completed && s.done + 1 >= s.rounds)) {
                finish(ctx, after, alert)
                return
            }
            val long = done > 0 && done % 4 == 0
            val ms = (if (long) s.longMin else s.shortMin) * 60_000L
            val b = after.copy(phase = if (long) "long" else "short", phaseMs = ms, endsAt = now + ms, pausedLeft = -1)
            save(ctx, b); schedule(ctx, b); showOngoing(ctx, b)
            if (alert) alert(ctx, "Pomodoro $done of ${s.rounds} done", "Take a ${if (long) s.longMin else s.shortMin} minute ${if (long) "long " else ""}break. Focus starts again on its own.")
        } else {
            val ms = s.focusMin * 60_000L
            val f = s.copy(phase = "focus", phaseMs = ms, endsAt = now + ms, pausedLeft = -1)
            save(ctx, f); schedule(ctx, f); showOngoing(ctx, f)
            if (alert) alert(ctx, "Break over", "Pomodoro ${f.round} of ${s.rounds}: ${s.subject}. ${s.focusMin} minutes, starting now.")
        }
    }

    private fun finish(ctx: Context, s: StudyState, alert: Boolean) {
        dndOff(ctx, s)
        save(ctx, null)
        cancelAlarm(ctx)
        NotificationManagerCompat.from(ctx).cancel(ONGOING_ID)
        val minutes = (s.focusMs / 60_000L).toInt()
        if (minutes >= 1 || s.done > 0) {
            val entry = StudyLog(System.currentTimeMillis(), todayIso(), s.subject, s.task, s.planKey, minutes, s.done)
            val list = listOf(entry) + _log.value
            _log.value = list
            store(ctx).studyLog = list
        }
        if (alert) alert(ctx, "Study session complete", "${s.done} × ${s.focusMin} min of ${s.subject}." +
            if (s.dndByUs) " Do Not Disturb is off again." else "")
    }

    fun clearLog(ctx: Context) {
        _log.value = emptyList()
        store(ctx).studyLog = emptyList()
    }

    // ------------------------------------------------------------ alarm

    private fun alarmIntent(ctx: Context, endsAt: Long?): PendingIntent {
        val i = Intent(ctx, StudyReceiver::class.java).setAction(ACTION_END)
        if (endsAt != null) i.putExtra("ends_at", endsAt)
        return PendingIntent.getBroadcast(ctx, ALARM_REQUEST, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cancelAlarm(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, null))

    private fun schedule(ctx: Context, s: StudyState) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (s.paused) { cancelAlarm(ctx); return }
        val pi = alarmIntent(ctx, s.endsAt)
        try {
            if (exactAlarms(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAt, pi)
        }
    }

    // ------------------------------------------------------------ notifications

    private fun action(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, StudyReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    fun phaseLabel(s: StudyState) = when (s.phase) {
        "short" -> "Short break"
        "long" -> "Long break"
        else -> "Focus ${s.round} of ${s.rounds}"
    }

    fun clock(ms: Long): String {
        val sec = (ms + 999) / 1000
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    private fun showOngoing(ctx: Context, s: StudyState) {
        if (!Notify.allowed(ctx)) return
        val b = NotificationCompat.Builder(ctx, Notify.STUDY)
            .setSmallIcon(R.drawable.ic_stat_campusmate)
            .setColor(ContextCompat.getColor(ctx, R.color.accent))
            .setContentTitle("${phaseLabel(s)} · ${s.subject}")
            .setContentIntent(Notify.openIntent(ctx, "study"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (s.paused) {
            b.setContentText("Paused · ${clock(s.pausedLeft)} left").setShowWhen(false)
            b.addAction(0, "Resume", action(ctx, ACTION_RESUME, 32))
        } else {
            b.setContentText(if (s.focusing) (s.task.ifBlank { "Stay on it. Breaks start on their own." }) else "Stand up, stretch, drink some water.")
                .setWhen(s.endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            b.addAction(0, "Pause", action(ctx, ACTION_PAUSE, 33))
        }
        b.addAction(0, if (s.focusing) "Skip" else "Skip break", action(ctx, ACTION_SKIP, 34))
        b.addAction(0, "Stop", action(ctx, ACTION_STOP, 35))
        try {
            NotificationManagerCompat.from(ctx).notify(ONGOING_ID, b.build())
        } catch (_: SecurityException) {
        }
    }

    /** The phase-change alert. It uses the alarm category so it still rings while Do Not Disturb is on. */
    private fun alert(ctx: Context, title: String, text: String) {
        if (!Notify.allowed(ctx)) return
        val b = NotificationCompat.Builder(ctx, Notify.STUDY_ALERT)
            .setSmallIcon(R.drawable.ic_stat_campusmate)
            .setColor(ContextCompat.getColor(ctx, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notify.openIntent(ctx, "study"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
        try {
            NotificationManagerCompat.from(ctx).notify(ALERT_ID, b.build())
        } catch (_: SecurityException) {
        }
    }
}

/** Phase alarms and the buttons on the study notification. */
class StudyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            StudyTimer.ACTION_END -> StudyTimer.phaseEnded(context, intent.getLongExtra("ends_at", -1))
            StudyTimer.ACTION_PAUSE -> StudyTimer.pause(context)
            StudyTimer.ACTION_RESUME -> StudyTimer.resume(context)
            StudyTimer.ACTION_SKIP -> StudyTimer.skip(context)
            StudyTimer.ACTION_STOP -> StudyTimer.stop(context)
        }
    }
}
