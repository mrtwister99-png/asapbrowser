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

// AdBlock: pravidla, kosmetické filtry, YouTube, počítadlo a odmítání cookie lišt (rozšíření MainActivity).
// AdBlock je zapnuty a pro tenhle web neni vypnuta vyjimka (Informace o strance)
internal fun MainActivity.adBlockFor(url: String?): Boolean {
    if (!isAdBlockOn) return false
    val host = try { if (url.isNullOrBlank()) null else Uri.parse(url).host } catch (e: Exception) { null }
    return !SiteRules.adBlockOff(host)
}

// Vstrikne YouTube skript (vyrez reklam, skryti, preskoceni) - jen kdyz je AdBlock ON
internal fun MainActivity.injectYoutubeAdBlock(view: WebView?, url: String?) {
    if (!adBlockFor(url) || !AppSettings.adBlockYoutube.get() || view == null || !AdBlockRules.isYoutube(url)) return
    view.evaluateJavascript(AdBlockRules.youtubeScript, null)
}

// Vstrikne kosmeticke filtry do stranky (CSS pro domenu pocita Rust na pozadi)
internal fun MainActivity.injectCosmetic(view: WebView?, url: String?) {
    if (!adBlockFor(url) || !adblockReady || view == null || url == null || !isWebUrl(url)) return
    if (AdBlockRules.isYoutube(url)) return   // YouTube ma vlastni skript
    thread(name = "cosmetic") {
        val css = try { RustBridge.cosmeticCss(url) } catch (e: Throwable) { return@thread }
        runOnUiThread {
            // mezitim se mohla stranka zmenit
            val now = view.url ?: return@runOnUiThread
            if (Uri.parse(now).host != Uri.parse(url).host) return@runOnUiThread
            view.evaluateJavascript("window.__atbCss=" + JSONObject.quote(css) + ";" + cosmeticJs, null)
        }
    }
}

// Nacte EasyList + EasyPrivacy do Rust enginu na pozadi (pár sekund) - do te doby jedou jen Kotlin pravidla
internal fun MainActivity.initRustAdblock() {
    if (!RustBridge.isAvailable()) return   // chybi .so / 32bit telefon -> jen Kotlin pravidla
    thread(name = "adblock-init") {
        try {
            // stazena verze (filesDir) ma prednost pred tou v assets
            val list = ListUpdater.read(this, "easylist.txt") + "\n" + ListUpdater.read(this, "easyprivacy.txt")
            RustBridge.initAdblock(list)
            adblockReady = true
            android.util.Log.i("AdBlock", "Rust engine ready, v" + RustBridge.engineVersion())
            if (AppSettings.adBlockAutoUpdate.get()) ListUpdater.updateIfStale(this)   // stahne novejsi seznamy, pouziji se pri dalsim spusteni
        } catch (e: Throwable) {
            android.util.Log.e("AdBlock", "Rust init failed", e)
        }
    }
}

// Bezi na pozadi vlakne - nesmi se volat view.url
internal fun MainActivity.shouldBlockRequest(request: WebResourceRequest, tab: BrowserTab): Boolean {
    val url = request.url.toString()
    if (AdBlocker.isAd(url)) return true            // rychla Kotlin kontrola (puvodni chovani)
    if (request.isForMainFrame) {
        tab.pageUrl = url                           // zapamatuj stranku, ze ktere pozadavky jdou
        return false
    }
    if (!adblockReady) return false
    val source = if (tab.pageUrl.isNotEmpty()) tab.pageUrl else (request.requestHeaders["Referer"] ?: url)
    return try {
        RustBridge.shouldBlock(url, source, guessResourceType(request))
    } catch (e: Throwable) {
        false
    }
}

// Typ zdroje pro pravidla EasyListu (obrazek, skript, styl, iframe, video...)
internal fun MainActivity.guessResourceType(request: WebResourceRequest): String {
    val accept = request.requestHeaders["Accept"].orEmpty()
    val path = request.url.path.orEmpty().lowercase()
    return when {
        !request.isForMainFrame && accept.startsWith("text/html") -> "subdocument"   // reklamni iframy
        accept.contains("text/css") || path.endsWith(".css") -> "stylesheet"
        accept.contains("image") || IMAGE_EXTS.any { path.endsWith(it) } -> "image"
        path.endsWith(".js") -> "script"
        accept.contains("video") || accept.contains("audio") ||
            MEDIA_EXTS.any { path.endsWith(it) } -> "media"
        request.requestHeaders["X-Requested-With"] != null -> "xmlhttprequest"
        else -> "other"
    }
}

// Pocitadlo: vola se z pozadi vlakna, UI se obnovi max 1x za 300 ms
internal fun MainActivity.onRequestBlocked(tab: BrowserTab) {
    tab.blocked.incrementAndGet()
    val total = totalBlocked.incrementAndGet()
    if (total % 20 == 0) AppSettings.totalBlocked.set(total)
    if (badgeUpdatePending.compareAndSet(0, 1)) {
        webContainer.postDelayed({
            badgeUpdatePending.set(0)
            updateAdBlockBadge()
        }, 300)
    }
}

// Barva tlacitka, ikona a popisek podle stavu AdBlocku (vola se i pri startu)
internal fun MainActivity.applyAdBlockUi() {
    // kdyz ma tlacitko 7 jinou funkci nez AdBlock, zustane neutralni
    if (!isDefaultVisual(7)) {
        findViewById<View>(R.id.btnAdBlock).setBackgroundResource(R.drawable.btn_circle_dark)
        findViewById<ImageView>(R.id.ivAdBlock).clearColorFilter()
        return
    }
    findViewById<View>(R.id.btnAdBlock).setBackgroundResource(
        if (isAdBlockOn) R.drawable.btn_adblock_on else R.drawable.btn_circle_dark
    )
    findViewById<ImageView>(R.id.ivAdBlock).setColorFilter(
        if (isAdBlockOn) 0xFF06281D.toInt() else 0xFFFFFFFF.toInt()
    )
    updateAdBlockBadge()
}

// Popisek u stitu: OFF / ON / pocet zablokovanych (na teto strance)
internal fun MainActivity.updateAdBlockBadge() {
    val n = tabs.active()?.blocked?.get() ?: 0
    findViewById<TextView>(R.id.tvAdBlockState).text = when {
        !isAdBlockOn -> "OFF"
        n <= 0 -> "ON"
        n < 1000 -> n.toString()
        else -> (n / 1000).toString() + "k"
    }
}

// Auto-odmitnuti cookie listy - vice pokusu, lista se casto objevi az po nacteni
internal fun MainActivity.rejectCookies(view: WebView?) {
    if (!adBlockFor(view?.url) || !AppSettings.adBlockCookies.get() || view == null) return
    for (d in CookieReject.delaysMs) {
        view.postDelayed({ view.evaluateJavascript(CookieReject.script, null) }, d)
    }
}
