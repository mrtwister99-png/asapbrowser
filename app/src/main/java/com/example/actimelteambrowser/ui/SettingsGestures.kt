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
import androidx.compose.ui.graphics.toArgb
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

/**
 * Vzhled plochy gest: barva plochy, barvy tlacitek (1-9), barva znaku, barva projeti, spoj (cara / znaky) a efekt.
 * Sestavuje GestureLook.theme(); barvy, spoj a efekt jdou menit jen s odemcenymi Barvami (tajny kod).
 */
data class GestureTheme(
    val areaBg: Color = Color(0x33000000),
    val keyColors: List<Int>? = null,   // barva tlacitka 1-9 (0 = vychozi)
    val glyph: Color? = null,           // barva znaku (cislo, tecka, obrys); null = vychozi bila
    val trail: Color = Color(0xFF00B055),   // barva projeti (cara a zvyrazneni klavesy)
    val trailStyle: Int = 0,            // spoj: 0 cara, 1 carkovana, 2 teckovana, 3-7 znaky (srdce, kary, krize, piky, smajlik)
    val effect: Int = -1,               // -1 podle vzhledu, 0 zadny, 1 mini UFO, 2 kruh, 3 ctverecek, 4 hvezdicky
    val areaAlpha: Float = 1f,
    val keyAlpha: Float = 1f
)

internal val TRAIL_SYMBOLS = listOf("♥", "♦", "♣", "♠", "☺")
private const val FX_MS = 650L

private class GestureFx(val key: Int, val t0: Long, val type: Int, val seed: Int)

// Male UFO (efekt projeti): talir + kupole
private fun DrawScope.miniUfo(c: Offset, r: Float, color: Color) {
    drawOval(color, topLeft = Offset(c.x - r * 1.7f, c.y - r * 0.4f), size = Size(r * 3.4f, r * 0.9f))
    drawArc(color, 180f, 180f, useCenter = true, topLeft = Offset(c.x - r * 0.85f, c.y - r * 1.15f), size = Size(r * 1.7f, r * 1.7f))
}

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
    onBounds: ((Float, Float, Float, Float) -> Unit)? = null,  // poloha a velikost plochy v okne (x, y, sirka, vyska v px)
    theme: GestureTheme? = null                      // null = puvodni vzhled (pozadi dava modifier, bez efektu)
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val cw = sizePx / 3f       // sirka a vyska jedne klavesy; cell = mensi z nich (pismo, kolecka)
    val ch = heightPx / 3f
    val cell = minOf(cw, ch)
    val th = theme ?: GestureTheme(areaBg = Color.Transparent)
    // efekt pri projeti klavesou: podle vzhledu (cisla = mini UFO, kolecka = kruh, ctverecky = ctverecek, hvezdicky = hvezdicky)
    val effectType = if (theme == null) 0 else if (th.effect < 0) when (style) { 1 -> 2; 2 -> 3; 3 -> 4; else -> 1 } else th.effect
    var code by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var idle by remember { mutableStateOf(0) }
    var lastKey by remember { mutableStateOf(0) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    var extStarted by remember { mutableStateOf(false) }
    val fx = remember { mutableStateListOf<GestureFx>() }
    var now by remember { mutableStateOf(0L) }

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
            if (effectType > 0) fx.add(GestureFx(k, android.os.SystemClock.uptimeMillis(), effectType, kotlin.random.Random.nextInt()))
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

    // efekty: snimek po snimku, dokud nejaky bezi
    val fxRunning = fx.isNotEmpty()
    LaunchedEffect(fxRunning) {
        while (fx.isNotEmpty()) {
            androidx.compose.runtime.withFrameMillis { }
            now = android.os.SystemClock.uptimeMillis()
            fx.removeAll { now - it.t0 > FX_MS }
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
                        val chg = event.changes.firstOrNull()
                        if (chg != null) {
                            move(chg.position)
                            chg.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    end()
                }
            }
    ) {
        val ka = th.keyAlpha.coerceIn(0f, 1f)
        val aa = th.areaAlpha.coerceIn(0f, 1f)
        val va = maxOf(ka, aa)
        fun Color.k() = copy(alpha = alpha * ka)
        // plocha: jedna barva pod celou plochou
        if (aa > 0f && th.areaBg.alpha > 0f) drawRect(th.areaBg.copy(alpha = th.areaBg.alpha * aa))
        val glyphC = th.glyph ?: Color.White
        val custom = th.glyph != null
        fun g(a: Float) = (if (custom) glyphC else Color.White.copy(alpha = a)).k()
        val trailC = th.trail
        val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        numPaint.textAlign = android.graphics.Paint.Align.CENTER
        numPaint.textSize = cell * 0.38f
        numPaint.color = glyphC.copy(alpha = ka).toArgb()
        numPaint.isFakeBoldText = true

        for (row in 0..2) {
            for (col in 0..2) {
                val n = row * 3 + col + 1
                val active = code.contains('0' + n)
                val cx = (col + 0.5f) * cw
                val cy = (row + 0.5f) * ch
                val btn = th.keyColors?.getOrNull(n - 1) ?: 0
                val btnC: Color? = if (btn != 0) Color(btn) else null
                if (style == 0) {
                    val pad = cell * 0.09f
                    val tl = Offset(col * cw + pad, row * ch + pad)
                    val sz = Size(cw - 2 * pad, ch - 2 * pad)
                    val cr = CornerRadius(cell * 0.2f)
                    drawRoundRect(color = (if (active) trailC.copy(alpha = 0.55f) else btnC ?: Color.White.copy(alpha = 0.12f)).k(), topLeft = tl, size = sz, cornerRadius = cr)
                    drawRoundRect(color = if (active) trailC.k() else g(0.35f), topLeft = tl, size = sz, cornerRadius = cr, style = Stroke(width = 2.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(n.toString(), cx, cy + numPaint.textSize * 0.35f, numPaint)
                } else if (style == 2) {
                    // Ctverecky: ostre ctverce bez cisel
                    val half = cell * 0.26f
                    val tl = Offset(cx - half, cy - half)
                    val sz = Size(half * 2, half * 2)
                    drawRect(color = (if (active) trailC else btnC ?: Color.White.copy(alpha = 0.14f)).k(), topLeft = tl, size = sz)
                    drawRect(color = if (active) trailC.k() else g(0.6f), topLeft = tl, size = sz, style = Stroke(width = 2.dp.toPx()))
                } else if (style == 3) {
                    // Hvezdicky
                    val star = starPath(Offset(cx, cy), cell * (if (active) 0.32f else 0.28f))
                    drawPath(star, (if (active) trailC else btnC ?: Color.White.copy(alpha = 0.14f)).k())
                    drawPath(star, if (active) trailC.k() else g(0.6f), style = Stroke(width = 2.dp.toPx()))
                } else {
                    if (btnC != null) drawCircle(btnC.k(), radius = cell * 0.3f, center = Offset(cx, cy))
                    drawCircle(
                        color = if (active) trailC.k() else g(0.85f),
                        radius = cell * (if (active) 0.13f else 0.09f),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = if (active) trailC.copy(alpha = 0.5f).k() else g(0.22f),
                        radius = cell * 0.3f, center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // spoj: cara, carkovana, teckovana nebo znaky v rade za prstem
        val tc = trailC.copy(alpha = 0.9f * va)
        if (th.trailStyle in 0..2) {
            for (st in strokes) {
                if (st.size < 2) continue
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(st[0].x, st[0].y)
                for (k in 1 until st.size) path.lineTo(st[k].x, st[k].y)
                val pe = when (th.trailStyle) {
                    1 -> PathEffect.dashPathEffect(floatArrayOf(22.dp.toPx(), 14.dp.toPx()))
                    2 -> PathEffect.dashPathEffect(floatArrayOf(0.1f, 16.dp.toPx()))
                    else -> null
                }
                drawPath(
                    path, tc,
                    style = Stroke(width = (if (th.trailStyle == 2) 10 else 7).dp.toPx(), cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round, pathEffect = pe)
                )
            }
        } else {
            val sym = TRAIL_SYMBOLS.getOrElse(th.trailStyle - 3) { "♥" }
            val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            sp.textAlign = android.graphics.Paint.Align.CENTER
            sp.textSize = 22.dp.toPx()
            sp.color = tc.toArgb()
            val step = 24.dp.toPx()
            for (st in strokes) {
                if (st.isEmpty()) continue
                var acc = step
                var prev = st[0]
                for (k in 1 until st.size) {
                    val cur = st[k]
                    acc += kotlin.math.hypot(cur.x - prev.x, cur.y - prev.y)
                    if (acc >= step) {
                        acc = 0f
                        drawContext.canvas.nativeCanvas.drawText(sym, cur.x, cur.y + sp.textSize * 0.35f, sp)
                    }
                    prev = cur
                }
            }
        }

        // efekty pri projeti klavesou
        if (now > 0L) for (f in fx) {
            val p = ((now - f.t0) / FX_MS.toFloat()).coerceIn(0f, 1f)
            val idx = f.key - 1
            val cx = (idx % 3 + 0.5f) * cw
            val cy = (idx / 3 + 0.5f) * ch
            val rnd = java.util.Random(f.seed.toLong())
            val fade = (1f - p) * va
            when (f.type) {
                1 -> {
                    // tri mini UFO: leti od stredu plochy ven (nahore nahoru, dole dolu), kousek a zmizi
                    val dx = cx - sizePx / 2f
                    val dy = cy - heightPx / 2f
                    val baseA = if (kotlin.math.hypot(dx, dy) < 1f) rnd.nextFloat() * 6.283f else kotlin.math.atan2(dy, dx)
                    repeat(3) {
                        val a = baseA + (rnd.nextFloat() - 0.5f) * 1.4f
                        val sx = cx + (rnd.nextFloat() - 0.5f) * cell * 0.3f
                        val sy = cy + (rnd.nextFloat() - 0.5f) * cell * 0.3f
                        val dist = cell * (0.45f + rnd.nextFloat() * 0.3f) * p
                        miniUfo(Offset(sx + kotlin.math.cos(a) * dist, sy + kotlin.math.sin(a) * dist), cell * 0.08f, trailC.copy(alpha = fade))
                    }
                }
                2 -> drawCircle(trailC.copy(alpha = fade), radius = cell * (0.3f + 0.5f * p), center = Offset(cx, cy), style = Stroke(width = 2.5.dp.toPx()))
                3 -> {
                    // ctverecek: kopie obrysu se zmensi na 30 % a pak vystreli do ztracena
                    val h0 = cell * 0.26f
                    val half = if (p < 0.4f) h0 * (1f - 0.7f * (p / 0.4f)) else h0 * 0.3f + (cell * 0.75f - h0 * 0.3f) * ((p - 0.4f) / 0.6f)
                    val al = if (p < 0.4f) va else (1f - (p - 0.4f) / 0.6f) * va
                    drawRect(trailC.copy(alpha = al), topLeft = Offset(cx - half, cy - half), size = Size(half * 2, half * 2), style = Stroke(width = 2.dp.toPx()))
                }
                4 -> {
                    // 4-6 malych hvezdicek vystreli kolem znaku
                    val n = 4 + (kotlin.math.abs(f.seed) % 3)
                    val rot = rnd.nextFloat() * 6.283f
                    for (q in 0 until n) {
                        val a = rot + q * 6.283f / n
                        val r = cell * (0.22f + 0.5f * p)
                        drawPath(starPath(Offset(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r), cell * 0.07f), trailC.copy(alpha = fade))
                    }
                }
            }
        }
        finger?.let { drawCircle(Color.White.copy(alpha = 0.6f * va), 16.dp.toPx(), it) }
    }
}

/** Pevna hvezda s 5 cipy (vzhled plochy Hvezdicky a kulicka tlacitka gest). */
internal fun starPath(c: Offset, r: Float): androidx.compose.ui.graphics.Path {
    val p = androidx.compose.ui.graphics.Path()
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) r else r * 0.45f
        val a = Math.toRadians(-90.0 + k * 36.0)
        val x = c.x + (rad * kotlin.math.cos(a)).toFloat()
        val y = c.y + (rad * kotlin.math.sin(a)).toFloat()
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** Vzhled plochy gest podle nastaveni (zamcene vzory a barvy jen kdyz jsou odemcene). */
object GestureLook {
    const val SQUARE_PRICE = 100_000
    const val STAR_PRICE = 1_000_000
    val DEFAULT_BG = Color(0x33000000)

    fun owned(style: Int): Boolean = when (style) {
        2 -> AppSettings.squareStyleOwned.get()
        3 -> AppSettings.starStyleOwned.get()
        else -> true
    }

    /** Ulozeny vzhled; zamceny (napr. z importu cizeho nastaveni) = cisla. */
    fun style(): Int = AppSettings.gestureStyle.get().let { if (it in 0..3 && owned(it)) it else 0 }

    fun areaAlpha(): Float = AppSettings.gestureOpacity.get() / 100f
    fun keyAlpha(): Float = AppSettings.gestureKeysOpacity.get().let { if (it < 0) AppSettings.gestureOpacity.get() else it } / 100f

    fun rawKeyColors(): IntArray {
        val out = IntArray(9)
        AppSettings.gestureKeyColors.get().split(",").forEachIndexed { i, v -> if (i < 9) out[i] = v.trim().toIntOrNull() ?: 0 }
        return out
    }

    fun saveKeyColors(c: IntArray) = AppSettings.gestureKeyColors.set(if (c.all { it == 0 }) "" else c.joinToString(","))

    fun keyColors(): IntArray? = if (AppSettings.gestureColorsOwned.get()) rawKeyColors().takeIf { a -> a.any { it != 0 } } else null

    /** Cely vzhled plochy podle nastaveni (barvy, spoj a efekt jen s odemcenymi Barvami). */
    fun theme(): GestureTheme {
        val owned = AppSettings.gestureColorsOwned.get()
        return GestureTheme(
            areaBg = areaBg(),
            keyColors = keyColors()?.toList(),
            glyph = AppSettings.gestureGlyphColor.get().takeIf { owned && it != 0 }?.let { Color(it) },
            trail = AppSettings.gestureTrailColor.get().takeIf { owned && it != 0 }?.let { Color(it) } ?: GESTURE_GREEN,
            trailStyle = if (owned) AppSettings.gestureTrail.get().coerceIn(0, 7) else 0,
            effect = if (owned) AppSettings.gestureEffect.get().coerceIn(-1, 4) else -1,
            areaAlpha = areaAlpha(),
            keyAlpha = keyAlpha()
        )
    }

    fun areaBg(): Color {
        val c = AppSettings.gestureAreaColor.get()
        return if (c != 0 && AppSettings.gestureColorsOwned.get()) Color(c) else DEFAULT_BG
    }
}

/**
 * Pruhledna plocha pres cely web. Vychozi misto je vpravo dole (9 je u tlacitka 6); kdyz si uzivatel v konfiguracnim
 * modu plochu upravil, je podle rect (poloha a velikost). Plocha je vzdy na stejnem miste jako v editoru.
 * Zapis gesta: nad plochou, u plochy v horni polovine obrazovky pod ni (nikdy ji neposune).
 * Napoveda: od horniho pole az k plose; kdyz nad plochou neni misto, pod plochou. Klepnuti mimo plochu ji zavre.
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
    onSurfaceBounds: ((Float, Float, Float, Float) -> Unit)? = null,
    showHints: Boolean = true,                      // napoveda gest, ktera jdou dokreslit (v ukazce vypnuto)
    theme: GestureTheme = GestureTheme(),
    topInsetDp: Int = 8,                             // kde konci horni pole (napoveda zacina pod nim)
    onMatchAt: ((Float, Float) -> Unit)? = null      // stred posledni klavesy rozpoznaneho gesta (v okne, px) - ohnostroj
) {
    var progress by remember { mutableStateOf("") }
    val matched = gestures.firstOrNull { it.code == progress }
    val possible = gestures.filter { it.code.startsWith(progress) }
    val right = Gestures.startKey() != 7   // zapis gesta u strany tlacitka gest
    var bounds by remember { mutableStateOf<FloatArray?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val area = if (rect != null) areaDp(rect, wDp, hDp) else {
            val sz = sizeDp.toFloat()
            DpArea(if (right) wDp - 12f - sz else 12f, (hDp - bottomOffsetDp - sz).coerceAtLeast(0f), sz, sz)
        }
        val aw = area.width.coerceAtLeast(120f)
        val ah = area.height.coerceAtLeast(120f)

        GestureSurface(
            style = style,
            sizeDp = aw.toInt(),
            heightDp = ah.toInt(),
            modifier = Modifier
                .offset(x = area.left.dp, y = area.top.dp)
                .clip(RoundedCornerShape(24.dp)),
            theme = theme,
            onProgress = { progress = it },
            instant = { c -> gestures.any { it.code == c } && gestures.none { it.code != c && it.code.startsWith(c) } },
            external = external,
            externalDown = externalDown,
            onBounds = { x, y, w, h -> bounds = floatArrayOf(x, y, w, h); onSurfaceBounds?.invoke(x, y, w, h) },
            onComplete = { c ->
                // jedna klavesa (napr. jen klepnuti na tlacitko 6) neni gesto - plocha zustane otevrena
                if (c.length >= 2) {
                    val m = gestures.firstOrNull { it.code == c }
                    if (m != null) {
                        val b = bounds
                        val k = c.last() - '1'
                        if (b != null && k in 0..8) onMatchAt?.invoke(b[0] + (k % 3 + 0.5f) * b[2] / 3f, b[1] + (k / 3 + 0.5f) * b[3] / 3f)
                        onMatch(m)
                    } else onUnknown()
                }
            }
        )

        // zapis gesta: pevny pruh nad plochou; plocha v horni polovine (tlacitko nahore) nebo bez mista nad ni = pod plochou
        val labelH = 72f
        val below = area.top + ah / 2f < hDp / 2f || area.top < labelH
        val labelTop = if (below) (area.top + ah).coerceAtMost(hDp - labelH) else area.top - labelH
        Box(
            Modifier
                .offset(y = labelTop.dp)
                .fillMaxWidth()
                .height(labelH.dp)
                .padding(
                    start = if (right) 8.dp else area.left.dp,
                    end = if (right) (wDp - area.left - aw).coerceAtLeast(0f).dp else 8.dp
                ),
            contentAlignment = when {
                !below && right -> Alignment.BottomEnd
                !below -> Alignment.BottomStart
                right -> Alignment.TopEnd
                else -> Alignment.TopStart
            }
        ) {
            Column(
                horizontalAlignment = if (right) Alignment.End else Alignment.Start,
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                if (progress.isEmpty()) {
                    Text(
                        "Nakresli gesto – začni " + Gestures.startText(),
                        color = Color(0xCCFFFFFF), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                } else {
                    Text(
                        progress.toCharArray().joinToString(" › "),
                        color = GESTURE_YELLOW, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Text(
                        matched?.fn?.label ?: if (possible.isEmpty()) "Takové gesto není" else "…",
                        color = if (possible.isEmpty() && matched == null) Color(0xFFFF6B6B) else GESTURE_YELLOW,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
            }
        }

        // napoveda: od horniho pole po zacatek plochy (nad zapisem); kdyz tam neni misto, pod plochou
        if (showHints && possible.isNotEmpty()) {
            val top0 = topInsetDp.toFloat()
            val aboveEnd = if (below) area.top else area.top - labelH
            val roomAbove = aboveEnd - top0 - 8f
            val hintsTop: Float
            val hintsMax: Float
            if (roomAbove >= 90f) {
                hintsTop = top0; hintsMax = roomAbove
            } else {
                hintsTop = area.top + ah + (if (below) labelH else 0f) + 6f
                hintsMax = (hDp - bottomOffsetDp + 8f - hintsTop).coerceAtLeast(80f)
            }
            Column(
                Modifier
                    .offset(y = hintsTop.dp)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = hintsMax.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xD9141414))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (g in possible) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GesturePathPreview(g.code, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = GESTURE_YELLOW))
                                append(codeText(g.code.take(progress.length)))
                                pop()
                                val rest = g.code.drop(progress.length)
                                if (rest.isNotEmpty()) append((if (progress.isNotEmpty()) "-" else "") + codeText(rest))
                            },
                            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp)
                        )
                        Text(g.fn.label, color = Color(0xFFDDDDDD), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
    defaultSizeDp: Int,
    bottomOffsetDp: Int,
    resetKey: Int,
    onChange: (GestureRect) -> Unit,
    theme: GestureTheme = GestureTheme()
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
                .clip(RoundedCornerShape(24.dp)),
            // v editoru je plocha videt aspon slabe, i kdyz je viditelnost 0
            theme = theme.copy(areaAlpha = maxOf(theme.areaAlpha, 0.3f), keyAlpha = maxOf(theme.keyAlpha, 0.3f)),
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

/** Nadpis sekce panelu gest. */
@Composable
private fun PanelLabel(text: String) = Text(text, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)

/** Sipka (stejna jako zpet / dal ve spodnim poli): zavreno = dolu, otevreno = nahoru. */
@Composable
private fun OpenArrow(open: Boolean, tint: Color = Color.White, sizeDp: Int = 16) {
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else -90f, label = "arrow")
    Image(
        painterResource(R.drawable.ic_nav_back), null,
        modifier = Modifier.size(sizeDp.dp).graphicsLayer { rotationZ = rot },
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
    )
}

/** Prepinac v panelu (zabrneni, zvuk). */
@Composable
private fun PanelSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Siroke tlacitko v panelu. */
@Composable
private fun PanelButton(text: String, bg: Color = ROW, fg: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/** Volby v radcich po [perRow] (vybrana zelene oramovana). */
@Composable
private fun Chips(labels: List<String>, selected: Int, perRow: Int = 3, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((r, row) in labels.chunked(perRow).withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((c, label) in row.withIndex()) {
                    val idx = r * perRow + c
                    val on = idx == selected
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) Color(0xFF14382A) else ROW)
                            .then(if (on) Modifier.border(1.5.dp, ACCENT, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { onPick(idx) },
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Vyber barvy: Vychozi + vzdy 10 barev (barvy karet), dalsi stranky teckami dole. */
@Composable
private fun PagedPalette(selected: Int, onPick: (Int) -> Unit) {
    val pages = remember { com.example.actimelteambrowser.TabColors.ALL.chunked(10) }
    var page by remember { mutableStateOf(maxOf(0, pages.indexOfFirst { selected in it })) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(ROW)
                .then(if (selected == 0) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                .clickable { onPick(0) },
            contentAlignment = Alignment.Center
        ) { Text("Výchozí", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        for (row in pages[page.coerceIn(0, pages.lastIndex)].chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in row) {
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(c))
                            .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Color.White else Color(0x55FFFFFF), RoundedCornerShape(12.dp))
                            .clickable { onPick(c) }
                    )
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // stranky barev
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            for (k in pages.indices) {
                Box(Modifier.size(26.dp).clickable { page = k }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (k == page) 10.dp else 7.dp).clip(CircleShape).background(if (k == page) Color(0xFFFFD600) else Color(0x66FFFFFF)))
                }
            }
        }
    }
}

/**
 * Panel, ktery se pri nastaveni gest vysune shora. Nahore tri sekce se sipkou, otevrena muze byt vzdy jen jedna:
 *  Vzhled (styl, viditelnost, poloha, barvy - zamcene), Odezva (zabrneni, zvuk), Gesta (seznam).
 * Tajny kod: vypnout zabrneni i zvuk a smazat vychozi gesto Zpet = odemkne Ctverecky, Hvezdicky i Barvy (Zpet se hned vrati).
 */
@Composable
fun GesturePanel(onChanged: () -> Unit, onResetArea: () -> Unit, onClose: () -> Unit, onPreviewStyle: (Int?) -> Unit = {}, onAlignArea: () -> Unit = {}) {
    val ctx = LocalContext.current
    var style by remember { mutableStateOf(AppSettings.gestureStyle.get()) }
    var squareOwned by remember { mutableStateOf(AppSettings.squareStyleOwned.get()) }
    var starOwned by remember { mutableStateOf(AppSettings.starStyleOwned.get()) }
    var colorsOwned by remember { mutableStateOf(AppSettings.gestureColorsOwned.get()) }
    var opacity by remember { mutableStateOf(AppSettings.gestureOpacity.get()) }
    var keysOpacity by remember { mutableStateOf((GestureLook.keyAlpha() * 100).roundToInt()) }
    var hapticOn by remember { mutableStateOf(AppSettings.gestureHaptic.get()) }
    var soundOn by remember { mutableStateOf(AppSettings.gestureSound.get()) }
    var areaColor by remember { mutableStateOf(AppSettings.gestureAreaColor.get()) }
    var glyphColor by remember { mutableStateOf(AppSettings.gestureGlyphColor.get()) }
    var trailColor by remember { mutableStateOf(AppSettings.gestureTrailColor.get()) }
    var trailStyle by remember { mutableStateOf(AppSettings.gestureTrail.get()) }
    var effect by remember { mutableStateOf(AppSettings.gestureEffect.get()) }
    val keyColors = remember { mutableStateListOf<Int>().apply { addAll(GestureLook.rawKeyColors().toList()) } }
    var colorKey by remember { mutableStateOf(9) }        // vybrane tlacitko pro barvu (9 = vsechna stejne)
    var colorTarget by remember { mutableStateOf(0) }     // 0 plocha, 1 tlacitka, 2 znak, 3 projeti, 4 spoj, 5 efekt
    var colorsOpen by remember { mutableStateOf(false) }
    val gestures = remember { mutableStateListOf<GestureDef>().apply { addAll(Gestures.load()) } }
    var pickFunction by remember { mutableStateOf(false) }
    var pendingFn by remember { mutableStateOf<ButtonFunction?>(null) }
    var recordFor by remember { mutableStateOf<Int?>(null) }   // index prenahravaneho gesta, -1 = nove
    var open by remember { mutableStateOf(-1) }   // otevrena sekce: 0 vzhled, 1 odezva, 2 gesta, -1 zadna

    fun persist() = Gestures.save(gestures.toList())
    fun ownedStyle(st: Int) = when (st) { 2 -> squareOwned; 3 -> starOwned; else -> true }

    fun pickStyle(st: Int) {
        style = st
        if (ownedStyle(st)) { AppSettings.gestureStyle.set(st); onPreviewStyle(null) } else onPreviewStyle(st)
        onChanged()
    }

    fun deleteGesture(index: Int) {
        val g = gestures[index]
        gestures.removeAt(index)
        persist()
        // tajny kod: zabrneni i zvuk vypnute + smazane vychozi gesto Zpet
        val def = Gestures.defaults().firstOrNull { it.fn == ButtonFunction.BACK }
        if (!hapticOn && !soundOn && g.fn == ButtonFunction.BACK && def != null && g.code == def.code &&
            !(squareOwned && starOwned && colorsOwned)) {
            AppSettings.squareStyleOwned.set(true)
            AppSettings.starStyleOwned.set(true)
            AppSettings.gestureColorsOwned.set(true)
            squareOwned = true; starOwned = true; colorsOwned = true
            // gesto Zpet se hned vrati na sve misto (smazani bylo jen soucasti kodu)
            gestures.add(index.coerceAtMost(gestures.size), g)
            persist()
            gestureTick(ctx)
            com.example.actimelteambrowser.AppToast.show(ctx, "Tajný kód! Odemčeno: Čtverečky, Hvězdičky a Barvy", true,
                com.example.actimelteambrowser.AppToast.Kind.OK)
            onPreviewStyle(null)
            if (style == 2 || style == 3) AppSettings.gestureStyle.set(style)
            onChanged()
        }
    }

    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(primary = ACCENT, background = BG, surface = PANEL)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxPanel = maxHeight * 0.62f
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .heightIn(max = maxPanel)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Color(0xF71E1E1E))
                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 40.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gesta", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE61E1E1E))
                            .drawBehind {
                                drawRoundRect(color = Color(0xFFFFA000), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
                            }
                            .clickable(onClick = onClose)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text("Uložit a zavřít nastavení", color = Color(0xFFFFD600), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                // lista se tremi sipkami: otevrena muze byt vzdy jen jedna sekce
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    @Composable
                    fun Tab(label: String, k: Int, modifier: Modifier) {
                        val on = open == k
                        Row(
                            modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) Color(0xFF14382A) else ROW)
                                .then(if (on) Modifier.border(1.5.dp, ACCENT, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { open = if (on) -1 else k }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.width(6.dp))
                            OpenArrow(on, if (on) ACCENT else Color.White, 14)
                        }
                    }
                    Tab("Vzhled", 0, Modifier.weight(1f))
                    Tab("Odezva", 1, Modifier.weight(1f))
                    Tab("Gesta", 2, Modifier.weight(1f))
                }
                if (open >= 0) Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val fmt = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("cs"))

                    // ---------------- a) VZHLED (styl, viditelnost, poloha, barvy)
                    if (open == 0) {
                        PanelLabel("STYL PLOCHY")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StyleCard("Číslice", "1 2 3 / 4 5 6 / 7 8 9", style == 0, Modifier.weight(1f)) { pickStyle(0) }
                            StyleCard("Kolečka", "mřížka 3 × 3", style == 1, Modifier.weight(1f)) { pickStyle(1) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StyleCard(if (squareOwned) "Čtverečky" else "🔒 Čtverečky",
                                if (squareOwned) "ostré čtverce" else "za ${fmt.format(GestureLook.SQUARE_PRICE)} reklam", style == 2, Modifier.weight(1f)) { pickStyle(2) }
                            StyleCard(if (starOwned) "Hvězdičky" else "🔒 Hvězdičky",
                                if (starOwned) "pěticípé hvězdy" else "za ${fmt.format(GestureLook.STAR_PRICE)} reklam", style == 3, Modifier.weight(1f)) { pickStyle(3) }
                        }
                        if ((style == 2 || style == 3) && !ownedStyle(style)) {
                            val price = if (style == 2) GestureLook.SQUARE_PRICE else GestureLook.STAR_PRICE
                            val name = if (style == 2) "Čtverečky" else "Hvězdičky"
                            val have = AppSettings.totalBlocked.get()
                            val canBuy = have >= price
                            Text(
                                "$name jsou zamčené: zatím je jde jen vyzkoušet (neuloží se). Cena: ${fmt.format(price)} zablokovaných reklam – máš ${fmt.format(have)}.",
                                color = MUTED, fontSize = 12.sp
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (canBuy) Color(0xFFFFD600) else ROW)
                                    .clickable(enabled = canBuy) {
                                        if (style == 2) { AppSettings.squareStyleOwned.set(true); squareOwned = true }
                                        else { AppSettings.starStyleOwned.set(true); starOwned = true }
                                        AppSettings.gestureStyle.set(style)
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

                        PanelLabel("VIDITELNOST PLOCHY: $opacity %")
                        Slider(
                            value = opacity.toFloat(),
                            onValueChange = { opacity = it.toInt(); AppSettings.gestureOpacity.set(opacity); onChanged() },
                            valueRange = 0f..100f
                        )
                        PanelLabel("VIDITELNOST TLAČÍTEK: $keysOpacity %")
                        Slider(
                            value = keysOpacity.toFloat(),
                            onValueChange = { keysOpacity = it.toInt(); AppSettings.gestureKeysOpacity.set(keysOpacity); onChanged() },
                            valueRange = 0f..100f
                        )
                        Text("Při 0 % plocha ani tlačítka nejsou vidět, gesta ale fungují dál.", color = MUTED, fontSize = 12.sp)

                        PanelLabel("POLOHA A VELIKOST")
                        Text(
                            "Tahej za bílé body na okrajích a rozích plochy (velikost) nebo za její vnitřek (poloha).",
                            color = Color(0xFFCCCCCC), fontSize = 13.sp
                        )
                        PanelButton("Zarovnat k tlačítku") { onAlignArea() }
                        PanelButton("Vrátit výchozí polohu a velikost") { onResetArea() }

                        // barvy, spoj a efekt: cela kategorie je zamcena (odemkne ji jen tajny kod)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PANEL)
                                .clickable {
                                    if (colorsOwned) colorsOpen = !colorsOpen
                                    else com.example.actimelteambrowser.AppToast.show(ctx, "Barvy jsou zatím zamčené", false,
                                        com.example.actimelteambrowser.AppToast.Kind.INFO)
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (colorsOwned) "Barvy a efekty" else "🔒 Barvy a efekty",
                                    color = if (colorsOwned) Color.White else MUTED, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(if (colorsOwned) "plocha, tlačítka, znak, projetí, spoj a efekt" else "zamčeno",
                                    color = MUTED, fontSize = 12.sp)
                            }
                            OpenArrow(colorsOpen && colorsOwned, if (colorsOwned) Color.White else MUTED)
                        }
                        if (colorsOwned && colorsOpen) {
                            Chips(listOf("Plocha", "Tlačítka", "Znak", "Projetí", "Spoj", "Efekt"), colorTarget) { colorTarget = it }
                            when (colorTarget) {
                                0 -> {
                                    Text("Barva celého podkladu plochy.", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    PagedPalette(areaColor) { c -> areaColor = c; AppSettings.gestureAreaColor.set(c); onChanged() }
                                }
                                1 -> {
                                    Text("Vyber tlačítko (nebo všechna) a pak barvu.", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (areaColor != 0) Color(areaColor) else Color(0xFF151515))) {
                                            for (r in 0..2) Row {
                                                for (c in 0..2) {
                                                    val k = r * 3 + c
                                                    val kc = keyColors[k]
                                                    Box(
                                                        Modifier.size(44.dp).padding(4.dp).clip(RoundedCornerShape(10.dp))
                                                            .background(if (kc != 0) Color(kc) else Color(0x22FFFFFF))
                                                            .border(if (colorKey == k) 3.dp else 0.5.dp, if (colorKey == k) Color.White else Color(0x44FFFFFF), RoundedCornerShape(10.dp))
                                                            .clickable { colorKey = k },
                                                        contentAlignment = Alignment.Center
                                                    ) { Text("${k + 1}", color = if (glyphColor != 0) Color(glyphColor) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                                }
                                            }
                                        }
                                        StyleCard("Všechna tlačítka", "jedna barva pro 1–9", colorKey == 9, Modifier.weight(1f)) { colorKey = 9 }
                                    }
                                    PagedPalette(if (colorKey == 9) (keyColors.distinct().singleOrNull() ?: -2) else keyColors[colorKey]) { picked ->
                                        if (colorKey == 9) for (k in 0..8) keyColors[k] = picked else keyColors[colorKey] = picked
                                        GestureLook.saveKeyColors(keyColors.toIntArray())
                                        onChanged()
                                    }
                                }
                                2 -> {
                                    Text("Barva znaku – čísla, tečky a obrysů tlačítek.", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    PagedPalette(glyphColor) { c -> glyphColor = c; AppSettings.gestureGlyphColor.set(c); onChanged() }
                                }
                                3 -> {
                                    Text("Barva při projetí – čára za prstem a projeté tlačítko.", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    PagedPalette(trailColor) { c -> trailColor = c; AppSettings.gestureTrailColor.set(c); onChanged() }
                                }
                                4 -> {
                                    Text("Čím se spojují projetá tlačítka (znaky se kreslí v řadě za prstem).", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    Chips(listOf("Čára", "Čárkovaná", "Tečkovaná", "♥ Srdce", "♦ Káry", "♣ Kříže", "♠ Piky", "☺ Smajlík"), trailStyle) {
                                        trailStyle = it; AppSettings.gestureTrail.set(it); onChanged()
                                    }
                                }
                                else -> {
                                    Text("Efekt při projetí tlačítkem.", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    // -1 podle vzhledu, 0 zadny, 1 mini UFO, 2 kruh, 3 ctverecek, 4 hvezdicky
                                    Chips(listOf("Podle vzhledu", "Mini UFO", "Kruh", "Čtvereček", "Hvězdičky", "Žádný"),
                                        when (effect) { -1 -> 0; 0 -> 5; else -> effect }) { k ->
                                        effect = when (k) { 0 -> -1; 5 -> 0; else -> k }
                                        AppSettings.gestureEffect.set(effect); onChanged()
                                    }
                                }
                            }
                            PanelButton("Vrátit výchozí barvy a efekty") {
                                areaColor = 0; glyphColor = 0; trailColor = 0; trailStyle = 0; effect = -1
                                for (k in 0..8) keyColors[k] = 0
                                AppSettings.gestureAreaColor.set(0); AppSettings.gestureGlyphColor.set(0); AppSettings.gestureTrailColor.set(0)
                                AppSettings.gestureTrail.set(0); AppSettings.gestureEffect.set(-1)
                                GestureLook.saveKeyColors(IntArray(9))
                                onChanged()
                            }
                        }
                    }

                    // ---------------- b) ODEZVA
                    if (open == 1) {
                        PanelLabel("ODEZVA PO GESTU")
                        PanelSwitch("Drobné zabrnění", hapticOn) { hapticOn = it; AppSettings.gestureHaptic.set(it) }
                        PanelSwitch("Zvuk úspěšného gesta", soundOn) { soundOn = it; AppSettings.gestureSound.set(it) }
                    }

                    // ---------------- c) GESTA
                    if (open == 2) {
                        PanelLabel("GESTA")
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
                                        Text(g.fn.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(codeText(g.code), color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                GesturePathPreview(g.code, Modifier.padding(end = 6.dp).size(38.dp))
                                Box(Modifier.size(44.dp).clickable { deleteGesture(index) }, contentAlignment = Alignment.Center) {
                                    Text("✕", color = MUTED, fontSize = 16.sp)
                                }
                            }
                        }
                        Text("Klepnutím na gesto ho přenahraješ novým kódem.", color = MUTED, fontSize = 12.sp)
                        PanelButton("+ NOVÉ GESTO", ACCENT, Color(0xFF06281D)) { pickFunction = true }
                        PanelButton("Obnovit výchozí gesta") {
                            Gestures.reset()
                            gestures.clear()
                            gestures.addAll(Gestures.load())
                        }
                    }
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
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PANEL).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val target = if (idx >= 0) gestures[idx].fn else pendingFn
                    Text("Nahraj gesto" + (if (target != null) ": " + target.label else ""), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (error.isNotEmpty()) error else "Nakresli kód aspoň ze 2 kláves (start: " + Gestures.startText() + ").",
                        color = if (error.isNotEmpty()) Color(0xFFFF6B6B) else MUTED, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    GestureSurface(
                        style = if (ownedStyle(style)) style else 0,
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
