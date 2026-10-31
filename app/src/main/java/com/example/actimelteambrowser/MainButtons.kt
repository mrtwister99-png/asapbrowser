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
internal fun MainActivity.wireGestureTouch(v: View, n: Int) {
    v.setOnTouchListener { view, ev ->
        if (buttonFunction(n, false) != ButtonFunction.GESTURES) return@setOnTouchListener false
        val wl = IntArray(2)
        window.decorView.getLocationOnScreen(wl)
        val x = ev.rawX - wl[0]
        val y = ev.rawY - wl[1]
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                val down = ExternalTouch(x, y, 0, ++extSeq)
                externalDown = down
                if (gestureView.visibility != View.VISIBLE) showGestures()
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
    if (gestureView.visibility == View.VISIBLE) hideGestures()
    if (leftBarOpen) closeLeftBarAndHide()
    hideTabs()
    etSearchMain.isEnabled = false
    etTopUrl.isEnabled = false
    val parent = root as ViewGroup
    val green = Color.parseColor("#00B055")

    // lehke ztmaveni plochy; pohlti klepnuti do webu (tlacitka maji vyssi elevation)
    val scrim = View(this)
    scrim.setBackgroundColor(Color.parseColor("#44000000"))
    scrim.isClickable = true
    scrim.elevation = dp(7).toFloat()
    val slp = ConstraintLayout.LayoutParams(0, 0)
    slp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    slp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    slp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    slp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    parent.addView(scrim, slp)
    configScrim = scrim

    // uprostred: zelene prerusovane tlacitko
    val save = TextView(this)
    save.text = "Uložit a zavřít nastavení"
    save.setTextColor(green)
    save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    save.setTypeface(save.typeface, Typeface.BOLD)
    save.gravity = Gravity.CENTER
    save.setPadding(dp(26), dp(18), dp(26), dp(18))
    val sbg = GradientDrawable()
    sbg.setColor(Color.parseColor("#E61E1E1E"))
    sbg.cornerRadius = dp(18).toFloat()
    sbg.setStroke(dp(2), green, dp(10).toFloat(), dp(7).toFloat())   // prerusovany obrys
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
            ButtonConfigModal(
                n = n,
                onChanged = { refreshConfigUi() },
                onRemoved = { configButton.value = null; refreshConfigUi() },
                onDismiss = { configButton.value = null }
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
    Toast.makeText(this, "Nastavení tlačítek uloženo", Toast.LENGTH_SHORT).show()
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
    val green = Color.parseColor("#00B055")
    for (left in listOf(true, false)) {
        val list = if (left) l else r
        if (list.size >= 2) continue
        val anchor = if (list.isEmpty()) (if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain)
        else extraViews[list.last()]?.id ?: continue
        val plus = TextView(this)
        plus.text = "+"
        plus.setTextColor(green)
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        plus.setTypeface(plus.typeface, Typeface.BOLD)
        plus.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#E61E1E1E"))
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
internal fun MainActivity.buzz(v: View) {
    if (AppSettings.vibrationOn.get()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

// Klepnuti: tlacitka 1-4 maji sve animace (1 problikne 2x zelene, 2 se otoci o 360 stupnu,
// 3 vyplni zlutou hvezdu s jiskrami, 4 prohodi logo za kolecko) a az potom provedou funkci
internal fun MainActivity.runTap(i: Int) {
    val fn = buttonFunction(i, false)
    if (fn == ButtonDefaults.tap(i) && isDefaultVisual(i) && AppSettings.animationsOn.get()) {
        when (i) {
            1 -> { blinkHome(); performFunction(fn); return }
            2 -> { spinTabs { performFunction(fn) }; return }
            3 -> { animateStar { performFunction(fn) }; return }
            4 -> { playSettingsOutro { performFunction(fn) }; return }
        }
    }
    performFunction(fn)
}

// Drzeni: puvodni animace se prehraje jen u vychozi funkce toho tlacitka
internal fun MainActivity.runLongPress(i: Int) {
    val fn = buttonFunction(i, true)
    if (fn != ButtonFunction.NONE && fn == ButtonDefaults.long(i) && isDefaultVisual(i) && AppSettings.animationsOn.get()) {
        when (i) {
            1 -> { animateHome { performFunction(fn) }; return }
            2 -> { animateTabsButton { performFunction(fn) }; return }
            3 -> { animateStar { performFunction(fn) }; return }
        }
    }
    performFunction(fn)
}

// Bocni tlacitko je trvale zasunute za okraj (50 nebo 70 %); klepnuti i drzeni spusti funkci rovnou
internal fun MainActivity.wireSideButton(n: Int, v: View) {
    // spusti funkci tlacitka; u 5/6 si zapamatuje, ze lista ma vyjet z teto strany
    fun press(long: Boolean) {
        if (n == 5 || n == 6) pressedOrigin = n
        performFunction(buttonFunction(n, long))
        pressedOrigin = 0
    }
    v.setOnClickListener { press(false) }
    v.setOnLongClickListener {
        buzz(it)
        press(true)
        true
    }
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
    bg.setColor(Color.parseColor("#1E1E1E"))
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
        b.setOnClickListener { action() }
        val blp = LinearLayout.LayoutParams(dp(48), dp(48))
        blp.bottomMargin = dp(8)
        bar.addView(b, blp)
        return b
    }
    fun glyph(text: String, sizeSp: Float = 22f) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    // 1) screenshot obrazovky (sdilet odkaz a QR jsou ted u adresniho radku)
    fun whiteIcon(g: String) = ImageView(this).apply { setImageBitmap(GlyphIcons.white(g, dp(28))) }
    fun fnIcon(fn: ButtonFunction) = ImageView(this).apply { setImageBitmap(glyphBitmap(fn, dp(28))) }
    fun resIcon(res: Int) = ImageView(this).apply {
        val bmp = Bitmap.createBitmap(dp(28), dp(28), Bitmap.Config.ARGB_8888)
        androidx.appcompat.content.res.AppCompatResources.getDrawable(this@setupSideBar, res)?.let {
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
        g.setColor(Color.parseColor("#00B055"))
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

// Dokud je lista otevrena, pridana tlacitka nad tlacitkem na teze strane se uplne schovaji (100 %),
// po zavreni se vrati. Tlacitko s kulickou gest se jen skryje/ukaze (jeho pruhlednost ridi kulicka).
internal fun MainActivity.hideExtrasForBar(open: Boolean) {
    val orbN = gestureButtonN()
    for ((n, v) in extraViews) {
        val sameSide = isLeftSide(n) == (barOrigin != 6)
        val hide = open && sameSide
        if (n == orbN) {
            v.visibility = if (hide) View.INVISIBLE else View.VISIBLE
            continue
        }
        v.animate().cancel()
        if (hide) {
            v.animate().alpha(0f).setDuration(200).withEndAction { if (leftBarOpen) v.visibility = View.INVISIBLE }.start()
        } else {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(200).start()
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
    a.duration = 320
    a.interpolator = android.view.animation.DecelerateInterpolator()
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
    Toast.makeText(this, "Nastavení vráceno na tlačítko 4", Toast.LENGTH_LONG).show()
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
                    val bd = android.graphics.drawable.BitmapDrawable(resources, glyphBitmap(fn, dp(28)))
                    bd.gravity = Gravity.CENTER
                    label.background = bd
                } else {
                    label.text = tabs.list.size.toString()
                    label.background = tabCountBg
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
}

// Sytě svetle modre problikavani ~10 s (22 x 450 ms) u prave pridaneho tlacitka
internal fun MainActivity.startBlink(v: View) {
    blinkAnim?.cancel()
    val bg = GradientDrawable()
    bg.cornerRadius = dp(16).toFloat()
    val base = Color.parseColor("#1E1E1E")
    val blue = Color.parseColor("#00B0FF")
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
    if (isOrb) b6.background = ColorDrawable(Color.TRANSPARENT) else b6.setBackgroundResource(R.drawable.side_button_bg)
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
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val d = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, fn.iconRes)
    if (d != null) {
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(Canvas(bmp))
    }
    return bmp
}

// Velke cislo tlacitka (ukazuje se na neaktivnich tlacitkach 5-8 v konfiguracnim modu, aby bylo poznat, ktere to je)
internal fun MainActivity.numberBitmap(n: Int): Bitmap {
    val size = dp(56)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
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
