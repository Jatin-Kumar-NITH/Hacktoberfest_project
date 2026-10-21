package app.vercel.campusmate_nith.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.R
import kotlinx.coroutines.delay

private val SplashBlack = Color.Black
private val SplashGold = Color(0xFFFFD60A)

/**
 * Shown while the app finds out who is signed in. It continues the system launch screen:
 * the bolt is drawn at the same size and place, then the name fades in under it.
 */
@Composable
fun LoadingScreen() {
    val name = remember { Animatable(0f) }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { name.animateTo(1f, tween(450)) }
    // only show a progress bar when loading takes a while, so quick starts don't flicker
    LaunchedEffect(Unit) { delay(700); slow = true }

    Box(Modifier.fillMaxSize().background(SplashBlack), contentAlignment = Alignment.Center) {
        // 288dp is the system launch screen's icon size for an icon without a background
        Image(painterResource(R.drawable.splash_bolt), contentDescription = null, modifier = Modifier.size(288.dp))
        Text(
            stringResource(R.string.app_name),
            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.offset(y = 100.dp).alpha(name.value),
        )
        AnimatedVisibility(slow, enter = fadeIn(tween(300)), modifier = Modifier.offset(y = 150.dp)) {
            LinearProgressIndicator(
                color = SplashGold, trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}
