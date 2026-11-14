package com.example.actimelteambrowser

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

// Rychle nastaveni uz jednou doslo na konec (5/5): kulicky jdou klepnout a v oknech je tlacitko Zpet
private var quickSeenEnd = false

// Krok rychleho nastaveni podle cisla (1-5)
private fun MainActivity.quickGo(n: Int) {
    quickSpotOff?.invoke()
    when (n) { 1 -> quickStage1(); 2 -> quickStage2(); 3 -> quickStage3(); 4 -> quickStage4(); else -> quickStage5() }
}

// 5 kulicek nahore misto "1/5": aktualni zluta, prosle bile, dalsi jen obrys; po dojeti na konec jdou klepnout
private fun MainActivity.stageDots(stage: Int, dismiss: () -> Unit): View {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER
    for (k in 1..5) {
        val dot = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        when {
            k == stage -> g.setColor("#FFD600".toColorInt())
            k < stage || quickSeenEnd -> g.setColor(Color.WHITE)
            else -> { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), "#777777".toColorInt()) }
        }
        dot.background = g
        val sz = if (k == stage) dp(14) else dp(11)
        // vetsi plocha pro klepnuti kolem tecky
        val hit = android.widget.FrameLayout(this)
        hit.addView(dot, android.widget.FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
        if (quickSeenEnd && k != stage) hit.setOnClickListener { dismiss(); quickGo(k) }
        row.addView(hit, LinearLayout.LayoutParams(dp(30), dp(30)))
    }
    return row
}

// Okno s textem a tlacitky pod sebou (prvni = hlavni zelene); kazde tlacitko okno zavre a spusti svou akci.
// Uvodni prvni nastaveni: plocha za oknem hodne ztmavena, zluty obrys; stage 1-5 = kulicky nahore (+ Zpet po dojeti na konec)
private fun MainActivity.choiceDialog(title: String, text: String, extra: View? = null, vararg buttons0: Pair<String, () -> Unit>, stage: Int = 0) {
    val buttons = if (stage > 1 && quickSeenEnd) arrayOf(*buttons0, "← Zpět" to { quickGo(stage - 1) }) else arrayOf(*buttons0)
    showStyledDialog(
        title = title, negativeText = null, dimAmount = 0.8f, strokeColor = "#FFD600".toColorInt(),
        topView = if (stage > 0) ({ d -> stageDots(stage, d) }) else null
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
        "Projdu si rychlé nastavení možností prohlížeče" to { quickStage1() },
        "Přeskočit rychlé nastavení" to { startLiveTutorial() }
    )
}

// 1) domovska stranka + jak otevrit karty pri startu
private fun MainActivity.quickStage1() {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.addView(dialogLabel("Domovská stránka"))
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
    box.addView(url, ulp)
    box.addView(dialogLabel("Při startu prohlížeče"))
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
        box.addView(v, lp)
        views.add(v)
    }
    paint()
    choiceDialog(
        "Domovská stránka a karty",
        "Kam se má prohlížeč vracet tlačítkem Domů a co otevřít po spuštění.",
        box,
        "OK" to {
            var u = url.text.toString().trim()
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (u.isNotEmpty() && runCatching { u.toUri().host }.getOrNull()?.contains('.') == true) AppSettings.homeUrl.set(u)
            AppSettings.startupRestoreTabs.set(restore)
            quickStage2()
        },
        stage = 1
    )
}

// 2) gesta: cela plocha zlute (zivy tutorial), tlacitko gest ceka na klepnuti -> ukazka -> nechat tlacitko?
private fun MainActivity.quickStage2() {
    startLiveTutorial()
    if (gestureButtonN() == 0) { quickStage3(); return }
    quickSetupStage = 2
    // vse ztmavene, videt je jen kulicka gest (na ni jde klepnout) a tip nahore
    window.decorView.postDelayed({
        if (quickSetupStage == 2) gestureTarget()?.let {
            // vychozi vzhled ma na kulicce UFO
            val tip = if (AppSettings.gestureStyleChanged.get()) "Klikni na kuličku 👆" else "Klikni na UFO 👆"
            quickSpotlight(listOf(it), tip, circle = it === orbView, onSkip = {
                quickSetupStage = 0
                quickStage3()
            }, holeScale = if (it === orbView) 2.1f else 1f)
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

/** Volano z endDemo: skoncila ukazka gest v rychlem nastaveni. */
internal fun MainActivity.onQuickSetupDemoEnded() {
    if (quickSetupStage != 21) return
    quickSetupStage = 0
    window.decorView.postDelayed({
        choiceDialog(
            "Tlačítko gest",
            "Chceš tlačítko pro gesta nechat?",
            null,
            "Ano – tlačítko gest zkusím" to { quickStage3() },
            "✕ – Kdyžtak si ho pak zapnu" to {
                val n = gestureButtonN()
                if (n in 5..8) AppSettings.buttonEnabled[n - 1].set(false)
                else if (n != 0) AppSettings.buttonTap[n - 1].set(ButtonFunction.NONE.key)
                updateButtonVisuals()
                quickStage3()
            },
            stage = 2
        )
    }, 400)
}

// 3) pridatelna tlacitka: 2 + 2, kazde umi klepnuti i drzeni
private fun MainActivity.quickStage3() {
    // vse ztmavene, videt je tlacitko listy a gest; nad bocnimi tlacitky vyjedou 2 + 2 ukazkova tlacitka
    quickSpotlight(listOfNotNull(buttonView(defaultBarOrigin()), gestureTarget()), "Na každou stranu přidáš 2 tlačítka")
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
        choiceDialog(
            "Tlačítka",
            "Na každou stranu můžeš přidat až 2 další tlačítka – žluté „+“ nad bočními tlačítky.\n\nKaždé tlačítko umí dvě věci: klepnutí a podržení. Obojí si nastavíš v Nastavení → Tlačítka.",
            null,
            "Dále" to { quickSpotOff?.invoke(); quickStage4() },
            stage = 3
        )
    }, delay + 900)
}

// 4) bocni lista + pravidlo lista / gesta (ukazka listy)
private fun MainActivity.quickStage4() {
    choiceDialog(
        "Boční lišta",
        "Lišta vyjíždí z tlačítka a může být jen na spodních pozicích vlevo nebo vpravo (tlačítko 5 nebo 6).\n\nTlačítko gest může být kdekoli jinde. Lišta a gesta nemůžou být na jednom tlačítku.",
        null,
        "Ukázat lištu" to {
            toggleBarFrom(defaultBarOrigin())
            window.decorView.postDelayed({
                if (leftBarOpen) closeLeftBarAndHide()
                window.decorView.postDelayed({ quickStage5() }, 700)
            }, 3000)
        },
        "Dále" to { quickStage5() },
        stage = 4
    )
}

// 5) export a import
private fun MainActivity.quickStage5() {
    quickSeenEnd = true   // od ted jdou kulicky klepnout a je tu Zpet
    choiceDialog(
        "Záloha nastavení",
        "Veškeré nastavení si uložíš a můžeš zálohovat – nebo ho jednoduše pošleš na e-mail. Pak si ho stáhneš do telefonu a kdyžtak nahraješ zpět.\n\nNajdeš to v Nastavení → Export a Import.",
        null,
        "Hotovo" to { quickSeenEnd = false; AppToast.show(this, "Rychlé nastavení hotové") },
        stage = 5
    )
}
