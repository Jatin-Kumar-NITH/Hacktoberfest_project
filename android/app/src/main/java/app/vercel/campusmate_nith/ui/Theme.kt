package app.vercel.campusmate_nith.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colours from static/css/style.css, so the app looks like the website.

@Immutable
data class Palette(
    val bg: Color, val card: Color, val fill: Color, val fill2: Color,
    val ink: Color, val ink2: Color, val muted: Color, val line: Color,
    val accent: Color, val accentSoft: Color,
    val red: Color, val redSoft: Color, val orange: Color, val orangeSoft: Color,
    val green: Color, val greenSoft: Color, val indigo: Color,
    val gold: Color, val goldSoft: Color, val onGold: Color,
    val high: Color, val med: Color, val low: Color,
)

private val Light = Palette(
    bg = Color(0xFFF5F5F7), card = Color.White, fill = Color(0xFFF5F5F7), fill2 = Color(0xFFE8E8ED),
    ink = Color(0xFF1D1D1F), ink2 = Color(0xFF424245), muted = Color(0xFF6E6E73), line = Color(0xFFE5E5EA),
    accent = Color(0xFF0071E3), accentSoft = Color(0x170071E3),
    red = Color(0xFFD70015), redSoft = Color(0x1AFF3B30), orange = Color(0xFFB25000), orangeSoft = Color(0x1FFF9500),
    green = Color(0xFF248A3D), greenSoft = Color(0x1F34C759), indigo = Color(0xFF5856D6),
    gold = Color(0xFF8F6200), goldSoft = Color(0x24FFB800), onGold = Color.White,
    high = Color(0xFFFF3B30), med = Color(0xFFFF9500), low = Color(0xFF34C759),
)

private val Dark = Palette(
    bg = Color.Black, card = Color(0xFF1C1C1E), fill = Color(0xFF2C2C2E), fill2 = Color(0xFF3A3A3C),
    ink = Color(0xFFF5F5F7), ink2 = Color(0xFFD1D1D6), muted = Color(0xFF98989D), line = Color(0xFF38383A),
    accent = Color(0xFF0A84FF), accentSoft = Color(0x2E0A84FF),
    red = Color(0xFFFF6961), redSoft = Color(0x2EFF453A), orange = Color(0xFFFFB340), orangeSoft = Color(0x2EFF9F0A),
    green = Color(0xFF30D158), greenSoft = Color(0x2930D158), indigo = Color(0xFF7D7AFF),
    gold = Color(0xFFFFD60A), goldSoft = Color(0x24FFD60A), onGold = Color(0xFF1D1D1F),
    high = Color(0xFFFF453A), med = Color(0xFFFF9F0A), low = Color(0xFF30D158),
)

val LocalPalette = staticCompositionLocalOf { Light }

val P: Palette
    @Composable get() = LocalPalette.current

@Composable
fun CampusMateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, surfaceVariant = p.fill, onSurfaceVariant = p.muted,
        surfaceContainer = p.card, surfaceContainerHigh = p.fill, surfaceContainerLow = p.card,
        outline = p.line, outlineVariant = p.line, error = p.red, secondaryContainer = p.accentSoft, onSecondaryContainer = p.accent,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, surfaceVariant = p.fill, onSurfaceVariant = p.muted,
        surfaceContainer = p.card, surfaceContainerHigh = p.fill, surfaceContainerLow = p.card,
        outline = p.line, outlineVariant = p.line, error = p.red, secondaryContainer = p.accentSoft, onSecondaryContainer = p.accent,
    )
    val base = Typography()
    val type = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
