package com.example.actimelteambrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.actimelteambrowser.core.settings.AppSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * Maly ohnostroj po uspesnem gestu: jiskry v nahodnych barvach (barvy karet) vyletí z bodu, padaji a zhasnou (~0,8 s).
 * Nechyta dotyky a po dohrani se sam odstrani.
 */
@SuppressLint("ViewConstructor")
private class FireworkView(ctx: Context, private val cx: Float, private val cy: Float) : View(ctx) {
    private class Spark(val a: Double, val v: Float, val color: Int, val r: Float)

    private val d = resources.displayMetrics.density
    private val rnd = java.util.Random()
    private val sparks = List(26) {
        Spark(rnd.nextDouble() * Math.PI * 2, (60 + rnd.nextInt(60)) * d, TabColors.ALL[rnd.nextInt(TabColors.ALL.size)], (2f + rnd.nextFloat() * 2.2f) * d)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = SystemClock.uptimeMillis()
    private val dur = 800f
    private var removing = false

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val p = ((SystemClock.uptimeMillis() - t0) / dur).coerceIn(0f, 1f)
        val ease = 1f - (1f - p) * (1f - p)     // rychly vyletu, pak zpomali
        val fall = 40f * d * p * p              // jiskry trochu padaji
        val alpha = ((1f - p) * 255).toInt()
        for (s in sparks) {
            val x = cx + (cos(s.a) * s.v * ease).toFloat()
            val y = cy + (sin(s.a) * s.v * ease).toFloat() + fall
            paint.color = s.color
            paint.alpha = alpha
            canvas.drawCircle(x, y, s.r * (1f - 0.4f * p), paint)
        }
        // odebrat se primo v onDraw nejde (rodic prave kresli potomky = pad aplikace): az po snimku
        if (p < 1f) postInvalidateOnAnimation() else if (!removing) { removing = true; post { (parent as? ViewGroup)?.removeView(this) } }
    }
}

/** Ohnostroj v bode okna (px); vypnute animace = nic. */
internal fun MainActivity.showFirework(xWin: Float, yWin: Float) {
    if (!AppSettings.animationsOn.get()) return
    val parent = root as? ConstraintLayout ?: return
    val loc = IntArray(2)
    parent.getLocationInWindow(loc)
    val v = FireworkView(this, xWin - loc[0], yWin - loc[1])
    v.elevation = dp(60).toFloat()
    val lp = ConstraintLayout.LayoutParams(0, 0)
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    parent.addView(v, lp)
}
