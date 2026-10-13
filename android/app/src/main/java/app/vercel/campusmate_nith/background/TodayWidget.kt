package app.vercel.campusmate_nith.background

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.R
import app.vercel.campusmate_nith.data.ClassItem
import app.vercel.campusmate_nith.data.dayName
import app.vercel.campusmate_nith.data.hh
import app.vercel.campusmate_nith.data.forYear
import app.vercel.campusmate_nith.data.holidayOn
import java.time.LocalDate
import java.time.LocalTime

/** Home-screen widget: the rest of today's classes, or the next day that has some. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private val ROWS = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4)
        private val TIMES = intArrayOf(R.id.w_time0, R.id.w_time1, R.id.w_time2, R.id.w_time3, R.id.w_time4)
        private val NAMES = intArrayOf(R.id.w_name0, R.id.w_name1, R.id.w_name2, R.id.w_name3, R.id.w_name4)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_today)
            v.setOnClickPendingIntent(R.id.widget_root, Notify.openIntent(context, "timetable"))
            ROWS.forEach { v.setViewVisibility(it, View.GONE) }

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
            v.setTextViewText(R.id.w_sub, "Section ${sec.id}")

            val today = LocalDate.now()
            val nowHour = LocalTime.now().hour
            var day = today
            var list: List<ClassItem> = classesOn(campus, sec, store.group, today, store.year).filter { it.end > nowHour }
            var note: String? = holidayOn(campus.calendar.forYear(store.year), today.toString())?.let { "Holiday today: ${it.title}" }
            if (list.isEmpty()) {
                for (i in 1..7) {
                    val d = today.plusDays(i.toLong())
                    val l = classesOn(campus, sec, store.group, d, store.year)
                    if (l.isNotEmpty()) { day = d; list = l; break }
                }
            }
            val title = when (day) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> dayName(day)
            }
            v.setTextViewText(R.id.w_title, title)
            if (list.isEmpty()) {
                v.setTextViewText(R.id.w_empty, note ?: "No classes this week.")
                v.setViewVisibility(R.id.w_empty, View.VISIBLE)
                return v
            }
            if (note == null && day != today) note = "No more classes today"
            v.setTextViewText(R.id.w_empty, note.orEmpty())
            v.setViewVisibility(R.id.w_empty, if (note != null) View.VISIBLE else View.GONE)
            list.take(ROWS.size).forEachIndexed { i, c ->
                v.setViewVisibility(ROWS[i], View.VISIBLE)
                v.setTextViewText(TIMES[i], hh(c.start))
                val room = c.room.ifEmpty { sec.classroom }
                v.setTextViewText(NAMES[i], listOf(c.code, c.label, room).filter { it.isNotBlank() }.joinToString(" · "))
            }
            return v
        }
    }
}
