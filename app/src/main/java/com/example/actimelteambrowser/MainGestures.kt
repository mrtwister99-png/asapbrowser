package com.example.actimelteambrowser

import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt
import androidx.core.view.*
import com.example.actimelteambrowser.core.settings.AppSettings
import androidx.compose.ui.platform.ComposeView
import com.example.actimelteambrowser.core.settings.Gestures
import com.example.actimelteambrowser.core.gestures.gestureTick
import com.example.actimelteambrowser.ui.GestureOverlay
import com.example.actimelteambrowser.ui.GestureArea
import com.example.actimelteambrowser.ui.GestureAreaEditor
import com.example.actimelteambrowser.ui.GesturePanel
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.actimelteambrowser.core.settings.ButtonFunction
import android.view.ViewGroup

// Gesta: plocha, kulička a konfigurační mód gest (rozšíření MainActivity).
internal fun MainActivity.enterGestureConfig() {
    if (gestureConfigMode) return
    if (configMode) exitConfigMode()
    gestureConfigMode = true
    AppSettings.totalBlocked.set(totalBlocked.get())   // aktualni pocet zablokovanych reklam (cena Ctverecku v panelu)
    setChromeHidden(false)
    hideKeyboard()
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    if (leftBarOpen) closeLeftBarAndHide()
    hideTabs()
    val parent = root as ViewGroup
    val density = resources.displayMetrics.density
    val bottomDp = ((bottomBar.height - lastSystemBottom) / density).toInt() + 16

    // lehke ztmaveni (pohlti klepnuti do webu)
    val scrim = View(this)
    scrim.setBackgroundColor("#44000000".toColorInt())
    scrim.isClickable = true
    scrim.elevation = dp(7).toFloat()
    val slp = ConstraintLayout.LayoutParams(0, 0)
    slp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    slp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    slp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    slp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    parent.addView(scrim, slp)
    gestScrim = scrim

    // editor plochy (pouziva stejny ComposeView jako samotna plocha gest)
    gestureView.setContent {
        @Suppress("UNUSED_VARIABLE", "unused") val tick = gestTick.intValue
        GestureAreaEditor(
            style = gestPreviewStyle.value ?: AppSettings.gestureStyle.get(),
            opacity = AppSettings.gestureOpacity.get() / 100f,
            defaultSizeDp = AppSettings.gestureSize.get().coerceIn(180, 340),
            bottomOffsetDp = bottomDp,
            resetKey = gestResetKey.intValue,
            onChange = { AppSettings.gestureRect.set(GestureArea.format(it)) }
        )
    }
    gestureView.visibility = View.VISIBLE

    // panel shora
    val panel = ComposeView(this)
    panel.setContent {
        GesturePanel(
            onChanged = { gestTick.intValue += 1 },
            onResetArea = {
                AppSettings.gestureRect.reset()
                AppSettings.gestureSize.reset()
                gestResetKey.intValue += 1
            },
            onClose = { exitGestureConfig() },
            onPreviewStyle = { gestPreviewStyle.value = it },
            onAlignArea = { alignGestureAreaToOrb() }
        )
    }
    panel.elevation = dp(45).toFloat()
    val plp = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
    plp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    plp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    plp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    panel.visibility = View.INVISIBLE
    parent.addView(panel, plp)
    gestPanel = panel
    panel.post {
        panel.translationY = -panel.height.toFloat()
        panel.visibility = View.VISIBLE
        panel.animate().translationY(0f).setDuration(280).start()
    }
    for (n in listOf(5, 6) + extraViews.keys) buttonView(n)?.let { applySideMode(n, it) }   // bocni tlacitka celá
    updateOrbTarget()   // kulicka (start gesta) je v konfiguraci videt vzdy
    orbView?.alwaysShow = true              // editor schova vsechna tlacitka, kulicka (tlacitko gest) ale musi zustat videt
    orbView?.elevation = dp(40).toFloat()   // nad editorem plochy (35), aby tlacitko gest bylo dobre videt a plochu sla zarovnat
    if (gestureButtonN() == 0) AppToast.show(this, "Tlačítko Gesta není přiřazené, kulička ukazuje výchozí místo (tlačítko 6)", true)
    applyGestureConfigUi()
}

// Skryje vse krome weboveho obsahu, kulicky gest, plochy a panelu (vola se i po onResume, ktery tlacitka obnovi)
internal fun MainActivity.applyGestureConfigUi() {
    val parent = root as ViewGroup
    val webTop = run {
        var v: View = webContainer
        var top: View = webContainer
        while (v.parent != null && v.parent !== parent) { v = v.parent as View; top = v }
        top
    }
    for (i in 0 until parent.childCount) {
        val c = parent.getChildAt(i)
        if (c === gestScrim || c === gestPanel || c === gestureView || c === orbView || c === webTop) continue
        if (c.isVisible) {
            gestHidden[c] = View.VISIBLE
            c.visibility = View.INVISIBLE
        }
    }
}

internal fun MainActivity.exitGestureConfig() {
    if (!gestureConfigMode) return
    gestureConfigMode = false
    val parent = root as ViewGroup
    gestPanel?.let { p ->
        p.animate().translationY(-p.height.toFloat()).setDuration(220).withEndAction { parent.removeView(p) }.start()
    }
    gestPanel = null
    gestScrim?.let { parent.removeView(it) }
    gestScrim = null
    gestPreviewStyle.value = null   // zamceny vzor se neuklada
    orbView?.elevation = dp(20).toFloat()
    orbView?.alwaysShow = false
    for ((v, vis) in gestHidden) v.visibility = vis
    gestHidden.clear()
    hideGestures()   // zavre editor (ComposeView plochy)
    rebuildExtraButtons()
    wireButtons()
    updateButtonVisuals()
    AppToast.show(this, "Nastavení gest uloženo")
}

// Otevre pruhlednou plochu pro gesta (vychozi vpravo dole, 9 je u tlacitka 6; poloha a velikost podle nastaveni)
// demoBounds != null = ukazka: plocha nic neprovede ani nezavre, jen hlasi svou polohu (x, y, sirka, vyska v px)
internal fun MainActivity.showGestures(demoBounds: ((Float, Float, Float, Float) -> Unit)? = null) {
    hideKeyboard()
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    val list = Gestures.load()
    // Ctverecky jen kdyz jsou koupene (napr. import cizeho nastaveni je neodemkne)
    val style = AppSettings.gestureStyle.get().let { if (it == 2 && !AppSettings.squareStyleOwned.get()) 0 else it }
    val size = AppSettings.gestureSize.get().coerceIn(180, 340)
    val density = resources.displayMetrics.density
    val bottomDp = ((bottomBar.height - lastSystemBottom) / density).toInt() + 16
    gestureView.setContent {
        GestureOverlay(
            style = style,
            sizeDp = size,
            gestures = list,
            bottomOffsetDp = bottomDp,
            rect = GestureArea.parse(AppSettings.gestureRect.get()),
            opacity = AppSettings.gestureOpacity.get() / 100f,
            onDismiss = { if (demoBounds == null) hideGestures() },
            onMatch = { def ->
                if (demoBounds == null) {
                    // vibrace pri rozpoznanem gestu
                    gestureTick(this)
                    hideGestures()
                    performFunction(def.fn)
                }
            },
            external = externalTouch.value,
            externalDown = externalDown,
            onUnknown = { if (demoBounds == null) AppToast.show(this, "Neznámé gesto") },
            onSurfaceBounds = demoBounds,
            showHints = demoBounds == null
        )
    }
    gestureView.visibility = View.VISIBLE
}

internal fun MainActivity.hideGestures() {
    externalTouch.value = null
    externalDown = null
    gestureView.visibility = View.GONE
    gestureView.disposeComposition()
}

// Cislo tlacitka, ktere ma funkci Gesta (klepnuti) = tam je kulicka; 0 = nikde. 7 a 8 nejsou povolena.
internal fun MainActivity.gestureButtonN(): Int {
    for (n in listOf(1, 2, 3, 4, 5, 6, 9, 10, 11, 12)) {
        if (AppSettings.isEnabled(n) && buttonView(n) != null && buttonFunction(n, false) == ButtonFunction.GESTURES) return n
    }
    return 0
}

// Kulicka se presune na tlacitko s funkci Gesta; samotne tlacitko se zneviditelni (zustava klikaci/dotykove)
internal fun MainActivity.updateOrbTarget() {
    val g = gestureButtonN()
    for (n in 1..12) {
        val v = buttonView(n) ?: continue
        if (n != g && v.alpha == 0f) v.alpha = 1f
    }
    val orb = orbView ?: return
    if (g == 0) {
        // v konfiguraci gest je kulicka videt vzdy, i kdyz zadne tlacitko nema funkci Gesta: ukazuje, kde gesto zacina
        val b6 = buttonView(6)
        if (gestureConfigMode && b6 != null) {
            if (b6.isGone) b6.visibility = View.INVISIBLE   // jinak by nemelo polohu
            orb.target = b6
            orb.visibility = View.VISIBLE
            return
        }
        orb.visibility = View.GONE
        return
    }
    val tv = buttonView(g) ?: return
    tv.alpha = 0f
    orb.target = tv
    orb.visibility = View.VISIBLE
}

// Kulicka pod tlacitkem Gest (bile kolecko + zelena tecka)
internal fun MainActivity.setupGestureOrb() {
    if (orbView != null) return
    val btn6 = findViewById<View>(R.id.btnSideRightMain)
    val orb = GestureOrbView(this, btn6)
    orb.elevation = dp(20).toFloat()   // nad spodni listou i bocnimi tlacitky; dotyky nechyta
    // vlevo nahore v rodici; presnou polohu na tlacitku si orb pocita sam (sleduje cilove tlacitko)
    val lp = ConstraintLayout.LayoutParams(dp(56), dp(56))
    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    (root as ViewGroup).addView(orb, lp)
    orbView = orb
}

// Zarovnat k tlacitku: plocha se prisune tak, aby startovni klavesa (9 vpravo dole, u tlacitek vlevo 7) lezela na kulicce tlacitka gest; velikost zustava
internal fun MainActivity.alignGestureAreaToOrb() {
    val orb = orbView ?: return
    val w = gestureView.width.toFloat()
    val h = gestureView.height.toFloat()
    if (w <= 0f || h <= 0f) return
    val gl = IntArray(2)
    val tl = IntArray(2)
    gestureView.getLocationInWindow(gl)
    orb.target.getLocationInWindow(tl)
    val cx = tl[0] - gl[0] + orb.target.width / 2f
    val cy = tl[1] - gl[1] + orb.target.height / 2f
    val density = resources.displayMetrics.density
    val cur = GestureArea.parse(AppSettings.gestureRect.get())
    val aw: Float
    val ah: Float
    if (cur != null) {
        aw = (cur.r - cur.l) / 1000f * w
        ah = (cur.b - cur.t) / 1000f * h
    } else {
        aw = AppSettings.gestureSize.get().coerceIn(180, 340) * density
        ah = aw
    }
    // tlacitko vlevo (start 7): klavesa 7 (vlevo dole) lezi na kulicce; jinak klavesa 9 (vpravo dole)
    val left = if (Gestures.startKey() == 7) (cx - aw / 6f).coerceIn(0f, w - aw) else ((cx + aw / 6f).coerceIn(aw, w) - aw)
    val bottom = (cy + ah / 6f).coerceIn(ah, h)
    val rect = com.example.actimelteambrowser.ui.GestureRect(
        (left / w * 1000f).roundToInt(), ((bottom - ah) / h * 1000f).roundToInt(),
        ((left + aw) / w * 1000f).roundToInt(), (bottom / h * 1000f).roundToInt()
    )
    AppSettings.gestureRect.set(GestureArea.format(rect))
    gestResetKey.intValue += 1   // editor nacte novou polohu
}
