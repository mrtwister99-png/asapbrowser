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

private fun MainActivity.quickSpotlight(targets: List<View>, tip: String, circle: Boolean = false, onSkip: (() -> Unit)? = null, holeScale: Float = 1f, fieldHoles: Boolean = true, pass: Boolean = true) {
    quickSpotOff?.invoke()
    val parent = root as ConstraintLayout
    val dim = DemoDim(this)
    dim.passHoles = pass   // na ukazovany prvek jde klepnout
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
        // dira presne podle pole (stejne zaobleni, o kousek vetsi), jinak ztmaveni orezava rohy zluteho obrysu
        if (fieldHoles) for (f in listOf(findViewById<View>(R.id.urlContainer), etSearchMain)) if (f.isShown) {
            val r = rectOf(f).apply { inset(-dp(2).toFloat(), -dp(2).toFloat()) }
            dim.holes.add(r)
            dim.holeRadius[r] = dp(if (f === etSearchMain) 30 else 22).toFloat()
        }
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


// ======================= PRVOTNI NASTAVENI (7 kroku, bez oken) =======================
// Pozadi s UFO je videt cele nastaveni. Nadpis kroku je zlute v hornim / dolnim poli, krok ukazuje tip (zluty obrys)
// a pod nim zelene "OK, pokračuj" (stejne jako demo videa). Nad listou karet je 7 kolecek - klepnutim se da prejit na krok.
// Kroky: 1 domovska stranka, 2 spodni lista, 3 horni lista, 4 gesta, 5 pomocna lista, 6 pridatelna tlacitka, 7 export a import

private const val SETUP_STEPS = 7

@SuppressLint("StaticFieldLeak")
private var setupLayer: FrameLayout? = null
@SuppressLint("StaticFieldLeak")
private var setupCard: View? = null
private var setupStageNow = 0
private var setupGen = 0          // kazdy krok / podkrok ma sve cislo: stare zpozdene akce se samy zahodi
private var setupLoopOff: (() -> Unit)? = null   // bezici smycka kroku (pulz tlacitka gest, cekani na listu)

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

// Kolecka nad spodni listou karet: aktualni zlute a vetsi, ostatni polopruhledne bile; vsechna jdou klepnout
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
        row.addView(hit, LinearLayout.LayoutParams(dp(34), dp(34)))
    }
    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    lp.bottomMargin = setupBottomOffset() + dp(36)   // nad listou karet (30 dp + mezera)
    l.addView(row, lp)
}

private fun removeSetupCard() {
    setupCard?.let { (it.parent as? ViewGroup)?.removeView(it) }
    setupCard = null
}

// Tip kroku (tmava karta se zlutym obrysem) a pod nim tlacitka (zelene OK, pokracuj) - jako v demo videich.
// Dole nad koleckami; top = pod horni listou (krok s polem pro psani, aby ho neprekryla klavesnice)
private fun MainActivity.showSetupCard(top: Boolean = false, build: (LinearLayout) -> Unit) {
    removeSetupCard()
    val outer = LinearLayout(this)
    outer.orientation = LinearLayout.VERTICAL
    outer.isClickable = true   // klepnuti do tipu neprojde na pozadi
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.background = demoCaptionBg(this)
    box.setPadding(dp(18), dp(14), dp(18), dp(14))
    val buttons = LinearLayout(this)
    buttons.orientation = LinearLayout.VERTICAL
    box.tag = buttons
    outer.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    outer.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    build(box)
    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, if (top) Gravity.TOP else Gravity.BOTTOM)
    lp.marginStart = dp(24); lp.marginEnd = dp(24)
    if (top) lp.topMargin = topBar.bottom + dp(14)
    else lp.bottomMargin = setupBottomOffset() + dp(36) + dp(38)   // nad koleckami
    ensureSetupLayer().addView(outer, lp)
    outer.alpha = 0f
    outer.translationY = dp(12).toFloat()
    outer.animate().alpha(1f).translationY(0f).setDuration(240).start()
    setupCard = outer
}

private fun MainActivity.cardText(card: LinearLayout, text: String) {
    val tv = bodyText(text)
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    tv.gravity = Gravity.CENTER
    card.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

// Tlacitko pod tipem (primary = zelene OK, jinak tmave s cervenym obrysem)
@Suppress("UNUSED_PARAMETER")
private fun MainActivity.cardButton(card: LinearLayout, label: String, primary: Boolean, first: Boolean, action: () -> Unit) {
    val col = card.tag as? LinearLayout ?: card
    val b = demoButton(label, primary)
    b.setOnClickListener { action() }
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = dp(if (col.childCount == 0) 12 else 8)
    col.addView(b, lp)
}

private fun MainActivity.tipCard(text: String, okText: String? = null, onOk: (() -> Unit)? = null) {
    showSetupCard { card ->
        cardText(card, text)
        if (okText != null && onOk != null) cardButton(card, okText, true, true, onOk)
    }
}

// Akce za [ms], jen pokud jsme porad ve stejnem kroku / podkroku
private fun MainActivity.setupAfter(ms: Long, block: () -> Unit) {
    val g = setupGen
    window.decorView.postDelayed({ if (g == setupGen && setupStageNow != 0) block() }, ms)
}

// Zlute probliknuti [times] krat (ve vrstve nastaveni, nad ztmavenim)
private fun MainActivity.setupBlink(v: View?, times: Int = 4) {
    if (v != null && v.isShown) blinkOutline(ensureSetupLayer(), rectOf(v).apply { inset(-dp(3).toFloat(), -dp(3).toFloat()) }, times = times, stepMs = 220)
}

// Ztmaveni nastaveni: videt jsou jen [targets] (bez poli s nadpisy, bez tipu nahore); pass = dotyk do diry projde
private fun MainActivity.setupDim(targets: List<View?>, pass: Boolean = false, circle: Boolean = false, holeScale: Float = 1f, fields: Boolean = false) {
    quickSpotlight(targets.filterNotNull(), "", circle = circle, holeScale = holeScale, fieldHoles = fields, pass = pass)
}

// Prechod na krok n (1-7): uklidi predchozi krok (ztmaveni, tip, lista, smycky) a ukaze novy
private fun MainActivity.quickGo(n: Int) {
    setupGen++
    setupLoopOff?.invoke(); setupLoopOff = null
    quickSpotOff?.invoke()
    removeSetupCard()
    quickSetupStage = 0
    if (leftBarOpen) closeLeftBarAndHide()
    setupStageNow = n
    if (firstBackdrop == null) showFirstBackdrop()
    setupDots(n)
    // sipky, lupa, zamek a Sdilet mohly zustat schovane (preskoceni kroku uprostred animace)
    for (id in listOf(R.id.btnNavBack, R.id.btnNavForward, R.id.ivUrlShare)) findViewById<View>(id).alpha = 1f
    (findViewById<View>(R.id.ivUrlLock).parent as View).alpha = 1f
    etSearchMain.compoundDrawablesRelative[0]?.alpha = 255
    uncoverFields()                     // pole maji zlute nadpisy
    if (n >= 4) revealAllFirstLook()    // od gest dal uz je vse odkryte
    when (n) {
        1 -> quickStage1(); 2 -> quickStageBottom(); 3 -> quickStageTop(); 4 -> quickStageGestures()
        5 -> quickStageBar(); 6 -> quickStageExtraButtons(); else -> quickStageBackup()
    }
}

private fun MainActivity.endQuickSetup() {
    setupGen++
    setupLoopOff?.invoke(); setupLoopOff = null
    quickSpotOff?.invoke()
    removeSetupCard()
    setupLayer?.let { (it.parent as? ViewGroup)?.removeView(it) }
    setupLayer = null
    quickSetupStage = 0
    setupStageNow = 0
    if (leftBarOpen) closeLeftBarAndHide()
    clearFieldTitles()
    hideFirstBackdrop()
    startLiveTutorial()   // po nastaveni: nove veci ukazou zlute obrysy (behem nastaveni by se pletly s nadpisy v polich)
}

/** Nastaveni -> Tutorialy -> Prvotni nastaveni: projit vsechny kroky znovu. */
internal fun MainActivity.startQuickSetupAgain() {
    if (setupStageNow != 0) return
    showFirstBackdrop()
    // zlute obrysy ziveho tutorialu by se pletly s nadpisy v polich; po nastaveni se tutorial spusti znovu
    AppSettings.liveTutorial.set("")
    applyLiveTutorial()
    applyFirstLook()   // znovu od zacatku: tlacitka bez ikon, postupne se odkryji
    quickGo(1)
}

// 1) domovska stranka + co otevrit pri startu
private fun MainActivity.quickStage1() {
    showFieldTitles("DOMOVSKÁ STRÁNKA", "NASTAV SI SÁM")
    showSetupCard(top = true) { card ->
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
            quickGo(2)
        }
    }
}

// 2) spodni lista: vse ztmavene krome ni.
//    a) tlacitka Domu, karty, Oblibene, Nastaveni 4x zlute probliknou a ikony se pomalu objevi,
//    b) textove pole a lista zalozek 4x probliknou, objevi se sipky a lupa a dlazdice 2-5 odjedou doprava za okraj
private fun MainActivity.quickStageBottom() {
    showFieldTitles("SPODNÍ LIŠTA", "TLAČÍTKA")
    setupDim(listOf(bottomBar, stripPlaceholderView(false)))
    tipCard("Spodní lišta – tlačítka")
    setupAfter(400) {
        bottomButtonViews().forEach { setupBlink(it) }
        setupAfter(1900) {
            revealFirstLook(bottomButtonViews()) {
                if (setupStageNow != 2) return@revealFirstLook
                tipCard("Spodní lišta – tlačítka\n\nDomů, karty, Oblíbené a Nastavení. Každé tlačítko umí klepnutí i podržení.", "OK, pokračuj") { quickStageBottomField() }
            }
        }
    }
}

private fun MainActivity.quickStageBottomField() {
    setupGen++
    val arrows = listOf(findViewById<View>(R.id.btnNavBack), findViewById<View>(R.id.btnNavForward))
    showFieldTitles("SPODNÍ LIŠTA", "TEXTOVÉ POLE", keep = setOf(R.id.btnNavBack, R.id.btnNavForward))
    arrows.forEach { it.alpha = 0f }   // sipky a lupa se objevi az po probliknuti
    val lupa = etSearchMain.compoundDrawablesRelative[0]?.mutate()
    lupa?.alpha = 0
    tipCard("Spodní lišta – textové pole + lišta záložek")
    setupAfter(400) {
        setupBlink(bottomFieldView())
        setupBlink(stripPlaceholderView(false))
        setupAfter(1900) {
            fadeInViews(arrows)
            val a = android.animation.ValueAnimator.ofFloat(0f, 1f)
            a.duration = 1400
            a.addUpdateListener { an -> lupa?.alpha = (255 * (an.animatedValue as Float)).toInt(); etSearchMain.invalidate() }
            a.start()
            slideStripPlaceholder(false, keepFirst = true) {
                if (setupStageNow != 2) return@slideStripPlaceholder
                tipCard("Spodní lišta – textové pole + lišta záložek\n\nDo pole napíšeš adresu nebo hledaný text, šipkami jdeš o stránku zpět a vpřed. Nad polem jsou tvoje otevřené karty.", "OK, pokračuj") { quickGo(3) }
            }
        }
    }
}

// 3) horni lista: totez nahore. a) AdBlock a VPN 4x probliknou a ukazou se ikony,
//    b) textove pole a lista zalozek 4x probliknou, ukaze se zamek a Sdilet a vsechny dlazdice odjedou doprava
private fun MainActivity.quickStageTop() {
    showFieldTitles("HORNÍ LIŠTA", null)
    setupDim(listOf(topBar, stripPlaceholderView(true)))
    val btns = listOf(findViewById<View>(R.id.btnAdBlock), findViewById<View>(R.id.btnVpn))
    tipCard("Horní lišta – tlačítka")
    setupAfter(400) {
        btns.forEach { setupBlink(it) }
        setupAfter(1900) {
            revealFirstLook(btns) {
                if (setupStageNow != 3) return@revealFirstLook
                tipCard("Horní lišta – tlačítka\n\nAdBlock blokuje reklamy, VPN se připravuje.", "OK, pokračuj") { quickStageTopField() }
            }
        }
    }
}

private fun MainActivity.quickStageTopField() {
    setupGen++
    val icons = listOf(findViewById<View>(R.id.ivUrlLock).parent as View, findViewById(R.id.ivUrlShare))
    showFieldTitles("TEXTOVÉ POLE", null, keep = setOf(R.id.ivUrlLock, R.id.ivUrlShare))
    icons.forEach { it.alpha = 0f }
    tipCard("Horní lišta – textové pole + lišta záložek")
    setupAfter(400) {
        setupBlink(findViewById(R.id.urlContainer))
        setupBlink(stripPlaceholderView(true))
        setupAfter(1900) {
            fadeInViews(icons)
            slideStripPlaceholder(true, keepFirst = false) {
                if (setupStageNow != 3) return@slideStripPlaceholder
                tipCard("Horní lišta – textové pole + lišta záložek\n\nNahoře je adresa stránky, zámek (informace o stránce) a Sdílet. Když máš víc než 5 karet, další jsou v horní liště.", "OK, pokračuj") { quickGo(4) }
            }
        }
    }
}

// 4) gesta: tlacitko gest pulzuje a kazde 3 s z nej vyleti zluty obrys -> klepnuti spusti ukazku gest ve smycce
private fun MainActivity.quickStageGestures() {
    showFieldTitles("DEMO VIDEO", "GESTA")
    val target = gestureTarget()
    if (gestureButtonN() == 0 || target == null) { quickGo(5); return }
    quickSetupStage = 2
    val isOrb = target === orbView
    setupDim(listOf(target), pass = true, circle = isOrb, holeScale = if (isOrb) 2.4f else 1.6f, fields = true)
    tipCard("Gesta – klikni na tlačítko gest!")
    // pulz: zvetsuje se a zmensuje
    val pulse = android.animation.ValueAnimator.ofFloat(1f, 1.45f)
    pulse.duration = 900
    pulse.repeatMode = android.animation.ValueAnimator.REVERSE
    pulse.repeatCount = android.animation.ValueAnimator.INFINITE
    pulse.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
    pulse.addUpdateListener { a -> val k = a.animatedValue as Float; target.scaleX = k; target.scaleY = k }
    pulse.start()
    // kazde 3 s zluty obrys tlacitka, ktery se zvetsi do ztracena
    val layer = ensureSetupLayer()
    val ring = object : Runnable {
        override fun run() {
            if (quickSetupStage != 2 || setupStageNow != 4) return
            val r = rectOf(target)
            val side = minOf(r.width(), r.height()).toInt()
            val v = View(this@quickStageGestures)
            v.background = GradientDrawable().apply {
                shape = if (isOrb) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                if (!isOrb) cornerRadius = dp(16).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), "#FFD600".toColorInt())
            }
            val w = if (isOrb) side else r.width().toInt()
            val h = if (isOrb) side else r.height().toInt()
            layer.addView(v, FrameLayout.LayoutParams(w, h))
            v.translationX = r.centerX() - w / 2f
            v.translationY = r.centerY() - h / 2f
            v.animate().scaleX(2.6f).scaleY(2.6f).alpha(0f).setDuration(1300).withEndAction { layer.removeView(v) }.start()
            layer.postDelayed(this, 3000)
        }
    }
    layer.postDelayed(ring, 600)
    setupLoopOff = { pulse.cancel(); target.scaleX = 1f; target.scaleY = 1f; layer.removeCallbacks(ring) }
}

/** Klepnuti na tlacitko gest v kroku Gesta: pulz pryc, spusti se ukazka gest ve smycce (PrvniTutorial). */
internal fun MainActivity.onSetupGestureTapped() {
    setupLoopOff?.invoke(); setupLoopOff = null
    quickSpotOff?.invoke()
    removeSetupCard()
    setupLayer?.visibility = View.GONE   // kolecka a tip ma ted ukazka sama
}

/** Volano z endDemo: skoncila ukazka gest v prvotnim nastaveni -> dalsi krok. */
internal fun MainActivity.onQuickSetupDemoEnded() {
    if (quickSetupStage != 21) return
    quickSetupStage = 0
    setupLayer?.visibility = View.VISIBLE
    if (setupStageNow == 4) window.decorView.postDelayed({ if (setupStageNow == 4) quickGo(5) }, 250)
}

// 5) pomocna lista: tlacitko listy 4x problikne -> uzivatel klepne -> lista se otevre -> ikony postupne 2x probliknou
private fun MainActivity.quickStageBar() {
    showFieldTitles("POMOCNÁ LIŠTA", "DEMO VIDEO")
    val barBtn = buttonView(defaultBarOrigin())
    setupDim(listOf(barBtn), pass = true, fields = true)
    tipCard("Pomocná lišta – klikni na tlačítko lišty.")
    setupAfter(400) { setupBlink(barBtn) }
    // cekani, az uzivatel listu otevre
    val g = setupGen
    val wait = object : Runnable {
        override fun run() {
            if (g != setupGen || setupStageNow != 5) return
            if (leftBarOpen) { quickSpotOff?.invoke(); window.decorView.postDelayed({ quickStageBarOpen() }, 400); return }
            window.decorView.postDelayed(this, 200)
        }
    }
    window.decorView.postDelayed(wait, 300)
}

private fun MainActivity.quickStageBarOpen() {
    if (setupStageNow != 5) return
    setupGen++
    val bar = sideBarLeft as? ViewGroup
    setupDim(listOf(bar), fields = true)
    tipCard("Tady jsou schované další pomocné věci: Screenshot, PC verze, Vyhledej na stránce a Trezor.")
    // shora dolu: kazda ikona 2x zlute
    val n = minOf(4, bar?.childCount ?: 0)
    for (i in 0 until n) setupAfter(500L + i * 1000L) { setupBlink(bar?.getChildAt(i), times = 2) }
    setupAfter(500L + n * 1000L) {
        tipCard("Tady jsou schované další pomocné věci: Screenshot, PC verze, Vyhledej na stránce a Trezor.", "OK, pokračuj") {
            setupGen++
            tipCard("Co a jak použít, zjistíš po rozkliknutí dané ikonky.", "OK, pokračuj") { quickGo(6) }
        }
    }
}

// 6) pridatelna tlacitka: nad bocnimi tlacitky vyjedou 2 + 2 zluta "+" (blikaji) a tip
private fun MainActivity.quickStageExtraButtons() {
    showFieldTitles("TLAČÍTKA NAVÍC", "DEMO VIDEO")
    setupDim(emptyList(), fields = true)
    val parent = root as ConstraintLayout
    val ghosts = ArrayList<View>()
    var delay = 400L
    val yellow = "#FFD600".toColorInt()
    for (left in listOf(true, false)) {
        val anchor = findViewById<View>(if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain) ?: continue
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
            // napul za okrajem, jako skutecna pridavaci tlacitka
            v.translationX = if (left) -dp(28).toFloat() else (resources.displayMetrics.widthPixels - dp(28)).toFloat()
            v.translationY = ar.top
            v.alpha = 0f
            v.animate().setStartDelay(delay).translationY(ar.top - k * (dp(56) + dp(12))).alpha(1f).setDuration(380).start()
            delay += 300
            ghosts.add(v)
        }
    }
    // blikani
    val blink = android.animation.ValueAnimator.ofFloat(1f, 0.25f)
    blink.duration = 550
    blink.repeatMode = android.animation.ValueAnimator.REVERSE
    blink.repeatCount = android.animation.ValueAnimator.INFINITE
    blink.startDelay = delay + 400
    blink.addUpdateListener { a -> val k = a.animatedValue as Float; ghosts.forEach { it.alpha = k } }
    blink.start()
    setupLoopOff = { blink.cancel(); ghosts.forEach { parent.removeView(it) } }
    setupAfter(delay + 300) {
        tipCard("V Nastavení → Tlačítka si můžeš přidat až 4 tlačítka navíc – s funkcí, jakou chceš ty!", "OK, pokračuj") { quickGo(7) }
    }
}

// 7) export a import
private fun MainActivity.quickStageBackup() {
    showFieldTitles("VŠE SI ULOŽ!", "NENÍ PROBLÉM")
    tipCard("Veškeré nastavení si uložíš a můžeš zálohovat – nebo ho jednoduše pošleš na e-mail. Pak si ho stáhneš do telefonu a kdyžtak nahraješ zpět.\n\nNajdeš to v Nastavení → Export a Import.", "Hotovo") {
        endQuickSetup()
        AppToast.show(this, "Prvotní nastavení hotové")
    }
}
