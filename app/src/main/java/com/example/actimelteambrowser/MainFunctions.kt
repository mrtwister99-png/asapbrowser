package com.example.actimelteambrowser

import android.util.Log
import android.os.SystemClock
import android.content.ClipboardManager
import android.content.ClipData
import androidx.core.net.toUri
import android.webkit.*
import kotlin.concurrent.thread
import com.example.actimelteambrowser.core.settings.AppSettings
import android.content.Intent
import com.example.actimelteambrowser.core.settings.SiteRules
import android.content.Context
import com.example.actimelteambrowser.core.settings.ButtonFunction

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

internal fun MainActivity.openSettingsAt(start: String?) {
    if (settingsOpen) return   // okno Nastaveni uz je otevrene / se otevira
    settingsOpen = true
    val i = settingsIntent()
    if (start != null) i.putExtra(SettingsActivity.EXTRA_START, start)
    settingsLauncher.launch(i)
}

internal fun MainActivity.toggleAdBlock() {
    if (isDemoRunning()) return   // behem ukazky AdBlock prepina jen ukazka
    isAdBlockOn = !isAdBlockOn
    AppSettings.adBlockOn.set(isAdBlockOn)
    applyAdBlockUi()
    AppToast.show(this, if (isAdBlockOn) "AdBlock zapnut" else "AdBlock vypnut")
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
        val host = runCatching { tab.webView.url?.toUri()?.host }.getOrNull()
        SiteRules.setDesktop(host, tab.desktop)
    }
    tab.webView.reload()
    updateDeskBtn()
    AppToast.show(this, if (tab.desktop) "Verze pro počítač (pamatuje se pro tento web)" else "Mobilní verze")
}

// Provede funkci tlacitka (klepnuti i drzeni jdou stejnou cestou)
@Suppress("DEPRECATION")
internal fun MainActivity.performFunction(fn: ButtonFunction) {
    if (isOpeningFunction(fn)) {
        val now = SystemClock.uptimeMillis()
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
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", curUrl))
            AppToast.show(this, "Adresa zkopírována")
        }
        ButtonFunction.ZOOM_IN -> webView.zoomIn()
        ButtonFunction.ZOOM_OUT -> webView.zoomOut()
        // --- karty
        ButtonFunction.TABS_OVERVIEW -> openTabsOverview()
        ButtonFunction.NEW_TAB -> {
            val active = tabs.active()
            val openNew = {
                if (active?.incognito == true) {
                    // v anonymnim rezimu se otevre nova anonymni karta
                    createTab(homeUrl, incognito = true)
                    AppToast.show(this, "Anonymní karta")
                } else {
                    openStartTab()
                    AppToast.show(this, "Nová karta")
                }
            }
            if (active != null) capturePreview(active) { openNew() } else openNew()
        }
        ButtonFunction.NEW_INCOGNITO -> {
            createTab(homeUrl, incognito = true)
            AppToast.show(this, "Anonymní karta")
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
                AppToast.show(this, "Tuhle stránku nelze uložit")
            } else {
                val title = webView.title.orEmpty()
                thread(name = "bookmark-quick") {
                    quiet {
                        bookmarksRepo.addBookmark(bookmarksRepo.defaultFolderId(), curUrl, title)
                    }
                    runOnUiThread { softBuzz(35); AppToast.show(this, "Přidáno do Oblíbených") }
                }
            }
        }
        ButtonFunction.ADD_TO_FOLDER -> addCurrentPageToFolder()
        ButtonFunction.OPEN_HISTORY -> openSettingsAt(SettingsActivity.START_HISTORY)
        ButtonFunction.FOCUS_SEARCH -> etSearchMain.requestFocus()
        ButtonFunction.HOME_SETTINGS -> showHomeDialog()
        // --- nastroje a system
        ButtonFunction.ADBLOCK_TOGGLE -> toggleAdBlock()
        ButtonFunction.VPN -> AppToast.show(this, "VPN je v rekonstrukci")
        ButtonFunction.OPEN_SETTINGS -> openSettingsAt(null)
        ButtonFunction.OPEN_BUTTONS -> enterConfigMode()
        ButtonFunction.EXPORT -> openSettingsAt(SettingsActivity.START_EXPORT)
        ButtonFunction.FEEDBACK -> openSettingsAt(SettingsActivity.START_FEEDBACK)
        ButtonFunction.IMPORT -> openSettingsAt(SettingsActivity.START_IMPORT)
        ButtonFunction.FIND_IN_PAGE -> showFindBar()   // vlastni panel (stejny vzhled jako v ukazce)
        ButtonFunction.GESTURES -> showGestures()
        ButtonFunction.LEFT_BAR -> toggleLeftBarAny()
        ButtonFunction.SCREENSHOT -> showScreenshotChooser()
        ButtonFunction.QUICK_SCREENSHOT -> captureFullPage()
        ButtonFunction.EXIT_APP -> finishAffinity()
    }
}

/** Tichý try/catch na jednom místě: chyba se spolkne, nevznikají nepoužité parametry `e`. */
internal inline fun quiet(block: () -> Unit) {
    runCatching(block)
}

/** Zalogování spolknuté chyby (používá se v catch blocích, kde chybu neřešíme). */
internal fun noteError(t: Throwable) {
    Log.d("quiet", t.toString())
}
