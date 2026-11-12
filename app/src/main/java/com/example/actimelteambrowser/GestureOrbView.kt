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

    private val d = resources.displayMetrics.density
    private val orbR = 28f * d
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
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
        translationX = tl[0] - pl[0] + target.width / 2f - width / 2f
        translationY = tl[1] - pl[1] + target.height / 2f - height / 2f
        visibility = VISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, orbR, fill)
        canvas.drawCircle(cx, cy, 7f * d, dot)
    }
}
