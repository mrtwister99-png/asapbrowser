package com.example.actimelteambrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pozadi "UFO Random Field": pohled z orbitalni stanice na roj 28 UFO (3 vrstvy hloubky) nad nocni mlhovinou a
 * jemnou teckovanou sitou. Lode se nahodne presouvaji, levituji, cekaji; pri srazce ve stejne vrstve vybuchne
 * barevny ohnostroj. Dotyk: klepnuti = vlastni ohnostroj, prst na plose = odpuzovac (lode panikari a utikaji,
 * kazdy 10. vstup do zony = portal a unik na druhou stranu, 2,5 % sance na modreho stalkera, ktery prileti
 * k prstu a pak ho sleduje). Port z React/Canvas verze (App.tsx); vsechny rozmery jsou v dp.
 */
class UfoFieldView(context: Context) : View(context) {

    // ---------------------------------------------------------------- typy

    private enum class State { MOVING, LEVITATING, WAITING, PANIC }
    private enum class Mode { NORMAL, PORTAL_ENTER, PORTAL_HIDDEN, PORTAL_EXIT, CHASING, FOLLOWING }

    /** Vrstva hloubky: vzdalena -> blizka. par = jak silne se vrstva posouva s kamerou. */
    private class LayerCfg(
        val size: Float, val speedMul: Float, val par: Float,
        val main: Int, val light: Int, val dark: Int, val alpha: Float
    )

    private class Ufo(
        var x: Float, var y: Float, val layer: Int, val filled: Boolean, val gray: Boolean, var size: Float
    ) {
        var tx = 0f; var ty = 0f
        var rx = 0f; var ry = 0f            // posledni vykreslena poloha na obrazovce
        var speed = 1f
        var state = State.MOVING
        var timer = 0f
        var bobPhase = 0f; var tiltSeed = 0f
        var cooldown = 0f
        var panic = false
        var calmTimer = 0f
        var jx = 0f; var jy = 0f; var rotJ = 0f
        var mode = Mode.NORMAL
        var inRadius = false
        var scaleMul = 1f; var scaleTarget = 1f
        var chaseSpeed = 6f
        var followAngle = 0f; var followDist = 55f
        var idleTimer = 0f
    }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float,
        val sparkle: Boolean, val rainbow: Boolean, val hue: Float
    )

    private class Burst(
        val x: Float, val y: Float, val particles: List<Particle>, val color: Int
    ) {
        var ringR = 3f; var ringAlpha = 0.85f; var flash = 1f
    }

    private class Portal(
        val ufo: Ufo, val entry: Boolean, val x: Float, val y: Float,
        val exitDirX: Float, val exitDirY: Float
    ) {
        var progress = 0f
        var phase = 0            // 0 = otevira se, 1 = otevreny, 2 = zavira se
        var spin = 0f
        var released = false
    }

    private class PendingExit(val ufo: Ufo, var delay: Float, val x: Float, val y: Float, val dirX: Float, val dirY: Float)

    // ---------------------------------------------------------------- konstanty

    private fun hex(s: String) = Color.parseColor(s)

    private val layers = arrayOf(
        LayerCfg(5f, 0.45f, 0.3f, hex("#46584c"), hex("#5d7263"), hex("#232e27"), 0.5f),
        LayerCfg(8.5f, 0.85f, 0.62f, hex("#35b264"), hex("#4ed383"), hex("#1f6b3a"), 0.9f),
        LayerCfg(12f, 1.3f, 1f, hex("#00E676"), hex("#5cffab"), hex("#2e9e57"), 1f)
    )
    private val fireworkColors = intArrayOf(
        hex("#FF0000"), hex("#FFFF00"), hex("#00E676"), hex("#00BFFF"), hex("#FF00FF"),
        hex("#FF8C00"), hex("#00FF00"), hex("#FF1493"), hex("#00FFFF"), hex("#FFD700")
    )
    private val colChaseMain = hex("#0080FF")
    private val colChaseLight = hex("#00BFFF")
    private val colChaseDark = hex("#003a75")
    private val colFollowPurple = hex("#9C27B0")
    private val colFollowPurpleLight = hex("#E040FB")
    private val colFollowDark = hex("#4a148c")
    private val colPanicDark = hex("#7a0000")
    private val colGray = hex("#4c4c4c")
    private val colBase = hex("#0A0A0A")
    private val margin = 70f
    private val dangerRadius = 160f
    private val panicRadius = 100f
    private val bgPad = 72f

    // ---------------------------------------------------------------- stav

    private val rnd = java.util.Random()
    private val ufos = ArrayList<Ufo>()
    private val bursts = ArrayList<Burst>()
    private val portals = ArrayList<Portal>()
    private val pendingExits = ArrayList<PendingExit>()
    private var radiusEnterCount = 0

    private var mouseX = 0f
    private var mouseY = 0f
    private var mouseActive = false
    private var mouseLastMoveMs = 0L

    private var w = 0f            // rozmery v dp
    private var h = 0f
    private var ww = 0f           // svet: 2x obrazovka
    private var wh = 0f
    private var camBaseX = 0f
    private var camBaseY = 0f
    private var driftX = 0f
    private var driftY = 0f

    private val density = resources.displayMetrics.density
    private var bg: Bitmap? = null
    private var bgScale = 1f
    private var vignette: Paint? = null
    private var lastNs = System.nanoTime()
    private val startNs = lastNs
    private var running = true

    // opakovane pouzite nastroje kresleni (zadne alokace v kazdem snimku, kde to jde)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val oval = RectF()
    private val path = Path()
    private val glowShaders = HashMap<Int, RadialGradient>()

    private fun rand(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private fun clamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))
    private fun withAlpha(c: Int, a: Float): Int = Color.argb((Color.alpha(c) * a.coerceIn(0f, 1f)).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    private fun rgba(r: Int, g: Int, b: Int, a: Float): Int = Color.argb((a * 255).toInt().coerceIn(0, 255), r, g, b)

    // ---------------------------------------------------------------- zivotni cyklus a dotyk

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastNs = System.nanoTime()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(newW: Int, newH: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(newW, newH, oldW, oldH)
        w = newW / density
        h = newH / density
        if (w <= 0f || h <= 0f) return
        bgScale = min(density, 1.5f)
        bg = buildBackground(w + bgPad * 2, h + bgPad * 2, bgScale)
        ww = w * 2
        wh = h * 2
        if (ufos.isEmpty()) buildFleet()
        for (u in ufos) {
            u.x = clamp(u.x, margin, max(margin, ww - margin)); u.y = clamp(u.y, margin, max(margin, wh - margin))
            u.tx = clamp(u.tx, margin, max(margin, ww - margin)); u.ty = clamp(u.ty, margin, max(margin, wh - margin))
        }
        // vinetace: tmavsi okraje jako pohled oknem stanice (elipsa 88 % x 78 % se stredem 50 % / 44 %)
        val shader = RadialGradient(0f, 0f, 1f, intArrayOf(0, 0, Color.argb(158, 0, 0, 0)), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        val m = Matrix()
        m.postScale(0.88f * w, 0.78f * h)
        m.postTranslate(w * 0.5f, h * 0.44f)
        shader.setLocalMatrix(m)
        vignette = Paint().apply { this.shader = shader }
    }

    /** Dotyk (souradnice v pixelech view): volat z onTouchEvent, nebo z nadrazene obrazovky, kdyz dotyky neprijima sam. */
    fun touchDown(xPx: Float, yPx: Float) {
        touchMove(xPx, yPx)
        if (w > 0f) spawnBurst(xPx / density, yPx / density)
    }

    fun touchMove(xPx: Float, yPx: Float) {
        mouseX = xPx / density
        mouseY = yPx / density
        mouseActive = true
        mouseLastMoveMs = System.nanoTime() / 1_000_000L
    }

    fun touchUp() { mouseActive = false }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchDown(e.x, e.y)
            MotionEvent.ACTION_MOVE -> touchMove(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchUp()
        }
        return true
    }

    // ---------------------------------------------------------------- tvorba sveta

    private fun pickTarget(u: Ufo) {
        val ang = rnd.nextFloat() * 2f * PI.toFloat()
        val dist = rand(120f, 560f)
        u.tx = clamp(u.x + cos(ang) * dist, margin, max(margin, ww - margin))
        u.ty = clamp(u.y + sin(ang) * dist, margin, max(margin, wh - margin))
        u.speed = rand(0.3f, 2.5f) * layers[u.layer].speedMul
    }

    private fun makeUfo(layer: Int, filled: Boolean, gray: Boolean): Ufo {
        val u = Ufo(
            rand(margin, max(margin + 1f, ww - margin)), rand(margin, max(margin + 1f, wh - margin)),
            layer, filled, gray, layers[layer].size * rand(0.9f, 1.12f)
        )
        u.speed = rand(0.3f, 2.5f) * layers[layer].speedMul
        u.bobPhase = rnd.nextFloat() * 2f * PI.toFloat()
        u.tiltSeed = rnd.nextFloat() * 2f * PI.toFloat()
        pickTarget(u)
        return u
    }

    // 28 lodi: 12 vzdalenych, 9 strednich, 7 blizkych (kresleno od nejvzdalenejsich)
    private fun buildFleet() {
        ufos.clear()
        val plan = arrayOf(intArrayOf(0, 12, 5), intArrayOf(1, 9, 5), intArrayOf(2, 7, 5))
        for (p in plan) for (i in 0 until p[1]) ufos.add(makeUfo(p[0], i < p[2], i % 2 == 1))
    }

    private fun spawnBurst(x: Float, y: Float) {
        val count = rand(50f, 71f).toInt()
        val list = ArrayList<Particle>(count)
        for (i in 0 until count) {
            val ang = rnd.nextFloat() * 2f * PI.toFloat()
            val spd = rand(35f, 330f)
            val maxLife = rand(0.85f, 1.8f)
            list.add(
                Particle(
                    x, y, cos(ang) * spd, sin(ang) * spd - rand(0f, 45f), maxLife, maxLife,
                    fireworkColors[rnd.nextInt(fireworkColors.size)], rand(0.8f, 2.3f),
                    rnd.nextFloat() < 0.32f, rnd.nextFloat() < 0.22f, rand(0f, 360f)
                )
            )
        }
        bursts.add(Burst(x, y, list, fireworkColors[rnd.nextInt(fireworkColors.size)]))
    }

    // Nocni pozadi: mlhoviny, slabe svisle cary a jemna sit tecek (predkresleno, driftuje pomalu)
    private fun buildBackground(bw: Float, bh: Float, s: Float): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, (bw * s).toInt()), max(1, (bh * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = hex("#070907")
        c.drawRect(0f, 0f, bw, bh, p)
        val nebula = arrayOf(
            floatArrayOf(0.5f, 0.46f, 0.62f, 0f, 110f, 58f, 0.10f),
            floatArrayOf(0.34f, 0.62f, 0.4f, 0f, 70f, 80f, 0.07f),
            floatArrayOf(0.68f, 0.3f, 0.36f, 24f, 52f, 96f, 0.07f),
            floatArrayOf(0.52f, 0.5f, 0.3f, 0f, 230f, 118f, 0.045f)
        )
        for (n in nebula) {
            val r = max(bw, bh) * n[2]
            val col = rgba(n[3].toInt(), n[4].toInt(), n[5].toInt(), n[6])
            p.shader = RadialGradient(bw * n[0], bh * n[1], r, col, rgba(0, 0, 0, 0f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, bw, bh, p)
        }
        p.shader = null
        p.color = hex("#0e0f0e")
        var x = 64f
        while (x < bw) { c.drawRect(x, 0f, x + 1f, bh, p); x += 128f }
        val step = 22f
        var row = 0
        var y = step / 2
        while (y < bh) {
            var col = 0
            var xx = step / 2
            while (xx < bw) {
                p.color = if ((row * 31 + col * 17) % 53 == 0) rgba(0, 230, 118, 0.1f) else hex("#1b1b1b")
                c.drawCircle(xx, y, 0.45f, p)
                xx += step; col++
            }
            y += step; row++
        }
        return bmp
    }

    private fun wrapAxis(v: Float, maxV: Float): Float {
        val m = maxV + 140f
        return ((((v + 70f) % m) + m) % m) - 70f
    }

    private fun camX(layer: Int) = camBaseX + driftX * (ww - w) * 0.3f * layers[layer].par
    private fun camY(layer: Int) = camBaseY + driftY * (wh - h) * 0.3f * layers[layer].par

    // ---------------------------------------------------------------- simulace

    private fun releaseToNormal(u: Ufo) {
        u.x = clamp(u.rx + camX(u.layer), margin, max(margin, ww - margin))
        u.y = clamp(u.ry + camY(u.layer), margin, max(margin, wh - margin))
        u.mode = Mode.NORMAL
        u.scaleTarget = 1f
        u.panic = false
        u.jx = 0f; u.jy = 0f; u.rotJ = 0f
        pickTarget(u)
        u.state = State.MOVING
    }

    // Vstup lode do zony kolem prstu: kazdy 10. vstup = portal, jinak 2,5 % sance na modreho stalkera (max 2 naraz)
    private fun onRadiusEnter(u: Ufo) {
        radiusEnterCount += 1
        u.panic = false
        u.jx = 0f; u.jy = 0f; u.rotJ = 0f
        if (radiusEnterCount % 10 == 0) {
            val ang = atan2(u.ry - mouseY, u.rx - mouseX)
            val p = Portal(
                u, true,
                clamp(u.rx + cos(ang) * 70f, 36f, max(36f, w - 36f)),
                clamp(u.ry + sin(ang) * 70f, 36f, max(36f, h - 36f)),
                cos(ang), sin(ang)
            )
            p.spin = rand(0f, 2f * PI.toFloat())
            portals.add(p)
            u.mode = Mode.PORTAL_ENTER
        } else if (ufos.count { it.mode == Mode.CHASING || it.mode == Mode.FOLLOWING } < 2 && rnd.nextFloat() < 0.025f) {
            u.mode = Mode.CHASING
            u.scaleTarget = 1.5f
            u.chaseSpeed = rand(5f, 7f)
            u.idleTimer = 0f
            u.followAngle = rand(0f, 2f * PI.toFloat())
            u.followDist = rand(50f, 60f)
        }
    }

    private fun step(dt: Float, t: Float, nowMs: Long) {
        driftX = sin(t * 0.045f)
        driftY = cos(t * 0.033f)
        camBaseX = (ww - w) / 2
        camBaseY = (wh - h) / 2

        // odpuzovac prstu: lode v zone panikari a utikaji
        for (u in ufos) {
            val dxs = u.rx - mouseX
            val dys = u.ry - mouseY
            val distC = hypot(dxs, dys)
            val inside = mouseActive && distC < dangerRadius
            if (u.mode != Mode.NORMAL) { u.inRadius = inside; continue }
            if (inside && !u.inRadius) onRadiusEnter(u)
            u.inRadius = inside
            if (u.mode != Mode.NORMAL) continue
            if (inside) {
                val deep = distC < panicRadius
                u.panic = true
                u.calmTimer = 0f
                u.state = State.PANIC
                val ang = atan2(dys, dxs)
                u.tx = clamp(u.x + cos(ang) * 300f, margin, max(margin, ww - margin))
                u.ty = clamp(u.y + sin(ang) * 300f, margin, max(margin, wh - margin))
                u.speed = if (deep) rand(4.5f, 6.5f) else rand(3.5f, 5.5f)
                val amp = if (deep) 6f else 3f
                u.jx = rand(-amp, amp)
                u.jy = rand(-amp, amp)
                u.rotJ = rand(-0.3f, 0.3f) * (if (deep) 1.8f else 1f)
            } else if (u.panic) {
                u.calmTimer += dt
                u.jx *= 0.75f; u.jy *= 0.75f; u.rotJ *= 0.75f
                if (u.calmTimer >= 0.5f) {
                    u.panic = false
                    u.jx = 0f; u.jy = 0f; u.rotJ = 0f
                    if (u.state == State.PANIC) { pickTarget(u); u.state = State.MOVING }
                }
            }
        }

        // stavovy automat lodi (svetove souradnice)
        for (u in ufos) {
            if (u.mode != Mode.NORMAL) continue
            if (u.cooldown > 0f) u.cooldown -= dt
            when (u.state) {
                State.MOVING, State.PANIC -> {
                    val dx = u.tx - u.x
                    val dy = u.ty - u.y
                    val dist = hypot(dx, dy)
                    val stp = u.speed * 60f * dt
                    if (dist <= max(4f, stp)) {
                        u.x = u.tx; u.y = u.ty
                        if (u.state == State.PANIC) pickTarget(u)
                        else { u.state = State.LEVITATING; u.timer = rand(0.8f, 2f) }
                    } else {
                        u.x += dx / dist * stp
                        u.y += dy / dist * stp
                    }
                }
                State.LEVITATING -> {
                    u.timer -= dt
                    if (u.timer <= 0f) { u.state = State.WAITING; u.timer = rand(1f, 4f) }
                }
                State.WAITING -> {
                    u.timer -= dt
                    if (u.timer <= 0f) { pickTarget(u); u.state = State.MOVING }
                }
            }
        }

        // projekce svet -> obrazovka s paralaxou
        for (u in ufos) {
            if (u.mode != Mode.NORMAL) continue
            u.rx = wrapAxis(u.x - camX(u.layer), w)
            u.ry = wrapAxis(u.y - camY(u.layer), h)
        }

        // specialni rezimy: portaly a modri stalkeri (obrazovkove souradnice)
        val cursorIdle = !mouseActive || nowMs - mouseLastMoveMs > 3000
        for (i in portals.indices.reversed()) {
            val p = portals[i]
            p.spin += dt * 2.4f
            if (p.phase == 0) {
                p.progress = min(1f, p.progress + dt / 0.4f)
                if (p.progress >= 1f) p.phase = 1
            } else if (p.phase == 2) {
                p.progress = max(0f, p.progress - dt / 0.4f)
                if (p.progress <= 0f) {
                    portals.removeAt(i)
                    if (p.entry) pendingExits.add(PendingExit(p.ufo, 0.3f, p.x, p.y, p.exitDirX, p.exitDirY))
                    continue
                }
            }
            if (!p.entry && p.phase == 1 && !p.released) {
                p.released = true
                p.ufo.mode = Mode.PORTAL_EXIT
                p.ufo.rx = p.x; p.ufo.ry = p.y
                p.ufo.scaleMul = 0.15f
                p.ufo.scaleTarget = 1f
                p.phase = 2
            }
        }
        for (i in pendingExits.indices.reversed()) {
            val pe = pendingExits[i]
            pe.delay -= dt
            if (pe.delay > 0f) continue
            pendingExits.removeAt(i)
            var ex = w - pe.x + rand(-40f, 40f)
            var ey = h - pe.y + rand(-40f, 40f)
            if (abs(ex - pe.x) < w * 0.7f) ex = if (pe.x < w / 2) w * 0.88f else w * 0.12f
            if (abs(ey - pe.y) < h * 0.7f) ey = if (pe.y < h / 2) h * 0.88f else h * 0.12f
            val p = Portal(pe.ufo, false, clamp(ex, 40f, max(40f, w - 40f)), clamp(ey, 40f, max(40f, h - 40f)), -pe.dirX, -pe.dirY)
            p.spin = rand(0f, 2f * PI.toFloat())
            portals.add(p)
        }
        for (u in ufos) {
            u.scaleMul += (u.scaleTarget - u.scaleMul) * min(1f, dt * 6f)
            when (u.mode) {
                Mode.PORTAL_ENTER -> {
                    val p = portals.firstOrNull { it.ufo === u && it.entry }
                    if (p == null) { releaseToNormal(u); continue }
                    val dx = p.x - u.rx
                    val dy = p.y - u.ry
                    val d = hypot(dx, dy)
                    if (d < 10f) { u.mode = Mode.PORTAL_HIDDEN; p.phase = 2 }
                    else {
                        val stp = 6f * 60f * dt
                        u.rx += dx / d * min(stp, d)
                        u.ry += dy / d * min(stp, d)
                        u.scaleTarget = clamp(d / 46f, 0.12f, 1f)
                    }
                }
                Mode.PORTAL_EXIT -> {
                    val p = portals.firstOrNull { it.ufo === u && !it.entry }
                    u.rx += (p?.exitDirX ?: 1f) * 5f * 60f * dt
                    u.ry += (p?.exitDirY ?: 0f) * 5f * 60f * dt
                    u.scaleTarget = 1f
                    if (p == null || hypot(u.rx - p.x, u.ry - p.y) > 64f) releaseToNormal(u)
                }
                Mode.CHASING -> {
                    if (cursorIdle) {
                        u.idleTimer += dt
                        if (u.idleTimer >= 3f) releaseToNormal(u)
                    } else {
                        u.idleTimer = 0f
                        val dx = mouseX - u.rx
                        val dy = mouseY - u.ry
                        val d = hypot(dx, dy)
                        if (d < 45f) { u.mode = Mode.FOLLOWING; u.scaleTarget = 1f }
                        else {
                            val stp = u.chaseSpeed * 60f * dt
                            u.rx += dx / d * min(stp, d)
                            u.ry += dy / d * min(stp, d)
                        }
                    }
                }
                Mode.FOLLOWING -> {
                    if (cursorIdle) {
                        u.idleTimer += dt
                        if (u.idleTimer >= 3f) releaseToNormal(u)
                    } else {
                        u.idleTimer = 0f
                        u.followAngle += dt * 0.7f
                        val txp = mouseX + cos(u.followAngle) * u.followDist
                        val typ = mouseY + sin(u.followAngle) * u.followDist
                        val k = min(1f, 0.08f * dt * 60f)
                        u.rx += (txp - u.rx) * k
                        u.ry += (typ - u.ry) * k
                    }
                }
                else -> {}
            }
        }

        // srazky ve stejne vrstve -> ohnostroj
        for (i in ufos.indices) for (j in i + 1 until ufos.size) {
            val a = ufos[i]
            val b = ufos[j]
            if (a.layer != b.layer) continue
            if (a.mode != Mode.NORMAL || b.mode != Mode.NORMAL) continue
            if (a.cooldown > 0f || b.cooldown > 0f) continue
            if (hypot(b.rx - a.rx, b.ry - a.ry) < (a.size + b.size) * 1.25f) {
                spawnBurst((a.rx + b.rx) / 2, (a.ry + b.ry) / 2)
                for (u in arrayOf(a, b)) {
                    var dirx = u.x - (a.x + b.x) / 2
                    var diry = u.y - (a.y + b.y) / 2
                    val len = hypot(dirx, diry)
                    if (len < 0.001f) {
                        val ang = rnd.nextFloat() * 2f * PI.toFloat()
                        dirx = cos(ang); diry = sin(ang)
                    } else { dirx /= len; diry /= len }
                    val d = rand(160f, 380f)
                    u.tx = clamp(u.x + dirx * d, margin, max(margin, ww - margin))
                    u.ty = clamp(u.y + diry * d, margin, max(margin, wh - margin))
                    u.speed = rand(1.4f, 2.5f) * layers[u.layer].speedMul
                    u.state = State.MOVING
                    u.cooldown = 1.4f
                }
            }
        }

        // fyzika ohnostroju
        for (i in bursts.indices.reversed()) {
            val b = bursts[i]
            b.ringR += 330f * dt
            b.ringAlpha -= dt * 1.05f
            b.flash -= dt * 1.9f
            var alive = b.ringAlpha > 0.01f || b.flash > 0.01f
            for (p in b.particles) {
                if (p.life <= 0f) continue
                p.vy += 200f * dt
                p.vx *= 1f - 0.6f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.life -= dt
                if (p.life > 0f) alive = true
            }
            if (!alive) bursts.removeAt(i)
        }
    }

    // ---------------------------------------------------------------- kresleni

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = min(0.05f, (now - lastNs) / 1e9f)
        lastNs = now
        val t = (now - startNs) / 1e9f
        canvas.drawColor(colBase)
        if (w > 0f && h > 0f) {
            step(dt, t, now / 1_000_000L)
            canvas.save()
            canvas.scale(density, density)
            bg?.let {
                val panX = -bgPad + driftX * 20f
                val panY = -bgPad + driftY * 16f
                oval.set(panX, panY, panX + w + bgPad * 2, panY + h + bgPad * 2)
                canvas.drawBitmap(it, null, oval, bgPaint)
            }
            for (p in portals) drawPortal(canvas, p, t)
            for (u in ufos) if (u.mode != Mode.PORTAL_HIDDEN) drawUfo(canvas, u, t)
            for (b in bursts) drawBurst(canvas, b, t)
            if (mouseActive) drawDangerZone(canvas, t)
            vignette?.let { canvas.drawRect(0f, 0f, w, h, it) }
            canvas.restore()
        }
        if (running) postInvalidateOnAnimation()
    }

    private fun drawDangerZone(c: Canvas, t: Float) {
        val pulse = sin(t * 4f) * 5f
        fill.shader = null
        fill.color = rgba(255, 0, 0, 0.08f)
        c.drawCircle(mouseX, mouseY, dangerRadius + pulse, fill)
        stroke.color = rgba(255, 60, 40, 0.4f + 0.2f * sin(t * 4f)); stroke.strokeWidth = 1.4f
        c.drawCircle(mouseX, mouseY, dangerRadius + pulse, stroke)
        stroke.color = rgba(255, 210, 0, 0.35f); stroke.strokeWidth = 1f
        c.drawCircle(mouseX, mouseY, panicRadius + pulse * 0.6f, stroke)
        fill.color = rgba(255, 80, 60, 0.85f)
        c.drawCircle(mouseX, mouseY, 2.5f, fill)
    }

    private fun glowShader(color: Int): RadialGradient =
        glowShaders.getOrPut(color) {
            RadialGradient(0f, 0f, 1f, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }

    private fun drawUfo(c: Canvas, u: Ufo, t: Float) {
        val cfg = layers[u.layer]
        val s = u.size * u.scaleMul
        val bobAmp = (if (u.state == State.LEVITATING) 1.6f else 1f) * (0.3f + u.layer * 0.2f)
        val bobY = sin(t * 2f + u.bobPhase) * bobAmp
        val heading = if (u.state == State.MOVING) clamp((u.tx - u.x) / 220f, -1f, 1f) * 0.1f else 0f
        val tilt = sin(t * 1.4f + u.tiltSeed) * 0.06f + heading
        val ga = cfg.alpha
        val panicking = u.panic
        val chasing = u.mode == Mode.CHASING
        val following = u.mode == Mode.FOLLOWING
        val stalk = chasing || following
        val purple = floor(t / 0.15f).toInt() % 2 == 1
        val stalkMain = if (chasing) colChaseMain else if (purple) colFollowPurple else Color.WHITE
        val stalkLight = if (chasing) colChaseLight else if (purple) colFollowPurpleLight else Color.WHITE
        val stalkDark = if (chasing) colChaseDark else colFollowDark
        val blink = if (sin(t * 20f + u.bobPhase) > 0f) Color.RED else Color.YELLOW
        val mainCol = if (stalk) stalkMain else cfg.main

        c.save()
        c.translate(u.rx + u.jx, u.ry + bobY + u.jy)

        // rozmazana stopa pri panice
        if (panicking) {
            for (k in 2 downTo 1) {
                c.save()
                c.translate(-u.jx * k * 1.8f, -u.jy * k * 1.8f)
                oval.set(-s, s * 0.16f - s * 0.3f, s, s * 0.16f + s * 0.3f)
                stroke.color = withAlpha(blink, 0.2f / k * ga); stroke.strokeWidth = 1.1f
                c.drawOval(oval, stroke)
                c.restore()
            }
        }

        // mekka zare pod lodi
        val glowR = s * (1.1f + u.layer * 0.25f)
        val glowCol = when {
            panicking -> rgba(255, 70, 0, 0.22f)
            chasing -> rgba(0, 128, 255, 0.32f)
            following -> rgba(224, 64, 251, 0.24f)
            u.filled -> rgba(0, 230, 118, 0.05f + u.layer * 0.06f)
            else -> rgba(0, 230, 118, 0.04f)
        }
        c.save()
        c.translate(0f, s * 0.52f)
        c.scale(glowR, glowR * 0.34f)
        fill.shader = glowShader(withAlpha(glowCol, ga))
        c.drawCircle(0f, 0f, 1f, fill)
        fill.shader = null
        c.restore()

        c.rotate(Math.toDegrees((tilt + u.rotJ).toDouble()).toFloat())
        if (panicking) {
            val pulse = 0.8f + 0.6f * abs(sin(t * 16f + u.bobPhase))
            c.scale(pulse, pulse)
        }

        if (u.filled) {
            // kopule: barevny prechod
            oval.set(-s * 0.58f, -s * 0.08f - s * 0.42f, s * 0.58f, -s * 0.08f + s * 0.42f)
            path.reset()
            path.addArc(oval, 180f, 180f)
            path.close()
            val c0 = withAlpha(if (panicking) Color.WHITE else if (stalk) stalkLight else cfg.light, ga)
            val c1 = withAlpha(if (panicking) blink else mainCol, ga)
            val c2 = withAlpha(if (panicking) colPanicDark else if (stalk) stalkDark else cfg.dark, ga)
            fill.shader = LinearGradient(0f, -s * 0.55f, 0f, s * 0.25f, intArrayOf(c0, c1, c2), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            fill.shader = null
            if (u.layer == 2) {
                fill.color = withAlpha(Color.WHITE, 0.5f * ga)
                c.save()
                c.translate(-s * 0.17f, -s * 0.3f)
                c.rotate(Math.toDegrees(-0.5).toFloat())
                oval.set(-s * 0.16f, -s * 0.09f, s * 0.16f, s * 0.09f)
                c.drawOval(oval, fill)
                c.restore()
            }
            // prstenec
            oval.set(-s, s * 0.16f - s * 0.3f, s, s * 0.16f + s * 0.3f)
            fill.color = withAlpha(
                when { panicking -> rgba(255, 0, 0, 0.10f); chasing -> rgba(0, 128, 255, 0.14f); following -> rgba(224, 64, 251, 0.12f); else -> rgba(0, 230, 118, 0.08f) },
                ga
            )
            c.drawOval(oval, fill)
            val ringCol = if (panicking) blink else mainCol
            if (u.layer == 2) {   // zare kolem prstence (blizka vrstva)
                stroke.color = withAlpha(ringCol, 0.28f * ga); stroke.strokeWidth = 4.5f
                c.drawOval(oval, stroke)
            }
            stroke.color = withAlpha(ringCol, ga); stroke.strokeWidth = if (u.layer == 2) 1.8f else 1.2f
            c.drawOval(oval, stroke)
            // blikajici svetylka (ne na vzdalene vrstve)
            if (u.layer > 0) {
                val ks = floatArrayOf(-0.68f, -0.23f, 0.23f, 0.68f)
                for (i in ks.indices) {
                    val k = ks[i]
                    val a = 0.35f + 0.65f * abs(sin(t * 3f + u.bobPhase + i * 1.3f))
                    fill.color = if (i % 2 == 1) rgba(255, 255, 255, a * ga)
                    else withAlpha(if (chasing) rgba(0, 191, 255, 1f) else if (following) rgba(224, 64, 251, 1f) else rgba(0, 230, 118, 1f), a * ga)
                    c.drawCircle(k * s, s * 0.16f + k * k * s * 0.14f, max(1f, s * 0.09f), fill)
                }
            }
        } else {
            // obrysova lod: jen tenka cara
            val col = withAlpha(if (panicking) blink else if (stalk) stalkMain else if (u.gray) colGray else cfg.main, ga)
            stroke.color = col; stroke.strokeWidth = 1f
            oval.set(-s * 0.58f, -s * 0.08f - s * 0.42f, s * 0.58f, -s * 0.08f + s * 0.42f)
            path.reset()
            path.addArc(oval, 180f, 180f)
            path.close()
            c.drawPath(path, stroke)
            oval.set(-s, s * 0.16f - s * 0.3f, s, s * 0.16f + s * 0.3f)
            c.drawOval(oval, stroke)
            fill.color = col
            c.drawCircle(0f, -s * 0.05f, max(0.7f, s * 0.07f), fill)
        }
        c.restore()
    }

    private fun drawBurst(c: Canvas, b: Burst, t: Float) {
        // barevny zablesk
        if (b.flash > 0.01f) {
            val r = 150f * (1.5f - b.flash) + 12f
            fill.shader = RadialGradient(
                b.x, b.y, r,
                intArrayOf(rgba(255, 255, 255, 0.42f * b.flash), withAlpha(b.color, 0.3f * b.flash), withAlpha(b.color, 0f)),
                floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(b.x, b.y, r, fill)
            fill.shader = null
        }
        // rozsirujici se prstence
        if (b.ringAlpha > 0.01f) {
            stroke.color = withAlpha(b.color, b.ringAlpha); stroke.strokeWidth = 1.4f
            c.drawCircle(b.x, b.y, b.ringR, stroke)
            stroke.color = rgba(255, 255, 255, b.ringAlpha * 0.55f); stroke.strokeWidth = 0.8f
            c.drawCircle(b.x, b.y, b.ringR * 0.62f, stroke)
        }
        // jiskry (duhove a blikajici hvezdicky)
        val hsv = FloatArray(3)
        for (p in b.particles) {
            if (p.life <= 0f) continue
            val a = clamp(p.life / p.maxLife, 0f, 1f)
            val twinkle = if (p.sparkle) 0.35f + 0.65f * abs(sin(t * 14f + p.hue)) else 1f
            val base = if (p.rainbow) {
                hsv[0] = (p.hue + t * 170f) % 360f; hsv[1] = 0.76f; hsv[2] = 1f
                Color.HSVToColor(hsv)
            } else p.color
            val col = withAlpha(base, a * twinkle)
            val r = p.size * (0.5f + 0.5f * a)
            fill.color = col
            c.drawCircle(p.x, p.y, r, fill)
            if (p.sparkle) {
                val len = r * 3.2f
                stroke.color = col; stroke.strokeWidth = 0.7f
                c.drawLine(p.x - len, p.y, p.x + len, p.y, stroke)
                c.drawLine(p.x, p.y - len, p.x, p.y + len, stroke)
            }
        }
    }

    // Portal: zeleno-modro-fialova trhlina s rotujicimi prstenci a jiskrami na okraji
    private fun drawPortal(c: Canvas, p: Portal, t: Float) {
        val r = 46f * p.progress
        if (r <= 0.5f) return
        c.save()
        c.translate(p.x, p.y)
        fill.shader = RadialGradient(
            0f, 0f, r * 2.1f,
            intArrayOf(rgba(0, 230, 118, 0.20f), rgba(0, 80, 255, 0.12f), rgba(156, 39, 176, 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(0f, 0f, r * 2.1f, fill)
        c.save()
        c.scale(1f, 0.62f)
        fill.shader = RadialGradient(
            0f, 0f, r,
            intArrayOf(rgba(255, 255, 255, 0.95f), rgba(0, 230, 118, 0.85f), rgba(0, 128, 255, 0.75f), rgba(156, 39, 176, 0.15f)),
            floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(0f, 0f, r, fill)
        fill.shader = null
        c.restore()
        val ringCols = intArrayOf(rgba(0, 255, 150, 0.9f), rgba(0, 191, 255, 0.8f), rgba(224, 64, 251, 0.8f))
        for (k in 0 until 3) {
            c.save()
            c.rotate(Math.toDegrees((p.spin * (if (k % 2 == 1) -1.4f else 1f) + t * 0.6f + k * 1.1f).toDouble()).toFloat())
            c.scale(1f, 0.62f)
            val rr = r * (0.92f - k * 0.2f)
            oval.set(-rr, -rr, rr, rr)
            stroke.color = ringCols[k]; stroke.strokeWidth = 2f - k * 0.5f
            c.drawArc(oval, 0f, 270f, false, stroke)
            c.restore()
        }
        for (i in 0 until 10) {
            val a = p.spin * 1.7f + i / 10f * 2f * PI.toFloat()
            val rr = r * 1.18f
            fill.color = if (i % 3 == 0) hex("#E040FB") else if (i % 3 == 1) hex("#00BFFF") else hex("#00E676")
            c.drawCircle(cos(a) * rr, sin(a) * rr * 0.62f, 1.6f, fill)
        }
        c.restore()
    }
}
