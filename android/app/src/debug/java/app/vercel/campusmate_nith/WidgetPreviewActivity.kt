package app.vercel.campusmate_nith

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.vercel.campusmate_nith.background.TodayWidget
import app.vercel.campusmate_nith.data.CampusResponse
import app.vercel.campusmate_nith.data.json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Temporary: draws the home-screen widget at a few times of day, light or dark, for screenshots. */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val ctx = createConfigurationContext(config)
        val campus = json.decodeFromString(CampusResponse.serializer(), assets.open("preview_campus.json").bufferedReader().readText()).campus
        val dp = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (dark) Color.BLACK else Color.parseColor("#E5E5EA"))
            setPadding((20 * dp).toInt(), (48 * dp).toInt(), (20 * dp).toInt(), 0)
        }
        val day = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
        listOf(LocalTime.of(10, 20), LocalTime.of(13, 5), LocalTime.of(21, 40)).forEach { t ->
            col.addView(TextView(this).apply {
                text = "Tuesday ${t.hour % 12}:${"%02d".format(t.minute)} ${if (t.hour >= 12) "PM" else "AM"} · boys' hostel, 1st year"
                setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
                setPadding(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
            })
            val frame = FrameLayout(ctx)
            val (views, _) = TodayWidget.render(ctx, campus, 1, "", "", "boys", day.atTime(t))
            frame.addView(views.apply(ctx, frame))
            col.addView(frame, LinearLayout.LayoutParams((350 * dp).toInt(), (172 * dp).toInt()))
        }
        setContentView(col)
    }
}
