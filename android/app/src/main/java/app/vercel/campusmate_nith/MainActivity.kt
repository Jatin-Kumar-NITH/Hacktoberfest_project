package app.vercel.campusmate_nith

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vercel.campusmate_nith.background.Notify
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.AuthState
import app.vercel.campusmate_nith.ui.CampusMateApp
import app.vercel.campusmate_nith.ui.CampusMateTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as CampusMateApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        // fade the launch screen into the app (or into the loading screen, which looks the same)
        installSplashScreen().setOnExitAnimationListener { splash ->
            splash.view.animate().alpha(0f).setDuration(200L).withEndAction { splash.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) takeRoute(intent)
        setContent {
            val vm: AppViewModel = viewModel()
            val dark = when (app.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // the loading screen is black in both themes, so it needs light bar icons
            val light = !dark && vm.auth != AuthState.Checking
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            CampusMateTheme(dark) { CampusMateApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeRoute(intent)
    }

    private fun takeRoute(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it in Notify.ROUTES }?.let { app.pendingRoute = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
