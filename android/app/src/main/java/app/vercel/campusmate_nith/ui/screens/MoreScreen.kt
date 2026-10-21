package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.BuildConfig
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.RoleTag
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.UpdateBanner

private data class Entry(val route: String, val label: String, val hint: String, val icon: ImageVector)

@Composable
fun MoreScreen(vm: AppViewModel, nav: Nav) {
    val u = vm.user
    val study = listOf(
        Entry("assignments", "Assignments", "Deadlines and PDFs", Icons.Outlined.Assignment),
        Entry("exams", "Exams", "Your exam schedule", Icons.Outlined.EventNote),
        Entry("calendar", "Academic calendar", "Holidays and important dates", Icons.Outlined.Event),
        Entry("attendance", "My attendance", "Track and plan your 75%", Icons.Outlined.CheckCircle),
        Entry("syllabus", "Syllabus", "Units, credits and PDFs", Icons.AutoMirrored.Outlined.MenuBook),
        Entry("mess", "Mess menu", "What's cooking this week", Icons.Outlined.Restaurant),
        Entry("results", "Results", "SGPA and CGPA by roll number", Icons.Outlined.Grade),
    )
    val news = listOf(
        Entry("college-updates", "College updates", "News from around the college", Icons.Outlined.Newspaper),
        Entry("hostel-updates", "Hostel updates", "News for your hostel and year", Icons.Outlined.Apartment),
    )
    val ai = listOf(
        Entry("ask", "Ask Zenkai", "Questions about your campus data", Icons.Outlined.AutoAwesome),
        Entry("explain", "Notice explainer", "Deadlines and a checklist from any notice", Icons.Outlined.Description),
        Entry("planner", "Study planner", "A day-by-day plan for your exams", Icons.Outlined.School),
        Entry("study", "Study mode", "Pomodoro timer, phone on Do Not Disturb", Icons.Outlined.Timer),
    )
    val admin = buildList {
        if (vm.isAdmin) add(Entry("publish", "Publish", "Notices, announcements, assignments, material", Icons.Outlined.Publish))
        if (vm.isAdmin) add(Entry("journalists", "Journalists", "Choose who can post updates", Icons.Outlined.HistoryEdu))
        if (u?.isOwner == true) add(Entry("admins", "Manage admins", "Give or remove admin and co-owner rights", Icons.Outlined.AdminPanelSettings))
    }
    Screen("More", vm) {
        item { UpdateBanner(vm) }
        item {
            CmCard(onClick = { nav.go("profile") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(u?.name.orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    RoleTag(u?.role ?: "user")
                }
                Muted(u?.email.orEmpty(), size = 13)
                Text("${vm.myLabel} · change", color = P.accent, fontSize = 14.sp)
            }
        }
        item { Group("Campus", study, nav) }
        item { Group("Updates", news, nav) }
        item { Group("AI tools", ai, nav) }
        if (admin.isNotEmpty()) item { Group("Admin", admin, nav) }
        item { Group("App", listOf(Entry("settings", "Settings", "Theme, notifications, widget, updates", Icons.Outlined.Settings)), nav) }
        item {
            CmCard(onClick = { vm.logout() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = P.red)
                    Text("Sign out", color = P.red, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(4.dp)) {
                Icon(Icons.Outlined.Info, null, tint = P.muted, modifier = Modifier.size(16.dp))
                Muted("Zenkai ${BuildConfig.VERSION_NAME} · Built for NIT Hamirpur", size = 12)
            }
        }
    }
}

@Composable
private fun Group(title: String, entries: List<Entry>, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Muted(title, size = 13)
        CmCard {
            entries.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = P.line)
                Row(
                    Modifier.fillMaxWidth().clickable { nav.go(e.route) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(e.icon, null, tint = P.accent)
                    Column(Modifier.weight(1f)) {
                        Text(e.label, fontWeight = FontWeight.SemiBold)
                        Muted(e.hint, size = 12)
                    }
                }
            }
        }
    }
}
