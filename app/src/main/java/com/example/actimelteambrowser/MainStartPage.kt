package com.example.actimelteambrowser

import android.app.DownloadManager
import androidx.core.net.toUri
import android.webkit.*
import android.widget.*
import kotlin.concurrent.thread
import com.example.actimelteambrowser.core.settings.AppSettings
import androidx.compose.ui.platform.ComposeView
import android.graphics.Bitmap
import com.example.actimelteambrowser.ui.StartPage
import com.example.actimelteambrowser.ui.StartSite
import android.content.Context
import java.io.FileOutputStream
import android.os.Build
import android.view.ViewGroup

// Úvodní stránka nové karty, náhledy a ikony webů, stahování souborů (rozšíření MainActivity).
// Stahovani souboru ze stranky pres systemovy DownloadManager (notifikace, seznam v "Stahovani")
internal fun MainActivity.startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, wv: WebView? = null) {
    if (startSpecialDownload(url, contentDisposition, mimeType, wv)) return   // data: / blob: (PDF z tlacitka Stahnout apod.)
    if (!isWebUrl(url)) {
        AppToast.show(this, "Tenhle typ odkazu nejde stáhnout")
        return
    }
    runCatching {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val req = DownloadManager.Request(url.toUri())
        req.setMimeType(mimeType)
        cookiesFor(url, tabs.active()?.incognito == true)?.let { req.addRequestHeader("Cookie", it) }
        if (!userAgent.isNullOrBlank()) req.addRequestHeader("User-Agent", userAgent)
        req.setTitle(name)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // vybrana slozka: DownloadManager do ni psat neumi -> stahne do slozky appky a po dokonceni se soubor presune
        val folder = com.example.actimelteambrowser.data.DownloadsStore.folder(this)
        if (folder != null) {
            req.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, name)
        // Android 10+: verejna slozka Stazene; starsi: slozka appky (bez nutnosti povoleni uloziste)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        } else {
            req.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, name)
        }
        val id = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        // anonymni karta: soubor se stahne, ale zaznam v Stazenych se neulozi (zmizi po restartu appky); cookies jdou dal jen na stahovany server
        com.example.actimelteambrowser.data.DownloadsStore.track(this, id, name, folder?.toString(), temp = tabs.active()?.incognito == true)
        startDownloadPoll()
        AppToast.show(this, "Stahuji: $name")
    }.onFailure {
        AppToast.show(this, "Stahování se nepovedlo")
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
                blockedTotal = startBlockedTotal.intValue,
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
    startBlockedTotal.intValue = totalBlocked.get()
    startShowFav.value = AppSettings.startShowFavorites.get()
    loadStartSites()
}

// Nejnavstevovanejsi weby z historie (po domenach); kdyz jich je malo, doplni se obvyklymi
internal fun MainActivity.loadStartSites() {
    com.example.actimelteambrowser.core.bg {
        val result = ArrayList<StartSite>()
        try {
            val perHost = LinkedHashMap<String, Int>()
            for (m in historyRepo.mostVisited(300)) {
                val host = m.url.toUri().host?.removePrefix("www.") ?: continue
                if (host.isBlank()) continue
                perHost[host] = (perHost[host] ?: 0) + m.visitCount
            }
            for ((host, _) in perHost.entries.sortedByDescending { it.value }.take(12)) {
                result.add(StartSite(host, "https://$host/", loadSiteThumb(host), loadSiteIcon(host)))
            }
        } catch (e: Exception) {
            noteError(e)
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
                val h = bm.url.toUri().host?.removePrefix("www.") ?: continue
                favs.add(StartSite(bm.title.ifBlank { h }, bm.url, loadSiteThumb(h), loadSiteIcon(h)))
            }
        } catch (e: Exception) {
            noteError(e)
        }
        runOnUiThread {
            startSites.value = result
            startFavorites.value = favs
        }
    }
}

internal fun MainActivity.saveSiteIcon(host: String, icon: Bitmap) {
    if (!AppSettings.thumbsOn.get()) return   // ukladani nahledu a ikon je vypnute v Konfiguraci
    val copy = runCatching { icon.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
    com.example.actimelteambrowser.core.bg {
        quiet {
            val f = siteIconFile(host)
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { copy.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
    val host = runCatching { (tab.liveUrl().ifBlank { tab.url }).toUri().host }.getOrNull()?.removePrefix("www.") ?: return
    if (host.isBlank() || !thumbStale(host)) return   // cerstvy nahled uz mame
    com.example.actimelteambrowser.core.bg {
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
    flyBurst("+", "#2979FF")   // modre + pri nove anonymni karte
    AppToast.show(this, "Anonymní karta")
}
