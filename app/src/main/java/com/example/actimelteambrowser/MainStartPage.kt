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

// Úvodní stránka nové karty, náhledy a ikony webů, stahování souborů (rozšíření MainActivity).
// Stahovani souboru ze stranky pres systemovy DownloadManager (notifikace, seznam v "Stahovani")
internal fun MainActivity.startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
    if (!isWebUrl(url)) {
        Toast.makeText(this, "Tenhle typ odkazu nejde stáhnout", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val req = android.app.DownloadManager.Request(Uri.parse(url))
        req.setMimeType(mimeType)
        cookiesFor(url, tabs.active()?.incognito == true)?.let { req.addRequestHeader("Cookie", it) }
        if (!userAgent.isNullOrBlank()) req.addRequestHeader("User-Agent", userAgent)
        req.setTitle(name)
        req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // Android 10+: verejna slozka Stazene; starsi: slozka appky (bez nutnosti povoleni uloziste)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        } else {
            req.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, name)
        }
        val id = (getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
        com.example.actimelteambrowser.data.DownloadsStore.track(this, id, name)
        startDownloadPoll()
        Toast.makeText(this, "Stahuji: $name", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(this, "Stahování se nepovedlo", Toast.LENGTH_SHORT).show()
    }
}

// Nova karta s uvodni strankou (normalni / anonymni + nejnavstevovanejsi stranky)
internal fun MainActivity.openStartTab() {
    val t = createTab(null)
    t.startPage = true
    t.title = "Nová karta"
    updateStartPage()
}

// Ukaze / schova uvodni stranku podle aktivni karty (je pres WebView v kontejneru)
internal fun MainActivity.updateStartPage() {
    val tab = tabs.active()
    val show = tab != null && tab.startPage && !tab.incognito
    val v = startPageView
    if (!show) {
        if (v != null && v.parent != null) webContainer.removeView(v)
        return
    }
    val view = v ?: ComposeView(this).also { cv ->
        cv.setContent {
            StartPage(
                sites = startSites.value,
                favorites = startFavorites.value,
                showFavorites = startShowFav.value,
                onShowFavorites = { fav ->
                    startShowFav.value = fav
                    AppSettings.startShowFavorites.set(fav)
                },
                blockedTotal = startBlockedTotal.value,
                onNormal = { startPageNormal() },
                onIncognito = { startPageIncognito() },
                onOpen = { site -> startPageOpen(site.url) }
            )
        }
        startPageView = cv
    }
    if (view.parent == null) {
        webContainer.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    etTopUrl.setText("")
    startBlockedTotal.value = totalBlocked.get()
    startShowFav.value = AppSettings.startShowFavorites.get()
    loadStartSites()
}

// Nejnavstevovanejsi weby z historie (po domenach); kdyz jich je malo, doplni se obvyklymi
internal fun MainActivity.loadStartSites() {
    thread(name = "start-sites") {
        val result = ArrayList<StartSite>()
        try {
            val perHost = LinkedHashMap<String, Int>()
            for (m in historyRepo.mostVisited(300)) {
                val host = Uri.parse(m.url).host?.removePrefix("www.") ?: continue
                if (host.isBlank()) continue
                perHost[host] = (perHost[host] ?: 0) + m.visitCount
            }
            for ((host, _) in perHost.entries.sortedByDescending { it.value }.take(12)) {
                result.add(StartSite(host, "https://$host/", loadSiteThumb(host), loadSiteIcon(host)))
            }
        } catch (e: Exception) {
        }
        if (result.size < 12) {
            for (host in listOf("youtube.com", "google.com", "wikipedia.org", "seznam.cz", "reddit.com", "github.com")) {
                if (result.size >= 12) break
                if (result.none { it.host == host }) result.add(StartSite(host, "https://$host/", loadSiteThumb(host), loadSiteIcon(host)))
            }
        }
        // oblibene = ulozene stranky (zalozky), max 30, bez duplicit
        val favs = ArrayList<StartSite>()
        try {
            for (bm in bookmarksRepo.allBookmarks()) {
                if (favs.size >= 30) break
                if (favs.any { it.url == bm.url }) continue
                val h = Uri.parse(bm.url).host?.removePrefix("www.") ?: continue
                favs.add(StartSite(bm.title.ifBlank { h }, bm.url, loadSiteThumb(h), loadSiteIcon(h)))
            }
        } catch (e: Exception) {
        }
        runOnUiThread {
            startSites.value = result
            startFavorites.value = favs
        }
    }
}

internal fun MainActivity.saveSiteIcon(host: String, icon: Bitmap) {
    if (!AppSettings.thumbsOn.get()) return   // ukladani nahledu a ikon je vypnute v Konfiguraci
    val copy = try { icon.copy(Bitmap.Config.ARGB_8888, false) } catch (e: Exception) { null } ?: return
    thread(name = "site-icon") {
        try {
            val f = siteIconFile(host)
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { copy.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
        }
    }
}

// Ulozeny nahled domeny chybi nebo je starsi nez 3 dny
internal fun MainActivity.thumbStale(host: String): Boolean {
    val f = siteThumbFile(host)
    return !f.exists() || System.currentTimeMillis() - f.lastModified() > 3L * 24 * 3600 * 1000
}

// Ulozi nahled prave zachycene stranky pro jeji domenu (jen bezne karty)
internal fun MainActivity.saveSiteThumb(tab: BrowserTab) {
    if (tab.incognito || tab.startPage || !AppSettings.thumbsOn.get()) return
    val bmp = tab.preview ?: return
    val host = try { Uri.parse(tab.liveUrl().ifBlank { tab.url }).host } catch (e: Exception) { null }?.removePrefix("www.") ?: return
    if (host.isBlank() || !thumbStale(host)) return   // cerstvy nahled uz mame
    thread(name = "site-thumb") {
        val file = siteThumbFile(host)
        file.parentFile?.mkdirs()
        savePreview(bmp, file)
    }
}

internal fun MainActivity.startPageOpen(url: String) {
    val tab = tabs.active() ?: return
    tab.startPage = false
    updateStartPage()
    webView.loadUrl(url)
}

// Anonymni: nova anonymni karta na miste teto (puvodni uvodni karta se zavre)
internal fun MainActivity.startPageIncognito() {
    val old = tabs.active() ?: return
    val idx = tabs.list.indexOf(old)
    val fresh = createTab(homeUrl, activate = false, incognito = true)
    tabs.list.remove(fresh)
    tabs.list.add(idx.coerceIn(0, tabs.list.size), fresh)
    closeTab(old.id)
    switchTo(fresh)
    updateTabCount()
    refreshTiles()
    Toast.makeText(this, "Anonymní karta", Toast.LENGTH_SHORT).show()
}
