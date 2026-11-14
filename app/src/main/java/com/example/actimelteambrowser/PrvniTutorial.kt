@file:SuppressLint("SetTextI18n")   // texty ukazky jsou zatim napsane primo v kodu

package com.example.actimelteambrowser

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
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import androidx.core.graphics.toColorInt
import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import androidx.core.view.isVisible
import com.example.actimelteambrowser.ui.ExternalTouch

/**
 * PRVNI TUTORIAL: kdyz uzivatel poprve klepne na zlute oznaceny prvek (AdBlock, VPN, Sdilet, zamek),
 * misto bezne akce se ukaze kratka ukazka. Ukazka pouziva SKUTECNE UI (stejne tlacitko AdBlock, stejne modaly jako u uzivatele),
 * jen se nic neulozi, nesmaze ani nezkopiruje - zmeny jsou pouze vizualni.
 *  - Obrazovka ztmavne (dim v root, jasne zustane horni pole + AdBlock + VPN), skutecne modaly se otevrou nad dimem.
 *  - Prst, popisek a Preskocit jsou ve vlastnim okne (Dialog) = uplne nejvyssi vrstva (nad modaly); toasty jsou jeste nad nimi.
 *  - Vrstva s prstem pohlcuje dotyky, takze se pod ukazkou neda klepat.
 */

private val demoHandler = Handler(Looper.getMainLooper())
@SuppressLint("StaticFieldLeak")   // po ukazce se vzdy vynuluje (endDemo)
private var demoOverlay: View? = null      // dim v root (nebo okno VPN)
private var demoRunning = false
@SuppressLint("StaticFieldLeak")   // po ukazce se vzdy vynuluje (endDemo)
private var layerDialog: android.app.Dialog? = null
@SuppressLint("StaticFieldLeak")   // po ukazce se vzdy vynuluje (endDemo)
private var layerContent: FrameLayout? = null
private var demoDialogOpen = false          // ukazka otevrela skutecny modal
private var demoAdOrig: Boolean? = null     // puvodni stav AdBlocku (obnovi se po ukazce)
private var demoGestureOpen = false         // ukazka otevrela plochu gest
private var demoTabsOpen = false            // ukazka otevrela skutecny prehled karet
private var demoFindOpen = false            // ukazka otevrela panel Najit na strance
private var demoResume: (() -> Unit)? = null   // pokracovani ukazky po navratu z okna Nastaveni
private var demoNewTabId = -1L              // nova karta otevrena ukazkou (po ukazce se zavre)
private var demoPrevTabId = -1L             // karta, na kterou se po ukazce vrati

/** Ukazkovy vyber casti obrazovky (jen kresleni, nic se neuklada): ramecek roste z rohu a ukaze tlacitka Ulozit / Zrusit. */
private class DemoRegion(ctx: Context) : View(ctx) {
    var x0 = 0f; var y0 = 0f; var x1 = 0f; var y1 = 0f
    var p = 0f
    private val d = resources.displayMetrics.density
    private val green = "#00B055".toColorInt()
    private val fill = Paint().apply { color = "#22FFFFFF".toColorInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 3f * d }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f * d; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    override fun onDraw(c: Canvas) {
        val r = RectF(x0, y0, x0 + (x1 - x0) * p, y0 + (y1 - y0) * p)
        c.drawRect(r, fill)
        c.drawRect(r, border)
        for ((cx, cy) in listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)) {
            c.drawCircle(cx, cy, 8f * d, handle)
            c.drawCircle(cx, cy, 8f * d, border)
        }
        if (p >= 1f) {
            val w = 100f * d; val h = 40f * d; val top = r.bottom + 16f * d
            val save =  RectF(r.centerX() + 6f * d, top, r.centerX() + 6f * d + w, top + h)
            val cancel = RectF(r.centerX() - 6f * d - w, top, r.centerX() - 6f * d, top + h)
            pill.color = green; c.drawRoundRect(save, h / 2, h / 2, pill)
            pill.color = "#444444".toColorInt(); c.drawRoundRect(cancel, h / 2, h / 2, pill)
            label.color = "#06281D".toColorInt(); c.drawText("Uložit", save.centerX(), save.centerY() + 5f * d, label)
            label.color = Color.WHITE; c.drawText("Zrušit", cancel.centerX(), cancel.centerY() + 5f * d, label)
        }
    }
}

/** Kolecko postupu ukazky: prazdny bily krouzek, ktery se plni zelene (p = 0..1). */
private class DemoDot(ctx: Context) : View(ctx) {
    var p = 0f
    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.6f * d }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#00B055".toColorInt() }
    override fun onDraw(c: Canvas) {
        val r = RectF(1.6f * d, 1.6f * d, width - 1.6f * d, height - 1.6f * d)
        if (p > 0f) c.drawArc(r, -90f, 360f * p.coerceAtMost(1f), true, green)
        c.drawOval(r, ring)
    }
}

internal fun MainActivity.rectOf(v: View): RectF {
    val a = IntArray(2); val r = IntArray(2)
    v.getLocationInWindow(a); root.getLocationInWindow(r)
    val x = (a[0] - r[0]).toFloat(); val y = (a[1] - r[1]).toFloat()
    return RectF(x, y, x + v.width, y + v.height)
}

// Okno nejvyssi vrstvy: znovu se zobrazi (a tim vyleze nad prave otevreny modal)
private fun MainActivity.raiseLayer() {
    val c = layerContent ?: return
    runCatching { layerDialog?.dismiss() }
    (c.parent as? ViewGroup)?.removeView(c)
    val d = android.app.Dialog(this)
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
    d.setContentView(c, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    d.setCancelable(false)
    d.window?.let { w ->
        w.setBackgroundDrawableResource(android.R.color.transparent)
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val wp = w.attributes
        wp.windowAnimations = 0
        w.attributes = wp
    }
    runCatching { d.show() }
    layerDialog = d
}

/** Bezi ukazka (skutecna tlacitka se behem ni neprepinaji). */
internal fun isDemoRunning(): Boolean = demoRunning

private fun MainActivity.endDemo() {
    demoHandler.removeCallbacksAndMessages(null)
    demoAdOrig?.let { isAdBlockOn = it; applyAdBlockUi() }   // vrati puvodni vzhled AdBlocku
    demoAdOrig = null
    if (demoGestureOpen) { hideGestures(); demoGestureOpen = false }   // zavre plochu gest, nic neprovede
    if (demoTabsOpen) { hideTabs(); demoTabsOpen = false }             // zavre prehled karet otevreny ukazkou
    if (demoFindOpen) { hideFindBar(); demoFindOpen = false }          // zavre ukazkovy panel hledani
    tabsView.scaleX = 1f; tabsView.scaleY = 1f; tabsView.alpha = 1f
    if (demoNewTabId >= 0) {
        // nova karta z ukazky zmizi a vrati se puvodni karta
        val prev = tabs.list.firstOrNull { it.id == demoPrevTabId }
        if (tabs.list.size > 1) closeTabNow(demoNewTabId)
        prev?.let { switchTo(it) }
        updateTabStrips()
        demoNewTabId = -1L; demoPrevTabId = -1L
    }
    demoResume = null
    updateDeskBtn()   // tlacitko Verze pro pocitac zpet podle skutecneho stavu
    if (demoDialogOpen) { runCatching { lastDialogDismiss?.invoke() }; demoDialogOpen = false }
    runCatching { layerDialog?.dismiss() }
    layerDialog = null; layerContent = null
    demoOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
    demoOverlay = null
    demoTargets.clear()
    if (isWebViewReady()) updateLockIcon(webView.url)   // vrati tecku u zamku podle skutecneho stavu
    demoRunning = false
    onQuickSetupDemoEnded()
}

private fun MainActivity.card(): GradientDrawable {
    val g = GradientDrawable()
    g.setColor("#242424".toColorInt())
    g.cornerRadius = dp(18).toFloat()
    g.setStroke(dp(1), "#444444".toColorInt())
    return g
}

/** Vrati true, kdyz se ukazka spustila (puvodni akce se tentokrat neprovede). */
internal fun MainActivity.startFirstDemo(n: Int): Boolean {
    if (demoRunning) return false
    // 11-14 = ukazka tlacitek 1-4 (klepnuti + drzeni), 300 = tlacitko s funkci Gesta
    val id = if (n in 1..4 && buttonFunction(n, false) != ButtonFunction.GESTURES) 10 + n else if (n in 1..12 && n != 7 && n != 8) 300 else n
    if ((n == 7 || n == 8) && buttonFunction(n, false) != ButtonDefaults.tap(n)) return false   // tlacitko ma jinou funkci
    if (n == 8) { showVpnSoon(); return true }

    demoRunning = true
    if (id == 300 && quickSetupStage == 2) { quickSetupStage = 21; quickSpotOff?.invoke() }   // rychle nastaveni: ukazka gest, po ni dotaz "nechat tlacitko?"
    val parent = root as ConstraintLayout
    val urlBox = findViewById<View>(R.id.urlContainer)
    val adBtn = findViewById<View>(R.id.btnAdBlock)

    // ztmaveni v root (skutecne modaly jsou okna nad nim)
    val dim = DemoDim(this)
    val dlp = ConstraintLayout.LayoutParams(0, 0)
    dlp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    dlp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    dlp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    dlp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    dim.elevation = dp(100).toFloat()   // ztmavi uplne vse krome prave ukazovaneho prvku (i prvky s elevaci)
    parent.addView(dim, dlp)
    demoOverlay = dim
    dim.post {
        dim.holes.clear()
        // jasne je jen tlacitko, na ktere ukazka klepne (zbytek obrazovky je ztmaveny)
        val pad = -dp(4).toFloat()
        when (id) {
            201, 202, 203 -> (sideBarLeft as? ViewGroup)?.getChildAt(id - 201)?.let { dim.holes.add(rectOf(it).apply { inset(pad, pad) }) }
            in 11..14 -> buttonView(n)?.let { dim.holes.add(rectOf(it).apply { inset(pad, pad) }) }
            300 -> (orbView?.takeIf { it.isVisible } ?: buttonView(n))?.let { dim.holes.add(rectOf(it)); dim.circle = true }   // jen kulicka
            103 -> dim.holes.add(rectOf(findViewById(R.id.ivUrlShare)).apply { inset(pad, pad) })
            104 -> dim.holes.add(rectOf(findViewById(R.id.ivUrlLock)).apply { inset(pad, pad) })
            105 -> for (bid in listOf(R.id.btnNavBack, R.id.btnNavForward)) dim.holes.add(rectOf(findViewById(bid)).apply { inset(pad, pad) })
            else -> dim.holes.add(rectOf(adBtn).apply { inset(pad, pad) })
        }
        dim.invalidate()
    }

    // nejvyssi vrstva: prst, popisek, Preskocit
    val ov = FrameLayout(this)
    ov.isClickable = true   // pohlti dotyky
    layerContent = ov

    val caption = TextView(this)
    caption.setTextColor(Color.WHITE)
    caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    caption.gravity = Gravity.CENTER
    caption.setPadding(dp(18), dp(14), dp(18), dp(14))
    caption.background = demoCaptionBg(this)   // kazdy tip ma zluty obrys
    caption.visibility = View.INVISIBLE
    // popisek dole; u gest nahore (dole je plocha gest)
    val clp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, (if (id == 300 || id == 203 || id in 11..14) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL)
    clp.marginStart = dp(28); clp.marginEnd = dp(28)
    // Preskocit je u vsech ukazek na stejnem miste: dole, tesne nad spodni listou (neprekryva tlacitka)
    val skipBottom = (if (bottomBar.height > 0) bottomBar.height else lastSystemBottom + dp(80)) + dp(12)
    if (id == 300 || id == 203 || id in 11..14) clp.topMargin = dp(60) else clp.bottomMargin = skipBottom + dp(72)
    ov.addView(caption, clp)
    fun say(t: String) { caption.text = t; caption.visibility = View.VISIBLE }

    val skip = TextView(this)
    skip.text = getString(R.string.tutorial_skip)
    skip.setTextColor(Color.WHITE)
    skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    skip.setTypeface(skip.typeface, android.graphics.Typeface.BOLD)
    skip.gravity = Gravity.CENTER
    skip.setPadding(dp(28), dp(12), dp(28), dp(12))
    skip.background = demoSkipBg(this)   // Preskocit ma cerveny obrys
    skip.setOnClickListener { endDemo() }   // preskoci jen tuto ukazku
    val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    slp.bottomMargin = skipBottom
    ov.addView(skip, slp)

    // prst + kruh pri klepnuti
    val fs = dp(44)
    val finger = View(this)
    val fg = GradientDrawable()
    fg.shape = GradientDrawable.OVAL
    fg.setColor("#CCFFFFFF".toColorInt())
    fg.setStroke(dp(2), "#FFD600".toColorInt())
    finger.background = fg
    finger.alpha = 0f
    ov.addView(finger, FrameLayout.LayoutParams(fs, fs))
    finger.translationX = resources.displayMetrics.widthPixels / 2f - fs / 2
    finger.translationY = resources.displayMetrics.heightPixels * 0.6f
    raiseLayer()

    fun moveTo(v: View, ms: Long) {
        if (!v.isAttachedToWindow) { endDemo(); return }   // modal se mezitim zavrel (napr. tlacitkem zpet)
        val a = IntArray(2); val b = IntArray(2)
        v.getLocationOnScreen(a); ov.getLocationOnScreen(b)
        finger.animate().alpha(1f)
            .translationX(a[0] - b[0] + v.width / 2f - fs / 2)
            .translationY(a[1] - b[1] + v.height / 2f - fs / 2)
            .setDuration(ms).start()
    }
    // prst na bod v souradnicich obrazovky
    fun moveXY(sx: Float, sy: Float, ms: Long) {
        val b = IntArray(2); ov.getLocationOnScreen(b)
        finger.animate().alpha(1f).translationX(sx - b[0] - fs / 2).translationY(sy - b[1] - fs / 2).setDuration(ms).start()
    }
    fun tap() {
        finger.animate().scaleX(0.7f).scaleY(0.7f).setDuration(110).withEndAction { finger.animate().scaleX(1f).scaleY(1f).setDuration(110).start() }.start()
        val ring = View(this)
        val rg = GradientDrawable()
        rg.shape = GradientDrawable.OVAL
        rg.setColor(Color.TRANSPARENT)
        rg.setStroke(dp(3), "#FFD600".toColorInt())
        ring.background = rg
        ov.addView(ring, FrameLayout.LayoutParams(fs, fs))
        ring.translationX = finger.translationX; ring.translationY = finger.translationY
        ring.animate().scaleX(2.2f).scaleY(2.2f).alpha(0f).setDuration(450).withEndAction { ov.removeView(ring) }.start()
    }
    // klepne na skutecne tlacitko v modalu (jeho "demo" listener udela jen vizualni zmenu + toast)
    fun press(key: String) {
        val v = demoTargets[key] ?: return
        tap(); v.performClick()
    }
    // otevre skutecny modal a zvedne vrstvu s prstem nad nej
    fun openModal(open: () -> Unit) {
        open(); demoDialogOpen = true
        demoHandler.postDelayed({ if (demoRunning) raiseLayer() }, 120)
    }

    // ztmaveni: jasne zustanou jen tyto prvky
    fun focusViews(vararg vs: View?) {
        dim.circle = false
        dim.holes.clear()
        for (v in vs) if (v != null && v.isAttachedToWindow) dim.holes.add(rectOf(v).apply { inset(-dp(4).toFloat(), -dp(4).toFloat()) })
        dim.visibility = View.VISIBLE
        dim.invalidate()
    }
    // zlute probliknuti (ve vrstve nad vsim, i nad modaly)
    fun blinkScreen(r: RectF) {
        val b = IntArray(2); ov.getLocationOnScreen(b)
        blinkOutline(ov, RectF(r.left - b[0], r.top - b[1], r.right - b[0], r.bottom - b[1]))
    }
    fun screenRect(v: View): RectF {
        val a = IntArray(2); v.getLocationOnScreen(a)
        return RectF(a[0].toFloat(), a[1].toFloat(), (a[0] + v.width).toFloat(), (a[1] + v.height).toFloat())
    }
    fun blinkView(v: View) { if (v.isAttachedToWindow) blinkScreen(screenRect(v)) }
    fun moveToRect(r: RectF, ms: Long) = moveXY(r.centerX(), r.centerY(), ms)
    fun holdFinger() { finger.animate().scaleX(1.35f).scaleY(1.35f).setDuration(500).start() }   // prst zustane tlacit
    fun releaseFinger() { finger.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }

    // casovy plan: kazdy krok ma svoje zpozdeni od zacatku
    var t = 350L
    fun at(delay: Long, block: () -> Unit) { t += delay; demoHandler.postDelayed({ if (demoRunning) block() }, t) }

    when (id) {
        7 -> {
            // Ukazka AdBlocku ve dvou castech (dve kolecka pod Preskocit se plni zelene):
            //  1) klepnuti = vypnout / zapnout,  2) drzeni = pridat web k blokovani.
            // Kazda cast: tip 3x problikne, tlacitko AdBlock 3x problikne, zelene "OK, pokračuj" 3x problikne a ceka na uzivatele.
            val orig = isAdBlockOn
            demoAdOrig = orig
            // ukazka zacina vzdy ze zapnuteho stavu: vypne a zase zapne (skutecny stav se po ukazce vrati)
            if (!isAdBlockOn) { isAdBlockOn = true; applyAdBlockUi() }
            fun flip() { isAdBlockOn = !isAdBlockOn; applyAdBlockUi(); AppToast.show(this, if (isAdBlockOn) "AdBlock zapnut" else "AdBlock vypnut") }
            fun after(ms: Long, block: () -> Unit) { demoHandler.postDelayed({ if (demoRunning) block() }, ms) }

            // tip a hned pod nim: vlevo cervene Preskocit (klikaci celou dobu), vpravo zelene "OK, pokračuj"; pod tim dve kolecka
            ov.removeView(skip)
            ov.removeView(caption)
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.addView(caption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val slp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(skip, slp2)
            val ok = TextView(this)
            ok.text = "OK, pokračuj"
            ok.setTextColor("#06281D".toColorInt())
            ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            ok.setTypeface(ok.typeface, android.graphics.Typeface.BOLD)
            ok.gravity = Gravity.CENTER
            ok.setPadding(dp(12), dp(12), dp(12), dp(12))
            val okBg = GradientDrawable()
            okBg.setColor("#00B055".toColorInt())
            okBg.cornerRadius = dp(22).toFloat()
            ok.background = okBg
            ok.visibility = View.INVISIBLE   // misto vpravo je drzene, Preskocit zustava vlevo
            val olp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            olp.marginStart = dp(12)
            row.addView(ok, olp)
            val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rowLp.topMargin = dp(12)
            box.addView(row, rowLp)
            val blp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            blp.marginStart = dp(24); blp.marginEnd = dp(24)
            blp.bottomMargin = skipBottom + dp(18)
            ov.addView(box, blp)
            val dots = LinearLayout(this)
            dots.orientation = LinearLayout.HORIZONTAL
            val dot1 = DemoDot(this)
            val dot2 = DemoDot(this)
            for ((k, d) in listOf(dot1, dot2).withIndex()) {
                val dl = LinearLayout.LayoutParams(dp(14), dp(14))
                if (k == 1) dl.marginStart = dp(10)
                dots.addView(d, dl)
            }
            val dlp2 = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            dlp2.bottomMargin = skipBottom - dp(4)
            ov.addView(dots, dlp2)

            var gen = 0             // kazda faze ma sve cislo: staré problikavani se samo zastavi
            var okAction: (() -> Unit)? = null
            fun setOk(text: String, enabled: Boolean, action: (() -> Unit)?) {
                ok.text = text
                ok.visibility = View.VISIBLE
                ok.alpha = if (enabled) 1f else 0.4f
                okAction = if (enabled) action else null
            }
            ok.setOnClickListener { okAction?.let { a -> okAction = null; gen++; ok.animate().cancel(); ok.alpha = 0.4f; caption.animate().cancel(); caption.alpha = 1f; a() } }

            // 3x probliknout: 0,25 s zmizi, 0,25 s se objevi, 1 s drzi
            fun pulse(v: View, g: Int, n: Int, done: () -> Unit) {
                if (!demoRunning || g != gen) return
                if (n == 0) { done(); return }
                v.animate().alpha(0f).setDuration(250).withEndAction {
                    v.animate().alpha(1f).setDuration(250).withEndAction { after(1000) { pulse(v, g, n - 1, done) } }.start()
                }.start()
            }
            // zluty ramecek pres tlacitko AdBlock (problikne stejne jako tip)
            fun pulseButton(g: Int, done: () -> Unit) {
                val r = screenRect(adBtn)
                val b = IntArray(2); ov.getLocationOnScreen(b)
                val frame = View(this)
                val fgd = GradientDrawable()
                fgd.setColor("#33FFD600".toColorInt())
                fgd.cornerRadius = dp(18).toFloat()
                fgd.setStroke(dp(3), "#FFD600".toColorInt())
                frame.background = fgd
                val pad = dp(4)
                ov.addView(frame, FrameLayout.LayoutParams(r.width().toInt() + 2 * pad, r.height().toInt() + 2 * pad))
                frame.translationX = r.left - b[0] - pad
                frame.translationY = r.top - b[1] - pad
                pulse(frame, g, 3) { ov.removeView(frame); done() }
                // kdyz se faze prerusi, ramecek zmizi
                after(6000) { if (g != gen) ov.removeView(frame) }
            }
            // uvod faze: tip -> tlacitko -> OK (kazde 3x), pak ceka na OK
            fun intro(tip: String, okText: String, action: () -> Unit) {
                val g = ++gen
                caption.animate().cancel(); caption.alpha = 1f
                say(tip)
                after(700) {
                    pulse(caption, g, 3) {
                        pulseButton(g) {
                            setOk(okText, true, action)
                            pulse(ok, g, 3) { }
                        }
                    }
                }
            }
            fun fill(d: DemoDot, ms: Long) {
                val a = android.animation.ValueAnimator.ofFloat(0f, 1f)
                a.duration = ms
                a.addUpdateListener { d.p = it.animatedValue as Float; d.invalidate() }
                a.start()
            }
            // prst se objevi uprostred obrazovky
            fun fingerFromCenter() {
                finger.animate().cancel()
                finger.scaleX = 1f; finger.scaleY = 1f
                finger.translationX = ov.width / 2f - fs / 2
                finger.translationY = ov.height / 2f - fs / 2
                finger.alpha = 0f
                finger.animate().alpha(1f).setDuration(200).start()
            }
            fun fingerHide() { finger.animate().alpha(0f).setDuration(200).start() }

            // cast 2: drzeni = okno pridani webu -> aktualni stranka -> OK -> vysvetleni
            fun partTwo() {
                setOk("OK, pokračuj", false, null)
                fill(dot2, 7600)
                fingerFromCenter()
                after(250) { moveTo(adBtn, 650) }
                after(1000) { holdFinger() }
                after(1900) { releaseFinger(); fingerHide() }
                after(2200) { openModal { showAdBlockSitesDialog(demo = true) } }
                after(3100) { fingerFromCenter() }
                after(3350) { demoTargets["site_current"]?.let { moveTo(it, 600) } }
                after(4150) { press("site_current") }
                after(5000) { demoTargets["site_ok"]?.let { moveTo(it, 550) } }
                after(5800) { press("site_ok") }
                after(6000) { raiseLayer() }   // vrstva s tlacitky nad okno s vysvetlenim
                after(6300) { fingerHide() }
                after(7700) {
                    say("Hotovo – web se přidá k pravidlům (v ukázce se nic neuloží)")
                    setOk("Hotovo", true) { endDemo() }
                    val g = ++gen
                    pulse(ok, g, 3) { }
                }
            }
            // cast 1: klepnuti = vypnout (toast), pauza, znovu klepnuti = zapnout (toast)
            fun partOne() {
                setOk("OK, pokračuj", false, null)
                fill(dot1, 5200)
                fingerFromCenter()
                after(250) { moveTo(adBtn, 650) }
                after(1050) { tap() }
                after(1200) { flip() }
                after(1500) { fingerHide() }
                after(3500) { fingerFromCenter() }
                after(3750) { moveTo(adBtn, 650) }
                after(4550) { tap() }
                after(4700) { flip() }
                after(5000) { fingerHide() }
                after(5400) {
                    intro("Podržením AdBlocku přidáš web, na kterém reklamy pořád prolézají", "OK, pokračuj") { partTwo() }
                }
            }
            intro("AdBlock: klepnutím zapneš nebo vypneš blokování reklam", "OK, pokračuj") { partOne() }
        }
        105 -> {
            // sipky ve spodnim poli: zpet a vpred mezi strankami; seda = neni kam jit
            val back = findViewById<View>(R.id.btnNavBack)
            val fwd = findViewById<View>(R.id.btnNavForward)
            at(0) { say("Šipka vlevo: o stránku zpět"); moveTo(back, 600) }
            at(800) { tap(); blinkView(back) }
            at(1600) { say("Šipka vpravo: o stránku vpřed"); moveTo(fwd, 700) }
            at(900) { tap(); blinkView(fwd) }
            at(1700) { say("Když není kam jít, šipka je šedá – po klepnutí zčervená a napíše „Není kam jít“") }
            at(3200) { endDemo() }
        }
        103 -> {
            at(0) { say("Sdílet: pošli nebo zkopíruj adresu stránky"); moveTo(findViewById(R.id.ivUrlShare), 600) }
            at(800) { tap() }
            at(250) { openModal { showShareDialog(demo = true) } }
            at(900) { demoTargets["copy"]?.let { moveTo(it, 550) } }
            at(800) { press("copy") }
            at(2600) { endDemo() }
        }
        104 -> {
            at(0) { say("Zámek: informace o stránce a nastavení pro tento web"); moveTo(findViewById(R.id.ivUrlLock), 600) }
            at(800) { tap() }
            at(250) { openModal { showPageInfo(demo = true) } }
            at(900) { say("AdBlock jen pro tento web"); demoTargets["adblock"]?.let { moveTo(it, 550) } }
            at(800) { press("adblock") }   // vypne + cervena tecka u zamku + toast
            at(2300) { press("adblock") }  // zapne + toast
            at(2300) {
                say("Smazat cookies: web zapomene přihlášení a nastavení")
                demoTargets["cookies"]?.let { c ->
                    c.requestRectangleOnScreen(android.graphics.Rect(0, 0, c.width, c.height))   // dojede v posuvnem okne
                    demoHandler.postDelayed({ if (demoRunning) moveTo(c, 550) }, 350)
                }
            }
            at(1300) { press("cookies") }
            at(2600) { endDemo() }
        }
        201 -> {
            // Screenshot: nabidka -> Cela obrazovka (probliknuti) -> nabidka -> Vyber casti (ramecek). Nic se neuklada.
            val cam = (sideBarLeft as? ViewGroup)?.getChildAt(0)
            if (cam == null) { endDemo(); return true }
            at(0) { say("Screenshot: nejdřív vybereš, co chceš zachytit"); moveTo(cam, 600) }
            at(800) { tap() }
            at(250) { openModal { showScreenshotChooser(demo = true) } }
            at(900) { demoTargets["full"]?.let { moveTo(it, 550) } }
            at(800) { say("Celá obrazovka: uloží se hned"); press("full") }
            at(250) {
                demoDialogOpen = false
                val flash = View(this)
                flash.setBackgroundColor(Color.WHITE)
                flash.alpha = 0f
                ov.addView(flash, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                flash.animate().alpha(0.8f).setDuration(80).withEndAction {
                    flash.animate().alpha(0f).setDuration(300).withEndAction { ov.removeView(flash) }.start()
                }.start()
                finger.animate().alpha(0f).setDuration(100).start()
            }
            at(1500) { say("Screenshot: nebo jen část obrazovky"); moveTo(cam, 600) }
            at(800) { tap() }
            at(250) { openModal { showScreenshotChooser(demo = true) } }
            at(900) { demoTargets["part"]?.let { moveTo(it, 550) } }
            at(800) { say("Výběr části: tažením prstu nakreslíš oblast"); press("part") }
            at(500) {
                demoDialogOpen = false
                val dm = resources.displayMetrics
                val reg = DemoRegion(this)
                reg.x0 = dm.widthPixels * 0.2f; reg.y0 = dm.heightPixels * 0.28f
                reg.x1 = dm.widthPixels * 0.8f; reg.y1 = dm.heightPixels * 0.58f
                ov.addView(reg, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                moveXY(reg.x0, reg.y0, 350)
                demoHandler.postDelayed({
                    if (!demoRunning) return@postDelayed
                    val b = IntArray(2); ov.getLocationOnScreen(b)
                    val a = ValueAnimator.ofFloat(0f, 1f)
                    a.duration = 1300
                    a.addUpdateListener { an ->
                        val p = an.animatedValue as Float
                        reg.p = p; reg.invalidate()
                        finger.translationX = reg.x0 + (reg.x1 - reg.x0) * p - b[0] - fs / 2
                        finger.translationY = reg.y0 + (reg.y1 - reg.y0) * p - b[1] - fs / 2
                    }
                    a.start()
                }, 450)
            }
            at(2200) { say("Pak oblast doladíš a uložíš") }
            at(2400) { endDemo() }
        }
        202 -> {
            // Verze pro pocitac: tlacitko se jen vizualne zezelena (skutecny stav se nemeni)
            val b = (sideBarLeft as? ViewGroup)?.getChildAt(1)
            if (b == null) { endDemo(); return true }
            fun green(on: Boolean) {
                if (on) {
                    val g = GradientDrawable()
                    g.setColor("#00B055".toColorInt()); g.cornerRadius = dp(18).toFloat(); g.setStroke(dp(1), Color.WHITE)
                    b.background = g
                } else b.setBackgroundResource(R.drawable.btn_circle_dark)
            }
            at(0) { say("Verze pro počítač: stránka se zobrazí jako na počítači"); moveTo(b, 600) }
            at(800) { tap() }
            at(150) { green(true); AppToast.show(this, "Verze pro počítač (pamatuje se pro tento web)") }
            at(2400) { say("Dalším klepnutím se vrátí mobilní verze"); tap() }
            at(150) { green(false); AppToast.show(this, "Mobilní verze") }
            at(2200) { endDemo() }
        }
        203 -> {
            // Najit na strance: otevre se SKUTECNY panel hledani (ukazkovy rezim = nehleda ve strance), pisou se pismena a pocet
            val b = (sideBarLeft as? ViewGroup)?.getChildAt(2)
            if (b == null) { endDemo(); return true }
            at(0) { say("Najít na stránce: najde a zvýrazní hledaný text"); moveTo(b, 600) }
            at(800) { tap() }
            at(250) {
                showFindBar(demo = true); demoFindOpen = true
                findBarView()?.post { if (demoRunning) findBarView()?.let { focusViews(it) } }
            }
            for (w in listOf("r", "re", "rec", "rece", "recept")) at(260) { setFindDemo(w, "") }
            at(500) { setFindDemo("recept", "1 / 4"); say("Šipkami ▲ ▼ přeskakuješ mezi nalezenými místy") }
            at(2600) { endDemo() }
        }
        11, 12, 13, 14 -> {
            // Tlacitka 1-4: klepnuti + drzeni. Otevira se SKUTECNE UI (prehled karet, Oblibene, Nastaveni, okna), nic se neulozi.
            val btn = buttonView(n)
            if (btn == null) { endDemo(); return true }
            val tapText = mapOf(1 to "Klepnutí: Domů – stránka se obnoví a přejde na domovskou", 2 to "Klepnutí: přehled všech karet", 3 to "Klepnutí: otevře Oblíbené (záložky)", 4 to "Klepnutí: otevře Nastavení")
            val holdText = mapOf(1 to "Podržení: nastavení domovské stránky", 2 to "Podržení: nová karta", 3 to "Podržení: přidá stránku do složky Oblíbených", 4 to "Podržení: hodnocení a hlášení chyb")
            // okno Nastaveni v rezimu ukazky; po navratu (done) ukazka pokracuje [resume]
            fun openSettingsDemo(demo: String, resume: () -> Unit) {
                if (settingsOpen) { endDemo(); return }
                demoResume = { t = 0L; resume() }
                settingsOpen = true
                val i = settingsIntent().putExtra(SettingsActivity.EXTRA_DEMO, demo)
                when (demo) {
                    SettingsActivity.DEMO_BOOKMARKS -> i.putExtra(SettingsActivity.EXTRA_START, SettingsActivity.START_BOOKMARKS)
                    SettingsActivity.DEMO_FEEDBACK -> i.putExtra(SettingsActivity.EXTRA_START, SettingsActivity.START_FEEDBACK)
                }
                settingsLauncher.launch(i)
            }
            at(0) { focusViews(btn); say(tapText[n] ?: ""); moveTo(btn, 600) }
            at(800) { tap() }
            when (n) {
                1 -> {
                    // ukazka = otacejici se ikona obnoveni, pak ikona domu (skutecna stranka se nenacita)
                    val rm = ImageView(this)
                    rm.setImageResource(R.drawable.ic_refresh)
                    rm.setColorFilter(Color.WHITE)
                    rm.background = card()
                    rm.setPadding(dp(18), dp(18), dp(18), dp(18))
                    rm.visibility = View.INVISIBLE
                    ov.addView(rm, FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER))
                    at(150) {
                        dim.visibility = View.GONE   // po klepnuti zadne ztmaveni
                        blinkHome()
                        rm.alpha = 1f; rm.rotation = 0f; rm.visibility = View.VISIBLE
                        rm.animate().rotation(360f).setDuration(900).withEndAction {
                            rm.setImageResource(R.drawable.ic_home)   // po obnoveni skoci na domovskou
                            rm.animate().alpha(0f).setStartDelay(500).setDuration(300).start()
                        }.start()
                    }
                    at(2300) { focusViews(btn); say(holdText[1] ?: ""); moveTo(btn, 400) }   // zase ztmaveni, prst na domecku
                    at(600) { holdFinger() }
                    // ikona se zespoda vyplni, zvetsi se a az potom se otevre skutecne okno nastaveni domovske stranky (bez ztmaveni, zluty obrys)
                    at(650) {
                        animateHome {
                            if (demoRunning) {
                                releaseFinger()
                                dim.visibility = View.GONE
                                openModal { showHomeDialog() }
                                (lastDialogCard?.background as? GradientDrawable)?.mutate()?.let { (it as GradientDrawable).setStroke(dp(3), DEMO_YELLOW.toColorInt()) }
                            }
                        }
                    }
                    at(3600) { endDemo() }
                }
                2 -> {
                    at(150) {
                        spinTabs {
                            if (demoRunning) {
                                dim.visibility = View.GONE   // nejdriv je videt cely prehled karet
                                openTabsOverview(); demoTabsOpen = true
                            }
                        }
                    }
                    // bez ztmaveni problikne Bezne -> Skupiny -> Anonymni
                    at(1500) {
                        say("Běžné karty…")
                        DemoMarks.rects["tabs_normal"]?.let { moveToRect(it, 500); blinkScreen(it) }
                    }
                    at(1400) {
                        say("…skupiny karet…")
                        DemoMarks.rects["tabs_groups"]?.let { moveToRect(it, 500); blinkScreen(it) }
                    }
                    at(1400) {
                        say("…a anonymní karty")
                        DemoMarks.rects["tabs_incog"]?.let { moveToRect(it, 500); blinkScreen(it) }
                    }
                    // prehled se stahne dolu do stredu obrazovky a zavre se
                    at(1600) {
                        tabsView.pivotX = tabsView.width / 2f
                        tabsView.pivotY = tabsView.height * 0.75f
                        tabsView.animate().scaleX(0.05f).scaleY(0.05f).alpha(0f).setDuration(420).withEndAction {
                            if (demoTabsOpen) { hideTabs(); demoTabsOpen = false }
                            tabsView.scaleX = 1f; tabsView.scaleY = 1f; tabsView.alpha = 1f
                        }.start()
                    }
                    at(800) { focusViews(btn); say(holdText[2] ?: ""); moveTo(btn, 600) }   // ztmaveni, prst na tlacitko karet
                    at(800) { holdFinger() }
                    at(650) {
                        // drzeni = okno nove karty (skutecna nova karta, po ukazce se zavre)
                        animateTabsButton { }
                        flyBurst("+", "#00B055")
                        dim.visibility = View.GONE
                        demoPrevTabId = tabs.activeId
                        openStartTab()
                        demoNewTabId = tabs.activeId
                    }
                    at(700) { releaseFinger(); say("Nová karta je otevřená") }
                    at(2200) { endDemo() }
                }
                3 -> {
                    at(150) {
                        spinFavorites {
                            if (demoRunning) openSettingsDemo(SettingsActivity.DEMO_BOOKMARKS) {
                                // po navratu z Oblibenych: drzeni = skutecne okno "Pridat do slozky" (nic se neulozi)
                                at(500) { focusViews(btn); say(holdText[3] ?: ""); moveTo(btn, 600) }
                                at(800) { holdFinger() }
                                at(650) { holdIconFlip(3); animateStar { } }
                                at(700) { releaseFinger(); openModal { chooseFolderAndSave(webView.url?.takeIf { isWebUrl(it) } ?: "https://example.com", "Ukázka", demo = true) } }
                                at(500) { if (demoRunning) raiseLayer() }   // okno nacita slozky na pozadi
                                at(600) { demoTargets["folder0"]?.let { say("Vybereš složku – třeba Oblíbené…"); moveTo(it, 500); blinkView(it) } }
                                at(1500) { demoTargets["folder1"]?.let { say("…nebo další složku"); moveTo(it, 500); blinkView(it) } }
                                at(1700) { endDemo() }
                            }
                        }
                    }
                }
                else -> {
                    at(150) {
                        playSettingsOutro {
                            if (demoRunning) openSettingsDemo(SettingsActivity.DEMO_SETTINGS) {
                                // po navratu z Nastaveni: drzeni = Hodnoceni a hlaseni chyb
                                at(600) { focusViews(btn); say(holdText[4] ?: ""); moveTo(btn, 600) }
                                at(800) { holdFinger() }
                                at(650) {
                                    holdIconFlip(4)
                                    spinSettingsLogo {
                                        if (demoRunning) { releaseFinger(); openSettingsDemo(SettingsActivity.DEMO_FEEDBACK) { at(200) { endDemo() } } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        300 -> {
            // Gesta: kulicka -> otevre se plocha -> prst nakresli 9-8-5-2 (nova karta); na konci se plocha zavre a nic se neprovede
            val target = orbView?.takeIf { it.isVisible } ?: buttonView(n)
            if (target == null) { endDemo(); return true }
            var sx = 0f; var sy = 0f; var sw = 0f; var sh = 0f   // poloha plochy v okne (hlasi ji sama plocha)
            fun keyPt(k: Int): Pair<Float, Float> = Pair(sx + ((k - 1) % 3 + 0.5f) * sw / 3f, sy + ((k - 1) / 3 + 0.5f) * sh / 3f)
            fun fingerTo(pt: Pair<Float, Float>, ms: Long) {
                val wl = IntArray(2); window.decorView.getLocationOnScreen(wl)
                moveXY(pt.first + wl[0], pt.second + wl[1], ms)
            }
            fun touch(phase: Int, pt: Pair<Float, Float>) {
                val e = ExternalTouch(pt.first, pt.second, phase, ++extSeq)
                if (phase == 0) externalDown = e
                externalTouch.value = e
            }
            // kod gesta "nova karta" podle strany tlacitka gest (vlevo = zrcadlove, zacina 7)
            val code = com.example.actimelteambrowser.core.settings.Gestures.load()
                .firstOrNull { it.fn == ButtonFunction.NEW_TAB }?.code ?: "9852"
            val keys = code.map { it - '0' }
            // klepl sam uzivatel (rychle nastaveni): bez prstu, plocha se otevre hned a tip je hned nahore
            val fromUser = quickSetupStage == 21
            if (fromUser) t = 0L
            at(0) { say("Gesta: kuličkou otevřeš plochu pro gesta"); if (!fromUser) moveTo(target, 600) }
            at(if (fromUser) 0L else 800L) { if (!fromUser) tap() }
            at(if (fromUser) 0L else 300L) {
                // plocha si ztmavi pozadi sama, ukazkove ztmaveni se odstrani
                demoOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                demoOverlay = null
                externalTouch.value = null; externalDown = null
                demoGestureOpen = true
                showGestures { x, y, w, h -> sx = x; sy = y; sw = w; sh = h }
            }
            at(700) {
                if (sw <= 0f) { endDemo(); return@at }
                finger.animate().alpha(0f).setDuration(200).start()
                say("Plocha pro gesta, defaultně jsou nastavené.")
                // obrys vsech klaves plochy ~4 s problikava zlute
                val wl = IntArray(2); window.decorView.getLocationOnScreen(wl)
                for (k in 1..9) {
                    val c = (k - 1) % 3; val r = (k - 1) / 3
                    val pad = sw / 3f * 0.09f
                    val l = wl[0] + sx + c * sw / 3f + pad; val tp = wl[1] + sy + r * sh / 3f + pad
                    val rect = RectF(l, tp, l + sw / 3f - 2 * pad, tp + sh / 3f - 2 * pad)
                    val b = IntArray(2); ov.getLocationOnScreen(b)
                    blinkOutline(ov, RectF(rect.left - b[0], rect.top - b[1], rect.right - b[0], rect.bottom - b[1]), times = 9, stepMs = 220)
                }
            }
            at(4200) { say("Můžeš si přidat / odebrat gesta – je to na tobě 😃") }
            at(3000) {
                say("Tahem: 9 → 8 → 5 → 2 = nová karta".let { if (keys.firstOrNull() == 7) "Tahem: " + keys.joinToString(" → ") + " = nová karta" else it })
                fingerTo(keyPt(keys[0]), 500)
            }
            at(650) { touch(0, keyPt(keys[0])) }
            for (k in keys.drop(1)) {
                at(250) { fingerTo(keyPt(k), 400) }
                at(450) { touch(1, keyPt(k)) }
            }
            at(400) { touch(2, keyPt(keys.last())) }
            // po vyhodnoceni prvniho gesta (1,2 s): na gesta jde i klikat
            at(2200) { say("Na gesta můžeš i klikat.") }
            at(900) { fingerTo(keyPt(keys[0]), 400) }
            for (k in keys) {
                at(500) { fingerTo(keyPt(k), 300) }
                at(350) { tap(); touch(0, keyPt(k)) }
                at(120) { touch(2, keyPt(k)) }
            }
            at(1800) { say("Takže je to na tobě, jak ti to je příjemnější.") }
            at(2600) { endDemo() }
        }
    }
    return true
}

// VPN: jednoduche okno se zlutym obrysem
private fun MainActivity.showVpnSoon() {
    demoRunning = true
    val parent = root as ConstraintLayout
    val ov = FrameLayout(this)
    ov.setBackgroundColor("#B3000000".toColorInt())
    ov.isClickable = true
    val lp = ConstraintLayout.LayoutParams(0, 0)
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    parent.addView(ov, lp)
    demoOverlay = ov
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.gravity = Gravity.CENTER_HORIZONTAL
    val g = GradientDrawable()
    g.setColor("#1E1E1E".toColorInt())
    g.cornerRadius = dp(24).toFloat()
    g.setStroke(dp(3), "#FFD600".toColorInt())
    box.background = g
    box.setPadding(dp(28), dp(26), dp(28), dp(22))
    val tv = TextView(this)
    tv.text = "VPN se připravuje"
    tv.setTextColor(Color.WHITE)
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
    tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
    box.addView(tv)
    val ok = TextView(this)
    ok.text = "Okay"
    ok.setTextColor("#06281D".toColorInt())
    ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    ok.setTypeface(ok.typeface, android.graphics.Typeface.BOLD)
    ok.gravity = Gravity.CENTER
    val og = GradientDrawable()
    og.setColor("#00B055".toColorInt())
    og.cornerRadius = dp(16).toFloat()
    ok.background = og
    ok.setOnClickListener { endDemo() }
    val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
    olp.topMargin = dp(20)
    box.addView(ok, olp)
    val blp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
    blp.marginStart = dp(40); blp.marginEnd = dp(40)
    ov.addView(box, blp)
}

// Vysledek okna Nastaveni otevreneho ukazkou: done = pokracovat, jinak (preskoceno) ukazka konci
internal fun MainActivity.onDemoSettingsResult(result: String?) {
    val r = demoResume ?: return
    demoResume = null
    if (!demoRunning) return
    if (result == SettingsActivity.DEMO_DONE) { raiseLayer(); r() } else endDemo()
}

// Prvni klepnuti na tlacitko trezoru v liste: prst na tlacitko, klepnuti a otevre se trezor
// (v nem se pak sam spusti pruvodce "co je trezor"; pozdeji ho pusti otaznik vpravo dole)
internal fun MainActivity.vaultButtonDemo(btn: View) {
    if (demoRunning || !btn.isAttachedToWindow) { closeLeftBarAndHide(); openSettingsAt(SettingsActivity.START_VAULT); return }
    demoRunning = true
    val st = DemoStage(this) {
        demoRunning = false
        closeLeftBarAndHide()
        openSettingsAt(SettingsActivity.START_VAULT)
    }
    val loc = IntArray(2)
    btn.getLocationOnScreen(loc)
    val r = android.graphics.RectF(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + btn.width).toFloat(), (loc[1] + btn.height).toFloat())
    st.at(0) { st.focus(r); st.say("Trezor hesel – tvoje hesla pod zámkem 🔐"); st.moveTo(r, 650) }
    st.at(1000) { st.tap(); st.blink(r) }
    st.at(1300) {
        st.end()
        demoRunning = false
        closeLeftBarAndHide()
        openSettingsAt(SettingsActivity.START_VAULT)
    }
}


/** Nastaveni -> Tutorialy -> Demo videa: po navratu na hlavni plochu se prehraje vybrana ukazka. */
internal fun MainActivity.runDemoFromSettings(n: Int) {
    window.decorView.postDelayed({
        if (demoRunning) return@postDelayed
        when (n) {
            in 201..204 -> {
                // ukazky z bocni listy: nejdriv se lista otevre
                if (!leftBarOpen) toggleBarFrom(defaultBarOrigin())
                window.decorView.postDelayed({
                    if (n == 204) (sideBarLeft as? ViewGroup)?.getChildAt(3)?.let { vaultButtonDemo(it) }
                    else startFirstDemo(n)
                }, 500)
            }
            300 -> {
                val g = gestureButtonN()
                if (g == 0) AppToast.show(this, "Tlačítko Gesta není přiřazené – přidej ho v Nastavení → Tlačítka", true)
                else startFirstDemo(g)
            }
            7 -> if (!startFirstDemo(7)) AppToast.show(this, "Tlačítko AdBlock má teď jinou funkci", true)
            else -> startFirstDemo(n)
        }
    }, 700)
}
