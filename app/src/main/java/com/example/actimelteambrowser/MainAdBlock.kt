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
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.graphics.toColorInt

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
    if (strictBlock(request, tab)) return true      // web pridany drzenim AdBlocku: prisnejsi pravidla
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
    if (total % 20 == 0) com.example.actimelteambrowser.core.settings.RewardGuard.setBlocked(total)
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


// ---------------- Prisnejsi blokovani na webech, ktere si uzivatel pridal (drzeni tlacitka AdBlock) ----------------
// Bezne seznamy (EasyList, EasyPrivacy) bezi dal. Navic se na takovem webu:
//  - zablokuji vlozene ramecky (iframe) z cizich domen (vetsina bannerovych reklam),
//  - zablokuji pozadavky na cizi domeny, ktere adresou vypadaji jako reklama nebo mereni,
//  - skryji prvky stranky, ktere jsou v kodu oznacene jako reklama.
// Vlastni server webu se neblokuje nikdy (jinak by se rozbil obsah); videa a overeni (captcha) take ne.

private val STRICT_AD = Regex("""(?i)(^|[/._?=&-])(ads?|adv|advert[a-z]*|adserv[a-z]*|adsystem|adtech|banners?|sponsor[a-z]*|promo[a-z]*|popunder|popads|doubleclick|googlesyndication|taboola|outbrain|affiliate|tracking|tracker|pixel|prebid|criteo|adnxs|pubmatic|rubicon|smartadserver|teads)([/._?=&-]|$)""")
private val STRICT_SAFE = listOf(
    "youtube.com", "youtube-nocookie.com", "ytimg.com", "vimeo.com", "recaptcha", "hcaptcha.com",
    "challenges.cloudflare.com", "gstatic.com", "google.com/maps", "googleapis.com/maps"
)
private const val STRICT_CSS = "ins.adsbygoogle,[id^='ad-'],[id^='ad_'],[id^='ads-'],[id^='ads_'],[class^='ad-'],[class*=' ad-']," +
    "[class^='ads-'],[class*=' ads-'],[class*='advert'],[id*='advert'],[class*='sponsor'],[id*='sponsor'],[class*='banner-ad']," +
    "[class*='ad-banner'],[class*='ad-slot'],[class*='adslot'],[data-ad],[data-ad-slot],[data-adunit],[aria-label='Advertisement']," +
    "[aria-label='Reklama'],div[id*='google_ads'],iframe[src*='doubleclick'],iframe[id*='google_ads']{display:none!important}"

// "sub.priklad.cz" -> "priklad.cz" (stejny web = stejna posledni dve casti domeny)
private fun baseDomain(h: String): String {
    val p = h.lowercase().removePrefix("www.").split('.')
    if (p.size <= 2) return p.joinToString(".")
    // co.uk, com.au, ac.jp ... = koncovka ma dve casti, web je az treti od konce (jinak by bbc.co.uk bylo "co.uk")
    val n = if (p.size >= 3 && p.last().length == 2 && p[p.size - 2] in SECOND_LEVEL) 3 else 2
    return p.takeLast(n).joinToString(".")
}

private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gob", "mil", "nic")

// Bezi na pozadi vlakne (shouldInterceptRequest)
internal fun MainActivity.strictBlock(request: WebResourceRequest, tab: BrowserTab): Boolean {
    val page = runCatching { tab.pageUrl.toUri().host }.getOrNull() ?: return false
    if (!SiteRules.strict(page)) return false
    val host = request.url.host?.lowercase() ?: return false
    val base = baseDomain(page)
    if (host == base || host.endsWith(".$base")) return false   // vlastni server webu
    val url = request.url.toString()
    if (STRICT_SAFE.any { url.contains(it) }) return false
    if (guessResourceType(request) == "subdocument") return true   // cizi ramecek = skoro vzdy reklama
    return STRICT_AD.containsMatchIn(url)
}

// Skryje prvky oznacene jako reklama (jen na pridanych webech)
internal fun MainActivity.injectStrict(view: WebView?, url: String?) {
    if (view == null || url == null || !adBlockFor(url)) return
    val host = runCatching { url.toUri().host }.getOrNull()
    if (!SiteRules.strict(host)) return
    val js = "(function(){if(document.getElementById('__atbStrict'))return;var s=document.createElement('style');s.id='__atbStrict';" +
        "s.textContent=" + JSONObject.quote(STRICT_CSS) + ";(document.head||document.documentElement).appendChild(s);})();"
    view.evaluateJavascript(js, null)
}

/**
 * Drzeni tlacitka AdBlock: pridat web k prisnejsimu blokovani - aktualni stranku, nebo web zadany rucne.
 * Po OK se ukaze vysvetleni (blokovani nemusi fungovat vzdy). demo = ukazka: nic se neulozi (demoTargets site_current, site_ok).
 */
internal fun MainActivity.showAdBlockSitesDialog(demo: Boolean = false) {
    if (demo) demoTargets.clear()
    val cur = webView.url?.takeIf { isWebUrl(it) }?.let { SiteRules.hostOf(it) } ?: if (demo) "priklad.cz" else null
    var useCurrent = false
    var input: EditText? = null
    showStyledDialog(
        title = "Přidat web k blokování reklam",
        positiveText = "OK",
        onPositive = {
            val typed = input?.text?.toString().orEmpty()
            val host = if (useCurrent) cur else SiteRules.hostOf(typed)
            if (host == null) {
                AppToast.show(this, if (typed.isBlank()) "Vyber stránku nebo zadej adresu webu" else "Neplatná adresa webu")
                false
            } else {
                showStrictInfo(host, demo)
                true
            }
        },
        negativeText = "Zavřít"
    ) { _ ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        val intro = TextView(this)
        intro.text = "Když na nějakém webu reklamy pořád prolézají, přidej ho. Prohlížeč na něm zapne přísnější blokování."
        intro.setTextColor("#BBBBBB".toColorInt())
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        box.addView(intro)

        // a) aktualni stranka (vybira se klepnutim)
        val curBtn = styledButton(if (cur != null) "Přidat aktuální stránku k pravidlům\n$cur" else "Aktuální stránka není web", false)
        val curBg = GradientDrawable()
        curBg.cornerRadius = dp(14).toFloat()
        curBtn.background = curBg
        fun paintCur() {
            curBg.setColor((if (useCurrent) "#14382A" else "#2A2A2A").toColorInt())
            curBg.setStroke(dp(2), (if (useCurrent) "#00B055" else "#2A2A2A").toColorInt())
            curBtn.setTextColor((if (useCurrent) "#FFFFFF" else "#CCCCCC").toColorInt())
        }
        paintCur()
        curBtn.setPadding(dp(14), dp(14), dp(14), dp(14))
        curBtn.isEnabled = cur != null
        curBtn.setOnClickListener {
            useCurrent = !useCurrent
            if (useCurrent) input?.setText("")
            paintCur()
        }
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.topMargin = dp(14)
        box.addView(curBtn, clp)
        if (demo) demoTargets["site_current"] = curBtn

        // b) rucne zadany web
        val lbl = TextView(this)
        lbl.text = "nebo zadej web ručně:"
        lbl.setTextColor("#888888".toColorInt())
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        lbl.setPadding(dp(2), dp(14), 0, dp(6))
        box.addView(lbl)
        val et = EditText(this)
        et.hint = "např. priklad.cz"
        et.setHintTextColor("#666666".toColorInt())
        et.setTextColor(Color.WHITE)
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        et.isSingleLine = true
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        val ebg = GradientDrawable()
        ebg.cornerRadius = dp(12).toFloat()
        ebg.setColor("#2A2A2A".toColorInt())
        et.background = ebg
        et.setPadding(dp(14), dp(12), dp(14), dp(12))
        et.isEnabled = !demo
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrBlank() && useCurrent) { useCurrent = false; paintCur() }
            }
        })
        box.addView(et)
        input = et

        // uz pridane weby (klepnutim na ✕ se odeberou)
        val sites = SiteRules.strictSites()
        if (sites.isNotEmpty() && !demo) {
            val head = TextView(this)
            head.text = "Přidané weby"
            head.setTextColor("#888888".toColorInt())
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            head.setPadding(dp(2), dp(16), 0, dp(4))
            box.addView(head)
            for (site in sites) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val tv = TextView(this)
                tv.text = site
                tv.setTextColor(Color.WHITE)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val x = TextView(this)
                x.text = "✕"
                x.setTextColor("#E53935".toColorInt())
                x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                x.setPadding(dp(14), dp(8), dp(8), dp(8))
                x.setOnClickListener {
                    SiteRules.setStrict(site, false)
                    row.visibility = View.GONE
                    AppToast.show(this, "Web odebrán z pravidel: $site")
                }
                row.addView(x)
                box.addView(row)
            }
        }
        val sv = ScrollView(this)
        sv.addView(box)
        sv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sv
    }
    // tlacitko OK (posledni v radku tlacitek) pro ukazku
    if (demo) {
        val card = lastDialogCard as? ViewGroup
        val row = card?.getChildAt(card.childCount - 1) as? ViewGroup
        row?.getChildAt(row.childCount - 1)?.let { demoTargets["site_ok"] = it }
    }
}

// Vysvetleni po vyberu webu; "Rozumím, přidat" web prida a stranku nacte znovu
private fun MainActivity.showStrictInfo(host: String, demo: Boolean) {
    showStyledDialog(
        title = "Přidat $host?",
        positiveText = "Rozumím, přidat",
        onPositive = {
            if (!demo) {
                SiteRules.setStrict(host, true)
                AppToast.show(this, "Web přidán k pravidlům: $host")
                val now = webView.url?.let { runCatching { it.toUri().host }.getOrNull() }
                if (SiteRules.strict(now)) webView.reload()
            }
            true
        },
        negativeText = "Zrušit"
    ) { _ ->
        val tv = TextView(this)
        tv.setTextColor("#DDDDDD".toColorInt())
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.setLineSpacing(0f, 1.15f)
        tv.text = "Stránka bude přidána k aktuálním pravidlům a prohlížeč se pokusí reklamu zablokovat. " +
            "Měj však na paměti, že toto blokování nemusí fungovat vždy.\n\n" +
            "Jak to funguje: kromě běžných seznamů reklam (EasyList, EasyPrivacy) se na tomto webu zapne přísnější režim. " +
            "Prohlížeč zablokuje vložené rámečky (iframe) a skripty z cizích serverů, které podle adresy vypadají jako reklama " +
            "nebo sledování, a skryje části stránky, které jsou v jejím kódu označené jako reklama.\n\n" +
            "Proč to nemusí fungovat: některé weby posílají reklamu ze svého vlastního serveru, nebo ji mají přímo v textu stránky – " +
            "taková reklama se od obsahu nedá spolehlivě odlišit, a proto ji prohlížeč nechá být. " +
            "Výjimečně se může skrýt i něco, co reklama není (třeba vložené okno nebo přehrávač). " +
            "Pak web z pravidel zase odeber: podrž AdBlock a klepni na ✕ u webu."
        val sv = ScrollView(this)
        sv.addView(tv)
        sv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.5f).toInt())
        sv
    }
}
