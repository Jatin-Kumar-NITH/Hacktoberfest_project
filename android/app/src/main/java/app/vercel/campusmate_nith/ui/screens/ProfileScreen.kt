package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.YEAR_NAMES
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.SectionTitle

/** Year / section / group, like the pickers in the website's sidebar. */
@Composable
fun ProfileScreen(vm: AppViewModel, firstRun: Boolean, onDone: () -> Unit) {
    var year by remember { mutableIntStateOf(vm.year) }
    var section by remember { mutableStateOf(vm.section) }
    var group by remember { mutableStateOf(vm.group) }
    val sections = vm.timetable(year).sections
    val sec = sections.firstOrNull { it.id == section } ?: sections.firstOrNull()
    Column(
        Modifier.fillMaxSize().background(P.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (firstRun) "Welcome${vm.user?.name?.let { ", ${it.substringBefore(' ')}" } ?: ""} 👋" else "Your class",
            fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Muted("Pick your year, section and lab group so Zenkai shows your own timetable, notices and assignments. You can change this any time under More.")
        CmCard {
            SectionTitle("Year")
            ChipRow((1..4).map { it to (YEAR_NAMES[it] ?: "$it") }, year) {
                year = it; section = vm.sectionOf(it, section).id; group = ""
            }
        }
        CmCard {
            SectionTitle("Section")
            if (sections.isEmpty()) Muted("The timetable for this year hasn't been added yet.")
            ChipRow(sections.map { it.id to it.id }, sec?.id ?: section) { section = it; group = "" }
            sec?.let { if (it.branches.isNotEmpty()) Muted("${it.name} · ${it.branches}", size = 13) }
        }
        if (sec != null && sec.groups.isNotEmpty()) {
            CmCard {
                SectionTitle("Lab group")
                ChipRow(listOf("" to "All groups") + sec.groups.map { it.id to it.id }, group) { group = it }
                sec.groups.firstOrNull { it.id == group }?.let { Muted("Roll numbers ${it.rolls}", size = 13) }
            }
        }
        PrimaryButton(if (firstRun) "Continue" else "Save", {
            vm.setProfile(year, sec?.id ?: section, group); onDone()
        })
    }
}
