package app.vercel.campusmate_nith.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vercel.campusmate_nith.data.ApiException
import app.vercel.campusmate_nith.data.Hostel
import app.vercel.campusmate_nith.data.MEALS
import app.vercel.campusmate_nith.data.WEEK7
import app.vercel.campusmate_nith.data.fmtTime
import app.vercel.campusmate_nith.data.json
import app.vercel.campusmate_nith.data.todayName
import app.vercel.campusmate_nith.ui.Alert
import app.vercel.campusmate_nith.ui.AlertKind
import app.vercel.campusmate_nith.ui.AppViewModel
import app.vercel.campusmate_nith.ui.ChipRow
import app.vercel.campusmate_nith.ui.CmCard
import app.vercel.campusmate_nith.ui.EmptyLine
import app.vercel.campusmate_nith.ui.Field
import app.vercel.campusmate_nith.ui.Muted
import app.vercel.campusmate_nith.ui.Nav
import app.vercel.campusmate_nith.ui.P
import app.vercel.campusmate_nith.ui.PrimaryButton
import app.vercel.campusmate_nith.ui.Screen
import app.vercel.campusmate_nith.ui.SectionTitle
import app.vercel.campusmate_nith.ui.SmallButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Composable
fun MessScreen(vm: AppViewModel, nav: Nav) {
    var editing by remember { mutableStateOf(false) }
    val hostels = vm.campus.hostel.hostels
    val h = vm.myHostel()
    if (editing) {
        MenuEditor(vm, vm.hostel, h, onClose = { editing = false })
        return
    }
    val empty = WEEK7.none { d -> MEALS.any { !h.mess_menu[d]?.get(it).isNullOrEmpty() } }
    val today = todayName()
    Screen("Mess menu", vm, onBack = { nav.back() }) {
        item { ChipRow(hostels.map { it.key to it.value.name }, vm.hostel) { vm.chooseHostel(it) } }
        item {
            Column {
                Text("${h.name} · weekly menu", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Muted("Today is $today")
                h.edited_by?.let { Muted("Last edited by $it · ${fmtTime(h.edited_at)}", size = 12) }
            }
        }
        if (vm.isAdmin) item { SmallButton("Edit this menu", { editing = true }) }
        if (MEALS.any { !h.mess_timings[it].isNullOrEmpty() }) item {
            CmCard {
                SectionTitle("Timings")
                MEALS.forEach { m -> h.mess_timings[m]?.takeIf { it.isNotEmpty() }?.let { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(m, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Muted(it) } } }
            }
        }
        if (empty) item { CmCard { EmptyLine("The menu for this hostel hasn't been added yet.") } }
        else items(WEEK7.sortedBy { if (it == today) 0 else 1 }) { d ->
            CmCard(border = if (d == today) P.accent else null) {
                Text(if (d == today) "$d · today" else d, fontWeight = FontWeight.SemiBold)
                MEALS.forEach { m ->
                    Column {
                        Muted(m, size = 12)
                        Text(h.mess_menu[d]?.get(m)?.ifEmpty { null } ?: "—", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditor(vm: AppViewModel, id: String, h: Hostel, onClose: () -> Unit) {
    val menu = remember { mutableStateMapOf<String, String>().apply { WEEK7.forEach { d -> MEALS.forEach { m -> put("$d|$m", h.mess_menu[d]?.get(m).orEmpty()) } } } }
    val times = remember { mutableStateMapOf<String, String>().apply { MEALS.forEach { put(it, h.mess_timings[it].orEmpty()) } } }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Screen("Edit · ${h.name}", vm, onBack = onClose) {
        item {
            CmCard {
                SectionTitle("Timings")
                MEALS.forEach { m -> Field(times[m].orEmpty(), { times[m] = it }, "$m, e.g. 7:30–9:30 AM", maxLength = 40) }
            }
        }
        items(WEEK7) { d ->
            CmCard {
                SectionTitle(d)
                MEALS.forEach { m -> Field(menu["$d|$m"].orEmpty(), { menu["$d|$m"] = it }, m, maxLength = 200) }
            }
        }
        error?.let { item { Alert(it, AlertKind.Error) } }
        item {
            PrimaryButton("Save menu", {
                scope.launch {
                    saving = true; error = null
                    try {
                        val body = buildJsonObject {
                            put("hostel", JsonPrimitive(id))
                            put("mess_menu", buildJsonObject { WEEK7.forEach { d -> put(d, buildJsonObject { MEALS.forEach { m -> put(m, JsonPrimitive(menu["$d|$m"].orEmpty())) } }) } })
                            put("mess_timings", buildJsonObject { MEALS.forEach { m -> put(m, JsonPrimitive(times[m].orEmpty())) } })
                        }
                        val res = vm.api.edit("/api/edit/menu", body.toString())
                        val saved = json.decodeFromJsonElement(Hostel.serializer(), res["hostel"]!!)
                        val c = vm.campus
                        vm.replaceCampus(c.copy(hostel = c.hostel.copy(hostels = c.hostel.hostels + (id to saved))))
                        onClose()
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        saving = false
                    }
                }
            }, loading = saving)
        }
        item { SmallButton("Cancel", onClose) }
    }
}
