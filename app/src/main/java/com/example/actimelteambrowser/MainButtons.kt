package com.example.actimelteambrowser

import android.annotation.SuppressLint
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.appcompat.content.res.AppCompatResources
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.toColorInt
import android.view.View
import android.widget.*
import androidx.core.view.*
import com.example.actimelteambrowser.core.settings.AppSettings
import androidx.compose.ui.platform.ComposeView
import com.example.actimelteambrowser.ui.ButtonConfigModal
import android.graphics.Bitmap
import android.view.MotionEvent
import com.example.actimelteambrowser.ui.ExternalTouch
import android.animation.ArgbEvaluator
import androidx.constraintlayout.widget.ConstraintLayout
import android.graphics.Paint
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.util.TypedValue
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup

// Tlačítka 1–12, konfigurační mód tlačítek, boční lišta a vzhled tlačítek (rozšíření MainActivity).
// Pridana boci tlacitka (9-12): vytvori se nad tlacitky 5 a 6 podle nastaveni
internal fun MainActivity.rebuildExtraButtons() {
    blinkAnim?.cancel()
    val parent = root as ViewGroup
    for (v in extraViews.values) parent.removeView(v)
    extraViews.clear()
    for (left in listOf(true, false)) {
        var anchorId = if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain
        for (n in AppSettings.extraButtons(left)) {
            val b = ImageButton(this)
            b.id = View.generateViewId()
            b.setBackgroundResource(R.drawable.side_button_bg)
            b.scaleType = ImageView.ScaleType.CENTER_INSIDE
            b.setPadding(dp(8), dp(8), dp(8), dp(8))
            b.elevation = dp(8).toFloat()
            b.translationY = lastImeShift
            val lp = ConstraintLayout.LayoutParams(dp(56), dp(56))
            lp.bottomToTop = anchorId
            if (left) lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            else lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomMargin = dp(12)
            parent.addView(b, lp)
            extraViews[n] = b
            anchorId = b.id
        }
    }
}

// Tlacitko s funkci Gesta (kulicka, vychozi je 6): prst polozeny na kulicku hned ukaze plochu a dotyk pokracuje
// na ploše (jednim tahem bez zvednuti prstu). Funkce se kontroluje pri kazdem dotyku, takze jde menit za behu.
@SuppressLint("ClickableViewAccessibility")   // kulicka Gesta predava dotyk dal, klik se tu nevola
internal fun MainActivity.wireGestureTouch(v: View, n: Int) {
    var swallow = false   // prvni dotyk spustil ukazku: zbytek tohoto dotyku se zahodi
    v.setOnTouchListener { view, ev ->
        if (buttonFunction(n, false) != ButtonFunction.GESTURES) return@setOnTouchListener false
        if (swallow) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallow = false
            return@setOnTouchListener true
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && liveTutorialDone(n)) { swallow = true; return@setOnTouchListener true }
        val wl = IntArray(2)
        window.decorView.getLocationOnScreen(wl)
        val x = ev.rawX - wl[0]
        val y = ev.rawY - wl[1]
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                val down = ExternalTouch(x, y, 0, ++extSeq)
                externalDown = down
                if (!gestureView.isVisible) showGestures()
                externalTouch.value = down
            }
            MotionEvent.ACTION_MOVE -> externalTouch.value = ExternalTouch(x, y, 1, ++extSeq)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                externalTouch.value = ExternalTouch(x, y, 2, ++extSeq)
            }
        }
        true
    }
}

internal fun MainActivity.wireButtons() {
    if (configMode) { applyConfigWiring(); return }
    val numbers = (1..8).toList() + extraViews.keys.sorted()
    for (i in numbers) {
        val v = buttonView(i) ?: continue
        if (i != 7 && i != 8) wireGestureTouch(v, i) else v.setOnTouchListener(null)
        if (isSideButton(i)) {
            wireSideButton(i, v)
        } else {
            v.setOnClickListener { runTap(i) }
            v.setOnLongClickListener {
                buzz(it)
                holdIconFlip(i)
                runLongPress(i)
                true
            }
        }
    }
}

internal fun MainActivity.enterConfigMode() {
    if (configMode) return
    if (gestureConfigMode) exitGestureConfig()
    configMode = true
    setChromeHidden(false)
    hideKeyboard()
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    if (gestureView.isVisible) hideGestures()
    if (leftBarOpen) closeLeftBarAndHide()
    hideTabs()
    etSearchMain.isEnabled = false
    etTopUrl.isEnabled = false
    val parent = root as ViewGroup

    // pruhledna vrstva; pohlti klepnuti do webu (tlacitka maji vyssi elevation)
    val scrim = View(this)
    scrim.setBackgroundColor(Color.TRANSPARENT)   // bez ztmaveni: zmeny tlacitek jsou videt hned
    scrim.isClickable = true
    scrim.elevation = dp(7).toFloat()
    val slp = ConstraintLayout.LayoutParams(0, 0)
    slp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    slp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    slp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    slp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    parent.addView(scrim, slp)
    configScrim = scrim

    // uprostred: zluty text s plnym zlutooranzovym obrysem
    val saveYellow = "#FFD600".toColorInt()
    val saveOrange = "#FFA000".toColorInt()
    val save = TextView(this)
    save.text = getString(R.string.config_save_close)
    save.setTextColor(saveYellow)
    save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    save.setTypeface(save.typeface, Typeface.BOLD)
    save.gravity = Gravity.CENTER
    save.setPadding(dp(26), dp(18), dp(26), dp(18))
    val sbg = GradientDrawable()
    sbg.setColor("#E61E1E1E".toColorInt())
    sbg.cornerRadius = dp(18).toFloat()
    sbg.setStroke(dp(2), saveOrange)   // plny obrys
    save.background = sbg
    save.elevation = dp(9).toFloat()
    save.setOnClickListener { exitConfigMode() }
    val vlp = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    vlp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    vlp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    vlp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    vlp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    parent.addView(save, vlp)
    configSave = save

    // okno s nastavenim vybraneho tlacitka
    val host = ComposeView(this)
    host.setContent {
        val n = configButton.value
        if (n != null) {
            // upravovane tlacitko pomalu pulzuje modre, dokud je okno otevrene
            DisposableEffect(n) {
                startEditPulse(n)
                onDispose { stopEditPulse() }
            }
            ButtonConfigModal(
                n = n,
                onChanged = { refreshConfigUi() },
                onRemoved = { configButton.value = null; refreshConfigUi() },
                onDismiss = { configButton.value = null },
                // po ✓: okno se zavre a upravene tlacitko 10 s pomalu bliká modře/žlutě
                onSaved = { configButton.value = null; blinkSaved(n) }
            )
        }
    }
    parent.addView(host, ConstraintLayout.LayoutParams(1, 1))
    configHost = host

    refreshConfigUi()
}

internal fun MainActivity.exitConfigMode() {
    if (!configMode) return
    configMode = false
    configButton.value = null
    stopEditPulse()
    val parent = root as ViewGroup
    for (v in listOf(configScrim, configSave, configHost)) if (v != null) parent.removeView(v)
    configScrim = null
    configSave = null
    configHost = null
    for (v in configPlus) parent.removeView(v)
    configPlus.clear()
    etSearchMain.isEnabled = true
    etTopUrl.isEnabled = true
    rebuildExtraButtons()
    wireButtons()
    updateButtonVisuals()
    AppToast.show(this, "Nastavení tlačítek uloženo")
}

// Po zmene v modalu: znovu postavit tlacitka (i pridana) a "+" nad nimi
internal fun MainActivity.refreshConfigUi() {
    rebuildExtraButtons()
    wireButtons()
    updateButtonVisuals()
    rebuildConfigPlus()
}

// V konfiguracnim modu klepnuti i drzeni na tlacitko otevre jeho nastaveni
internal fun MainActivity.applyConfigWiring() {
    val numbers = (1..8).toList() + extraViews.keys.sorted()
    for (n in numbers) {
        val v = buttonView(n) ?: continue
        v.setOnTouchListener(null)
        v.setOnClickListener { configButton.value = n }
        v.setOnLongClickListener { configButton.value = n; true }
    }
}

// "+" nad bocnimi tlacitky (vlevo nad 5, vpravo nad 6): pridani dalsiho bocniho tlacitka (max 2 + 2)
internal fun MainActivity.rebuildConfigPlus() {
    val parent = root as ViewGroup
    for (v in configPlus) parent.removeView(v)
    configPlus.clear()
    val l = AppSettings.extraButtons(true)
    val r = AppSettings.extraButtons(false)
    if (l.size + r.size >= 4) return
    val green = "#00B055".toColorInt()
    for (left in listOf(true, false)) {
        val list = if (left) l else r
        if (list.size >= 2) continue
        val anchor = if (list.isEmpty()) (if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain)
        else extraViews[list.last()]?.id ?: continue
        val plus = TextView(this)
        plus.text = getString(R.string.plus_sign)
        plus.setTextColor(green)
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        plus.setTypeface(plus.typeface, Typeface.BOLD)
        plus.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setColor("#E61E1E1E".toColorInt())
        bg.cornerRadius = dp(18).toFloat()
        bg.setStroke(dp(2), green, dp(8).toFloat(), dp(5).toFloat())
        plus.background = bg
        plus.elevation = dp(8).toFloat()
        plus.translationY = lastImeShift
        plus.setOnClickListener { addConfigButton(left) }
        val lp = ConstraintLayout.LayoutParams(dp(56), dp(56))
        lp.bottomToTop = anchor
        if (left) lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID else lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        lp.bottomMargin = dp(12)
        parent.addView(plus, lp)
        configPlus.add(plus)
    }
}

// Nove bocni tlacitko dostane nejmensi volne cislo od 9 a rovnou se otevre jeho nastaveni
internal fun MainActivity.addConfigButton(left: Boolean) {
    val used = AppSettings.extraButtons(true) + AppSettings.extraButtons(false)
    val next = (9..12).firstOrNull { it !in used } ?: return
    val list = AppSettings.extraButtons(left).toMutableList()
    if (list.size >= 2) return
    list.add(next)
    AppSettings.setExtraButtons(left, list)
    refreshConfigUi()
    configButton.value = next
}

// Vibrace pri drzeni tlacitka (jde vypnout v Konfiguraci)
@Suppress("UnusedReceiverParameter")
internal fun MainActivity.buzz(v: View) {
    if (AppSettings.vibrationOn.get()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

// Klepnuti: tlacitka 1-4 maji sve animace (1 problikne 2x zelene, 2 se otoci o 360 stupnu,
// 3 vyplni zlutou hvezdu s jiskrami, 4 prohodi logo za kolecko) a az potom provedou funkci
internal fun MainActivity.runTap(i: Int) {
    if (buttonFunction(i, false) == ButtonFunction.GESTURES) return   // tlacitko Gest: jen gesto
    if (liveTutorialDone(i)) return   // prvni klepnuti na AdBlock / VPN = ukazka misto akce
    val fn = buttonFunction(i, false)
    if (fn == ButtonDefaults.tap(i) && isDefaultVisual(i) && AppSettings.animationsOn.get()) {
        when (i) {
            1 -> { blinkHome(); performFunction(fn); return }
            2 -> { spinTabs { performFunction(fn) }; return }
            3 -> { spinFavorites { performFunction(fn) }; return }
            4 -> { playSettingsOutro { performFunction(fn) }; return }
        }
    }
    performFunction(fn)
}

// Drzeni: puvodni animace se prehraje jen u vychozi funkce toho tlacitka
internal fun MainActivity.runLongPress(i: Int) {
    if (buttonFunction(i, false) == ButtonFunction.GESTURES) return   // tlacitko Gest: jen gesto, zadne klepnuti / drzeni
    if (liveTutorialDone(i)) return
    val fn = buttonFunction(i, true)
    if (fn != ButtonFunction.NONE && fn == ButtonDefaults.long(i) && isDefaultVisual(i) && AppSettings.animationsOn.get()) {
        when (i) {
            1 -> { animateHome { performFunction(fn) }; return }
            2 -> { animateTabsButton { performFunction(fn) }; return }
            3 -> { animateStar { performFunction(fn) }; return }
            4 -> { spinSettingsLogo { performFunction(fn) }; return }
        }
    }
    performFunction(fn)
}

// Bocni tlacitko je trvale zasunute za okraj (50 nebo 70 %); klepnuti i drzeni spusti funkci rovnou
internal fun MainActivity.wireSideButton(n: Int, v: View) {
    // spusti funkci tlacitka; u 5/6 si zapamatuje, ze lista ma vyjet z teto strany
    fun press(long: Boolean) {
        if (buttonFunction(n, false) == ButtonFunction.GESTURES) return   // jen gesto
        liveTutorialDone(n)
        val fn = buttonFunction(n, long)
        val run = {
            if (n == 5 || n == 6) pressedOrigin = n
            performFunction(fn)
            pressedOrigin = 0
        }
        // Gesta, Bocni lista a prazdne tlacitko bez animace; ostatni: vyjede na 100 %, zelene blikne a pak funkce
        if (fn == ButtonFunction.GESTURES || fn == ButtonFunction.LEFT_BAR || fn == ButtonFunction.NONE) run()
        else confirmSideButton(n, v, run)
    }
    v.setOnClickListener { press(false) }
    v.setOnLongClickListener {
        buzz(it)
        holdIconFlip(n)
        press(true)
        true
    }
}

@SuppressLint("StaticFieldLeak")
private val sideConfirming = HashSet<Int>()

// Potvrzeni stisku zasunuteho bocniho tlacitka: vyjede na 100 %, 2x zelene blikne, spusti se funkce a tlacitko se zase zasune.
// Behem animace dalsi stisky tlacitka nic nedelaji. Vypnute animace (Konfigurace) = funkce hned.
internal fun MainActivity.confirmSideButton(n: Int, v: View, onDone: () -> Unit) {
    if (!AppSettings.animationsOn.get()) { onDone(); return }
    if (!sideConfirming.add(n)) return
    val greenBg = GradientDrawable()
    greenBg.setColor(accentState.value)
    greenBg.cornerRadius = dp(16).toFloat()
    greenBg.setStroke(dp(1), Color.WHITE)
    fun retract() {
        // jako applySideMode: schovat za okraj, krome otevrene listy tohoto tlacitka a konfiguracnich modu
        val target = if (!configMode && !gestureConfigMode && !(n == barOrigin && leftBarOpen)) hiddenX(n) else 0f
        v.animate().translationX(target).setDuration(250).withEndAction { sideConfirming.remove(n) }.start()
    }
    v.animate().translationX(0f).setDuration(220).withEndAction {
        var step = 0
        val blink = object : Runnable {
            override fun run() {
                if (step >= 4) {
                    v.setBackgroundResource(R.drawable.side_button_bg)
                    onDone()
                    v.postDelayed({ retract() }, 150)
                    return
                }
                if (step % 2 == 0) v.background = greenBg else v.setBackgroundResource(R.drawable.side_button_bg)
                step++
                v.postDelayed(this, 110)
            }
        }
        blink.run()
    }.start()
}

// Boční tlačítka: trvale zasunutá (50 / 70 %); celá jsou vidět jen v konfiguračních módech a u otevřené lišty
internal fun MainActivity.applySideMode(n: Int, v: View) {
    v.translationX = if (!configMode && !gestureConfigMode && !(n == barOrigin && leftBarOpen)) hiddenX(n) else 0f
    if (n == barOrigin) setLeftBar(leftBarOpen, false)
}

// Lista za levym bocnim tlacitkem 5: klepnuti na tlacitko ho zcela vysune a potom lista vyjede nahoru.
// Uvnitr jsou tlacitka 1 (Domu), 2 (Karty), 3 (Oblibene); dalsi pridame pozdeji.
internal fun MainActivity.setupSideBar() {
    if (sideBarLeft != null) return
    val d = resources.displayMetrics.density
    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.VERTICAL
    bar.gravity = Gravity.CENTER_HORIZONTAL
    bar.setPadding(0, dp(12), 0, 0)
    bar.elevation = dp(6).toFloat()   // nad webem, pod tlacitky (8)
    val bg = GradientDrawable()
    bg.setColor("#1E1E1E".toColorInt())
    bg.cornerRadius = 24f * d
    bg.setStroke((1f * d).toInt().coerceAtLeast(1), Color.WHITE)
    bar.background = bg

    // jedno kulate tlacitko v liste s pevnou akci (klepnuti)
    fun barButton(content: View, action: () -> Unit): FrameLayout {
        val b = FrameLayout(this)
        b.setBackgroundResource(R.drawable.btn_circle_dark)
        b.isClickable = true
        b.isFocusable = true
        b.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        val liveId = 201 + bar.childCount   // cislo v zive napovede (201-203)
        b.setOnClickListener { if (!liveTutorialDone(liveId)) action() }   // prvni klepnuti = ukazka misto akce
        val blp = LinearLayout.LayoutParams(dp(48), dp(48))
        blp.bottomMargin = dp(8)
        bar.addView(b, blp)
        return b
    }
    // 1) screenshot obrazovky (sdilet odkaz a QR jsou ted u adresniho radku)
    fun fnIcon(fn: ButtonFunction) = ImageView(this).apply { setImageBitmap(glyphBitmap(fn, dp(28))) }
    fun resIcon(res: Int) = ImageView(this).apply {
        val bmp = createBitmap(dp(28), dp(28))
        AppCompatResources.getDrawable(this@setupSideBar, res)?.let {
            it.setBounds(0, 0, dp(28), dp(28))
            it.draw(Canvas(bmp))
        }
        setImageBitmap(bmp)
    }
    // fotacek: klepnuti ho na chvili zezeleni a zepta se, jaky screenshot
    var camBtn: FrameLayout? = null
    camBtn = barButton(resIcon(R.drawable.ic_screenshot)) {
        camBtn?.let { blinkGreen(it) }
        performFunction(ButtonFunction.SCREENSHOT)
    }
    // 4) verze pro pocitac (zap/vyp; zelene = zapnuto), 5) najit na strance (text)
    leftDeskBtn = barButton(fnIcon(ButtonFunction.DESKTOP_MODE)) {
        toggleDesktopMode()
        updateDeskBtn()
    }
    barButton(fnIcon(ButtonFunction.FIND_IN_PAGE)) {
        closeLeftBarAndHide()
        performFunction(ButtonFunction.FIND_IN_PAGE)
    }
    // 6) trezor hesel (posledni v liste): otevre Nastaveni rovnou na trezoru
    barButton(resIcon(R.drawable.ic_vault)) {
        closeLeftBarAndHide()
        openSettingsAt(SettingsActivity.START_VAULT)
    }
    updateDeskBtn()

    val lp = ConstraintLayout.LayoutParams((68 * d).toInt(), 0)
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.topToBottom = R.id.topBar
    lp.topMargin = (8 * d).toInt()
    lp.bottomToTop = R.id.bottomBar
    lp.bottomMargin = (18 * d).toInt()
    (root as ViewGroup).addView(bar, 0, lp)
    sideBarLeft = bar
    bar.translationX = -6f * d
    bar.visibility = View.INVISIBLE
}

// Zelene pozadi tlacitka, kdyz ma aktivni karta zapnutou verzi pro pocitac
internal fun MainActivity.updateDeskBtn() {
    val b = leftDeskBtn ?: return
    if (tabs.active()?.desktop == true) {
        val g = GradientDrawable()
        g.setColor("#00B055".toColorInt())
        g.cornerRadius = dp(18).toFloat()
        g.setStroke(dp(1), Color.WHITE)
        b.background = g
    } else {
        b.setBackgroundResource(R.drawable.btn_circle_dark)
    }
}

// Tlacitko, ze ktereho se lista otevira: stisknute 5/6, jinak to, ktere ma funkci "Bocni lista"
internal fun MainActivity.defaultBarOrigin(): Int {
    for (i in listOf(5, 6)) {
        if (AppSettings.isEnabled(i) && (buttonFunction(i, false) == ButtonFunction.LEFT_BAR || buttonFunction(i, true) == ButtonFunction.LEFT_BAR)) return i
    }
    return 5
}

// Lista se otevira na strane tlacitka: 5 = vlevo, 6 = vpravo
internal fun MainActivity.applyBarSide() {
    val bar = sideBarLeft ?: return
    val right = barOrigin == 6
    val lp = bar.layoutParams as ConstraintLayout.LayoutParams
    lp.startToStart = if (right) ConstraintLayout.LayoutParams.UNSET else ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = if (right) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
    bar.layoutParams = lp
    bar.translationX = (if (right) 6f else -6f) * resources.displayMetrics.density
}

// Klepnuti na tlacitko: zavrena lista -> tlacitko se zcela vysune a lista vyjede nahoru; otevrena -> zavre se
internal fun MainActivity.toggleBarFrom(origin: Int) {
    if (leftBarOpen) {
        closeLeftBarAndHide()
        return
    }
    barOrigin = origin
    applyBarSide()
    val btn = barBtn()
    if (AppSettings.isEnabled(origin) && kotlin.math.abs(btn.translationX) > 1f) {
        btn.animate().translationX(0f).setDuration(250).withEndAction { setLeftBar(true, true) }.start()
    } else {
        setLeftBar(true, true)
    }
}

// Lista zajede dolu do tlacitka a pak se tlacitko zasune do vychozi polohy (podle nastaveni vysouvani)
internal fun MainActivity.closeLeftBarAndHide() {
    val btn = barBtn()
    val origin = barOrigin
    setLeftBar(false, true) {
        btn.animate().translationX(hiddenX(origin)).setDuration(250).start()
    }
}

// Dokud je lista otevrena, pridana tlacitka nad tlacitkem na teze strane zajedou za okraj (100 % schovana),
// po zavreni se vrati do vychozi (zasunute) polohy. Tlacitko s kulickou gest se navic skryje/ukaze.
internal fun MainActivity.hideExtrasForBar(open: Boolean) {
    val orbN = gestureButtonN()
    for ((n, v) in extraViews) {
        val sameSide = isLeftSide(n) == (barOrigin != 6)
        val hide = open && sameSide
        v.animate().cancel()
        v.alpha = if (n == orbN) v.alpha else 1f
        if (hide) {
            val out = (if (isLeftSide(n)) -1f else 1f) * dp(56)   // cela sirka tlacitka = uplne za okrajem
            v.animate().translationX(out).setDuration(300).withEndAction {
                if (n == orbN && leftBarOpen) v.visibility = View.INVISIBLE
            }.start()
        } else {
            v.visibility = View.VISIBLE
            val back = if (configMode || gestureConfigMode) 0f else hiddenX(n)
            v.animate().translationX(back).setDuration(300).start()
        }
    }
}

// Otevre/zavre levou listu: odkryva se odspodu nahoru (od tlacitka 5), tlacitka uvnitr se nemacknou
internal fun MainActivity.setLeftBar(open: Boolean, animate: Boolean, onDone: (() -> Unit)? = null) {
    val bar = sideBarLeft ?: return
    leftBarAnim?.cancel()
    leftBarOpen = open
    hideExtrasForBar(open)
    if (!animate) {
        bar.clipBounds = null
        bar.visibility = if (open) View.VISIBLE else View.INVISIBLE
        onDone?.invoke()
        return
    }
    fun reveal(p: Float) {
        val h = bar.height
        bar.clipBounds = Rect(0, (h * (1f - p)).toInt(), bar.width, h)
    }
    bar.visibility = View.VISIBLE
    reveal(if (open) 0f else 1f)
    val a = ValueAnimator.ofFloat(if (open) 0f else 1f, if (open) 1f else 0f)
    a.duration = 570   // otevreni i zavreni listy (drive 320 ms)
    a.interpolator = DecelerateInterpolator()
    a.addUpdateListener { reveal(it.animatedValue as Float) }
    a.addListener(object : AnimatorListenerAdapter() {
        private var cancelled = false
        override fun onAnimationCancel(animation: Animator) { cancelled = true }
        override fun onAnimationEnd(animation: Animator) {
            bar.clipBounds = null
            if (!open) bar.visibility = View.INVISIBLE
            if (!cancelled) onDone?.invoke()
        }
    })
    leftBarAnim = a
    a.start()
}

// Ikona tlacitka podle jeho funkce (klepnuti). Vychozi funkce = puvodni ikona.
// Pojistka: nejaka tlacitko musi umet otevrit Nastaveni, jinak by se uz nedaly zmenit funkce.
// Pokud ne (napr. po importu), vrati se tlacitku 4 vychozi funkce.
internal fun MainActivity.ensureSettingsReachable() {
    for (i in 1..12) {
        if (!AppSettings.isEnabled(i)) continue   // skryte tlacitko se nepocita
        for (long in listOf(false, true)) {
            val fn = buttonFunction(i, long)
            if (fn == ButtonFunction.OPEN_SETTINGS) return
        }
    }
    AppSettings.buttonTap[3].set(ButtonDefaults.tap(4).key)
    AppToast.show(this, "Nastavení vráceno na tlačítko 4", true)
}

@SuppressLint("StaticFieldLeak")
private val liveOutlines = HashMap<Int, Pair<View, GradientDrawable>>()
@SuppressLint("StaticFieldLeak")
private val tutorialPlus = ArrayList<View>()
private var liveAnim: ValueAnimator? = null
@SuppressLint("StaticFieldLeak")
private val liveIcons = ArrayList<View>()   // ikony Sdilet / zamek, ktere ted blikaji zlute

// Zlute vybarveni ikony Sdilet (103) a zamku (104); vypnuti vraci puvodni vzhled
private fun MainActivity.paintTutorialIcon(n: Int, on: Boolean) {
    val iv = findViewById<ImageView>(if (n == 103) R.id.ivUrlShare else R.id.ivUrlLock)
    if (on) {
        iv.setColorFilter("#FFD600".toColorInt())
    } else if (n == 103) {
        iv.clearColorFilter()
        iv.alpha = 0.85f
    } else {
        iv.alpha = 1f
        updateLockIcon(if (isWebViewReady()) webView.url else null)   // vrati barvu zamku podle stranky (pri startu WebView jeste neni)
    }
}

// Cisla v seznamu zive napovedy: 1-12 tlacitka, 101 horni pole, 102 dolni pole, 103 ikona Sdilet a 104 ikona zamku (zluta ikona misto obrysu), 201-203 tlacitka v liste
@Suppress("UnusedReceiverParameter")
internal fun MainActivity.liveIds(): List<Int> =
    AppSettings.liveTutorial.get().split(",").mapNotNull { it.trim().toIntOrNull() }

// Zivy tutorial: prvky, na ktere uzivatel jeste neklepl, maji blikajici zluty obrys (9-12 carkovany).
// Obrys je v ViewOverlay, aby se nepral s plnenim tlacitek pri stahovani (to pouziva foreground).
internal fun MainActivity.applyLiveTutorial() {
    liveAnim?.cancel()
    liveAnim = null
    for ((v, d) in liveOutlines.values) v.overlay.remove(d)
    liveOutlines.clear()
    val left = liveIds()
    liveIcons.clear()
    for (n in listOf(103, 104)) {
        paintTutorialIcon(n, n in left)
        if (n in left) liveIcons.add(findViewById(if (n == 103) R.id.ivUrlShare else R.id.ivUrlLock))
    }
    rebuildTutorialPlus(left.any { it in 9..12 })
    if (left.isEmpty()) return
    val yellow = "#FFD600".toColorInt()
    val orbN = gestureButtonN()
    for (n in left) {
        // cil obrysu a polomer rohu (v px; 0 = kruh)
        val found: View? = when (n) {
            in 1..12 -> buttonView(n)
            101 -> findViewById(R.id.urlContainer)
            102 -> etSearchMain
            in 201..203 -> (sideBarLeft as? ViewGroup)?.getChildAt(n - 201)
            else -> null
        }
        val v: View = found ?: continue
        val radius = when (n) { in 1..4 -> dp(18); in 5..12 -> dp(16); 101 -> dp(18); 102 -> dp(22); else -> dp(18) }
        val d = GradientDrawable()
        d.setColor(Color.TRANSPARENT)
        if (n in 9..12) d.setStroke(dp(2), yellow, dp(8).toFloat(), dp(5).toFloat()) else d.setStroke(dp(2), yellow)
        // tlacitko s funkci Gesta je nevidite, kresli se misto nej kulicka: obrys jde na kulicku (kruh podle tlacitka)
        val orbTarget: View? = if (n == orbN) v else null
        val target: View = if (orbTarget != null) (orbView ?: continue) else v
        val circle = orbTarget != null
        liveOutlines[n] = Pair(target, d)
        val place = {
            if (orbTarget != null) {
                val side = orbTarget.width
                val l = (target.width - side) / 2
                val t = (target.height - side) / 2
                d.setBounds(l, t, l + side, t + side)
            } else d.setBounds(0, 0, target.width, target.height)
            d.cornerRadius = if (circle) minOf(d.bounds.width(), d.bounds.height()) / 2f else radius.toFloat()
            target.overlay.add(d)
        }
        if (target.width > 0) place() else target.post { if (liveOutlines[n]?.second === d) place() }
    }
    val a = ValueAnimator.ofFloat(0.2f, 1f)
    a.duration = 650
    a.repeatMode = ValueAnimator.REVERSE
    a.repeatCount = ValueAnimator.INFINITE
    a.addUpdateListener { anim ->
        if (!root.isAttachedToWindow) { anim.cancel(); return@addUpdateListener }
        val k = anim.animatedValue as Float
        val al = (k * 255).toInt()
        for ((_, d) in liveOutlines.values) d.alpha = al
        for (p in tutorialPlus) p.alpha = k
        for (ic in liveIcons) ic.alpha = 0.3f + 0.7f * k
    }
    liveAnim = a
    a.start()
}

// "+" (zluty carkovany) na kazde strane nad tlacitky 5 / 6: pri prvnim klepnuti se otevre modal "Pridatelne tlacitko"
private fun MainActivity.rebuildTutorialPlus(show: Boolean) {
    val parent = root as ViewGroup
    for (v in tutorialPlus) parent.removeView(v)
    tutorialPlus.clear()
    if (!show || configMode) return
    val l = AppSettings.extraButtons(true)
    val r = AppSettings.extraButtons(false)
    if (l.size + r.size >= 4) return
    val yellow = "#FFD600".toColorInt()
    for (left in listOf(true, false)) {
        val list = if (left) l else r
        if (list.size >= 2) continue
        val anchor = if (list.isEmpty()) (if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain)
        else extraViews[list.last()]?.id ?: continue
        val plus = TextView(this)
        plus.text = getString(R.string.plus_sign)
        plus.setTextColor(yellow)
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        plus.setTypeface(plus.typeface, Typeface.BOLD)
        plus.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setColor("#E61E1E1E".toColorInt())
        bg.cornerRadius = dp(16).toFloat()
        bg.setStroke(dp(2), yellow, dp(8).toFloat(), dp(5).toFloat())
        plus.background = bg
        plus.elevation = dp(8).toFloat()
        plus.translationY = lastImeShift
        plus.setOnClickListener { showTutorialPlusDialog() }
        val lp = ConstraintLayout.LayoutParams(dp(56), dp(56))
        lp.bottomToTop = anchor
        if (left) lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID else lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        lp.bottomMargin = dp(12)
        parent.addView(plus, lp)
        tutorialPlus.add(plus)
    }
}

// Klepnuti na tutorialove "+": modal s vysvetlenim. "Pridat tlacitko nyni" prepne do nastaveni tlacitek, "Preskocit" jen ukonci tutorial "+"
internal fun MainActivity.showTutorialPlusDialog() {
    fun finishPlusTutorial() {
        AppSettings.liveTutorial.set(liveIds().filter { it !in 9..12 }.joinToString(","))
        updateButtonVisuals()   // znovu vykresli obrysy a odstrani "+"
    }
    showStyledDialog(title = "Přidatelné tlačítko", negativeText = null) { dismiss ->
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val text = TextView(this)
        text.text = "Tlačítkem + můžeš přidat další tlačítko na kraj displeje."
        text.setTextColor(Color.WHITE)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        body.addView(text)
        val add = styledButton("Přidat tlačítko nyní", true)
        add.setOnClickListener { dismiss(); finishPlusTutorial(); enterConfigMode() }
        // text tlacitka blika: 1 s zlute, 1 s vychozi barva, dokola (dokud je okno otevrene)
        val addDefault = add.currentTextColor
        val addYellow = "#FFD600".toColorInt()
        val blink = object : Runnable {
            var on = true
            override fun run() {
                if (!add.isAttachedToWindow) return
                add.setTextColor(if (on) addYellow else addDefault)
                on = !on
                add.postDelayed(this, 1000)
            }
        }
        add.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { v.removeCallbacks(blink); blink.on = true; blink.run() }
            override fun onViewDetachedFromWindow(v: View) { v.removeCallbacks(blink) }
        })
        val addLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addLp.topMargin = dp(18)
        body.addView(add, addLp)
        val skip = styledButton("Přeskočit", false)   // vzdy dole
        skip.setOnClickListener { dismiss(); finishPlusTutorial() }
        val skipLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        skipLp.topMargin = dp(10)
        body.addView(skip, skipLp)
        body
    }
}

// Zacatek zive tutorialu (po "Okay" v uvodni zprave): tlacitka, pole, Sdilet a tlacitka v liste dostanou obrys
internal fun MainActivity.startLiveTutorial() {
    AppSettings.liveTutorial.set(((1..12) + listOf(101, 102, 103, 104, 201, 202, 203)).joinToString(","))
    applyLiveTutorial()
}

// Prvni klepnuti / drzeni na prvek: jeho obrys zmizi a je zase normalni
// Vrati true, kdyz se misto bezne akce spustila ukazka prvniho tutorialu (AdBlock 7, VPN 8, Sdilet 103, zamek 104)
internal fun MainActivity.liveTutorialDone(n: Int): Boolean {
    val left = liveIds()
    if (n !in left) return false
    AppSettings.liveTutorial.set(left.filter { it != n }.joinToString(","))
    liveOutlines.remove(n)?.let { (v, d) -> v.overlay.remove(d) }
    if (n == 103 || n == 104) {
        liveIcons.remove(findViewById(if (n == 103) R.id.ivUrlShare else R.id.ivUrlLock))
        paintTutorialIcon(n, false)
    }
    if (liveIds().none { it in 9..12 } && tutorialPlus.isNotEmpty()) rebuildTutorialPlus(false)
    if (liveOutlines.isEmpty() && tutorialPlus.isEmpty() && liveIcons.isEmpty()) { liveAnim?.cancel(); liveAnim = null }
    val gestBtn = n in 1..12 && n != 7 && n != 8 && buttonFunction(n, false) == ButtonFunction.GESTURES
    // tlacitka 1-4: ukazka jen pri vychozich funkcich (klepnuti i drzeni), jinak by ukazovala neco jineho nez tlacitko dela
    val basic = n in 1..4 && !gestBtn && buttonFunction(n, false) == ButtonDefaults.tap(n) && buttonFunction(n, true) == ButtonDefaults.long(n)
    return (n == 7 || n == 8 || n == 103 || n == 104 || n in 201..203 || gestBtn || basic) && startFirstDemo(n)
}

internal fun MainActivity.updateButtonVisuals() {
    ensureSettingsReachable()
    updateSideButtons()
    for (i in 1..8) {
        val fn = buttonFunction(i, false)
        val custom = fn != ButtonDefaults.tap(i)
        val glyph = if (custom) glyphBitmap(fn) else null
        when (i) {
            1 -> setIcon(R.id.ivHome, R.drawable.ic_home, glyph)
            2 -> {
                val label = findViewById<TextView>(R.id.tvTabCount)
                if (custom) {
                    // vlastni funkce: bila ikona (obrazek) misto cisla
                    label.text = ""
                    val bd = glyphBitmap(fn, dp(28)).toDrawable(resources)
                    bd.gravity = Gravity.CENTER
                    label.background = bd
                } else {
                    label.text = tabs.list.size.toString()
                    label.background = tabCountLayer
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }
            }
            3 -> setIcon(R.id.ivBookmarks, R.drawable.ic_bookmarks, glyph)
            4 -> {
                // vychozi funkce = logo appky na svetlem tlacitku; vlastni funkce = bila ikona na tmavem
                setIcon(R.id.btnSettings, R.drawable.asap_logo, glyph)
                findViewById<View>(R.id.btnSettings).setBackgroundResource(
                    if (glyph == null) R.drawable.bg_search_white else R.drawable.btn_circle_dark
                )
            }
            5 -> setIcon(R.id.btnSideLeftMain, R.drawable.ic_sidebar, glyph)
            // 6 = gesta: zvlastni kulicka (kresli ji GestureOrbView), bez ikony
            6 -> {
                // kulicka (bez ikony) jen pro funkci Gesta; jinak bezne boční tlacitko s ikonou funkce
                if (custom) setIcon(R.id.btnSideRightMain, android.R.drawable.ic_menu_info_details, glyph)
                else findViewById<ImageView>(R.id.btnSideRightMain).setImageDrawable(null)
            }
            7 -> {
                setIcon(R.id.ivAdBlock, R.drawable.ic_adblock, glyph)
                findViewById<View>(R.id.tvAdBlockState).visibility = if (custom) View.GONE else View.VISIBLE
            }
            8 -> {
                setIcon(R.id.ivVpn, R.drawable.ic_vpn, glyph)
                findViewById<View>(R.id.tvVpnState).visibility = if (custom) View.GONE else View.VISIBLE
            }
        }
    }
    showNumbersOnDisabledButtons()
    applyLiveTutorial()
}

// Sytě svetle modre problikavani ~10 s (22 x 450 ms) u prave pridaneho tlacitka
internal fun MainActivity.startBlink(v: View) {
    blinkAnim?.cancel()
    val bg = GradientDrawable()
    bg.cornerRadius = dp(16).toFloat()
    val base = "#1E1E1E".toColorInt()
    val blue = "#00B0FF".toColorInt()
    val eval = ArgbEvaluator()
    v.background = bg
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 450
    a.repeatMode = ValueAnimator.REVERSE
    a.repeatCount = 21
    a.addUpdateListener {
        val k = it.animatedValue as Float
        bg.setColor(eval.evaluate(k, base, blue) as Int)
        bg.setStroke(dp(2), blue)
    }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            v.setBackgroundResource(R.drawable.side_button_bg)
        }

        override fun onAnimationCancel(animation: Animator) {
            v.setBackgroundResource(R.drawable.side_button_bg)
        }
    })
    blinkAnim = a
    a.start()
}

// Konec updateButtonVisuals: tvar tlacitka 6, pridana tlacitka a vysouvani bocnich
internal fun MainActivity.updateSideButtons() {
    // tlacitko 6: s funkci Gesta je to jen pruhledna plocha - kulicku kresli GestureOrbView pod nim;
    // s jinou funkci je to bezne boční tlacitko (tmave, s ikonou)
    val isOrb = gestureButtonN() == 6
    val b6 = findViewById<View>(R.id.btnSideRightMain)
    if (isOrb) b6.background = Color.TRANSPARENT.toDrawable() else b6.setBackgroundResource(R.drawable.side_button_bg)
    (b6 as? ImageView)?.scaleType = if (isOrb) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_INSIDE
    b6.setPadding(if (isOrb) 0 else dp(8), if (isOrb) 0 else dp(8), if (isOrb) 0 else dp(8), if (isOrb) 0 else dp(8))
    // tlacitka 5-8 si uzivatel muze skryt (kulicka mizi s tlacitkem 6 nebo kdyz neni funkce Gesta)
    // v konfiguracnim modu jsou videt i skryta tlacitka (pruhledna), aby je slo nastavit
    fun show(id: Int, n: Int) {
        val v = findViewById<View>(id)
        val on = AppSettings.isEnabled(n)
        v.visibility = if (on || configMode) View.VISIBLE else View.GONE
        v.alpha = if (!on && configMode) 0.75f else 1f
    }
    show(R.id.btnSideLeftMain, 5)
    show(R.id.btnSideRightMain, 6)
    show(R.id.btnAdBlock, 7)
    show(R.id.btnVpn, 8)
    updateOrbTarget()

    for ((n, v) in extraViews) {
        (v as ImageView).setImageBitmap(glyphBitmap(buttonFunction(n, false)))
    }
    for (n in listOf(5, 6) + extraViews.keys.sorted()) buttonView(n)?.let { applySideMode(n, it) }
}

internal fun MainActivity.setIcon(viewId: Int, defaultRes: Int, glyph: Bitmap?) {
    val iv = findViewById<ImageView>(viewId)
    iv.clearColorFilter()
    if (glyph != null) iv.setImageBitmap(glyph) else iv.setImageResource(defaultRes)
}

// Ikona funkce (bila vektorova ikona ze sady) nakreslena do obrazku pro tlacitko
internal fun MainActivity.glyphBitmap(fn: ButtonFunction, sizePx: Int = dp(48)): Bitmap {
    val bmp = createBitmap(sizePx, sizePx)
    val d = AppCompatResources.getDrawable(this, fn.iconRes)
    if (d != null) {
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(Canvas(bmp))
    }
    return bmp
}

// Velke cislo tlacitka (ukazuje se na neaktivnich tlacitkach 5-8 v konfiguracnim modu, aby bylo poznat, ktere to je)
internal fun MainActivity.numberBitmap(n: Int): Bitmap {
    val size = dp(56)
    val bmp = createBitmap(size, size)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.WHITE
    p.textSize = size * 0.62f
    p.typeface = Typeface.DEFAULT_BOLD
    p.textAlign = Paint.Align.CENTER
    c.drawText(n.toString(), size / 2f, size / 2f - (p.descent() + p.ascent()) / 2f, p)
    return bmp
}

// Neaktivni tlacitka (5-8) maji v konfiguracnim modu misto ikony velke cislo
internal fun MainActivity.showNumbersOnDisabledButtons() {
    if (!configMode) return
    for (n in 5..8) {
        if (AppSettings.isEnabled(n)) continue
        val bmp = numberBitmap(n)
        when (n) {
            5 -> findViewById<ImageView>(R.id.btnSideLeftMain).setImageBitmap(bmp)
            6 -> findViewById<ImageView>(R.id.btnSideRightMain).setImageBitmap(bmp)
            7 -> {
                findViewById<ImageView>(R.id.ivAdBlock).setImageBitmap(bmp)
                findViewById<View>(R.id.tvAdBlockState).visibility = View.GONE
            }
            8 -> {
                findViewById<ImageView>(R.id.ivVpn).setImageBitmap(bmp)
                findViewById<View>(R.id.tvVpnState).visibility = View.GONE
            }
        }
    }
}


// Pomale blikani (modra <-> zluta) upraveneho tlacitka 10 s po potvrzeni v modalu. Kresli se pres ViewOverlay,
// takze nemeni pozadi tlacitka (nic se nemusi vracet).
// Pomale modre pulzovani upravovaneho tlacitka (bezi, dokud se okno nezavre)
private var editPulse: ValueAnimator? = null
@SuppressLint("StaticFieldLeak")
private var editPulseView: View? = null
private var editPulseDrawable: GradientDrawable? = null

internal fun MainActivity.startEditPulse(n: Int) {
    stopEditPulse()
    val v = buttonView(n) ?: return
    val d = GradientDrawable()
    d.cornerRadius = dp(18).toFloat()
    v.overlay.add(d)
    val blue = "#00B0FF".toColorInt()
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 1400
    a.repeatMode = ValueAnimator.REVERSE
    a.repeatCount = ValueAnimator.INFINITE
    a.addUpdateListener {
        d.setBounds(0, 0, v.width, v.height)
        val f = it.animatedValue as Float
        d.setColor((blue and 0x00FFFFFF) or ((0x10 + (0x40 * f).toInt()) shl 24))
        d.setStroke(dp(3), (blue and 0x00FFFFFF) or ((0x70 + (0x8F * f).toInt()) shl 24))
    }
    a.start()
    editPulse = a; editPulseView = v; editPulseDrawable = d
}

@Suppress("UnusedReceiverParameter")
internal fun MainActivity.stopEditPulse() {
    editPulse?.cancel()
    editPulseDrawable?.let { editPulseView?.overlay?.remove(it) }
    editPulse = null; editPulseView = null; editPulseDrawable = null
}

internal fun MainActivity.blinkSaved(n: Int) {
    val v = buttonView(n) ?: return
    val d = GradientDrawable()
    d.cornerRadius = dp(18).toFloat()
    val blue = "#00B0FF".toColorInt()
    val yellow = "#FFD600".toColorInt()
    val eval = ArgbEvaluator()
    fun place() = d.setBounds(0, 0, v.width, v.height)
    place()
    v.overlay.add(d)
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 1000
    a.repeatMode = ValueAnimator.REVERSE
    a.repeatCount = 9   // 10 pulzu x 1 s = 10 s
    a.addUpdateListener {
        place()
        val c = eval.evaluate(it.animatedValue as Float, blue, yellow) as Int
        d.setColor((c and 0x00FFFFFF) or 0x40000000)
        d.setStroke(dp(3), c)
    }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) { v.overlay.remove(d) }
        override fun onAnimationCancel(animation: Animator) { v.overlay.remove(d) }
    })
    a.start()
}


// Pri drzeni tlacitka: ikona klepnuti rychle zmizi a naskoci ikona funkce pro drzeni; za chvili se vrati ta puvodni.
internal fun MainActivity.holdIconFlip(n: Int) {
    if (!AppSettings.animationsOn.get()) return
    val tapFn = buttonFunction(n, false)
    val longFn = buttonFunction(n, true)
    if (longFn == ButtonFunction.NONE || longFn == tapFn) return
    if (longFn == ButtonFunction.GESTURES || tapFn == ButtonFunction.GESTURES || n == 2 || n == 6) return   // kulicka / cislo karet maji vlastni vzhled
    val iv: ImageView = when (n) {
        1 -> findViewById(R.id.ivHome)
        3 -> findViewById(R.id.ivBookmarks)
        4 -> findViewById(R.id.btnSettings)
        5 -> findViewById(R.id.btnSideLeftMain)
        7 -> findViewById(R.id.ivAdBlock)
        8 -> findViewById(R.id.ivVpn)
        else -> (buttonView(n) as? ImageView) ?: return
    }
    iv.animate().cancel()
    iv.animate().alpha(0f).setDuration(110).withEndAction {
        iv.clearColorFilter()
        iv.setImageBitmap(glyphBitmap(longFn))
        iv.animate().alpha(1f).setDuration(160).withEndAction {
            // po chvilce zpet na ikonu klepnuti
            iv.postDelayed({
                iv.animate().alpha(0f).setDuration(110).withEndAction {
                    updateButtonVisuals()
                    iv.animate().alpha(1f).setDuration(160).start()
                }.start()
            }, 900)
        }.start()
    }.start()
}
