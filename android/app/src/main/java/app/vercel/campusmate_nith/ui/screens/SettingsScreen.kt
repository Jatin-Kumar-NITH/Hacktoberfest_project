package app.vercel.campusmate_nith.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.vercel.campusmate_nith.BuildConfig
import app.vercel.campusmate_nith.background.Notify
import app.vercel.campusmate_nith.background.TodayWidget
import app.vercel.campusmate_nith.ui.Actions
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.ConfirmDelete
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import app.vercel.campusmate_nith.ui.UpdateBanner
import app.vercel.campusmate_nith.ui.savedAt

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as app.vercel.campusmate_nith.CampusMateApplication
    var allowed by remember { mutableStateOf(Notify.allowed(ctx)) }
    LifecycleResumeEffect(Unit) {
        allowed = Notify.allowed(ctx)
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = Notify.allowed(ctx)
        if (!granted) openAppNotificationSettings(ctx)
    }
    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openAppNotificationSettings(ctx)
    }

    Screen("Settings", vm, onBack = { nav.back() }) {
        item { UpdateBanner(vm) }

        item {
            CmCard {
                SectionTitle("Appearance")
                ChipRow(listOf("system" to "Match phone", "light" to "Light", "dark" to "Dark"), app.themeMode) { vm.setTheme(it) }
            }
        }

        item {
            CmCard {
                SectionTitle("Notifications")
                if (!allowed && (vm.notifyNew || vm.notifyClasses)) {
                    Alert("Notifications are turned off for Zenkai on this phone.", AlertKind.Warn)
                    SmallButton("Allow notifications", { allowNotifications() })
                }
                ToggleRow("New notices, assignments and exams", "Checked about once an hour for your year and section", vm.notifyNew) {
                    vm.changeNotifyNew(it); if (it && !allowed) allowNotifications()
                }
                HorizontalDivider(color = P.line)
                ToggleRow("Class reminders", "A heads-up before each class on your timetable", vm.notifyClasses) {
                    vm.changeNotifyClasses(it); if (it && !allowed) allowNotifications()
                }
                if (vm.notifyClasses) {
                    Muted("Remind me", size = 13)
                    ChipRow(listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), vm.classLead) { vm.changeClassLead(it) }
                }
            }
        }

        item {
            CmCard {
                SectionTitle("Home-screen widget")
                Muted("Shows the rest of today's classes, or the next day that has some.")
                val manager = AppWidgetManager.getInstance(ctx)
                if (manager.isRequestPinAppWidgetSupported) {
                    SmallButton("Add widget to home screen", {
                        manager.requestPinAppWidget(ComponentName(ctx, TodayWidget::class.java), null, null)
                    })
                } else {
                    Muted("Long-press your home screen, choose Widgets, then Zenkai.", size = 13)
                }
            }
        }

        item {
            CmCard {
                SectionTitle("Your class")
                Text(vm.myLabel)
                SmallButton("Change year or section", { nav.go("profile") })
            }
        }

        item {
            CmCard {
                SectionTitle("Data")
                Muted(if (vm.lastSync > 0) "Last updated ${savedAt(vm.lastSync)}." else "Not updated yet.", size = 13)
                Muted("Explained notices, your study plan and assignment ticks are kept on this phone.", size = 13)
                Actions {
                    SmallButton("Refresh now", { vm.refresh() })
                    ConfirmDelete("Clear saved data") {
                        vm.clearLocalData()
                        Toast.makeText(ctx, "Cleared.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        item {
            CmCard {
                SectionTitle("About")
                Muted("Zenkai ${BuildConfig.VERSION_NAME}. Built for NIT Hamirpur.", size = 13)
                Actions {
                    SmallButton(if (vm.checkingUpdate) "Checking…" else "Check for updates", { vm.checkUpdate(manual = true) }, enabled = !vm.checkingUpdate)
                    SmallButton("Show the tour again", { vm.changeTutorialSeen(false) })
                    SmallButton("Open the website", {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.BASE_URL))) }
                    })
                }
            }
        }

        item {
            CmCard(onClick = { vm.logout() }) {
                Text("Sign out", color = P.red, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Muted(hint, size = 12)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = P.accent))
    }
}

private fun openAppNotificationSettings(ctx: android.content.Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
}
