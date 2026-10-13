package app.vercel.campusmate_nith.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton

@Composable
fun LoginScreen(vm: AppViewModel) {
    val activity = LocalContext.current as Activity
    Box(Modifier.fillMaxSize().background(P.bg).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(64.dp).background(P.ink, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("C", color = P.bg, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
            Muted("For students of NIT Hamirpur", size = 14)
            Spacer(Modifier.height(8.dp))
            Text("Your campus essentials.", fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 36.sp)
            Text("Effortlessly organized.", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = P.muted, textAlign = TextAlign.Center, lineHeight = 36.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "One calm place for your timetable, syllabus, assignments and campus notices, with an assistant that answers from your own campus data.",
                color = P.ink2, textAlign = TextAlign.Center, fontSize = 16.sp,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Continue with Google", { vm.signIn(activity) }, loading = vm.signingIn)
            Spacer(Modifier.height(8.dp))
            Muted("Sign in with your institutional Google account", size = 13)
            vm.signInError?.let {
                Spacer(Modifier.height(16.dp))
                // selectable, so the details can be copied and sent to whoever looks after the app
                androidx.compose.foundation.text.selection.SelectionContainer { Alert(it, AlertKind.Error) }
            }
            Spacer(Modifier.height(32.dp))
            listOf(
                "Timetable" to "Today's classes for your section and group.",
                "Notices" to "Every notice, explained in plain words.",
                "Assignments" to "Deadlines and exams, before they sneak up.",
                "Mess menu" to "What's cooking, meal by meal.",
            ).forEach { (t, d) ->
                CmCard(Modifier.padding(vertical = 4.dp)) {
                    Text(t, fontWeight = FontWeight.SemiBold)
                    Muted(d)
                }
            }
            Spacer(Modifier.height(16.dp))
            Muted("CampusMate AI · Built for NIT Hamirpur", size = 12)
        }
    }
}
