package com.example.actimelteambrowser

import android.graphics.Bitmap
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicInteger

/** Jedna otevřená karta: vlastní WebView + údaje pro přehled karet. */
class BrowserTab(val id: Long, webViewFactory: () -> WebView) {
    // WebView se vytvori az pri prvnim pouziti (obnovene karty na pozadi ho nemaji, setri se cas i pamet)
    private val lazyWebView = lazy(webViewFactory)
    val webView: WebView get() = lazyWebView.value
    val webViewCreated: Boolean get() = lazyWebView.isInitialized()
    /** Adresa z WebView, nebo prazdny text, kdyz jeste neexistuje. */
    fun liveUrl(): String = if (webViewCreated) webView.url.orEmpty() else ""
    @Volatile var pageUrl: String = ""            // adresa hlavní stránky (zdroj požadavků pro AdBlock)
    var url: String = ""                          // aktuální adresa karty
    var title: String = ""                        // titulek stránky
    var incognito: Boolean = false                // anonymni karta (bez historie, neuklada se)
    var startPage: Boolean = false                // nova karta ukazuje uvodni stranku (normalni / anonymni + nejnavstevovanejsi)
    var desktop: Boolean = false                  // verze pro pocitac (jiny user-agent)
    @Volatile var pendingUrl: String? = null      // obnovena karta, ktera se nacte az pri prvnim zobrazeni
    var preview: Bitmap? = null                   // náhled (screenshot) pro dlaždici
    var favicon: Bitmap? = null                   // ikona webu (favicon) - zobrazuje se v nasepavaci u aktualni stranky
    val blocked = AtomicInteger(0)                // počet zablokovaných požadavků na této stránce
}

/** Seznam otevřených karet a ID té aktivní. Používá se jen z UI vlákna. */
class BrowserTabs {
    val list = ArrayList<BrowserTab>()
    var activeId: Long = -1L
    private var nextId = 1L

    fun newId(): Long = nextId++

    fun active(): BrowserTab? = list.firstOrNull { it.id == activeId }
}
