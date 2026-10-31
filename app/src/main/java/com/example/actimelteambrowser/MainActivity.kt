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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val INCOGNITO_PROFILE = "anonymni"
        private const val INTRO_ENABLED = true   // uvodni animace s logem a hudbou (vypnuti: false)
    }

    internal lateinit var webView: WebView            // WebView aktivni karty
    internal lateinit var webContainer: FrameLayout   // sem se vklada WebView aktivni karty
    private lateinit var tabsView: ComposeView       // prehled karet (dlazdice)
    internal lateinit var gestureView: ComposeView    // plocha gest (pruhledna)
    internal val externalTouch = mutableStateOf<ExternalTouch?>(null)   // dotyk pokracujici z tlacitka 6
    internal var externalDown: ExternalTouch? = null
    private var extSeq = 0L
    private var overviewIncognito = false            // prehled karet ukazuje anonymni (true) nebo bezne karty
    internal var lastSystemBottom = 0
    internal val tabs = BrowserTabs()
    private val tabTiles = mutableStateOf<List<TabTile>>(emptyList())

    // Slozky v prehledu karet: skupina karet (id karet), v prehledu se ukaze jako jedna dlazdice na miste prvni karty
    private class TabGroup(val id: Long, val members: MutableList<Long>, var color: Int = 0xFFCDA24D.toInt())
    private val groups = ArrayList<TabGroup>()
    private var nextGroupId = 1L

    // Pocet reklam zablokovanych celkem (uvodni stranka); do nastaveni se uklada po dvaceti a pri pauze
    internal val totalBlocked = java.util.concurrent.atomic.AtomicInteger(0)
    private val startBlockedTotal = mutableStateOf(0)
    internal lateinit var etSearchMain: EditText
    internal lateinit var etTopUrl: EditText
    internal lateinit var bottomBar: View
    private lateinit var topBar: View
    private lateinit var progressBar: ProgressBar
    internal lateinit var root: View

    private var isTopBarExpanded = false
    @Volatile internal var isAdBlockOn = true
    @Volatile internal var adblockReady = false   // Rust engine nacetl EasyList
    internal val badgeUpdatePending = AtomicInteger(0)
    private var isVpnOn = false

    // Historie, zalozky a nasepavac
    private val historyRepo by lazy { HistoryRepository(this) }
    private val bookmarksRepo by lazy { BookmarksRepository(this) }
    private val suggestions = mutableStateOf<List<SearchSuggestion>>(emptyList())
    private val suggestionsAtTop = mutableStateOf(false)   // true = seznam se ukazuje pod hornim polem
    private val pinnedPage = mutableStateOf<SearchSuggestion?>(null)   // aktualni stranka: prvni radek pri prazdnem poli
    private val suggestionsCompact = mutableStateOf(false)             // prazdne pole: nejnavstevovanejsi jen 3 radky (scroll)
    @Volatile private var suggestionToken = 0
    private var lastRecordedUrl = ""
    private var tabCountBg: GradientDrawable? = null   // ramecek ctverecku s poctem karet
    private var tabsAnimating = false
    private val closedUrls = ArrayList<String>()           // naposledy zavrene karty (pro "vratit zavrenou")
    private val extraViews = HashMap<Int, View>()          // pridana boci tlacitka: cislo (9-12) -> view
    private var lastImeShift = 0f
    private var searchBg: GradientDrawable? = null          // pozadi spodniho pole (#D9D9D9 + pulzujici obrys)
    private var urlBg: GradientDrawable? = null             // pozadi horniho pole
    private var pulseAnim: ValueAnimator? = null            // pulzovani zeleneho obrysu poli
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenHolder: FrameLayout? = null
    private var sideBarLeft: View? = null
    private var leftDeskBtn: View? = null                   // tlacitko "verze pro pocitac" v leve liste
    private var barOrigin = 5                                // tlacitko, ze ktereho lista vyjela (5 = vlevo, 6 = vpravo)
    private var pressedOrigin = 0                           // tlacitko 5/6 prave stisknute (pro funkci "Bocni lista")
    internal var leftBarOpen = false                         // levá lišta (tlačítko 5) je otevřená
    private var leftBarAnim: ValueAnimator? = null
    private var blinkAnim: ValueAnimator? = null           // blikani prave pridaneho tlacitka
    internal var orbView: GestureOrbView? = null            // zivá kulicka tlacitka 6 (gesta)
    private val sideHide = HashMap<Int, Runnable>()         // casovace schovani bocnich tlacitek
    private var starAnimating = false
    private var homeAnimating = false
    private var syncingText = false   // zabrani zacykleni pri zrcadleni textu mezi poli

    // Okno Nastaveni (Compose) - vrati adresu, kterou uzivatel vybral (zalozka / historie)
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            settingsOpen = false
            // po navratu na hlavni plochu se znovu prehraje animace kolecko -> logo (spusti ji onResume)
            introOnResume = true
            // Nastaveni -> Tlacitka: hlavni plocha prejde do konfiguracniho modu
            if (r.data?.getBooleanExtra(SettingsActivity.EXTRA_CONFIG_BUTTONS, false) == true) enterConfigMode()
            // Nastaveni -> Gesta: plocha gest + panel shora
            if (r.data?.getBooleanExtra(SettingsActivity.EXTRA_CONFIG_GESTURES, false) == true) enterGestureConfig()
            val url = r.data?.getStringExtra(SettingsActivity.EXTRA_URL)
            if (r.resultCode == RESULT_OK && url != null && isWebUrl(url)) webView.loadUrl(url)
        }
    private val homeUrl: String get() = AppSettings.homeUrl.get()   // domovska stranka z nastaveni

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // nacist ulozena nastaveni
        AppSettings.init(this)
        isAdBlockOn = AppSettings.adBlockOn.get()
        isVpnOn = AppSettings.vpnOn.get()

        root = findViewById(R.id.root)
        webContainer = findViewById(R.id.webContainer)
        purgeIncognito()   // zbytky anonymniho profilu po padu appky
        if (savedInstanceState == null) startIntro()
        tabsView = findViewById(R.id.tabsView)
        gestureView = findViewById(R.id.gestureView)
        etSearchMain = findViewById(R.id.etSearchMain)
        etTopUrl = findViewById(R.id.etTopUrl)
        bottomBar = findViewById(R.id.bottomBar)
        topBar = findViewById(R.id.topBar)
        progressBar = findViewById(R.id.progressBar)

        // FIX prekryvani + klavesnice
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // topBar ma padding podle status baru
            topBar.setPadding(8, systemBars.top + 8, 8, 8)
            // prehled karet: insety jsou na root "spotrebovane", proto odsazeni od systemovych listu dame sem
            tabsView.setPadding(0, systemBars.top, 0, systemBars.bottom)
            gestureView.setPadding(0, systemBars.top, 0, systemBars.bottom)
            lastSystemBottom = systemBars.bottom
            // web je mezi listami: nahore konci horni lista (status bar + 16 px + 52 dp), dole zacina spodni (nav bar + 16 px + 48 dp)
            webContainer.setPadding(0, systemBars.top + 16 + dp(52), 0, systemBars.bottom + 16 + dp(48))
            sideBarLeft?.visibility = if (imeVisible || !leftBarOpen) View.INVISIBLE else View.VISIBLE

            // bottomBar - kdyz je klavesnice, vysun nad ni
            lastImeShift = if (imeVisible) -(ime.bottom - systemBars.bottom).toFloat() else 0f
            for (v in extraViews.values) v.translationY = lastImeShift
            if (imeVisible) {
                bottomBar.translationY = -(ime.bottom - systemBars.bottom).toFloat()
                // bocni taky nad klavesnici
                findViewById<View>(R.id.btnSideLeftMain).translationY = -(ime.bottom - systemBars.bottom).toFloat()
                findViewById<View>(R.id.btnSideRightMain).translationY = -(ime.bottom - systemBars.bottom).toFloat()
                findViewById<View>(R.id.suggestionsView).translationY = -(ime.bottom - systemBars.bottom).toFloat()
            } else {
                bottomBar.translationY = 0f
                findViewById<View>(R.id.btnSideLeftMain).translationY = 0f
                findViewById<View>(R.id.btnSideRightMain).translationY = 0f
                findViewById<View>(R.id.suggestionsView).translationY = 0f
                bottomBar.setPadding(8, 8, 8, systemBars.bottom + 8)
            }

            WindowInsetsCompat.CONSUMED
        }

        initRustAdblock()
        rebuildExtraButtons()
        setupBars()
        setupGestureOrb()
        setupSideBar()
        // pisu dole -> navrhy nad spodni listou; pisu nahore -> navrhy pod hornim polem (shora dolu)
        findViewById<ComposeView>(R.id.suggestionsView).setContent {
            SearchSuggestionsList(
                suggestions = if (suggestionsAtTop.value) emptyList() else suggestions.value,
                pinned = if (suggestionsAtTop.value) null else pinnedPage.value,
                pinnedAtBottom = true,   // u dolniho pole je aktualni stranka dole = nejblíž poli
                compact = suggestionsCompact.value,
                onCopy = { copyPinnedUrl(it) },
                onPinned = { dismissInput() },
                onEdit = { editCurrentUrl() }
            ) { picked -> openSuggestion(picked.url) }
        }
        findViewById<ComposeView>(R.id.suggestionsTopView).setContent {
            SearchSuggestionsList(
                suggestions = if (suggestionsAtTop.value) suggestions.value else emptyList(),
                pinned = if (suggestionsAtTop.value) pinnedPage.value else null,
                compact = suggestionsCompact.value,
                onCopy = { copyPinnedUrl(it) },
                onPinned = { dismissInput() },
                onEdit = { editCurrentUrl() }
            ) { picked -> openSuggestion(picked.url) }
        }
        updateButtonVisuals()
        applyAdBlockUi()
        findViewById<TextView>(R.id.tvVpnState).text = "OFF"
        setupTabsOverlay()
        setupBackHandling()
        // po spusteni: obnovit posledni karty, nebo zacit jednou novou kartou (podle nastaveni)
        if (AppSettings.startupRestoreTabs.get()) restoreTabs() else createTab(homeUrl)
        warmUpDns()
    }

    // Predem rozresi DNS domovske stranky a obnovenych karet (na pozadi), prvni nacteni je pak rychlejsi.
    // WebView nema pravy "preconnect", tohle je nejblizsi bezpecna vec.
    private fun warmUpDns() {
        val urls = ArrayList<String>()
        urls.add(homeUrl)
        for (t in tabs.list) {
            if (t.incognito) continue
            (t.pendingUrl ?: t.url).takeIf { it.isNotBlank() }?.let { urls.add(it) }
        }
        thread(name = "dns-warmup") {
            val hosts = urls.mapNotNull { try { Uri.parse(it).host } catch (e: Exception) { null } }.distinct().take(8)
            for (h in hosts) {
                try { java.net.InetAddress.getAllByName(h) } catch (e: Exception) { }
            }
        }
    }

    // Stahovani souboru ze stranky pres systemovy DownloadManager (notifikace, seznam v "Stahovani")
    private fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
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
            val id = (getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
            com.example.actimelteambrowser.data.DownloadsStore.track(this, id, name)
            startDownloadPoll()
            Toast.makeText(this, "Stahuji: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Stahování se nepovedlo", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- UVODNI STRANKA NOVE KARTY ----------------

    private val startSites = mutableStateOf<List<StartSite>>(emptyList())
    private val startFavorites = mutableStateOf<List<StartSite>>(emptyList())
    private val startShowFav = mutableStateOf(false)
    private var startPageView: ComposeView? = null

    // Nova karta s uvodni strankou (normalni / anonymni + nejnavstevovanejsi stranky)
    private fun openStartTab() {
        val t = createTab(null)
        t.startPage = true
        t.title = "Nová karta"
        updateStartPage()
    }

    // Ukaze / schova uvodni stranku podle aktivni karty (je pres WebView v kontejneru)
    private fun updateStartPage() {
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
    private fun loadStartSites() {
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

    // Nahledy nejnavstevovanejsich webu: filesDir/site_thumbs/<domena>.jpg (uklada se pri opusteni / nacteni stranky)
    private fun siteThumbFile(host: String): File =
        File(File(filesDir, "site_thumbs"), host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".jpg")

    private fun loadSiteThumb(host: String): Bitmap? = if (AppSettings.thumbsOn.get()) loadPreview(siteThumbFile(host)) else null

    // Ikony webu (favicony): filesDir/site_icons/<domena>.png
    private fun siteIconFile(host: String): File =
        File(File(filesDir, "site_icons"), host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    private fun loadSiteIcon(host: String): Bitmap? = if (AppSettings.thumbsOn.get()) loadPreview(siteIconFile(host)) else null

    private fun saveSiteIcon(host: String, icon: Bitmap) {
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
    private fun thumbStale(host: String): Boolean {
        val f = siteThumbFile(host)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > 3L * 24 * 3600 * 1000
    }

    // Ulozi nahled prave zachycene stranky pro jeji domenu (jen bezne karty)
    private fun saveSiteThumb(tab: BrowserTab) {
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

    private fun startPageOpen(url: String) {
        val tab = tabs.active() ?: return
        tab.startPage = false
        updateStartPage()
        webView.loadUrl(url)
    }

    private fun startPageNormal() = startPageOpen(homeUrl)

    // Anonymni: nova anonymni karta na miste teto (puvodni uvodni karta se zavre)
    private fun startPageIncognito() {
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

    // ---------------- DLOUHE PODRZENI NA STRANCE (odkaz / obrazek) ----------------

    private fun onWebLongPress(wv: WebView): Boolean {
        val hit = wv.hitTestResult
        val extra = hit.extra
        when (hit.type) {
            WebView.HitTestResult.IMAGE_TYPE -> {
                if (extra.isNullOrBlank()) return false
                showImageMenu(extra)
                return true
            }
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // adresu odkazu a jeho text da WebView jen pres zpravu
                val imageSrc = if (hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) extra else null
                val handler = Handler(Looper.getMainLooper()) { m ->
                    val url = m.data.getString("url") ?: extra
                    val text = m.data.getString("title")
                    if (!url.isNullOrBlank()) showLinkMenu(url, text, imageSrc)
                    true
                }
                wv.requestFocusNodeHref(handler.obtainMessage())
                return true
            }
        }
        return false   // ostatni (text, prazdne misto) nechat na systemu
    }

    private fun openInNewTab(url: String, incognito: Boolean) {
        if (!isWebUrl(url)) {
            Toast.makeText(this, "Tuhle adresu nelze otevřít", Toast.LENGTH_SHORT).show()
            return
        }
        val active = tabs.active()
        val open = { createTab(url, incognito = incognito); Unit }
        if (active != null) capturePreview(active) { open() } else open()
    }

    private fun copyToClipboard(label: String, text: String, msg: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, title))
    }

    // Radek nabidky: kratky popisek, podtitulek (adresa), ikona (0 = bez ikony) a akce
    private class MenuRow(val label: String, val sub: String, val icon: Int, val highlight: Boolean = false, val action: () -> Unit)

    // Nabidka s ikonami (tmavy styl appky)
    private fun showIconMenu(title: String, rows: List<MenuRow>) {
        showStyledDialog(title = title, negativeText = "Zrušit") { dismiss ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            for (r in rows) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(14), dp(11), dp(14), dp(11))
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor(if (r.highlight) "#00B055" else "#2A2A2A"))   // zvyraznena nejcastejsi akce
                bg.cornerRadius = dp(14).toFloat()
                row.background = bg
                val iv = ImageView(this)
                if (r.icon != 0) iv.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(this, r.icon))
                row.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                val tv = TextView(this)
                tv.text = r.label
                tv.setTextColor(Color.WHITE)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                tv.setTypeface(tv.typeface, Typeface.BOLD)
                col.addView(tv)
                if (r.sub.isNotBlank()) {
                    val sub = TextView(this)
                    sub.text = r.sub
                    sub.setTextColor(Color.parseColor(if (r.highlight) "#D7FFE8" else "#888888"))
                    sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    sub.maxLines = 1
                    sub.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    col.addView(sub)
                }
                val clp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                clp.marginStart = dp(14)
                row.addView(col, clp)
                row.setOnClickListener {
                    dismiss()
                    r.action()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8)
                box.addView(row, lp)
            }
            val scroll = ScrollView(this)
            scroll.addView(box)
            scroll.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scroll
        }
    }

    // Nabidka po dlouhem podrzeni na odkazu: kratke nazvy, pod nimi adresa
    private fun showLinkMenu(url: String, text: String?, imageSrc: String?) {
        val addr = com.example.actimelteambrowser.ui.prettyUrl(url)
        val rows = ArrayList<MenuRow>()
        rows.add(MenuRow("Nová karta", addr, R.drawable.ic_new_tab, true) { openInNewTab(url, false) })
        rows.add(MenuRow("Anonymní karta", addr, R.drawable.ic_incognito) { openInNewTab(url, true) })
        rows.add(MenuRow("Uložit do Oblíbených", addr, R.drawable.ic_quick_favorite) { saveLinkToFavorites(url, text) })
        if (!text.isNullOrBlank()) {
            rows.add(MenuRow("Kopírovat text", text.trim(), R.drawable.ic_copy_url) { copyToClipboard("text", text.trim(), "Text zkopírován") })
        }
        rows.add(MenuRow("Kopírovat adresu", addr, R.drawable.ic_copy_url) { copyToClipboard("url", url, "Adresa zkopírována") })
        rows.add(MenuRow("Sdílet", addr, R.drawable.ic_share_url) { shareText(url, "Sdílet odkaz") })
        if (!imageSrc.isNullOrBlank()) {
            rows.add(MenuRow("Obrázek", com.example.actimelteambrowser.ui.prettyUrl(imageSrc), 0) { showImageMenu(imageSrc) })
        }
        showIconMenu("Odkaz", rows)
    }

    // Ulozi odkaz (bez otevreni) do vychozi slozky Oblibene
    private fun saveLinkToFavorites(url: String, text: String?) {
        if (!isWebUrl(url)) {
            Toast.makeText(this, "Tenhle odkaz nelze uložit", Toast.LENGTH_SHORT).show()
            return
        }
        thread(name = "bookmark-link") {
            try {
                bookmarksRepo.addBookmark(bookmarksRepo.defaultFolderId(), url, text?.trim().orEmpty())
            } catch (e: Exception) {
            }
            runOnUiThread { Toast.makeText(this, "Přidáno do Oblíbených", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showImageMenu(src: String) {
        val addr = com.example.actimelteambrowser.ui.prettyUrl(src)
        showIconMenu(
            "Obrázek",
            listOf(
                MenuRow("Nová karta", addr, R.drawable.ic_new_tab, true) { openInNewTab(src, false) },
                MenuRow("Stáhnout", addr, R.drawable.ic_import) { startDownload(src, webView.settings.userAgentString, null, null) },
                MenuRow("Sdílet", addr, R.drawable.ic_share_url) { shareImage(src) }
            )
        )
    }

    // Obrazek se stahne do cache (na pozadi) a nabidne se sdileni jako soubor; pri chybe se sdili aspon adresa
    private fun shareImage(src: String) {
        if (!isWebUrl(src)) {
            Toast.makeText(this, "Tenhle obrázek nejde sdílet", Toast.LENGTH_SHORT).show()
            return
        }
        val ua = webView.settings.userAgentString
        val cookie = cookiesFor(src, tabs.active()?.incognito == true)
        Toast.makeText(this, "Připravuji obrázek…", Toast.LENGTH_SHORT).show()
        thread(name = "share-image") {
            try {
                val conn = java.net.URL(src).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", ua)
                if (cookie != null) conn.setRequestProperty("Cookie", cookie)
                val mime = (conn.contentType ?: "image/jpeg").substringBefore(';').trim()
                val ext = when (mime) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "jpg" }
                val dir = File(cacheDir, ExportFileProvider.DIR)
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "obrazek-" + System.currentTimeMillis() + "." + ext)
                var total = 0L
                conn.inputStream.use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > 20L * 1024 * 1024) throw IllegalStateException("Obrázek je moc velký")
                            out.write(buf, 0, n)
                        }
                    }
                }
                val uri = ExportFileProvider.uriFor(this, f.name)
                val send = Intent(Intent.ACTION_SEND)
                send.type = if (mime.startsWith("image/")) mime else "image/jpeg"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.clipData = android.content.ClipData.newRawUri("", uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runOnUiThread { startActivity(Intent.createChooser(send, "Sdílet obrázek")) }
            } catch (e: Exception) {
                runOnUiThread { shareText(src, "Sdílet adresu obrázku") }
            }
        }
    }

    // ---------------- UVODNI ANIMACE ----------------

    // Uvodni animace: bile pozadi, sede logo se zviditelni, glitch do plneho loga (viz IntroView), hudba bez_zastaveni.mp3.
    // Tlacitko Preskocit je vpravo dole. Po skonceni (nebo preskoceni) hudba doznije a ukaze se appka.
    // Vypnuti: INTRO_ENABLED = false, nebo v Konfiguraci.
    private fun startIntro() {
        // uvodni animace jde vypnout v Konfiguraci (pak se rovnou ukaze appka)
        if (!INTRO_ENABLED || !AppSettings.introOn.get() || !AppSettings.animationsOn.get()) { playSettingsIntro(); return }
        val host = root as ViewGroup
        val layer = FrameLayout(this)
        layer.setBackgroundColor(Color.WHITE)
        layer.isClickable = true
        layer.elevation = dp(120).toFloat()
        val iv = IntroView(this)
        layer.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val skip = TextView(this)
        skip.text = "Přeskočit  ›"
        skip.setTextColor(Color.parseColor("#222222"))
        skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        skip.setTypeface(skip.typeface, Typeface.BOLD)
        skip.setPadding(dp(16), dp(10), dp(16), dp(10))
        skip.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.parseColor("#1A000000"))
        }
        val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        slp.bottomMargin = dp(56)
        slp.rightMargin = dp(16)
        layer.addView(skip, slp)
        introPlayer = startIntroMusic()
        var done = false
        fun finish() {
            if (done) return
            done = true
            iv.stop()
            fadeOutIntroMusic()
            layer.animate().alpha(0f).setDuration(300).withEndAction {
                host.removeView(layer)
                playSettingsIntro()
            }.start()
        }
        skip.setOnClickListener { finish() }
        layer.postDelayed({ finish() }, IntroView.DURATION_MS + 1700)   // po animaci zustane plne logo 1,7 s (hudba hraje do konce)
        host.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // Hudba k uvodni animaci: res/raw/bez_zastaveni.mp3 (kdyz chybi nebo jsou zvuky vypnute, je ticho)
    private var introPlayer: android.media.MediaPlayer? = null

    private fun startIntroMusic(): android.media.MediaPlayer? {
        if (!AppSettings.soundsOn.get()) return null
        return try {
            val resId = resources.getIdentifier("bez_zastaveni", "raw", packageName)
            if (resId == 0) return null
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val mp = android.media.MediaPlayer.create(applicationContext, resId, attrs, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
            mp?.setOnCompletionListener { it.release(); if (introPlayer === it) introPlayer = null }
            mp?.start()
            mp
        } catch (e: Exception) {
            null   // hudba je jen bonus, nesmi shodit appku
        }
    }

    // Hudba se na konci animace (nebo po Preskocit) za 0,4 s ztlumi a vypne
    private fun fadeOutIntroMusic() {
        val mp = introPlayer ?: return
        introPlayer = null
        val a = ValueAnimator.ofFloat(1f, 0f).setDuration(400)
        a.addUpdateListener { try { mp.setVolume(it.animatedValue as Float, it.animatedValue as Float) } catch (e: Exception) { } }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                try { mp.stop() } catch (e: Exception) { }
                try { mp.release() } catch (e: Exception) { }
            }
        })
        a.start()
    }

    // ---------------- PRUBEH STAHOVANI NA TLACITKACH ----------------

    private val dlHandler = Handler(Looper.getMainLooper())
    private var dlPolling = false
    private val dlFills = HashMap<Int, DownloadFillDrawable>()      // cislo tlacitka -> plnici vrstva
    private var dlActiveIds = HashSet<Long>()

    // Dokud neco stahujeme, ~1,4x za sekundu se zjisti stav a prekresli plneni tlacitek.
    // Zadna souvisla animace: kazde tlacitko dostane jen novou vysku zelene vrstvy.
    private fun startDownloadPoll() {
        if (dlPolling) return
        dlPolling = true
        dlHandler.post(object : Runnable {
            override fun run() {
                thread(name = "dl-poll") {
                    val items = try { com.example.actimelteambrowser.data.DownloadsStore.query(this@MainActivity) } catch (e: Exception) { emptyList() }
                    runOnUiThread {
                        val active = applyDownloadFill(items)
                        if (active) dlHandler.postDelayed(this, 700) else dlPolling = false
                    }
                }
            }
        })
    }

    // 1. probihajici stahovani plni tlacitko 1, 2. tlacitko 2 atd. (0 -> 100 % zdola nahoru).
    // Vraci true, kdyz neco dal bezi.
    private fun applyDownloadFill(items: List<com.example.actimelteambrowser.data.DlItem>): Boolean {
        val running = items.filter {
            it.status == android.app.DownloadManager.STATUS_RUNNING ||
                it.status == android.app.DownloadManager.STATUS_PENDING ||
                it.status == android.app.DownloadManager.STATUS_PAUSED
        }.sortedBy { it.id }   // od nejstarsiho
        // hotova stahovani: kratka zprava
        val nowIds = running.map { it.id }.toHashSet()
        for (id in dlActiveIds) {
            if (id in nowIds) continue
            items.firstOrNull { it.id == id }?.let {
                if (it.status == android.app.DownloadManager.STATUS_SUCCESSFUL) Toast.makeText(this, "Staženo: " + it.name, Toast.LENGTH_SHORT).show()
            }
        }
        dlActiveIds = nowIds
        // tlacitka, ktera lze plnit: zobrazena a ne ta s kulickou
        val orbN = gestureButtonN()
        val targets = (1..12).filter { AppSettings.isEnabled(it) && buttonView(it) != null && it != orbN }
        val used = HashSet<Int>()
        for ((i, d) in running.withIndex()) {
            val n = targets.getOrNull(i) ?: break
            val v = buttonView(n) ?: continue
            used.add(n)
            val fill = dlFills.getOrPut(n) {
                DownloadFillDrawable(dp(16).toFloat()).also { v.foreground = it }
            }
            if (v.foreground !== fill) v.foreground = fill
            fill.fraction = if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f)
            else 0.15f + 0.15f * (0.5f + 0.5f * kotlin.math.sin(System.currentTimeMillis() / 400.0).toFloat())   // velikost neznama
        }
        // ostatni tlacitka se vycisti
        val iter = dlFills.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key !in used) {
                buttonView(e.key)?.foreground = null
                iter.remove()
            }
        }
        return running.isNotEmpty()
    }


    // Jednorazova animace tlacitka Nastaveni po spusteni: ozubene kolecko se protoci a promeni v logo appky.
    // Hraje se jen kdyz ma tlacitko vychozi funkci (jinak tam je ikona vlastni funkce).
    private fun playSettingsIntro() {
        val btn = findViewById<ImageView>(R.id.btnSettings)
        if (buttonFunction(4, false) != ButtonDefaults.tap(4) || !AppSettings.animationsOn.get()) return
        val gearTint = Color.parseColor("#1E1E1E")   // kolecko je bile, na svetlem tlacitku by nebylo videt
        var swapped = false
        btn.setImageResource(R.drawable.ic_settings)
        btn.setColorFilter(gearTint)
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1100
        a.interpolator = android.view.animation.DecelerateInterpolator(1.6f)
        a.addUpdateListener {
            val p = it.animatedValue as Float
            btn.rotation = 720f * p                    // dve otocky, na konci zpomali
            if (p < 0.5f) {
                btn.alpha = 1f - p * 2f * 0.9f          // kolecko pomalu bledne
            } else {
                if (!swapped) {
                    swapped = true
                    btn.clearColorFilter()
                    btn.setImageResource(R.drawable.asap_logo)
                }
                btn.alpha = 0.1f + (p - 0.5f) * 2f * 0.9f   // logo se objevuje
            }
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                btn.rotation = 0f
                btn.alpha = 1f
                btn.clearColorFilter()
                btn.setImageResource(R.drawable.asap_logo)
            }
        })
        a.start()
    }

    private var settingsOutroRunning = false

    // Klepnuti na tlacitko Nastaveni: logo se otoci a promeni v kolecko (opak uvodni animace), pak se otevre Nastaveni
    private fun playSettingsOutro(onDone: () -> Unit) {
        if (settingsOutroRunning) return
        if (!AppSettings.animationsOn.get()) { onDone(); return }   // animace vypnute
        settingsOutroRunning = true
        val btn = findViewById<ImageView>(R.id.btnSettings)
        val gearTint = Color.parseColor("#1E1E1E")
        var swapped = false
        btn.clearColorFilter()
        btn.setImageResource(R.drawable.asap_logo)
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 600
        a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            val p = it.animatedValue as Float
            btn.rotation = 360f * p
            if (p < 0.5f) {
                btn.alpha = 1f - p * 2f * 0.9f
            } else {
                if (!swapped) {
                    swapped = true
                    btn.setImageResource(R.drawable.ic_settings)
                    btn.setColorFilter(gearTint)
                }
                btn.alpha = 0.1f + (p - 0.5f) * 2f * 0.9f
            }
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                btn.rotation = 0f
                btn.alpha = 1f
                settingsOutroRunning = false
                onDone()
                // kdyz se Nastaveni neotevrelo (zamek proti dvojitemu otevreni), vrati se logo
                btn.postDelayed({
                    if (!settingsOpen) {
                        btn.clearColorFilter()
                        btn.setImageResource(R.drawable.asap_logo)
                    }
                }, 900)
            }
        })
        a.start()
    }

    private fun setupBars() {
        setupFieldBackgrounds()
        // barva oznaceneho textu v polich (vcetne doplneneho "youtube.com"): #0099FF, lehce pruhledna
        val selColor = Color.parseColor("#660099FF")
        etSearchMain.highlightColor = selColor
        etTopUrl.highlightColor = selColor
        // TOP BAR - 75% viditelna, 1/4 za hranou - klepni pro vysunuti
        topBar.setOnClickListener { toggleTopBar() }
        findViewById<View>(R.id.urlContainer).setOnClickListener { toggleTopBar() }
        findViewById<View>(R.id.ivUrlLock).setOnClickListener { showPageInfo() }
        findViewById<View>(R.id.ivUrlShare).setOnClickListener { showShareDialog() }

        // Enter v hornim poli: stejne jako dole (adresa nebo hledani)
        etTopUrl.setOnEditorActionListener { _, _, _ ->
            submitInput(etTopUrl.text.toString())
            true
        }

        // Kdyz kliknu na listu = vysune nad klavesnici
        bottomBar.setOnClickListener {
            etSearchMain.requestFocus()
            showKeyboard()
        }
        etSearchMain.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // zajisti ze je videt nad klavesnici - insets listener to uz posune
                bottomBar.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
                startFreshInput(etSearchMain)
            } else {
                bottomBar.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                onInputFocusLost()
            }
        }
        etTopUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) startFreshInput(etTopUrl) else onInputFocusLost()
        }
        etSearchMain.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (prefillingUrl) return
                if (s != null && etSearchMain.hasFocus()) autoComplete(etSearchMain, s)
                mirrorText(etSearchMain, etTopUrl)
                if (etSearchMain.hasFocus()) updateSuggestions(s?.toString().orEmpty())
            }
        })
        // psani nahore se zrcadli dole
        etTopUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (prefillingUrl) return
                if (s != null && etTopUrl.hasFocus()) autoComplete(etTopUrl, s)
                mirrorText(etTopUrl, etSearchMain)
                if (etTopUrl.hasFocus()) updateSuggestions(s?.toString().orEmpty())
            }
        })
        etSearchMain.setOnEditorActionListener { _, _, _ ->
            submitInput(etSearchMain.text.toString())
            true
        }

        // KARTY: klik = prehled otevrenych karet, drzeni = nova karta
        val density = resources.displayMetrics.density
        tabCountBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6 * density
            setStroke((2 * density).toInt(), Color.WHITE)
            setColor(Color.TRANSPARENT)
        }
        findViewById<TextView>(R.id.tvTabCount).background = tabCountBg
        // vsech 8 tlacitek se nastavuje v Nastaveni -> Tlacitka
        wireButtons()
    }

    override fun onResume() {
        super.onResume()
        // po importu v Nastaveni nacist ulozena nastaveni znovu
        isAdBlockOn = AppSettings.adBlockOn.get()
        isVpnOn = AppSettings.vpnOn.get()
        totalBlocked.set(AppSettings.totalBlocked.get())   // po importu mohla hodnota prijit ze souboru
        // tlacitka (funkce, pridana boci tlacitka, vysouvani) se mohla zmenit v Nastaveni
        rebuildExtraButtons()
        wireButtons()
        updateButtonVisuals()
        if (configMode) rebuildConfigPlus()   // tlacitka se postavila znovu -> "+" musi mit nove kotvy
        if (gestureConfigMode) applyGestureConfigUi()   // znovu schovat tlacitka a listy (nastaveni gest)
        chromeHidden = false   // po navratu jsou listy a tlacitka znovu postavena na vychozich mistech
        // prave pridane boci tlacitko se ~10 s svetle modre sytě problikava
        val hl = AppSettings.highlightButton.get()
        if (hl in 9..12) {
            AppSettings.highlightButton.reset()
            extraViews[hl]?.let { startBlink(it) }
        }
        applyAdBlockUi()
        if (introOnResume) {
            introOnResume = false
            playSettingsIntro()
        }
        findViewById<TextView>(R.id.tvVpnState).text = "OFF"
        startDownloadPoll()   // kdyz se behem pauzy neco stahuje, obnovi se plneni tlacitek
        // po importu: nahradit otevrene karty tema ze souboru
        if (AppSettings.tabsImported.get()) {
            AppSettings.tabsImported.set(false)
            clearAllTabs()
            restoreTabs()
        }
    }

    // ---------------- NASTAVITELNA TLACITKA 1-8 ----------------
    // 1 Domu, 2 Karty, 3 Oblibene, 4 Nastaveni, 5 levy bok, 6 pravy bok, 7 AdBlock, 8 VPN
    // Kazde ma funkci pro klepnuti a funkci pro drzeni (Nastaveni -> Tlacitka).

    private val buttonViewIds = listOf(
        R.id.btnHome, R.id.btnTabs, R.id.btnBookmarks, R.id.btnSettings,
        R.id.btnSideLeftMain, R.id.btnSideRightMain, R.id.btnAdBlock, R.id.btnVpn
    )

    internal fun buttonView(n: Int): View? = if (n <= 8) findViewById(buttonViewIds[n - 1]) else extraViews[n]

    private fun isSideButton(n: Int) = n == 5 || n == 6 || n >= 9

    private fun isLeftSide(n: Int) = n == 5 || (n >= 9 && n in AppSettings.extraButtons(true))

    internal fun buttonFunction(i: Int, long: Boolean): ButtonFunction =
        ButtonFunction.fromKey((if (long) AppSettings.buttonLong[i - 1] else AppSettings.buttonTap[i - 1]).get())

    // true = tlacitko ma vychozi funkci (a tedy sve puvodni ikony a animace)
    internal fun isDefaultVisual(i: Int): Boolean = buttonFunction(i, false) == ButtonDefaults.tap(i)

    // Pridana boci tlacitka (9-12): vytvori se nad tlacitky 5 a 6 podle nastaveni
    internal fun rebuildExtraButtons() {
        blinkAnim?.cancel()
        val parent = root as ViewGroup
        for (v in extraViews.values) parent.removeView(v)
        extraViews.clear()
        for (left in listOf(true, false)) {
            var anchorId = if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain
            for (n in AppSettings.extraButtons(left)) {
                val b = ImageButton(this)
                b.id = View.generateViewId()
                b.setBackgroundResource(R.drawable.side_button_bg)
                b.scaleType = ImageView.ScaleType.CENTER_INSIDE
                b.setPadding(dp(8), dp(8), dp(8), dp(8))
                b.elevation = dp(8).toFloat()
                b.translationY = lastImeShift
                val lp = ConstraintLayout.LayoutParams(dp(56), dp(56))
                lp.bottomToTop = anchorId
                if (left) lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                else lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                lp.bottomMargin = dp(12)
                parent.addView(b, lp)
                extraViews[n] = b
                anchorId = b.id
            }
        }
    }

    // Tlacitko s funkci Gesta (kulicka, vychozi je 6): prst polozeny na kulicku hned ukaze plochu a dotyk pokracuje
    // na ploše (jednim tahem bez zvednuti prstu). Funkce se kontroluje pri kazdem dotyku, takze jde menit za behu.
    private fun wireGestureTouch(v: View, n: Int) {
        v.setOnTouchListener { view, ev ->
            if (buttonFunction(n, false) != ButtonFunction.GESTURES) return@setOnTouchListener false
            val wl = IntArray(2)
            window.decorView.getLocationOnScreen(wl)
            val x = ev.rawX - wl[0]
            val y = ev.rawY - wl[1]
            val slides = isSideButton(n) && AppSettings.buttonSlide[n - 1].get()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    // zasunute tlacitko vyjede, ale plocha se objevi hned a tah pokracuje
                    if (slides && kotlin.math.abs(view.translationX) > 1f) {
                        view.animate().translationX(0f).setDuration(200).start()
                    }
                    val down = ExternalTouch(x, y, 0, ++extSeq)
                    externalDown = down
                    if (gestureView.visibility != View.VISIBLE) showGestures()
                    externalTouch.value = down
                }
                MotionEvent.ACTION_MOVE -> externalTouch.value = ExternalTouch(x, y, 1, ++extSeq)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    externalTouch.value = ExternalTouch(x, y, 2, ++extSeq)
                    if (slides) scheduleSideHide(n, view)
                }
            }
            true
        }
    }

    internal fun wireButtons() {
        if (configMode) { applyConfigWiring(); return }
        val numbers = (1..8).toList() + extraViews.keys.sorted()
        for (i in numbers) {
            val v = buttonView(i) ?: continue
            if (i != 7 && i != 8) wireGestureTouch(v, i) else v.setOnTouchListener(null)
            if (isSideButton(i)) {
                wireSideButton(i, v)
            } else {
                v.setOnClickListener { runTap(i) }
                v.setOnLongClickListener {
                    buzz(it)
                    runLongPress(i)
                    true
                }
            }
        }
    }

    // ---------------- KONFIGURACNI MOD TLACITEK ----------------
    // Nastaveni -> Tlacitka otevre hlavni plochu v konfiguracnim modu: klepnuti na tlacitko otevre jeho nastaveni (modal),
    // nad bocnimi tlacitky jsou "+" pro pridani dalsiho, uprostred je zelene prerusovane tlacitko "Ulozit a zavrit nastaveni".

    internal var configMode = false
    private val configButton = mutableStateOf<Int?>(null)
    private var configScrim: View? = null
    private var configSave: View? = null
    private var configHost: ComposeView? = null
    private val configPlus = ArrayList<View>()

    private fun enterConfigMode() {
        if (configMode) return
        if (gestureConfigMode) exitGestureConfig()
        configMode = true
        setChromeHidden(false)
        hideKeyboard()
        etSearchMain.clearFocus()
        etTopUrl.clearFocus()
        if (gestureView.visibility == View.VISIBLE) hideGestures()
        if (leftBarOpen) closeLeftBarAndHide()
        hideTabs()
        etSearchMain.isEnabled = false
        etTopUrl.isEnabled = false
        val parent = root as ViewGroup
        val green = Color.parseColor("#00B055")

        // lehke ztmaveni plochy; pohlti klepnuti do webu (tlacitka maji vyssi elevation)
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
        configScrim = scrim

        // uprostred: zelene prerusovane tlacitko
        val save = TextView(this)
        save.text = "Uložit a zavřít nastavení"
        save.setTextColor(green)
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        save.setTypeface(save.typeface, Typeface.BOLD)
        save.gravity = Gravity.CENTER
        save.setPadding(dp(26), dp(18), dp(26), dp(18))
        val sbg = GradientDrawable()
        sbg.setColor(Color.parseColor("#E61E1E1E"))
        sbg.cornerRadius = dp(18).toFloat()
        sbg.setStroke(dp(2), green, dp(10).toFloat(), dp(7).toFloat())   // prerusovany obrys
        save.background = sbg
        save.elevation = dp(9).toFloat()
        save.setOnClickListener { exitConfigMode() }
        val vlp = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        vlp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        vlp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        vlp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        vlp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        parent.addView(save, vlp)
        configSave = save

        // okno s nastavenim vybraneho tlacitka
        val host = ComposeView(this)
        host.setContent {
            val n = configButton.value
            if (n != null) {
                ButtonConfigModal(
                    n = n,
                    onChanged = { refreshConfigUi() },
                    onRemoved = { configButton.value = null; refreshConfigUi() },
                    onDismiss = { configButton.value = null }
                )
            }
        }
        parent.addView(host, ConstraintLayout.LayoutParams(1, 1))
        configHost = host

        refreshConfigUi()
    }

    internal fun exitConfigMode() {
        if (!configMode) return
        configMode = false
        configButton.value = null
        val parent = root as ViewGroup
        for (v in listOf(configScrim, configSave, configHost)) if (v != null) parent.removeView(v)
        configScrim = null
        configSave = null
        configHost = null
        for (v in configPlus) parent.removeView(v)
        configPlus.clear()
        etSearchMain.isEnabled = true
        etTopUrl.isEnabled = true
        rebuildExtraButtons()
        wireButtons()
        updateButtonVisuals()
        Toast.makeText(this, "Nastavení tlačítek uloženo", Toast.LENGTH_SHORT).show()
    }

    // Po zmene v modalu: znovu postavit tlacitka (i pridana) a "+" nad nimi
    private fun refreshConfigUi() {
        rebuildExtraButtons()
        wireButtons()
        updateButtonVisuals()
        rebuildConfigPlus()
    }

    // V konfiguracnim modu klepnuti i drzeni na tlacitko otevre jeho nastaveni
    private fun applyConfigWiring() {
        val numbers = (1..8).toList() + extraViews.keys.sorted()
        for (n in numbers) {
            val v = buttonView(n) ?: continue
            v.setOnTouchListener(null)
            v.setOnClickListener { configButton.value = n }
            v.setOnLongClickListener { configButton.value = n; true }
        }
    }

    // "+" nad bocnimi tlacitky (vlevo nad 5, vpravo nad 6): pridani dalsiho bocniho tlacitka (max 2 + 2)
    private fun rebuildConfigPlus() {
        val parent = root as ViewGroup
        for (v in configPlus) parent.removeView(v)
        configPlus.clear()
        val l = AppSettings.extraButtons(true)
        val r = AppSettings.extraButtons(false)
        if (l.size + r.size >= 4) return
        val green = Color.parseColor("#00B055")
        for (left in listOf(true, false)) {
            val list = if (left) l else r
            if (list.size >= 2) continue
            val anchor = if (list.isEmpty()) (if (left) R.id.btnSideLeftMain else R.id.btnSideRightMain)
            else extraViews[list.last()]?.id ?: continue
            val plus = TextView(this)
            plus.text = "+"
            plus.setTextColor(green)
            plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            plus.setTypeface(plus.typeface, Typeface.BOLD)
            plus.gravity = Gravity.CENTER
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#E61E1E1E"))
            bg.cornerRadius = dp(18).toFloat()
            bg.setStroke(dp(2), green, dp(8).toFloat(), dp(5).toFloat())
            plus.background = bg
            plus.elevation = dp(8).toFloat()
            plus.translationY = lastImeShift
            plus.setOnClickListener { addConfigButton(left) }
            val lp = ConstraintLayout.LayoutParams(dp(56), dp(56))
            lp.bottomToTop = anchor
            if (left) lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID else lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomMargin = dp(12)
            parent.addView(plus, lp)
            configPlus.add(plus)
        }
    }

    // Nove bocni tlacitko dostane nejmensi volne cislo od 9 a rovnou se otevre jeho nastaveni
    private fun addConfigButton(left: Boolean) {
        val used = AppSettings.extraButtons(true) + AppSettings.extraButtons(false)
        val next = (9..12).firstOrNull { it !in used } ?: return
        val list = AppSettings.extraButtons(left).toMutableList()
        if (list.size >= 2) return
        list.add(next)
        AppSettings.setExtraButtons(left, list)
        refreshConfigUi()
        configButton.value = next
    }

    // Vibrace pri drzeni tlacitka (jde vypnout v Konfiguraci)
    private fun buzz(v: View) {
        if (AppSettings.vibrationOn.get()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // Klepnuti: tlacitka 1-4 maji sve animace (1 problikne 2x zelene, 2 se otoci o 360 stupnu,
    // 3 vyplni zlutou hvezdu s jiskrami, 4 prohodi logo za kolecko) a az potom provedou funkci
    private fun runTap(i: Int) {
        val fn = buttonFunction(i, false)
        if (fn == ButtonDefaults.tap(i) && isDefaultVisual(i) && AppSettings.animationsOn.get()) {
            when (i) {
                1 -> { blinkHome(); performFunction(fn); return }
                2 -> { spinTabs { performFunction(fn) }; return }
                3 -> { animateStar { performFunction(fn) }; return }
                4 -> { playSettingsOutro { performFunction(fn) }; return }
            }
        }
        performFunction(fn)
    }

    // Drzeni: puvodni animace se prehraje jen u vychozi funkce toho tlacitka
    private fun runLongPress(i: Int) {
        val fn = buttonFunction(i, true)
        if (fn != ButtonFunction.NONE && fn == ButtonDefaults.long(i) && isDefaultVisual(i) && AppSettings.animationsOn.get()) {
            when (i) {
                1 -> { animateHome { performFunction(fn) }; return }
                2 -> { animateTabsButton { performFunction(fn) }; return }
                3 -> { animateStar { performFunction(fn) }; return }
            }
        }
        performFunction(fn)
    }

    private fun hiddenX(n: Int): Float = (if (isLeftSide(n)) -28f else 28f) * resources.displayMetrics.density

    // Bocni tlacitko: "vysouvaci" = videt jen 50 %: klepnuti ho vysune, dalsi klepnuti spusti funkci,
    // po 4 s se samo schova. Jinak je videt cele a klepnuti spusti funkci hned.
    private fun wireSideButton(n: Int, v: View) {
        fun slides() = AppSettings.buttonSlide[n - 1].get()
        // spusti funkci tlacitka; u 5/6 si zapamatuje, ze lista ma vyjet z teto strany
        fun press(long: Boolean) {
            if (n == 5 || n == 6) pressedOrigin = n
            performFunction(buttonFunction(n, long))
            pressedOrigin = 0
        }
        v.setOnClickListener {
            if (!slides()) {
                press(false)
            } else {
                val hidden = kotlin.math.abs(v.translationX) > 1f
                if (hidden) {
                    v.animate().translationX(0f).setDuration(250).start()
                } else press(false)
                scheduleSideHide(n, v)
            }
        }
        // Drzeni: funkce se spusti HNED, i kdyz je tlacitko napul schovane (jen se pritom vysune)
        v.setOnLongClickListener {
            buzz(it)
            if (slides() && kotlin.math.abs(v.translationX) > 1f) v.animate().translationX(0f).setDuration(250).start()
            press(true)
            if (slides()) scheduleSideHide(n, v)
            true
        }
    }

    private fun scheduleSideHide(n: Int, v: View) {
        sideHide[n]?.let { v.removeCallbacks(it) }
        val r = Runnable {
            // otevrena lista drzi tlacitko 5 vysunute
            if (AppSettings.buttonSlide[n - 1].get() && !(n == barOrigin && leftBarOpen)) {
                v.animate().translationX(hiddenX(n)).setDuration(250).start()
            }
        }
        sideHide[n] = r
        v.postDelayed(r, 4000)
    }

    // Boční tlačítka: vysouvací = napůl schované, jinak celé vidět
    private fun applySideMode(n: Int, v: View) {
        val slide = AppSettings.buttonSlide[n - 1].get()
        v.translationX = if (slide && !configMode && !(n == barOrigin && leftBarOpen)) hiddenX(n) else 0f
        if (n == barOrigin) setLeftBar(leftBarOpen, false)
    }

    // Lista za levym bocnim tlacitkem 5: klepnuti na tlacitko ho zcela vysune a potom lista vyjede nahoru.
    // Uvnitr jsou tlacitka 1 (Domu), 2 (Karty), 3 (Oblibene); dalsi pridame pozdeji.
    private fun setupSideBar() {
        if (sideBarLeft != null) return
        val d = resources.displayMetrics.density
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.gravity = Gravity.CENTER_HORIZONTAL
        bar.setPadding(0, dp(12), 0, 0)
        bar.elevation = dp(6).toFloat()   // nad webem, pod tlacitky (8)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#1E1E1E"))
        bg.cornerRadius = 24f * d
        bg.setStroke((1f * d).toInt().coerceAtLeast(1), Color.WHITE)
        bar.background = bg

        // jedno kulate tlacitko v liste s pevnou akci (klepnuti)
        fun barButton(content: View, action: () -> Unit): FrameLayout {
            val b = FrameLayout(this)
            b.setBackgroundResource(R.drawable.btn_circle_dark)
            b.isClickable = true
            b.isFocusable = true
            b.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            b.setOnClickListener { action() }
            val blp = LinearLayout.LayoutParams(dp(48), dp(48))
            blp.bottomMargin = dp(8)
            bar.addView(b, blp)
            return b
        }
        fun glyph(text: String, sizeSp: Float = 22f) = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
        // 1) screenshot obrazovky (sdilet odkaz a QR jsou ted u adresniho radku)
        fun whiteIcon(g: String) = ImageView(this).apply { setImageBitmap(GlyphIcons.white(g, dp(28))) }
        fun fnIcon(fn: ButtonFunction) = ImageView(this).apply { setImageBitmap(glyphBitmap(fn, dp(28))) }
        fun resIcon(res: Int) = ImageView(this).apply {
            val bmp = Bitmap.createBitmap(dp(28), dp(28), Bitmap.Config.ARGB_8888)
            androidx.appcompat.content.res.AppCompatResources.getDrawable(this@MainActivity, res)?.let {
                it.setBounds(0, 0, dp(28), dp(28))
                it.draw(Canvas(bmp))
            }
            setImageBitmap(bmp)
        }
        // fotacek: klepnuti ho na chvili zezeleni a zepta se, jaky screenshot
        var camBtn: FrameLayout? = null
        camBtn = barButton(resIcon(R.drawable.ic_screenshot)) {
            camBtn?.let { blinkGreen(it) }
            performFunction(ButtonFunction.SCREENSHOT)
        }
        // 4) verze pro pocitac (zap/vyp; zelene = zapnuto), 5) najit na strance (text)
        leftDeskBtn = barButton(fnIcon(ButtonFunction.DESKTOP_MODE)) {
            toggleDesktopMode()
            updateDeskBtn()
        }
        barButton(fnIcon(ButtonFunction.FIND_IN_PAGE)) {
            closeLeftBarAndHide()
            performFunction(ButtonFunction.FIND_IN_PAGE)
        }
        updateDeskBtn()

        val lp = ConstraintLayout.LayoutParams((68 * d).toInt(), 0)
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topToBottom = R.id.topBar
        lp.topMargin = (8 * d).toInt()
        lp.bottomToTop = R.id.bottomBar
        lp.bottomMargin = (18 * d).toInt()
        (root as ViewGroup).addView(bar, 0, lp)
        sideBarLeft = bar
        bar.translationX = -6f * d
        bar.visibility = View.INVISIBLE
    }

    // ---------------- LEVA LISTA: SCREENSHOT A QR ----------------

    // Screenshot celeho okna: nejdriv se zavre lista (aby nebyla na obrazku), pak se po dojeti animace vyfoti
    // ---------------- SCREENSHOT: VYBER DRUHU ----------------

    internal var capturing = false          // probiha screenshot celé stranky: dotyky se ignoruji
    internal var regionView: View? = null   // vyber casti obrazovky (overlay)

    internal var shotPreview: View? = null

    // ---------------- OPRAVNENI WEBU (poloha, kamera, mikrofon) ----------------

    private var permCallback: ((Map<String, Boolean>) -> Unit)? = null
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val cb = permCallback
            permCallback = null
            cb?.invoke(res)
        }

    private fun androidPermsFor(type: String): List<String> = when (type) {
        "geo" -> listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        "cam" -> listOf(android.Manifest.permission.CAMERA)
        else -> listOf(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun hasAndroidPerm(type: String): Boolean =
        androidPermsFor(type).any { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }

    // Rozhodne o zadosti webu: ulozena volba (povolit / zakazat) plati hned, jinak se uzivatel zepta.
    // Povolene typy pak jeste projdou systemovym opravnenim Androidu. result dostane mnozinu skutecne povolenych typu.
    private fun handleSitePermission(host: String, types: List<String>, persist: Boolean, result: (Set<String>) -> Unit) {
        val allowed = types.filter { SiteRules.perm(host, it) == 1 }.toMutableSet()
        val ask = types.filter { SiteRules.perm(host, it) == -1 }
        fun finish() {
            // systemova opravneni, ktera aplikace jeste nema
            val missing = allowed.filter { !hasAndroidPerm(it) }
            if (missing.isEmpty()) { result(allowed); return }
            if (permCallback != null) { result(emptySet()); return }   // uz se nejake opravneni ptame
            permCallback = {
                for (t in missing) if (!hasAndroidPerm(t)) allowed.remove(t)
                result(allowed)
            }
            permLauncher.launch(missing.flatMap { androidPermsFor(it) }.distinct().toTypedArray())
        }
        if (ask.isEmpty()) { finish(); return }
        val names = ask.joinToString(", ") { when (it) { "geo" -> "polohu"; "cam" -> "kameru"; else -> "mikrofon" } }
        var answered = false
        AlertDialog.Builder(this)
            .setTitle(host.ifBlank { "Web" })
            .setMessage("Tento web chce použít: $names.\n\nVolbu si pamatuju, změníš ji v informacích o stránce (zámek).")
            .setPositiveButton("Povolit") { _, _ ->
                answered = true
                if (persist) for (t in ask) SiteRules.setPerm(host, t, 1)
                allowed.addAll(ask)
                finish()
            }
            .setNegativeButton("Zakázat") { _, _ ->
                answered = true
                if (persist) for (t in ask) SiteRules.setPerm(host, t, 0)
                finish()
            }
            .setOnDismissListener { if (!answered) { answered = true; finish() } }   // zavreno bokem = jen to, co uz bylo povolene
            .show()
    }

    // Smaze cookies webu (i nadrazenych domen) a jeho ulozena data; vrati pocet smazanych cookies
    private fun clearSiteCookies(url: String): Int {
        val host = try { Uri.parse(url).host } catch (e: Exception) { null } ?: return 0
        val cm = CookieManager.getInstance()
        val parts = host.split(".")
        val domains = ArrayList<String>()
        for (i in 0 until maxOf(1, parts.size - 1)) domains.add(parts.drop(i).joinToString("."))
        var n = 0
        for (d in domains) {
            for (scheme in listOf("https://", "http://")) {
                val raw = cm.getCookie(scheme + d) ?: continue
                for (c in raw.split(";")) {
                    val name = c.substringBefore("=").trim()
                    if (name.isEmpty()) continue
                    n++
                    for (dom in listOf("", "; Domain=$d", "; Domain=.$d")) {
                        cm.setCookie(scheme + d, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/$dom")
                    }
                }
            }
        }
        cm.flush()
        try { WebStorage.getInstance().deleteOrigin("https://$host"); WebStorage.getInstance().deleteOrigin("http://$host") } catch (e: Exception) { }
        return n
    }

    private fun updateLockIcon(url: String?) {
        // tecka na zamku = na tomhle webu je AdBlock vypnuty (vyjimka)
        val dot = findViewById<View>(R.id.vUrlLockDot)
        if (dot.background == null) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(Color.parseColor("#CDA24D"))
            g.setStroke(dp(1), Color.parseColor("#1E1E1E"))
            dot.background = g
        }
        val h = try { if (url != null && isWebUrl(url)) Uri.parse(url).host else null } catch (e: Exception) { null }
        dot.visibility = if (SiteRules.adBlockOff(h)) View.VISIBLE else View.GONE
        val iv = findViewById<ImageView>(R.id.ivUrlLock)
        when {
            url != null && url.startsWith("https://") -> iv.clearColorFilter()                        // zabezpecene
            url != null && url.startsWith("http://") -> iv.setColorFilter(Color.parseColor("#AC0001")) // nezabezpecene
            else -> iv.setColorFilter(Color.parseColor("#888888"))                                     // interni / prazdne
        }
    }

    // Zamek v adresnim radku: informace o aktualni strance + nastaveni pro tento web
    private fun showPageInfo() {
        val url = webView.url
        val tab = tabs.active()
        val secure = url != null && url.startsWith("https://")
        val plainHttp = url != null && url.startsWith("http://")
        val host = if (url != null && isWebUrl(url)) (try { Uri.parse(url).host } catch (e: Exception) { null }) else null
        val incognito = tab?.incognito == true
        showStyledDialog(title = "Informace o stránce", negativeText = "Zavřít") { _ ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL

            fun row(label: String, value: String, color: Int = Color.WHITE) {
                val l = dialogLabel(label)
                val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                llp.topMargin = dp(10)
                box.addView(l, llp)
                val v = TextView(this)
                v.text = value
                v.setTextColor(color)
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                v.maxLines = 3
                v.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                box.addView(v)
            }
            // radek s tlacitkem vpravo (hodnota se meni klepnutim)
            fun actionRow(label: String, value: String, onClick: (TextView) -> Unit) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                val t = TextView(this)
                t.text = label
                t.setTextColor(Color.WHITE)
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val b = styledButton(value, false)
                b.setPadding(dp(14), dp(7), dp(14), dp(7))
                b.setOnClickListener { onClick(b) }
                r.addView(b)
                val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rlp.topMargin = dp(8)
                box.addView(r, rlp)
            }

            // varovani nahore u nesifrovane stranky
            if (plainHttp) {
                val w = TextView(this)
                w.text = "Pozor: stránka není šifrovaná (HTTP). Nezadávej na ní hesla ani platební údaje."
                w.setTextColor(Color.parseColor("#FF8A8A"))
                w.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                w.setTypeface(w.typeface, Typeface.BOLD)
                w.setPadding(dp(12), dp(10), dp(12), dp(10))
                val wb = GradientDrawable()
                wb.setColor(Color.parseColor("#3A1414"))
                wb.cornerRadius = dp(12).toFloat()
                wb.setStroke(dp(1), Color.parseColor("#AC0001"))
                w.background = wb
                box.addView(w)
            }

            row("Název", webView.title.orEmpty().ifBlank { "—" })
            row("Adresa", url ?: "—")
            when {
                secure -> row("Zabezpečení", "HTTPS – spojení je šifrované", Color.parseColor("#00B055"))
                plainHttp -> row("Zabezpečení", "HTTP – spojení NENÍ šifrované", Color.parseColor("#FF5A5A"))
                else -> row("Zabezpečení", "Interní stránka")
            }
            val cert = webView.certificate
            if (secure && cert != null) {
                val issuer = cert.issuedBy?.let { it.cName ?: it.oName }.orEmpty()
                if (issuer.isNotBlank()) row("Certifikát vydal", issuer)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val until = cert.validNotAfterDate
                    if (until != null) row("Certifikát platí do", java.text.SimpleDateFormat("d. M. yyyy", java.util.Locale("cs")).format(until))
                }
            }
            row("Režim", (if (tab?.desktop == true) "Verze pro počítač" else "Mobilní verze") + (if (incognito) " · anonymní karta" else ""))

            // AdBlock: zablokovano + vyjimka pro tento web
            val n = tab?.blocked?.get() ?: 0
            if (!isAdBlockOn) {
                row("AdBlock", "Vypnutý (celkově)")
            } else {
                row("AdBlock – zablokováno na stránce", n.toString())
                if (host != null && !incognito) {
                    fun lbl() = if (SiteRules.adBlockOff(host)) "Vypnutý" else "Zapnutý"
                    actionRow("AdBlock na tomto webu", lbl()) { tv ->
                        SiteRules.setAdBlockOff(host, !SiteRules.adBlockOff(host))
                        tv.text = lbl()
                        updateLockIcon(webView.url)
                        webView.reload()
                    }
                }
            }

            // opravneni webu a cookies (v anonymni karte se nic neuklada)
            if (host != null && !incognito) {
                val head = dialogLabel("Oprávnění webu")
                val hlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                hlp.topMargin = dp(16)
                box.addView(head, hlp)
                for ((type, name) in listOf("geo" to "Poloha", "cam" to "Kamera", "mic" to "Mikrofon")) {
                    fun lbl() = when (SiteRules.perm(host, type)) { 1 -> "Povoleno"; 0 -> "Zakázáno"; else -> "Zeptat se" }
                    actionRow(name, lbl()) { tv ->
                        // zeptat se -> povoleno -> zakazano -> zeptat se
                        val next = when (SiteRules.perm(host, type)) { -1 -> 1; 1 -> 0; else -> -1 }
                        SiteRules.setPerm(host, type, next)
                        tv.text = lbl()
                    }
                }
                val clear = styledButton("Smazat cookies tohoto webu", false)
                clear.setOnClickListener {
                    val count = if (url != null) clearSiteCookies(url) else 0
                    Toast.makeText(this, if (count > 0) "Smazáno cookies: $count" else "Tento web nemá žádné cookies", Toast.LENGTH_SHORT).show()
                    webView.reload()
                }
                val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                clp.topMargin = dp(16)
                box.addView(clear, clp)
            } else if (incognito) {
                row("Oprávnění a cookies", "V anonymní kartě se nic neukládá")
            }

            // vysoke okno se posouva
            val scroll = ScrollView(this)
            scroll.addView(box)
            val dm = resources.displayMetrics
            box.measure(
                View.MeasureSpec.makeMeasureSpec((dm.widthPixels * 0.9f).toInt() - dp(44), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            scroll.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, minOf(box.measuredHeight, (dm.heightPixels * 0.6f).toInt())
            )
            scroll
        }
    }

    // Soubor s QR kodem v cache (sdili se pres ExportFileProvider)
    private fun qrFile(url: String): File {
        val dir = File(cacheDir, ExportFileProvider.DIR)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "qr-" + System.currentTimeMillis() + ".png")
        val bmp = makeQr(url)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    // Pravy symbol v adresnim radku: Sdilet - uzivatel si prepne, jestli posila/kopiruje odkaz, nebo QR kod
    private fun showShareDialog() {
        val url = webView.url
        if (url == null || !isWebUrl(url)) {
            Toast.makeText(this, "Není co sdílet", Toast.LENGTH_SHORT).show()
            return
        }
        var qrMode = false
        showStyledDialog(
            title = "Sdílet",
            positiveText = "Sdílet",
            onPositive = {
                if (!qrMode) {
                    shareText(url, "Sdílet odkaz")
                } else {
                    try {
                        val uri = ExportFileProvider.uriFor(this, qrFile(url).name)
                        val send = Intent(Intent.ACTION_SEND)
                        send.type = "image/png"
                        send.putExtra(Intent.EXTRA_STREAM, uri)
                        send.clipData = android.content.ClipData.newRawUri("", uri)
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(Intent.createChooser(send, "Sdílet QR kód"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "QR kód se nepodařilo vytvořit", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            },
            negativeText = "Zavřít"
        ) { _ ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL

            // prepinac Odkaz / QR kod
            val switchRow = LinearLayout(this)
            switchRow.orientation = LinearLayout.HORIZONTAL
            val tvLink = styledButton("Odkaz", true)
            val tvQr = styledButton("QR kód", false)
            val half = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            switchRow.addView(tvLink, half)
            val half2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            half2.marginStart = dp(8)
            switchRow.addView(tvQr, half2)
            box.addView(switchRow)

            // obsah: adresa, nebo QR kod
            val tvUrl = TextView(this)
            tvUrl.text = url
            tvUrl.setTextColor(Color.WHITE)
            tvUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tvUrl.maxLines = 4
            tvUrl.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            tvUrl.setPadding(dp(4), dp(14), dp(4), dp(4))
            box.addView(tvUrl)
            val ivQr = ImageView(this)
            ivQr.setBackgroundColor(Color.WHITE)   // bily podklad i v tmavem rezimu, jinak se kod nenaskenuje
            ivQr.setPadding(dp(8), dp(8), dp(8), dp(8))
            ivQr.visibility = View.GONE
            val qlp = LinearLayout.LayoutParams(dp(230), dp(230))
            qlp.gravity = Gravity.CENTER_HORIZONTAL
            qlp.topMargin = dp(14)
            box.addView(ivQr, qlp)

            fun restyle(tv: TextView, active: Boolean) {
                val bg = GradientDrawable()
                bg.cornerRadius = dp(14).toFloat()
                bg.setColor(if (active) Color.parseColor("#00B055") else Color.parseColor("#2A2A2A"))
                tv.background = bg
                tv.setTextColor(if (active) Color.parseColor("#06281D") else Color.parseColor("#CCCCCC"))
            }
            fun setMode(qr: Boolean) {
                if (qr && ivQr.drawable == null) {
                    try {
                        ivQr.setImageBitmap(makeQr(url))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Adresa je moc dlouhá pro QR kód", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                qrMode = qr
                restyle(tvLink, !qr)
                restyle(tvQr, qr)
                tvUrl.visibility = if (qr) View.GONE else View.VISIBLE
                ivQr.visibility = if (qr) View.VISIBLE else View.GONE
            }
            tvLink.setOnClickListener { setMode(false) }
            tvQr.setOnClickListener { setMode(true) }

            // kopirovat to, co je prave vybrane (odkaz nebo obrazek QR)
            val copy = styledButton("Kopírovat", false)
            copy.setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                if (!qrMode) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                    Toast.makeText(this, "Adresa zkopírována", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val uri = ExportFileProvider.uriFor(this, qrFile(url).name)
                        cm.setPrimaryClip(android.content.ClipData.newUri(contentResolver, "QR", uri))
                        Toast.makeText(this, "QR kód zkopírován", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "QR kód se nepodařilo zkopírovat", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clp.topMargin = dp(14)
            box.addView(copy, clp)
            box
        }
    }

    // Vykresli QR (zxing); vyhodi vyjimku, kdyz se adresa nevejde
    private fun makeQr(text: String): Bitmap {
        val hints = HashMap<com.google.zxing.EncodeHintType, Any>()
        hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
        hints[com.google.zxing.EncodeHintType.MARGIN] = 2
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
        val scale = maxOf(1, 600 / maxOf(m.width, m.height))
        val w = m.width * scale
        val h = m.height * scale
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (m.get(x / scale, y / scale)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // Zelene pozadi tlacitka, kdyz ma aktivni karta zapnutou verzi pro pocitac
    private fun updateDeskBtn() {
        val b = leftDeskBtn ?: return
        if (tabs.active()?.desktop == true) {
            val g = GradientDrawable()
            g.setColor(Color.parseColor("#00B055"))
            g.cornerRadius = dp(18).toFloat()
            g.setStroke(dp(1), Color.WHITE)
            b.background = g
        } else {
            b.setBackgroundResource(R.drawable.btn_circle_dark)
        }
    }

    // Tlacitko, ze ktereho se lista otevira: stisknute 5/6, jinak to, ktere ma funkci "Bocni lista"
    private fun defaultBarOrigin(): Int {
        for (i in listOf(5, 6)) {
            if (AppSettings.isEnabled(i) && (buttonFunction(i, false) == ButtonFunction.LEFT_BAR || buttonFunction(i, true) == ButtonFunction.LEFT_BAR)) return i
        }
        return 5
    }

    private fun barBtn(): View = findViewById(if (barOrigin == 6) R.id.btnSideRightMain else R.id.btnSideLeftMain)

    // Funkce "Bocni lista" (z tlacitka 5/6 nebo gesta)
    private fun toggleLeftBarAny() = toggleBarFrom(if (pressedOrigin != 0) pressedOrigin else defaultBarOrigin())

    // Lista se otevira na strane tlacitka: 5 = vlevo, 6 = vpravo
    private fun applyBarSide() {
        val bar = sideBarLeft ?: return
        val right = barOrigin == 6
        val lp = bar.layoutParams as ConstraintLayout.LayoutParams
        lp.startToStart = if (right) ConstraintLayout.LayoutParams.UNSET else ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd = if (right) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
        bar.layoutParams = lp
        bar.translationX = (if (right) 6f else -6f) * resources.displayMetrics.density
    }

    // Klepnuti na tlacitko: zavrena lista -> tlacitko se zcela vysune a lista vyjede nahoru; otevrena -> zavre se
    private fun toggleBarFrom(origin: Int) {
        if (leftBarOpen) {
            closeLeftBarAndHide()
            return
        }
        barOrigin = origin
        applyBarSide()
        val btn = barBtn()
        sideHide[origin]?.let { btn.removeCallbacks(it) }
        if (AppSettings.isEnabled(origin) && kotlin.math.abs(btn.translationX) > 1f) {
            btn.animate().translationX(0f).setDuration(250).withEndAction { setLeftBar(true, true) }.start()
        } else {
            setLeftBar(true, true)
        }
    }

    // Lista zajede dolu do tlacitka a pak se tlacitko zasune do vychozi polohy (podle nastaveni vysouvani)
    internal fun closeLeftBarAndHide() {
        val btn = barBtn()
        sideHide[barOrigin]?.let { btn.removeCallbacks(it) }
        val origin = barOrigin
        setLeftBar(false, true) {
            if (AppSettings.buttonSlide[origin - 1].get()) btn.animate().translationX(hiddenX(origin)).setDuration(250).start()
        }
    }

    // Dokud je lista otevrena, pridana tlacitka nad tlacitkem na teze strane se uplne schovaji (100 %),
    // po zavreni se vrati. Tlacitko s kulickou gest se jen skryje/ukaze (jeho pruhlednost ridi kulicka).
    private fun hideExtrasForBar(open: Boolean) {
        val orbN = gestureButtonN()
        for ((n, v) in extraViews) {
            val sameSide = isLeftSide(n) == (barOrigin != 6)
            val hide = open && sameSide
            if (n == orbN) {
                v.visibility = if (hide) View.INVISIBLE else View.VISIBLE
                continue
            }
            v.animate().cancel()
            if (hide) {
                v.animate().alpha(0f).setDuration(200).withEndAction { if (leftBarOpen) v.visibility = View.INVISIBLE }.start()
            } else {
                v.visibility = View.VISIBLE
                v.animate().alpha(1f).setDuration(200).start()
            }
        }
    }

    // Otevre/zavre levou listu: odkryva se odspodu nahoru (od tlacitka 5), tlacitka uvnitr se nemacknou
    private fun setLeftBar(open: Boolean, animate: Boolean, onDone: (() -> Unit)? = null) {
        val bar = sideBarLeft ?: return
        leftBarAnim?.cancel()
        leftBarOpen = open
        hideExtrasForBar(open)
        if (!animate) {
            bar.clipBounds = null
            bar.visibility = if (open) View.VISIBLE else View.INVISIBLE
            onDone?.invoke()
            return
        }
        fun reveal(p: Float) {
            val h = bar.height
            bar.clipBounds = Rect(0, (h * (1f - p)).toInt(), bar.width, h)
        }
        bar.visibility = View.VISIBLE
        reveal(if (open) 0f else 1f)
        val a = ValueAnimator.ofFloat(if (open) 0f else 1f, if (open) 1f else 0f)
        a.duration = 320
        a.interpolator = android.view.animation.DecelerateInterpolator()
        a.addUpdateListener { reveal(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                bar.clipBounds = null
                if (!open) bar.visibility = View.INVISIBLE
                if (!cancelled) onDone?.invoke()
            }
        })
        leftBarAnim = a
        a.start()
    }

    // Ikona tlacitka podle jeho funkce (klepnuti). Vychozi funkce = puvodni ikona.
    // Pojistka: nejaka tlacitko musi umet otevrit Nastaveni, jinak by se uz nedaly zmenit funkce.
    // Pokud ne (napr. po importu), vrati se tlacitku 4 vychozi funkce.
    private fun ensureSettingsReachable() {
        for (i in 1..12) {
            if (!AppSettings.isEnabled(i)) continue   // skryte tlacitko se nepocita
            for (long in listOf(false, true)) {
                val fn = buttonFunction(i, long)
                if (fn == ButtonFunction.OPEN_SETTINGS) return
            }
        }
        AppSettings.buttonTap[3].set(ButtonDefaults.tap(4).key)
        Toast.makeText(this, "Nastavení vráceno na tlačítko 4", Toast.LENGTH_LONG).show()
    }

    internal fun updateButtonVisuals() {
        ensureSettingsReachable()
        updateSideButtons()
        for (i in 1..8) {
            val fn = buttonFunction(i, false)
            val custom = fn != ButtonDefaults.tap(i)
            val glyph = if (custom) glyphBitmap(fn) else null
            when (i) {
                1 -> setIcon(R.id.ivHome, R.drawable.ic_home, glyph)
                2 -> {
                    val label = findViewById<TextView>(R.id.tvTabCount)
                    if (custom) {
                        // vlastni funkce: bila ikona (obrazek) misto cisla
                        label.text = ""
                        val bd = android.graphics.drawable.BitmapDrawable(resources, glyphBitmap(fn, dp(28)))
                        bd.gravity = Gravity.CENTER
                        label.background = bd
                    } else {
                        label.text = tabs.list.size.toString()
                        label.background = tabCountBg
                        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    }
                }
                3 -> setIcon(R.id.ivBookmarks, R.drawable.ic_bookmarks, glyph)
                4 -> {
                    // vychozi funkce = logo appky na svetlem tlacitku; vlastni funkce = bila ikona na tmavem
                    setIcon(R.id.btnSettings, R.drawable.asap_logo, glyph)
                    findViewById<View>(R.id.btnSettings).setBackgroundResource(
                        if (glyph == null) R.drawable.bg_search_white else R.drawable.btn_circle_dark
                    )
                }
                5 -> setIcon(R.id.btnSideLeftMain, R.drawable.ic_sidebar, glyph)
                // 6 = gesta: zvlastni kulicka (kresli ji GestureOrbView), bez ikony
                6 -> {
                    // kulicka (bez ikony) jen pro funkci Gesta; jinak bezne boční tlacitko s ikonou funkce
                    if (custom) setIcon(R.id.btnSideRightMain, android.R.drawable.ic_menu_info_details, glyph)
                    else findViewById<ImageView>(R.id.btnSideRightMain).setImageDrawable(null)
                }
                7 -> {
                    setIcon(R.id.ivAdBlock, R.drawable.ic_adblock, glyph)
                    findViewById<View>(R.id.tvAdBlockState).visibility = if (custom) View.GONE else View.VISIBLE
                }
                8 -> {
                    setIcon(R.id.ivVpn, R.drawable.ic_vpn, glyph)
                    findViewById<View>(R.id.tvVpnState).visibility = if (custom) View.GONE else View.VISIBLE
                }
            }
        }
    }

    // ---------------- GESTA ----------------

    // ---------------- KONFIGURACNI MOD GEST ----------------
    // Nastaveni -> Gesta otevre hlavni plochu jen s tlacitkem Gesta (kulickou) a plochou gest. Plocha ma na okrajich a rozich
    // uchyty (velikost) a jde presouvat; shora se vysune scrollovaci panel (styl, viditelnost, odezva, seznam gest).

    internal var gestureConfigMode = false
    internal val gestTick = mutableStateOf(0)        // zmena stylu / viditelnosti -> prekreslit editor
    internal val gestResetKey = mutableStateOf(0)     // vraceni vychozi polohy plochy
    internal var gestScrim: View? = null
    internal var gestPanel: ComposeView? = null
    internal val gestHidden = HashMap<View, Int>()    // skryte prvky a jejich puvodni viditelnost

    // Sytě svetle modre problikavani ~10 s (22 x 450 ms) u prave pridaneho tlacitka
    private fun startBlink(v: View) {
        blinkAnim?.cancel()
        val bg = GradientDrawable()
        bg.cornerRadius = dp(16).toFloat()
        val base = Color.parseColor("#1E1E1E")
        val blue = Color.parseColor("#00B0FF")
        val eval = ArgbEvaluator()
        v.background = bg
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 450
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = 21
        a.addUpdateListener {
            val k = it.animatedValue as Float
            bg.setColor(eval.evaluate(k, base, blue) as Int)
            bg.setStroke(dp(2), blue)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                v.setBackgroundResource(R.drawable.side_button_bg)
            }

            override fun onAnimationCancel(animation: Animator) {
                v.setBackgroundResource(R.drawable.side_button_bg)
            }
        })
        blinkAnim = a
        a.start()
    }

    // Konec updateButtonVisuals: tvar tlacitka 6, pridana tlacitka a vysouvani bocnich
    private fun updateSideButtons() {
        // tlacitko 6: s funkci Gesta je to jen pruhledna plocha - kulicku kresli GestureOrbView pod nim;
        // s jinou funkci je to bezne boční tlacitko (tmave, s ikonou)
        val isOrb = gestureButtonN() == 6
        val b6 = findViewById<View>(R.id.btnSideRightMain)
        if (isOrb) b6.background = ColorDrawable(Color.TRANSPARENT) else b6.setBackgroundResource(R.drawable.side_button_bg)
        (b6 as? ImageView)?.scaleType = if (isOrb) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_INSIDE
        b6.setPadding(if (isOrb) 0 else dp(8), if (isOrb) 0 else dp(8), if (isOrb) 0 else dp(8), if (isOrb) 0 else dp(8))
        // tlacitka 5-8 si uzivatel muze skryt (kulicka mizi s tlacitkem 6 nebo kdyz neni funkce Gesta)
        // v konfiguracnim modu jsou videt i skryta tlacitka (pruhledna), aby je slo nastavit
        fun show(id: Int, n: Int) {
            val v = findViewById<View>(id)
            val on = AppSettings.isEnabled(n)
            v.visibility = if (on || configMode) View.VISIBLE else View.GONE
            v.alpha = if (!on && configMode) 0.4f else 1f
        }
        show(R.id.btnSideLeftMain, 5)
        show(R.id.btnSideRightMain, 6)
        show(R.id.btnAdBlock, 7)
        show(R.id.btnVpn, 8)
        updateOrbTarget()

        for ((n, v) in extraViews) {
            (v as ImageView).setImageBitmap(glyphBitmap(buttonFunction(n, false)))
        }
        for (n in listOf(5, 6) + extraViews.keys.sorted()) buttonView(n)?.let { applySideMode(n, it) }
    }

    private fun setIcon(viewId: Int, defaultRes: Int, glyph: Bitmap?) {
        val iv = findViewById<ImageView>(viewId)
        iv.clearColorFilter()
        if (glyph != null) iv.setImageBitmap(glyph) else iv.setImageResource(defaultRes)
    }

    // Ikona funkce (bila vektorova ikona ze sady) nakreslena do obrazku pro tlacitko
    private fun glyphBitmap(fn: ButtonFunction, sizePx: Int = dp(48)): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val d = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, fn.iconRes)
        if (d != null) {
            d.setBounds(0, 0, sizePx, sizePx)
            d.draw(Canvas(bmp))
        }
        return bmp
    }

    private fun rememberClosed(tab: BrowserTab) {
        if (tab.incognito) return   // anonymni karty se nepamatuji
        val u = tab.pendingUrl ?: tab.url.ifBlank { tab.liveUrl() }
        if (isWebUrl(u)) {
            closedUrls.add(u)
            if (closedUrls.size > 20) closedUrls.removeAt(0)
        }
    }

    private fun openSettingsAt(start: String?): Unit {
        if (settingsOpen) return   // okno Nastaveni uz je otevrene / se otevira
        settingsOpen = true
        val i = settingsIntent()
        if (start != null) i.putExtra(SettingsActivity.EXTRA_START, start)
        settingsLauncher.launch(i)
    }

    private fun toggleAdBlock() {
        isAdBlockOn = !isAdBlockOn
        AppSettings.adBlockOn.set(isAdBlockOn)
        applyAdBlockUi()
        Toast.makeText(this, if (isAdBlockOn) "AdBlock ON" else "AdBlock OFF", Toast.LENGTH_SHORT).show()
        webView.reload()
    }

    private fun switchRelative(delta: Int) {
        // jen mezi kartami stejne skupiny (bezne / anonymni)
        val incog = tabs.active()?.incognito ?: false
        val group = tabs.list.filter { it.incognito == incog }
        val n = group.size
        if (n < 2) return
        val idx = group.indexOfFirst { it.id == tabs.activeId }
        switchTo(group[((idx + delta) % n + n) % n])
    }

    // Nastavi user-agent karty (mobilni / pocitac); stranku nenacita znovu
    private fun applyDesktop(tab: BrowserTab, on: Boolean) {
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
    private fun toggleDesktopMode() {
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

    // Zamek proti dvojitemu otevreni: po otevreni okna/prehledu/dialogu se dalsi "otevirani" hned za sebou ignoruje
    private var openLockUntil = 0L
    private var settingsOpen = false
    private var introOnResume = false

    private fun isOpeningFunction(fn: ButtonFunction): Boolean = when (fn) {
        ButtonFunction.OPEN_SETTINGS, ButtonFunction.OPEN_BUTTONS, ButtonFunction.OPEN_BOOKMARKS,
        ButtonFunction.OPEN_HISTORY, ButtonFunction.EXPORT, ButtonFunction.IMPORT,
        ButtonFunction.TABS_OVERVIEW, ButtonFunction.HOME_SETTINGS, ButtonFunction.ADD_TO_FOLDER,
        ButtonFunction.FIND_IN_PAGE, ButtonFunction.SCREENSHOT, ButtonFunction.FEEDBACK -> true
        else -> false
    }

    // Provede funkci tlacitka (klepnuti i drzeni jdou stejnou cestou)
    @Suppress("DEPRECATION")
    internal fun performFunction(fn: ButtonFunction) {
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
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
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

    // ---------------- ANIMACE TLACITEK (drzeni) ----------------

    // Karty: cislo se zmeni na zelene plus a ctverecek se otoci o 720 stupnu (dve otocky)
    private fun animateTabsButton(onDone: () -> Unit) {
        if (tabsAnimating) return
        tabsAnimating = true
        val green = Color.parseColor("#00B055")
        val density = resources.displayMetrics.density
        val label = findViewById<TextView>(R.id.tvTabCount)
        var switched = false
        val anim = ObjectAnimator.ofFloat(label, View.ROTATION, 0f, 720f)   // dve otocky
        anim.duration = 1200
        anim.addUpdateListener {
            // v polovine otoceni se obsah zmeni na plus
            if (!switched && it.animatedFraction >= 0.5f) {
                switched = true
                label.text = "+"
                label.setTextColor(green)
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                tabCountBg?.setStroke((2 * density).toInt(), green)
            }
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                label.rotation = 0f
                label.setTextColor(Color.WHITE)
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                tabCountBg?.setStroke((2 * density).toInt(), Color.WHITE)
                updateTabCount()
                tabsAnimating = false
                onDone()
            }
        })
        anim.start()
    }

    // Jeden "puls": zvetsit 1,5x, chvili drzet, vratit zpet
    private fun pulse(view: View, onEnd: () -> Unit) {
        view.animate().scaleX(1.5f).scaleY(1.5f).setDuration(400).withEndAction {
            view.postDelayed({
                view.animate().scaleX(1f).scaleY(1f).setDuration(400).withEndAction { onEnd() }.start()
            }, 120)
        }.start()
    }

    // Oblibene: hvezdicka se od stredu k okrajum vyplni zlute (0-100 %), pak kolem ni vyleti jiskricky a mala hvezdicka.
    // Funkce tlacitka (klepnuti: Zalozky, drzeni: ulozit do slozky) se spusti, jakmile je hvezda cela zluta.
    private fun animateStar(onDone: () -> Unit) {
        if (starAnimating) return
        starAnimating = true
        val icon = findViewById<ImageView>(R.id.ivBookmarks)
        val loc = IntArray(2)
        val rl = IntArray(2)
        icon.getLocationInWindow(loc)
        root.getLocationInWindow(rl)
        val cx = loc[0] - rl[0] + icon.width / 2f
        val cy = loc[1] - rl[1] + icon.height / 2f
        val view = StarBurstView(this, cx, cy, icon.width.toFloat().coerceAtLeast(dp(24).toFloat()))
        view.elevation = dp(70).toFloat()   // nad spodni listou
        (root as ViewGroup).addView(view, ViewGroup.LayoutParams(-1, -1))
        var called = false
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 1300
        anim.addUpdateListener {
            val t = it.animatedValue as Float
            view.fill = (t / 0.4f).coerceIn(0f, 1f)
            view.burst = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            view.invalidate()
            if (!called && t >= 0.45f) {
                called = true
                onDone()
            }
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                (root as ViewGroup).removeView(view)
                starAnimating = false
                if (!called) { called = true; onDone() }
            }
        })
        anim.start()
    }

    // Klepnuti na Domu: cele tlacitko 2x problikne zelene
    private fun blinkHome() {
        if (homeAnimating) return
        homeAnimating = true
        val b = findViewById<View>(R.id.btnHome)
        val green = GradientDrawable()
        green.setColor(Color.parseColor("#00B055"))
        green.cornerRadius = dp(18).toFloat()
        green.setStroke(dp(1), Color.WHITE)
        var step = 0
        val r = object : Runnable {
            override fun run() {
                if (step >= 4) {
                    b.setBackgroundResource(R.drawable.btn_circle_dark)
                    homeAnimating = false
                    return
                }
                if (step % 2 == 0) b.background = green else b.setBackgroundResource(R.drawable.btn_circle_dark)
                step++
                b.postDelayed(this, 120)
            }
        }
        r.run()
    }

    // Klepnuti na Karty: ctverecek s cislem se otoci o 360 stupnu, pak se otevre prehled karet
    private fun spinTabs(onDone: () -> Unit) {
        if (tabsAnimating) return
        tabsAnimating = true
        val label = findViewById<TextView>(R.id.tvTabCount)
        val anim = ObjectAnimator.ofFloat(label, View.ROTATION, 0f, 360f)
        anim.duration = 450
        anim.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                label.rotation = 0f
                tabsAnimating = false
                onDone()
            }
        })
        anim.start()
    }

    // Domu: jen ikona - zelena ikona se odkryva zespoda nahoru, tlacitko samo se nemeni
    private fun animateHome(onDone: () -> Unit) {
        if (homeAnimating) return
        homeAnimating = true
        val green = findViewById<ImageView>(R.id.ivHomeGreen)
        val w = green.width
        val h = green.height
        if (w <= 0 || h <= 0) {
            homeAnimating = false
            onDone()
            return
        }
        green.visibility = View.VISIBLE
        green.clipBounds = Rect(0, h, w, h)
        val anim = ValueAnimator.ofInt(h, 0)
        anim.duration = 900
        anim.addUpdateListener { green.clipBounds = Rect(0, it.animatedValue as Int, w, h) }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // chvili zustane cela zelena, az potom se otevre okno
                green.postDelayed({
                    onDone()
                    green.postDelayed({
                        green.visibility = View.INVISIBLE
                        green.clipBounds = null
                        homeAnimating = false
                    }, 250)
                }, 250)
            }
        })
        anim.start()
    }

    // ---------------- SJEDNOCENE DIALOGY ----------------

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    internal fun styledButton(text: String, primary: Boolean): TextView {
        val b = TextView(this)
        b.text = text
        b.gravity = Gravity.CENTER
        b.setPadding(dp(18), dp(10), dp(18), dp(10))
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.setTextColor(if (primary) Color.parseColor("#06281D") else Color.parseColor("#CCCCCC"))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(if (primary) Color.parseColor("#00B055") else Color.parseColor("#2A2A2A"))
        b.background = bg
        return b
    }

    // Tmavy zaoblený panel jako zbytek appky; "vyskoci" (zvetseni + prolnuti) pri otevreni.
    // onPositive vraci true = dialog se zavre. bodyBuilder dostane funkci pro zavreni dialogu.
    internal fun showStyledDialog(
        title: String,
        positiveText: String? = null,
        onPositive: (() -> Boolean)? = null,
        negativeText: String? = "Zrušit",
        bodyBuilder: (dismiss: () -> Unit) -> View
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dismiss = { dialog.dismiss() }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(22), dp(20), dp(22), dp(16))
        val cardBg = GradientDrawable()
        cardBg.setColor(Color.parseColor("#1E1E1E"))
        cardBg.cornerRadius = dp(24).toFloat()
        cardBg.setStroke(dp(1), Color.parseColor("#2A2A2A"))
        card.background = cardBg

        val titleView = TextView(this)
        titleView.text = title
        titleView.setTextColor(Color.WHITE)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        titleView.setTypeface(titleView.typeface, Typeface.BOLD)
        card.addView(titleView)

        val bodyView = bodyBuilder(dismiss)
        val bodyLp = (bodyView.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bodyLp.topMargin = dp(14)
        card.addView(bodyView, bodyLp)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.END
        if (negativeText != null) {
            val neg = styledButton(negativeText, false)
            neg.setOnClickListener { dismiss() }
            buttons.addView(neg)
        }
        if (positiveText != null) {
            val pos = styledButton(positiveText, true)
            pos.setOnClickListener { if (onPositive == null || onPositive()) dismiss() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(10)
            buttons.addView(pos, lp)
        }
        val btnLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        btnLp.topMargin = dp(16)
        card.addView(buttons, btnLp)

        dialog.setContentView(card)
        dialog.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setDimAmount(0.6f)
        }
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        dialog.show()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(OvershootInterpolator(1.3f)).start()
    }

    private fun dialogLabel(text: String): TextView {
        val l = TextView(this)
        l.text = text.uppercase()
        l.setTextColor(Color.parseColor("#888888"))
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        l.setTypeface(l.typeface, Typeface.BOLD)
        return l
    }

    // Okno Nastaveni dostane aktualni stranku (pro "pridat sem" ve slozce zalozek)
    private fun settingsIntent(): Intent =
        Intent(this, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_PAGE_URL, webView.url)
            .putExtra(SettingsActivity.EXTRA_PAGE_TITLE, webView.title.orEmpty())

    // Klepnuti do pole (dole i nahore): pole se vycisti, otevre se klavesnice a ukazou se navrhy
    // Pozadi poli: spodni #D9D9D9, horni tmave; obrys (zelena) se kresli jen pri psani a pulzuje
    private fun setupFieldBackgrounds() {
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
    private fun startOutlinePulse(top: Boolean) {
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

    private fun stopOutlinePulse() {
        pulseAnim?.cancel()
        pulseAnim = null
        searchBg?.setStroke(0, Color.TRANSPARENT)
        urlBg?.setStroke(0, Color.TRANSPARENT)
    }

    private fun startFreshInput(field: EditText) {
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

    // true = prave se do pole vkladá adresa stranky (watchery pak nespousti naseptavac ani zrcadleni)
    private var prefillingUrl = false

    // Vlozi adresu aktualni stranky do pole a da kurzor na konec. false = neni co vlozit (nova karta, interni stranka)
    private fun prefillCurrentUrl(field: EditText): Boolean {
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
    private fun editCurrentUrl() {
        val field = if (etTopUrl.hasFocus()) etTopUrl else etSearchMain
        if (prefillCurrentUrl(field)) field.post { showKeyboard(field) }
    }

    // Pole ztratilo focus: pokud nepise zadne, vratit horni pole na adresu stranky a vycistit dolni
    private fun onInputFocusLost() {
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
    private fun submitInput(raw: String) {
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

    // Inline doplnovani (jako Chrome): pisu "goo" -> pole ukaze "google.com" s oznacenym zbytkem,
    // Enter ho otevre, dalsi pismeno nebo Backspace doplneni prepise/smaze. Zdroj = znamé domeny z historie a zalozek.
    private var knownHosts: List<String> = emptyList()
    private var autoFilling = false
    private var lastTypedLen = 0

    private fun hostOf(url: String): String? =
        // bez "www.", "m." a "mobile." (m.youtube.com se doplni i na "youtube")
        try { Uri.parse(url).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("mobile.")?.takeIf { it.contains('.') } } catch (e: Exception) { null }

    private fun refreshKnownHosts(history: List<HistoryVisit>, bookmarks: List<Bookmark>) {
        // radi se podle poctu navstev, pak podle novosti (history je od nejnovejsi)
        val counts = LinkedHashMap<String, Int>()
        for (h in history) hostOf(h.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        for (b in bookmarks) hostOf(b.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        knownHosts = counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun autoComplete(field: EditText, s: Editable) {
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
    private fun mirrorText(from: EditText, to: EditText) {
        if (syncingText || !from.hasFocus()) return
        syncingText = true
        to.setText(from.text.toString())
        to.setSelection(to.text.length)
        syncingText = false
    }

    internal fun isWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    // Zapise navstevu do historie (jen http/https, ne dvakrat po sobe stejnou adresu)
    private fun recordVisit(view: WebView?, url: String?) {
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
    private fun copyPinnedUrl(s: SearchSuggestion) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", s.url))
        // potvrzeni ("Zkopirovano") se ukaze primo na zelenem radku
    }

    private fun updateSuggestions(query: String) {
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

    private val suggestHandler = Handler(Looper.getMainLooper())
    private var suggestRunnable: Runnable? = null
    private var suggestCacheAt = 0L
    private var cachedHist: List<HistoryVisit> = emptyList()
    private var cachedBms: List<Bookmark> = emptyList()
    private var cachedTop: List<com.example.actimelteambrowser.data.MostVisitedSite> = emptyList()

    private fun openSuggestion(url: String) {
        if (!isWebUrl(url)) return
        webView.loadUrl(url)
        etSearchMain.clearFocus()
        etTopUrl.clearFocus()
        hideKeyboard()
        suggestions.value = emptyList()
    }

    // ---------------- SKRYVANI LIST PRI POSOUVANI ----------------

    private var chromeHidden = false

    private fun onWebScrolled(tab: BrowserTab, sy: Int, oldY: Int) {
        if (tab.id != tabs.activeId || !AppSettings.hideBarsOnScroll.get() || configMode || gestureConfigMode) return
        if (etSearchMain.hasFocus() || etTopUrl.hasFocus() || leftBarOpen || customView != null) return
        val dy = sy - oldY
        when {
            sy <= dp(4) -> setChromeHidden(false)        // jsme nahore
            dy > dp(4) -> setChromeHidden(true)          // posouvame dolu = cteme
            dy < -dp(4) -> setChromeHidden(false)        // posun nahoru = lisly zpet
        }
    }

    // Zakladni (neschovana) poloha bocniho tlacitka podle "vysouvaci"
    private fun sideBaseX(n: Int): Float =
        if (AppSettings.buttonSlide[n - 1].get() && !configMode && !(n == barOrigin && leftBarOpen)) hiddenX(n) else 0f

    // Schova / vrati horni a dolni listu a bocni tlacitka (vsechna, i pridana)
    internal fun setChromeHidden(hidden: Boolean) {
        if (chromeHidden == hidden) return
        chromeHidden = hidden
        val d = 220L
        topBar.animate().translationY(if (hidden) -(topBar.height + dp(24)).toFloat() else 0f).setDuration(d).start()
        bottomBar.animate().translationY(if (hidden) (bottomBar.height + dp(24)).toFloat() else lastImeShift).setDuration(d).start()
        val off = dp(120).toFloat()
        for (n in listOf(5, 6) + extraViews.keys) {
            val v = buttonView(n) ?: continue
            val x = if (hidden) (if (isLeftSide(n)) -off else off) else sideBaseX(n)
            v.animate().translationX(x).setDuration(d).start()
        }
    }

    private fun toggleTopBar() {
        isTopBarExpanded = !isTopBarExpanded
        val targetY = 0f
        topBar.animate().translationY(targetY).setDuration(250).start()
    }


    // Skryva reklamni prvky podle EasyListu: CSS pro domenu + obecna pravidla (tridy/id ze stranky se posilaji do Rustu)
    internal val cosmeticJs: String = """
(function () {
  if (!document.documentElement) return;
  var css = window.__atbCss || '';
  function put(id, text) {
    try {
      if (document.adoptedStyleSheets !== undefined && window.CSSStyleSheet && CSSStyleSheet.prototype.replaceSync) {
        var m = (window.__atbSheets = window.__atbSheets || {});
        var sh = m[id];
        if (!sh) {
          sh = m[id] = new CSSStyleSheet();
          document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
        }
        sh.replaceSync(text);
        return;
      }
    } catch (e) {}
    var s = document.getElementById(id);
    if (!s) {
      s = document.createElement('style');
      s.id = id;
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = text;
  }
  put('__atb_cos', css);
  if (css.indexOf('/*generichide*/') === 0 || window.__atbCosOn || !window.AtbCos) return;
  window.__atbCosOn = true;
  var seenC = Object.create(null), seenI = Object.create(null);
  var pc = [], pi = [], extra = '', timer = null;
  function take(e) {
    if (e.id && !seenI[e.id]) { seenI[e.id] = 1; pi.push(e.id); }
    var cl = e.classList;
    if (cl) for (var j = 0; j < cl.length; j++) {
      var c = cl[j];
      if (!seenC[c]) { seenC[c] = 1; pc.push(c); }
    }
  }
  function addNode(n) {
    if (!n || n.nodeType !== 1) return;
    take(n);
    var d = n.querySelectorAll ? n.querySelectorAll('[class],[id]') : [];
    for (var i = 0; i < d.length; i++) take(d[i]);
  }
  function flush() {
    timer = null;
    if (!pc.length && !pi.length) return;
    var c = pc.join('\n'), i = pi.join('\n');
    pc = []; pi = [];
    var r = '';
    try { r = window.AtbCos.q(c, i); } catch (e) {}
    if (r) { extra += r; put('__atb_cos2', extra); }
  }
  addNode(document.documentElement);
  flush();
  try {
    new MutationObserver(function (list) {
      for (var k = 0; k < list.length; k++) {
        var m = list[k];
        if (m.type === 'attributes') take(m.target);
        else for (var a = 0; a < m.addedNodes.length; a++) addNode(m.addedNodes[a]);
      }
      if (!timer) timer = setTimeout(flush, 300);
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
  } catch (e) {}
})();
""".trimIndent()

    // Most pro JS ve strance: dostane tridy/id prvku a vrati CSS jen pro ty, ktere jsou v seznamech
    inner class CosmeticBridge {
        @JavascriptInterface
        fun q(classes: String, ids: String): String {
            if (!isAdBlockOn || !adblockReady) return ""
            return try { RustBridge.hiddenSelectors(classes, ids) } catch (e: Throwable) { "" }
        }
    }

    // Pripony pro odhad typu zdroje (hotove konstanty, aby se na kazdy pozadavek nekompilovalo Regex)
    internal val IMAGE_EXTS = arrayOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg")
    internal val MEDIA_EXTS = arrayOf(".mp4", ".webm", ".m3u8", ".mpd", ".ts")

    // ---------------- KARTY (taby) ----------------

    // Vytvori novou kartu s vlastnim WebView (a volitelne ji zobrazi a nacte adresu)
    // (@SuppressLint je na buildWebView)
    private fun createTab(url: String?, activate: Boolean = true, incognito: Boolean = false): BrowserTab {
        // WebView se postavi az pri prvnim pouziti (buildWebView); obnovene karty na pozadi ho tedy nemaji
        var ref: BrowserTab? = null
        val tab = BrowserTab(tabs.newId()) { buildWebView(ref!!) }
        ref = tab
        tab.incognito = incognito
        tabs.list.add(tab)
        if (activate) switchTo(tab)
        updateTabCount()
        if (url != null) tab.webView.loadUrl(url)
        if (!activate && tab.webViewCreated) { try { tab.webView.onPause() } catch (e: Exception) { } }   // karta na pozadi nema bezet
        return tab
    }

    // Postavi WebView karty: nastaveni, anonymni profil, klienti (AdBlock, ikony, oprávnění, scroll)
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tab: BrowserTab): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(CosmeticBridge(), "AtbCos")   // kosmeticke filtry (viz injectCosmetic)
        // bez setLayerType: WebView si vykresluje samo, hardwarova vrstva by drzela GPU pamet na kazdou kartu
        if (tab.incognito) {
            // anonymni karta: vlastni profil WebView = oddelene cookies, localStorage a cache od beznych karet
            // (smaze se po zavreni posledni anonymni karty). Musi se nastavit PRED nactenim cehokoliv.
            if (incognitoProfilesSupported()) {
                try { WebViewCompat.setProfile(wv, INCOGNITO_PROFILE) } catch (e: Exception) { }
            }
            wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            wv.settings.saveFormData = false
            wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS   // bez automatickeho vyplnovani
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request != null && adBlockFor(tab.pageUrl) && shouldBlockRequest(request, tab)) {
                    onRequestBlocked(tab)
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }
            // System zabil proces vykreslovani (malo pameti / pad): bez teto metody spadne cela appka.
            // Karta se nahradi novou se stejnou adresou.
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val lostUrl = tab.url.ifBlank { tab.pendingUrl ?: homeUrl }
                val wasActive = tab.id == tabs.activeId
                val wasIncognito = tab.incognito
                val idx = tabs.list.indexOf(tab)
                Handler(Looper.getMainLooper()).post {
                    // nejdriv nova karta (aby nikdy nezustal prazdny seznam), pak se stara zavre
                    val fresh = createTab(lostUrl, activate = false, incognito = wasIncognito)
                    tabs.list.remove(fresh)
                    tabs.list.add(idx.coerceIn(0, tabs.list.size), fresh)   // stejne misto v poradi
                    closeTab(tab.id)
                    if (wasActive) switchTo(fresh)
                    updateTabCount()
                    refreshTiles()
                    Toast.makeText(this@MainActivity, "Stránka se zhroutila, načítám znovu", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // jiny web = stara ikona uz neplati (nova prijde pres onReceivedIcon)
                val oldHost = try { Uri.parse(tab.url).host } catch (e: Exception) { null }
                val newHost = try { Uri.parse(url).host } catch (e: Exception) { null }
                if (oldHost != newHost) tab.favicon = null
                tab.url = url.orEmpty()
                setChromeHidden(false)   // nova stranka: lisly zase nahore
                // verze pro pocitac se pamatuje podle webu: pri prechodu na jiny web se pouzije jeho ulozena volba
                if (!tab.incognito && url != null && isWebUrl(url)) {
                    val want = SiteRules.desktopOn(newHost)
                    if (want != tab.desktop) {
                        applyDesktop(tab, want)
                        view?.stopLoading()
                        view?.loadUrl(url)
                        if (tab.id == tabs.activeId) updateDeskBtn()
                        return
                    }
                }
                if (tab.startPage) {
                    tab.startPage = false
                    if (tab.id == tabs.activeId) updateStartPage()
                }
                tab.blocked.set(0)
                if (tab.id == tabs.activeId) updateAdBlockBadge()
                injectYoutubeAdBlock(view, url)
                injectCosmetic(view, url)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                tab.url = url.orEmpty()
                tab.title = view?.title.orEmpty()
                if (tab.id == tabs.activeId) {
                    etTopUrl.setText(topUrlText(url))
                updateLockIcon(url)
                    progressBar.visibility = View.GONE
                }
                injectYoutubeAdBlock(view, url)
                injectCosmetic(view, url)
                rejectCookies(view)
                if (!tab.incognito) recordVisit(view, url)   // anonymni karta se do historie nezapisuje
                // po chvili se ulozi nahled stranky (pro dlazdice nejnavstevovanejsich webu), jen kdyz je karta porad videt
                val finishedHost = try { Uri.parse(url).host?.removePrefix("www.") } catch (e: Exception) { null }
                if (!tab.incognito && AppSettings.thumbsOn.get() && (finishedHost == null || thumbStale(finishedHost))) view?.postDelayed({
                    if (tab.id == tabs.activeId && tab.url == url.orEmpty() && tabsView.visibility != View.VISIBLE &&
                        lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                    ) capturePreview(tab) { }
                }, 3000)
            }
        }
        wv.isLongClickable = true
        wv.setOnLongClickListener { onWebLongPress(wv) }   // false = text/prazdne misto: vyber textu zustava systemu
        wv.setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
            startDownload(dlUrl, userAgent, contentDisposition, mimeType)
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tab.id != tabs.activeId) return
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title.orEmpty()
            }
            // ikona webu pro nasepavac (aktualni stranka)
            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                if (icon == null) return
                tab.favicon = icon
                // ikona se ulozi k webu (jen bezne karty), aby ji ukazaly dlazdice nejnavstevovanejsich
                if (!tab.incognito) {
                    val h = try { Uri.parse(view?.url).host?.removePrefix("www.") } catch (e: Exception) { null }
                    if (!h.isNullOrBlank()) saveSiteIcon(h, icon)
                }
            }
            // web chce kameru / mikrofon: rozhodne ulozena volba pro web, jinak se zeptame
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val host = request.origin?.host.orEmpty()
                val types = ArrayList<String>()
                for (r in request.resources) {
                    if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) types.add("cam")
                    if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE) types.add("mic")
                }
                if (types.isEmpty()) { request.deny(); return }
                handleSitePermission(host, types, !tab.incognito) { allowed ->
                    val res = request.resources.filter {
                        (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && "cam" in allowed) ||
                            (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && "mic" in allowed)
                    }
                    if (res.isEmpty()) request.deny() else request.grant(res.toTypedArray())
                }
            }
            // web chce polohu
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                if (origin == null || callback == null) return
                val host = try { Uri.parse(origin).host.orEmpty() } catch (e: Exception) { "" }
                handleSitePermission(host, listOf("geo"), !tab.incognito) { allowed ->
                    callback.invoke(origin, "geo" in allowed, false)
                }
            }
            // video na celou obrazovku (YouTube apod.)
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) { callback?.onCustomViewHidden(); return }
                if (customView != null) { callback?.onCustomViewHidden(); return }
                customView = view
                customViewCallback = callback
                val holder = FrameLayout(this@MainActivity)
                holder.setBackgroundColor(Color.BLACK)
                holder.elevation = dp(60).toFloat()   // nad vsemi tlacitky i prehledem karet
                holder.addView(view, FrameLayout.LayoutParams(-1, -1))
                (root as ViewGroup).addView(holder, ViewGroup.LayoutParams(-1, -1))
                fullscreenHolder = holder
                // jako v appce YouTube: video na celou obrazovku se samo otoci na sirku
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            override fun onHideCustomView() { exitFullscreenVideo() }
        }
        // klik do webu schova topBar zase na 75%
        wv.setOnTouchListener { _, _ ->
            if (isTopBarExpanded) toggleTopBar()
            false
        }
        // posouvani dolu schova listy a bocni tlacitka, posun nahoru je vrati (Konfigurace)
        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY -> onWebScrolled(tab, scrollY, oldScrollY) }

        return wv
    }

    // Text v poli s adresou nahore (puvodni chovani)
    private fun topUrlText(url: String?): String =
        url?.replace("https://", "")?.replace("/", "") ?: ""

    // Zobrazi danou kartu (jeji WebView vlozi do kontejneru)
    private fun switchTo(tab: BrowserTab) {
        // predchozi karta se pozastavi (JS, casovace, animace) - setri baterii a vykon
        if (::webView.isInitialized && webView !== tab.webView) {
            try { webView.onPause() } catch (e: Exception) { }
        }
        try { tab.webView.onResume() } catch (e: Exception) { }
        webContainer.removeAllViews()
        webContainer.addView(tab.webView)
        tabs.activeId = tab.id
        webView = tab.webView
        setChromeHidden(false)
        // obnovena karta se nacte az ted, kdyz se poprve zobrazi
        tab.pendingUrl?.let {
            tab.pendingUrl = null
            tab.webView.loadUrl(it)
        }
        etTopUrl.setText(topUrlText(tab.url))
                updateLockIcon(tab.url)
        // anonymni karta: adresa nahore je svetle fialova
        etTopUrl.setTextColor(if (tab.incognito) Color.parseColor("#CE93D8") else Color.WHITE)
        progressBar.visibility = View.GONE
        updateAdBlockBadge()
        updateTabCount()
        updateStartPage()
        updateDeskBtn()
        // anonymni karta: zakaz screenshotu a nahledu v seznamu aplikaci (jako ostatni prohlizece)
        if (tab.incognito) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ---------------- SLOZKY A PORADI KARET V PREHLEDU ----------------

    private fun groupOf(tabId: Long): TabGroup? = groups.firstOrNull { tabId in it.members }

    // Polozky prehledu v poradi karet: jedna karta, nebo slozka (na miste sve prvni karty)
    private fun buildItems(incognito: Boolean): MutableList<Pair<TabGroup?, List<BrowserTab>>> {
        val modeTabs = tabs.list.filter { it.incognito == incognito }
        val items = ArrayList<Pair<TabGroup?, List<BrowserTab>>>()
        val seen = HashSet<Long>()
        for (t in modeTabs) {
            if (t.id in seen) continue
            val g = groupOf(t.id)
            if (g == null) {
                items.add(Pair(null, listOf(t)))
                seen.add(t.id)
            } else {
                val ms = g.members.mapNotNull { id -> modeTabs.firstOrNull { it.id == id } }
                items.add(Pair(g, ms))
                for (x in ms) seen.add(x.id)
            }
        }
        return items
    }

    // Slozky s mene nez 2 kartami zanikaji, zavrene karty se ze slozek odeberou
    private fun pruneGroups() {
        val alive = tabs.list.map { it.id }.toHashSet()
        for (g in groups) g.members.retainAll(alive)
        groups.removeAll { it.members.size < 2 }
    }

    // Presune kartu / slozku na misto jine (pretahovani v prehledu); slozka se presouva i se vsemi kartami
    private fun moveTab(fromId: Long, toId: Long) {
        val items = buildItems(overviewIncognito)
        fun idx(id: Long) = items.indexOfFirst { (g, ms) -> if (id < 0) g?.id == -id else g == null && ms[0].id == id }
        val from = idx(fromId)
        val to = idx(toId)
        if (from < 0 || to < 0 || from == to) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        val flat = items.flatMap { it.second }
        val positions = tabs.list.indices.filter { tabs.list[it].incognito == overviewIncognito }
        if (positions.size != flat.size) return
        for ((k, p) in positions.withIndex()) tabs.list[p] = flat[k]
        refreshTiles()
    }

    // Poradi karet uvnitr otevrene slozky
    private fun moveInGroup(groupTileId: Long, fromId: Long, toId: Long) {
        val g = groups.firstOrNull { it.id == -groupTileId } ?: return
        val from = g.members.indexOf(fromId)
        val to = g.members.indexOf(toId)
        if (from < 0 || to < 0 || from == to) return
        g.members.add(to, g.members.removeAt(from))
        refreshTiles()
    }

    // Karta pustena na hlavicku jine karty = nova slozka, na hlavicku slozky = pridat do slozky
    private fun mergeTabs(dragId: Long, targetId: Long) {
        if (dragId < 0 || dragId == targetId) return
        if (groupOf(dragId) != null) return
        val members: MutableList<Long>
        if (targetId < 0) {
            val g = groups.firstOrNull { it.id == -targetId } ?: return
            g.members.add(dragId)
            members = g.members
        } else {
            if (groupOf(targetId) != null) return
            val g = TabGroup(nextGroupId++, mutableListOf(targetId, dragId))
            groups.add(g)
            members = g.members
        }
        // pretazena karta se v seznamu presune hned za posledni kartu slozky (slozka zustane na miste cile)
        val dragTab = tabs.list.firstOrNull { it.id == dragId } ?: return
        tabs.list.remove(dragTab)
        val lastIdx = tabs.list.indices.filter { tabs.list[it].id in members && tabs.list[it].id != dragId }.maxOrNull() ?: (tabs.list.size - 1)
        tabs.list.add(lastIdx + 1, dragTab)
        refreshTiles()
    }

    // Vyjme karty ze slozky (zustanou otevrene jako samostatne)
    private fun extractTabs(ids: List<Long>) {
        for (g in groups) g.members.removeAll(ids.toSet())
        pruneGroups()
        refreshTiles()
    }

    // Zavre vic karet najednou (id slozky = zavre vsechny jeji karty)
    private fun closeTabs(ids: List<Long>) {
        val all = HashSet<Long>()
        for (id in ids) {
            if (id < 0) groups.firstOrNull { it.id == -id }?.let { all.addAll(it.members) } else all.add(id)
        }
        val toClose = tabs.list.filter { it.id in all }
        if (toClose.isEmpty()) return
        val activeClosed = toClose.any { it.id == tabs.activeId }
        val mode = toClose[0].incognito
        for (tab in toClose) {
            rememberClosed(tab)
            disposeTab(tab)
        }
        tabs.list.removeAll(toClose)
        pruneGroups()
        if (tabs.list.isEmpty()) createTab(homeUrl)
        else if (activeClosed) {
            val same = tabs.list.filter { it.incognito == mode }
            switchTo(if (same.isNotEmpty()) same.last() else tabs.list.last())
        }
        updateTabCount()
        refreshTiles()
    }

    // Zavre vsechny karty a otevre jednu novou s domovskou strankou
    private fun closeAllTabs() {
        clearAllTabs()
        createTab(homeUrl)
        refreshTiles()
    }

    // ---------------- ANONYMNI PROFIL ----------------

    private fun incognitoProfilesSupported(): Boolean =
        try { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) } catch (e: Throwable) { false }

    // Smaze vsechna data anonymniho profilu (cookies, localStorage...). Smazani profilu selze, dokud ho nejaky WebView
    // pouziva - pak se aspon vymazou data.
    private fun purgeIncognito() {
        if (!incognitoProfilesSupported()) return
        try {
            ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE)
        } catch (e: Throwable) {
            try {
                val p = ProfileStore.getInstance().getProfile(INCOGNITO_PROFILE)
                p?.cookieManager?.removeAllCookies(null)
                p?.webStorage?.deleteAllData()
            } catch (e2: Throwable) { }
        }
    }

    // Cookies pro dane okno: anonymni karta ma vlastni (jinak by se pri stahovani poslaly cookies z beznych karet)
    private fun cookiesFor(url: String, incognito: Boolean): String? {
        if (incognito && incognitoProfilesSupported()) {
            try {
                return ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE).cookieManager.getCookie(url)
            } catch (e: Throwable) { }
            return null
        }
        return CookieManager.getInstance().getCookie(url)
    }

    // Odstrani WebView karty; u anonymni karty nejdriv smaze cache, formulare a historii prochazeni
    private fun disposeTab(tab: BrowserTab) {
        if (!tab.webViewCreated) return   // WebView se nikdy nevytvoril - neni co rusit
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        if (tab.incognito) {
            try {
                tab.webView.clearCache(true)
                tab.webView.clearFormData()
                tab.webView.clearHistory()
            } catch (e: Exception) {
            }
        }
        try { tab.webView.destroy() } catch (e: Exception) { }
        // po zavreni posledni anonymni karty smazat jeji data (az po dokonceni prave probihajici akce)
        if (tab.incognito) window.decorView.post { if (tabs.list.none { it.incognito }) purgeIncognito() }
    }

    // Prehled karet: "Zavrit vse" zavre jen karty te skupiny, kterou prave vidis
    private fun closeAllInMode(incognito: Boolean) {
        val toClose = tabs.list.filter { it.incognito == incognito }
        val activeClosed = toClose.any { it.id == tabs.activeId }
        for (tab in toClose) {
            rememberClosed(tab)
            disposeTab(tab)
        }
        tabs.list.removeAll(toClose)
        pruneGroups()
        if (tabs.list.isEmpty()) createTab(homeUrl)
        else if (activeClosed) switchTo(tabs.list.last())
        updateTabCount()
        refreshTiles()
    }

    private fun clearAllTabs() {
        for (tab in tabs.list) {
            rememberClosed(tab)
            disposeTab(tab)
        }
        tabs.list.clear()
        groups.clear()
        tabs.activeId = -1L
    }

    // ULOZENI A OBNOVA KARET: pri opusteni appky se ulozi adresy, po spusteni se obnovi
    private fun saveTabs() {
        val arr = JSONArray()
        var activeIdx = 0
        val savedIdx = HashMap<Long, Int>()   // id karty -> poradi v ulozenem seznamu (pro slozky)
        // nahledy se zapisuji na pozadi a jen zmenene (viz writePreviewsAsync); tady se jen sebere jejich poradi
        val previewDir = File(filesDir, "tab_previews")
        val previews = ArrayList<Bitmap?>()
        for (tab in tabs.list) {
            if (arr.length() >= 50) break
            if (tab.incognito) continue   // anonymni karty se neukladaji
            val u = tab.pendingUrl ?: tab.url.ifBlank { tab.liveUrl() }
            if (!isWebUrl(u)) continue
            if (tab.id == tabs.activeId) activeIdx = arr.length()
            savedIdx[tab.id] = arr.length()
            previews.add(tab.preview)
            arr.put(JSONObject().put("url", u).put("title", tab.title))
        }
        if (arr.length() == 0) return
        // slozky: seznamy poradi karet v ulozenem seznamu
        val garr = JSONArray()
        for (g in groups) {
            val idxs = g.members.mapNotNull { savedIdx[it] }
            if (idxs.size >= 2) garr.put(JSONObject().put("m", JSONArray(idxs)).put("c", g.color))
        }
        AppSettings.savedTabs.set(JSONObject().put("active", activeIdx).put("tabs", arr).put("groups", garr).toString())
        writePreviewsAsync(previewDir, previews)
    }

    // Jedno vlakno = zapisy se nepredbihaji. previewWritten pamatuje, ktery obrazek je ulozen v p<i>.jpg (jen z tohoto vlakna).
    private val previewExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val previewWritten = ArrayList<java.lang.ref.WeakReference<Bitmap>?>()

    // Ulozi nahledy karet mimo UI vlakno: prepise jen ty, ktere se zmenily, a smaze p<i>.jpg navic
    private fun writePreviewsAsync(dir: File, previews: List<Bitmap?>) {
        previewExecutor.execute {
            try {
                dir.mkdirs()
                for (i in previews.indices) {
                    while (previewWritten.size <= i) previewWritten.add(null)
                    val bmp = previews[i]
                    val file = File(dir, "p$i.jpg")
                    if (bmp == null) {
                        file.delete()
                        previewWritten[i] = null
                    } else if (previewWritten[i]?.get() !== bmp || !file.exists()) {
                        savePreview(bmp, file)
                        previewWritten[i] = java.lang.ref.WeakReference(bmp)
                    }
                }
                dir.listFiles()?.forEach { f ->
                    val n = f.name.removePrefix("p").removeSuffix(".jpg").toIntOrNull()
                    if (n == null || n >= previews.size) f.delete()
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun savePreview(bmp: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        } catch (e: Exception) {
        }
    }

    private fun loadPreview(file: File): Bitmap? =
        try {
            if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        } catch (e: Exception) {
            null
        }

    // Drzeni tlacitka Domu: adresa domovske stranky + co delat po spusteni prohlizece
    private fun showHomeDialog() {
        val green = Color.parseColor("#00B055")
        var inputRef: EditText? = null
        var restoreRef: RadioButton? = null
        var engineBtns: List<Pair<String, RadioButton>> = emptyList()

        showStyledDialog(
            title = "Domů",
            positiveText = "Uložit",
            onPositive = {
                var u = inputRef?.text?.toString().orEmpty().trim()
                if (u.isNotEmpty() && !u.contains("://")) u = "https://$u"
                if (!isWebUrl(u)) {
                    Toast.makeText(this, "Neplatná adresa (musí být http nebo https)", Toast.LENGTH_LONG).show()
                    false
                } else {
                    AppSettings.homeUrl.set(u)
                    AppSettings.startupRestoreTabs.set(restoreRef?.isChecked == true)
                    engineBtns.firstOrNull { it.second.isChecked }?.let { AppSettings.searchEngine.set(it.first) }
                    Toast.makeText(this, "Uloženo", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        ) { _ ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL

            box.addView(dialogLabel("Domovská stránka"))
            val input = EditText(this)
            input.setText(AppSettings.homeUrl.get())
            input.setSingleLine(true)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(Color.parseColor("#888888"))
            input.setPadding(dp(14), dp(12), dp(14), dp(12))
            val inputBg = GradientDrawable()
            inputBg.setColor(Color.parseColor("#2A2A2A"))
            inputBg.cornerRadius = dp(14).toFloat()
            input.background = inputBg
            val inLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            inLp.topMargin = dp(6)
            box.addView(input, inLp)
            inputRef = input

            // vyplni pole adresou stranky, ktera je prave otevrena (ulozi se az tlacitkem Ulozit)
            val addCurrent = styledButton("Přidat aktuální stránku", false)
            addCurrent.setOnClickListener {
                val u = webView.url
                if (u != null && isWebUrl(u)) {
                    input.setText(u)
                    input.setSelection(input.text.length)
                } else {
                    Toast.makeText(this, "Tuhle stránku nelze nastavit jako domovskou", Toast.LENGTH_SHORT).show()
                }
            }
            val acLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            acLp.topMargin = dp(8)
            box.addView(addCurrent, acLp)

            val startLabel = dialogLabel("Po spuštění prohlížeče")
            val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            slp.topMargin = dp(18)
            box.addView(startLabel, slp)

            val group = RadioGroup(this)
            val rbRestore = RadioButton(this)
            rbRestore.id = View.generateViewId()
            rbRestore.text = "Obnovit poslední karty"
            val rbNew = RadioButton(this)
            rbNew.id = View.generateViewId()
            rbNew.text = "Nová karta (domovská stránka)"
            for (rb in listOf(rbRestore, rbNew)) {
                rb.setTextColor(Color.WHITE)
                rb.buttonTintList = ColorStateList.valueOf(green)
            }
            group.addView(rbRestore)
            group.addView(rbNew)
            (if (AppSettings.startupRestoreTabs.get()) rbRestore else rbNew).isChecked = true
            box.addView(group)
            restoreRef = rbRestore

            // vyhledavac (stejne nastaveni jako v Konfiguraci)
            val engLabel = dialogLabel("Vyhledávač")
            val elp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            elp.topMargin = dp(18)
            box.addView(engLabel, elp)
            val engGroup = RadioGroup(this)
            val btns = ArrayList<Pair<String, RadioButton>>()
            for (e in SearchEngines.list) {
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = e.name
                rb.setTextColor(Color.WHITE)
                rb.buttonTintList = ColorStateList.valueOf(green)
                engGroup.addView(rb)
                btns.add(Pair(e.key, rb))
            }
            (btns.firstOrNull { it.first == SearchEngines.current().key } ?: btns[0]).second.isChecked = true
            box.addView(engGroup)
            engineBtns = btns
            box
        }
    }

    private fun restoreTabs() {
        val urls = ArrayList<String>()
        val titles = ArrayList<String>()
        val srcIdx = ArrayList<Int>()   // poradi v ulozenem seznamu pro kazdou obnovenou kartu
        var groupsJson: JSONArray? = null
        var active = 0
        try {
            val o = JSONObject(AppSettings.savedTabs.get())
            active = o.optInt("active")
            groupsJson = o.optJSONArray("groups")
            val a = o.optJSONArray("tabs")
            if (a != null) {
                for (i in 0 until minOf(a.length(), 50)) {
                    val e = a.optJSONObject(i) ?: continue
                    val u = e.optString("url")
                    if (isWebUrl(u)) {
                        urls.add(u)
                        titles.add(e.optString("title"))
                        srcIdx.add(i)
                    }
                }
            }
        } catch (e: Exception) {
        }
        if (urls.isEmpty()) {
            createTab(homeUrl)
            return
        }
        val created = ArrayList<BrowserTab>()
        for (i in urls.indices) {
            val tab = createTab(null, false)
            tab.url = urls[i]
            tab.title = titles[i]
            tab.pendingUrl = urls[i]
            tab.preview = loadPreview(File(File(filesDir, "tab_previews"), "p" + i + ".jpg"))
            created.add(tab)
        }
        // obnova slozek (jen platne a s aspon 2 kartami)
        try {
            val ga = groupsJson
            if (ga != null) {
                for (gi in 0 until ga.length()) {
                    // novy format {"m": [...], "c": barva}; starsi format = jen seznam poradi
                    val obj = ga.optJSONObject(gi)
                    val one = obj?.optJSONArray("m") ?: ga.optJSONArray(gi) ?: continue
                    val ids = ArrayList<Long>()
                    for (k in 0 until one.length()) {
                        val p = srcIdx.indexOf(one.optInt(k, -1))
                        if (p >= 0 && groupOf(created[p].id) == null) ids.add(created[p].id)
                    }
                    if (ids.size >= 2) {
                        val g = TabGroup(nextGroupId++, ids)
                        if (obj != null && obj.has("c")) g.color = obj.optInt("c", g.color)
                        groups.add(g)
                    }
                }
            }
        } catch (e: Exception) {
        }
        switchTo(created[active.coerceIn(0, created.size - 1)])
        updateTabCount()
    }

    private fun closeTab(id: Long) {
        val idx = tabs.list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val tab = tabs.list.removeAt(idx)
        rememberClosed(tab)
        val wasActive = tab.id == tabs.activeId
        disposeTab(tab)
        pruneGroups()
        if (tabs.list.isEmpty()) {
            createTab(homeUrl)          // vzdy zustane aspon jedna karta
        } else if (wasActive) {
            // radsi karta ze stejne skupiny (bezna / anonymni)
            val same = tabs.list.filter { it.incognito == tab.incognito }
            switchTo(if (same.isNotEmpty()) same[minOf(idx, same.size - 1)] else tabs.list.last())
        }
        updateTabCount()
        refreshTiles()
    }

    // Cislo v ctverecku na tlacitku
    private fun updateTabCount() {
        // cislo se ukazuje jen kdyz ma tlacitko 2 vychozi funkci (jinak je tam ikona funkce)
        val incog = tabs.active()?.incognito ?: false
        if (isDefaultVisual(2)) findViewById<TextView>(R.id.tvTabCount).text = tabs.list.count { it.incognito == incog }.toString()
    }

    private fun setupTabsOverlay() {
        tabsView.setContent {
            TabsScreen(
                tiles = tabTiles.value,
                incognito = overviewIncognito,
                normalCount = tabs.list.count { !it.incognito },
                incognitoCount = tabs.list.count { it.incognito },
                onToggleIncognito = { inc ->
                    overviewIncognito = inc
                    refreshTiles()
                },
                onSelect = { id ->
                    tabs.list.firstOrNull { it.id == id }?.let { switchTo(it) }
                    hideTabs()
                },
                onClose = { id -> closeTab(id) },
                onSave = { id -> saveTabToFolder(id) },
                onMove = { fromId, toId -> moveTab(fromId, toId) },
                onMoveInFolder = { folderId, fromId, toId -> moveInGroup(folderId, fromId, toId) },
                onMerge = { dragId, targetId -> mergeTabs(dragId, targetId) },
                onCloseMany = { ids -> closeTabs(ids) },
                onExtract = { ids -> extractTabs(ids) },
                onFolderColor = { tileId, color ->
                    groups.firstOrNull { it.id == -tileId }?.color = color
                    refreshTiles()
                },
                onNew = {
                    if (overviewIncognito) createTab(homeUrl, incognito = true) else openStartTab()
                    hideTabs()
                },
                onDismiss = { hideTabs() }
            )
        }
    }

    private fun openTabsOverview() {
        hideKeyboard()
        etSearchMain.clearFocus()
        val active = tabs.active()
        overviewIncognito = active?.incognito == true   // prehled se otevre ve skupine aktivni karty
        if (active == null) showTabs() else capturePreview(active) { showTabs() }
    }

    private fun showTabs() {
        refreshTiles()
        tabsView.visibility = View.VISIBLE
    }

    internal fun hideTabs() {
        tabsView.visibility = View.GONE
    }

    private fun refreshTiles() {
        fun tileOf(t: BrowserTab) = TabTile(t.id, t.title, t.url, t.preview, t.id == tabs.activeId, t.incognito)
        tabTiles.value = buildItems(overviewIncognito).map { (g, ms) ->
            if (g == null) tileOf(ms[0])
            else TabTile(-g.id, "", "", null, ms.any { it.id == tabs.activeId }, overviewIncognito, true, ms.map { tileOf(it) }, g.color)
        }
    }

    // Ulozi nahled (screenshot) karty, hotovo() se zavola vzdy (i kdyz se nahled nepovede)
    private fun capturePreview(tab: BrowserTab, done: () -> Unit) {
        val wv = tab.webView
        if (wv.width <= 0 || wv.height <= 0) {
            done()
            return
        }
        val bw = 360
        val bh = maxOf(1, wv.height * bw / wv.width)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val loc = IntArray(2)
            wv.getLocationInWindow(loc)
            val rect = Rect(loc[0], loc[1], loc[0] + wv.width, loc[1] + wv.height)
            try {
                PixelCopy.request(window, rect, bmp, PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (result == PixelCopy.SUCCESS) {
                        tab.preview = bmp
                        saveSiteThumb(tab)
                    }
                    done()
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                done()
            }
        } else {
            try {
                val canvas = Canvas(bmp)
                canvas.scale(bw.toFloat() / wv.width, bw.toFloat() / wv.width)
                wv.draw(canvas)
                tab.preview = bmp
                saveSiteThumb(tab)
            } catch (e: Exception) {
            }
            done()
        }
    }

    // Drzeni hvezdy: nabidne slozky a ulozi aktualni stranku do vybrane
    private fun addCurrentPageToFolder() = chooseFolderAndSave(webView.url, webView.title.orEmpty())

    // Hvezdicka na dlazdici karty: ulozi stranku teto karty do vybrane slozky
    private fun saveTabToFolder(id: Long) {
        val tab = tabs.list.firstOrNull { it.id == id } ?: return
        val u = tab.pendingUrl ?: tab.url.ifBlank { tab.liveUrl() }
        chooseFolderAndSave(u, tab.title)
    }

    private fun chooseFolderAndSave(curUrl: String?, pageTitle: String) {
        if (curUrl == null || !isWebUrl(curUrl)) {
            Toast.makeText(this, "Tuhle stranku nelze ulozit", Toast.LENGTH_SHORT).show()
            return
        }
        thread(name = "bookmark-folders") {
            val folders = try {
                bookmarksRepo.foldersForPicker()
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (folders.isEmpty()) return@runOnUiThread
                showStyledDialog(title = "Přidat do složky") { dismiss ->
                    val list = LinearLayout(this)
                    list.orientation = LinearLayout.VERTICAL
                    for (f in folders) {
                        val row = TextView(this)
                        row.text = "📁  " + f.name
                        row.setTextColor(Color.WHITE)
                        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        row.setPadding(dp(16), dp(14), dp(16), dp(14))
                        val rowBg = GradientDrawable()
                        rowBg.setColor(Color.parseColor("#2A2A2A"))
                        rowBg.cornerRadius = dp(14).toFloat()
                        row.background = rowBg
                        row.setOnClickListener {
                            dismiss()
                            thread(name = "bookmark-add") {
                                try {
                                    bookmarksRepo.addBookmark(f.id, curUrl, pageTitle)
                                } catch (e: Exception) {
                                }
                                runOnUiThread {
                                    Toast.makeText(this, "Přidáno do: " + f.name, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp.bottomMargin = dp(8)
                        list.addView(row, lp)
                    }
                    // pri hodne slozkach se seznam posouva (max ~ 320 dp)
                    val scroll = ScrollView(this)
                    scroll.addView(list)
                    scroll.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        minOf(dp(56) * folders.size, dp(320))
                    )
                    scroll
                }
            }
        }
    }

    private fun showKeyboard(target: View = etSearchMain) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
    }
    internal fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearchMain.windowToken, 0)
    }

    // Zavre naseptavac + klavesnici (pole ztrati focus, onInputFocusLost vrati adresu)
    private fun dismissInput() {
        suggestionToken++                       // zahodi rozpracovany dotaz do historie
        etSearchMain.clearFocus()
        etTopUrl.clearFocus()
        hideKeyboard()
        suggestions.value = emptyList()
    }

    private fun isTouchInside(v: View, ev: MotionEvent): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)              // pocita i s posunem nad klavesnici
        return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
            ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
    }

    // Klepnuti mimo listu/pole/naseptavac zavre naseptavac. Dotyk jde dal (scroll funguje).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (capturing) return true   // behem screenshotu cele stranky se dotyky ignoruji
        // otevrena leva lista: klepnuti mimo ni (a mimo tlacitko 5) ji zavre a tlacitko se zasune
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && leftBarOpen) {
            val bar = sideBarLeft
            val btn = barBtn()
            if ((bar == null || !isTouchInside(bar, ev)) && !isTouchInside(btn, ev)) closeLeftBarAndHide()
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && (etSearchMain.hasFocus() || etTopUrl.hasFocus())) {
            val inside = listOf(
                bottomBar, topBar,
                findViewById<View>(R.id.suggestionsView),
                findViewById<View>(R.id.suggestionsTopView)
            ).any { isTouchInside(it, ev) }
            if (!inside) dismissInput()
        }
        return super.dispatchTouchEvent(ev)
    }

    // Tlacitko zpet (novy zpusob): prehled karet -> zavrit, web ma historii -> zpet, jinak ukoncit appku
    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    gestureConfigMode -> exitGestureConfig()
                    regionView != null -> removeRegionOverlay()
                    configMode -> exitConfigMode()
                    etSearchMain.hasFocus() || etTopUrl.hasFocus() -> dismissInput()   // 1. zavrit psani/naseptavac
                    customView != null -> exitFullscreenVideo()
                    gestureView.visibility == View.VISIBLE -> hideGestures()
                    tabsView.visibility == View.VISIBLE -> hideTabs()
                    webView.canGoBack() -> webView.goBack()
                    tabs.list.size > 1 -> closeTab(tabs.activeId)   // konec historie -> zavrit kartu
                    // jinak nic - appka se zpetnym tlacitkem nikdy nezavre
                }
            }
        })
    }

    private fun exitFullscreenVideo() {
        val v = customView ?: return
        fullscreenHolder?.let { (root as ViewGroup).removeView(it) }
        fullscreenHolder = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED   // zpet podle telefonu
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        AppSettings.totalBlocked.set(totalBlocked.get())
        saveTabs()
    }

    override fun onDestroy() {
        for (tab in tabs.list) {
            if (tab.webViewCreated) try { tab.webView.destroy() } catch (e: Exception) { }
        }
        purgeIncognito()
        introPlayer?.let { try { it.release() } catch (e: Exception) { } }   // hudba uvodni animace
        previewExecutor.shutdown()   // dopise rozdelane nahledy a skonci
        super.onDestroy()
    }
}
