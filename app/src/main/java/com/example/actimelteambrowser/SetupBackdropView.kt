package com.example.actimelteambrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withScale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pozadi nastavovacich rezimu (uvodni nastaveni, nastaveni tlacitek, nastaveni gest): prechod cerna -> tmave seda
 * a konvoj tri UFO - vpredu dve zelena, kousek za nimi cervene. Konvoj leti na nahodne misto (rovne nebo klikate,
 * 3-15 s, nejdal tesne pred okraj obrazovky), zastavi, 15 s stoji a pak leti znovu. Vsechny rozmery jsou v dp.
 * Vypnute animace (Nastaveni -> Animace): tri UFO stoji kazde na jinem nahodnem miste a nic se neprekresluje.
 */
class SetupBackdropView(context: Context) : View(context) {

    private class Look(val main: Int, val light: Int, val dark: Int, val glow: Int)

    private val density = resources.displayMetrics.density
    private val rnd = java.util.Random()

    private val size = 10f                           // o stupen mensi nez nejvetsi UFO v "O aplikaci" (12)
    private val edge = 36f                           // konvoj doleti nejdal tesne pred okraj
    private val backs = floatArrayOf(0f, 26f, 68f)   // odstup za vedouci lodi: zelena, zelena, cervena
    private val waitSec = 15f
    private val still = !com.example.actimelteambrowser.core.settings.AppSettings.animationsOn.get()
    private val stillPos = Array(3) { FloatArray(2) }  // mista stojicich UFO (jen pri vypnutych animacich)

    private val green = Look("#00E676".toColorInt(), "#5CFFAB".toColorInt(), "#2E9E57".toColorInt(), Color.argb(46, 0, 230, 118))
    private val red = Look("#FF3B30".toColorInt(), "#FF8A80".toColorInt(), "#7A0000".toColorInt(), Color.argb(46, 255, 59, 48))

    private var w = 0f
    private var h = 0f
    private var lx = 0f                              // vedouci lod
    private var ly = 0f
    private val trail = ArrayList<FloatArray>()      // stopa vedouci lodi, po ktere leti ostatni
    private var fromX = 0f; private var fromY = 0f
    private var toX = 0f; private var toY = 0f
    private var nx = 0f; private var ny = 0f         // kolmice na smer letu (klikaty let)
    private var amp = 0f
    private var waves = 1
    private var dur = 5f
    private var prog = 0f
    private var moving = false
    private var wait = 0f

    private var lastNs = System.nanoTime()
    private val startNs = lastNs
    private val bgPaint = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val oval = RectF()
    private val path = Path()
    private val pos = FloatArray(2)
    private val domeShaders = HashMap<Look, LinearGradient>()
    private val glowShaders = HashMap<Look, RadialGradient>()

    private fun rand(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private fun alphaOf(c: Int, a: Float) = Color.argb((255 * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    override fun onSizeChanged(newW: Int, newH: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(newW, newH, oldW, oldH)
        w = newW / density
        h = newH / density
        if (w <= 0f || h <= 0f) return
        bgPaint.shader = LinearGradient(0f, 0f, 0f, newH.toFloat(), Color.BLACK, "#2B2B2B".toColorInt(), Shader.TileMode.CLAMP)
        if (still) { placeStill(); return }
        if (trail.isEmpty()) {
            lx = w * 0.5f
            ly = h * 0.4f
            var x = lx - 96f
            while (x < lx) { trail.add(floatArrayOf(x, ly)); x += 3f }
            startFlight()
        } else {
            // otoceni / zmena velikosti: konvoj zustane na obrazovce
            lx = lx.coerceIn(edge, max(edge, w - edge)); ly = ly.coerceIn(edge, max(edge, h - edge))
            toX = toX.coerceIn(edge, max(edge, w - edge)); toY = toY.coerceIn(edge, max(edge, h - edge))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastNs = System.nanoTime()
        invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && !still) { lastNs = System.nanoTime(); invalidate() }
    }

    // Tri nahodna mista od sebe dost daleko (aby se UFO neprekryvala)
    private fun placeStill() {
        val minDist = min(w, h) * 0.25f
        for (i in stillPos.indices) {
            for (tries in 0 until 30) {
                stillPos[i][0] = rand(edge, max(edge + 1f, w - edge))
                stillPos[i][1] = rand(edge, max(edge + 1f, h - edge))
                if ((0 until i).all { j -> hypot(stillPos[i][0] - stillPos[j][0], stillPos[i][1] - stillPos[j][1]) >= minDist }) break
            }
        }
    }

    // Novy let: nahodny cil (dost daleko), nahodna doba 3-15 s, nahodne rovne nebo klikate
    private fun startFlight() {
        fromX = lx; fromY = ly
        val minDist = min(w, h) * 0.3f
        var tx = lx
        var ty = ly
        for (i in 0 until 12) {
            tx = rand(edge, max(edge + 1f, w - edge))
            ty = rand(edge, max(edge + 1f, h - edge))
            if (hypot(tx - lx, ty - ly) >= minDist) break
        }
        toX = tx; toY = ty
        val dist = max(1f, hypot(toX - fromX, toY - fromY))
        nx = -(toY - fromY) / dist
        ny = (toX - fromX) / dist
        amp = if (rnd.nextBoolean()) rand(18f, 58f) else 0f
        waves = 1 + rnd.nextInt(3)
        dur = rand(3f, 15f)
        prog = 0f
        moving = true
    }

    private fun step(dt: Float) {
        if (!moving) {
            wait -= dt
            if (wait <= 0f) startFlight()
            return
        }
        prog = min(1f, prog + dt / dur)
        val e = prog * prog * (3f - 2f * prog)   // plynuly rozjezd a dojezd
        val off = amp * sin(2f * PI.toFloat() * waves * e) * sin(PI.toFloat() * e)
        lx = (fromX + (toX - fromX) * e + nx * off).coerceIn(edge * 0.5f, max(edge * 0.5f, w - edge * 0.5f))
        ly = (fromY + (toY - fromY) * e + ny * off).coerceIn(edge * 0.5f, max(edge * 0.5f, h - edge * 0.5f))
        val last = trail.lastOrNull()
        if (last == null || hypot(lx - last[0], ly - last[1]) >= 1.5f) {
            trail.add(floatArrayOf(lx, ly))
            while (trail.size > 320) trail.removeAt(0)
        }
        if (prog >= 1f) { moving = false; wait = waitSec }
    }

    // Misto na stope [dist] dp za vedouci lodi
    private fun trailAt(dist: Float) {
        var rem = dist
        var px = lx
        var py = ly
        for (i in trail.indices.reversed()) {
            val q = trail[i]
            val seg = hypot(px - q[0], py - q[1])
            if (seg > 0f && seg >= rem) {
                val k = rem / seg
                pos[0] = px + (q[0] - px) * k
                pos[1] = py + (q[1] - py) * k
                return
            }
            rem -= seg
            px = q[0]; py = q[1]
        }
        pos[0] = px; pos[1] = py
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = min(0.05f, (now - lastNs) / 1e9f)
        lastNs = now
        val t = (now - startNs) / 1e9f
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (still) {
            if (w > 0f && h > 0f) canvas.withScale(density, density) {
                for (i in stillPos.indices.reversed()) drawUfo(canvas, stillPos[i][0], stillPos[i][1], if (i == 2) red else green, 0f, i)
            }
            return   // stoji = zadne dalsi prekreslovani
        }
        if (w > 0f && h > 0f) {
            step(dt)
            canvas.withScale(density, density) {
                // odzadu: cervene, druhe zelene, vedouci zelene
                for (i in backs.indices.reversed()) {
                    trailAt(backs[i])
                    drawUfo(canvas, pos[0], pos[1], if (i == 2) red else green, t, i)
                }
            }
        }
        if (isAttachedToWindow && isShown) postInvalidateOnAnimation()   // schovane pozadi nekresli (setri baterku)
    }

    private fun drawUfo(c: Canvas, x: Float, y: Float, look: Look, t: Float, index: Int) {
        val s = size
        val bob = sin(t * 2f + index * 1.7f) * (if (moving) 0.8f else 1.6f)
        val heading = if (moving) ((toX - fromX) / 220f).coerceIn(-1f, 1f) * 0.1f else 0f
        val tilt = sin(t * 1.4f + index * 2.1f) * 0.06f + heading
        c.save()
        c.translate(x, y + bob)

        // mekka zare pod lodi
        c.save()
        c.translate(0f, s * 0.52f)
        c.scale(s * 1.6f, s * 0.55f)
        fill.shader = glowShaders.getOrPut(look) { RadialGradient(0f, 0f, 1f, look.glow, look.glow and 0x00FFFFFF, Shader.TileMode.CLAMP) }
        c.drawCircle(0f, 0f, 1f, fill)
        fill.shader = null
        c.restore()

        c.rotate(Math.toDegrees(tilt.toDouble()).toFloat())

        // kopule
        oval.set(-s * 0.58f, -s * 0.5f, s * 0.58f, s * 0.34f)
        path.reset()
        path.addArc(oval, 180f, 180f)
        path.close()
        fill.shader = domeShaders.getOrPut(look) {
            LinearGradient(0f, -s * 0.55f, 0f, s * 0.25f, intArrayOf(look.light, look.main, look.dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawPath(path, fill)
        fill.shader = null
        fill.color = Color.argb(128, 255, 255, 255)
        c.save()
        c.translate(-s * 0.17f, -s * 0.3f)
        c.rotate(-28f)
        oval.set(-s * 0.16f, -s * 0.09f, s * 0.16f, s * 0.09f)
        c.drawOval(oval, fill)
        c.restore()

        // prstenec
        oval.set(-s, -s * 0.14f, s, s * 0.46f)
        fill.color = alphaOf(look.main, 0.1f)
        c.drawOval(oval, fill)
        stroke.color = alphaOf(look.main, 0.28f); stroke.strokeWidth = 4f
        c.drawOval(oval, stroke)
        stroke.color = look.main; stroke.strokeWidth = 1.6f
        c.drawOval(oval, stroke)

        // blikajici svetylka
        val ks = floatArrayOf(-0.68f, -0.23f, 0.23f, 0.68f)
        for (i in ks.indices) {
            val k = ks[i]
            val a = 0.35f + 0.65f * abs(sin(t * 3f + index + i * 1.3f))
            fill.color = if (i % 2 == 1) Color.argb((255 * a).toInt(), 255, 255, 255) else alphaOf(look.main, a)
            c.drawCircle(k * s, s * 0.16f + k * k * s * 0.14f, max(1f, s * 0.09f), fill)
        }
        c.restore()
    }
}

// Prida pozadi s UFO pres celou plochu (nad web, pod tlacitka a listy); clickable = pohlti klepnuti do webu
internal fun MainActivity.addSetupBackdrop(elevationDp: Int, clickable: Boolean): View {
    val v = SetupBackdropView(this)
    v.isClickable = clickable
    v.elevation = dp(elevationDp).toFloat()
    val lp = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, 0)
    lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
    lp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
    lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
    (root as android.view.ViewGroup).addView(v, lp)
    v.alpha = 0f
    v.animate().alpha(1f).setDuration(220).start()
    return v
}

// Uvodni nastaveni: pozadi je videt od uvodni zpravy do konce rychleho nastaveni
internal fun MainActivity.showFirstBackdrop() {
    if (firstBackdrop == null) firstBackdrop = addSetupBackdrop(4, true)   // pod bocni listou (6) a tlacitky (8)
}

internal fun MainActivity.hideFirstBackdrop() {
    val v = firstBackdrop ?: return
    firstBackdrop = null
    v.animate().alpha(0f).setDuration(220).withEndAction { (root as android.view.ViewGroup).removeView(v) }.start()
}
