package com.example.actimelteambrowser.ui

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

// Barvy ve stejném duchu jako lišty v appce (kopie ze SettingsScreens.kt, v každém souboru soukromé)
// Barvy ve stejném duchu jako lišty v appce
private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF00B055)
private val MUTED = Color(0xFF888888)
private val DANGER = Color(0xFFB91C1C)

// Gesta: plocha, překryv, seznam gest, editor plochy a panel (konfigurační mód gest).

// ---------------------------------------------------------------- GESTA

internal val GESTURE_GREEN = Color(0xFF00B055)

/**
 * Plocha 3x3 pro zadani gesta: cisla 1-9 (style 0) nebo kulicky (style 1); 9 je vpravo dole.
 * Klavesy se zadavaji prejetim i klepanim a mohou se opakovat (napr. 963654147).
 * Gesto je hotove 1,2 s po zvednuti prstu - hotovy kod se preda do onComplete.
 */
/** Dotyk, ktery zacal na tlacitku 6 a pokracuje na ploše (souřadnice v okně; phase 0 = dolů, 1 = pohyb, 2 = zvednuto). */
class ExternalTouch(val x: Float, val y: Float, val phase: Int, val seq: Long)

private val GESTURE_YELLOW = Color(0xFFFFD600)   // to, co uzivatel prave zadava

@Composable
fun GestureSurface(
    style: Int,
    sizeDp: Int,
    onComplete: (String) -> Unit,
    heightDp: Int = sizeDp,                          // vyska plochy (sizeDp je sirka); nastavitelna v konfiguracnim modu gest
    modifier: Modifier = Modifier,
    onProgress: ((String) -> Unit)? = null,
    instant: (String) -> Boolean = { false },       // true = gesto je jednoznacne, vyhodnotit hned po zvednuti
    external: ExternalTouch? = null,                 // pokracovani dotyku z tlacitka 6
    externalDown: ExternalTouch? = null,             // misto, kde dotyk zacal (aby se nezratila klavesa 9)
    onBounds: ((Float, Float, Float, Float) -> Unit)? = null   // poloha a velikost plochy v okne (x, y, sirka, vyska v px) - pro ukazku
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val cw = sizePx / 3f       // sirka a vyska jedne klavesy; cell = mensi z nich (pismo, kolecka)
    val ch = heightPx / 3f
    val cell = minOf(cw, ch)
    var code by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var idle by remember { mutableStateOf(0) }
    var lastKey by remember { mutableStateOf(0) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    var extStarted by remember { mutableStateOf(false) }

    // klavesa pod prstem (0 = zadna); kulicky maji mensi "zasahovou" oblast
    fun keyAt(p: Offset): Int {
        if (p.x < 0f || p.y < 0f || p.x >= sizePx || p.y >= heightPx) return 0
        val col = (p.x / cw).toInt().coerceIn(0, 2)
        val row = (p.y / ch).toInt().coerceIn(0, 2)
        if (style == 1) {
            val dx = p.x - (col + 0.5f) * cw
            val dy = p.y - (row + 0.5f) * ch
            val lim = cell * 0.42f
            if (dx * dx + dy * dy > lim * lim) return 0
        }
        return row * 3 + col + 1
    }

    fun addKey(k: Int) {
        if (k != 0 && k != lastKey) {
            code += k
            lastKey = k
            onProgress?.invoke(code)
        }
    }

    fun begin(p: Offset) {
        finger = p
        strokes = strokes + listOf(listOf(p))
        lastKey = 0
        addKey(keyAt(p))
    }

    fun move(p: Offset) {
        finger = p
        strokes = if (strokes.isEmpty()) listOf(listOf(p)) else strokes.dropLast(1) + listOf(strokes.last() + p)
        addKey(keyAt(p))
    }

    fun end() {
        finger = null
        idle += 1
    }

    val touching = finger != null
    LaunchedEffect(idle, touching) {
        if (!touching && code.isNotEmpty()) {
            // jednoznacne gesto hned, jinak chvili pocka na dalsi klavesy
            delay(if (instant(code)) 60L else 1200L)
            val c = code
            code = ""
            strokes = emptyList()
            onProgress?.invoke("")
            onComplete(c)
        }
    }

    // dotyk, ktery zacal na tlacitku 6 a prst se nezvedl
    LaunchedEffect(external?.seq, origin) {
        val e = external ?: return@LaunchedEffect
        val o = origin ?: return@LaunchedEffect
        val p = Offset(e.x - o.x, e.y - o.y)
        if (e.phase == 2) {
            if (extStarted) {
                end()
                extStarted = false
            }
        } else if (!extStarted) {
            extStarted = true
            val d0 = externalDown
            if (d0 != null) begin(Offset(d0.x - o.x, d0.y - o.y)) else begin(p)
            if (d0 != null) move(p)
        } else {
            move(p)
        }
    }

    Canvas(
        modifier
            .size(sizeDp.dp, heightDp.dp)
            .onGloballyPositioned {
                origin = it.positionInWindow()
                onBounds?.invoke(it.positionInWindow().x, it.positionInWindow().y, sizePx, heightPx)
            }
            .pointerInput(sizePx, heightPx, style) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    begin(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull()
                        if (ch != null) {
                            move(ch.position)
                            ch.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    end()
                }
            }
    ) {
        val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        numPaint.textAlign = android.graphics.Paint.Align.CENTER
        numPaint.textSize = cell * 0.38f
        numPaint.color = android.graphics.Color.WHITE
        numPaint.isFakeBoldText = true

        for (row in 0..2) {
            for (col in 0..2) {
                val n = row * 3 + col + 1
                val active = code.contains('0' + n)
                val cx = (col + 0.5f) * cw
                val cy = (row + 0.5f) * ch
                if (style == 0) {
                    val pad = cell * 0.09f
                    val tl = Offset(col * cw + pad, row * ch + pad)
                    val sz = Size(cw - 2 * pad, ch - 2 * pad)
                    val cr = CornerRadius(cell * 0.2f)
                    drawRoundRect(
                        color = if (active) GESTURE_GREEN.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                        topLeft = tl, size = sz, cornerRadius = cr
                    )
                    drawRoundRect(
                        color = if (active) GESTURE_GREEN else Color.White.copy(alpha = 0.35f),
                        topLeft = tl, size = sz, cornerRadius = cr, style = Stroke(width = 2.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(n.toString(), cx, cy + numPaint.textSize * 0.35f, numPaint)
                } else if (style == 2) {
                    // Ctverecky (zamceny vzor, jen nahled): ostre ctverce bez cisel
                    val half = cell * 0.26f
                    val tl = Offset(cx - half, cy - half)
                    val sz = Size(half * 2, half * 2)
                    drawRect(color = if (active) GESTURE_GREEN else Color.White.copy(alpha = 0.14f), topLeft = tl, size = sz)
                    drawRect(
                        color = if (active) GESTURE_GREEN else Color.White.copy(alpha = 0.6f),
                        topLeft = tl, size = sz, style = Stroke(width = 2.dp.toPx())
                    )
                } else {
                    drawCircle(
                        color = if (active) GESTURE_GREEN else Color.White.copy(alpha = 0.85f),
                        radius = cell * (if (active) 0.13f else 0.09f),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = if (active) GESTURE_GREEN.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.22f),
                        radius = cell * 0.3f, center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
        for (s in strokes) {
            for (i in 1 until s.size) {
                drawLine(
                    color = GESTURE_GREEN.copy(alpha = 0.9f),
                    start = s[i - 1], end = s[i], strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        finger?.let { drawCircle(Color.White.copy(alpha = 0.6f), 16.dp.toPx(), it) }
    }
}

/**
 * Pruhledna plocha pres cely web. Vychozi misto je vpravo dole (9 je u tlacitka 6); kdyz si uzivatel v konfiguracnim
 * modu plochu upravil, je podle rect (poloha a velikost). opacity = viditelnost plochy (gesta funguji i pri 0).
 * Klepnuti mimo plochu ji zavre.
 */
@Composable
fun GestureOverlay(
    style: Int,
    sizeDp: Int,
    gestures: List<GestureDef>,
    bottomOffsetDp: Int,
    onDismiss: () -> Unit,
    onMatch: (GestureDef) -> Unit,
    onUnknown: () -> Unit,
    external: ExternalTouch? = null,
    externalDown: ExternalTouch? = null,
    rect: GestureRect? = null,
    opacity: Float = 1f,
    onSurfaceBounds: ((Float, Float, Float, Float) -> Unit)? = null,
    showHints: Boolean = true                       // nahore seznam gest, ktera jdou dokreslit (v ukazce vypnuto)
) {
    var progress by remember { mutableStateOf("") }
    val matched = gestures.firstOrNull { it.code == progress }
    // gesta, ktera zacinaji tim, co uz je nakreslene (na zacatku vsechna)
    val possible = gestures.filter { it.code.startsWith(progress) }

    @Composable
    fun Content(w: Int, h: Int, align: Alignment.Horizontal) {
        Column(horizontalAlignment = align) {
            if (progress.isEmpty()) {
                Text(
                    "Nakresli gesto – začni " + Gestures.startText(),
                    color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
                )
            } else {
                // co uzivatel prave zadava: zlute nad plochou (kod + funkce, kdyz uz nejaka odpovida)
                Text(
                    progress.toCharArray().joinToString(" › "),
                    color = GESTURE_YELLOW, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    matched?.fn?.label ?: if (possible.isEmpty()) "Takové gesto není" else "…",
                    color = if (possible.isEmpty() && matched == null) Color(0xFFFF6B6B) else GESTURE_YELLOW,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
                )
            }
            GestureSurface(
                style = style,
                sizeDp = w,
                heightDp = h,
                modifier = Modifier
                    .alpha(opacity.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x33000000)),
                onProgress = { progress = it },
                instant = { c -> gestures.any { it.code == c } && gestures.none { it.code != c && it.code.startsWith(c) } },
                external = external,
                externalDown = externalDown,
                onBounds = onSurfaceBounds,
                onComplete = { c ->
                    // jedna klavesa (napr. jen klepnuti na tlacitko 6) neni gesto - plocha zustane otevrena
                    if (c.length >= 2) {
                        val m = gestures.firstOrNull { it.code == c }
                        if (m != null) onMatch(m) else onUnknown()
                    }
                }
            )
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        // napoveda: gesta, ktera jdou z toho, co je uz nakreslene, dokoncit (nahore; kdyz je plocha nahore, tak dole)
        if (showHints && possible.isNotEmpty()) {
            val areaTop = rect?.let { it.t / 1000f } ?: 1f
            Column(
                Modifier
                    .align(if (areaTop < 0.45f) Alignment.BottomCenter else Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight.value * 0.42f).dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCC141414))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (g in possible) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GesturePathPreview(g.code, Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        // uz nakreslena cast kodu zlute, zbytek bile
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = GESTURE_YELLOW))
                                append(codeText(g.code.take(progress.length)))
                                pop()
                                val rest = g.code.drop(progress.length)
                                if (rest.isNotEmpty()) append((if (progress.isNotEmpty()) "-" else "") + codeText(rest))
                            },
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp)
                        )
                        Text(g.fn.label, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (rect == null) {
            // vychozi misto podle tlacitka gest: vlevo (klavesa 7) nebo vpravo (klavesa 9)
            val left = Gestures.startKey() == 7
            Box(
                Modifier
                    .align(if (left) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomOffsetDp.dp)
            ) {
                Content(sizeDp, sizeDp, if (left) Alignment.Start else Alignment.End)
            }
        } else {
            val area = areaDp(rect, maxWidth.value, maxHeight.value)
            // nadpis nad plochou (kdyz je plocha uplne nahore, je pres ni)
            Box(Modifier.offset(x = area.left.dp, y = (area.top - 28f).coerceAtLeast(0f).dp)) {
                Content(area.width.toInt().coerceAtLeast(120), area.height.toInt().coerceAtLeast(120), Alignment.Start)
            }
        }
    }
}

private fun codeText(code: String): String = code.toCharArray().joinToString("-")

/** Maly nahled drahy gesta na mrizce 3x3: velka zelena tecka = start, bila = konec, cara = prejezd mezi klavesami. */
@Composable
internal fun GesturePathPreview(code: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.width / 3f
        fun pt(k: Char): Offset {
            val i = k - '1'
            return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
        }
        for (i in 0..8) drawCircle(Color.White.copy(alpha = 0.3f), radius = 1.5.dp.toPx(), center = pt('1' + i))
        val pts = code.filter { it in '1'..'9' }.map { pt(it) }
        for (i in 1 until pts.size) {
            drawLine(GESTURE_GREEN, pts[i - 1], pts[i], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        if (pts.isNotEmpty()) {
            drawCircle(GESTURE_GREEN, radius = 4.dp.toPx(), center = pts.first())
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = pts.last())
        }
    }
}

@Composable
internal fun StyleCard(
    title: String, subtitle: String, selected: Boolean, modifier: Modifier,
    accent: Color = ACCENT, fill: Color = Color(0xFF14382A),   // barva obrysu a vyplne vybrane karty
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) fill else ROW)
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = MUTED, fontSize = 12.sp)
    }
}

@Composable
private fun FunctionPickerDialog(onPick: (ButtonFunction) -> Unit, onDismiss: () -> Unit) {
    StyledDialog(
        onDismissRequest = onDismiss,
        title = { Text("Funkce pro gesto") },
        text = {
            var query by remember { mutableStateOf("") }
            Column {
                FunctionSearchField(query) { query = it }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    val groups = ButtonFunction.values().filter { it != ButtonFunction.NONE && it.matches(query) }.groupBy { it.group }
                    for ((group, list) in groups) {
                        item(key = "gg-$group") {
                            Text(
                                group.uppercase(), color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { "gf-" + it.key }) { fn -> FunctionChoice(fn, false) { onPick(fn) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } }
    )
}

/** Nastaveni gest: vzhled a velikost plochy, vyzkouseni a seznam gest (kody jdou nahrat primo na plose). */
@Composable
internal fun GesturesScreen(onBack: () -> Unit) {
    var style by remember { mutableStateOf(AppSettings.gestureStyle.get()) }
    var size by remember { mutableStateOf(AppSettings.gestureSize.get().coerceIn(180, 340)) }
    var hapticOn by remember { mutableStateOf(AppSettings.gestureHaptic.get()) }
    var soundOn by remember { mutableStateOf(AppSettings.gestureSound.get()) }
    val gestures = remember { mutableStateListOf<GestureDef>().apply { addAll(Gestures.load()) } }
    val hostView = LocalView.current
    var testInfo by remember { mutableStateOf("Vyzkoušej plochu: nakresli gesto.") }
    var testProgress by remember { mutableStateOf("") }
    var pickFunction by remember { mutableStateOf(false) }
    var pendingFn by remember { mutableStateOf<ButtonFunction?>(null) }
    var recordFor by remember { mutableStateOf<Int?>(null) }   // index prenahravaneho gesta, -1 = nove

    fun persist() = Gestures.save(gestures.toList())

    ScreenScaffold(title = "Gesta", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Gesto se spouští tlačítkem s funkcí Gesta (kulička). Zobrazí se průhledná plocha u tlačítka; " +
                    "kód zadáš přejetím i klepáním, začínáš ${Gestures.startText()}.",
                color = MUTED, fontSize = 13.sp
            )

            Text("VZHLED PLOCHY", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleCard("Číselná klávesnice", "1 2 3 / 4 5 6 / 7 8 9", style == 0, Modifier.weight(1f)) {
                    style = 0
                    AppSettings.gestureStyle.set(0)
                }
                StyleCard("Kuličky", "mřížka 3 × 3 jako odemykání", style == 1, Modifier.weight(1f)) {
                    style = 1
                    AppSettings.gestureStyle.set(1)
                }
            }

            Text("VELIKOST PLOCHY: $size dp", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = size.toFloat(),
                onValueChange = { size = it.toInt() },
                valueRange = 180f..340f,
                onValueChangeFinished = { AppSettings.gestureSize.set(size) }
            )

            Text("ODEZVA PO GESTU", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Drobné zabrnění", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = hapticOn, onCheckedChange = { hapticOn = it; AppSettings.gestureHaptic.set(it) })
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zvuk úspěšného gesta", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = soundOn, onCheckedChange = { soundOn = it; AppSettings.gestureSound.set(it) })
            }

            Text("VYZKOUŠEJ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GestureSurface(
                    style = style,
                    sizeDp = size,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF151515)),
                    onProgress = { testProgress = it },
                    instant = { c -> gestures.any { it.code == c } && gestures.none { it.code != c && it.code.startsWith(c) } },
                    onComplete = { c ->
                        val m = gestures.firstOrNull { it.code == c }
                        // vibrace pri rozpoznanem gestu
                        if (m != null) {
                            gestureTick(hostView.context)
                        }
                        testInfo = if (m != null) "✓ " + codeText(c) + "  →  " + m.fn.label
                        else "✗ " + codeText(c) + "  (neznámé gesto)"
                    }
                )
            }
            Text(
                if (testProgress.isNotEmpty()) "Kód: " + codeText(testProgress) else testInfo,
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )

            Text("GESTA", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            for ((index, g) in gestures.withIndex()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PANEL)
                        .clickable { recordFor = index }
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(g.fn, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                g.fn.label, color = Color.White, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(codeText(g.code), color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    GesturePathPreview(g.code, Modifier.padding(end = 6.dp).size(38.dp))
                    Box(
                        Modifier.size(44.dp).clickable {
                            gestures.removeAt(index)
                            persist()
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("✕", color = MUTED, fontSize = 16.sp) }
                }
            }
            Text("Klepnutím na gesto ho přenahraješ novým kódem.", color = MUTED, fontSize = 12.sp)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ACCENT)
                    .clickable { pickFunction = true },
                contentAlignment = Alignment.Center
            ) { Text("+ PŘIDAT GESTO", color = Color(0xFF06281D), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ROW)
                    .clickable {
                        Gestures.reset()
                        gestures.clear()
                        gestures.addAll(Gestures.load())
                    },
                contentAlignment = Alignment.Center
            ) { Text("Obnovit výchozí gesta", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickFunction) {
        FunctionPickerDialog(
            onPick = { fn ->
                pickFunction = false
                pendingFn = fn
                recordFor = -1
            },
            onDismiss = { pickFunction = false }
        )
    }

    // nahrani kodu primo na plose
    recordFor?.let { idx ->
        var error by remember(idx) { mutableStateOf("") }
        Dialog(
            onDismissRequest = { recordFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val target = if (idx >= 0) gestures[idx].fn else pendingFn
                Text(
                    "Nahraj gesto" + (if (target != null) ": " + target.label else ""),
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (error.isNotEmpty()) error else "Nakresli kód aspoň ze 2 kláves (start: " + Gestures.startText() + ").",
                    color = if (error.isNotEmpty()) Color(0xFFFF6B6B) else MUTED, fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                GestureSurface(
                    style = style,
                    sizeDp = minOf(size, 280),
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF151515)),
                    onComplete = { c ->
                        val clash = gestures.indexOfFirst { it.code == c }
                        if (!Gestures.valid(c)) {
                            error = "Kód musí mít aspoň 2 klávesy."
                        } else if (clash >= 0 && clash != idx) {
                            error = "Tento kód už používá: " + gestures[clash].fn.label
                        } else {
                            if (idx >= 0) gestures[idx] = gestures[idx].copy(code = c)
                            else pendingFn?.let { gestures.add(GestureDef(c, it)) }
                            persist()
                            recordFor = null
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { recordFor = null }) { Text("Zrušit") }
            }
        }
    }
}

// ---------------------------------------------------------------- KONFIGURACNI MOD GEST (plocha s prichytkami + panel shora)

/** Poloha a velikost plochy gest: tisiciny sirky / vysky okna (aby sedela i po otoceni). */
data class GestureRect(val l: Int, val t: Int, val r: Int, val b: Int)

object GestureArea {
    /** "l,t,r,b" (tisiciny) -> obdelnik; prazdne / neplatne = null (pouzije se vychozi misto vpravo dole). */
    fun parse(s: String): GestureRect? {
        val p = s.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (p.size != 4) return null
        val r = GestureRect(p[0].coerceIn(0, 1000), p[1].coerceIn(0, 1000), p[2].coerceIn(0, 1000), p[3].coerceIn(0, 1000))
        return if (r.r > r.l && r.b > r.t) r else null
    }

    fun format(r: GestureRect): String = "${r.l},${r.t},${r.r},${r.b}"
}

/** Obdelnik plochy v dp (vlevo, nahore, sirka, vyska). */
private data class DpArea(val left: Float, val top: Float, val width: Float, val height: Float)

private fun areaDp(r: GestureRect, wDp: Float, hDp: Float) = DpArea(
    left = r.l / 1000f * wDp, top = r.t / 1000f * hDp,
    width = (r.r - r.l) / 1000f * wDp, height = (r.b - r.t) / 1000f * hDp
)

/**
 * Editor plochy gest na hlavni plose: plocha s uchyty (body na okrajich a rozich), tahem za uchyt se meni velikost,
 * tahem uvnitr se plocha presouva. Zmena se ulozi po pusteni prstu (onChange).
 */
@Composable
fun GestureAreaEditor(
    style: Int,
    opacity: Float,
    defaultSizeDp: Int,
    bottomOffsetDp: Int,
    resetKey: Int,
    onChange: (GestureRect) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0x55000000))) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val minPx = with(density) { 150.dp.toPx() }
        val handleR = with(density) { 11.dp.toPx() }
        val hitR = with(density) { 34.dp.toPx() }
        val snapPx = with(density) { 16.dp.toPx() }   // plocha se pri presouvani prichyti k okraji, kdyz je blize nez 16 dp

        // vychozi umisteni = jako drive: vpravo dole, ctvercova plocha
        fun initial(): FloatArray {
            val saved = GestureArea.parse(AppSettings.gestureRect.get())
            if (saved != null) return floatArrayOf(saved.l / 1000f * wPx, saved.t / 1000f * hPx, saved.r / 1000f * wPx, saved.b / 1000f * hPx)
            val s = with(density) { defaultSizeDp.dp.toPx() }
            val bottom = hPx - with(density) { bottomOffsetDp.dp.toPx() }
            if (Gestures.startKey() == 7) {
                // tlacitko gest vlevo: plocha vlevo dole (klavesa 7 u tlacitka)
                val left = with(density) { 12.dp.toPx() }
                return floatArrayOf(left, (bottom - s).coerceAtLeast(0f), (left + s).coerceAtMost(wPx), bottom)
            }
            val right = wPx - with(density) { 12.dp.toPx() }
            return floatArrayOf((right - s).coerceAtLeast(0f), (bottom - s).coerceAtLeast(0f), right, bottom)
        }

        val init = remember(resetKey) { initial() }
        var rl by remember(resetKey) { mutableStateOf(init[0]) }
        var rt by remember(resetKey) { mutableStateOf(init[1]) }
        var rr by remember(resetKey) { mutableStateOf(init[2]) }
        var rb by remember(resetKey) { mutableStateOf(init[3]) }

        // nahled plochy (nereaguje na dotyk - ten bere vrstva s uchyty nad nim)
        GestureSurface(
            style = style,
            sizeDp = ((rr - rl) / density.density).toInt().coerceAtLeast(1),
            heightDp = ((rb - rt) / density.density).toInt().coerceAtLeast(1),
            modifier = Modifier
                .offset { IntOffset(rl.roundToInt(), rt.roundToInt()) }
                .alpha(maxOf(opacity, 0.3f))   // v editoru je plocha videt aspon slabe, i kdyz je viditelnost 0
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x33000000)),
            onComplete = {}
        )

        Canvas(
            Modifier
                .fillMaxSize()
                // klic resetKey: po "Zarovnat" / "Vratit vychozi" se vytvori novy stav plochy, a tak se musi znovu spustit i zpracovani dotyku
                // (jinak by drzelo stary stav a plocha by nesla ovladat)
                .pointerInput(resetKey, wPx, hPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // ktery uchyt je pod prstem: hx, hy = -1 (vlevo/nahore), 0 (stred), 1 (vpravo/dole); 0,0 = posun celeho
                        var hx = 2
                        var hy = 2
                        var best = hitR
                        for (ix in -1..1) for (iy in -1..1) {
                            if (ix == 0 && iy == 0) continue
                            val px = when (ix) { -1 -> rl; 0 -> (rl + rr) / 2f; else -> rr }
                            val py = when (iy) { -1 -> rt; 0 -> (rt + rb) / 2f; else -> rb }
                            val dist = kotlin.math.hypot((down.position.x - px).toDouble(), (down.position.y - py).toDouble()).toFloat()
                            if (dist < best) { best = dist; hx = ix; hy = iy }
                        }
                        if (hx == 2) {
                            if (down.position.x in rl..rr && down.position.y in rt..rb) { hx = 0; hy = 0 } else return@awaitEachGesture
                        }
                        down.consume()
                        val moveW = rr - rl
                        val moveH = rb - rt
                        var rawL = rl   // skutecna poloha prstu (bez prichyceni), aby se plocha od okraje dala odtahnout
                        var rawT = rt
                        do {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull() ?: break
                            val dx = ch.positionChange().x
                            val dy = ch.positionChange().y
                            if (hx == 0 && hy == 0) {
                                rawL = (rawL + dx).coerceIn(0f, wPx - moveW)
                                rawT = (rawT + dy).coerceIn(0f, hPx - moveH)
                                var nl = rawL
                                var nt = rawT
                                if (nl < snapPx) nl = 0f else if (wPx - moveW - nl < snapPx) nl = wPx - moveW
                                if (nt < snapPx) nt = 0f else if (hPx - moveH - nt < snapPx) nt = hPx - moveH
                                rl = nl; rr = nl + moveW; rt = nt; rb = nt + moveH
                            } else {
                                if (hx == -1) rl = (rl + dx).coerceIn(0f, rr - minPx)
                                if (hx == 1) rr = (rr + dx).coerceIn(rl + minPx, wPx)
                                if (hy == -1) rt = (rt + dy).coerceIn(0f, rb - minPx)
                                if (hy == 1) rb = (rb + dy).coerceIn(rt + minPx, hPx)
                            }
                            ch.consume()
                        } while (event.changes.any { it.pressed })
                        onChange(
                            GestureRect(
                                (rl / wPx * 1000).roundToInt(), (rt / hPx * 1000).roundToInt(),
                                (rr / wPx * 1000).roundToInt(), (rb / hPx * 1000).roundToInt()
                            )
                        )
                    }
                }
        ) {
            // preruseny zeleny obrys + uchyty (body) na okrajich a rozich
            drawRoundRect(
                color = GESTURE_GREEN,
                topLeft = Offset(rl, rt), size = Size(rr - rl, rb - rt),
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
            )
            for (ix in -1..1) for (iy in -1..1) {
                if (ix == 0 && iy == 0) continue
                val px = when (ix) { -1 -> rl; 0 -> (rl + rr) / 2f; else -> rr }
                val py = when (iy) { -1 -> rt; 0 -> (rt + rb) / 2f; else -> rb }
                drawCircle(Color.White, radius = handleR, center = Offset(px, py))
                drawCircle(GESTURE_GREEN, radius = handleR, center = Offset(px, py), style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

/**
 * Panel, ktery se pri nastaveni gest vysune shora: vzhled plochy, viditelnost, odezva, poloha a seznam gest.
 * Je scrollovaci. onChanged = zmena se projevila (editor se prekresli), onResetArea = vratit polohu a velikost plochy.
 */
/** Cena vzhledu Ctverecky: pocet zablokovanych reklam. */
private const val SQUARE_PRICE = 100_000

@Composable
fun GesturePanel(onChanged: () -> Unit, onResetArea: () -> Unit, onClose: () -> Unit, onPreviewStyle: (Int?) -> Unit = {}, onAlignArea: () -> Unit = {}) {
    var style by remember { mutableStateOf(AppSettings.gestureStyle.get()) }
    var owned by remember { mutableStateOf(AppSettings.squareStyleOwned.get()) }   // Ctverecky koupene
    var opacity by remember { mutableStateOf(AppSettings.gestureOpacity.get()) }
    var hapticOn by remember { mutableStateOf(AppSettings.gestureHaptic.get()) }
    var soundOn by remember { mutableStateOf(AppSettings.gestureSound.get()) }
    val gestures = remember { mutableStateListOf<GestureDef>().apply { addAll(Gestures.load()) } }
    var pickFunction by remember { mutableStateOf(false) }
    var pendingFn by remember { mutableStateOf<ButtonFunction?>(null) }
    var recordFor by remember { mutableStateOf<Int?>(null) }   // index prenahravaneho gesta, -1 = nove
    var minimized by remember { mutableStateOf(false) }   // true = z panelu zustane jen hlavicka (plocha je pak lepe videt a jde upravit)

    fun persist() = Gestures.save(gestures.toList())

    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(primary = ACCENT, background = BG, surface = PANEL)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxPanel = maxHeight * 0.58f
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .heightIn(max = maxPanel)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Color(0xF71E1E1E))
                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                // pevna hlavicka: nazev + zluto-oranzove tlacitko ulozit a zavrit
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 40.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gesta", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    // minimalizovat / rozbalit panel
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable { minimized = !minimized },
                        contentAlignment = Alignment.Center
                    ) { Text(if (minimized) "▼" else "▲", color = Color.White, fontSize = 16.sp) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE61E1E1E))
                            .drawBehind {
                                // plny zlutooranzovy obrys + zluty text (stejne jako "Uložit a zavřít nastavení" u tlacitek)
                                drawRoundRect(
                                    color = Color(0xFFFFA000), cornerRadius = CornerRadius(14.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            .clickable(onClick = onClose)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text("Uložit a zavřít nastavení", color = Color(0xFFFFD600), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                if (!minimized) Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("STYL PLOCHY", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleCard("Číslice", "1 2 3 / 4 5 6 / 7 8 9", style == 0, Modifier.weight(1f)) {
                            style = 0; AppSettings.gestureStyle.set(0); onPreviewStyle(null); onChanged()
                        }
                        StyleCard("Kolečka", "mřížka 3 × 3", style == 1, Modifier.weight(1f)) {
                            style = 1; AppSettings.gestureStyle.set(1); onPreviewStyle(null); onChanged()
                        }
                        // Ctverecky: koupi se za 100 000 zablokovanych reklam; do te doby jen nahled (neulozi se)
                        StyleCard(if (owned) "Čtverečky" else "🔒 Čtverečky", if (owned) "ostré čtverce" else "zamčeno, jen náhled", style == 2, Modifier.weight(1f)) {
                            style = 2
                            if (owned) { AppSettings.gestureStyle.set(2); onPreviewStyle(null) } else onPreviewStyle(2)
                            onChanged()
                        }
                    }
                    if (style == 2 && !owned) {
                        val price = SQUARE_PRICE
                        val have = AppSettings.totalBlocked.get()
                        val canBuy = have >= price
                        val fmt = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("cs"))
                        Text(
                            "Čtverečky jsou zamčené: zatím je jde jen vyzkoušet (neuloží se). Cena: ${fmt.format(price)} zablokovaných reklam – máš ${fmt.format(have)}.",
                            color = MUTED, fontSize = 12.sp
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (canBuy) Color(0xFFFFD600) else ROW)
                                .clickable(enabled = canBuy) {
                                    AppSettings.squareStyleOwned.set(true)
                                    AppSettings.gestureStyle.set(2)
                                    owned = true
                                    onPreviewStyle(null)
                                    onChanged()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (canBuy) "KOUPIT za ${fmt.format(price)} reklam" else "KOUPIT – chybí ${fmt.format(price - have)} reklam",
                                color = if (canBuy) Color(0xFF1A1A1A) else MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text("VIDITELNOST PLOCHY: $opacity %", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = opacity.toFloat(),
                        onValueChange = { opacity = it.toInt(); AppSettings.gestureOpacity.set(opacity); onChanged() },
                        valueRange = 0f..100f
                    )
                    Text("Při 0 % plocha není vidět, gesta ale fungují dál.", color = MUTED, fontSize = 12.sp)

                    Text("POLOHA A VELIKOST", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Tahej za bílé body na okrajích a rozích plochy (velikost) nebo za její vnitřek (poloha).",
                        color = Color(0xFFCCCCCC), fontSize = 13.sp
                    )
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable { onAlignArea() },
                        contentAlignment = Alignment.Center
                    ) { Text("Zarovnat k tlačítku", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable { onResetArea() },
                        contentAlignment = Alignment.Center
                    ) { Text("Vrátit výchozí polohu a velikost", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

                    Text("ODEZVA PO GESTU", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Drobné zabrnění", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = hapticOn, onCheckedChange = { hapticOn = it; AppSettings.gestureHaptic.set(it) })
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Zvuk úspěšného gesta", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = soundOn, onCheckedChange = { soundOn = it; AppSettings.gestureSound.set(it) })
                    }

                    Text("GESTA", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    for ((index, g) in gestures.withIndex()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PANEL)
                                .clickable { recordFor = index }
                                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlyphIcon(g.fn, 20.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        g.fn.label, color = Color.White, fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(codeText(g.code), color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            GesturePathPreview(g.code, Modifier.padding(end = 6.dp).size(38.dp))
                            Box(
                                Modifier.size(44.dp).clickable { gestures.removeAt(index); persist() },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", color = MUTED, fontSize = 16.sp) }
                        }
                    }
                    Text("Klepnutím na gesto ho přenahraješ novým kódem.", color = MUTED, fontSize = 12.sp)
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(ACCENT).clickable { pickFunction = true },
                        contentAlignment = Alignment.Center
                    ) { Text("+ PŘIDAT GESTO", color = Color(0xFF06281D), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(ROW).clickable {
                            Gestures.reset()
                            gestures.clear()
                            gestures.addAll(Gestures.load())
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("Obnovit výchozí gesta", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (pickFunction) {
            FunctionPickerDialog(
                onPick = { fn ->
                    pickFunction = false
                    pendingFn = fn
                    recordFor = -1
                },
                onDismiss = { pickFunction = false }
            )
        }

        // nahrani kodu primo na plose
        recordFor?.let { idx ->
            var error by remember(idx) { mutableStateOf("") }
            Dialog(onDismissRequest = { recordFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(PANEL)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val target = if (idx >= 0) gestures[idx].fn else pendingFn
                    Text(
                        "Nahraj gesto" + (if (target != null) ": " + target.label else ""),
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (error.isNotEmpty()) error else "Nakresli kód aspoň ze 2 kláves (start: " + Gestures.startText() + ").",
                        color = if (error.isNotEmpty()) Color(0xFFFF6B6B) else MUTED, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    GestureSurface(
                        style = style,
                        sizeDp = 260,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF151515)),
                        onComplete = { c ->
                            val clash = gestures.indexOfFirst { it.code == c }
                            if (!Gestures.valid(c)) {
                                error = "Kód musí mít aspoň 2 klávesy."
                            } else if (clash >= 0 && clash != idx) {
                                error = "Tento kód už používá: " + gestures[clash].fn.label
                            } else {
                                if (idx >= 0) gestures[idx] = gestures[idx].copy(code = c)
                                else pendingFn?.let { gestures.add(GestureDef(c, it)) }
                                persist()
                                recordFor = null
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { recordFor = null }) { Text("Zrušit") }
                }
            }
        }
    }
}
