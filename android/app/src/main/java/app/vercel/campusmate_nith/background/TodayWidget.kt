package app.vercel.campusmate_nith.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.R
import app.vercel.campusmate_nith.data.ClassItem
import app.vercel.campusmate_nith.data.MealNow
import app.vercel.campusmate_nith.data.currentMeal
import app.vercel.campusmate_nith.data.dayName
import app.vercel.campusmate_nith.data.forYear
import app.vercel.campusmate_nith.data.holidayOn
import app.vercel.campusmate_nith.data.niceDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Home-screen widget: the rest of today's classes (or the next day that has some) and the meal
 * that's on at the mess right now, or the next one once its time is over.
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TICK) refresh(context) else super.onReceive(context, intent)
    }

    companion object {
        private const val TICK = "app.vercel.campusmate_nith.WIDGET_TICK"
        private val ROWS = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3)
        private val TIMES = intArrayOf(R.id.w_time0, R.id.w_time1, R.id.w_time2, R.id.w_time3)
        private val NAMES = intArrayOf(R.id.w_name0, R.id.w_name1, R.id.w_name2, R.id.w_name3)
        private val ROOMS = intArrayOf(R.id.w_room0, R.id.w_room1, R.id.w_room2, R.id.w_room3)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        /** "9:00", "2:00" — classes run in the day, so no AM/PM is needed. */
        private fun short(h: Int) = "${if (h > 12) h - 12 else h}:00"

        private fun clock(min: Int): String {
            val h = (min / 60) % 24
            return "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(min % 60)} ${if (h >= 12) "PM" else "AM"}"
        }

        private fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_today)
            v.setOnClickPendingIntent(R.id.widget_root, Notify.openIntent(context, "timetable"))
            v.setOnClickPendingIntent(R.id.w_meal, Notify.openIntent(context, "mess"))
            ROWS.forEach { v.setViewVisibility(it, View.GONE) }
            v.setViewVisibility(R.id.w_meal, View.GONE)

            val store = (context.applicationContext as CampusMateApplication).store
            val campus = store.campusCache?.campus
            val sec = campus?.let { studentSection(it, store) }
            if (campus == null || sec == null) {
                v.setTextViewText(R.id.w_title, "CampusMate")
                v.setTextViewText(R.id.w_sub, "")
                v.setTextViewText(R.id.w_empty, "Open CampusMate and sign in to see your classes here.")
                v.setViewVisibility(R.id.w_empty, View.VISIBLE)
                return v
            }

            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val nowMin = now.hour * 60 + now.minute

            // ---- meal: the student's hostel and year; hidden when that menu has no dishes
            val hostel = campus.hostel.hostels[store.hostel] ?: campus.hostel.hostels.values.firstOrNull()
            val meal = hostel?.forYear(store.year)?.let { currentMeal(it, today, nowMin) }
            if (meal != null) {
                v.setViewVisibility(R.id.w_meal, View.VISIBLE)
                v.setTextViewText(R.id.w_meal_name, meal.meal)
                val time = meal.timing.ifEmpty { null }
                v.setTextViewText(R.id.w_meal_when, when (meal.state) {
                    MealNow.State.NOW -> "Now · till ${clock(meal.window.end)}"
                    MealNow.State.NEXT -> "Next · ${time ?: clock(meal.window.start)}"
                    MealNow.State.TOMORROW -> "Tomorrow · ${time ?: clock(meal.window.start)}"
                })
                v.setTextViewText(R.id.w_meal_dishes, meal.dishes.replace(" + ", ", "))
            }

            // ---- classes: what's left today, else the next day with classes
            var day = today
            var list: List<ClassItem> = classesOn(campus, sec, store.group, today, store.year).filter { it.end > now.hour }
            val holiday = holidayOn(campus.calendar.forYear(store.year), today.toString())
            var note: String? = holiday?.let { "Holiday: ${it.title}" }
            if (list.isEmpty()) {
                for (i in 1..7) {
                    val d = today.plusDays(i.toLong())
                    val l = classesOn(campus, sec, store.group, d, store.year)
                    if (l.isNotEmpty()) { day = d; list = l; break }
                }
            }
            v.setTextViewText(R.id.w_title, when (day) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> dayName(day)
            })
            v.setTextViewText(R.id.w_sub, "${niceDate(day.toString())} · Sec ${sec.id}")
            if (list.isEmpty()) {
                v.setTextViewText(R.id.w_empty, note ?: "No classes this week.")
                v.setViewVisibility(R.id.w_empty, View.VISIBLE)
            } else {
                if (note == null && day != today) note = "Done for today"
                v.setTextViewText(R.id.w_empty, note.orEmpty())
                v.setViewVisibility(R.id.w_empty, if (note != null) View.VISIBLE else View.GONE)
                list.take(ROWS.size).forEachIndexed { i, c ->
                    val live = day == today && c.start <= now.hour && now.hour < c.end
                    v.setViewVisibility(ROWS[i], View.VISIBLE)
                    v.setTextViewText(TIMES[i], if (live) "Now" else short(c.start))
                    v.setTextColor(TIMES[i], context.getColor(if (live) R.color.accent else R.color.widget_muted))
                    v.setTextViewText(NAMES[i], c.code.ifEmpty { c.label }.ifEmpty { c.type })
                    v.setTextViewText(ROOMS[i], c.room.ifEmpty { sec.classroom })
                }
            }

            scheduleNext(context, now, meal)
            return v
        }

        /** Redraws at the next class hour or meal change, so "Now" and the meal stay right. */
        private fun scheduleNext(context: Context, now: LocalDateTime, meal: MealNow?) {
            var next = now.toLocalDate().atTime(LocalTime.of(now.hour, 0)).plusHours(1)
            if (meal != null && meal.state != MealNow.State.TOMORROW) {
                val change = if (meal.state == MealNow.State.NOW) meal.window.end else meal.window.start
                val at = now.toLocalDate().atStartOfDay().plusMinutes(change.toLong())
                if (at.isAfter(now) && at.isBefore(next)) next = at
            }
            val pi = PendingIntent.getBroadcast(
                context, 11, Intent(context, TodayWidget::class.java).setAction(TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // not a wake-up alarm: if the phone is asleep it redraws when the screen comes on
            context.getSystemService(AlarmManager::class.java)
                ?.setWindow(AlarmManager.RTC, millis, 2 * 60_000L, pi)
        }
    }
}
