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
import app.vercel.campusmate_nith.background.Notify
import app.vercel.campusmate_nith.ui.CampusMateApp
import app.vercel.campusmate_nith.ui.CampusMateTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as CampusMateApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) takeRoute(intent)
        setContent {
            val dark = when (app.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            CampusMateTheme(dark) { CampusMateApp() }
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
