package com.example.actimelteambrowser

import androidx.core.graphics.toColorInt
import android.view.View
import android.content.Context
import android.view.Choreographer
import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Canvas

/**
 * Kulicka tlacitka Gest: vzdy stejna - bile kolecko se zelenou teckou uprostred (zadna animace, zadne jiskry).
 * Jen sleduje cilove tlacitko (vysouvani, posun nad klavesnici); pod tlacitkem a nezachytava dotyky.
 */
@SuppressLint("ViewConstructor")   // vytvari se jen z kodu
class GestureOrbView(context: Context, var target: View) : View(context) {

    /** true = kulicka je videt, i kdyz je cilove tlacitko schovane (editor gest schova vsechna tlacitka) */
    var alwaysShow = false

    /** Vzhled, ktery se prave zkousi v nastaveni gest (zamceny vzor); null = ulozeny vzhled. */
    var styleOverride: Int? = null

    private val d = resources.displayMetrics.density
    private val orbR = 28f * d
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val path = android.graphics.Path()
    private var running = false

    init {
        isClickable = false
        isFocusable = false
        fill.color = "#FFFFFF".toColorInt()
        dot.color = "#00B055".toColorInt()
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            follow()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    // Poloha se pocita z okna (funguje i pro tlacitka ve spodni liste); skryte / nenamerene tlacitko = kulicka se neukazuje,
    // takze nemuze "uletet" na spatne misto (drive se tak objevovala mezi tlacitky).
    private fun follow() {
        val ok = target.isShown && target.width > 0 && target.height > 0 && width > 0
        if (!(ok || (alwaysShow && target.width > 0 && width > 0))) {
            visibility = INVISIBLE
            return
        }
        val tl = IntArray(2)
        val pl = IntArray(2)
        target.getLocationInWindow(tl)
        (parent as? View)?.getLocationInWindow(pl)
        // kulicka je vzdy cela videt (i u tlacitka zasunuteho za okraj obrazovky)
        val pw = (parent as? View)?.width ?: 0
        val ph = (parent as? View)?.height ?: 0
        var tx = tl[0] - pl[0] + target.width / 2f - width / 2f
        var ty = tl[1] - pl[1] + target.height / 2f - height / 2f
        if (pw > width) tx = tx.coerceIn(0f, (pw - width).toFloat())
        if (ph > height) ty = ty.coerceIn(0f, (ph - height).toFloat())
        translationX = tx
        translationY = ty
        visibility = VISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, orbR, fill)
        // znak podle vzhledu plochy: cisla = vyrazne zelene G, kolecka = tecka, ctverecky = ctverecek, hvezdicky = hvezdicka
        val st = styleOverride ?: com.example.actimelteambrowser.ui.GestureLook.style()
        // dokud uzivatel vzhled nezmenil: zelene UFO (raritni - po zmene vzhledu uz se nevrati)
        val ufo = styleOverride == null && !com.example.actimelteambrowser.core.settings.AppSettings.gestureStyleChanged.get()
        when (if (ufo) -1 else st) {
            0 -> {
                letter.color = dot.color
                letter.textSize = 30f * d
                val fm = letter.fontMetrics
                canvas.drawText("G", cx, cy - (fm.ascent + fm.descent) / 2f, letter)
            }
            -1 -> {
                // zelene UFO
                val r = 7.5f * d
                canvas.drawOval(cx - r * 1.75f, cy - r * 0.15f, cx + r * 1.75f, cy + r * 0.85f, dot)
                canvas.drawArc(cx - r * 0.9f, cy - r * 1.05f, cx + r * 0.9f, cy + r * 0.75f, 180f, 180f, true, dot)
                // svetylka na talíri
                for (k in -1..1) canvas.drawCircle(cx + k * r * 0.9f, cy + r * 0.35f, 1.3f * d, fill)
            }
            2 -> canvas.drawRect(cx - 8f * d, cy - 8f * d, cx + 8f * d, cy + 8f * d, dot)
            3 -> {
                path.reset()
                for (k in 0 until 10) {
                    val r = (if (k % 2 == 0) 13f else 5.6f) * d
                    val a = Math.toRadians(-90.0 + k * 36.0)
                    val x = cx + (r * Math.cos(a)).toFloat()
                    val y = cy + 1f * d + (r * Math.sin(a)).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, dot)
            }
            else -> canvas.drawCircle(cx, cy, 7f * d, dot)
        }
    }
}
