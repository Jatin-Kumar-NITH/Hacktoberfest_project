package app.vercel.campusmate_nith.background

import android.content.Context
import app.vercel.campusmate_nith.data.Campus
import app.vercel.campusmate_nith.data.Store
import app.vercel.campusmate_nith.data.assignmentItems
import app.vercel.campusmate_nith.data.boardItems
import app.vercel.campusmate_nith.data.branchOptions
import app.vercel.campusmate_nith.data.dmy
import app.vercel.campusmate_nith.data.forStudent
import app.vercel.campusmate_nith.data.todayIso

/** Something new for the student: a notice, an assignment or an exam on their schedule. */
data class Update(val id: String, val kind: String, val title: String, val text: String, val route: String)

object Updates {
    /** Everything the student can currently see, keyed by a stable id. */
    fun current(c: Campus, year: Int, section: String, examBranch: String = ""): List<Update> {
        val out = mutableListOf<Update>()
        boardItems(c).filter { forStudent(it, year, section) }.forEach {
            val kind = if (it.kind == "announcement") "Announcement" else "Notice"
            out += Update("n:${it.id}", kind, it.title, it.from.ifEmpty { dmy(it.date) }, "board")
        }
        assignmentItems(c).filter { forStudent(it, year, section) }.forEach {
            val due = it.due?.let { d -> " · due ${dmy(d)}" }.orEmpty()
            out += Update("a:${it.id}", "Assignment", "${it.code}: ${it.title}", "Assignment$due", "assignments")
        }
        val today = todayIso()
        val ex = c.exams.years[year.toString()]
        val branch = examBranch.takeIf { ex != null && it in ex.branchOptions() }.orEmpty()
        ex?.rows.orEmpty().filter { forStudent(it, section, branch) }.forEach {
            if (it.date >= today) {
                out += Update("e:${it.date}|${it.time}|${it.code}", "Exam", "${it.code} exam on ${dmy(it.date)}",
                    listOf(it.time, it.venue).filter { s -> s.isNotBlank() }.joinToString(" · ").ifEmpty { "Exam schedule" }, "exams")
            }
        }
        return out
    }

    /**
     * Returns the items the student hasn't seen yet and remembers them as seen.
     * The first run (or a change of section) only records what is there.
     */
    fun takeNew(store: Store, c: Campus): List<Update> {
        val all = current(c, store.year, store.section, store.examBranch)
        val seen = store.seenIds
        store.seenIds = all.map { it.id }.toSet()
        if (seen == null) return emptyList()
        return all.filter { it.id !in seen }
    }

    fun notify(context: Context, news: List<Update>) {
        if (news.isEmpty()) return
        if (news.size <= 3) {
            news.forEach { u ->
                Notify.post(context, u.id.hashCode(), Notify.UPDATES, "New ${u.kind.lowercase()}: ${u.title}", u.text, u.route)
            }
        } else {
            val route = news.groupingBy { it.route }.eachCount().maxByOrNull { it.value }?.key ?: "home"
            Notify.post(
                context, 1001, Notify.UPDATES, "${news.size} new updates for your class",
                news.joinToString(", ") { it.title }, route,
                lines = news.map { "${it.kind}: ${it.title}" },
            )
        }
    }
}
