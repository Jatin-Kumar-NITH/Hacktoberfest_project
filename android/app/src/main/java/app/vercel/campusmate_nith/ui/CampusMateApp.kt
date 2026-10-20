package app.vercel.campusmate_nith.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.vercel.campusmate_nith.CampusMateApplication
import app.vercel.campusmate_nith.background.Notify
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.vercel.campusmate_nith.ui.screens.AdminsScreen
import app.vercel.campusmate_nith.ui.screens.JournalistsScreen
import app.vercel.campusmate_nith.ui.screens.ResultsScreen
import app.vercel.campusmate_nith.ui.screens.UpdatesScreen
import app.vercel.campusmate_nith.ui.screens.AskScreen
import app.vercel.campusmate_nith.ui.screens.AssignmentsScreen
import app.vercel.campusmate_nith.ui.screens.AttendanceScreen
import app.vercel.campusmate_nith.ui.screens.BoardScreen
import app.vercel.campusmate_nith.ui.screens.CalendarScreen
import app.vercel.campusmate_nith.ui.screens.ChatScreen
import app.vercel.campusmate_nith.ui.screens.ExamsScreen
import app.vercel.campusmate_nith.ui.screens.ExplainScreen
import app.vercel.campusmate_nith.ui.screens.HomeScreen
import app.vercel.campusmate_nith.ui.screens.LoginScreen
import app.vercel.campusmate_nith.ui.screens.MessScreen
import app.vercel.campusmate_nith.ui.screens.MoreScreen
import app.vercel.campusmate_nith.ui.screens.PlannerScreen
import app.vercel.campusmate_nith.ui.screens.ProfileScreen
import app.vercel.campusmate_nith.ui.screens.PublishScreen
import app.vercel.campusmate_nith.ui.screens.SettingsScreen
import app.vercel.campusmate_nith.ui.screens.SetupScreen
import app.vercel.campusmate_nith.ui.screens.TutorialScreen
import app.vercel.campusmate_nith.ui.screens.SyllabusScreen
import app.vercel.campusmate_nith.ui.screens.TimetableScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("timetable", "Timetable", Icons.Outlined.CalendarViewWeek),
    Tab("board", "Notices", Icons.Outlined.Campaign),
    Tab("chat", "Chat", Icons.AutoMirrored.Outlined.Chat),
    Tab("more", "More", Icons.Outlined.GridView),
)

/** Navigation shared by every screen. */
class Nav(private val c: NavHostController) {
    fun go(route: String) = c.navigate(route) { launchSingleTop = true }
    fun back() { c.popBackStack() }
    fun tab(route: String) = c.navigate(route) {
        popUpTo(c.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun CampusMateApp(vm: AppViewModel = viewModel()) {
    when (vm.auth) {
        AuthState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AuthState.SignedOut -> LoginScreen(vm)
        AuthState.SignedIn -> when {
            !vm.profileChosen -> SetupScreen(vm, onDone = {})
            !vm.tutorialSeen -> TutorialScreen(onDone = { vm.changeTutorialSeen(true) })
            else -> Main(vm)
        }
    }
}

@Composable
private fun Main(vm: AppViewModel) {
    val controller = rememberNavController()
    val nav = Nav(controller)
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CampusMateApplication

    // a notification, app shortcut or the widget asked for a screen
    val pending = app.pendingRoute
    LaunchedEffect(pending) {
        if (pending != null) {
            app.pendingRoute = null
            if (TABS.any { it.route == pending }) nav.tab(pending) else nav.go(pending)
        }
    }

    // ask once for notifications (Android 13+), so reminders and new-notice alerts can show
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !vm.store.askedNotifications && !Notify.allowed(ctx)) {
            vm.store.askedNotifications = true
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val bottomBar = @Composable {
        if (TABS.any { it.route == route }) {
            NavigationBar(containerColor = P.card, tonalElevation = 0.dp) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, onClick = { nav.tab(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = P.accentSoft, selectedIconColor = P.accent, selectedTextColor = P.accent),
                    )
                }
            }
        }
    }
    Scaffold(bottomBar = bottomBar, containerColor = P.bg) { pad ->
        NavHost(controller, startDestination = "home", modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("timetable") { TimetableScreen(vm, nav) }
            composable("board") { BoardScreen(vm, nav) }
            composable("chat") { ChatScreen(vm, nav) }
            composable("more") { MoreScreen(vm, nav) }
            composable("assignments") { AssignmentsScreen(vm, nav) }
            composable("exams") { ExamsScreen(vm, nav) }
            composable("calendar") { CalendarScreen(vm, nav) }
            composable("attendance") { AttendanceScreen(vm, nav) }
            composable("syllabus") { SyllabusScreen(vm, nav) }
            composable("explain") { ExplainScreen(vm, nav) }
            composable("planner") { PlannerScreen(vm, nav) }
            composable("mess") { MessScreen(vm, nav) }
            composable("ask") { AskScreen(vm, nav) }
            composable("publish") { PublishScreen(vm, nav) }
            composable("admins") { AdminsScreen(vm, nav) }
            composable("journalists") { JournalistsScreen(vm, nav) }
            composable("college-updates") { UpdatesScreen(vm, nav, "college") }
            composable("hostel-updates") { UpdatesScreen(vm, nav, "hostel") }
            composable("results") { ResultsScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("profile") { ProfileScreen(vm, firstRun = false, onDone = { nav.back() }) }
        }
    }
}
