package com.example.actimelteambrowser

import androidx.core.graphics.withClip
import android.graphics.drawable.Drawable
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.ColorFilter
import androidx.core.graphics.toColorInt
import android.graphics.Paint
import android.graphics.Canvas

/** Zelena vrstva, ktera plni tlacitko zdola nahoru podle `fraction` (0..1) - ukazatel prubehu stahovani. */
class DownloadFillDrawable(private val radiusPx: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#9900B055".toColorInt() }
    private val clip = Path()
    var fraction: Float = 0f
        set(v) {
            if (v != field) {
                field = v
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || fraction <= 0f) return
        clip.reset()
        clip.addRoundRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), radiusPx, radiusPx, Path.Direction.CW)
        val top = b.bottom - b.height() * fraction
        canvas.withClip(clip) {
            drawRect(b.left.toFloat(), top, b.right.toFloat(), b.bottom.toFloat(), paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
