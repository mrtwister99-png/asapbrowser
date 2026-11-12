package com.example.actimelteambrowser

import android.util.Log
import androidx.core.net.toUri
import android.view.View
import android.webkit.*
import android.widget.*
import com.example.actimelteambrowser.core.adblock.AdBlocker
import com.example.actimelteambrowser.core.adblock.AdBlockRules
import com.example.actimelteambrowser.core.engine.RustBridge
import kotlin.concurrent.thread
import com.example.actimelteambrowser.core.adblock.CookieReject
import com.example.actimelteambrowser.core.adblock.ListUpdater
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.SiteRules
import org.json.JSONObject
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

// AdBlock: pravidla, kosmetické filtry, YouTube, počítadlo a odmítání cookie lišt (rozšíření MainActivity).
// AdBlock je zapnuty a pro tenhle web neni vypnuta vyjimka (Informace o strance)
internal fun MainActivity.adBlockFor(url: String?): Boolean {
    if (!isAdBlockOn) return false
    val host = runCatching { if (url.isNullOrBlank()) null else url.toUri().host }.getOrNull()
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
        val css = try { RustBridge.cosmeticCss(url) } catch (e: Throwable) { noteError(e); return@thread }
        runOnUiThread {
            // mezitim se mohla stranka zmenit
            val now = view.url ?: return@runOnUiThread
            if (now.toUri().host != url.toUri().host) return@runOnUiThread
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
            Log.i("AdBlock", "Rust engine ready, v" + RustBridge.engineVersion())
            if (AppSettings.adBlockAutoUpdate.get()) ListUpdater.updateIfStale(this)   // stahne novejsi seznamy, pouziji se pri dalsim spusteni
        } catch (e: Throwable) {
            Log.e("AdBlock", "Rust init failed", e)
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
    val source = tab.pageUrl.ifEmpty { request.requestHeaders["Referer"] ?: url }
    return try {
        RustBridge.shouldBlock(url, source, guessResourceType(request))
    } catch (e: Throwable) {
        noteError(e)
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
    val blue = accentState.value != 0xFF00B055.toInt()   // anonymni rezim: modre misto zelene
    val adBtn = findViewById<View>(R.id.btnAdBlock)
    if (isAdBlockOn && blue) {
        val g = GradientDrawable()
        g.setColor(accentState.value)
        g.cornerRadius = dp(18).toFloat()
        g.setStroke(dp(1), Color.WHITE)
        adBtn.background = g
    } else {
        adBtn.setBackgroundResource(if (isAdBlockOn) R.drawable.btn_adblock_on else R.drawable.btn_circle_dark)
    }
    findViewById<ImageView>(R.id.ivAdBlock).setColorFilter(
        if (isAdBlockOn) (if (blue) 0xFF001A40.toInt() else 0xFF06281D.toInt()) else 0xFFFFFFFF.toInt()
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
