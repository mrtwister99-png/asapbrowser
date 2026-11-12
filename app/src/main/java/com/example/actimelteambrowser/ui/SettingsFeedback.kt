package com.example.actimelteambrowser.ui

import com.example.actimelteambrowser.demoMark
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.actimelteambrowser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.actimelteambrowser.core.settings.GestureDef
import com.example.actimelteambrowser.core.gestures.gestureTick
import com.example.actimelteambrowser.core.settings.Gestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.actimelteambrowser.core.settings.SearchEngines
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import com.example.actimelteambrowser.data.BookmarkFolder
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.data.BookmarksRepository
import com.example.actimelteambrowser.data.FolderWithCount
import com.example.actimelteambrowser.data.HistoryRepository
import com.example.actimelteambrowser.data.HistoryVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import com.example.actimelteambrowser.UfoFieldView

// Barvy ve stejném duchu jako lišty v appce (kopie ze SettingsScreens.kt)
private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF00B055)
private val MUTED = Color(0xFF888888)
private val DANGER = Color(0xFFB91C1C)
private val ORANGE = Color(0xFFFF8A00)

// Hodnoceni aplikace a hlaseni chyb (otevira se drzenim tlacitka Nastaveni) + sdilene pozadi UFO Field.

/**
 * Pozadi "UFO Field" pod obsahem obrazovky. Dotyky, ktere zadny prvek obsahu nespotrebuje (klepnuti do prazdna,
 * prst na pozadi), se predavaji pozadi: klepnuti = ohnostroj, prst = odpuzovac UFO.
 */
@Composable
internal fun UfoBackground(content: @Composable BoxScope.() -> Unit) {
    var view by remember { mutableStateOf<UfoFieldView?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Final)   // az po deti: zname, co spotrebovaly
                        val ch = ev.changes.firstOrNull() ?: continue
                        val v = view ?: continue
                        when {
                            ev.type == PointerEventType.Press && !ch.isConsumed -> v.touchDown(ch.position.x, ch.position.y)
                            ev.type == PointerEventType.Move && ch.pressed && !ch.isConsumed -> v.touchMove(ch.position.x, ch.position.y)
                            !ch.pressed || ch.isConsumed -> v.touchUp()
                        }
                    }
                }
            }
    ) {
        AndroidView(factory = { UfoFieldView(it).also { v -> view = v } }, modifier = Modifier.fillMaxSize())
        content()
    }
}

/**
 * Kam se hodnoceni a hlaseni chyb odesilaji: HTTPS adresa Supabase Edge Function "submit-feedback" a "anon" klic
 * projektu (je verejny, patri do aplikace). Dokud je adresa prazdna, odesilani je vypnute a obrazovka to rekne.
 * Klic od e-mailove sluzby ani servisni klic se do aplikace NIKDY nedavaji, zustavaji v tajnych promennych funkce.
 */
internal object FeedbackConfig {
    const val FUNCTION_URL = "https://ukcqkospxxauoygxhufk.supabase.co/functions/v1/submit-feedback"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVrY3Frb3NweHhhdW95Z3hodWZrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE0MjM1NDUsImV4cCI6MjEwNjk5OTU0NX0.1BBPqgNUSZK2TlQ8LfpuHsRZR-fjhjwkUEEkR3S05jI"
}

// Odesle zpravu na server. Vraci null = odeslano, jinak text chyby (vola se z pozadi vlakna).
private fun postFeedback(ctx: android.content.Context, rating: Int, type: String, message: String, contact: String): String? {
    val url = FeedbackConfig.FUNCTION_URL
    if (!url.startsWith("https://")) return "Odesílání zatím není nastavené."
    return try {
        val body = org.json.JSONObject()
            .put("rating", rating)
            .put("type", type)
            .put("message", message.trim())
            .put("contact", contact.trim())
            .put("app_version", appVersionText(ctx))
            .put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            .put("device", Build.MANUFACTURER + " " + Build.MODEL)
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("apikey", FeedbackConfig.ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer " + FeedbackConfig.ANON_KEY)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) null else "Server odpověděl chybou ($code)."
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        "Odeslání se nepodařilo, zkontroluj připojení."
    }
}

@Composable
internal fun FeedbackScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf(0) }            // 0 = recenze, 1 = chyba
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val configured = FeedbackConfig.FUNCTION_URL.startsWith("https://")
    val needRating = type == 0
    val canSend = !sending && !sent && message.trim().length >= 5 && (!needRating || rating > 0)

    BackHandler { onBack() }

    UfoBackground {
        ScreenScaffold(title = "Hodnocení a chyby", onBack = onBack, containerColor = Color.Transparent) {
            Column(
                Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xD91E1E1E)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (type == 0) {
                        Text("Jak se ti líbí ASAP Browser?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (i in 1..5) {
                                Box(Modifier.size(48.dp).clip(CircleShape).clickable { rating = i }, contentAlignment = Alignment.Center) {
                                    Text(if (i <= rating) "★" else "☆", color = if (i <= rating) Color(0xFFFFD400) else MUTED, fontSize = 34.sp)
                                }
                            }
                        }
                    } else {
                        // u chyby se hvezdicky nezobrazuji, misto nich vyzva (stejna vyska jako radek hvezdicek, nic neposkakuje)
                        Text("Ohlaš nalezenou chybu.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
                            Text("Každé nahlášení pomůže chybu rychle opravit.", color = MUTED, fontSize = 13.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleCard("Recenze", "Co se ti líbí a co ne", type == 0, Modifier.weight(1f).demoMark("fb_review")) { type = 0 }
                        StyleCard("Chyba", "Něco nefunguje", type == 1, Modifier.weight(1f).demoMark("fb_bug"), accent = ORANGE, fill = Color(0xFF3D2410)) { type = 1 }
                    }
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { if (it.length <= 60) nickname = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Přezdívka") }
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 2000) message = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        label = { Text(if (type == 0) "Tvoje recenze" else "Co se stalo? Popiš chybu.") }
                    )
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { if (it.length <= 200) contact = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        label = { Text("E-mail pro odpověď (nepovinné)") }
                    )
                    Text(
                        "Za veškeré recenze a vaši zpětnou vazbu jsem velmi vděčný a cílem je všem umožnit jednoduché, rychlé a bezpečné prohlížení.",
                        color = MUTED, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canSend) ACCENT else ROW)
                            .clickable(enabled = canSend) {
                                sending = true
                                status = null
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) {
                                        // prezdivka jde na zacatek textu (server ma pevny tvar zpravy)
                                        val text = if (nickname.isBlank()) message else "Přezdívka: " + nickname.trim() + "\n\n" + message
                                        postFeedback(ctx, if (type == 0) rating else 0, if (type == 0) "review" else "bug", text, contact)
                                    }
                                    sending = false
                                    if (err == null) { sent = true; status = "Díky! Zpráva je odeslaná." } else status = err
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (sending) "Odesílám…" else if (sent) "Odesláno ✓" else "ODESLAT",
                            color = if (canSend) Color(0xFF06281D) else MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    val st = status
                    if (st != null) {
                        Text(st, color = if (sent) ACCENT else Color(0xFFFF6B6B), fontSize = 13.sp)
                    } else if (!configured) {
                        Text("Odesílání zatím není nastavené (chybí adresa serveru).", color = MUTED, fontSize = 12.sp)
                    } else if (needRating && rating == 0) {
                        Text("U recenze nejdřív vyber počet hvězdiček.", color = MUTED, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
