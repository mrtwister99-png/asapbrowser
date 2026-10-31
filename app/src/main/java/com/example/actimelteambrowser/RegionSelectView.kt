package com.example.actimelteambrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.example.actimelteambrowser.core.adblock.AdBlocker
import com.example.actimelteambrowser.core.adblock.AdBlockRules
import java.io.ByteArrayInputStream
import com.example.actimelteambrowser.core.engine.RustBridge
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import com.example.actimelteambrowser.core.adblock.CookieReject
import com.example.actimelteambrowser.core.adblock.ListUpdater
import com.example.actimelteambrowser.core.settings.AppSettings
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.example.actimelteambrowser.data.BookmarksRepository
import com.example.actimelteambrowser.data.HistoryRepository
import com.example.actimelteambrowser.data.HistoryVisit
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.ui.SearchSuggestion
import com.example.actimelteambrowser.ui.ButtonConfigModal
import com.example.actimelteambrowser.ui.SearchSuggestionsList
import com.example.actimelteambrowser.ui.buildSearchSuggestions
import android.graphics.Bitmap
import android.view.MotionEvent
import com.example.actimelteambrowser.ui.ExternalTouch
import com.example.actimelteambrowser.core.settings.Gestures
import com.example.actimelteambrowser.core.gestures.gestureTick
import com.example.actimelteambrowser.core.gestures.playRawSound
import com.example.actimelteambrowser.core.settings.GlyphIcons
import com.example.actimelteambrowser.core.settings.SiteRules
import com.example.actimelteambrowser.core.settings.SearchEngines
import com.example.actimelteambrowser.ui.StartPage
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.actimelteambrowser.ui.StartSite
import com.example.actimelteambrowser.ui.GestureOverlay
import com.example.actimelteambrowser.ui.GestureArea
import com.example.actimelteambrowser.ui.GestureAreaEditor
import com.example.actimelteambrowser.ui.GesturePanel
import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.view.Choreographer
import androidx.constraintlayout.widget.ConstraintLayout
import android.graphics.Paint
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.util.TypedValue
import android.transition.TransitionManager
import android.net.Uri
import android.graphics.BitmapFactory
import android.text.InputType
import java.io.File
import java.io.FileOutputStream
import androidx.activity.OnBackPressedCallback
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.example.actimelteambrowser.ui.TabTile
import com.example.actimelteambrowser.ui.TabsScreen

// Ztmaveny snimek obrazovky: tazenim se nakresli obdelnik, ktery jde doladit tazenim rohu (nebo posunout celý);
// "Ulozit" ulozi vyrez, "Zrusit" (nebo zpet) skonci bez ulozeni
internal class RegionSelectView(
    ctx: android.content.Context,
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
    private val btnSave = android.graphics.RectF()
    private val btnCancel = android.graphics.RectF()
    private val green = Color.parseColor("#00B055")
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

    private fun drawButton(canvas: Canvas, rect: android.graphics.RectF, label: String, fill: Int, textColor: Int) {
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
            val rr = android.graphics.RectF(x1, y1, x2, y2)
            canvas.save()
            canvas.clipRect(rr)
            canvas.drawBitmap(shot, 0f, 0f, null)
            canvas.restore()
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
            drawButton(canvas, btnCancel, "Zrušit", Color.parseColor("#CC2A2A2A"), Color.WHITE)
            drawButton(canvas, btnSave, "Uložit", green, Color.parseColor("#06281D"))
        }
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
