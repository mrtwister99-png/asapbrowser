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

// Karty: vytváření, přepínání, složky, zavírání, ukládání a obnova, přehled karet (rozšíření MainActivity).
// Vytvori novou kartu s vlastnim WebView (a volitelne ji zobrazi a nacte adresu)
// (@SuppressLint je na buildWebView)
internal fun MainActivity.createTab(url: String?, activate: Boolean = true, incognito: Boolean = false): BrowserTab {
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

// Zobrazi danou kartu (jeji WebView vlozi do kontejneru)
internal fun MainActivity.switchTo(tab: BrowserTab) {
    // predchozi karta se pozastavi (JS, casovace, animace) - setri baterii a vykon
    if (isWebViewReady() && webView !== tab.webView) {
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

// Polozky prehledu v poradi karet: jedna karta, nebo slozka (na miste sve prvni karty)
internal fun MainActivity.buildItems(incognito: Boolean): MutableList<Pair<TabGroup?, List<BrowserTab>>> =
    tabManager.buildItems(tabs.list.filter { it.incognito == incognito }) { it.id }

// Slozky s mene nez 2 kartami zanikaji, zavrene karty se ze slozek odeberou
internal fun MainActivity.pruneGroups() {
    tabManager.prune(tabs.list.map { it.id }.toHashSet())
}

// Presune kartu / slozku na misto jine (pretahovani v prehledu); slozka se presouva i se vsemi kartami
internal fun MainActivity.moveTab(fromId: Long, toId: Long) {
    val modeTabs = tabs.list.filter { it.incognito == overviewIncognito }
    val flat = tabManager.reorderedFlat(modeTabs, { it.id }, fromId, toId) ?: return
    val positions = tabs.list.indices.filter { tabs.list[it].incognito == overviewIncognito }
    if (positions.size != flat.size) return
    for ((k, p) in positions.withIndex()) tabs.list[p] = flat[k]
    refreshTiles()
}

// Poradi karet uvnitr otevrene slozky
internal fun MainActivity.moveInGroup(groupTileId: Long, fromId: Long, toId: Long) {
    if (tabManager.moveInGroup(groupTileId, fromId, toId)) refreshTiles()
}

// Karta pustena na hlavicku jine karty = nova slozka, na hlavicku slozky = pridat do slozky
internal fun MainActivity.mergeTabs(dragId: Long, targetId: Long) {
    val members = tabManager.merge(dragId, targetId) ?: return
    // pretazena karta se v seznamu presune hned za posledni kartu slozky (slozka zustane na miste cile)
    val dragTab = tabs.list.firstOrNull { it.id == dragId } ?: return
    tabs.list.remove(dragTab)
    val lastIdx = tabs.list.indices.filter { tabs.list[it].id in members && tabs.list[it].id != dragId }.maxOrNull() ?: (tabs.list.size - 1)
    tabs.list.add(lastIdx + 1, dragTab)
    refreshTiles()
}

// Vyjme karty ze slozky (zustanou otevrene jako samostatne)
internal fun MainActivity.extractTabs(ids: List<Long>) {
    tabManager.removeFromGroups(ids)
    pruneGroups()
    refreshTiles()
}

// Zavre vic karet najednou (id slozky = zavre vsechny jeji karty)
internal fun MainActivity.closeTabs(ids: List<Long>) {
    val all = tabManager.expandIds(ids)
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
internal fun MainActivity.closeAllTabs() {
    clearAllTabs()
    createTab(homeUrl)
    refreshTiles()
}

// Smaze vsechna data anonymniho profilu (cookies, localStorage...). Smazani profilu selze, dokud ho nejaky WebView
// pouziva - pak se aspon vymazou data.
internal fun MainActivity.purgeIncognito() {
    if (!incognitoProfilesSupported()) return
    try {
        ProfileStore.getInstance().deleteProfile(MainActivity.INCOGNITO_PROFILE)
    } catch (e: Throwable) {
        try {
            val p = ProfileStore.getInstance().getProfile(MainActivity.INCOGNITO_PROFILE)
            p?.cookieManager?.removeAllCookies(null)
            p?.webStorage?.deleteAllData()
        } catch (e2: Throwable) { }
    }
}

// Cookies pro dane okno: anonymni karta ma vlastni (jinak by se pri stahovani poslaly cookies z beznych karet)
internal fun MainActivity.cookiesFor(url: String, incognito: Boolean): String? {
    if (incognito && incognitoProfilesSupported()) {
        try {
            return ProfileStore.getInstance().getOrCreateProfile(MainActivity.INCOGNITO_PROFILE).cookieManager.getCookie(url)
        } catch (e: Throwable) { }
        return null
    }
    return CookieManager.getInstance().getCookie(url)
}

// Odstrani WebView karty; u anonymni karty nejdriv smaze cache, formulare a historii prochazeni
internal fun MainActivity.disposeTab(tab: BrowserTab) {
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
internal fun MainActivity.closeAllInMode(incognito: Boolean) {
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

internal fun MainActivity.clearAllTabs() {
    for (tab in tabs.list) {
        rememberClosed(tab)
        disposeTab(tab)
    }
    tabs.list.clear()
    tabManager.clear()
    tabs.activeId = -1L
}

// ULOZENI A OBNOVA KARET: pri opusteni appky se ulozi adresy, po spusteni se obnovi
internal fun MainActivity.saveTabs() {
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

// Ulozi nahledy karet mimo UI vlakno: prepise jen ty, ktere se zmenily, a smaze p<i>.jpg navic
internal fun MainActivity.writePreviewsAsync(dir: File, previews: List<Bitmap?>) {
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

internal fun MainActivity.savePreview(bmp: Bitmap, file: File) {
    try {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    } catch (e: Exception) {
    }
}

// Drzeni tlacitka Domu: adresa domovske stranky + co delat po spusteni prohlizece
internal fun MainActivity.showHomeDialog() {
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

internal fun MainActivity.restoreTabs() {
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
                    val g = tabManager.newGroup(ids)
                    if (obj != null && obj.has("c")) g.color = obj.optInt("c", g.color)
                }
            }
        }
    } catch (e: Exception) {
    }
    switchTo(created[active.coerceIn(0, created.size - 1)])
    updateTabCount()
}

internal fun MainActivity.closeTab(id: Long) {
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
internal fun MainActivity.updateTabCount() {
    // cislo se ukazuje jen kdyz ma tlacitko 2 vychozi funkci (jinak je tam ikona funkce)
    val incog = tabs.active()?.incognito ?: false
    if (isDefaultVisual(2)) findViewById<TextView>(R.id.tvTabCount).text = tabs.list.count { it.incognito == incog }.toString()
}

internal fun MainActivity.setupTabsOverlay() {
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
                tabManager.groupByTile(tileId)?.color = color
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

internal fun MainActivity.openTabsOverview() {
    hideKeyboard()
    etSearchMain.clearFocus()
    val active = tabs.active()
    overviewIncognito = active?.incognito == true   // prehled se otevre ve skupine aktivni karty
    if (active == null) showTabs() else capturePreview(active) { showTabs() }
}

internal fun MainActivity.showTabs() {
    refreshTiles()
    tabsView.visibility = View.VISIBLE
}

internal fun MainActivity.hideTabs() {
    tabsView.visibility = View.GONE
}

internal fun MainActivity.refreshTiles() {
    fun tileOf(t: BrowserTab) = TabTile(t.id, t.title, t.url, t.preview, t.id == tabs.activeId, t.incognito)
    tabTiles.value = buildItems(overviewIncognito).map { (g, ms) ->
        if (g == null) tileOf(ms[0])
        else TabTile(-g.id, "", "", null, ms.any { it.id == tabs.activeId }, overviewIncognito, true, ms.map { tileOf(it) }, g.color)
    }
}

// Ulozi nahled (screenshot) karty, hotovo() se zavola vzdy (i kdyz se nahled nepovede)
internal fun MainActivity.capturePreview(tab: BrowserTab, done: () -> Unit) {
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

// Hvezdicka na dlazdici karty: ulozi stranku teto karty do vybrane slozky
internal fun MainActivity.saveTabToFolder(id: Long) {
    val tab = tabs.list.firstOrNull { it.id == id } ?: return
    val u = tab.pendingUrl ?: tab.url.ifBlank { tab.liveUrl() }
    chooseFolderAndSave(u, tab.title)
}

internal fun MainActivity.chooseFolderAndSave(curUrl: String?, pageTitle: String) {
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
