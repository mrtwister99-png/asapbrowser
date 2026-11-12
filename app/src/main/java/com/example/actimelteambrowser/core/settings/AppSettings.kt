package com.example.actimelteambrowser.core.settings

import androidx.core.content.edit
import com.example.actimelteambrowser.noteError
import com.example.actimelteambrowser.quiet
import android.content.Context
import android.content.SharedPreferences
import com.example.actimelteambrowser.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Jedno misto pro VSECHNA nastaveni appky (SharedPreferences).
 *
 * Pridani noveho nastaveni = jeden radek dole v sekci "NASTAVENI" + pridat do seznamu `all`.
 * Pri importu se berou jen zname klice se spravnym typem, zbytek se ignoruje.
 */
object AppSettings {

    private const val FILE = "actimelteam_settings"

    internal var prefs: SharedPreferences? = null

    // Volat jednou v MainActivity.onCreate (pred cteni nastaveni)
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            fixButtons()   // opravi stare/neplatne funkce tlacitek (7/8 jen on/off, Gesta a Bocni lista max 1x)
        }
    }

    // ---------- typy nastaveni ----------

    abstract class Setting<T>(val key: String, val default: T) {
        abstract fun get(): T
        abstract fun set(value: T)
        internal abstract fun toJsonValue(): Any
        // true = hodnota z JSONu mela spravny typ a byla ulozena
        internal abstract fun fromJsonValue(value: Any?): Boolean
        fun reset() { AppSettings.prefs?.edit { remove(key) } }
    }

    class BoolSetting(key: String, default: Boolean) : Setting<Boolean>(key, default) {
        override fun get(): Boolean = AppSettings.prefs?.getBoolean(key, default) ?: default
        override fun set(value: Boolean) { AppSettings.prefs?.edit { putBoolean(key, value) } }
        override fun toJsonValue(): Any = get()
        override fun fromJsonValue(value: Any?): Boolean {
            if (value !is Boolean) return false
            set(value); return true
        }
    }

    class StringSetting(key: String, default: String) : Setting<String>(key, default) {
        override fun get(): String = AppSettings.prefs?.getString(key, default) ?: default
        override fun set(value: String) { AppSettings.prefs?.edit { putString(key, value) } }
        override fun toJsonValue(): Any = get()
        override fun fromJsonValue(value: Any?): Boolean {
            if (value !is String) return false
            set(value); return true
        }
    }

    class IntSetting(key: String, default: Int) : Setting<Int>(key, default) {
        override fun get(): Int = AppSettings.prefs?.getInt(key, default) ?: default
        override fun set(value: Int) { AppSettings.prefs?.edit { putInt(key, value) } }
        override fun toJsonValue(): Any = get()
        override fun fromJsonValue(value: Any?): Boolean {
            if (value !is Int) return false
            set(value); return true
        }
    }

    // ---------- NASTAVENI (sem pridavat dalsi) ----------

    // AdBlock
    val adBlockOn = BoolSetting("adblock_on", true)                    // hlavni prepinac
    val adBlockYoutube = BoolSetting("adblock_youtube", true)          // YouTube skript (vyrez reklam)
    val adBlockCookies = BoolSetting("adblock_cookies", true)          // auto-odmitnuti cookie list
    val adBlockAutoUpdate = BoolSetting("adblock_auto_update", true)   // stahovani novych seznamu
    val thumbsOn = BoolSetting("thumbs_on", true)                      // ukladat a ukazovat nahledy stranek a ikony webu
    val tabStripBottom = BoolSetting("tab_strip_bottom", true)         // spodni lista karet (prvnich 5); vychozi zapnuto
    val tabStripTop = BoolSetting("tab_strip_top", true)               // horni lista karet (dalsi); vychozi zapnuto
    val infoToasts = BoolSetting("info_toasts", true)                  // informacni toasty (chyby se ukazuji vzdy)
    val animationsOn = BoolSetting("animations_on", true)              // animace tlacitek a prechodu
    // Zivy tutorial: cisla tlacitek, na ktera uzivatel jeste neklepl (maji zluty obrys); prazdne = hotovo. Neexportuje se.
    val liveTutorial = StringSetting("live_tutorial", "")
    val vaultDemoSeen = BoolSetting("vault_demo_seen", false)          // pruvodce trezorem uz byl ukazan (pak jen otaznikem); neexportuje se
    val welcomeShown = BoolSetting("welcome_shown", false)             // uvodni zprava (2 strany) uz byla ukazana; neexportuje se
    val introOn = BoolSetting("intro_on", true)                        // uvodni animace pri spusteni
    val soundsOn = BoolSetting("sounds_on", true)                      // hudba uvodni animace (zvuky gest a screenshotu maji vlastni prepinace)
    val screenshotSound = BoolSetting("screenshot_sound", true)        // zvuk po vyfoceni obrazovky
    val vibrationOn = BoolSetting("vibration_on", true)                // vsechna vibrace (drzeni tlacitek, gesta)
    val pullRefreshOn = BoolSetting("pull_refresh_on", true)           // obnoveni stranky tazenim shora dolu
    val pullRefreshDistance = IntSetting("pull_refresh_distance", 120) // vzdalenost tazeni (dp), od ktere se stranka obnovi
    val hideBarsOnScroll = BoolSetting("hide_bars_on_scroll", true)    // schovat listy a bocni tlacitka pri posouvani stranky
    val searchEngine = StringSetting("search_engine", "google")        // google / seznam / duckduckgo
    val desktopSites = StringSetting("desktop_sites", "")              // weby, kde se pouziva verze pro pocitac (domeny oddelene carkou)
    val startShowFavorites = BoolSetting("start_show_favorites", false)   // nova karta: false = nejnavstevovanejsi, true = oblibene
    val totalBlocked = IntSetting("total_blocked", 0)                  // kolik reklam AdBlock zablokoval celkem (uvodni stranka)
    val squareStyleOwned = BoolSetting("square_style_owned", false)    // vzhled gest "Ctverecky" koupeny za 100 000 zablokovanych reklam
    val adBlockExceptions = StringSetting("adblock_exceptions", "")    // weby, kde je AdBlock vypnuty (domeny oddelene carkou)
    val sitePermissions = StringSetting("site_permissions", "")        // povoleni webu (JSON: domena -> {geo, cam, mic})

    // VPN (zatim jen prepinac, bez napojeni na skutecnou VPN)
    val vpnOn = BoolSetting("vpn_on", false)

    // Prohlizec
    val homeUrl = StringSetting("home_url", "https://m.youtube.com/")
    // Po spusteni: true = obnovit posledni karty, false = jedna nova karta s domovskou strankou
    val startupRestoreTabs = BoolSetting("startup_restore_tabs", true)

    // Otevrene karty (JSON). NENI v seznamu `all` - SettingsCodes je exportuje zvlast.
    val savedTabs = StringSetting("saved_tabs", "")
    // Po importu: hlavni okno ma nacist karty z `savedTabs`
    val tabsImported = BoolSetting("tabs_import_pending", false)

    // Funkce tlacitek 1-8: klepnuti a drzeni (klic funkce z ButtonFunction)
    // Tlacitko 4 je pevne, 1-3 maji omezeny vyber funkci (viz ButtonFunction.allowedOn), 9-12 jsou pridana boci (max 2 vlevo a 2 vpravo)
    val buttonTap: List<StringSetting> = (1..12).map { StringSetting("btn${it}_tap", ButtonDefaults.tap(it).key) }
    val buttonLong: List<StringSetting> = (1..12).map { StringSetting("btn${it}_long", ButtonDefaults.long(it).key) }
    // Bocni tlacitka (5, 6, 9-12): true = vysouvaci (videt jen 50 %), false = videt cele
    // Tlacitko 6 (gesta) je vychozi nevysouvaci: klepnuti rovnou otevre plochu gest
    val buttonSlide: List<BoolSetting> = (1..12).map { BoolSetting("btn${it}_slide", it != 6) }   // zastarale (nahrazeno buttonRetract), zustava kvuli starsimu exportu a nepouzite obrazovce
    // Bocni tlacitka (5, 6, 9-12) jsou trvale zasunuta za okraj: o kolik % sirky (50 nebo 70)
    val buttonRetract: List<IntSetting> = (1..12).map { IntSetting("btn${it}_retract", 50) }
    // Gesta: vzhled plochy (0 = ciselna klavesnice, 1 = kulicky), velikost v dp a seznam gest (JSON)
    val gestureStyle = IntSetting("gesture_style", 0)
    val gestureSize = IntSetting("gesture_size", 260)
    // Poloha a velikost plochy gest: "l,t,r,b" v tisicinach okna; prazdne = vychozi misto vpravo dole
    val gestureRect = StringSetting("gesture_rect", "")
    // Viditelnost plochy gest v %: 100 = plne videt, 0 = neviditelna (gesta funguji dal)
    val gestureOpacity = IntSetting("gesture_opacity", 100)
    val gestureList = StringSetting("gesture_list", "")   // prazdne = vychozi gesta
    val gestureNewTabMigrated = BoolSetting("gesture_newtab_migrated", false)   // jednorazova zmena gesta "nova karta" 98741 -> 9852 (neexportuje se)
    val gestureIncognitoMigrated = BoolSetting("gesture_incognito_migrated", false)   // jednorazova migrace gesta "nove anonymni okno" (neexportuje se)
    // Od ktere klavesy je ulozeny seznam gest zapsany: 9 = vpravo dole (tlacitka vpravo), 7 = vlevo dole (zrcadlove)
    val gestureStart = IntSetting("gesture_start", 9)
    // Odezva po uspesnem gestu: drobne zabrneni (vychozi zap) a zvuk orb.mp3 (vychozi zap)
    val gestureHaptic = BoolSetting("gesture_haptic", true)
    val gestureSound = BoolSetting("gesture_sound", true)

    // Seznam stahovani (JSON: id, nazev). NENI v `all` - do exportu nepatri.
    val downloadList = StringSetting("download_list", "")

    // Zobrazeni tlacitek: 1-4 jsou stala, 5-8 si uzivatel muze skryt (odebrat), 9-12 se pridavaji/odebiraji zvlast
    val buttonEnabled: List<BoolSetting> = (1..12).map { BoolSetting("btn${it}_enabled", true) }

    /** true = tlacitko je zobrazene (1-4 a 9-12 vzdy; 5-8 podle nastaveni). */
    fun isEnabled(i: Int): Boolean = i !in 5..8 || buttonEnabled[i - 1].get()

    // Ktera pridana tlacitka (cisla 9-12) jsou vlevo a vpravo, napr. "9,11"
    val extraLeft = StringSetting("extra_left", "")
    val extraRight = StringSetting("extra_right", "")

    // Cislo prave pridaneho bocniho tlacitka: hlavni okno ho po navratu zvyrazni (neexportuje se)
    val highlightButton = IntSetting("highlight_button", 0)

    private fun parseNums(s: String): List<Int> =
        s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 9..12 }.distinct()

    /** Cisla pridanych tlacitek na dane strane (osetreno: max 2, bez duplicit mezi stranami). */
    fun extraButtons(left: Boolean): List<Int> {
        val l = parseNums(extraLeft.get()).take(2)
        if (left) return l
        return parseNums(extraRight.get()).filterNot { it in l }.take(2)
    }

    fun setExtraButtons(left: Boolean, list: List<Int>) {
        (if (left) extraLeft else extraRight).set(list.joinToString(","))
    }

    /**
     * Uklid funkci tlacitek: 4 pevne vychozi, 1-3 jen povolene funkce (jinak vychozi), na ostatnich jen povolena funkce (7 a 8 jen on/off)
     * a Gesta i Bocni lista smi byt max 1x (prvni nalezena zustane, dalsi se nastavi na "Zadna funkce").
     */
    fun fixButtons() {
        // tlacitko 4 (Nastaveni) je pevne: vzdy vychozi funkce; tlacitka 1-3: nepovolena funkce se vrati na vychozi
        for (i in 1..4) {
            for ((slot, def) in listOf(buttonTap[i - 1] to ButtonDefaults.tap(i), buttonLong[i - 1] to ButtonDefaults.long(i))) {
                val fn = ButtonFunction.fromKey(slot.get())
                val reset = if (i == 4) fn != def else (fn != ButtonFunction.NONE && !fn.allowedOn(i))
                if (reset) slot.set(def.key)
            }
        }
        var gestures = false
        var leftBar = false
        for (i in 1..12) {
            for (slot in listOf(buttonTap[i - 1], buttonLong[i - 1])) {
                val fn = ButtonFunction.fromKey(slot.get())
                if (fn == ButtonFunction.NONE) continue
                var bad = !fn.allowedOn(i)
                if (!bad && fn == ButtonFunction.GESTURES) { if (gestures) bad = true else gestures = true }
                if (!bad && fn == ButtonFunction.LEFT_BAR) { if (leftBar) bad = true else leftBar = true }
                if (bad) slot.set(ButtonFunction.NONE.key)
            }
        }
    }

    // Trezor hesel (klice NEJSOU v seznamu `all`: exportuji se zvlast v sekci [va], ne jako bezne nastaveni)
    val vaultSalt = StringSetting("vault_salt", "")                // base64, neni tajne
    val vaultEncryptedDek = StringSetting("vault_enc_dek", "")     // DEK zasifrovany klicem z 12 slov
    val vaultCreated = BoolSetting("vault_created", false)
    val vaultPinBlob = StringSetting("vault_pin_blob", "")          // DEK zabaleny PINem + klicem z Android Keystore (jen v tomto telefonu)
    val vaultPinSalt = StringSetting("vault_pin_salt", "")
    val vaultBioBlob = StringSetting("vault_bio_blob", "")          // DEK zabaleny klicem z Keystore, ktery se odemyka otiskem prstu
    val vaultPinFails = IntSetting("vault_pin_fails", 0)           // spatne pokusy o PIN; po 5 se PIN smaze

    // Seznam vseho, co se exportuje / importuje
    private val all: List<Setting<*>> = listOf<Setting<*>>(
        adBlockOn, adBlockYoutube, adBlockCookies, adBlockAutoUpdate,
        vpnOn,
        homeUrl, startupRestoreTabs
    ) + buttonTap + buttonLong + buttonSlide + buttonRetract + buttonEnabled + listOf(extraLeft, extraRight) +
        listOf<Setting<*>>(gestureStyle, gestureSize, gestureList, gestureStart, gestureHaptic, gestureSound, gestureRect, gestureOpacity, adBlockExceptions, sitePermissions, totalBlocked, startShowFavorites,
        squareStyleOwned, thumbsOn, animationsOn, infoToasts, tabStripBottom, tabStripTop, introOn, soundsOn, screenshotSound, vibrationOn, hideBarsOnScroll, pullRefreshOn, pullRefreshDistance, searchEngine, desktopSites)

    // Vsechna nastaveni pro kodovy export (SettingsCodes)
    val allSettings: List<Setting<*>> get() = all

}


// ---------------------------------------------------------------- FUNKCE TLACITEK

/** Funkce, ktere se daji priradit tlacitkum 1-8 (klepnuti i drzeni). */
enum class ButtonFunction(val key: String, val label: String, val group: String, val iconRes: Int) {
    NONE("none", "Žádná funkce", "", R.drawable.ic_none),

    // Navigace a stránka
    HOME("home", "Domů (domovská stránka)", "Navigace a stránka", R.drawable.ic_home),
    BACK("back", "Zpět", "Navigace a stránka", R.drawable.ic_back),
    FORWARD("forward", "Vpřed", "Navigace a stránka", R.drawable.ic_forward),
    RELOAD("reload", "Obnovit stránku", "Navigace a stránka", R.drawable.ic_refresh),
    SCROLL_TOP("scroll_top", "Nahoru na stránce", "Navigace a stránka", R.drawable.ic_scroll_up),
    SCROLL_BOTTOM("scroll_bottom", "Dolů na stránce", "Navigace a stránka", R.drawable.ic_scroll_down),
    DESKTOP_MODE("desktop_mode", "Verze pro počítač (zap/vyp)", "Navigace a stránka", R.drawable.ic_desktop_mode),
    SHARE_URL("share_url", "Sdílet adresu", "Navigace a stránka", R.drawable.ic_share_url),
    COPY_URL("copy_url", "Kopírovat adresu", "Navigace a stránka", R.drawable.ic_copy_url),
    ZOOM_IN("zoom_in", "Přiblížit", "Navigace a stránka", R.drawable.ic_zoom_in),
    ZOOM_OUT("zoom_out", "Oddálit", "Navigace a stránka", R.drawable.ic_zoom_out),

    // Karty
    TABS_OVERVIEW("tabs_overview", "Přehled karet", "Karty", R.drawable.ic_tabs_overview),
    NEW_TAB("new_tab", "Nová karta", "Karty", R.drawable.ic_new_tab),
    NEW_INCOGNITO("new_incognito", "Nové anonymní okno", "Karty", R.drawable.ic_incognito),
    CLOSE_TAB("close_tab", "Zavřít aktuální kartu", "Karty", R.drawable.ic_close_tab),
    CLOSE_ALL_TABS("close_all_tabs", "Zavřít všechny karty", "Karty", R.drawable.ic_close_all_tabs),
    PREV_TAB("prev_tab", "Předchozí karta", "Karty", R.drawable.ic_prev_tab),
    NEXT_TAB("next_tab", "Další karta", "Karty", R.drawable.ic_next_tab),
    DUPLICATE_TAB("duplicate_tab", "Duplikovat kartu", "Karty", R.drawable.ic_duplicate_tab),
    REOPEN_TAB("reopen_tab", "Vrátit poslední zavřenou kartu", "Karty", R.drawable.ic_restore_tab),

    // Záložky, historie, hledání
    OPEN_BOOKMARKS("open_bookmarks", "Otevřít Záložky", "Záložky, historie, hledání", R.drawable.ic_bookmarks),
    QUICK_BOOKMARK("quick_bookmark", "Rychle přidat do Oblíbených", "Záložky, historie, hledání", R.drawable.ic_quick_favorite),
    ADD_TO_FOLDER("add_to_folder", "Přidat stránku do složky (výběr)", "Záložky, historie, hledání", R.drawable.ic_add_to_folder),
    OPEN_HISTORY("open_history", "Historie", "Záložky, historie, hledání", R.drawable.ic_history),
    FOCUS_SEARCH("focus_search", "Hledání (otevřít klávesnici)", "Záložky, historie, hledání", R.drawable.ic_search_keyboard),
    HOME_SETTINGS("home_settings", "Nastavení domovské stránky", "Záložky, historie, hledání", R.drawable.ic_home_settings),

    // Nástroje a systém
    ADBLOCK_TOGGLE("adblock_toggle", "AdBlock zap/vyp", "Nástroje a systém", R.drawable.ic_adblock),
    VPN("vpn", "VPN (zatím v rekonstrukci)", "Nástroje a systém", R.drawable.ic_vpn),
    OPEN_SETTINGS("open_settings", "Nastavení", "Nástroje a systém", R.drawable.ic_settings),
    OPEN_BUTTONS("open_buttons", "Nastavení tlačítek", "Nástroje a systém", R.drawable.ic_button_settings),
    EXPORT("export", "Export nastavení", "Nástroje a systém", R.drawable.ic_export),
    IMPORT("import", "Import nastavení", "Nástroje a systém", R.drawable.ic_import),
    FIND_IN_PAGE("find_in_page", "Najít na stránce", "Nástroje a systém", R.drawable.ic_find_in_page),
    GESTURES("gestures", "Gesta (otevřít plochu)", "Nástroje a systém", R.drawable.ic_gestures),
    LEFT_BAR("left_bar", "Boční lišta (otevřít/zavřít)", "Nástroje a systém", R.drawable.ic_sidebar),
    SCREENSHOT("screenshot", "Screenshot (obrazovka / část / celá stránka)", "Nástroje a systém", R.drawable.ic_screenshot),
    QUICK_SCREENSHOT("quick_screenshot", "Rychlý screenshot (celá stránka rovnou)", "Nástroje a systém", R.drawable.ic_screenshot_quick),
    FEEDBACK("feedback", "Ohodnotit / nahlásit chybu", "Nástroje a systém", R.drawable.ic_feedback),
    EXIT_APP("exit_app", "Ukončit aplikaci", "Nástroje a systém", R.drawable.ic_exit);

    /**
     * Kde smi funkce byt: tlacitka 7 a 8 jen on/off funkce (AdBlock, VPN, Verze pro pocitac);
     * Boční lištu lze dát jen na tlačítko 5 nebo 6. Gesta (kulicka) kdekoliv mimo 7 a 8.
     */
    fun allowedOn(button: Int): Boolean {
        if (button in 7..8) return this == ADBLOCK_TOGGLE || this == VPN || this == DESKTOP_MODE
        if (this == LEFT_BAR) return button == 5 || button == 6
        // Tlacitka 1-4: vychozi funkce tlacitka smi vzdy; navic 1 = Navigace a stranka + Gesta, 2 a 3 = Zalozky, historie, hledani;
        // 4 (Nastaveni) je pevne
        if (button in 1..4) {
            if (this == ButtonDefaults.tap(button) || this == ButtonDefaults.long(button)) return true
            return when (button) {
                1 -> group == "Navigace a stránka" || this == GESTURES
                2 -> group == "Záložky, historie, hledání" || this == GESTURES
                3 -> group == "Záložky, historie, hledání" || this == GESTURES
                else -> false
            }
        }
        return true
    }

    /** Kratky popis, co funkce dela (ukazuje se pod nazvem ve vyberu funkci). */
    fun description(): String = when (this) {
        NONE -> "Tlačítko nic nedělá"
        HOME -> "Načte domovskou stránku"
        BACK -> "Zpět na předchozí stránku"
        FORWARD -> "Vpřed na další stránku"
        RELOAD -> "Znovu načte stránku"
        SCROLL_TOP -> "Skočí na začátek stránky"
        SCROLL_BOTTOM -> "Skočí na konec stránky"
        DESKTOP_MODE -> "Přepne mobilní a počítačovou verzi"
        SHARE_URL -> "Pošle adresu do jiné aplikace"
        COPY_URL -> "Zkopíruje adresu do schránky"
        ZOOM_IN -> "Přiblíží stránku"
        ZOOM_OUT -> "Oddálí stránku"
        TABS_OVERVIEW -> "Otevře přehled karet"
        NEW_TAB -> "Otevře novou kartu"
        NEW_INCOGNITO -> "Otevře novou anonymní kartu"
        CLOSE_TAB -> "Zavře aktuální kartu"
        CLOSE_ALL_TABS -> "Zavře všechny karty, i anonymní"
        PREV_TAB -> "Přepne na předchozí kartu"
        NEXT_TAB -> "Přepne na další kartu"
        DUPLICATE_TAB -> "Otevře kopii stránky v nové kartě"
        REOPEN_TAB -> "Vrátí poslední zavřenou kartu"
        OPEN_BOOKMARKS -> "Otevře uložené stránky"
        QUICK_BOOKMARK -> "Uloží stránku do Oblíbených"
        ADD_TO_FOLDER -> "Uloží stránku do vybrané složky"
        OPEN_HISTORY -> "Otevře historii prohlížení"
        FOCUS_SEARCH -> "Otevře hledání a klávesnici"
        HOME_SETTINGS -> "Nastaví domovskou stránku a start"
        ADBLOCK_TOGGLE -> "Zapne / vypne blokování reklam"
        VPN -> "VPN zap/vyp (v rekonstrukci)"
        OPEN_SETTINGS -> "Otevře Nastavení"
        OPEN_BUTTONS -> "Úprava tlačítek na ploše"
        EXPORT -> "Uloží nastavení do souboru"
        IMPORT -> "Načte nastavení ze souboru"
        FIND_IN_PAGE -> "Hledá text na stránce"
        GESTURES -> "Otevře plochu pro gesta"
        LEFT_BAR -> "Vysune boční lištu"
        SCREENSHOT -> "Obrazovka / část / celá stránka"
        QUICK_SCREENSHOT -> "Rovnou vyfotí celou stránku"
        EXIT_APP -> "Ukončí aplikaci"
        FEEDBACK -> "Otevře hodnocení aplikace a hlášení chyb"
    }

    companion object {
        fun fromKey(key: String): ButtonFunction = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Vychozi funkce tlacitek 1-8 (9-12 jsou pridana boci tlacitka, vychozi = zadna). */
object ButtonDefaults {
    // 1 Domu, 2 Karty, 3 Oblibene, 4 Nastaveni, 5 levy bok, 6 pravy bok, 7 AdBlock, 8 VPN
    fun tap(i: Int): ButtonFunction = when (i) {
        1 -> ButtonFunction.HOME
        2 -> ButtonFunction.TABS_OVERVIEW
        3 -> ButtonFunction.OPEN_BOOKMARKS
        4 -> ButtonFunction.OPEN_SETTINGS
        5 -> ButtonFunction.LEFT_BAR
        6 -> ButtonFunction.GESTURES
        7 -> ButtonFunction.ADBLOCK_TOGGLE
        8 -> ButtonFunction.VPN
        else -> ButtonFunction.NONE
    }

    fun long(i: Int): ButtonFunction = when (i) {
        1 -> ButtonFunction.HOME_SETTINGS
        2 -> ButtonFunction.NEW_TAB
        3 -> ButtonFunction.ADD_TO_FOLDER
        4 -> ButtonFunction.FEEDBACK   // drzeni Nastaveni: hodnoceni a hlaseni chyb
        else -> ButtonFunction.NONE
    }

    /** Nazvy pro nastaveni. */
    fun name(i: Int): String = when (i) {
        1 -> "Domů"
        2 -> "Karty"
        3 -> "Oblíbené"
        4 -> "Nastavení"
        5 -> "Levé boční"
        6 -> "Pravé boční"
        7 -> "AdBlock (vlevo nahoře)"
        8 -> "VPN (vpravo nahoře)"
        else -> "Přidané boční tlačítko"
    }
}


// ---------------------------------------------------------------- GESTA

/** Jedno gesto: kod z klaves 1-9 (3x3, 9 = vpravo dole) a funkce, kterou spusti. */
data class GestureDef(val code: String, val fn: ButtonFunction)

object Gestures {
    // Vychozi gesta (1 2 3 / 4 5 6 / 7 8 9). Zacinaji devitkou = vpravo dole u tlacitka vpravo; pro tlacitka vlevo se zrcadli (7).
    val DEFAULTS = listOf(
        GestureDef("9852", ButtonFunction.NEW_TAB),
        GestureDef("98747", ButtonFunction.CLOSE_TAB),
        GestureDef("987412369", ButtonFunction.QUICK_BOOKMARK),
        GestureDef("9856", ButtonFunction.FORWARD),
        GestureDef("9854", ButtonFunction.BACK),
        GestureDef("98523", ButtonFunction.NEXT_TAB),
        GestureDef("98521", ButtonFunction.PREV_TAB),
        GestureDef("963654147", ButtonFunction.OPEN_HISTORY),
        GestureDef("98752", ButtonFunction.NEW_INCOGNITO)
    )

    fun valid(code: String): Boolean = code.length in 2..20 && code.all { it in '1'..'9' }

    /** Zrcadlove prevraceni kodu zleva doprava (1<->3, 4<->6, 7<->9; stred zustava). */
    fun mirror(code: String): String = code.map {
        when (it) { '1' -> '3'; '3' -> '1'; '4' -> '6'; '6' -> '4'; '7' -> '9'; '9' -> '7'; else -> it }
    }.joinToString("")

    /** Cislo tlacitka s funkci Gesta (klepnuti); 0 = zadne (kulicka je pak u vychoziho tlacitka 6). */
    fun gestureButton(): Int {
        val extras = AppSettings.extraButtons(true) + AppSettings.extraButtons(false)
        for (n in listOf(1, 2, 3, 5, 6, 9, 10, 11, 12)) {
            if (n >= 9 && n !in extras) continue
            if (!AppSettings.isEnabled(n)) continue
            if (ButtonFunction.fromKey(AppSettings.buttonTap[n - 1].get()) == ButtonFunction.GESTURES) return n
        }
        return 0
    }

    /** Klavesa, kterou gesto zacina: 7 u tlacitek vlevo (1, 5 a pridana vlevo), jinak 9 (2, 3, 6 a pridana vpravo). */
    fun startKey(): Int = when (val n = gestureButton()) {
        1, 5 -> 7
        0, 2, 3, 6 -> 9
        else -> if (n in AppSettings.extraButtons(true)) 7 else 9
    }

    /** Popis startu pro napovedu v UI. */
    fun startText(): String = if (startKey() == 7) "vlevo dole (7)" else "vpravo dole (9)"

    private fun defaultsFor(start: Int): List<GestureDef> =
        if (start == 7) DEFAULTS.map { it.copy(code = mirror(it.code)) } else DEFAULTS

    /**
     * Nacte seznam gest (osetreno: jen platne kody a funkce, bez duplicit kodu).
     * Kdyz je gesto na jine strane nez ulozena gesta (vlevo 7 / vpravo 9), kody se automaticky zrcadlove prevrati.
     */
    fun load(): List<GestureDef> {
        val start = startKey()
        val raw = AppSettings.gestureList.get()
        if (raw.isBlank()) return defaultsFor(start)
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<GestureDef>()
            val seen = HashSet<String>()
            for (i in 0 until minOf(arr.length(), 100)) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("code")
                val fn = ButtonFunction.fromKey(o.optString("fn"))
                if (fn == ButtonFunction.NONE || !valid(code) || !seen.add(code)) continue
                out.add(GestureDef(code, fn))
            }
            // Jednorazova migrace: ulozeny seznam z drivejska nema gesto "nove anonymni okno" -> doplnit, je-li kod volny
            if (!AppSettings.gestureIncognitoMigrated.get()) {
                AppSettings.gestureIncognitoMigrated.set(true)
                if (out.none { it.fn == ButtonFunction.NEW_INCOGNITO }) {
                    val savedStart = AppSettings.gestureStart.get()
                    val code = if (savedStart == 7) mirror("98752") else "98752"
                    if (out.none { it.code == code }) {
                        out.add(GestureDef(code, ButtonFunction.NEW_INCOGNITO))
                        save(out)   // ulozi se se starym startem; zrcadleni nize se postara o druhou stranu
                        AppSettings.gestureStart.set(savedStart)
                    }
                }
            }
            // Jednorazova migrace: vychozi gesto "nova karta" 9-8-7-4-1 se meni na 9-8-5-2 (jen kdyz je puvodni kod porad na teto funkci a novy je volny)
            if (!AppSettings.gestureNewTabMigrated.get()) {
                AppSettings.gestureNewTabMigrated.set(true)
                val savedStart = AppSettings.gestureStart.get()
                val oldCode = if (savedStart == 7) mirror("98741") else "98741"
                val newCode = if (savedStart == 7) mirror("9852") else "9852"
                val idx = out.indexOfFirst { it.code == oldCode && it.fn == ButtonFunction.NEW_TAB }
                if (idx >= 0 && out.none { it.code == newCode }) {
                    out[idx] = GestureDef(newCode, ButtonFunction.NEW_TAB)
                    save(out)
                    AppSettings.gestureStart.set(savedStart)
                }
            }
            if (AppSettings.gestureStart.get() != start) {
                val flipped = out.map { it.copy(code = mirror(it.code)) }
                mirrorSavedArea()   // ulozena plocha gest se presune na druhou stranu spolu s gesty
                save(flipped)   // ulozi i novy start
                flipped
            } else out
        } catch (e: Exception) {
            noteError(e)
            defaultsFor(start)
        }
    }

    /** Ulozena plocha "l,t,r,b" (tisiciny okna) se zrcadlove prevrati zleva doprava; prazdna zustane prazdna (vychozi misto). */
    private fun mirrorSavedArea() {
        val p = AppSettings.gestureRect.get().split(",").mapNotNull { it.trim().toIntOrNull() }
        if (p.size != 4) return
        AppSettings.gestureRect.set("${1000 - p[2]},${p[1]},${1000 - p[0]},${p[3]}")
    }

    fun save(list: List<GestureDef>) {
        val arr = JSONArray()
        for (g in list) arr.put(JSONObject().put("code", g.code).put("fn", g.fn.key))
        AppSettings.gestureList.set(arr.toString())
        AppSettings.gestureStart.set(startKey())
    }

    fun reset() {
        AppSettings.gestureList.reset()
        AppSettings.gestureStart.reset()
    }
}


// ---------------------------------------------------------------- PRAVIDLA PRO JEDNOTLIVE WEBY

/** Vyjimky AdBlocku a povoleni (poloha, kamera, mikrofon) podle domeny. Uklada se v nastaveni a exportuje se. */
object SiteRules {
    private fun norm(host: String?): String = host.orEmpty().lowercase().removePrefix("www.")

    private var lastRaw = "\u0000"
    private var lastSet: Set<String> = emptySet()

    /** true = na tomhle webu je AdBlock vypnuty (vyjimka). Vola se casto, proto se seznam drzi v pameti. */
    fun adBlockOff(host: String?): Boolean {
        val raw = AppSettings.adBlockExceptions.get()
        if (raw != lastRaw) {
            lastSet = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            lastRaw = raw
        }
        val h = norm(host)
        return h.isNotEmpty() && h in lastSet
    }

    fun setAdBlockOff(host: String?, off: Boolean) {
        val h = norm(host)
        if (h.isEmpty()) return
        val set = AppSettings.adBlockExceptions.get().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (off) set.add(h) else set.remove(h)
        AppSettings.adBlockExceptions.set(set.sorted().take(500).joinToString(","))
    }

    private var dRaw = "\u0000"
    private var dSet: Set<String> = emptySet()

    /** true = tento web se ma otevirat ve verzi pro pocitac (pamatuje se). */
    fun desktopOn(host: String?): Boolean {
        val raw = AppSettings.desktopSites.get()
        if (raw != dRaw) {
            dSet = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            dRaw = raw
        }
        val h = norm(host)
        return h.isNotEmpty() && h in dSet
    }

    fun setDesktop(host: String?, on: Boolean) {
        val h = norm(host)
        if (h.isEmpty()) return
        val set = AppSettings.desktopSites.get().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (on) set.add(h) else set.remove(h)
        AppSettings.desktopSites.set(set.sorted().take(500).joinToString(","))
    }

    /** Povoleni pro typ "geo" / "cam" / "mic": 1 = povoleno, 0 = zakazano, -1 = zeptat se. */
    fun perm(host: String?, type: String): Int {
        val h = norm(host)
        if (h.isEmpty()) return -1
        return try {
            val o = JSONObject(AppSettings.sitePermissions.get().ifBlank { "{}" }).optJSONObject(h) ?: return -1
            if (o.has(type)) o.getInt(type) else -1
        } catch (e: Exception) {
            noteError(e)
            -1
        }
    }

    fun setPerm(host: String?, type: String, value: Int) {
        val h = norm(host)
        if (h.isEmpty()) return
        quiet {
            val all = JSONObject(AppSettings.sitePermissions.get().ifBlank { "{}" })
            val o = all.optJSONObject(h) ?: JSONObject()
            if (value < 0) o.remove(type) else o.put(type, value)
            if (o.length() == 0) all.remove(h) else all.put(h, o)
            AppSettings.sitePermissions.set(all.toString())
        }
    }
}


// ---------------------------------------------------------------- VYHLEDAVACE

/** Vyhledavace, mezi kterymi jde volit (Konfigurace, drzeni tlacitka Domu). */
object SearchEngines {
    class Engine(val key: String, val name: String, val base: String)

    val list = listOf(
        Engine("google", "Google", "https://www.google.com/search?q="),
        Engine("seznam", "Seznam", "https://search.seznam.cz/?q="),
        Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=")
    )

    fun current(): Engine = list.firstOrNull { it.key == AppSettings.searchEngine.get() } ?: list[0]

    fun searchUrl(q: String): String = current().base + java.net.URLEncoder.encode(q, "UTF-8")
}
