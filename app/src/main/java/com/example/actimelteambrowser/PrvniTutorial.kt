package com.example.actimelteambrowser

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    private val grey = "#444444".toColorInt()
    private val darkGreen = "#06281D".toColorInt()
    private val r = RectF()
    private val save = RectF()
    private val cancel = RectF()
    private fun corner(c: Canvas, cx: Float, cy: Float) {
        c.drawCircle(cx, cy, 8f * d, handle)
        c.drawCircle(cx, cy, 8f * d, border)
    }
    override fun onDraw(c: Canvas) {
        r.set(x0, y0, x0 + (x1 - x0) * p, y0 + (y1 - y0) * p)
        c.drawRect(r, fill)
        c.drawRect(r, border)
        corner(c, r.left, r.top); corner(c, r.right, r.top); corner(c, r.left, r.bottom); corner(c, r.right, r.bottom)
        if (p >= 1f) {
            val w = 100f * d; val h = 40f * d; val top = r.bottom + 16f * d
            save.set(r.centerX() + 6f * d, top, r.centerX() + 6f * d + w, top + h)
            cancel.set(r.centerX() - 6f * d - w, top, r.centerX() - 6f * d, top + h)
            pill.color = green; c.drawRoundRect(save, h / 2, h / 2, pill)
            pill.color = grey; c.drawRoundRect(cancel, h / 2, h / 2, pill)
            label.color = darkGreen; c.drawText("Uložit", save.centerX(), save.centerY() + 5f * d, label)
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
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        r.set(1.6f * d, 1.6f * d, width - 1.6f * d, height - 1.6f * d)
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

// nova karta z ukazky zmizi a vrati se puvodni karta
private fun MainActivity.closeDemoTab() {
    if (demoNewTabId < 0) return
    val prev = tabs.list.firstOrNull { it.id == demoPrevTabId }
    if (tabs.list.size > 1) closeTabNow(demoNewTabId)
    prev?.let { switchTo(it) }
    updateTabStrips()
    demoNewTabId = -1L; demoPrevTabId = -1L
}

private fun MainActivity.endDemo() {
    demoHandler.removeCallbacksAndMessages(null)
    demoAdOrig?.let { isAdBlockOn = it; applyAdBlockUi() }   // vrati puvodni vzhled AdBlocku
    demoAdOrig = null
    if (demoGestureOpen) { hideGestures(); demoGestureOpen = false }   // zavre plochu gest, nic neprovede
    if (demoTabsOpen) { hideTabs(); demoTabsOpen = false }             // zavre prehled karet otevreny ukazkou
    if (demoFindOpen) { hideFindBar(); demoFindOpen = false }          // zavre ukazkovy panel hledani
    tabsView.scaleX = 1f; tabsView.scaleY = 1f; tabsView.alpha = 1f
    closeDemoTab()
    demoResume = null
    topDemoRestore?.let { topDemoRestore = null; it() }   // ukazka horni listy: vrati listy, tlacitka a pole
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
    // prvotni nastaveni, krok Gesta: klepnuti na tlacitko gest = ukazka gest ve smycce
    if (quickSetupStage == 2 && n in 1..12 && buttonFunction(n, false) == ButtonFunction.GESTURES) { quickSetupStage = 21; return startGestureLoopDemo() }
    // 11-14 = ukazka tlacitek 1-4 (klepnuti + drzeni), 300 = tlacitko s funkci Gesta
    val id = if (n in 1..4 && buttonFunction(n, false) != ButtonFunction.GESTURES) 10 + n else if (n in 1..12 && n != 7 && n != 8) 300 else n
    if ((n == 7 || n == 8) && buttonFunction(n, false) != ButtonDefaults.tap(n)) return false   // tlacitko ma jinou funkci
    // horni a spodni lista: vlastni prubeh (nadpis v poli, tip, OK, animace, kolecka, Preskocit)
    if (n == 7 || n == 8 || n == 103 || n == 104 || n == 105) { startBarDemo(n); return true }
    if (n in 1..4 && buttonFunction(n, false) != ButtonFunction.GESTURES) { startBarDemo(10 + n); return true }

    demoRunning = true
    if (id == 300 && quickSetupStage == 2) { quickSetupStage = 21; quickSpotOff?.invoke() }
    // bocni dema (lista, gesta): videt je horni i spodni lista, nadpis nahore i dole (v prvotnim nastaveni zustava jeho nadpis)
    val sideDemo = id in 201..203 || id == 300
    val sideTitle = when (id) { 201 -> "SCREENSHOT"; 202 -> "VERZE PRO POČÍTAČ"; 203 -> "NAJÍT NA STRÁNCE"; else -> "GESTA" }
    if (sideDemo && fieldTitleTop == null) { showFieldTitles(sideTitle, "DEMO VIDEO"); topDemoRestore = { clearFieldTitles() } }   // rychle nastaveni: ukazka gest, po ni dotaz "nechat tlacitko?"
    val parent = root as ConstraintLayout
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
            else -> dim.holes.add(rectOf(adBtn).apply { inset(pad, pad) })
        }
        if (sideDemo) { dim.holes.add(rectOf(topBar)); dim.holes.add(rectOf(bottomBar)) }
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
    if (id == 300 || id == 203 || id in 11..14) clp.topMargin = topBar.bottom + dp(12) else clp.bottomMargin = skipBottom + dp(72)
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

    // "prst" = zluta tecka (pohyb, klepnuti = zmackne se, drzeni = zvetsi se)
    val fs = dp(20)
    val finger = View(this)
    finger.background = demoDotDrawable(this)
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
        finger.animate().scaleX(0.55f).scaleY(0.55f).setDuration(110).withEndAction { finger.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start()
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
        if (sideDemo) { dim.holes.add(rectOf(topBar)); dim.holes.add(rectOf(bottomBar)) }
        dim.visibility = View.VISIBLE
        dim.invalidate()
    }
    // casovy plan: kazdy krok ma svoje zpozdeni od zacatku
    var t = 350L
    fun at(delay: Long, block: () -> Unit) { t += delay; demoHandler.postDelayed({ if (demoRunning) block() }, t) }

    when (id) {
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
    val r = RectF(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + btn.width).toFloat(), (loc[1] + btn.height).toFloat())
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
            com.example.actimelteambrowser.ui.SETUP_AGAIN_ID -> startQuickSetupAgain()   // Tutorialy -> Prvotni nastaveni
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

// ======================= DEMO VIDEA HORNI LISTY (AdBlock, VPN, Sdilet, zamek) =======================
// Prubeh: nadpis v hornim poli + uvodni tip + "OK, pokračuj" -> animace -> zaverecny tip (+ dalsi cast / Hotovo).
// Videt je cela horni lista; spodni lista, bocni tlacitka a kulicka jsou schovane. Misto spodni listy je Preskocit
// (jen tento tutorial / vsechny tutorialy). Vic casti (klepnuti, drzeni) = kolecka nad Preskocit, jdou libovolne preklikat.

private var topDemoRestore: (() -> Unit)? = null

/** Rizeni animace casti: at = krok v ms od zacatku animace, finish = cast skoncila (u casti s ms = 0). */
private class DemoCtl(val at: (Long, () -> Unit) -> Unit, val finish: () -> Unit)

/** Cast ukazky: uvodni tip, zaverecny tip, delka animace (0 = skonci sama pres finish) a animace. */
private class TopPart(val intro: String, val outro: String, val ms: Long, val run: (DemoCtl) -> Unit)

/** Preskocit vsechny tutorialy: nic uz nesviti ani neblika zlute. */
internal fun MainActivity.skipAllTutorials() {
    com.example.actimelteambrowser.core.settings.AppSettings.liveTutorial.set("")
    applyLiveTutorial()
}

internal fun MainActivity.demoButton(text: String, primary: Boolean): TextView {
    val b = TextView(this)
    b.text = text
    b.setTextColor(if (primary) "#06281D".toColorInt() else Color.WHITE)
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
    b.gravity = Gravity.CENTER
    b.setPadding(dp(12), dp(12), dp(12), dp(12))
    if (primary) {
        val g = GradientDrawable()
        g.setColor("#00B055".toColorInt())
        g.cornerRadius = dp(22).toFloat()
        g.setStroke(dp(1), Color.WHITE)   // OK, pokracuj ma vzdy tenky bily obrys
        b.background = g
    } else b.background = demoSkipBg(this)
    return b
}

private fun MainActivity.startBarDemo(id: Int) {
    demoRunning = true
    val bottom = id in 11..14 || id == 105   // spodni lista (Domu, sipky, karty, Oblibene, Nastaveni)
    val parent = root as ConstraintLayout
    val adBtn = findViewById<View>(R.id.btnAdBlock)
    val shareBtn = findViewById<View>(R.id.ivUrlShare)
    val lockBtn = findViewById<View>(R.id.ivUrlLock)
    val vpnBtn = findViewById<View>(R.id.btnVpn)
    val backBtn = findViewById<View>(R.id.btnNavBack)
    val fwdBtn = findViewById<View>(R.id.btnNavForward)
    setChromeHidden(false)
    if (leftBarOpen) closeLeftBarAndHide()

    // videt je jen ukazovana lista; ostatni zmizi (vrati se v endDemo)
    val hidden = ArrayList<Pair<View, Int>>()
    fun hide(v: View?) { if (v != null && v.isVisible) { hidden.add(v to v.visibility); v.visibility = View.INVISIBLE } }
    hide(if (bottom) topBar else bottomBar)
    for (k in listOf(5, 6) + extraViews.keys) hide(buttonView(k))
    hide(orbView)
    stripViews().forEach { hide(it.first) }
    tutorialPlus.forEach { hide(it) }
    val title = when (id) {
        7 -> "TLAČÍTKO ADBLOCK"; 8 -> "TLAČÍTKO VPN"; 103 -> "TLAČÍTKO SDÍLET"; 104 -> "ZÁMEK"
        11 -> "DOMOVSKÁ STRÁNKA"; 12 -> "NOVÉ OKNO A ZÁLOŽKY"; 13 -> "OBLÍBENÉ ZÁLOŽKY"; 14 -> "NASTAVENÍ + RECENZE"
        else -> "KROK VPŘED + VZAD"
    }
    // nadpis do pole ukazovane listy; pole jsou cista (ikony zustanou jen u ukazovaneho tlacitka)
    val keep = when (id) { 103 -> setOf(R.id.ivUrlShare); 104 -> setOf(R.id.ivUrlLock); 105 -> setOf(R.id.btnNavBack, R.id.btnNavForward); else -> emptySet() }
    if (bottom) showFieldTitles(null, title, keep) else showFieldTitles(title, null, keep)
    var resetHook: (() -> Unit)? = null
    topDemoRestore = { hidden.forEach { (v, vis) -> v.visibility = vis }; clearFieldTitles() }

    // ztmaveni: jasna je cela ukazovana lista
    val dim = DemoDim(this)
    val dlp = ConstraintLayout.LayoutParams(0, 0)
    dlp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    dlp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    dlp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    dlp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    dim.elevation = dp(100).toFloat()
    parent.addView(dim, dlp)
    demoOverlay = dim
    dim.post { dim.holes.clear(); dim.holes.add(rectOf(if (bottom) bottomBar else topBar)); dim.invalidate() }
    fun dimOff() { dim.visibility = View.GONE }
    fun dimOn() { dim.visibility = View.VISIBLE }

    // nejvyssi vrstva: tecka, tip + OK, kolecka, Preskocit (u spodni listy nad listou)
    val ov = FrameLayout(this)
    ov.isClickable = true
    layerContent = ov
    val skipBottom = if (bottom) (if (bottomBar.height > 0) bottomBar.height else lastSystemBottom + dp(80)) + dp(12) else lastSystemBottom + dp(20)

    // rozlozeni odspodu: kolecka (jen u vic casti), nad nimi Preskocit, nad nim OK, pokracuj a nad nim tip
    val dotsRow = LinearLayout(this)
    dotsRow.orientation = LinearLayout.HORIZONTAL
    dotsRow.gravity = Gravity.CENTER
    val dlp2 = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    dlp2.bottomMargin = skipBottom
    ov.addView(dotsRow, dlp2)

    val skip = demoButton(getString(R.string.tutorial_skip), false)
    skip.setPadding(dp(28), dp(12), dp(28), dp(12))
    val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    slp.bottomMargin = skipBottom
    ov.addView(skip, slp)

    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    val caption = TextView(this)
    caption.setTextColor(Color.WHITE)
    caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    caption.gravity = Gravity.CENTER
    caption.setLineSpacing(0f, 1.12f)
    caption.setPadding(dp(18), dp(14), dp(18), dp(14))
    caption.background = demoCaptionBg(this)
    box.addView(caption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    val ok = demoButton("OK, pokračuj", true)
    val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    olp.topMargin = dp(12)
    box.addView(ok, olp)
    val blp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
    blp.marginStart = dp(24); blp.marginEnd = dp(24)
    blp.bottomMargin = skipBottom + dp(58)
    ov.addView(box, blp)

    // Preskocit: jen tento tutorial, nebo vsechny (to se jeste potvrzuje)
    val choice = LinearLayout(this)
    choice.orientation = LinearLayout.VERTICAL
    choice.background = demoCaptionBg(this)
    choice.setPadding(dp(16), dp(14), dp(16), dp(14))
    val chText = TextView(this)
    chText.setTextColor(Color.WHITE)
    chText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    chText.gravity = Gravity.CENTER
    choice.addView(chText)
    val chNote = TextView(this)
    chNote.text = "(všechny tutoriály jsou k přehrání v Nastavení → Tutoriály)"
    chNote.setTextColor("#AAAAAA".toColorInt())
    chNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    chNote.gravity = Gravity.CENTER
    val nlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    nlp.topMargin = dp(6)
    choice.addView(chNote, nlp)
    val chA = demoButton("", true)
    val chB = demoButton("", false)
    val chBack = demoButton("Zpět", false)
    chBack.background = GradientDrawable().apply { setColor("#333333".toColorInt()); cornerRadius = dp(22).toFloat() }
    for (b in listOf(chA, chB, chBack)) {
        val l = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.topMargin = dp(10)
        choice.addView(b, l)
    }
    choice.visibility = View.GONE
    val chlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
    chlp.marginStart = dp(24); chlp.marginEnd = dp(24)
    chlp.bottomMargin = skipBottom + dp(58)
    ov.addView(choice, chlp)
    var boxWasVisible = true
    fun closeChoice() { choice.visibility = View.GONE; if (boxWasVisible) box.visibility = View.VISIBLE }
    // 1. krok: co preskocit
    fun askWhat() {
        chText.text = "Co chceš přeskočit?"
        chNote.visibility = View.GONE
        chA.text = "Pouze tento tutoriál"; chB.text = "Přeskočit všechny tutoriály"
        chBack.visibility = View.VISIBLE
        chA.setOnClickListener { endDemo() }
        chB.setOnClickListener {
            // 2. krok: potvrzeni
            chText.text = "Opravdu chceš přeskočit všechny tutoriály?"
            chNote.visibility = View.VISIBLE
            chA.text = "ANO"; chB.text = "NE"
            chBack.visibility = View.GONE
            chA.setOnClickListener { skipAllTutorials(); endDemo(); AppToast.show(this, "Tutoriály přeskočeny") }
            chB.setOnClickListener { closeChoice() }
        }
    }
    skip.setOnClickListener {
        if (choice.isVisible) return@setOnClickListener
        boxWasVisible = box.isVisible
        box.visibility = View.INVISIBLE
        askWhat()
        choice.visibility = View.VISIBLE
    }
    chBack.setOnClickListener { closeChoice() }

    // tecka
    val fs = dp(20)
    val finger = View(this)
    finger.background = demoDotDrawable(this)
    finger.alpha = 0f
    ov.addView(finger, FrameLayout.LayoutParams(fs, fs))
    raiseLayer()

    fun screenRect(v: View): RectF {
        val a = IntArray(2); v.getLocationOnScreen(a)
        return RectF(a[0].toFloat(), a[1].toFloat(), (a[0] + v.width).toFloat(), (a[1] + v.height).toFloat())
    }
    fun local(r: RectF): RectF { val b = IntArray(2); ov.getLocationOnScreen(b); return RectF(r.left - b[0], r.top - b[1], r.right - b[0], r.bottom - b[1]) }
    fun moveToRect(sr: RectF, ms: Long) {
        val r = local(sr)
        finger.animate().alpha(1f).translationX(r.centerX() - fs / 2f).translationY(r.centerY() - fs / 2f).setDuration(ms).start()
    }
    fun moveTo(v: View, ms: Long) { if (v.isAttachedToWindow) moveToRect(screenRect(v), ms) }
    fun fingerFromCenter() {
        finger.animate().cancel()
        finger.scaleX = 1f; finger.scaleY = 1f
        finger.translationX = ov.width / 2f - fs / 2f
        finger.translationY = ov.height / 2f - fs / 2f
        finger.alpha = 0f
        finger.animate().alpha(1f).setDuration(200).start()
    }
    fun fingerHide() { finger.animate().alpha(0f).setDuration(200).start() }
    fun tap() { finger.animate().scaleX(0.55f).scaleY(0.55f).setDuration(110).withEndAction { finger.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start() }
    fun holdFinger() { finger.animate().scaleX(1.7f).scaleY(1.7f).setDuration(500).start() }
    fun releaseFinger() { finger.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }
    fun blinkFast(v: View?) { if (v != null && v.isAttachedToWindow) blinkOutline(ov, local(screenRect(v)), times = 3, stepMs = 120) }
    fun blink(v: View?) { if (v != null && v.isAttachedToWindow) blinkOutline(ov, local(screenRect(v))) }
    fun blinkRect(sr: RectF) = blinkOutline(ov, local(sr))
    fun press(key: String) { demoTargets[key]?.let { tap(); it.performClick() } }
    fun openModal(open: () -> Unit) {
        open(); demoDialogOpen = true
        demoHandler.postDelayed({ if (demoRunning) raiseLayer() }, 120)
    }
    fun closeModal() { if (demoDialogOpen) { runCatching { lastDialogDismiss?.invoke() }; demoDialogOpen = false } }
    // okno Nastaveni v rezimu ukazky; po navratu (hotovo) ukazka pokracuje [resume], Preskocit v nem ukazku ukonci
    fun openSettingsDemo(demo: String, resume: () -> Unit) {
        if (settingsOpen) { endDemo(); return }
        demoResume = resume
        settingsOpen = true
        val i = settingsIntent().putExtra(SettingsActivity.EXTRA_DEMO, demo)
        when (demo) {
            SettingsActivity.DEMO_BOOKMARKS -> i.putExtra(SettingsActivity.EXTRA_START, SettingsActivity.START_BOOKMARKS)
            SettingsActivity.DEMO_FEEDBACK -> i.putExtra(SettingsActivity.EXTRA_START, SettingsActivity.START_FEEDBACK)
        }
        settingsLauncher.launch(i)
    }

    // ---------------- casti ukazky ----------------
    val parts = ArrayList<TopPart>()
    val target: View? = when (id) {
        7 -> adBtn; 8 -> vpnBtn; 103 -> shareBtn; 104 -> lockBtn; 105 -> backBtn
        else -> buttonView(id - 10)
    }
    when (id) {
        7 -> {
            demoAdOrig = isAdBlockOn
            resetHook = { if (!isAdBlockOn) { isAdBlockOn = true; applyAdBlockUi() } }   // kazda cast zacina se zapnutym AdBlockem
            fun flip() { isAdBlockOn = !isAdBlockOn; applyAdBlockUi(); AppToast.show(this, if (isAdBlockOn) "AdBlock zapnut" else "AdBlock vypnut") }
            parts += TopPart(
                "Klepnutím na AdBlock zapneš nebo vypneš blokování reklam.",
                "Číslo na tlačítku ukazuje, kolik reklam se na stránce zablokovalo.", 5200
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(adBtn, 650) }
                c.at(1050) { tap() }
                c.at(1200) { flip() }
                c.at(1500) { fingerHide() }
                c.at(3000) { fingerFromCenter() }
                c.at(3250) { moveTo(adBtn, 650) }
                c.at(4050) { tap() }
                c.at(4200) { flip() }
                c.at(4500) { fingerHide() }
            }
            parts += TopPart(
                "Podržením AdBlocku přidáš web, na kterém reklamy pořád prolézají – bude se na něm blokovat přísněji.",
                "Hotovo – web se přidá k přísnějšímu blokování (v ukázce se nic neuloží).", 7600
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(adBtn, 650) }
                c.at(1000) { holdFinger() }
                c.at(1900) { releaseFinger(); fingerHide() }
                c.at(2200) { openModal { showAdBlockSitesDialog(demo = true) } }
                c.at(3100) { fingerFromCenter() }
                c.at(3350) { demoTargets["site_current"]?.let { moveTo(it, 600) } }
                c.at(4150) { press("site_current") }
                c.at(5000) { demoTargets["site_ok"]?.let { moveTo(it, 550) } }
                c.at(5800) { press("site_ok") }
                c.at(6000) { raiseLayer() }
                c.at(6300) { fingerHide() }
                c.at(7400) { closeModal() }
            }
        }
        103 -> {
            parts += TopPart(
                "Klepnutím na Sdílet otevřeš okno, kde adresu stránky zkopíruješ nebo pošleš.",
                "Kopírovat = adresa do schránky. Sdílet = pošleš ji třeba do zprávy. Přepínačem QR kód sdílíš místo adresy QR kód.", 5000
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(shareBtn, 650) }
                c.at(1000) { tap() }
                c.at(1200) { openModal { showShareDialog(demo = true) } }
                c.at(1700) { fingerHide() }
                c.at(2000) { blinkFast(demoTargets["copy"]) }        // Kopirovat 3x rychle
                c.at(2900) { blinkFast(lastDialogPositive) }         // pak Sdilet 3x rychle
                c.at(4700) { closeModal() }
            }
            parts += TopPart(
                "Podržením Sdílet rovnou otevřeš sdílení telefonu – bez okna.",
                "Tak adresu pošleš nejrychleji.", 3000
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(shareBtn, 650) }
                c.at(1000) { holdFinger() }
                c.at(1900) { releaseFinger(); blinkFast(shareBtn); AppToast.show(this, "Otevře se sdílení telefonu (v ukázce ne)") }
                c.at(2400) { fingerHide() }
            }
        }
        104 -> {
            parts += TopPart(
                "Zámek ukazuje, jestli je spojení se stránkou šifrované. Klepnutím otevřeš informace o stránce a nastavení jen pro tento web.",
                "Zelený zámek = šifrované spojení (https), červený = nešifrované (http). Tady si taky vypneš AdBlock jen pro tento web nebo smažeš jeho cookies.", 7800
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(lockBtn, 650) }
                c.at(1000) { tap() }
                c.at(1200) { openModal { showPageInfo(demo = true) } }
                c.at(2000) { demoTargets["adblock"]?.let { moveTo(it, 550) } }
                c.at(2700) { press("adblock") }   // vypne + cervena tecka u zamku + toast
                c.at(4000) { press("adblock") }   // zapne + toast
                c.at(4800) {
                    demoTargets["cookies"]?.let { v ->
                        v.requestRectangleOnScreen(android.graphics.Rect(0, 0, v.width, v.height))
                        demoHandler.postDelayed({ if (demoRunning) moveTo(v, 550) }, 350)
                    }
                }
                c.at(6000) { press("cookies") }
                c.at(6600) { fingerHide() }
                c.at(7500) { closeModal() }
            }
        }
        8 -> {
            parts += TopPart(
                "VPN skryje, odkud se připojuješ – provoz půjde šifrovaně přes jiný server.",
                "VPN se ještě připravuje. Až bude hotová, zapneš ji tady jedním klepnutím.", 2600
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(vpnBtn, 650) }
                c.at(1000) { tap(); blinkFast(vpnBtn) }
                c.at(1200) { AppToast.show(this, "VPN se připravuje") }
                c.at(1800) { fingerHide() }
            }
        }
        105 -> {
            parts += TopPart(
                "Šipkami ve spodním poli přejdeš o stránku zpět nebo vpřed.",
                "Když není kam jít, šipka po klepnutí zčervená a napíše „Není kam jít“.", 4200
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(backBtn, 650) }
                c.at(1000) { tap(); blinkFast(backBtn); AppToast.show(this, "O stránku zpět") }
                c.at(1900) { moveTo(fwdBtn, 800) }
                c.at(2800) { tap(); blinkFast(fwdBtn); AppToast.show(this, "O stránku vpřed") }
                c.at(3600) { fingerHide() }
            }
        }
        11 -> {
            val btn = buttonView(1) ?: run { endDemo(); return }
            // obnoveni: otacejici se ikona, pak domecek (skutecna stranka se nenacita)
            val rm = ImageView(this)
            rm.setColorFilter(Color.WHITE)
            rm.background = card()
            rm.setPadding(dp(18), dp(18), dp(18), dp(18))
            rm.visibility = View.INVISIBLE
            ov.addView(rm, FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER))
            resetHook = { rm.animate().cancel(); rm.visibility = View.INVISIBLE }
            parts += TopPart(
                "Klepnutím na Domů se stránka obnoví a přejdeš na domovskou stránku.",
                "", 3000
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { tap(); blinkHome() }
                c.at(1150) {
                    rm.setImageResource(R.drawable.ic_refresh)
                    rm.alpha = 1f; rm.rotation = 0f; rm.visibility = View.VISIBLE
                    rm.animate().rotation(360f).setDuration(900).withEndAction {
                        rm.setImageResource(R.drawable.ic_home)
                        rm.animate().alpha(0f).setStartDelay(500).setDuration(300).start()
                    }.start()
                }
                c.at(1400) { fingerHide() }
            }
            parts += TopPart(
                "Podržením tlačítka si nastavíš svou domovskou stránku a hlavní věci pro prohlížeč (vyhledávač, karty při zapnutí, …).",
                "Sem napíšeš adresu, kam se má Domů vracet.", 0
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { holdFinger() }
                c.at(1500) {
                    animateHome {
                        if (!demoRunning) return@animateHome
                        releaseFinger(); fingerHide()
                        openModal { showHomeDialog() }
                        (lastDialogCard?.background as? GradientDrawable)?.mutate()?.let { (it as GradientDrawable).setStroke(dp(3), DEMO_YELLOW.toColorInt()) }
                        demoHandler.postDelayed({ c.finish() }, 2600)
                    }
                }
            }
        }
        12 -> {
            val btn = buttonView(2) ?: run { endDemo(); return }
            resetHook = {
                dimOn()
                if (demoTabsOpen) { hideTabs(); demoTabsOpen = false }
                tabsView.animate().cancel(); tabsView.scaleX = 1f; tabsView.scaleY = 1f; tabsView.alpha = 1f
                closeDemoTab()
            }
            parts += TopPart(
                "Klepnutím na tlačítko karet otevřeš přehled všech karet (záložek).",
                "Karty máš běžné, ve skupinách a anonymní.", 0
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { tap() }
                c.at(1150) {
                    spinTabs {
                        if (!demoRunning) return@spinTabs
                        fingerHide(); dimOff()
                        openTabsOverview(); demoTabsOpen = true
                        val steps = listOf("tabs_normal", "tabs_groups", "tabs_incog")
                        for ((i, key) in steps.withIndex()) demoHandler.postDelayed({
                            if (demoRunning) DemoMarks.rects[key]?.let { moveToRect(it, 450); blinkRect(it) }
                        }, 700L + i * 1300L)
                        demoHandler.postDelayed({
                            if (!demoRunning) return@postDelayed
                            fingerHide()
                            tabsView.pivotX = tabsView.width / 2f
                            tabsView.pivotY = tabsView.height * 0.75f
                            tabsView.animate().scaleX(0.05f).scaleY(0.05f).alpha(0f).setDuration(420).withEndAction {
                                if (demoTabsOpen) { hideTabs(); demoTabsOpen = false }
                                tabsView.scaleX = 1f; tabsView.scaleY = 1f; tabsView.alpha = 1f
                                dimOn()
                                c.finish()
                            }.start()
                        }, 700L + 3 * 1300L + 400L)
                    }
                }
            }
            parts += TopPart(
                "Podržením tlačítka karet otevřeš nové okno (novou kartu).",
                "Nová karta je otevřená (po ukázce se zase zavře).", 3400
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { holdFinger() }
                c.at(1700) {
                    animateTabsButton { }
                    flyBurst("+", "#00B055")
                    dimOff()
                    demoPrevTabId = tabs.activeId
                    openStartTab()
                    demoNewTabId = tabs.activeId
                }
                c.at(2300) { releaseFinger(); fingerHide() }
            }
        }
        13 -> {
            val btn = buttonView(3) ?: run { endDemo(); return }
            parts += TopPart(
                "Klepnutím na hvězdu otevřeš Oblíbené – uložené stránky ve složkách.",
                "Stránky si třídíš do složek, jak se ti to hodí.", 0
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { tap() }
                c.at(1150) { spinFavorites { if (demoRunning) { fingerHide(); openSettingsDemo(SettingsActivity.DEMO_BOOKMARKS) { c.finish() } } } }
            }
            parts += TopPart(
                "Podržením hvězdy přidáš aktuální stránku do složky Oblíbených.",
                "Vybereš složku a stránka se do ní uloží (v ukázce se nic neuloží).", 5600
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { holdFinger() }
                c.at(1500) { holdIconFlip(3); animateStar { } }
                c.at(2200) { releaseFinger(); fingerHide(); openModal { chooseFolderAndSave(webView.url?.takeIf { isWebUrl(it) } ?: "https://example.com", "Ukázka", demo = true) } }
                c.at(2700) { raiseLayer() }   // okno nacita slozky na pozadi
                c.at(3200) { demoTargets["folder0"]?.let { moveTo(it, 500); blink(it) } }
                c.at(4300) { demoTargets["folder1"]?.let { moveTo(it, 500); blink(it) } }
                c.at(5300) { fingerHide(); closeModal() }
            }
        }
        14 -> {
            val btn = buttonView(4) ?: run { endDemo(); return }
            parts += TopPart(
                "Klepnutím na logo otevřeš Nastavení.",
                "V Nastavení si upravíš tlačítka, gesta, vzhled a všechno ostatní.", 0
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { tap() }
                c.at(1150) { playSettingsOutro { if (demoRunning) { fingerHide(); openSettingsDemo(SettingsActivity.DEMO_SETTINGS) { c.finish() } } } }
            }
            parts += TopPart(
                "Podržením loga otevřeš Hodnocení a hlášení chyb.",
                "Napiš recenzi nebo nahlas chybu – moc to pomůže.", 0
            ) { c ->
                c.at(0) { fingerFromCenter() }
                c.at(250) { moveTo(btn, 650) }
                c.at(1000) { holdFinger() }
                c.at(1500) {
                    holdIconFlip(4)
                    spinSettingsLogo { if (demoRunning) { releaseFinger(); fingerHide(); openSettingsDemo(SettingsActivity.DEMO_FEEDBACK) { c.finish() } } }
                }
            }
        }
    }
    if (parts.isEmpty()) { endDemo(); return }

    // ---------------- prubeh ----------------
    var gen = 0
    var okAction: (() -> Unit)? = null
    val done = HashSet<Int>()
    val dots = ArrayList<DemoDot>()
    fun after(ms: Long, g: Int, block: () -> Unit) { demoHandler.postDelayed({ if (demoRunning && g == gen) block() }, ms) }
    fun setOk(text: String, action: () -> Unit) { ok.text = text; okAction = action; box.visibility = View.VISIBLE }
    ok.setOnClickListener { okAction?.let { a -> okAction = null; a() } }
    fun paintDots(current: Int) { for ((i, d) in dots.withIndex()) { d.p = if (i in done && i != current) 1f else 0f; d.invalidate() } }
    fun resetStage() {
        closeModal()
        finger.animate().cancel(); finger.alpha = 0f; finger.scaleX = 1f; finger.scaleY = 1f
        resetHook?.invoke()
        choice.visibility = View.GONE
    }

    fun runAnim(k: Int) {
        val g = ++gen
        resetStage()
        paintDots(k)
        box.visibility = View.INVISIBLE   // behem animace tip nezakryva okna
        val part = parts[k]
        val dot = dots.getOrNull(k)
        if (dot != null) {
            val a = ValueAnimator.ofFloat(0f, if (part.ms > 0) 1f else 0.6f)
            a.duration = if (part.ms > 0) part.ms else 1500
            a.addUpdateListener { an -> if (g == gen) { dot.p = an.animatedValue as Float; dot.invalidate() } }
            a.start()
        }
        var finished = false
        val finish = {
            if (!finished && demoRunning && g == gen) {
                finished = true
                dot?.let { it.p = 1f; it.invalidate() }
                done.add(k)
                val next = k + 1
                if (next < parts.size) {
                    caption.text = listOf(part.outro, parts[next].intro).filter { it.isNotBlank() }.joinToString("\n\n")
                    setOk("OK, pokračuj") { runAnim(next) }
                    blink(target)
                } else {
                    caption.text = part.outro
                    setOk("Hotovo") { endDemo() }
                }
            }
        }
        part.run(DemoCtl({ ms, block -> after(ms, g, block) }, finish))
        if (part.ms > 0) after(part.ms + 250, g) { finish() }
    }
    fun showIntro(k: Int) {
        ++gen
        resetStage()
        paintDots(k)
        caption.text = parts[k].intro
        setOk("OK, pokračuj") { runAnim(k) }
        ov.post { blink(target) }   // ukazovane tlacitko 3x zlute problikne
    }
    if (parts.size > 1) {
        // kolecka jsou uplne dole: Preskocit, OK a tip se posunou nad ne
        slp.bottomMargin = skipBottom + dp(42); skip.layoutParams = slp
        blp.bottomMargin = skipBottom + dp(100); box.layoutParams = blp
        chlp.bottomMargin = skipBottom + dp(100); choice.layoutParams = chlp
    }
    if (parts.size > 1) for (k in parts.indices) {
        val hit = FrameLayout(this)
        val d = DemoDot(this)
        hit.addView(d, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        hit.setOnClickListener { if (!choice.isVisible) showIntro(k) }   // libovolne preklikani mezi castmi
        dotsRow.addView(hit, LinearLayout.LayoutParams(dp(40), dp(40)))
        dots.add(d)
    }
    showIntro(0)
}

// ======================= PRVOTNI NASTAVENI: UKAZKA GEST VE SMYCCE =======================
// Plocha gest se otevre a dokola se ukazuje: tah gesta -> uspech -> pauza -> naklikani klaves -> uspech -> pauza.
// Nahore tip a "OK, pokračuj" (ukonci ukazku, nastaveni jde dal). Nic se neprovede.
private fun MainActivity.startGestureLoopDemo(): Boolean {
    demoRunning = true
    onSetupGestureTapped()
    val ov = FrameLayout(this)
    ov.isClickable = true
    layerContent = ov

    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    val caption = TextView(this)
    caption.text = "Gesto aktivuješ buď tažením prstu přes klávesy, nebo ho postupně naklikáš – klávesu po klávese. Je to na tobě, co ti sedí víc."
    caption.setTextColor(Color.WHITE)
    caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    caption.gravity = Gravity.CENTER
    caption.setLineSpacing(0f, 1.12f)
    caption.setPadding(dp(18), dp(14), dp(18), dp(14))
    caption.background = demoCaptionBg(this)
    box.addView(caption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    val ok = demoButton("OK, pokračuj", true)
    ok.setOnClickListener { endDemo() }
    val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    olp.topMargin = dp(12)
    box.addView(ok, olp)
    val blp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
    blp.marginStart = dp(24); blp.marginEnd = dp(24)
    blp.topMargin = topBar.bottom + dp(12)
    ov.addView(box, blp)

    val fs = dp(20)
    val finger = View(this)
    finger.background = demoDotDrawable(this)
    finger.alpha = 0f
    ov.addView(finger, FrameLayout.LayoutParams(fs, fs))
    raiseLayer()

    var sx = 0f; var sy = 0f; var sw = 0f; var sh = 0f   // poloha plochy v okne (hlasi ji sama plocha)
    fun keyPt(k: Int): Pair<Float, Float> = Pair(sx + ((k - 1) % 3 + 0.5f) * sw / 3f, sy + ((k - 1) / 3 + 0.5f) * sh / 3f)
    fun fingerTo(pt: Pair<Float, Float>, ms: Long) {
        val wl = IntArray(2); window.decorView.getLocationOnScreen(wl)
        val b = IntArray(2); ov.getLocationOnScreen(b)
        finger.animate().alpha(1f).translationX(pt.first + wl[0] - b[0] - fs / 2f).translationY(pt.second + wl[1] - b[1] - fs / 2f).setDuration(ms).start()
    }
    fun touch(phase: Int, pt: Pair<Float, Float>) {
        val e = ExternalTouch(pt.first, pt.second, phase, ++extSeq)
        if (phase == 0) externalDown = e
        externalTouch.value = e
    }
    fun tap() { finger.animate().scaleX(0.55f).scaleY(0.55f).setDuration(110).withEndAction { finger.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start() }
    val def = com.example.actimelteambrowser.core.settings.Gestures.load().firstOrNull { it.fn == ButtonFunction.NEW_TAB }
    val keys = (def?.code ?: "9852").map { it - '0' }
    val label = def?.fn?.label ?: "Nová karta"
    fun success() {
        val p = keyPt(keys.last())
        showFirework(p.first, p.second)
        AppToast.show(this, "Gesto rozpoznáno: $label", false, AppToast.Kind.OK)
    }

    externalTouch.value = null; externalDown = null
    demoGestureOpen = true
    showGestures { x, y, w, h -> sx = x; sy = y; sw = w; sh = h }

    fun cycle() {
        if (!demoRunning) return
        if (sw <= 0f) { demoHandler.postDelayed({ cycle() }, 300); return }
        var t = 0L
        fun at(d: Long, block: () -> Unit) { t += d; demoHandler.postDelayed({ if (demoRunning) block() }, t) }
        // 1) tah
        at(0) { fingerTo(keyPt(keys[0]), 450) }
        at(550) { touch(0, keyPt(keys[0])) }
        for (k in keys.drop(1)) {
            at(250) { fingerTo(keyPt(k), 380) }
            at(420) { touch(1, keyPt(k)) }
        }
        at(350) { touch(2, keyPt(keys.last())) }
        at(250) { success(); finger.animate().alpha(0f).setDuration(250).start() }
        // 2) naklikani klaves postupne
        at(1700) { fingerTo(keyPt(keys[0]), 400) }
        for (k in keys) {
            at(480) { fingerTo(keyPt(k), 300) }
            at(340) { tap(); touch(0, keyPt(k)) }
            at(110) { touch(2, keyPt(k)) }
        }
        at(450) { success(); finger.animate().alpha(0f).setDuration(250).start() }
        at(1800) { cycle() }
    }
    demoHandler.postDelayed({ cycle() }, 700)
    return true
}
