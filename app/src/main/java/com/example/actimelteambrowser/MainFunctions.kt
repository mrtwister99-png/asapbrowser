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

// Provedení funkcí tlačítek a gest (performFunction) a jednoduché akce (rozšíření MainActivity).

// Odkaz z jine aplikace (appka je vychozi prohlizec): otevre se na nove karte. Jen http/https, nic jineho.
internal fun MainActivity.handleIncomingIntent(i: Intent?, cold: Boolean) {
    if (i?.action != Intent.ACTION_VIEW) return
    val url = i.dataString ?: return
    if (!isWebUrl(url)) return
    i.action = null   // zpracovat jen jednou
    if (cold) createTab(url) else openInNewTab(url, false)
}
internal fun MainActivity.rememberClosed(tab: BrowserTab) {
    if (tab.incognito) return   // anonymni karty se nepamatuji
    val u = tab.pendingUrl ?: tab.url.ifBlank { tab.liveUrl() }
    if (isWebUrl(u)) {
        closedUrls.add(u)
        if (closedUrls.size > 20) closedUrls.removeAt(0)
    }
}

internal fun MainActivity.openSettingsAt(start: String?): Unit {
    if (settingsOpen) return   // okno Nastaveni uz je otevrene / se otevira
    settingsOpen = true
    val i = settingsIntent()
    if (start != null) i.putExtra(SettingsActivity.EXTRA_START, start)
    settingsLauncher.launch(i)
}

internal fun MainActivity.toggleAdBlock() {
    isAdBlockOn = !isAdBlockOn
    AppSettings.adBlockOn.set(isAdBlockOn)
    applyAdBlockUi()
    Toast.makeText(this, if (isAdBlockOn) "AdBlock ON" else "AdBlock OFF", Toast.LENGTH_SHORT).show()
    webView.reload()
}

internal fun MainActivity.switchRelative(delta: Int) {
    // jen mezi kartami stejne skupiny (bezne / anonymni)
    val incog = tabs.active()?.incognito ?: false
    val group = tabs.list.filter { it.incognito == incog }
    val n = group.size
    if (n < 2) return
    val idx = group.indexOfFirst { it.id == tabs.activeId }
    switchTo(group[((idx + delta) % n + n) % n])
}

// Nastavi user-agent karty (mobilni / pocitac); stranku nenacita znovu
internal fun MainActivity.applyDesktop(tab: BrowserTab, on: Boolean) {
    tab.desktop = on
    val s = tab.webView.settings
    if (on) {
        s.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
    } else {
        s.userAgentString = WebSettings.getDefaultUserAgent(this)
        s.useWideViewPort = false
        s.loadWithOverviewMode = false
    }
}

// Prepnuti verze pro pocitac: pamatuje se podle webu (jen bezne karty), pri dalsi navsteve se pouzije samo
internal fun MainActivity.toggleDesktopMode() {
    val tab = tabs.active() ?: return
    applyDesktop(tab, !tab.desktop)
    if (!tab.incognito) {
        val host = try { Uri.parse(tab.webView.url).host } catch (e: Exception) { null }
        SiteRules.setDesktop(host, tab.desktop)
    }
    tab.webView.reload()
    updateDeskBtn()
    Toast.makeText(this, if (tab.desktop) "Verze pro počítač (pamatuje se pro tento web)" else "Mobilní verze", Toast.LENGTH_SHORT).show()
}

// Provede funkci tlacitka (klepnuti i drzeni jdou stejnou cestou)
@Suppress("DEPRECATION")
internal fun MainActivity.performFunction(fn: ButtonFunction) {
    if (isOpeningFunction(fn)) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now < openLockUntil) return   // pred chvili se uz neco otevrelo
        openLockUntil = now + 900
    }
    val curUrl = webView.url
    when (fn) {
        ButtonFunction.NONE -> {}
        // --- navigace a stranka
        ButtonFunction.HOME -> webView.loadUrl(homeUrl)
        ButtonFunction.BACK -> if (webView.canGoBack()) webView.goBack()
        ButtonFunction.FORWARD -> if (webView.canGoForward()) webView.goForward()
        ButtonFunction.RELOAD -> webView.reload()
        ButtonFunction.SCROLL_TOP -> webView.pageUp(true)
        ButtonFunction.SCROLL_BOTTOM -> webView.pageDown(true)
        ButtonFunction.DESKTOP_MODE -> toggleDesktopMode()
        ButtonFunction.SHARE_URL -> if (curUrl != null) {
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_TEXT, curUrl)
            startActivity(Intent.createChooser(send, "Sdílet adresu"))
        }
        ButtonFunction.COPY_URL -> if (curUrl != null) {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", curUrl))
            Toast.makeText(this, "Adresa zkopírována", Toast.LENGTH_SHORT).show()
        }
        ButtonFunction.ZOOM_IN -> webView.zoomIn()
        ButtonFunction.ZOOM_OUT -> webView.zoomOut()
        // --- karty
        ButtonFunction.TABS_OVERVIEW -> openTabsOverview()
        ButtonFunction.NEW_TAB -> {
            val active = tabs.active()
            val openNew = {
                openStartTab()
                Toast.makeText(this, "Nová karta", Toast.LENGTH_SHORT).show()
            }
            if (active != null) capturePreview(active) { openNew() } else openNew()
        }
        ButtonFunction.NEW_INCOGNITO -> {
            createTab(homeUrl, incognito = true)
            Toast.makeText(this, "Anonymní karta", Toast.LENGTH_SHORT).show()
        }
        ButtonFunction.CLOSE_TAB -> closeTab(tabs.activeId)
        ButtonFunction.CLOSE_ALL_TABS -> closeAllTabs()
        ButtonFunction.PREV_TAB -> switchRelative(-1)
        ButtonFunction.NEXT_TAB -> switchRelative(1)
        ButtonFunction.DUPLICATE_TAB -> if (curUrl != null && isWebUrl(curUrl)) createTab(curUrl)
        ButtonFunction.REOPEN_TAB -> if (closedUrls.isNotEmpty()) createTab(closedUrls.removeAt(closedUrls.size - 1))
        // --- zalozky, historie, hledani
        ButtonFunction.OPEN_BOOKMARKS -> openSettingsAt(SettingsActivity.START_BOOKMARKS)
        ButtonFunction.QUICK_BOOKMARK -> {
            if (curUrl == null || !isWebUrl(curUrl)) {
                Toast.makeText(this, "Tuhle stránku nelze uložit", Toast.LENGTH_SHORT).show()
            } else {
                val title = webView.title.orEmpty()
                thread(name = "bookmark-quick") {
                    try {
                        bookmarksRepo.addBookmark(bookmarksRepo.defaultFolderId(), curUrl, title)
                    } catch (e: Exception) {
                    }
                    runOnUiThread { Toast.makeText(this, "Přidáno do Oblíbených", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        ButtonFunction.ADD_TO_FOLDER -> addCurrentPageToFolder()
        ButtonFunction.OPEN_HISTORY -> openSettingsAt(SettingsActivity.START_HISTORY)
        ButtonFunction.FOCUS_SEARCH -> etSearchMain.requestFocus()
        ButtonFunction.HOME_SETTINGS -> showHomeDialog()
        // --- nastroje a system
        ButtonFunction.ADBLOCK_TOGGLE -> toggleAdBlock()
        ButtonFunction.VPN -> Toast.makeText(this, "VPN je v rekonstrukci", Toast.LENGTH_SHORT).show()
        ButtonFunction.OPEN_SETTINGS -> openSettingsAt(null)
        ButtonFunction.OPEN_BUTTONS -> enterConfigMode()
        ButtonFunction.EXPORT -> openSettingsAt(SettingsActivity.START_EXPORT)
        ButtonFunction.FEEDBACK -> openSettingsAt(SettingsActivity.START_FEEDBACK)
        ButtonFunction.IMPORT -> openSettingsAt(SettingsActivity.START_IMPORT)
        ButtonFunction.FIND_IN_PAGE -> webView.showFindDialog(null, true)
        ButtonFunction.GESTURES -> showGestures()
        ButtonFunction.LEFT_BAR -> toggleLeftBarAny()
        ButtonFunction.SCREENSHOT -> showScreenshotChooser()
        ButtonFunction.QUICK_SCREENSHOT -> captureFullPage()
        ButtonFunction.EXIT_APP -> finishAffinity()
    }
}
