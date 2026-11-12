package com.example.actimelteambrowser

import androidx.core.graphics.drawable.toDrawable
import androidx.appcompat.content.res.AppCompatResources
import android.view.animation.LinearInterpolator
import android.os.SystemClock
import android.graphics.RectF
import android.content.ClipboardManager
import android.content.ClipData
import android.animation.PropertyValuesHolder
import androidx.core.graphics.createBitmap
import android.annotation.SuppressLint
import androidx.core.graphics.toColorInt
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import kotlin.concurrent.thread
import android.text.Editable
import com.example.actimelteambrowser.data.HistoryVisit
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.ui.SearchSuggestion
import com.example.actimelteambrowser.ui.buildSearchSuggestions
import android.view.MotionEvent
import com.example.actimelteambrowser.core.settings.SearchEngines
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.graphics.Paint
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.transition.TransitionManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup

// Adresní pole, našeptávač, klávesnice a zadávání adresy (rozšíření MainActivity).
// Klepnuti do pole (dole i nahore): pole se vycisti, otevre se klavesnice a ukazou se navrhy
// Pozadi poli: spodni #D9D9D9, horni tmave; obrys (zelena) se kresli jen pri psani a pulzuje
internal fun MainActivity.setupFieldBackgrounds() {
    val sb = GradientDrawable()
    sb.setColor("#D9D9D9".toColorInt())
    sb.cornerRadius = dp(28).toFloat()
    val ub = GradientDrawable()
    ub.setColor("#7D7C7C".toColorInt())
    ub.cornerRadius = dp(20).toFloat()
    searchBg = sb
    urlBg = ub
    etSearchMain.background = sb
    findViewById<View>(R.id.urlContainer).background = ub
}

// Zeleny obrys obou poli pomalu roste na 200 % tloustky a vraci se zpet (cyklus ~1,8 s)
internal fun MainActivity.startOutlinePulse(top: Boolean) {
    // pulzuje jen pole, ve kterem se pise; druhe ma obrys vypnuty
    pulseAnim?.cancel()
    searchBg?.setStroke(0, Color.TRANSPARENT)
    urlBg?.setStroke(0, Color.TRANSPARENT)
    val target = if (top) urlBg else searchBg
    val green = accentState.value   // zelena, v anonymnim rezimu modra
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

internal fun MainActivity.stopOutlinePulse() {
    pulseAnim?.cancel()
    pulseAnim = null
    searchBg?.setStroke(0, Color.TRANSPARENT)
    urlBg?.setStroke(0, Color.TRANSPARENT)
}

internal fun MainActivity.startFreshInput(field: EditText) {
    setChromeHidden(false)
    startOutlinePulse(field === etTopUrl)
    // tlacitko Oblibene zmizi a pole se prodlouzi
    TransitionManager.beginDelayedTransition(bottomBar as ViewGroup)
    findViewById<View>(R.id.btnBookmarks).visibility = View.GONE
    suggestionsAtTop.value = (field === etTopUrl)
    lastTypedLen = 0
    // horni pole ukaze adresu aktualni stranky (kurzor na konci, jde upravit); dolni pole zacina prazdne (psani od nuly)
    if (field === etTopUrl && prefillCurrentUrl(field)) field.selectAll()   // cela adresa oznacena = jde jednim klepnutim prepsat
    else field.setText("")
    updateSuggestions("")
    field.post { showKeyboard(field) }
}

// Vlozi adresu aktualni stranky do pole a da kurzor na konec. false = neni co vlozit (nova karta, interni stranka)
internal fun MainActivity.prefillCurrentUrl(field: EditText): Boolean {
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
internal fun MainActivity.editCurrentUrl() {
    val field = if (etTopUrl.hasFocus()) etTopUrl else etSearchMain
    if (prefillCurrentUrl(field)) field.post { showKeyboard(field) }
}

// Pole ztratilo focus: pokud nepise zadne, vratit horni pole na adresu stranky a vycistit dolni
internal fun MainActivity.onInputFocusLost() {
    etSearchMain.postDelayed({
        if (!etSearchMain.hasFocus() && !etTopUrl.hasFocus()) {
            suggestions.value = emptyList()
            pinnedPage.value = null   // zeleny radek aktualni stranky musi zmizet taky (drive zustaval na obrazovce)
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
internal fun MainActivity.submitInput(raw: String) {
    val q = raw.trim()
    if (q.isEmpty()) return
    val url = if (q.contains(".") && !q.contains(" ")) {
        if (q.startsWith("http")) q else "https://$q"
    } else SearchEngines.searchUrl(q)
    webView.loadUrl(url)
    releaseInputFocus()
    hideKeyboard()
}

internal fun MainActivity.refreshKnownHosts(history: List<HistoryVisit>, bookmarks: List<Bookmark>) {
    // radi se podle poctu navstev, pak podle novosti (history je od nejnovejsi)
    val counts = LinkedHashMap<String, Int>()
    for (h in history) hostOf(h.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
    for (b in bookmarks) hostOf(b.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
    knownHosts = counts.entries.sortedByDescending { it.value }.map { it.key }
}

internal fun MainActivity.autoComplete(field: EditText, s: Editable) {
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
internal fun MainActivity.mirrorText(from: EditText, to: EditText) {
    if (syncingText || !from.hasFocus()) return
    syncingText = true
    to.setText(from.text.toString())
    to.setSelection(to.text.length)
    syncingText = false
}

// Zapise navstevu do historie (jen http/https, ne dvakrat po sobe stejnou adresu)
internal fun MainActivity.recordVisit(view: WebView?, url: String?) {
    val u = url ?: return
    if (view == null || u == "about:blank" || u == lastRecordedUrl || !isWebUrl(u)) return
    lastRecordedUrl = u
    val title = view.title.orEmpty()
    thread(name = "history-write") {
        quiet {
            historyRepo.recordVisit(u, title)
        }
    }
}

// Nasepavac: dotaz do historie/zalozek bezi na pozadi, vysledek se ukaze nad spodni listou
internal fun MainActivity.copyPinnedUrl(s: SearchSuggestion) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("url", s.url))
    // potvrzeni ("Zkopirovano") se ukaze primo na zelenem radku
}

internal fun MainActivity.updateSuggestions(query: String) {
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
                val now = SystemClock.uptimeMillis()
                if (empty || now - suggestCacheAt > 15000) {
                    cachedHist = historyRepo.recent(300)
                    cachedBms = bookmarksRepo.allBookmarks()
                    cachedTop = historyRepo.mostVisited(10)
                    refreshKnownHosts(cachedHist, cachedBms)
                    suggestCacheAt = now
                }
                buildSearchSuggestions(query, cachedHist, cachedBms, cachedTop)
            } catch (e: Exception) {
                noteError(e)
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

internal fun MainActivity.openSuggestion(url: String) {
    if (!isWebUrl(url)) return
    webView.loadUrl(url)
    releaseInputFocus()
    hideKeyboard()
    suggestions.value = emptyList()
    pinnedPage.value = null
}

internal fun MainActivity.showKeyboard(target: View = etSearchMain) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    @Suppress("DEPRECATION") imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
}

internal fun MainActivity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(etSearchMain.windowToken, 0)
}

// Focus z poli pryc: predat ho korenovemu layoutu. Pouhe clearFocus() ho Androidu necha prehodit na prvni
// editovatelne pole (druhe pole), cimz se naseptavac hned znovu otevrel.
internal fun MainActivity.releaseInputFocus() {
    root.isFocusableInTouchMode = true
    root.requestFocus()
    etSearchMain.clearFocus()
    etTopUrl.clearFocus()
}

// Zavre naseptavac + klavesnici (pole ztrati focus, onInputFocusLost vrati adresu)
internal fun MainActivity.dismissInput() {
    suggestionToken++                       // zahodi rozpracovany dotaz do historie
    releaseInputFocus()
    hideKeyboard()
    suggestions.value = emptyList()
    pinnedPage.value = null
}

// Klepnuti do horniho pole, ktere uz ma focus: naseptavac se znovu nacte (i z DB) a ukaze
internal fun MainActivity.refreshSuggestionsOnClick(field: EditText) {
    if (!field.hasFocus()) return
    suggestionsAtTop.value = (field === etTopUrl)
    suggestCacheAt = 0L   // vynuti nove nacteni historie/zalozek
    updateSuggestions(field.text.toString())
}

@Suppress("UnusedReceiverParameter")
internal fun MainActivity.isTouchInside(v: View, ev: MotionEvent): Boolean {
    if (v.visibility != View.VISIBLE) return false
    val loc = IntArray(2)
    v.getLocationOnScreen(loc)              // pocita i s posunem nad klavesnici
    return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
        ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
}


// Kruzek pri tazeni stranky dolu: plni se podle toho, jak daleko jsi pretahl (1 = pusteni obnovi stranku)
class PullRefreshView(context: Context) : View(context) {
    var progress = 0f
        set(value) { field = value; invalidate() }
    var color = 0xFF00B055.toInt()
    var loading = false           // po pusteni: krouzek se toci, dokud se stranka nenacte
        set(value) { field = value; invalidate() }
    var loadStartedAt = 0L
    private var spinner: ObjectAnimator? = null

    fun startSpin() {
        loading = true
        loadStartedAt = SystemClock.uptimeMillis()
        spinner?.cancel()
        spinner = ObjectAnimator.ofFloat(this, ROTATION, 0f, 360f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    fun stopSpin() {
        loading = false
        spinner?.cancel()
        spinner = null
        rotation = 0f
    }

    // kratke "cuknuti": krouzek se zvetsi a vrati se (je zaplneny = pripraveno)
    fun pop() {
        val up = ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.3f, 1f),
            PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.3f, 1f)
        )
        up.duration = 220
        up.start()
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xE61E1E1E.toInt() }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val rect = RectF()
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        canvas.drawCircle(cx, cy, r, bg)
        val pad = r * 0.38f
        rect.set(cx - r + pad, cy - r + pad, cx + r - pad, cy + r - pad)
        arc.color = color
        canvas.drawArc(rect, -90f, if (loading) 270f else 360f * progress, false, arc)
    }
}

internal fun MainActivity.updatePull(p: Float) {
    var v = pullView
    if (v == null) {
        v = PullRefreshView(this)
        v.elevation = dp(15).toFloat()
        val lp = ConstraintLayout.LayoutParams(dp(40), dp(40))
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        (root as ViewGroup).addView(v, lp)
        pullView = v
    }
    v.animate().cancel()
    v.stopSpin()
    v.color = accentState.value
    v.visibility = View.VISIBLE
    v.alpha = p
    v.translationY = (topBar.height + dp(10)).toFloat() + dp(24) * p
    v.progress = p
}

internal fun MainActivity.endPull(force: Boolean = false) {
    val v = pullView ?: return
    if (v.loading && !force) return   // behem nacitani se krouzek neschovava
    if (v.visibility != View.VISIBLE) return
    v.loading = false
    v.animate().alpha(0f).setDuration(150).withEndAction { v.stopSpin(); v.visibility = View.GONE }.start()
}

// Po pusteni: krouzek zustane a toci se, dokud se stranka nenacte (nejdele 15 s)
internal fun MainActivity.startPullLoading() {
    val v = pullView ?: return
    v.animate().cancel()
    v.alpha = 1f
    v.visibility = View.VISIBLE
    v.color = accentState.value
    v.startSpin()
    v.postDelayed({ if (v.loading) endPull(true) }, 15000)
}

// Stranka se nacetla: krouzek se schova (kratka prodleva, aby bylo videt aspon jedno otoceni)
internal fun MainActivity.finishPullLoading() {
    val v = pullView ?: return
    if (!v.loading) return
    val wait = (500L - (SystemClock.uptimeMillis() - v.loadStartedAt)).coerceAtLeast(0L)
    v.postDelayed({ endPull(true) }, wait)
}

// Hlavni barva plochy: zelena, v anonymnim rezimu modra #006AFF (lupa, obrys pole, naseptavac, AdBlock, animace)
internal fun MainActivity.applyAccent(incognito: Boolean) {
    val c = if (incognito) 0xFF006AFF.toInt() else 0xFF00B055.toInt()
    if (accentState.value == c) return
    accentState.value = c
    val d = AppCompatResources.getDrawable(this, R.drawable.ic_search)?.mutate()
    d?.setTint(c)
    etSearchMain.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
    applyAdBlockUi()
}


// Maly krizek na konci horniho pole (jen kdyz pole ma focus a neco obsahuje): klepnuti smaze text
internal fun MainActivity.updateTopClear() {
    val show = etTopUrl.hasFocus() && etTopUrl.text.isNotEmpty()
    if (!show) {
        etTopUrl.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        return
    }
    // ikona kopirovani adresy na zacatku pole
    val cs = dp(22)
    val cbmp = createBitmap(cs, cs)
    val cc = Canvas(cbmp)
    val cp = Paint(Paint.ANTI_ALIAS_FLAG)
    cp.style = Paint.Style.STROKE
    cp.color = "#CCCCCC".toColorInt()
    cp.strokeWidth = dp(2).toFloat() * 0.8f
    cc.drawRoundRect(cs * 0.12f, cs * 0.12f, cs * 0.62f, cs * 0.62f, dp(3).toFloat(), dp(3).toFloat(), cp)
    cc.drawRoundRect(cs * 0.38f, cs * 0.38f, cs * 0.88f, cs * 0.88f, dp(3).toFloat(), dp(3).toFloat(), cp)
    val size = dp(22)
    val bmp = createBitmap(size, size)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = "#888888".toColorInt()
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.color = Color.WHITE
    p.strokeWidth = dp(2).toFloat()
    p.strokeCap = Paint.Cap.ROUND
    val m = size * 0.32f
    c.drawLine(m, m, size - m, size - m, p)
    c.drawLine(size - m, m, m, size - m, p)
    etTopUrl.setCompoundDrawablesRelativeWithIntrinsicBounds(cbmp.toDrawable(resources), null, bmp.toDrawable(resources), null)
    etTopUrl.compoundDrawablePadding = dp(6)
}

@SuppressLint("ClickableViewAccessibility")   // tap na ikony v poli, klik na pole zustava
internal fun MainActivity.setupTopClear() {
    etTopUrl.setOnTouchListener { v, ev ->
        // ikona kopirovani (zacatek pole): zkopiruje adresu z pole
        if (ev.actionMasked == MotionEvent.ACTION_UP && etTopUrl.compoundDrawablesRelative[0] != null) {
            val cw = etTopUrl.compoundDrawablesRelative[0].bounds.width() + dp(14)
            if (ev.x <= v.paddingLeft + cw) {
                val t = etTopUrl.text.toString()
                if (t.isNotBlank()) copyToClipboard("url", t, "Adresa zkopírována")
                return@setOnTouchListener true
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP && etTopUrl.compoundDrawablesRelative[2] != null) {
            val w = etTopUrl.compoundDrawablesRelative[2].bounds.width() + dp(14)
            if (ev.x >= v.width - v.paddingRight - w) {
                etTopUrl.setText("")
                v.performClick()
                return@setOnTouchListener true
            }
        }
        false
    }
}
