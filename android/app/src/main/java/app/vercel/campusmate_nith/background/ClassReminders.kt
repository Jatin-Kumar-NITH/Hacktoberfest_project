package app.vercel.campusmate_nith.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.data.Campus
import app.vercel.campusmate_nith.data.ClassItem
import app.vercel.campusmate_nith.data.Section
import app.vercel.campusmate_nith.data.Store
import app.vercel.campusmate_nith.data.dayClasses
import app.vercel.campusmate_nith.data.dayName
import app.vercel.campusmate_nith.data.hh
import app.vercel.campusmate_nith.data.forYear
import app.vercel.campusmate_nith.data.holidayOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The student's section in the cached timetable, or null if there is no data yet. */
fun studentSection(c: Campus, store: Store): Section? {
    val secs = c.timetables[store.year.toString()]?.sections.orEmpty()
    return secs.firstOrNull { it.id == store.section } ?: secs.firstOrNull()
}

/** Real classes (not free periods) on a date, or none on a holiday. */
fun classesOn(c: Campus, sec: Section, group: String, date: LocalDate, year: Int): List<ClassItem> {
    if (holidayOn(c.calendar.forYear(year), date.toString()) != null) return emptyList()
    return dayClasses(sec, dayName(date), group).filter { it.type != "Free" }
}

/** One alarm at a time: a few minutes before the next class, then the one after. */
object ClassReminders {
    private const val REQUEST = 7

    data class Next(val at: LocalDateTime, val cls: ClassItem, val room: String)

    fun next(c: Campus, store: Store, from: LocalDateTime): Next? {
        val sec = studentSection(c, store) ?: return null
        for (i in 0..7) {
            val date = from.toLocalDate().plusDays(i.toLong())
            for (cls in classesOn(c, sec, store.group, date, store.year)) {
                val at = date.atTime(cls.start.coerceIn(0, 23), 0).minusMinutes(store.classLead.toLong())
                if (at.isAfter(from)) return Next(at, cls, cls.room.ifEmpty { if (cls.type == "Lab") "" else sec.classroom })
            }
        }
        return null
    }

    fun schedule(context: Context, campus: Campus?) {
        val app = context.applicationContext as CampusMateApplication
        val store = app.store
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, null)
        am.cancel(pi)
        if (campus == null || !store.notifyClasses) return
        val n = next(campus, store, LocalDateTime.now().plusSeconds(30)) ?: return
        val millis = n.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // a two-minute window needs no exact-alarm permission and still lands on time
        am.setWindow(AlarmManager.RTC_WAKEUP, millis, 2 * 60_000L, pending(context, n))
    }

    private fun pending(context: Context, n: Next?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        if (n != null) {
            val what = listOf(n.cls.label, n.cls.type).filter { it.isNotBlank() }.joinToString(" · ")
            intent.putExtra("title", "${n.cls.code} at ${hh(n.cls.start)}")
            intent.putExtra("text", listOf(what, n.room.takeIf { it.isNotBlank() }?.let { "Room $it" }, n.cls.faculty)
                .filter { !it.isNullOrBlank() }.joinToString(" · "))
        }
        return PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CampusMateApplication
        val title = intent.getStringExtra("title")
        if (title != null && app.store.notifyClasses) {
            Notify.post(context, 2001, Notify.CLASSES, title, intent.getStringExtra("text").orEmpty(), "timetable")
        }
        val pending = goAsync()
        app.background {
            try {
                ClassReminders.schedule(context, app.store.campusCache?.campus)
                TodayWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are forgotten when the phone restarts or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val app = context.applicationContext as CampusMateApplication
                val pending = goAsync()
                app.background {
                    try {
                        ClassReminders.schedule(context, app.store.campusCache?.campus)
                        TodayWidget.refresh(context)
                        StudyTimer.restore(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
