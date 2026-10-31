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

/**
 * Zivá kulicka pro tlacitko 6 (gesta): svetle seda -> zelena v prechodu, ktera "dysha" a lehce
 * poletuje na miste; z okraje prskaji male vicebarevne jiskry. Jednou za 10 min se kulicka
 * rozepne na vsechny strany a vrati se zpet na stred.
 * Je vetsi nez tlacitko (aby jiskry vyletely ven), pod tlacitkem a nezachytava dotyky.
 */
class GestureOrbView(context: Context, var target: View) : View(context) {

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )

    private val d = resources.displayMetrics.density
    private val orbR = 28f * d
    private val rnd = java.util.Random()
    private val particles = ArrayList<Particle>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)     // mala zelena tecka uprostred
    private var lastDrawNs = 0L

    private var running = false
    private var startNs = 0L
    private var lastNs = 0L
    private var nextEmit = 0L
    private val expandEveryMs = 600_000L   // jak casto se kulicka rozepne (10 min)
    private val expandMs = 1400L           // jak dlouho rozepnuti trva
    private var nextExpand = 600_000L
    private var expandStart = -1L

    init {
        isClickable = false
        isFocusable = false
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2.5f * d
        fill.color = Color.parseColor("#D9D9D9")
        dot.color = Color.parseColor("#00B055")
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            // kresli se jen ~30x za sekundu (setri baterii)
            if (frameTimeNanos - lastDrawNs >= 33_000_000L) {
                lastDrawNs = frameTimeNanos
                step(frameTimeNanos)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun start() {
        if (running) return
        running = true
        startNs = 0L
        lastNs = 0L
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
        if (visibility == View.VISIBLE) start() else stop()
    }

    private fun step(now: Long) {
        if (startNs == 0L) {
            startNs = now
            lastNs = now
        }
        val dt = (now - lastNs) / 1_000_000_000f
        lastNs = now
        val ms = (now - startNs) / 1_000_000L

        // orb sleduje tlacitko (vysouvani, posun nad klavesnici)
        // (poloha se pocita z okna, takze funguje i pro tlacitka ve spodni liste a po posunu nad klavesnici)
        val tl = IntArray(2)
        val pl = IntArray(2)
        target.getLocationInWindow(tl)
        (parent as? View)?.getLocationInWindow(pl)
        translationX = tl[0] - pl[0] + target.width / 2f - width / 2f
        translationY = tl[1] - pl[1] + target.height / 2f - height / 2f
        visibility = if (target.isShown) View.VISIBLE else View.INVISIBLE

        // jiskry: kazdych ~140 ms jedna nova z okraje
        if (ms >= nextEmit) {
            nextEmit = ms + 140
            repeat(1) {
                val ang = rnd.nextFloat() * (2f * Math.PI.toFloat())
                val speed = (40f + rnd.nextFloat() * 80f) * d
                val hsv = floatArrayOf(rnd.nextFloat() * 360f, 0.9f, 1f)
                val life = 0.6f + rnd.nextFloat() * 0.5f
                particles.add(
                    Particle(
                        kotlin.math.cos(ang) * orbR, kotlin.math.sin(ang) * orbR,
                        kotlin.math.cos(ang) * speed, kotlin.math.sin(ang) * speed,
                        life, life, Color.HSVToColor(hsv), (1.5f + rnd.nextFloat() * 2f) * d
                    )
                )
            }
        }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                it.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt + 20f * d * dt * dt   // jemny pad
            p.vx *= 0.985f
            p.vy *= 0.985f
        }
        if (particles.size > 60) particles.subList(0, particles.size - 60).clear()

        // jednou za dlouho: rozepnuti na vsechny strany a navrat na stred
        if (ms >= nextExpand) {
            expandStart = ms
            nextExpand = ms + expandEveryMs
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val t = if (startNs == 0L) 0f else (lastNs - startNs) / 1_000_000_000f
        val ms = (lastNs - startNs) / 1_000_000L
        val twoPi = 2f * Math.PI.toFloat()

        // rozepnuti: 0 -> 1 -> 0 (hladce), jen behem expandMs
        var e = 0f
        if (expandStart >= 0L) {
            val ph = (ms - expandStart) / expandMs.toFloat()
            if (ph in 0f..1f) {
                val x = kotlin.math.sin(Math.PI.toFloat() * ph)
                e = x * x * (3f - 2f * x)
            }
        }

        // dychani kulicky a zelene tecky + rozepnuti
        val breath = kotlin.math.sin(t * twoPi / 1.8f)
        val r = orbR * (1f + 0.05f * breath) * (1f + 0.45f * e)
        val dotR = 7f * d * (1f + 0.15f * breath) * (1f + 0.4f * e)

        // lehke poletovani na miste (pri rozepnuti se kulicka vraci na stred)
        val ox = (kotlin.math.sin(t * twoPi / 7f) * 2.5f + kotlin.math.sin(t * twoPi / 3.1f) * 1f) * d * (1f - e)
        val oy = (kotlin.math.cos(t * twoPi / 5.3f) * 2.5f + kotlin.math.cos(t * twoPi / 2.7f) * 1f) * d * (1f - e)

        // jiskry (pod kulickou)
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            spark.color = p.color
            spark.alpha = (255 * a).toInt()
            canvas.drawCircle(cx + p.x, cy + p.y, p.size * (0.4f + 0.6f * a), spark)
        }

        canvas.drawCircle(cx + ox, cy + oy, r, fill)
        canvas.drawCircle(cx + ox, cy + oy, dotR, dot)
    }
}
