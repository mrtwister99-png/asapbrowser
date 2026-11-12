package com.example.actimelteambrowser

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.toColorInt

/**
 * Spolecne kousky demo ukazek (prvni tutorial):
 *  - styl popisku (zluty obrys) a tlacitka Preskocit (cerveny obrys),
 *  - ztmaveni s "dirami" (videt je jen prave ukazovany prvek),
 *  - DemoMarks: polohy prvku v Compose obrazovkach (na obrazovce), aby na ne ukazka umela ukazat,
 *  - DemoStage: ukazka v okne Nastaveni (Oblibene, Nastaveni, Hodnoceni) - prst, popisek, Preskocit, zlute probliknuti.
 */

internal const val DEMO_YELLOW = "#FFD600"
internal const val DEMO_RED = "#FF3B30"

private fun Context.px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Pozadi popisku (tipu): tmava karta se zlutym obrysem. */
internal fun demoCaptionBg(ctx: Context): GradientDrawable = GradientDrawable().apply {
    setColor("#242424".toColorInt())
    cornerRadius = ctx.px(18).toFloat()
    setStroke(ctx.px(2), DEMO_YELLOW.toColorInt())
}

/** Pozadi tlacitka Preskocit: tmava karta s cervenym obrysem. */
internal fun demoSkipBg(ctx: Context): GradientDrawable = GradientDrawable().apply {
    setColor("#242424".toColorInt())
    cornerRadius = ctx.px(18).toFloat()
    setStroke(ctx.px(2), DEMO_RED.toColorInt())
}

/** Ztmaveni s "dirami" (jasna mista); zachytava dotyky, aby se pod ni nedalo klepat. */
internal class DemoDim(ctx: Context) : View(ctx) {
    val holes = ArrayList<RectF>()
    var circle = false   // diry jsou kulate (kulicka gest)
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    init { setLayerType(LAYER_TYPE_HARDWARE, null) }
    override fun onDraw(c: Canvas) {
        c.drawColor("#E6000000".toColorInt())
        val r = 22f * resources.displayMetrics.density
        for (h in holes) {
            val rr = if (circle) minOf(h.width(), h.height()) / 2f else r
            c.drawRoundRect(h, rr, rr, clear)
        }
    }
    override fun performClick(): Boolean {
        super.performClick()   // pristupnost
        return true
    }

    var passHoles = false   // dotek do diry projde na prvek pod ni (rychle nastaveni: klepni na kulicku)

    // pohlti vsechny doteky; performClick kvuli pristupnosti, bez zvuku
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (passHoles && event.actionMasked == MotionEvent.ACTION_DOWN && holes.any { it.contains(event.x, event.y) }) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            isSoundEffectsEnabled = false
            performClick()
        }
        return true
    }
}

/** Polohy prvku Compose obrazovek v souradnicich obrazovky (klic -> obdelnik). */
internal object DemoMarks {
    val rects = HashMap<String, RectF>()
    var demo = false                              // Nastaveni bezi jako ukazka (dole je misto navic, aby radky vyjely nad popisek)
    var scrollBy: ((Float) -> Unit)? = null       // posun hlavniho seznamu Nastaveni o px (ukazka)
}

/** Oznaci prvek, aby na nej ukazka umela ukazat (ulozi jeho polohu na obrazovce). */
internal fun Modifier.demoMark(key: String): Modifier = composed {
    val view = LocalView.current
    this.onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        val loc = IntArray(2)
        view.rootView.getLocationOnScreen(loc)
        DemoMarks.rects[key] = RectF(b.left + loc[0], b.top + loc[1], b.right + loc[0], b.bottom + loc[1])
    }
}

/** Kratky zluty obrys, ktery 3x problikne nad obdelnikem (souradnice v ramci [host]). */
internal fun blinkOutline(host: FrameLayout, r: RectF, times: Int = 3, stepMs: Long = 220) {
    val ctx = host.context
    val v = View(ctx)
    val g = GradientDrawable()
    g.setColor("#33FFD600".toColorInt())
    g.cornerRadius = ctx.px(16).toFloat()
    g.setStroke(ctx.px(3), DEMO_YELLOW.toColorInt())
    v.background = g
    val pad = ctx.px(3)
    val lp = FrameLayout.LayoutParams(r.width().toInt() + 2 * pad, r.height().toInt() + 2 * pad)
    host.addView(v, lp)
    v.translationX = r.left - pad
    v.translationY = r.top - pad
    var step = 0
    val run = object : Runnable {
        override fun run() {
            if (step >= times * 2 || !v.isAttachedToWindow) { host.removeView(v); return }
            v.visibility = if (step % 2 == 0) View.VISIBLE else View.INVISIBLE
            step++
            v.postDelayed(this, stepMs)
        }
    }
    run.run()
}

/**
 * Ukazka v jinem okne nez hlavnim (Nastaveni): vrstva pres celou obrazovku (ztmaveni s dirou, prst, popisek, Preskocit).
 * Pohlcuje dotyky; nic se neklika doopravdy, jen se ukazuje.
 */
internal class DemoStage(private val act: Activity, private val onSkip: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    var running = true
        private set
    val ov = FrameLayout(act)
    private val dim = DemoDim(act)
    private val caption = TextView(act)
    private val finger = View(act)
    private val fs = act.px(44)
    private var t = 0L

    init {
        ov.isClickable = true
        ov.addView(dim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dim.visibility = View.INVISIBLE

        caption.setTextColor(Color.WHITE)
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        caption.gravity = Gravity.CENTER
        caption.setPadding(act.px(18), act.px(14), act.px(18), act.px(14))
        caption.background = demoCaptionBg(act)
        caption.visibility = View.INVISIBLE
        val navBottom = navBarHeight()
        val clp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        clp.marginStart = act.px(28); clp.marginEnd = act.px(28)
        clp.bottomMargin = navBottom + act.px(84)
        ov.addView(caption, clp)

        val skip = TextView(act)
        skip.text = act.getString(R.string.tutorial_skip)
        skip.setTextColor(Color.WHITE)
        skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        skip.setTypeface(skip.typeface, android.graphics.Typeface.BOLD)
        skip.gravity = Gravity.CENTER
        skip.setPadding(act.px(28), act.px(12), act.px(28), act.px(12))
        skip.background = demoSkipBg(act)
        skip.setOnClickListener { end(); onSkip() }
        val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        slp.bottomMargin = navBottom + act.px(16)
        ov.addView(skip, slp)

        val fg = GradientDrawable()
        fg.shape = GradientDrawable.OVAL
        fg.setColor("#CCFFFFFF".toColorInt())
        fg.setStroke(act.px(2), DEMO_YELLOW.toColorInt())
        finger.background = fg
        finger.alpha = 0f
        ov.addView(finger, FrameLayout.LayoutParams(fs, fs))
        finger.translationX = act.resources.displayMetrics.widthPixels / 2f - fs / 2
        finger.translationY = act.resources.displayMetrics.heightPixels * 0.6f

        ov.elevation = act.px(200).toFloat()
        (act.window.decorView as ViewGroup).addView(ov, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun navBarHeight(): Int {
        val ins = androidx.core.view.ViewCompat.getRootWindowInsets(act.window.decorView)
        return ins?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    }

    /** Krok casoveho planu: [delay] ms po predchozim kroku. */
    fun at(delay: Long, block: () -> Unit) {
        t += delay
        handler.postDelayed({ if (running) block() }, t)
    }

    fun say(text: String) { caption.text = text; caption.visibility = View.VISIBLE }

    // obdelnik z obrazovky do souradnic vrstvy
    private fun local(r: RectF): RectF {
        val b = IntArray(2); ov.getLocationOnScreen(b)
        return RectF(r.left - b[0], r.top - b[1], r.right - b[0], r.bottom - b[1])
    }

    /** Ztmavi vse krome [r] (null = ztmaveni pryc). */
    fun focus(r: RectF?) {
        dim.holes.clear()
        if (r == null) { dim.visibility = View.INVISIBLE; return }
        dim.holes.add(local(r).apply { inset(-act.px(4).toFloat(), -act.px(4).toFloat()) })
        dim.visibility = View.VISIBLE
        dim.invalidate()
    }

    fun moveTo(r: RectF, ms: Long) {
        val l = local(r)
        finger.animate().alpha(1f).translationX(l.centerX() - fs / 2).translationY(l.centerY() - fs / 2).setDuration(ms).start()
    }

    fun tap() {
        finger.animate().scaleX(0.7f).scaleY(0.7f).setDuration(110).withEndAction { finger.animate().scaleX(1f).scaleY(1f).setDuration(110).start() }.start()
        val ring = View(act)
        val rg = GradientDrawable()
        rg.shape = GradientDrawable.OVAL
        rg.setColor(Color.TRANSPARENT)
        rg.setStroke(act.px(3), DEMO_YELLOW.toColorInt())
        ring.background = rg
        ov.addView(ring, FrameLayout.LayoutParams(fs, fs))
        ring.translationX = finger.translationX; ring.translationY = finger.translationY
        ring.animate().scaleX(2.2f).scaleY(2.2f).alpha(0f).setDuration(450).withEndAction { ov.removeView(ring) }.start()
    }

    /** Zlute probliknuti bez ztmaveni (vse zustava videt). */
    fun blinkOnly(r: RectF) {
        focus(null)
        blinkOutline(ov, local(r))
    }

    /** Zlute probliknuti obdelniku (a ztmaveni kolem nej). */
    fun blink(r: RectF) {
        focus(r)
        blinkOutline(ov, local(r))
    }

    fun end() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        (ov.parent as? ViewGroup)?.removeView(ov)
    }
}
