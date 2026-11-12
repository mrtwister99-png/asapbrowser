package com.example.actimelteambrowser

import android.view.View
import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import android.annotation.SuppressLint

// Kresli hvezdu z ikony Oblibene (vyplnovana od stredu) a jiskricky s malymi hvezdickami kolem ni
@SuppressLint("ViewConstructor")   // vytvari se jen z kodu
internal class StarBurstView(
    ctx: Context,
    private val cx: Float,
    private val cy: Float,
    private val size: Float
) : View(ctx) {
    var fill = 0f    // 0..1: kolik hvezdy je vyplnene zlute (od stredu)
    var burst = 0f   // 0..1: jiskricky a hvezdicky odletaji

    private val gold = "#FFD400".toColorInt()
    private val star: android.graphics.Path
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }

    init {
        isClickable = false
        // stejna hvezda jako v ikone ic_bookmarks (viewport 24x24), vycentrovana kolem (0,0)
        val p = androidx.core.graphics.PathParser.createPathFromPathData(
            "M12.92 5.15L14.42 8.66A0.7 0.7 0 0 0 15 9.08L18.8 9.42A1 1 0 0 1 19.37 11.17L16.5 13.68A0.7 0.7 0 0 0 16.28 14.37L17.12 18.09A1 1 0 0 1 15.64 19.17L12.36 17.21A0.7 0.7 0 0 0 11.64 17.21L8.36 19.17A1 1 0 0 1 6.88 18.09L7.72 14.37A0.7 0.7 0 0 0 7.5 13.68L4.63 11.17A1 1 0 0 1 5.2 9.42L9 9.08A0.7 0.7 0 0 0 9.58 8.66L11.08 5.15A1 1 0 0 1 12.92 5.15Z"
        )
        val m = Matrix()
        m.postTranslate(-12f, -12.2f)
        m.postScale(size / 24f, size / 24f)
        p.transform(m)
        star = p
        outline.strokeWidth = size * 0.8f / 24f
    }

    // pravidelna petihrota hvezdicka o polomeru r kolem (0,0)
    private fun smallStar(r: Float): android.graphics.Path {
        val p = android.graphics.Path()
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) r else r * 0.45f
            val a = Math.toRadians(-90.0 + k * 36.0)
            val x = (kotlin.math.cos(a) * rad).toFloat()
            val y = (kotlin.math.sin(a) * rad).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withTranslation(cx, cy) {
        // vyplneni: kruh rostouci od stredu, oriznuty tvarem hvezdy
        if (fill > 0f) {
            canvas.withClip(star) {
                drawCircle(0f, 0f, size * 0.42f * fill, fillPaint)
            }
        }
        canvas.drawPath(star, outline)
        if (burst > 0f) {
            val fade = (1f - burst).coerceIn(0f, 1f)
            // jiskricky: kratke carky odletaji do vsech stran
            linePaint.strokeWidth = size * 0.07f
            linePaint.alpha = (255 * fade).toInt()
            for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0 + 8.0)
                val d0 = size * (0.45f + 0.55f * burst)
                val d1 = d0 + size * 0.22f * fade
                canvas.drawLine(
                    (kotlin.math.cos(a) * d0).toFloat(), (kotlin.math.sin(a) * d0).toFloat(),
                    (kotlin.math.cos(a) * d1).toFloat(), (kotlin.math.sin(a) * d1).toFloat(), linePaint
                )
            }
            // male hvezdicky: leti dal a otaci se
            fillPaint.alpha = (255 * fade).toInt()
            for (k in 0 until 6) {
                val a = Math.toRadians(k * 60.0 + 30.0)
                val d = size * (0.5f + 1.1f * burst)
                canvas.withTranslation((kotlin.math.cos(a) * d).toFloat(), (kotlin.math.sin(a) * d).toFloat()) {
                    withRotation(burst * 120f) {
                        drawPath(smallStar(size * (0.17f - 0.06f * burst)), fillPaint)
                    }
                }
            }
            fillPaint.alpha = 255
        }
        }
    }
}
