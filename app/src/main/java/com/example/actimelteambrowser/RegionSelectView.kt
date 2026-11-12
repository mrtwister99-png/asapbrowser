package com.example.actimelteambrowser

import android.annotation.SuppressLint
import android.view.View
import android.graphics.Bitmap
import android.view.MotionEvent
import android.content.Context
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.withClip
import androidx.core.graphics.toColorInt
import android.graphics.RectF

// Ztmaveny snimek obrazovky: tazenim se nakresli obdelnik, ktery jde doladit tazenim rohu (nebo posunout celý);
// "Ulozit" ulozi vyrez, "Zrusit" (nebo zpet) skonci bez ulozeni
@SuppressLint("ViewConstructor")   // vytvari se jen kodem (ma vlastni konstruktor)
internal class RegionSelectView(
    ctx: Context,
    private val shot: Bitmap,
    private val onResult: (Rect?) -> Unit
) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private var l = 0f
    private var t = 0f
    private var r = 0f
    private var b = 0f
    private var has = false      // obdelnik je nakreslen (da se doladit)
    private var mode = 0         // 0 nic, 1 kresleni, 2 posun, 3..6 rohy (LT, RT, LB, RB), 7 tlacitko Ulozit, 8 tlacitko Zrusit
    private var lastX = 0f
    private var lastY = 0f
    private val selRect = RectF()
    private val btnSave = RectF()
    private val btnCancel = RectF()
    private val green = "#00B055".toColorInt()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 3f * d }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 3f * d }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f * d; textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val btnBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f * d; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    init { isClickable = true }

    private fun norm() {
        if (l > r) { val x = l; l = r; r = x }
        if (t > b) { val y = t; t = b; b = y }
    }

    private fun rectInt(): Rect {
        norm()
        return Rect(
            l.toInt().coerceIn(0, shot.width), t.toInt().coerceIn(0, shot.height),
            r.toInt().coerceIn(0, shot.width), b.toInt().coerceIn(0, shot.height)
        )
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, fill: Int, textColor: Int) {
        btnBg.color = fill
        canvas.drawRoundRect(rect, 14f * d, 14f * d, btnBg)
        btnText.color = textColor
        canvas.drawText(label, rect.centerX(), rect.centerY() - (btnText.descent() + btnText.ascent()) / 2f, btnText)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(shot, 0f, 0f, null)
        canvas.drawColor(0x99000000.toInt())
        val show = has || mode == 1 || mode in 3..6 || mode == 2
        if (show) {
            val x1 = minOf(l, r); val x2 = maxOf(l, r); val y1 = minOf(t, b); val y2 = maxOf(t, b)
            val rr = selRect.apply { set(x1, y1, x2, y2) }   // predalokovano (bez novych objektu pri kresleni)
            canvas.withClip(rr) { canvas.drawBitmap(shot, 0f, 0f, null) }
            canvas.drawRect(rr, border)
            if (has) {
                // uchyty na rozich
                for ((cx, cy) in listOf(x1 to y1, x2 to y1, x1 to y2, x2 to y2)) {
                    canvas.drawCircle(cx, cy, 11f * d, handleFill)
                    canvas.drawCircle(cx, cy, 11f * d, handleStroke)
                }
            }
        } else {
            canvas.drawText("Tažením prstu vyber oblast  (zpět = zrušit)", width / 2f, height / 2f, text)
        }
        if (has) {
            val bw = 130f * d
            val bh = 48f * d
            val cy = height - 90f * d
            btnCancel.set(width / 2f - bw - 8f * d, cy - bh / 2f, width / 2f - 8f * d, cy + bh / 2f)
            btnSave.set(width / 2f + 8f * d, cy - bh / 2f, width / 2f + bw + 8f * d, cy + bh / 2f)
            drawButton(canvas, btnCancel, "Zrušit", "#CC2A2A2A".toColorInt(), Color.WHITE)
            drawButton(canvas, btnSave, "Uložit", green, "#06281D".toColorInt())
        }
    }

    override fun performClick(): Boolean {
        super.performClick()   // pristupnost
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x.coerceIn(0f, shot.width.toFloat())
        val y = e.y.coerceIn(0f, shot.height.toFloat())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y
                if (has) {
                    norm()
                    val hit = 32f * d
                    val corners = listOf(l to t, r to t, l to b, r to b)
                    var best = -1
                    var bestD = hit
                    for ((i, c) in corners.withIndex()) {
                        val dist = kotlin.math.hypot((x - c.first).toDouble(), (y - c.second).toDouble()).toFloat()
                        if (dist < bestD) { bestD = dist; best = i }
                    }
                    mode = when {
                        btnSave.contains(x, y) -> 7
                        btnCancel.contains(x, y) -> 8
                        best >= 0 -> 3 + best
                        x in l..r && y in t..b -> 2
                        else -> { has = false; l = x; t = y; r = x; b = y; 1 }
                    }
                } else {
                    l = x; t = y; r = x; b = y
                    mode = 1
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    1 -> { r = x; b = y }
                    2 -> {
                        var dx = x - lastX
                        var dy = y - lastY
                        // obdelnik nesmi vyjet z obrazovky
                        dx = dx.coerceIn(-l, shot.width - r)
                        dy = dy.coerceIn(-t, shot.height - b)
                        l += dx; r += dx; t += dy; b += dy
                        lastX = x; lastY = y
                    }
                    3 -> { l = x; t = y }
                    4 -> { r = x; t = y }
                    5 -> { l = x; b = y }
                    6 -> { r = x; b = y }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                isSoundEffectsEnabled = false
                performClick()   // pristupnost
                when (mode) {
                    7 -> if (btnSave.contains(x, y)) { onResult(rectInt()); return true }
                    8 -> if (btnCancel.contains(x, y)) { onResult(null); return true }
                    1, 2, 3, 4, 5, 6 -> {
                        norm()
                        val min = 48f * d
                        has = (r - l) >= min && (b - t) >= min
                    }
                }
                mode = 0
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { mode = 0; invalidate() }
        }
        return true
    }
}
