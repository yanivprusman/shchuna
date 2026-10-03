package com.automatelinux.whereAmI.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.whereAmI.data.model.Cello
import com.automatelinux.whereAmI.data.model.Locality
import com.automatelinux.whereAmI.data.model.ParkingSession
import com.automatelinux.whereAmI.data.model.Where
import com.automatelinux.whereAmI.data.model.Zone

/** Everything the screen draws. Built by the Android ViewModel. */
data class ScreenState(
    val needsPermission: Boolean = false,
    val locationOff: Boolean = false,
    val accuracyM: Int? = null,
    val where: Where? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val sessions: List<ParkingSession> = emptyList(),
    val parkingBusy: Boolean = false,
    val parkingMessage: String? = null,
    val confirmation: Confirmation? = null,
)

/** Cello showed a warning before parking; it needs the driver's yes. */
data class Confirmation(val zoneId: Int, val message: String, val code: String)

class Actions(
    val refresh: () -> Unit,
    val grantPermission: () -> Unit,
    val startParking: (zoneId: Int) -> Unit,
    val confirmParking: (Confirmation) -> Unit,
    val dismissConfirmation: () -> Unit,
    val stopParking: () -> Unit,
)

private val Ink = Color(0xFF14202B)
private val Muted = Color(0xFF5B6B78)
private val Line = Color(0xFFE3E8EC)
private val Page = Color(0xFFF5F7F8)
private val Cello = Color(0xFF1F8A8A)
private val CelloSoft = Color(0xFFE4F3F2)
private val Stop = Color(0xFFB3261E)

@Composable
fun WhereScreen(state: ScreenState, actions: Actions) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(Modifier.fillMaxSize(), color = Page) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header(state, actions)
                when {
                    state.needsPermission -> Notice(
                        "כדי לדעת איפה אתה, האפליקציה צריכה גישה למיקום.",
                        "אפשר גישה למיקום", "grant-location", actions.grantPermission,
                    )
                    state.locationOff -> Notice("שירותי המיקום בטלפון כבויים. הפעל אותם ונסה שוב.", "נסה שוב", "retry-location", actions.refresh)
                    state.where == null && state.error != null -> Notice(state.error, "נסה שוב", "retry", actions.refresh)
                    state.where == null -> Waiting()
                    else -> {
                        state.error?.let { Text(it, color = Stop, fontSize = 13.sp) }
                        PlaceCard(state.where)
                        ParkingCard(state, actions)
                        state.where.locality?.let { TownCard(it) }
                    }
                }
            }
        }
        state.confirmation?.let { c ->
            AlertDialog(
                onDismissRequest = actions.dismissConfirmation,
                title = { Text("Cello מבקש אישור") },
                text = { Text(c.message) },
                confirmButton = {
                    TextButton(onClick = { actions.confirmParking(c) }, modifier = Modifier.testTag("confirm-parking")) { Text("אשר והתחל חניה") }
                },
                dismissButton = {
                    TextButton(onClick = actions.dismissConfirmation, modifier = Modifier.testTag("cancel-parking")) { Text("ביטול") }
                },
            )
        }
    }
}

@Composable
private fun Header(state: ScreenState, actions: Actions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("איפה אני", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            val sub = when {
                state.accuracyM != null -> "מיקום מדויק עד כ-${state.accuracyM} מ׳"
                else -> "מחפש מיקום…"
            }
            Text(sub, fontSize = 13.sp, color = Muted)
        }
        if (state.loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Cello)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = actions.refresh, modifier = Modifier.testTag("refresh")) {
            Icon(Icons.Filled.Refresh, contentDescription = "רענן", tint = Ink)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun CardTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, color = Ink, fontSize = 14.sp)
    }
}

@Composable
private fun PlaceCard(where: Where) {
    val a = where.address
    Card {
        CardTitle(Icons.Filled.MyLocation, "המקום")
        val streetLine = listOfNotNull(a.street, a.houseNumber).joinToString(" ")
        Text(
            a.neighborhood ?: a.city ?: "מקום לא מזוהה",
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 32.sp,
        )
        val second = listOfNotNull(streetLine.ifBlank { null }, a.city.takeIf { a.neighborhood != null }).joinToString(", ")
        if (second.isNotBlank()) Text(second, fontSize = 17.sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(4.dp))
        Fact("מקום", a.place ?: where.landmarks.firstOrNull())
        Fact("רובע", a.quarter)
        val otherHoods = where.neighborhoods.filter { it != a.neighborhood && it != a.quarter }
        Fact("גם", otherHoods.joinToString(", ").ifBlank { null })
        Fact("נפה", a.subdistrict)
        Fact("מחוז", a.district)
        Fact("מיקוד", a.postcode)
        if (where.industrial == true) Fact("שימוש קרקע", "אזור תעשייה")
        where.parkingArea?.let { Fact("אזור חניה", "אזור $it (מפת העירייה)") }
    }
}

@Composable
private fun ParkingCard(state: ScreenState, actions: Actions) {
    val cello = state.where?.cello ?: return
    var showAll by remember { mutableStateOf(false) }
    Card {
        CardTitle(Icons.Filled.LocalParking, "מה לבחור ב-Cello")
        val active = state.sessions.firstOrNull()
        when (cello) {
            is Cello.NoCity -> Text(cello.message, fontSize = 16.sp, color = Ink)
            is Cello.One -> {
                Text("עיר: ${cello.city}", fontSize = 15.sp, color = Muted)
                ZoneChoice(cello.zone, recommended = true, enabled = active == null && !state.parkingBusy) { actions.startParking(cello.zone.id) }
            }
            is Cello.Several -> {
                Text("עיר: ${cello.city}", fontSize = 15.sp, color = Muted)
                Text(cello.message, fontSize = 14.sp, color = Ink)
                cello.candidates.forEach { z ->
                    ZoneChoice(z, recommended = false, enabled = active == null && !state.parkingBusy) { actions.startParking(z.id) }
                }
            }
            is Cello.None -> {
                Text("עיר: ${cello.city}", fontSize = 15.sp, color = Muted)
                Text(cello.message, fontSize = 14.sp, color = Ink)
                showAll = true
            }
        }

        if (active != null) {
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CelloSoft).padding(12.dp),
            ) {
                Text("חניה פעילה", fontWeight = FontWeight.Bold, color = Cello)
                Text(listOfNotNull(active.zoneName, active.startedAt?.let { "מ-${it.substringAfter('T').take(5)}" }).joinToString(" · "), color = Ink, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = actions.stopParking,
                    enabled = !state.parkingBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Stop),
                    modifier = Modifier.fillMaxWidth().testTag("stop-parking"),
                ) { Text("עצור חניה") }
            }
        }
        state.parkingMessage?.let { Text(it, fontSize = 14.sp, color = Ink) }

        val others = when (cello) {
            is Cello.One -> cello.others
            is Cello.Several -> cello.others
            is Cello.None -> cello.others
            is Cello.NoCity -> emptyList()
        }
        if (others.isNotEmpty()) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.testTag("toggle-other-zones")) {
                Text(if (showAll) "הסתר את שאר האזורים" else "שאר האזורים בעיר (${others.size})", color = Cello)
            }
            if (showAll) others.forEach { z ->
                ZoneChoice(z, recommended = false, enabled = active == null && !state.parkingBusy) { actions.startParking(z.id) }
            }
        }
    }
}

@Composable
private fun ZoneChoice(zone: Zone, recommended: Boolean, enabled: Boolean, onStart: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (recommended) CelloSoft else Color.Transparent)
            .border(1.dp, if (recommended) Cello else Line, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(zone.name, fontSize = if (recommended) 20.sp else 15.sp, fontWeight = if (recommended) FontWeight.Bold else FontWeight.Normal, color = Ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
        zone.reason?.let { Text(it, fontSize = 13.sp, color = Muted) }
        Spacer(Modifier.height(8.dp))
        if (recommended) {
            Button(
                onClick = { asking = true },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Cello),
                modifier = Modifier.fillMaxWidth().testTag("start-parking-${zone.id}"),
            ) { Text("התחל חניה כאן") }
        } else {
            OutlinedButton(
                onClick = { asking = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("start-parking-${zone.id}"),
            ) { Text("התחל חניה באזור הזה", color = Cello) }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("להתחיל חניה?") },
            text = { Text("החניה תחויב בחשבון ה-Cello שלך, באזור:\n${zone.name}") },
            confirmButton = {
                TextButton(onClick = { asking = false; onStart() }, modifier = Modifier.testTag("confirm-start-${zone.id}")) { Text("התחל") }
            },
            dismissButton = { TextButton(onClick = { asking = false }, modifier = Modifier.testTag("cancel-start-${zone.id}")) { Text("ביטול") } },
        )
    }
}

@Composable
private fun TownCard(locality: Locality) {
    Card {
        CardTitle(Icons.Filled.LocationCity, "על ${locality.name}")
        Text("${formatThousands(locality.population)} תושבים", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Fact("נפה", locality.subdistrict)
        Fact("מועצה", locality.regionalCouncil)
        Spacer(Modifier.height(6.dp))
        val max = locality.ages.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        locality.ages.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                // LTR isolate: in an RTL row "0–5" would otherwise render as "5–0".
                Text("\u2066${band.label}\u2069", fontSize = 13.sp, color = Muted, modifier = Modifier.width(52.dp))
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(band.count.toFloat() / max)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Cello.copy(alpha = 0.75f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${percent(band.count, locality.population)}%", fontSize = 13.sp, color = Ink)
            }
        }
        Text("מקור: ${locality.source}", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun Notice(text: String, button: String, tag: String, onClick: () -> Unit) {
    Card {
        Text(text, fontSize = 16.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Cello), modifier = Modifier.testTag(tag)) { Text(button) }
    }
}

@Composable
private fun Waiting() {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Cello)
            Spacer(Modifier.height(12.dp))
            Text("מאתר את המיקום שלך…", color = Muted)
        }
    }
}

private fun formatThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

private fun percent(part: Int, whole: Int): Int = if (whole <= 0) 0 else (part * 100.0 / whole).let { kotlin.math.round(it).toInt() }
