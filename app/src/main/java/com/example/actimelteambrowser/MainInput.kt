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

// Adresní pole, našeptávač, klávesnice a zadávání adresy (rozšíření MainActivity).
// Klepnuti do pole (dole i nahore): pole se vycisti, otevre se klavesnice a ukazou se navrhy
// Pozadi poli: spodni #D9D9D9, horni tmave; obrys (zelena) se kresli jen pri psani a pulzuje
internal fun MainActivity.setupFieldBackgrounds() {
    val sb = GradientDrawable()
    sb.setColor(Color.parseColor("#D9D9D9"))
    sb.cornerRadius = dp(28).toFloat()
    val ub = GradientDrawable()
    ub.setColor(Color.parseColor("#7D7C7C"))
    ub.cornerRadius = dp(20).toFloat()
    searchBg = sb
    urlBg = ub
    etSearchMain.background = sb
    findViewById<View>(R.id.urlContainer).background = ub
}

// Zeleny obrys obou poli pomalu roste na 200 % tloustky a vraci se zpet (cyklus ~1,8 s)
internal fun MainActivity.startOutlinePulse(top: Boolean) {
    // pulzuje jen pole, ve kterem se pise; druhe ma obrys vypnuty
    pulseAnim?.cancel()
    searchBg?.setStroke(0, Color.TRANSPARENT)
    urlBg?.setStroke(0, Color.TRANSPARENT)
    val target = if (top) urlBg else searchBg
    val green = Color.parseColor("#00B055")
    val base = dp(2)
    val a = ValueAnimator.ofFloat(1f, 2f)
    a.duration = 900
    a.repeatMode = ValueAnimator.REVERSE
    a.repeatCount = ValueAnimator.INFINITE
    a.addUpdateListener {
        val w = (base * (it.animatedValue as Float)).toInt()
        target?.setStroke(w, green)
    }
    pulseAnim = a
    a.start()
}

internal fun MainActivity.stopOutlinePulse() {
    pulseAnim?.cancel()
    pulseAnim = null
    searchBg?.setStroke(0, Color.TRANSPARENT)
    urlBg?.setStroke(0, Color.TRANSPARENT)
}

internal fun MainActivity.startFreshInput(field: EditText) {
    setChromeHidden(false)
    startOutlinePulse(field === etTopUrl)
    // tlacitko Oblibene zmizi a pole se prodlouzi
    TransitionManager.beginDelayedTransition(bottomBar as ViewGroup)
    findViewById<View>(R.id.btnBookmarks).visibility = View.GONE
    suggestionsAtTop.value = (field === etTopUrl)
    lastTypedLen = 0
    // dolni pole ukaze adresu aktualni stranky (kurzor na konci, jde upravit); horni pole zacina prazdne
    if (!(field === etSearchMain && prefillCurrentUrl(field))) field.setText("")
    updateSuggestions("")
    field.post { showKeyboard(field) }
}

// Vlozi adresu aktualni stranky do pole a da kurzor na konec. false = neni co vlozit (nova karta, interni stranka)
internal fun MainActivity.prefillCurrentUrl(field: EditText): Boolean {
    val cur = webView.url ?: return false
    if (!isWebUrl(cur)) return false
    prefillingUrl = true
    try {
        field.setText(cur)
        field.setSelection(field.text.length)
    } finally {
        prefillingUrl = false
    }
    lastTypedLen = cur.length   // aby doplnovani (autoComplete) nereagovalo na vlozenou adresu
    return true
}

// Tlacitko "Upravit" na zelenem radku: adresa stranky se vlozi do pole, ve kterem se pise (horni i dolni)
internal fun MainActivity.editCurrentUrl() {
    val field = if (etTopUrl.hasFocus()) etTopUrl else etSearchMain
    if (prefillCurrentUrl(field)) field.post { showKeyboard(field) }
}

// Pole ztratilo focus: pokud nepise zadne, vratit horni pole na adresu stranky a vycistit dolni
internal fun MainActivity.onInputFocusLost() {
    etSearchMain.postDelayed({
        if (!etSearchMain.hasFocus() && !etTopUrl.hasFocus()) {
            suggestions.value = emptyList()
            stopOutlinePulse()
            TransitionManager.beginDelayedTransition(bottomBar as ViewGroup)
            findViewById<View>(R.id.btnBookmarks).visibility = View.VISIBLE
            etSearchMain.setText("")
            etTopUrl.setText(topUrlText(webView.url))
            updateLockIcon(webView.url)
        }
    }, 250)
}

// Enter v poli: adresa se otevre, jinak se hleda vybranym vyhledavacem (Konfigurace / drzeni Domu)
internal fun MainActivity.submitInput(raw: String) {
    val q = raw.trim()
    if (q.isEmpty()) return
    val url = if (q.contains(".") && !q.contains(" ")) {
        if (q.startsWith("http")) q else "https://$q"
    } else SearchEngines.searchUrl(q)
    webView.loadUrl(url)
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    hideKeyboard()
}

internal fun MainActivity.refreshKnownHosts(history: List<HistoryVisit>, bookmarks: List<Bookmark>) {
    // radi se podle poctu navstev, pak podle novosti (history je od nejnovejsi)
    val counts = LinkedHashMap<String, Int>()
    for (h in history) hostOf(h.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
    for (b in bookmarks) hostOf(b.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
    knownHosts = counts.entries.sortedByDescending { it.value }.map { it.key }
}

internal fun MainActivity.autoComplete(field: EditText, s: Editable) {
    if (autoFilling) return
    val typed = s.toString()
    val grew = typed.length > lastTypedLen
    lastTypedLen = typed.length
    // doplnuje se jen pri psani (ne pri mazani), jen jedno slovo bez mezery a lomitka
    if (!grew || typed.isEmpty() || typed.any { it == ' ' || it == '/' }) return
    val q = typed.lowercase().removePrefix("www.")
    if (q.isEmpty()) return
    val host = knownHosts.firstOrNull { it.startsWith(q) && it.length > q.length } ?: return
    autoFilling = true
    try {
        s.replace(0, s.length, typed + host.substring(q.length))
        field.setSelection(typed.length, s.length)
    } finally {
        autoFilling = false
    }
}

// Text z pole, ve kterem se prave pise (ma focus), se zkopiruje do druheho pole
internal fun MainActivity.mirrorText(from: EditText, to: EditText) {
    if (syncingText || !from.hasFocus()) return
    syncingText = true
    to.setText(from.text.toString())
    to.setSelection(to.text.length)
    syncingText = false
}

// Zapise navstevu do historie (jen http/https, ne dvakrat po sobe stejnou adresu)
internal fun MainActivity.recordVisit(view: WebView?, url: String?) {
    val u = url ?: return
    if (view == null || u == "about:blank" || u == lastRecordedUrl || !isWebUrl(u)) return
    lastRecordedUrl = u
    val title = view.title.orEmpty()
    thread(name = "history-write") {
        try {
            historyRepo.recordVisit(u, title)
        } catch (e: Exception) {
        }
    }
}

// Nasepavac: dotaz do historie/zalozek bezi na pozadi, vysledek se ukaze nad spodni listou
internal fun MainActivity.copyPinnedUrl(s: SearchSuggestion) {
    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", s.url))
    // potvrzeni ("Zkopirovano") se ukaze primo na zelenem radku
}

internal fun MainActivity.updateSuggestions(query: String) {
    // prazdne pole na strance: aktualni stranka je prvni radek (s ikonou kopirovat), pod ni nejnavstevovanejsi
    val cur = webView.url
    val empty = query.isBlank()
    suggestionsCompact.value = empty
    pinnedPage.value = if (empty && cur != null && isWebUrl(cur)) {
        SearchSuggestion(webView.title.orEmpty().ifBlank { cur }, cur, cur, SearchSuggestion.Type.CURRENT, icon = tabs.active()?.favicon)
    } else null
    val token = ++suggestionToken
    suggestRunnable?.let { suggestHandler.removeCallbacks(it) }
    val run = Runnable {
        thread(name = "suggestions") {
            val result = try {
                // historie, zalozky a nejnavstevovanejsi se ctou z DB jen jednou za 15 s (a pri prazdnem poli)
                val now = android.os.SystemClock.uptimeMillis()
                if (empty || now - suggestCacheAt > 15000) {
                    cachedHist = historyRepo.recent(300)
                    cachedBms = bookmarksRepo.allBookmarks()
                    cachedTop = historyRepo.mostVisited(10)
                    refreshKnownHosts(cachedHist, cachedBms)
                    suggestCacheAt = now
                }
                buildSearchSuggestions(query, cachedHist, cachedBms, cachedTop)
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (token == suggestionToken && (etSearchMain.hasFocus() || etTopUrl.hasFocus())) suggestions.value = result
            }
        }
    }
    suggestRunnable = run
    // prazdne pole hned, pri psani kratka pauza (nehleda se po kazdem pismenu)
    if (empty) run.run() else suggestHandler.postDelayed(run, 120)
}

internal fun MainActivity.openSuggestion(url: String) {
    if (!isWebUrl(url)) return
    webView.loadUrl(url)
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    hideKeyboard()
    suggestions.value = emptyList()
}

internal fun MainActivity.showKeyboard(target: View = etSearchMain) {
    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
}

internal fun MainActivity.hideKeyboard() {
    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(etSearchMain.windowToken, 0)
}

// Zavre naseptavac + klavesnici (pole ztrati focus, onInputFocusLost vrati adresu)
internal fun MainActivity.dismissInput() {
    suggestionToken++                       // zahodi rozpracovany dotaz do historie
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
    hideKeyboard()
    suggestions.value = emptyList()
}

internal fun MainActivity.isTouchInside(v: View, ev: MotionEvent): Boolean {
    if (v.visibility != View.VISIBLE) return false
    val loc = IntArray(2)
    v.getLocationOnScreen(loc)              // pocita i s posunem nad klavesnici
    return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
        ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
}
