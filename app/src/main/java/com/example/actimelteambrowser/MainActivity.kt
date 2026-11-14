package com.example.actimelteambrowser

import com.example.actimelteambrowser.data.MostVisitedSite
import androidx.lifecycle.Lifecycle
import android.media.MediaPlayer
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import java.io.ByteArrayInputStream
import com.example.actimelteambrowser.core.engine.RustBridge
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.vault.VaultManager
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import com.example.actimelteambrowser.data.BookmarksRepository
import com.example.actimelteambrowser.data.HistoryRepository
import com.example.actimelteambrowser.data.HistoryVisit
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.ui.SearchSuggestion
import com.example.actimelteambrowser.ui.SearchSuggestionsList
import android.graphics.Bitmap
import android.view.MotionEvent
import com.example.actimelteambrowser.ui.ExternalTouch
import com.example.actimelteambrowser.core.settings.SiteRules
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.actimelteambrowser.ui.StartSite
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.graphics.BitmapFactory
import java.io.File
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.example.actimelteambrowser.ui.TabTile

class MainActivity : AppCompatActivity() {

    companion object {
        internal const val INCOGNITO_PROFILE = "anonymni"
        internal const val INTRO_ENABLED = true   // uvodni animace s logem a hudbou (vypnuti: false)
    }

    internal lateinit var webView: WebView            // WebView aktivni karty
    // lateinit pole jde zkontrolovat jen uvnitr tridy, proto pomocna funkce pro rozsireni v jinych souborech
    internal fun isWebViewReady(): Boolean = ::webView.isInitialized
    internal lateinit var webContainer: FrameLayout   // sem se vklada WebView aktivni karty
    internal lateinit var tabsView: ComposeView       // prehled karet (dlazdice)
    internal lateinit var gestureView: ComposeView    // plocha gest (pruhledna)
    internal val externalTouch = mutableStateOf<ExternalTouch?>(null)   // dotyk pokracujici z tlacitka 6
    internal var externalDown: ExternalTouch? = null
    internal var extSeq = 0L
    // stav prepinace v prehledu karet je Compose stav: prepnuti se prekresli, i kdyz se seznam dlazdic nezmeni
    private val overviewIncognitoState = mutableStateOf(false)
    private val overviewGroupsState = mutableStateOf(false)
    internal var overviewIncognito: Boolean           // prehled karet ukazuje anonymni (true) nebo bezne karty
        get() = overviewIncognitoState.value
        set(v) { overviewIncognitoState.value = v }
    internal var overviewGroups: Boolean              // prehled karet ukazuje zalozku Skupiny
        get() = overviewGroupsState.value
        set(v) { overviewGroupsState.value = v }
    internal var lastSystemBottom = 0
    internal val tabs = BrowserTabs()
    internal val tabTiles = mutableStateOf<List<TabTile>>(emptyList())
    internal val overviewSession = androidx.compose.runtime.mutableIntStateOf(0)

    // Slozky v prehledu karet: skupina karet (id karet), v prehledu se ukaze jako jedna dlazdice na miste prvni karty
    // (logika slozek je v TabManager.kt; karty samotne jsou v `tabs`)
    internal val tabManager = TabManager()
    internal val groups: ArrayList<TabGroup> get() = tabManager.groups

    // Pocet reklam zablokovanych celkem (uvodni stranka); do nastaveni se uklada po dvaceti a pri pauze
    internal val totalBlocked = AtomicInteger(0)
    internal val startBlockedTotal = mutableIntStateOf(0)
    internal lateinit var etSearchMain: EditText
    internal lateinit var etTopUrl: EditText
    internal lateinit var bottomBar: View
    internal lateinit var topBar: View
    internal lateinit var progressBar: ProgressBar
    internal lateinit var root: View

    private var isTopBarExpanded = false
    @Volatile internal var isAdBlockOn = true
    @Volatile internal var adblockReady = false   // Rust engine nacetl EasyList
    internal val badgeUpdatePending = AtomicInteger(0)
    private var isVpnOn = false

    // Historie, zalozky a nasepavac
    internal val historyRepo by lazy { HistoryRepository(this) }
    internal val bookmarksRepo by lazy { BookmarksRepository(this) }
    internal val suggestions = mutableStateOf<List<SearchSuggestion>>(emptyList())
    internal val suggestionsAtTop = mutableStateOf(false)   // true = seznam se ukazuje pod hornim polem
    internal val pinnedPage = mutableStateOf<SearchSuggestion?>(null)   // aktualni stranka: prvni radek pri prazdnem poli
    internal val suggestionsCompact = mutableStateOf(false)             // prazdne pole: nejnavstevovanejsi jen 3 radky (scroll)
    @Volatile internal var suggestionToken = 0
    internal var lastRecordedUrl = ""
    internal var tabCountBg: GradientDrawable? = null   // ramecek ctverecku s poctem karet (predni karta)
    internal var tabCountLayer: android.graphics.drawable.Drawable? = null   // cela ikona: zadni karta + predni ctverecek
    internal val tabStrokePx: Int get() = (1.1f * resources.displayMetrics.density).toInt().coerceAtLeast(1)   // tenka cara jako u ostatnich ikon
    internal var tabsAnimating = false
    internal val accentState = mutableStateOf(0xFF00B055.toInt())   // hlavni barva plochy: zelena, v anonymnim rezimu modra #006AFF
    internal var pullView: PullRefreshView? = null                  // kruzek pri tazeni stranky dolu (obnoveni)
    internal val closedUrls = ArrayList<String>()           // naposledy zavrene karty (pro "vratit zavrenou")
    internal val extraViews = HashMap<Int, View>()          // pridana boci tlacitka: cislo (9-12) -> view
    internal var lastImeShift = 0f
    internal var searchBg: GradientDrawable? = null          // pozadi spodniho pole (#D9D9D9 + pulzujici obrys)
    internal var urlBg: GradientDrawable? = null             // pozadi horniho pole
    internal var pulseAnim: ValueAnimator? = null            // pulzovani zeleneho obrysu poli
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenHolder: FrameLayout? = null
    internal var sideBarLeft: View? = null
    internal var leftDeskBtn: View? = null                   // tlacitko "verze pro pocitac" v leve liste
    internal var barOrigin = 5                                // tlacitko, ze ktereho lista vyjela (5 = vlevo, 6 = vpravo)
    internal var pressedOrigin = 0                           // tlacitko 5/6 prave stisknute (pro funkci "Bocni lista")
    internal var leftBarOpen = false                         // levá lišta (tlačítko 5) je otevřená
    internal var leftBarAnim: ValueAnimator? = null
    internal var blinkAnim: ValueAnimator? = null           // blikani prave pridaneho tlacitka
    internal var orbView: GestureOrbView? = null            // zivá kulicka tlacitka 6 (gesta)
    internal var starAnimating = false
    internal var homeAnimating = false
    internal var syncingText = false   // zabrani zacykleni pri zrcadleni textu mezi poli

    // Okno Nastaveni (Compose) - vrati adresu, kterou uzivatel vybral (zalozka / historie)
    internal val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            settingsOpen = false
            onDemoSettingsResult(r.data?.getStringExtra(SettingsActivity.EXTRA_DEMO_RESULT))   // ukazka prvniho tutorialu pokracuje
            // po navratu na hlavni plochu se znovu prehraje animace kolecko -> logo (spusti ji onResume)
            introOnResume = true
            // Nastaveni -> Tlacitka: hlavni plocha prejde do konfiguracniho modu
            if (r.data?.getBooleanExtra(SettingsActivity.EXTRA_CONFIG_BUTTONS, false) == true) enterConfigMode()
            // Nastaveni -> Gesta: plocha gest + panel shora
            if (r.data?.getBooleanExtra(SettingsActivity.EXTRA_CONFIG_GESTURES, false) == true) enterGestureConfig()
            // Tutorialy -> Demo videa: ukazka se prehraje na hlavni plose
            r.data?.getIntExtra(SettingsActivity.EXTRA_RUN_DEMO, 0)?.takeIf { it != 0 }?.let { runDemoFromSettings(it) }
            val url = r.data?.getStringExtra(SettingsActivity.EXTRA_URL)
            if (r.resultCode == RESULT_OK && url != null && isWebUrl(url)) webView.loadUrl(url)
        }
    internal val homeUrl: String get() = AppSettings.homeUrl.get()   // domovska stranka z nastaveni

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
        setupTopClear()
        etTopUrl.hint = "Zadej adresu"   // napoveda na nove karte (prazdne pole)
        etTopUrl.setHintTextColor("#888888".toColorInt())
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
            lastSystemTopPx = systemBars.top
            updateWebPadding()
            sideBarLeft?.visibility = if (imeVisible || !leftBarOpen) View.INVISIBLE else View.VISIBLE

            // bottomBar - kdyz je klavesnice, vysun nad ni
            lastImeShift = if (imeVisible) -(ime.bottom - systemBars.bottom).toFloat() else 0f
            for (v in extraViews.values) v.translationY = lastImeShift
            // lista karet se pri klavesnici schova; az PO zmene lastImeShift (driv se volala pred ni a po zavreni
            // klavesnice zustala lista schovana podle stareho stavu, dokud ji neobnovilo neco jineho)
            updateTabStrips()
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
                accent = androidx.compose.ui.graphics.Color(accentState.value),
                compact = suggestionsCompact.value,
                onCopy = { copyPinnedUrl(it) },
                onPinned = {   // klepnuti na aktualni stranku = znovunacist
                    dismissInput()
                    webView.reload()
                    AppToast.show(this, "Načítám znovu")
                },
                onEdit = { editCurrentUrl() }
            ) { picked -> openSuggestion(picked.url) }
        }
        findViewById<ComposeView>(R.id.suggestionsTopView).setContent {
            SearchSuggestionsList(
                suggestions = if (suggestionsAtTop.value) suggestions.value else emptyList(),
                pinned = if (suggestionsAtTop.value) pinnedPage.value else null,
                accent = androidx.compose.ui.graphics.Color(accentState.value),
                compact = suggestionsCompact.value,
                onCopy = { copyPinnedUrl(it) },
                onPinned = {   // klepnuti na aktualni stranku = znovunacist
                    dismissInput()
                    webView.reload()
                    AppToast.show(this, "Načítám znovu")
                },
                onEdit = { editCurrentUrl() }
            ) { picked -> openSuggestion(picked.url) }
        }
        updateButtonVisuals()
        applyAdBlockUi()
        findViewById<TextView>(R.id.tvVpnState).setText(R.string.vpn_off)
        setupTabsOverlay()
        setupBackHandling()
        // po spusteni: obnovit posledni karty, nebo zacit jednou novou kartou (podle nastaveni)
        if (AppSettings.startupRestoreTabs.get()) restoreTabs() else createTab(homeUrl)
        if (savedInstanceState == null) handleIncomingIntent(intent, cold = true)   // odkaz z jine aplikace (vychozi prohlizec)
        warmUpDns()
    }

    // Appka uz bezi a z jine aplikace prisel dalsi odkaz (launchMode singleTask)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, cold = false)
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
            val hosts = urls.mapNotNull { runCatching { it.toUri().host }.getOrNull() }.distinct().take(8)
            for (h in hosts) {
                quiet { java.net.InetAddress.getAllByName(h) }
            }
        }
    }

    // ---------------- UVODNI STRANKA NOVE KARTY ----------------

    internal val startSites = mutableStateOf<List<StartSite>>(emptyList())
    internal val startFavorites = mutableStateOf<List<StartSite>>(emptyList())
    internal val startShowFav = mutableStateOf(false)
    internal var startPageView: ComposeView? = null

    // Nahledy nejnavstevovanejsich webu: filesDir/site_thumbs/<domena>.jpg (uklada se pri opusteni / nacteni stranky)
    internal fun siteThumbFile(host: String): File =
        File(File(filesDir, "site_thumbs"), host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".jpg")

    internal fun loadSiteThumb(host: String): Bitmap? = if (AppSettings.thumbsOn.get()) loadPreview(siteThumbFile(host)) else null

    // Ikony webu (favicony): filesDir/site_icons/<domena>.png
    internal fun siteIconFile(host: String): File =
        File(File(filesDir, "site_icons"), host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    internal fun loadSiteIcon(host: String): Bitmap? = if (AppSettings.thumbsOn.get()) loadPreview(siteIconFile(host)) else null

    internal fun startPageNormal() = startPageOpen(homeUrl)

    // ---------------- DLOUHE PODRZENI NA STRANCE (odkaz / obrazek) ----------------

    // Radek nabidky: kratky popisek, podtitulek (adresa), ikona (0 = bez ikony) a akce
    internal class MenuRow(val label: String, val sub: String, val icon: Int, val highlight: Boolean = false, val action: () -> Unit)

    // ---------------- UVODNI ANIMACE ----------------

    // Hudba k uvodni animaci: res/raw/bez_zastaveni.mp3 (kdyz chybi nebo jsou zvuky vypnute, je ticho)
    internal var introPlayer: MediaPlayer? = null

    // ---------------- PRUBEH STAHOVANI NA TLACITKACH ----------------

    internal val dlHandler = Handler(Looper.getMainLooper())
    internal var dlPolling = false
    internal val dlFills = HashMap<Int, DownloadFillDrawable>()      // cislo tlacitka -> plnici vrstva
    internal var dlActiveIds = HashSet<Long>()

    internal var settingsOutroRunning = false

    private fun setupBars() {
        setupFieldBackgrounds()
        // barva oznaceneho textu v polich (vcetne doplneneho "youtube.com"): #0099FF, lehce pruhledna
        val selColor = "#660099FF".toColorInt()
        etSearchMain.highlightColor = selColor
        etTopUrl.highlightColor = selColor
        // uchyt kurzoru (kapka pod kurzorem) cerne, ne svetle fialove (jen Android 10+)
        if (Build.VERSION.SDK_INT >= 29) {
            for (et in listOf(etSearchMain, etTopUrl)) {
                et.textSelectHandle?.mutate()?.let { d -> d.setTint(Color.BLACK); et.setTextSelectHandle(d) }
                et.textSelectHandleLeft?.mutate()?.let { d -> d.setTint(Color.BLACK); et.setTextSelectHandleLeft(d) }
                et.textSelectHandleRight?.mutate()?.let { d -> d.setTint(Color.BLACK); et.setTextSelectHandleRight(d) }
            }
        }
        // TOP BAR - 75% viditelna, 1/4 za hranou - klepni pro vysunuti
        topBar.setOnClickListener { toggleTopBar() }
        findViewById<View>(R.id.urlContainer).setOnClickListener { liveTutorialDone(101); toggleTopBar() }
        findViewById<View>(R.id.ivUrlLock).setOnClickListener { if (!liveTutorialDone(104)) showPageInfo() }
        findViewById<View>(R.id.ivUrlShare).setOnClickListener { if (!liveTutorialDone(103)) showShareDialog() }

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
                liveTutorialDone(102)
                // zajisti ze je videt nad klavesnici - insets listener to uz posune
                bottomBar.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
                startFreshInput(etSearchMain)
            } else {
                bottomBar.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                onInputFocusLost()
            }
        }
        etTopUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { liveTutorialDone(101); startFreshInput(etTopUrl) } else onInputFocusLost()
            updateTopClear()
        }
        // klepnuti do horniho pole = naseptavac se obnovi
        etTopUrl.setOnClickListener { refreshSuggestionsOnClick(etTopUrl) }
        etSearchMain.setOnClickListener { refreshSuggestionsOnClick(etSearchMain) }
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
                updateTopClear()   // krizek na smazani
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
        // ikona karet: tenky predni ctverecek s cislem a za nim posunuta zadni karta (stoh karet)
        val frontCard = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5 * density
            setStroke(tabStrokePx, Color.WHITE)
            setColor("#2A2A2A".toColorInt())   // nepruhledna = zakryje cary zadni karty
        }
        tabCountBg = frontCard
        val backCard = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5 * density
            setStroke(tabStrokePx, "#8CFFFFFF".toColorInt())
            setColor(Color.TRANSPARENT)
        }
        val off = (4 * density).toInt()
        tabCountLayer = android.graphics.drawable.LayerDrawable(arrayOf<android.graphics.drawable.Drawable>(backCard, frontCard)).apply {
            setLayerInset(0, off, 0, 0, off)
            setLayerInset(1, 0, off, off, 0)
        }
        findViewById<TextView>(R.id.tvTabCount).apply {
            background = tabCountLayer
            setPadding(0, off, off, 0)   // cislo na stred predniho ctverecku
        }
        // vsech 8 tlacitek se nastavuje v Nastaveni -> Tlacitka
        wireButtons()
        setupNavArrows()   // krok zpet / vpred ve spodnim poli
        setupFieldSwipe()  // tah po spodnim poli = jina karta (horni pole to ma v setupTopClear)
    }

    override fun onStart() {
        VaultManager.activityStarted()   // trezor: auto-zamek po 15 min na pozadi
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        VaultManager.activityStopped()
    }

    override fun onResume() {
        super.onResume()
        if (isWebViewReady()) updateTabStrips()   // listy karet se mohly v Nastaveni zapnout / vypnout
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
        findViewById<TextView>(R.id.tvVpnState).setText(R.string.vpn_off)
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

    internal fun isSideButton(n: Int) = n == 5 || n == 6 || n >= 9

    internal fun isLeftSide(n: Int) = n == 5 || (n >= 9 && n in AppSettings.extraButtons(true))

    internal fun buttonFunction(i: Int, long: Boolean): ButtonFunction =
        ButtonFunction.fromKey((if (long) AppSettings.buttonLong[i - 1] else AppSettings.buttonTap[i - 1]).get())

    // true = tlacitko ma vychozi funkci (a tedy sve puvodni ikony a animace)
    internal fun isDefaultVisual(i: Int): Boolean = buttonFunction(i, false) == ButtonDefaults.tap(i)

    // ---------------- KONFIGURACNI MOD TLACITEK ----------------
    // Nastaveni -> Tlacitka otevre hlavni plochu v konfiguracnim modu: klepnuti na tlacitko otevre jeho nastaveni (modal),
    // nad bocnimi tlacitky jsou "+" pro pridani dalsiho, uprostred je zelene prerusovane tlacitko "Ulozit a zavrit nastaveni".

    internal var configMode = false
    internal val configButton = mutableStateOf<Int?>(null)
    internal var configScrim: View? = null
    internal var configSave: View? = null
    internal var configHost: ComposeView? = null
    internal val configPlus = ArrayList<View>()

    // O kolik je bocni tlacitko trvale zasunute za okraj (50 nebo 70 % sirky tlacitka 56 dp)
    internal fun hiddenX(n: Int): Float {
        val pct = AppSettings.buttonRetract[n - 1].get().coerceIn(50, 70)
        return (if (isLeftSide(n)) -1f else 1f) * dp(56) * pct / 100f
    }

    // ---------------- LEVA LISTA: SCREENSHOT A QR ----------------

    // Screenshot celeho okna: nejdriv se zavre lista (aby nebyla na obrazku), pak se po dojeti animace vyfoti
    // ---------------- SCREENSHOT: VYBER DRUHU ----------------

    internal var capturing = false          // probiha screenshot celé stranky: dotyky se ignoruji
    internal var regionView: View? = null   // vyber casti obrazovky (overlay)

    internal var shotPreview: View? = null

    // ---------------- OPRAVNENI WEBU (poloha, kamera, mikrofon) ----------------

    internal var permCallback: ((Map<String, Boolean>) -> Unit)? = null
    internal val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val cb = permCallback
            permCallback = null
            cb?.invoke(res)
        }

    internal fun androidPermsFor(type: String): List<String> = when (type) {
        "geo" -> listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        "cam" -> listOf(android.Manifest.permission.CAMERA)
        else -> listOf(android.Manifest.permission.RECORD_AUDIO)
    }

    internal fun hasAndroidPerm(type: String): Boolean =
        androidPermsFor(type).any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    internal fun barBtn(): View = findViewById(if (barOrigin == 6) R.id.btnSideRightMain else R.id.btnSideLeftMain)

    // Funkce "Bocni lista" (z tlacitka 5/6 nebo gesta)
    internal fun toggleLeftBarAny() = toggleBarFrom(if (pressedOrigin != 0) pressedOrigin else defaultBarOrigin())

    // ---------------- GESTA ----------------

    // ---------------- KONFIGURACNI MOD GEST ----------------
    // Nastaveni -> Gesta otevre hlavni plochu jen s tlacitkem Gesta (kulickou) a plochou gest. Plocha ma na okrajich a rozich
    // uchyty (velikost) a jde presouvat; shora se vysune scrollovaci panel (styl, viditelnost, odezva, seznam gest).

    internal var gestureConfigMode = false
    internal val gestTick = mutableIntStateOf(0)        // zmena stylu / viditelnosti -> prekreslit editor
    internal val gestPreviewStyle = mutableStateOf<Int?>(null)   // zamceny vzor (Ctverecky): jen nahled v editoru, neuklada se
    internal val gestResetKey = mutableIntStateOf(0)     // vraceni vychozi polohy plochy
    internal var gestScrim: View? = null
    internal var gestPanel: ComposeView? = null
    internal val gestHidden = HashMap<View, Int>()    // skryte prvky a jejich puvodni viditelnost

    // Zamek proti dvojitemu otevreni: po otevreni okna/prehledu/dialogu se dalsi "otevirani" hned za sebou ignoruje
    internal var openLockUntil = 0L
    internal var settingsOpen = false
    private var introOnResume = false

    internal fun isOpeningFunction(fn: ButtonFunction): Boolean = when (fn) {
        ButtonFunction.OPEN_SETTINGS, ButtonFunction.OPEN_BUTTONS, ButtonFunction.OPEN_BOOKMARKS,
        ButtonFunction.OPEN_HISTORY, ButtonFunction.EXPORT, ButtonFunction.IMPORT,
        ButtonFunction.TABS_OVERVIEW, ButtonFunction.HOME_SETTINGS, ButtonFunction.ADD_TO_FOLDER,
        ButtonFunction.FIND_IN_PAGE, ButtonFunction.SCREENSHOT, ButtonFunction.FEEDBACK -> true
        else -> false
    }

    // ---------------- ANIMACE TLACITEK (drzeni) ----------------

    // ---------------- SJEDNOCENE DIALOGY ----------------

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Okno Nastaveni dostane aktualni stranku (pro "pridat sem" ve slozce zalozek)
    internal fun settingsIntent(): Intent =
        Intent(this, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_PAGE_URL, webView.url)
            .putExtra(SettingsActivity.EXTRA_PAGE_TITLE, webView.title.orEmpty())

    // true = prave se do pole vkladá adresa stranky (watchery pak nespousti naseptavac ani zrcadleni)
    internal var prefillingUrl = false

    // Inline doplnovani (jako Chrome): pisu "goo" -> pole ukaze "google.com" s oznacenym zbytkem,
    // Enter ho otevre, dalsi pismeno nebo Backspace doplneni prepise/smaze. Zdroj = znamé domeny z historie a zalozek.
    internal var knownHosts: List<String> = emptyList()
    internal var autoFilling = false
    internal var lastTypedLen = 0

    internal fun hostOf(url: String): String? =
        // bez "www.", "m." a "mobile." (m.youtube.com se doplni i na "youtube")
        runCatching { url.toUri().host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("mobile.")?.takeIf { it.contains('.') } }.getOrNull()

    internal fun isWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    internal val suggestHandler = Handler(Looper.getMainLooper())
    internal var suggestRunnable: Runnable? = null
    internal var suggestCacheAt = 0L
    internal var cachedHist: List<HistoryVisit> = emptyList()
    internal var cachedBms: List<Bookmark> = emptyList()
    internal var cachedTop: List<MostVisitedSite> = emptyList()

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

    // Zakladni poloha bocniho tlacitka: trvale zasunute; cele jen v konfiguracnich modech a u otevrene listy
    private fun sideBaseX(n: Int): Float =
        if (!configMode && !gestureConfigMode && !(n == barOrigin && leftBarOpen)) hiddenX(n) else 0f

    // Schova / vrati horni a dolni listu a bocni tlacitka (vsechna, i pridana)
    internal fun setChromeHidden(hidden: Boolean) {
        if (chromeHidden == hidden) return
        chromeHidden = hidden
        val d = 220L
        topBar.animate().translationY(if (hidden) -(topBar.height + dp(24)).toFloat() else 0f).setDuration(d).start()
        stripViews().forEach { (v, top) -> v.animate().translationY(if (hidden) (if (top) -(topBar.height + dp(60)).toFloat() else (bottomBar.height + dp(60)).toFloat()) else 0f).setDuration(d).start() }
        bottomBar.animate().translationY(if (hidden) (bottomBar.height + dp(24)).toFloat() else lastImeShift).setDuration(d).start()
        val off = dp(120).toFloat()
        for (n in listOf(5, 6) + extraViews.keys) {
            val v = buttonView(n) ?: continue
            val x = if (hidden) (if (isLeftSide(n)) -off else off) else sideBaseX(n)
            v.animate().translationX(x).setDuration(d).start()
        }
        val half = resources.displayMetrics.widthPixels / 2f
        for (p in tutorialPlusViews()) {
            val leftSide = p.left + p.width / 2f < half
            p.animate().translationX(if (hidden) (if (leftSide) -off else off) else 0f).setDuration(d).start()
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
            return try { RustBridge.hiddenSelectors(classes, ids) } catch (e: Throwable) { noteError(e); "" }
        }
    }

    // Pripony pro odhad typu zdroje (hotove konstanty, aby se na kazdy pozadavek nekompilovalo Regex)
    internal val IMAGE_EXTS = arrayOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg")
    internal val MEDIA_EXTS = arrayOf(".mp4", ".webm", ".m3u8", ".mpd", ".ts")

    // ---------------- KARTY (taby) ----------------

    // Postavi WebView karty: nastaveni, anonymni profil, klienti (AdBlock, ikony, oprávnění, scroll)
    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature", "ClickableViewAccessibility")   // profil hlidan pres incognitoProfilesSupported(); dotyk jen sleduje tah
    internal fun buildWebView(tab: BrowserTab): WebView {
        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(CosmeticBridge(), "AtbCos")   // kosmeticke filtry (viz injectCosmetic)
        wv.addJavascriptInterface(VaultBridge(this, tab), "AtbVault")
        wv.addJavascriptInterface(BlobDownloadBridge(this), "AtbDl")   // stahovani blob: souboru (napr. PDF vytvorene strankou)   // trezor hesel: hlaseni odeslaneho prihlaseni (viz MainVault)
        // bez setLayerType: WebView si vykresluje samo, hardwarova vrstva by drzela GPU pamet na kazdou kartu
        if (tab.incognito) {
            // anonymni karta: vlastni profil WebView = oddelene cookies, localStorage a cache od beznych karet
            // (smaze se po zavreni posledni anonymni karty). Musi se nastavit PRED nactenim cehokoliv.
            if (incognitoProfilesSupported()) {
                quiet { WebViewCompat.setProfile(wv, INCOGNITO_PROFILE) }
            }
            wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION") run { wv.settings.saveFormData = false }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS   // bez automatickeho vyplnovani (API 26+)
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
                    AppToast.show(this@MainActivity, "Stránka se zhroutila, načítám znovu")
                }
                return true
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // jiny web = stara ikona uz neplati (nova prijde pres onReceivedIcon)
                val oldHost = runCatching { tab.url.toUri().host }.getOrNull()
                val newHost = runCatching { url?.toUri()?.host }.getOrNull()
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
                injectStrict(view, url)
            }
            // zmena historie (i u webu bez nacteni nove stranky) = sipky zpet / vpred sedi
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (tab.id == tabs.activeId) updateNavArrows()
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (tab.id == tabs.activeId) { finishPullLoading(); updateNavArrows() }
                tab.url = url.orEmpty()
                tab.title = view?.title.orEmpty()
                if (tab.id == tabs.activeId) {
                    etTopUrl.setText(topUrlText(url))
                updateLockIcon(url)
                    progressBar.visibility = View.GONE
                }
                injectYoutubeAdBlock(view, url)
                injectCosmetic(view, url)
                injectStrict(view, url)
                rejectCookies(view)
                vaultOnPageFinished(tab, view, url)   // trezor hesel: zachytavani prihlaseni + vyplneni
                if (!tab.incognito) recordVisit(view, url)   // anonymni karta se do historie nezapisuje
                // po chvili se ulozi nahled stranky (pro dlazdice nejnavstevovanejsich webu), jen kdyz je karta porad videt
                val finishedHost = runCatching { url?.toUri()?.host?.removePrefix("www.") }.getOrNull()
                if (!tab.incognito && AppSettings.thumbsOn.get() && (finishedHost == null || thumbStale(finishedHost))) view?.postDelayed({
                    if (tab.id == tabs.activeId && tab.url == url.orEmpty() && tabsView.visibility != View.VISIBLE &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    ) capturePreview(tab) { }
                }, 3000)
            }
        }
        wv.isLongClickable = true
        wv.setOnLongClickListener { onWebLongPress(wv) }   // false = text/prazdne misto: vyber textu zustava systemu
        wv.setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
            startDownload(dlUrl, userAgent, contentDisposition, mimeType, wv)
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
                updateTabStrips()   // ikona v liste karet
                // ikona se ulozi k webu (jen bezne karty), aby ji ukazaly dlazdice nejnavstevovanejsich
                if (!tab.incognito) {
                    val h = runCatching { view?.url?.toUri()?.host?.removePrefix("www.") }.getOrNull()
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
                val host = try { origin.toUri().host.orEmpty() } catch (e: Exception) { noteError(e); "" }
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
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            override fun onHideCustomView() { exitFullscreenVideo() }
        }
        // klik do webu schova topBar zase na 75%
        // tazeni zeshora dolu na vrcholu stranky = znovunacist (pull to refresh)
        var pullStartY = -1f
        var pullStartX = 0f
        var pullArmed = false   // kruzek je zaplneny (haptika se da jen jednou za tah)
        wv.setOnTouchListener { v, ev ->
            if (isTopBarExpanded) toggleTopBar()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pullStartY = if (AppSettings.pullRefreshOn.get() && !v.canScrollVertically(-1) && !tab.startPage) ev.y else -1f
                    pullStartX = ev.x
                    pullArmed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pullStartY >= 0f && (v.canScrollVertically(-1) || ev.pointerCount > 1)) {
                        pullStartY = -1f
                        endPull()
                    } else if (pullStartY >= 0f) {
                        val dy = ev.y - pullStartY
                        if (dy > dp(12) && Math.abs(ev.x - pullStartX) < dy / 2) {
                            val prog = (dy / dp(AppSettings.pullRefreshDistance.get())).coerceIn(0f, 1f)
                            updatePull(prog)
                            if (prog >= 1f && !pullArmed) {
                                pullArmed = true
                                pullView?.pop()
                                if (AppSettings.vibrationOn.get()) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            } else if (prog < 1f) pullArmed = false
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dy = ev.y - pullStartY
                    if (pullStartY >= 0f && dy > dp(AppSettings.pullRefreshDistance.get()) && Math.abs(ev.x - pullStartX) < dy / 2 && isWebUrl(wv.url.orEmpty())) {
                        wv.reload()
                        AppToast.show(this@MainActivity, "Načítám znovu")
                        startPullLoading()
                    } else endPull()
                    pullStartY = -1f
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    pullStartY = -1f
                    endPull()
                }
            }
            false
        }
        // posouvani dolu schova listy a bocni tlacitka, posun nahoru je vrati (Konfigurace)
        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY -> onWebScrolled(tab, scrollY, oldScrollY) }

        return wv
    }

    // Text v poli s adresou nahore (puvodni chovani)
    internal fun topUrlText(url: String?): String =
        url?.replace("https://", "")?.replace("/", "") ?: ""

    // ---------------- SLOZKY A PORADI KARET V PREHLEDU ----------------

    internal fun groupOf(tabId: Long): TabGroup? = tabManager.groupOf(tabId)

    // ---------------- ANONYMNI PROFIL ----------------

    internal fun incognitoProfilesSupported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    // Jedno vlakno = zapisy se nepredbihaji. previewWritten pamatuje, ktery obrazek je ulozen v p<i>.jpg (jen z tohoto vlakna).
    internal val previewExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    internal val previewWritten = ArrayList<java.lang.ref.WeakReference<Bitmap>?>()

    internal fun loadPreview(file: File): Bitmap? =
        runCatching {
            if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        }.getOrNull()

    // Drzeni hvezdy: nabidne slozky a ulozi aktualni stranku do vybrane
    internal fun addCurrentPageToFolder() = chooseFolderAndSave(webView.url, webView.title.orEmpty())

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
                    isFindBarOpen() -> hideFindBar()   // zavrit hledani na strance
                    etSearchMain.hasFocus() || etTopUrl.hasFocus() -> dismissInput()   // 1. zavrit psani/naseptavac
                    customView != null -> exitFullscreenVideo()
                    gestureView.isVisible -> hideGestures()
                    tabsView.isVisible -> hideTabs()
                    webView.canGoBack() -> webView.goBack()
                    tabs.list.size > 1 -> closeTab(tabs.activeId)   // konec historie -> zavrit kartu
                    // jinak nic - appka se zpetnym tlacitkem nikdy nezavre
                }
            }
        })
    }

    private fun exitFullscreenVideo() {
        if (customView == null) return
        fullscreenHolder?.let { (root as ViewGroup).removeView(it) }
        fullscreenHolder = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED   // zpet podle telefonu
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        AppSettings.totalBlocked.set(totalBlocked.get())
        saveTabs()
    }

    override fun onDestroy() {
        for (tab in tabs.list) {
            if (tab.webViewCreated) quiet { tab.webView.destroy() }
        }
        purgeIncognito()
        introPlayer?.let { quiet { it.release() } }   // hudba uvodni animace
        previewExecutor.shutdown()   // dopise rozdelane nahledy a skonci
        super.onDestroy()
    }
}
