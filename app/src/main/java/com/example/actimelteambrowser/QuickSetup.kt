package com.example.actimelteambrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import android.view.Gravity
import android.graphics.Typeface
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.ButtonFunction

/**
 * Po uvodni zprave: dotaz na vychozi prohlizec a pak volba
 *  - Prejit rychle nastaveni = vse v zakladu zlute (zivy tutorial),
 *  - Rychle nastaveni po etapach: 1) domovska stranka + karty po startu, 2) gesta (ukazka + nechat tlacitko?),
 *    3) pridatelna tlacitka (klepnuti / drzeni), 4) bocni lista + pravidlo lista / gesta, 5) export a import.
 *    (Trezor bude dalsi etapa pozdeji.)
 */

// 0 = nic, 2 = ceka se na klepnuti na tlacitko gest, 21 = bezi ukazka gest
internal var quickSetupStage = 0

// Ztmaveni rychleho nastaveni (vse tmave krome [targets], tip nahore); zavola se pro odstraneni
internal var quickSpotOff: (() -> Unit)? = null

private fun MainActivity.quickSpotlight(targets: List<View>, tip: String, circle: Boolean = false, onSkip: (() -> Unit)? = null, holeScale: Float = 1f) {
    quickSpotOff?.invoke()
    val parent = root as ConstraintLayout
    val dim = DemoDim(this)
    dim.passHoles = true   // na ukazovany prvek jde klepnout
    dim.circle = circle
    val lp = ConstraintLayout.LayoutParams(0, 0)
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    dim.elevation = dp(100).toFloat()
    parent.addView(dim, lp)
    val cap = TextView(this)
    if (tip.isBlank()) cap.visibility = View.GONE
    cap.text = tip
    cap.setTextColor(Color.WHITE)
    cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    cap.gravity = Gravity.CENTER
    cap.setPadding(dp(18), dp(14), dp(18), dp(14))
    cap.background = demoCaptionBg(this)
    cap.elevation = dp(101).toFloat()
    val clp = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    clp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    clp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    clp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    clp.topMargin = lastSystemTopPx + dp(80)
    clp.marginStart = dp(28); clp.marginEnd = dp(28)
    parent.addView(cap, clp)
    // Preskocit: dole nad spodni listou, je tam po celou dobu kroku
    var skip: TextView? = null
    if (onSkip != null) {
        val sk = TextView(this)
        sk.text = getString(R.string.tutorial_skip)
        sk.setTextColor(Color.WHITE)
        sk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        sk.setTypeface(sk.typeface, Typeface.BOLD)
        sk.gravity = Gravity.CENTER
        sk.setPadding(dp(28), dp(12), dp(28), dp(12))
        sk.background = demoSkipBg(this)
        sk.elevation = dp(101).toFloat()
        sk.setOnClickListener { quickSpotOff?.invoke(); onSkip() }
        val sl = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sl.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        sl.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        sl.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        sl.bottomMargin = (if (bottomBar.height > 0) bottomBar.height else lastSystemBottom + dp(80)) + dp(90)
        parent.addView(sk, sl)
        skip = sk
    }
    dim.post {
        dim.holes.clear()
        // holeScale > 1: dira je vetsi (pulzujici UFO se do ni vejde i na 200 %)
        // horni a dolni pole (se zlutym nadpisem) jsou videt po cele nastaveni
        for (f in listOf(findViewById<View>(R.id.urlContainer), etSearchMain)) if (f.isShown) dim.holes.add(rectOf(f))
        for (t in targets) if (t.isShown) dim.holes.add(rectOf(t).apply {
            val gx = width() * (holeScale - 1f) / 2f
            val gy = height() * (holeScale - 1f) / 2f
            inset(-dp(4).toFloat() - gx, -dp(4).toFloat() - gy)
        })
        dim.invalidate()
    }
    quickSpotOff = { parent.removeView(dim); parent.removeView(cap); skip?.let { parent.removeView(it) }; quickSpotOff = null }
}

private fun MainActivity.gestureTarget(): View? {
    val n = gestureButtonN()
    if (n == 0) return null
    return orbView?.takeIf { it.isShown } ?: buttonView(n)
}

private fun MainActivity.bodyText(t: String): TextView {
    val tv = TextView(this)
    tv.text = t
    tv.setTextColor(Color.WHITE)
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    tv.setLineSpacing(0f, 1.15f)
    return tv
}

// Okno s textem a tlacitky pod sebou (prvni = hlavni zelene); kazde tlacitko okno zavre a spusti svou akci.
// Jen pred prvotnim nastavenim (vychozi prohlizec, chces nastaveni?); samotne kroky nastaveni uz okna nemaji.
private fun MainActivity.choiceDialog(title: String, text: String, extra: View? = null, vararg buttons: Pair<String, () -> Unit>) {
    showStyledDialog(
        // s pozadim s UFO staci mensi ztmaveni (jinak by UFO nebyla videt)
        title = title, negativeText = null, dimAmount = if (firstBackdrop != null) 0.45f else 0.8f, strokeColor = "#FFD600".toColorInt(),
        onCancel = { quickSpotOff?.invoke(); hideFirstBackdrop() }
    ) { dismiss ->
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.addView(bodyText(text))
        if (extra != null) {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            body.addView(extra, lp)
        }
        buttons.forEachIndexed { i, (label, action) ->
            val b = styledButton(label, i == 0)
            b.setOnClickListener { dismiss(); action() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(if (i == 0) 18 else 10)
            body.addView(b, lp)
        }
        body
    }
}

// Po "Okay" v uvodni zprave
internal fun MainActivity.afterWelcome() {
    choiceDialog(
        "Výchozí prohlížeč",
        "ASAP si můžete nastavit jako váš výchozí prohlížeč. Odkazy z jiných aplikací se pak otevřou tady. Chcete to?",
        null,
        "Ano" to { requestDefaultBrowser { askQuickSetup() } },
        "Ne" to { askQuickSetup() }
    )
}

// Systemovy dotaz "vychozi prohlizec" (Android 10+), jinak Vychozi aplikace; po navratu pokracuje next
private fun MainActivity.requestDefaultBrowser(next: () -> Unit) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val rm = getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
            var launcher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
            launcher = activityResultRegistry.register("asap_default_browser", ActivityResultContracts.StartActivityForResult()) {
                launcher?.unregister()
                window.decorView.postDelayed({ next() }, 300)
            }
            runCatching { launcher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)) }
                .onFailure { launcher.unregister(); next() }
            return
        }
        if (rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
            AppToast.show(this, "ASAP už je výchozí prohlížeč")
            next(); return
        }
    }
    runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    window.decorView.postDelayed({ next() }, 600)
}

private fun MainActivity.askQuickSetup() {
    choiceDialog(
        "Rychlé nastavení",
        "Chceš si v pár krocích projít hlavní možnosti prohlížeče? Nebo to přeskoč – všechno zůstane ve výchozí podobě a nové věci ti ukážou žluté obrysy.",
        null,
        "Projdu si rychlé nastavení možností prohlížeče" to { quickGo(1) },
        "Přeskočit rychlé nastavení" to { hideFirstBackdrop(); startLiveTutorial() }
    )
}


// ======================= PRVOTNI NASTAVENI (5 kroku, bez oken) =======================
// Pozadi s UFO je videt cele nastaveni. Nadpis kroku je zlute v hornim a dolnim poli, obsah kroku je karta mezi listami
// a dole nad spodni listou je 5 kolecek (jako v Hodnoceni) - klepnutim se da prejit na kterykoli krok.

@SuppressLint("StaticFieldLeak")
private var setupLayer: FrameLayout? = null
@SuppressLint("StaticFieldLeak")
private var setupCard: View? = null
private var setupStageNow = 0

private fun MainActivity.setupBottomOffset(): Int = if (bottomBar.height > 0) bottomBar.height else lastSystemBottom + dp(80)

// Vrstva nastaveni nad ztmavenim (100) a tipem (101); sama dotyky nepohlcuje (projdou na UFO / tlacitka pod ni)
private fun MainActivity.ensureSetupLayer(): FrameLayout {
    setupLayer?.let { return it }
    val l = FrameLayout(this)
    l.elevation = dp(102).toFloat()
    val lp = ConstraintLayout.LayoutParams(0, 0)
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    (root as ConstraintLayout).addView(l, lp)
    setupLayer = l
    return l
}

// 6 kolecek dole: aktualni zlute a vetsi, ostatni polopruhledne bile; vsechna jdou klepnout
private fun MainActivity.setupDots(stage: Int) {
    val l = ensureSetupLayer()
    l.findViewWithTag<View>("setup_dots")?.let { l.removeView(it) }
    val row = LinearLayout(this)
    row.tag = "setup_dots"
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER
    for (k in 1..SETUP_STEPS) {
        val dot = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(if (k == stage) "#FFD600".toColorInt() else "#66FFFFFF".toColorInt())
        dot.background = g
        val sz = if (k == stage) dp(14) else dp(10)
        val hit = FrameLayout(this)
        hit.addView(dot, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
        hit.setOnClickListener { if (k != setupStageNow && quickSetupStage != 21) quickGo(k) }
        row.addView(hit, LinearLayout.LayoutParams(dp(36), dp(36)))
    }
    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    lp.bottomMargin = setupBottomOffset() + dp(6)
    l.addView(row, lp)
}

private fun removeSetupCard() {
    setupCard?.let { (it.parent as? ViewGroup)?.removeView(it) }
    setupCard = null
}

// Karta kroku (tmava, zluty obrys) mezi horni listou a koleckami; top = u horni listy (napr. kdyz jsou dole tlacitka)
private fun MainActivity.showSetupCard(top: Boolean = false, build: (LinearLayout) -> Unit) {
    removeSetupCard()
    val card = LinearLayout(this)
    card.orientation = LinearLayout.VERTICAL
    card.isClickable = true   // klepnuti do karty neprojde na pozadi
    val bg = GradientDrawable()
    bg.setColor("#E61E1E1E".toColorInt())
    bg.cornerRadius = dp(24).toFloat()
    bg.setStroke(dp(2), "#FFD600".toColorInt())
    card.background = bg
    card.setPadding(dp(20), dp(18), dp(20), dp(16))
    build(card)
    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, if (top) Gravity.TOP else Gravity.CENTER)
    lp.marginStart = dp(20); lp.marginEnd = dp(20)
    lp.topMargin = topBar.bottom + dp(14)
    lp.bottomMargin = setupBottomOffset() + dp(56)
    ensureSetupLayer().addView(card, lp)
    card.alpha = 0f
    card.translationY = dp(12).toFloat()
    card.animate().alpha(1f).translationY(0f).setDuration(240).start()
    setupCard = card
}

private fun MainActivity.cardText(card: LinearLayout, text: String) {
    val tv = bodyText(text)
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    card.addView(tv)
}

private fun MainActivity.cardButton(card: LinearLayout, label: String, primary: Boolean, first: Boolean, action: () -> Unit) {
    val b = styledButton(label, primary)
    b.setOnClickListener { action() }
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = dp(if (first) 18 else 10)
    card.addView(b, lp)
}

// Prechod na krok n (1-6): uklidi predchozi krok (ztmaveni, karta, lista) a ukaze novy
private fun MainActivity.quickGo(n: Int) {
    quickSpotOff?.invoke()
    removeSetupCard()
    quickSetupStage = 0
    if (leftBarOpen) closeLeftBarAndHide()
    setupStageNow = n
    if (firstBackdrop == null) showFirstBackdrop()
    setupDots(n)
    // sipky a lupa mohly zustat schovane (preskoceni kroku 2 uprostred animace)
    for (id in listOf(R.id.btnNavBack, R.id.btnNavForward)) findViewById<View>(id).alpha = 1f
    etSearchMain.compoundDrawablesRelative[0]?.alpha = 255
    uncoverFields()                    // pole maji zlute nadpisy
    if (n >= 3) revealAllFirstLook()   // od gest dal uz je vse odkryte (horni lista prijde na radu priste)
    when (n) { 1 -> quickStage1(); 2 -> quickStageBottom(); 3 -> quickStage2(); 4 -> quickStage3(); 5 -> quickStage4(); else -> quickStage5() }
}

// Kroky: 1 domovska stranka, 2 spodni cast, 3 gesta, 4 pridatelna tlacitka, 5 bocni lista, 6 export a import
private const val SETUP_STEPS = 6

private fun MainActivity.endQuickSetup() {
    quickSpotOff?.invoke()
    removeSetupCard()
    setupLayer?.let { (it.parent as? ViewGroup)?.removeView(it) }
    setupLayer = null
    quickSetupStage = 0
    setupStageNow = 0
    if (leftBarOpen) closeLeftBarAndHide()
    clearFieldTitles()
    hideFirstBackdrop()
}

/** Nastaveni -> Tutorialy -> Prvotni nastaveni: projit vsech 5 kroku znovu. */
internal fun MainActivity.startQuickSetupAgain() {
    if (setupStageNow != 0) return
    showFirstBackdrop()
    applyFirstLook()   // znovu od zacatku: tlacitka bez ikon, postupne se odkryji
    quickGo(1)
}

// 1) domovska stranka + co otevrit pri startu
private fun MainActivity.quickStage1() {
    showFieldTitles("DOMOVSKÁ STRÁNKA", "NASTAV SI SÁM")
    showSetupCard { card ->
        cardText(card, "Kam se má prohlížeč vracet tlačítkem Domů a co otevřít po spuštění.")
        val l1 = dialogLabel("Domovská stránka")
        val l1p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l1p.topMargin = dp(14)
        card.addView(l1, l1p)
        val url = EditText(this)
        url.setText(AppSettings.homeUrl.get())
        url.setTextColor(Color.WHITE)
        url.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        url.isSingleLine = true
        url.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT
        val ubg = GradientDrawable()
        ubg.setColor("#2A2A2A".toColorInt()); ubg.cornerRadius = dp(12).toFloat()
        url.background = ubg
        url.setPadding(dp(12), dp(10), dp(12), dp(10))
        val ulp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ulp.topMargin = dp(6); ulp.bottomMargin = dp(14)
        card.addView(url, ulp)
        card.addView(dialogLabel("Při startu prohlížeče"))
        var restore = AppSettings.startupRestoreTabs.get()
        val opts = listOf(true to "Obnovit poslední karty", false to "Jedna nová karta s domovskou stránkou")
        val views = ArrayList<TextView>()
        fun paint() {
            for ((i, v) in views.withIndex()) {
                val on = opts[i].first == restore
                val g = GradientDrawable()
                g.setColor((if (on) "#14382A" else "#2A2A2A").toColorInt()); g.cornerRadius = dp(12).toFloat()
                if (on) g.setStroke(dp(2), "#00B055".toColorInt())
                v.background = g
            }
        }
        for ((value, label) in opts) {
            val v = TextView(this)
            v.text = label
            v.setTextColor(Color.WHITE)
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            v.setPadding(dp(14), dp(12), dp(14), dp(12))
            v.setOnClickListener { restore = value; paint() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6)
            card.addView(v, lp)
            views.add(v)
        }
        paint()
        cardButton(card, "OK, pokračuj", true, true) {
            var u = url.text.toString().trim()
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (u.isNotEmpty() && runCatching { u.toUri().host }.getOrNull()?.contains('.') == true) AppSettings.homeUrl.set(u)
            AppSettings.startupRestoreTabs.set(restore)
            hideKeyboard()
            quickGo(2)   // spodni cast
        }
    }
}

// 2) gesta: vse ztmavene krome UFO (kulicky) -> klepnuti spusti ukazku gest -> nechat tlacitko?
private fun MainActivity.quickStage2() {
    showFieldTitles("GESTA UKÁZKA", "DEMO VIDEO")
    startLiveTutorial()
    if (gestureButtonN() == 0) { quickGo(4); return }
    quickSetupStage = 2
    window.decorView.postDelayed({
        if (quickSetupStage == 2 && setupStageNow == 3) gestureTarget()?.let {
            // vychozi vzhled ma na kulicce UFO
            val tip = if (AppSettings.gestureStyleChanged.get()) "Klikni na kuličku 👆" else "Klikni na UFO 👆"
            quickSpotlight(listOf(it), tip, circle = it === orbView, onSkip = { quickGo(4) }, holeScale = if (it === orbView) 2.1f else 1f)
            if (it === orbView) {
                val orb = it
                val pulse = android.animation.ValueAnimator.ofFloat(1f, 2f)
                pulse.duration = 1100
                pulse.repeatMode = android.animation.ValueAnimator.REVERSE
                pulse.repeatCount = android.animation.ValueAnimator.INFINITE
                pulse.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                pulse.addUpdateListener { a -> val k = a.animatedValue as Float; orb.scaleX = k; orb.scaleY = k }
                pulse.start()
                val off = quickSpotOff
                quickSpotOff = { pulse.cancel(); orb.scaleX = 1f; orb.scaleY = 1f; off?.invoke() }
            }
        }
    }, 350)
}

/** Volano z endDemo: skoncila ukazka gest v prvotnim nastaveni. */
internal fun MainActivity.onQuickSetupDemoEnded() {
    if (quickSetupStage != 21) return
    quickSetupStage = 0
    window.decorView.postDelayed({
        if (setupStageNow != 3) return@postDelayed
        showFieldTitles("GESTA UKÁZKA", "DEMO VIDEO")
        showSetupCard { card ->
            cardText(card, "Chceš tlačítko pro gesta nechat?")
            cardButton(card, "Ano – tlačítko gest zkusím", true, true) { quickGo(4) }
            cardButton(card, "✕ – Kdyžtak si ho pak zapnu", false, false) {
                val n = gestureButtonN()
                if (n in 5..8) AppSettings.buttonEnabled[n - 1].set(false)
                else if (n != 0) AppSettings.buttonTap[n - 1].set(ButtonFunction.NONE.key)
                updateButtonVisuals()
                quickGo(4)
            }
        }
    }, 400)
}

// 3) pridatelna tlacitka: nad bocnimi tlacitky vyjedou 2 + 2 zluta "+" a pak vysvetleni
private fun MainActivity.quickStage3() {
    showFieldTitles("PŘIDEJ TLAČÍTKA", "DEMO VIDEO")
    quickSpotlight(listOfNotNull(buttonView(defaultBarOrigin()), gestureTarget()), "")
    val parent = root as ConstraintLayout
    val ghosts = ArrayList<View>()
    var delay = 500L
    val yellow = "#FFD600".toColorInt()
    for (left in listOf(true, false)) {
        val anchor = findViewById<View>(if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain) ?: continue
        if (!anchor.isShown) continue
        val ar = rectOf(anchor)
        for (k in 1..2) {
            val v = TextView(this)
            v.text = getString(R.string.plus_sign)
            v.setTextColor(yellow)
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            v.setTypeface(v.typeface, Typeface.BOLD)
            v.gravity = Gravity.CENTER
            val bg = GradientDrawable()
            bg.setColor("#1E1E1E".toColorInt())
            bg.cornerRadius = dp(18).toFloat()
            bg.setStroke(dp(2), yellow, dp(8).toFloat(), dp(5).toFloat())
            v.background = bg
            v.elevation = dp(102).toFloat()   // nad ztmavenim
            parent.addView(v, ConstraintLayout.LayoutParams(dp(56), dp(56)))
            v.translationX = ar.centerX() - dp(28)
            v.translationY = ar.top
            v.alpha = 0f; v.scaleX = 0.5f; v.scaleY = 0.5f
            v.animate().setStartDelay(delay).translationY(ar.top - k * (dp(56) + dp(12))).alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).start()
            delay += 350
            ghosts.add(v)
        }
    }
    val off = quickSpotOff
    quickSpotOff = { ghosts.forEach { parent.removeView(it) }; off?.invoke() }
    window.decorView.postDelayed({
        if (setupStageNow != 4) return@postDelayed
        showSetupCard(top = true) { card ->
            cardText(card, "Na každou stranu můžeš přidat až 2 další tlačítka – žluté „+“ nad bočními tlačítky.\n\nKaždé tlačítko umí dvě věci: klepnutí a podržení. Obojí si nastavíš v Nastavení → Tlačítka.")
            cardButton(card, "OK, pokračuj", true, true) { quickGo(5) }
        }
    }, delay + 600)
}

// 4) bocni lista: tlacitko listy 3x problikne + tip -> lista vyjede + pravidlo lista / gesta
private fun MainActivity.quickStage4() {
    showFieldTitles("BOČNÍ LIŠTA", "MŮŽE SE HODIT")
    val barBtn = buttonView(defaultBarOrigin())
    quickSpotlight(listOfNotNull(barBtn), "")
    window.decorView.postDelayed({
        if (setupStageNow != 5) return@postDelayed
        barBtn?.takeIf { it.isShown }?.let { blinkOutline(ensureSetupLayer(), rectOf(it).apply { inset(-dp(4).toFloat(), -dp(4).toFloat()) }) }
        showSetupCard(top = true) { card ->
            cardText(card, "V liště se nachází další užitečné věci.")
            cardButton(card, "OK, pokračuj", true, true) {
                quickSpotOff?.invoke()
                removeSetupCard()
                toggleBarFrom(defaultBarOrigin())
                window.decorView.postDelayed({
                    if (setupStageNow != 5) return@postDelayed
                    showSetupCard(top = true) { c2 ->
                        cardText(c2, "Pozor: tlačítko lišty a tlačítko gest nemůžou být jedno a to samé tlačítko.\n\nLišta vyjíždí jen ze spodního bočního tlačítka vlevo nebo vpravo, gesta si můžeš dát na kterékoli jiné.")
                        cardButton(c2, "OK, pokračuj", true, true) { quickGo(6) }
                    }
                }, 450)
            }
        }
    }, 300)
}

// 5) export a import
private fun MainActivity.quickStage5() {
    showFieldTitles("VŠE SI ULOŽ!", "NENÍ PROBLÉM")
    showSetupCard { card ->
        cardText(card, "Veškeré nastavení si uložíš a můžeš zálohovat – nebo ho jednoduše pošleš na e-mail. Pak si ho stáhneš do telefonu a kdyžtak nahraješ zpět.\n\nNajdeš to v Nastavení → Export a Import.")
        cardButton(card, "Hotovo", true, true) {
            endQuickSetup()
            AppToast.show(this, "Prvotní nastavení hotové")
        }
    }
}

// 2) spodni cast: a) tlacitka Domu, karty, Oblibene, Nastaveni 2x zlute probliknou a ikony se pomalu objevi,
//    b) textove pole a lista zalozek 2x zlute probliknou a pak se pomalu objevi sipky, lupa a skutecna lista karet
private fun MainActivity.setupBlink2(v: View?) {
    if (v != null && v.isShown) blinkOutline(ensureSetupLayer(), rectOf(v).apply { inset(-dp(3).toFloat(), -dp(3).toFloat()) }, times = 2, stepMs = 260)
}

private fun MainActivity.quickStageBottom() {
    showFieldTitles("SPODNÍ ČÁST", "TLAČÍTKA")
    showSetupCard(top = true) { card -> cardText(card, "Spodní část – tlačítka") }
    window.decorView.postDelayed({
        if (setupStageNow != 2) return@postDelayed
        bottomButtonViews().forEach { setupBlink2(it) }
        window.decorView.postDelayed({
            if (setupStageNow != 2) return@postDelayed
            revealFirstLook(bottomButtonViews()) {
                if (setupStageNow != 2) return@revealFirstLook
                showSetupCard(top = true) { card ->
                    cardText(card, "Spodní část – tlačítka\n\nDomů, karty, Oblíbené a Nastavení. Každé tlačítko umí klepnutí i podržení – ukážou ti to žluté obrysy a demo videa.")
                    cardButton(card, "OK, pokračuj", true, true) { quickStageBottomField() }
                }
            }
        }, 1200)
    }, 400)
}

private fun MainActivity.quickStageBottomField() {
    val arrows = listOf(findViewById<View>(R.id.btnNavBack), findViewById<View>(R.id.btnNavForward))
    showFieldTitles("SPODNÍ LIŠTA", "TEXTOVÉ POLE", keep = setOf(R.id.btnNavBack, R.id.btnNavForward))
    // sipky a lupa se objevi az po probliknuti
    arrows.forEach { it.alpha = 0f }
    val lupa = etSearchMain.compoundDrawablesRelative[0]?.mutate()
    lupa?.alpha = 0
    showSetupCard(top = true) { card -> cardText(card, "Spodní lišta – textové pole + lišta záložek") }
    window.decorView.postDelayed({
        if (setupStageNow != 2) return@postDelayed
        setupBlink2(bottomFieldView())
        setupBlink2(stripPlaceholderView(false))
        window.decorView.postDelayed({
            if (setupStageNow != 2) return@postDelayed
            val a = android.animation.ValueAnimator.ofFloat(0f, 1f)
            a.duration = 1400
            a.addUpdateListener { an ->
                val k = an.animatedValue as Float
                arrows.forEach { it.alpha = k }
                lupa?.alpha = (255 * k).toInt()
                etSearchMain.invalidate()
            }
            a.start()
            revealFirstLook(emptyList(), listOf(false)) {
                if (setupStageNow != 2) return@revealFirstLook
                showSetupCard(top = true) { card ->
                    cardText(card, "Spodní lišta – textové pole + lišta záložek\n\nDo pole napíšeš adresu nebo hledaný text, šipkami jdeš o stránku zpět a vpřed. Nad polem jsou tvoje otevřené karty.")
                    cardButton(card, "OK, pokračuj", true, true) {
                        arrows.forEach { it.alpha = 1f }
                        lupa?.alpha = 255
                        quickGo(3)
                    }
                }
            }
        }, 1200)
    }, 400)
}
