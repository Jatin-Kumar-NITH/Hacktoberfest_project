package app.vercel.campusmate_nith.data

import java.time.LocalDate

/** When a meal is served, in minutes after midnight. */
data class MealWindow(val start: Int, val end: Int)

/** The meal to show right now, with whether it's being served, comes next today, or is tomorrow's first. */
data class MealNow(val meal: String, val dishes: String, val timing: String, val window: MealWindow, val state: State) {
    enum class State { NOW, NEXT, TOMORROW }
}

/** Used when the mess hasn't given a time for a meal. */
private val DEFAULT_WINDOWS = mapOf(
    "Breakfast" to MealWindow(7 * 60 + 30, 9 * 60 + 30),
    "Lunch" to MealWindow(12 * 60 + 30, 14 * 60 + 30),
    "Snacks" to MealWindow(17 * 60, 18 * 60 + 30),
    "Dinner" to MealWindow(19 * 60 + 30, 21 * 60 + 30),
)

private val CLOCK = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?""")

/** Reads a mess timing such as "7:30–9:00 AM", "12:30 - 2 PM" or "11:30 AM to 1:00 PM". Null if it can't. */
fun parseMealTime(text: String): MealWindow? {
    val parts = text.lowercase().replace("a.m.", "am").replace("p.m.", "pm")
        .split(Regex("""\s*(?:–|—|-|\bto\b)\s*""")).filter { it.isNotBlank() }
    if (parts.size != 2) return null
    val a = CLOCK.find(parts[0]) ?: return null
    val b = CLOCK.find(parts[1]) ?: return null
    fun minutes(m: MatchResult, ampm: String?): Int? {
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (h > 23 || min > 59) return null
        return when (ampm) {
            "am" -> (h % 12) * 60 + min
            "pm" -> (h % 12 + 12) * 60 + min
            else -> h * 60 + min
        }
    }
    val endAmpm = b.groupValues[3].ifEmpty { null }
    val end = minutes(b, endAmpm) ?: return null
    val ownAmpm = a.groupValues[3].ifEmpty { null }
    var start = minutes(a, ownAmpm ?: endAmpm) ?: return null
    // "11:30–1:00 PM": the start is in the morning
    if (ownAmpm == null && endAmpm == "pm" && start > end) start = minutes(a, "am") ?: return null
    return if (start < end) MealWindow(start, end) else null
}

/**
 * The meal for [now] (minutes after midnight on [date]): the one being served, else the next one
 * today, else tomorrow's first. A meal stays until its time ends, so after breakfast it moves on
 * to lunch. Meals with no dishes are skipped; null when the menu has none.
 */
fun currentMeal(h: Hostel, date: LocalDate, now: Int): MealNow? {
    for (offset in 0..1) {
        val day = dayName(date.plusDays(offset.toLong()))
        for (meal in MEALS) {
            val dishes = h.mess_menu[day]?.get(meal)?.trim().orEmpty()
            if (dishes.isEmpty()) continue
            val timing = h.timing(day, meal)
            val w = parseMealTime(timing) ?: DEFAULT_WINDOWS.getValue(meal)
            if (offset == 1) return MealNow(meal, dishes, timing, w, MealNow.State.TOMORROW)
            if (now < w.end) return MealNow(meal, dishes, timing, w, if (now >= w.start) MealNow.State.NOW else MealNow.State.NEXT)
        }
    }
    return null
}
