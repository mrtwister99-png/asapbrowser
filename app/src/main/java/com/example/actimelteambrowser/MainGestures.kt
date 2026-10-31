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

// Gesta: plocha, kulička a konfigurační mód gest (rozšíření MainActivity).
internal fun MainActivity.enterGestureConfig() {
    if (gestureConfigMode) return
    if (configMode) exitConfigMode()
    gestureConfigMode = true
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
    scrim.setBackgroundColor(Color.parseColor("#44000000"))
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
        @Suppress("UNUSED_VARIABLE") val tick = gestTick.value
        GestureAreaEditor(
            style = gestPreviewStyle.value ?: AppSettings.gestureStyle.get(),
            opacity = AppSettings.gestureOpacity.get() / 100f,
            defaultSizeDp = AppSettings.gestureSize.get().coerceIn(180, 340),
            bottomOffsetDp = bottomDp,
            resetKey = gestResetKey.value,
            onChange = { AppSettings.gestureRect.set(GestureArea.format(it)) }
        )
    }
    gestureView.visibility = View.VISIBLE

    // panel shora
    val panel = ComposeView(this)
    panel.setContent {
        GesturePanel(
            onChanged = { gestTick.value = gestTick.value + 1 },
            onResetArea = {
                AppSettings.gestureRect.reset()
                AppSettings.gestureSize.reset()
                gestResetKey.value = gestResetKey.value + 1
            },
            onClose = { exitGestureConfig() },
            onPreviewStyle = { gestPreviewStyle.value = it }
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
    if (gestureButtonN() == 0) Toast.makeText(this, "Tlačítko Gesta není přiřazené, kulička ukazuje výchozí místo (tlačítko 6)", Toast.LENGTH_LONG).show()
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
        if (c.visibility == View.VISIBLE) {
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
    for ((v, vis) in gestHidden) v.visibility = vis
    gestHidden.clear()
    hideGestures()   // zavre editor (ComposeView plochy)
    rebuildExtraButtons()
    wireButtons()
    updateButtonVisuals()
    Toast.makeText(this, "Nastavení gest uloženo", Toast.LENGTH_SHORT).show()
}

// Otevre pruhlednou plochu pro gesta (vychozi vpravo dole, 9 je u tlacitka 6; poloha a velikost podle nastaveni)
internal fun MainActivity.showGestures() {
    hideKeyboard()
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    val list = Gestures.load()
    val style = AppSettings.gestureStyle.get()
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
            onDismiss = { hideGestures() },
            onMatch = { def ->
                // vibrace pri rozpoznanem gestu
                gestureTick(this)
                hideGestures()
                performFunction(def.fn)
            },
            external = externalTouch.value,
            externalDown = externalDown,
            onUnknown = { Toast.makeText(this, "Neznámé gesto", Toast.LENGTH_SHORT).show() }
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
            if (b6.visibility == View.GONE) b6.visibility = View.INVISIBLE   // jinak by nemelo polohu
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

// Zivá kulicka pod tlacitkem 6 (gesta); vetsi nez tlacitko, aby jiskry mohly vyletet ven
internal fun MainActivity.setupGestureOrb() {
    if (orbView != null) return
    val btn6 = findViewById<View>(R.id.btnSideRightMain)
    val orb = GestureOrbView(this, btn6)
    orb.elevation = dp(20).toFloat()   // nad spodni listou i bocnimi tlacitky; dotyky nechyta
    // vlevo nahore v rodici; presnou polohu na tlacitku si orb pocita sam (sleduje cilove tlacitko)
    val lp = ConstraintLayout.LayoutParams(dp(150), dp(150))
    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    (root as ViewGroup).addView(orb, lp)
    orbView = orb
}
