package com.automatelinux.whereAmI.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.whereAmI.data.model.Cello
import com.automatelinux.whereAmI.data.model.Locality
import com.automatelinux.whereAmI.data.model.ParkingSession
import com.automatelinux.whereAmI.data.model.Where
import com.automatelinux.whereAmI.data.model.Zone
import com.automatelinux.whereAmI.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun WhereScreen(state: ScreenState, actions: Actions) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().background(Palette.Page)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TopBar(state, actions)
                when {
                    state.needsPermission -> Notice(
                        "כדי לדעת איפה אתה, האפליקציה צריכה גישה למיקום.",
                        "אפשר גישה למיקום", "grant-location", actions.grantPermission,
                    )
                    state.locationOff -> Notice("שירותי המיקום בטלפון כבויים. הפעל אותם ונסה שוב.", "נסה שוב", "retry-location", actions.refresh)
                    state.where == null && state.error != null -> Notice(state.error, "נסה שוב", "retry", actions.refresh)
                    state.where == null -> Locating()
                    else -> {
                        AnimatedVisibility(state.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Text(state.error.orEmpty(), color = Palette.Stop, fontSize = 13.sp)
                        }
                        StreetSign(state.where)
                        ParkingCard(state, actions)
                        FactsCard(state.where)
                        state.where.locality?.let { TownCard(it) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        state.confirmation?.let { c ->
            AlertDialog(
                onDismissRequest = actions.dismissConfirmation,
                containerColor = Palette.Card,
                shape = CardShape,
                title = { Text("Cello מבקש אישור", fontWeight = FontWeight.Bold) },
                text = { Text(c.message) },
                confirmButton = {
                    TextButton(onClick = { actions.confirmParking(c) }, modifier = Modifier.testTag("confirm-parking")) {
                        Text("אשר והתחל חניה", color = Palette.Sign, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = actions.dismissConfirmation, modifier = Modifier.testTag("cancel-parking")) { Text("ביטול", color = Palette.Muted) }
                },
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────

@Composable
private fun TopBar(state: ScreenState, actions: Actions) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        LiveDot(active = state.accuracyM != null)
        Spacer(Modifier.width(8.dp))
        Text(
            if (state.accuracyM != null) "מיקום חי · דיוק ${state.accuracyM} מ׳" else "מאתר מיקום…",
            fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.weight(1f),
        )
        val spin = rememberInfiniteTransition()
        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Palette.Card)
                .border(1.dp, Palette.Hairline, CircleShape)
                .clickable(onClick = actions.refresh)
                .testTag("refresh"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh, contentDescription = "רענן", tint = Palette.Ink,
                modifier = Modifier.size(20.dp).rotate(if (state.loading) angle else 0f),
            )
        }
    }
}

/** A green dot that breathes while the position is live. */
@Composable
private fun LiveDot(active: Boolean) {
    val pulse = rememberInfiniteTransition()
    val s by pulse.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1400), RepeatMode.Restart))
    val a by pulse.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1400), RepeatMode.Restart))
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (active) Box(Modifier.size(8.dp).scale(s).clip(CircleShape).background(Palette.Live.copy(alpha = a)))
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Palette.Live else Palette.Muted))
    }
}

// ── The street sign: where you are ────────────────────────────────────────

/**
 * The headline, drawn as an Israeli street-name plate: blue, a white inset rule,
 * white lettering — the neighbourhood big, the street and city beneath — standing
 * on a blue-and-white paid-parking kerb.
 */
@Composable
private fun StreetSign(where: Where) {
    val a = where.address
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Palette.Sign, Palette.SignDeep), start = Offset(0f, 0f), end = Offset(900f, 700f)))
                .padding(7.dp)
                .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(13.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            AnimatedContent(
                targetState = Triple(a.neighborhood, listOfNotNull(a.street, a.houseNumber).joinToString(" "), a.city),
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
            ) { (hood, streetLine, city) ->
                Column {
                    Text("אתה נמצא ב", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        hood ?: city ?: "מקום לא מזוהה",
                        color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (streetLine.isNotBlank()) Text(streetLine, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    if (city != null && hood != null) Text(city, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                }
            }
        }
        Kerb(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp))
    }
}

/**
 * The signature element: a blue-and-white kerb (כחול-לבן). `moving` slides the
 * blocks slowly — used while a paid session runs, like a meter ticking.
 */
@Composable
private fun Kerb(modifier: Modifier, block: Dp = 18.dp, moving: Boolean = false) {
    val shift = if (moving) {
        val t = rememberInfiniteTransition()
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing))).value
    } else 0f
    Canvas(modifier.clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))) {
        val w = block.toPx()
        val slant = size.height * 0.6f
        drawRect(Color.White)
        clipRect {
            var x = -2 * w + shift * 2 * w
            while (x < size.width + w) {
                val p = Path().apply {
                    moveTo(x + slant, 0f); lineTo(x + w + slant, 0f); lineTo(x + w, size.height); lineTo(x, size.height); close()
                }
                drawPath(p, Palette.Kerb)
                x += 2 * w
            }
        }
        drawRect(Palette.SignDeep.copy(alpha = 0.18f), topLeft = Offset(0f, size.height - 2f), size = Size(size.width, 2f))
    }
}

// ── Parking ───────────────────────────────────────────────────────────────

@Composable
private fun ParkingCard(state: ScreenState, actions: Actions) {
    val cello = state.where?.cello ?: return
    val active = state.sessions.firstOrNull()
    var showAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().clip(CardShape).background(Palette.Card).border(1.dp, Palette.Hairline, CardShape)) {
        Kerb(Modifier.fillMaxWidth().height(8.dp), block = 14.dp, moving = active != null)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ParkingGlyph()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("מה לבחור ב-Cello", fontSize = 13.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
                    val city = when (cello) {
                        is Cello.One -> cello.city
                        is Cello.Several -> cello.city
                        is Cello.None -> cello.city
                        is Cello.NoCity -> null
                    }
                    if (city != null) Text("עיר: $city", fontSize = 15.sp, color = Palette.Ink, fontWeight = FontWeight.Medium)
                }
            }

            if (active != null) {
                ActiveSession(active, state.parkingBusy, actions.stopParking)
            } else {
                when (cello) {
                    is Cello.NoCity -> Text(cello.message, fontSize = 17.sp, color = Palette.Ink)
                    is Cello.One -> RecommendedZone(cello.zone, state.parkingBusy) { actions.startParking(cello.zone.id) }
                    is Cello.Several -> {
                        Text(cello.message, fontSize = 14.sp, color = Palette.Ink)
                        cello.candidates.forEach { z -> ZoneRow(z, state.parkingBusy) { actions.startParking(z.id) } }
                    }
                    is Cello.None -> Text(cello.message, fontSize = 14.sp, color = Palette.Ink)
                }
            }

            AnimatedVisibility(state.parkingMessage != null) {
                Text(state.parkingMessage.orEmpty(), fontSize = 14.sp, color = Palette.Ink, fontWeight = FontWeight.Medium)
            }

            val others = when (cello) {
                is Cello.One -> cello.others
                is Cello.Several -> cello.others
                is Cello.None -> cello.others
                is Cello.NoCity -> emptyList()
            }
            if (others.isNotEmpty() && active == null) {
                val open = showAll || cello is Cello.None
                Text(
                    if (open) "הסתר את שאר האזורים" else "שאר האזורים בעיר · ${others.size}",
                    color = Palette.Sign, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showAll = !showAll }.padding(vertical = 6.dp).testTag("toggle-other-zones"),
                )
                AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { z -> ZoneRow(z, state.parkingBusy) { actions.startParking(z.id) } }
                    }
                }
            }
        }
    }
}

/** The blue square "P" of an Israeli parking sign. */
@Composable
private fun ParkingGlyph() {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Palette.Sign), contentAlignment = Alignment.Center) {
        Text("P", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RecommendedZone(zone: Zone, busy: Boolean, onStart: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.SignSoft).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("בחר את האזור", fontSize = 12.sp, color = Palette.Sign, fontWeight = FontWeight.Bold)
        AnimatedContent(zone, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }) { z ->
            Column {
                Text(z.name, fontSize = 26.sp, lineHeight = 30.sp, color = Palette.SignDeep, fontWeight = FontWeight.Black)
                z.reason?.let { Text(it, fontSize = 13.sp, color = Palette.Muted) }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("התחל חניה כאן", Palette.Sign, enabled = !busy, busy = busy, tag = "start-parking-${zone.id}") { asking = true }
    }
    if (asking) StartDialog(zone, onDismiss = { asking = false }) { asking = false; onStart() }
}

@Composable
private fun ZoneRow(zone: Zone, busy: Boolean, onStart: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Hairline, RoundedCornerShape(12.dp))
            .clickable(enabled = !busy) { asking = true }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("start-parking-${zone.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(zone.name, fontSize = 15.sp, color = Palette.Ink, fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            zone.reason?.let { Text(it, fontSize = 12.sp, color = Palette.Muted) }
        }
        Spacer(Modifier.width(10.dp))
        Text("חנה כאן", color = Palette.Sign, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
    if (asking) StartDialog(zone, onDismiss = { asking = false }) { asking = false; onStart() }
}

@Composable
private fun StartDialog(zone: Zone, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        shape = CardShape,
        title = { Text("להתחיל חניה?", fontWeight = FontWeight.Bold) },
        text = { Text("החניה תחויב בחשבון ה-Cello שלך, באזור:\n${zone.name}") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-start-${zone.id}")) { Text("התחל", color = Palette.Sign, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-start-${zone.id}")) { Text("ביטול", color = Palette.Muted) } },
    )
}

/** The running meter: the zone, a live elapsed clock, and the stop control. */
@Composable
private fun ActiveSession(session: ParkingSession, busy: Boolean, onStop: () -> Unit) {
    val started = remember(session.startedAt) { session.startedAt?.let(::parseLocal) }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(started) {
        while (true) { now = Clock.System.now().toEpochMilliseconds(); delay(1000) }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.SignSoft).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(active = true)
            Spacer(Modifier.width(6.dp))
            Text("חניה פעילה", fontSize = 13.sp, color = Palette.Live, fontWeight = FontWeight.Bold)
        }
        Text(session.zoneName ?: "", fontSize = 18.sp, color = Palette.SignDeep, fontWeight = FontWeight.Bold)
        if (started != null) {
            Text("⁦${elapsedText(now - started)}⁩", fontSize = 40.sp, color = Palette.Ink, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("עצור חניה", Palette.Stop, enabled = !busy, busy = busy, tag = "stop-parking", onClick = onStop)
    }
}

@Composable
private fun PrimaryButton(label: String, color: Color, enabled: Boolean, busy: Boolean, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(if (pressed) 0.98f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else color.copy(alpha = 0.45f))
            .clickable(enabled = enabled, interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Facts and the town ────────────────────────────────────────────────────

@Composable
private fun FactsCard(where: Where) {
    val a = where.address
    val facts = listOfNotNull(
        (a.place ?: where.landmarks.firstOrNull())?.let { "ליד" to it },
        a.quarter?.let { "רובע" to it },
        where.neighborhoods.filter { it != a.neighborhood && it != a.quarter }.joinToString(", ").ifBlank { null }?.let { "ידוע גם כ" to it },
        a.subdistrict?.let { "נפה" to it },
        a.district?.let { "מחוז" to it },
        a.postcode?.let { "מיקוד" to "⁦$it⁩" },
        if (where.industrial == true) "שימוש" to "אזור תעשייה" else null,
        where.parkingArea?.let { "אזור חניה עירוני" to "אזור $it" },
    )
    if (facts.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Palette.Card).border(1.dp, Palette.Hairline, CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        facts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 12.sp, color = Palette.Muted)
                        Text(value, fontSize = 16.sp, color = Palette.Ink, fontWeight = FontWeight.Medium)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val AgeColors = listOf(
    Color(0xFFBFD3F5), Color(0xFF8DB0EC), Color(0xFF2463C9), Color(0xFF1A4BA0), Color(0xFF123677), Color(0xFF0A2152),
)

@Composable
private fun TownCard(locality: Locality) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Palette.Card).border(1.dp, Palette.Hairline, CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("על ${locality.name}", fontSize = 13.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("⁦${formatThousands(locality.population)}⁩", fontSize = 32.sp, color = Palette.Ink, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text("תושבים", fontSize = 16.sp, color = Palette.Muted, modifier = Modifier.padding(bottom = 5.dp))
        }
        locality.regionalCouncil?.let { Text("מועצה אזורית $it", fontSize = 14.sp, color = Palette.Ink) }

        // One bar split by age, youngest first — on the right, where Hebrew reading starts.
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            locality.ages.forEachIndexed { i, band ->
                val share = band.count.toFloat() / locality.population.coerceAtLeast(1)
                if (share > 0f) Box(Modifier.weight(share).fillMaxSize().background(AgeColors[i % AgeColors.size]))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            locality.ages.withIndex().chunked(3).forEach { row ->
                Row {
                    row.forEach { (i, band) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(AgeColors[i % AgeColors.size]))
                            Spacer(Modifier.width(5.dp))
                            Text("⁦${band.label}⁩  ${percent(band.count, locality.population)}%", fontSize = 12.sp, color = Palette.Ink)
                        }
                    }
                }
            }
        }
        Text("מקור: ${locality.source}", fontSize = 11.sp, color = Palette.Muted)
    }
}

// ── States ────────────────────────────────────────────────────────────────

@Composable
private fun Notice(text: String, button: String, tag: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Palette.Card).border(1.dp, Palette.Hairline, CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text, fontSize = 17.sp, color = Palette.Ink)
        PrimaryButton(button, Palette.Sign, enabled = true, busy = false, tag = tag, onClick = onClick)
    }
}

@Composable
private fun Locating() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Palette.Sign, Palette.SignDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text("מאתר את המיקום שלך…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        Kerb(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp), moving = true)
    }
}

// ── Formatting ────────────────────────────────────────────────────────────

private fun formatThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

private fun percent(part: Int, whole: Int): Int = if (whole <= 0) 0 else kotlin.math.round(part * 100.0 / whole).toInt()

/** Cello's DateIn is Israel local time without a zone ("2026-10-03T20:21:05"). */
private fun parseLocal(raw: String): Long? = runCatching {
    LocalDateTime.parse(raw.take(19)).toInstant(TimeZone.of("Asia/Jerusalem")).toEpochMilliseconds()
}.getOrNull()

private fun elapsedText(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    fun two(n: Long) = n.toString().padStart(2, '0')
    return "${two(total / 3600)}:${two((total % 3600) / 60)}:${two(total % 60)}"
}
