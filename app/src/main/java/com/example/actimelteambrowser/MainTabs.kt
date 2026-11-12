package com.example.actimelteambrowser

import androidx.core.graphics.toColorInt
import android.annotation.SuppressLint
import androidx.core.graphics.createBitmap
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.core.view.*
import kotlin.concurrent.thread
import com.example.actimelteambrowser.core.settings.AppSettings
import android.graphics.Bitmap
import android.view.MotionEvent
import com.example.actimelteambrowser.core.settings.SearchEngines
import androidx.webkit.ProfileStore
import androidx.constraintlayout.widget.ConstraintLayout
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.util.TypedValue
import android.text.InputType
import java.io.File
import java.io.FileOutputStream
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
import com.example.actimelteambrowser.ui.TabTile
import com.example.actimelteambrowser.ui.TabsScreen

// Karty: vytváření, přepínání, složky, zavírání, ukládání a obnova, přehled karet (rozšíření MainActivity).
// Vytvori novou kartu s vlastnim WebView (a volitelne ji zobrazi a nacte adresu)
// (@SuppressLint je na buildWebView)
internal fun MainActivity.createTab(url: String?, activate: Boolean = true, incognito: Boolean = false, fly: Boolean = true): BrowserTab {
    // WebView se postavi az pri prvnim pouziti (buildWebView); obnovene karty na pozadi ho tedy nemaji
    var ref: BrowserTab? = null
    val tab = BrowserTab(tabs.newId()) { buildWebView(ref!!) }
    ref = tab
    tab.incognito = incognito
    tab.color = TabColors.random(tabs.list.lastOrNull()?.color ?: 0)   // kazda nova karta ma nahodnou barvu (jinou nez soused)
    tabs.list.add(tab)
    if (activate) switchTo(tab)
    updateTabCount()
    // animace: zelene + (normalni karta) / modre + (anonymni); az po rozlozeni okna, takze ne pri startu
    if (fly && activate && root.isLaidOut) {
        if (incognito) flyBurst("+", "#2979FF") else flyBurst("+", "#00B055")
        softBuzz()   // nova karta: jednou lehce zabrni
    }
    if (url != null) tab.webView.loadUrl(url)
    if (!activate && tab.webViewCreated) { quiet { tab.webView.onPause() } }   // karta na pozadi nema bezet
    return tab
}

// Zobrazi danou kartu (jeji WebView vlozi do kontejneru)
internal fun MainActivity.switchTo(tab: BrowserTab) {
    // predchozi karta se pozastavi (JS, casovace, animace) - setri baterii a vykon
    if (isWebViewReady() && webView !== tab.webView) {
        quiet { webView.onPause() }
    }
    quiet { tab.webView.onResume() }
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
    // anonymni karta: hlavni barva plochy je modra (text v polich zustava bily nahore / cerny dole)
    etTopUrl.setTextColor(Color.WHITE)
    etSearchMain.setTextColor(Color.BLACK)
    applyAccent(tab.incognito)
    updateLockIcon(tab.url)   // zamek v anonymnim rezimu modry
    progressBar.visibility = View.GONE
    updateAdBlockBadge()
    updateTabCount()
    updateStartPage()
    updateDeskBtn()
    updateNavArrows()
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
    flyBurst("−", "#E53935")   // cervene minus pri zavreni
    val activeClosed = toClose.any { it.id == tabs.activeId }
    val mode = toClose[0].incognito
    for (tab in toClose) {
        rememberClosed(tab)
        disposeTab(tab)
    }
    tabs.list.removeAll(toClose.toSet())
    pruneGroups()
    if (tabs.list.isEmpty()) createTab(homeUrl, fly = false)
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
@SuppressLint("RequiresFeature")   // hlidano pres incognitoProfilesSupported()
internal fun MainActivity.purgeIncognito() {
    if (!incognitoProfilesSupported()) return
    runCatching {
        ProfileStore.getInstance().deleteProfile(MainActivity.INCOGNITO_PROFILE)
    }.onFailure {
        quiet {
            val p = ProfileStore.getInstance().getProfile(MainActivity.INCOGNITO_PROFILE)
            p?.cookieManager?.removeAllCookies(null)
            p?.webStorage?.deleteAllData()
        }
    }
}

// Cookies pro dane okno: anonymni karta ma vlastni (jinak by se pri stahovani poslaly cookies z beznych karet)
@SuppressLint("RequiresFeature")   // hlidano pres incognitoProfilesSupported()
internal fun MainActivity.cookiesFor(url: String, incognito: Boolean): String? {
    if (incognito && incognitoProfilesSupported()) {
        quiet {
            return ProfileStore.getInstance().getOrCreateProfile(MainActivity.INCOGNITO_PROFILE).cookieManager.getCookie(url)
        }
        return null
    }
    return CookieManager.getInstance().getCookie(url)
}

// Odstrani WebView karty; u anonymni karty nejdriv smaze cache, formulare a historii prochazeni
internal fun MainActivity.disposeTab(tab: BrowserTab) {
    if (!tab.webViewCreated) return   // WebView se nikdy nevytvoril - neni co rusit
    (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
    if (tab.incognito) {
        quiet {
            tab.webView.clearCache(true)
            tab.webView.clearFormData()
            tab.webView.clearHistory()
        }
    }
    quiet { tab.webView.destroy() }
    // po zavreni posledni anonymni karty smazat jeji data (az po dokonceni prave probihajici akce)
    if (tab.incognito) window.decorView.post { if (tabs.list.none { it.incognito }) purgeIncognito() }
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
        arr.put(JSONObject().put("url", u).put("title", tab.title).put("pin", tab.pinned).put("pt", tab.pinnedTop).put("col", tab.color))
    }
    if (arr.length() == 0) return
    // slozky: seznamy poradi karet v ulozenem seznamu
    val garr = JSONArray()
    for (g in groups) {
        val idxs = g.members.mapNotNull { savedIdx[it] }
        if (idxs.size >= 2) garr.put(JSONObject().put("m", JSONArray(idxs)).put("c", g.color).put("n", g.name))
    }
    AppSettings.savedTabs.set(JSONObject().put("active", activeIdx).put("tabs", arr).put("groups", garr).toString())
    writePreviewsAsync(previewDir, previews)
}

// Ulozi nahledy karet mimo UI vlakno: prepise jen ty, ktere se zmenily, a smaze p<i>.jpg navic
internal fun MainActivity.writePreviewsAsync(dir: File, previews: List<Bitmap?>) {
    previewExecutor.execute {
        quiet {
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
        }
    }
}

@Suppress("UnusedReceiverParameter")
internal fun MainActivity.savePreview(bmp: Bitmap, file: File) {
    quiet {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }
}

// Drzeni tlacitka Domu: adresa domovske stranky + co delat po spusteni prohlizece
internal fun MainActivity.showHomeDialog() {
    val green = "#00B055".toColorInt()
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
                AppToast.show(this, "Neplatná adresa (musí být http nebo https)", true)
                false
            } else {
                AppSettings.homeUrl.set(u)
                AppSettings.startupRestoreTabs.set(restoreRef?.isChecked == true)
                engineBtns.firstOrNull { it.second.isChecked }?.let { AppSettings.searchEngine.set(it.first) }
                AppToast.show(this, "Uloženo")
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
        input.setHintTextColor("#888888".toColorInt())
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        val inputBg = GradientDrawable()
        inputBg.setColor("#2A2A2A".toColorInt())
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
                AppToast.show(this, "Tuhle stránku nelze nastavit jako domovskou")
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
        rbRestore.text = getString(R.string.home_restore_tabs)
        val rbNew = RadioButton(this)
        rbNew.id = View.generateViewId()
        rbNew.text = getString(R.string.home_new_tab)
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
    val pins = ArrayList<Boolean>()
    val pinTops = ArrayList<Boolean>()
    val colors = ArrayList<Int>()
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
                    pins.add(e.optBoolean("pin", false))
                    pinTops.add(e.optBoolean("pt", false))
                    colors.add(e.optInt("col", 0))
                    srcIdx.add(i)
                }
            }
        }
    } catch (e: Exception) {
        noteError(e)
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
        tab.pinned = pins[i]
        tab.pinnedTop = pinTops[i]
        if (colors[i] != 0) tab.color = colors[i]
        tab.pendingUrl = urls[i]
        tab.preview = loadPreview(File(File(filesDir, "tab_previews"), "p$i.jpg"))
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
                    if (obj != null && obj.has("n")) g.name = obj.optString("n")
                }
            }
        }
    } catch (e: Exception) {
        noteError(e)
    }
    switchTo(created[active.coerceIn(0, created.size - 1)])
    updateTabCount()
}

private var closingAnim = false

// Zavreni karty: aktivni karta se "vtahne" dolu do stredu obrazovky (zmensi se k dolnimu okraji a zmizi)
internal fun MainActivity.closeTab(id: Long) {
    if (AppSettings.animationsOn.get() && !closingAnim && id == tabs.activeId && tabsView.visibility != View.VISIBLE && webContainer.height > 0) {
        closingAnim = true
        webContainer.pivotX = webContainer.width / 2f
        webContainer.pivotY = webContainer.height.toFloat()
        webContainer.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(240)
            .withEndAction {
                closingAnim = false
                closeTabNow(id)
                webContainer.animate().cancel()
                webContainer.scaleX = 1f; webContainer.scaleY = 1f; webContainer.alpha = 1f
            }.start()
    } else closeTabNow(id)
}

internal fun MainActivity.closeTabNow(id: Long) {
    val idx = tabs.list.indexOfFirst { it.id == id }
    if (idx < 0) return
    flyBurst("−", "#E53935")   // cervene minus pri zavreni
    val tab = tabs.list.removeAt(idx)
    rememberClosed(tab)
    val wasActive = tab.id == tabs.activeId
    disposeTab(tab)
    pruneGroups()
    if (tabs.list.isEmpty()) {
        createTab(homeUrl, fly = false)          // vzdy zustane aspon jedna karta
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
    updateTabStrips()
}

internal fun MainActivity.setupTabsOverlay() {
    tabsView.setContent {
        TabsScreen(
            tiles = tabTiles.value,
            incognito = overviewIncognito && !overviewGroups,
            groupsMode = overviewGroups,
            onGroupsMode = {
                overviewGroups = true
                applyAccent(false)
                refreshTiles()
            },
            onNewGroup = { showNewGroupDialog() },
            onTabColor = { id, color ->
                tabs.list.firstOrNull { it.id == id }?.color = color
                refreshTiles(); updateTabStrips(); saveTabs()
            },
            normalCount = tabs.list.count { !it.incognito },
            incognitoCount = tabs.list.count { it.incognito },
            onToggleIncognito = { inc ->
                overviewGroups = false
                overviewIncognito = inc
                applyAccent(inc)
                refreshTiles()
            },
            onSelect = { id ->
                tabs.list.firstOrNull { it.id == id }?.let { switchTo(it) }
                hideTabs()
            },
            onClose = { id -> closeTab(id) },
            onDeleteDragged = { id -> deleteCardWithUndo(id) },
            onSave = { id -> saveTabToFolder(id) },
            onMove = { fromId, toId -> moveTab(fromId, toId) },
            onMoveInFolder = { folderId, fromId, toId -> moveInGroup(folderId, fromId, toId) },
            onMerge = { dragId, targetId -> mergeTabs(dragId, targetId) },
            onCloseMany = { ids -> closeTabs(ids) },
            onExtract = { ids -> extractTabs(ids) },
            onFolderColor = { tileId, color ->
                tabManager.groupByTile(tileId)?.color = color
                refreshTiles(); updateTabStrips(); saveTabs()
            },
            onFolderRename = { tileId, name ->
                tabManager.groupByTile(tileId)?.name = name.trim().take(40)
                refreshTiles()
            },
            onNew = { folderId ->
                if (overviewIncognito) createTab(homeUrl, incognito = true) else openStartTab()
                // otevrena slozka: nova karta patri do ni (v poradi hned za jeji posledni kartou)
                val g = folderId?.let { tabManager.groupByTile(it) }
                val t = tabs.active()
                if (g != null && t != null) {
                    val last = tabs.list.indexOfLast { it.id in g.members }
                    g.members.add(t.id)
                    if (last >= 0) { tabs.list.remove(t); tabs.list.add((last + 1).coerceAtMost(tabs.list.size), t) }
                    updateTabStrips()
                    saveTabs()
                }
                hideTabs()
            },
            onDismiss = { hideTabs() },
            session = overviewSession.intValue
        )
    }
}

internal fun MainActivity.openTabsOverview() {
    hideKeyboard()
    etSearchMain.clearFocus()
    val active = tabs.active()
    overviewIncognito = active?.incognito == true   // prehled se otevre ve skupine aktivni karty
    overviewGroups = false
    if (active == null) showTabs() else capturePreview(active) { showTabs() }
}

internal fun MainActivity.showTabs() {
    overviewSession.intValue++   // prehled zacina na hlavni urovni (zadna otevrena slozka)
    refreshTiles()
    tabsView.visibility = View.VISIBLE
}

internal fun MainActivity.hideTabs() {
    tabsView.visibility = View.GONE
    applyAccent(tabs.active()?.incognito == true)   // po zavreni prehledu barva podle aktivni karty
}

internal fun MainActivity.refreshTiles() {
    fun tileOf(t: BrowserTab) = TabTile(t.id, t.title, t.url, t.preview, t.id == tabs.activeId, t.incognito, color = t.color)
    val inc = overviewIncognito && !overviewGroups   // Skupiny = skupiny beznych karet
    tabTiles.value = buildItems(inc).mapNotNull { (g, ms) ->
        if (g == null) { if (overviewGroups) null else tileOf(ms[0]) }
        else TabTile(-g.id, "", "", null, ms.any { it.id == tabs.activeId }, inc, true, ms.map { tileOf(it) }, g.color, g.name)
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
    val bmp = createBitmap(bw, bh)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val loc = IntArray(2)
        wv.getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + wv.width, loc[1] + wv.height)
        runCatching {
            PixelCopy.request(window, rect, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    tab.preview = bmp
                    saveSiteThumb(tab)
                }
                done()
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            done()
        }
    } else {
        quiet {
            val canvas = Canvas(bmp)
            canvas.scale(bw.toFloat() / wv.width, bw.toFloat() / wv.width)
            wv.draw(canvas)
            tab.preview = bmp
            saveSiteThumb(tab)
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

// demo = ukazka prvniho tutorialu: radky se jen ukazou (demoTargets folder0, folder1...), klepnuti nic neulozi
internal fun MainActivity.chooseFolderAndSave(curUrl: String?, pageTitle: String, demo: Boolean = false) {
    if (curUrl == null || !isWebUrl(curUrl)) {
        AppToast.show(this, "Tuhle stranku nelze ulozit")
        return
    }
    thread(name = "bookmark-folders") {
        val folders = try {
            bookmarksRepo.foldersForPicker()
        } catch (e: Exception) {
            noteError(e)
            emptyList()
        }
        runOnUiThread {
            if (folders.isEmpty()) return@runOnUiThread
            if (demo) demoTargets.clear()
            showStyledDialog(title = "Přidat do složky") { dismiss ->
                val list = LinearLayout(this)
                list.orientation = LinearLayout.VERTICAL
                for ((fi, f) in folders.withIndex()) {
                    val row = TextView(this)
                    row.text = getString(R.string.folder_row, f.name)
                    row.setTextColor(Color.WHITE)
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    row.setPadding(dp(16), dp(14), dp(16), dp(14))
                    val rowBg = GradientDrawable()
                    rowBg.setColor("#2A2A2A".toColorInt())
                    rowBg.cornerRadius = dp(14).toFloat()
                    row.background = rowBg
                    if (demo) demoTargets["folder$fi"] = row
                    row.setOnClickListener {
                        dismiss()
                        if (demo) return@setOnClickListener
                        thread(name = "bookmark-add") {
                            quiet {
                                bookmarksRepo.addBookmark(f.id, curUrl, pageTitle)
                            }
                            runOnUiThread {
                                softBuzz(35)
                                AppToast.show(this, "Přidáno do: " + f.name)
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


// ---------------- Listy karet (barevne dlazdice s ikonou webu) ----------------
// Spodni lista: prvnich 5 karet, horni: dalsi (od 6.). Je-li zapnuta jen jedna, ukazuje vsechny karty. Aktivni karta je vyssi a jemne pulzuje.
// Kazda lista se posouva zvlast. Web se o vysku listy zmensi (updateWebPadding).

internal var lastSystemTopPx = 0
internal var stripTopPx = 0
internal var stripBottomPx = 0
@SuppressLint("StaticFieldLeak")
private var stripTop: HorizontalScrollView? = null
@SuppressLint("StaticFieldLeak")
private var stripBottom: HorizontalScrollView? = null
private var pulseAnims = ArrayList<ValueAnimator>()

private val NORMAL_BOTTOM = listOf("#FFEB3B", "#E53935", "#2962FF", "#4CE620", "#8D5A33")
private val NORMAL_TOP = listOf("#9EA3A8", "#B0156A", "#FF80FF", "#008055", "#FF5A00")
private val INCOG_BOTTOM = listOf("#0D47A1", "#1565C0", "#1976D2", "#1E88E5", "#2196F3")   // anonymni: dole odstiny modre
private val INCOG_TOP = listOf("#1B5E20", "#2E7D32", "#388E3C", "#43A047", "#4CAF50")      // nahore odstiny zelene

// Dalsi barvy karet od 11. karty dal (padaji nahodne; barva je stala podle id karty, v obou listach stejna)
private val EXTRA_COLORS = listOf(
    "#00ff1a", "#00ff84", "#9dfcce", "#024726", "#36baa4", "#00ffd5", "#00ffff", "#94e0e0",
    "#00c8f5", "#1776a6", "#0084ff", "#004cff", "#0800ff", "#5d00ff", "#9900ff", "#dd00ff",
    "#ff00c3", "#ff0059", "#ba5b7c", "#52323d", "#5b4585", "#bfc4e3", "#7d65a6", "#ff69f2",
    "#d4ff00", "#004cff", "#616161", "#2e2e2e", "#c2c2c2", "#969696", "#707070", "#e0d83a",
    "#222b63", "#910000", "#ff0000", "#992c2c", "#3d1212", "#968080", "#d9a275", "#f59445",
    "#ff7300", "#bf5600", "#ff4d00", "#913f1c", "#f0a400", "#fae8c3", "#7a6026", "#abb00e",
    "#f7ff0f", "#8e8f7b", "#c6fa0a", "#779605", "#a0ba43", "#98e802", "#527d02", "#2f7d02",
    "#64d622", "#71de5b"
)

// Barva karty podle jejiho poradi v celem seznamu: 1-5 pevne (spodni), 6-10 pevne (horni), od 11. nahodne ze seznamu
private fun tabColor(all: List<BrowserTab>, tab: BrowserTab, incog: Boolean): String {
    val idx = all.indexOfFirst { it.id == tab.id }.coerceAtLeast(0)
    val bottom = if (incog) INCOG_BOTTOM else NORMAL_BOTTOM
    val top = if (incog) INCOG_TOP else NORMAL_TOP
    if (idx < 5) return bottom[idx]
    if (idx < 10) return top[idx - 5]
    val pool = if (incog) INCOG_BOTTOM + INCOG_TOP else EXTRA_COLORS
    fun pick(t: BrowserTab) = (((t.id * 2654435761L) ushr 8) % pool.size).toInt()
    var k = pick(tab)
    if (idx > 10 && pick(all[idx - 1]) == k) k = (k + 1) % pool.size   // soused nema stejnou barvu
    return pool[k]
}

private var shownTopIds: Set<Long> = emptySet()      // karty, ktere lista prave ukazuje (pro pripnuti na stejnem miste)
private var shownBottomIds: Set<Long> = emptySet()
private var lastTapId = -1L                            // dvojklik na dlazdici = rychle menu
private var lastTapAt = 0L
@SuppressLint("StaticFieldLeak")
private var hintTop: TextView? = null      // "+N" u horni listy (vlevo)
@SuppressLint("StaticFieldLeak")
private var hintBottom: TextView? = null   // "+N" u spodni listy (vpravo)

@Suppress("UnusedReceiverParameter")
internal fun MainActivity.stripViews(): List<Pair<View, Boolean>> =
    listOfNotNull(stripTop?.let { it to true }, stripBottom?.let { it to false }, hintTop?.let { it to true }, hintBottom?.let { it to false })

// Ukazatel poctu karet, ktere se v zadne liste nevejdou: nahore uplne vlevo, dole uplne vpravo
private fun MainActivity.setHint(top: Boolean, count: Int) {
    var tv = if (top) hintTop else hintBottom
    if (tv == null) {
        tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(7), 0, dp(7), 0)
        val g = GradientDrawable()
        g.setColor("#E6141414".toColorInt())
        g.setStroke(dp(1), "#88FFFFFF".toColorInt())
        g.cornerRadius = dp(10).toFloat()
        tv.background = g
        tv.elevation = dp(6).toFloat()   // nad listou
        val lp = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(20))
        if (top) { lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID; lp.marginStart = dp(10); lp.topToTop = (stripTop ?: return).id; lp.topMargin = dp(5) }
        else { lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID; lp.marginEnd = dp(10); lp.bottomToBottom = (stripBottom ?: return).id; lp.bottomMargin = dp(5) }
        (root as ConstraintLayout).addView(tv, lp)
        if (top) hintTop = tv else hintBottom = tv
    }
    tv.text = getString(R.string.hint_more, count)
    tv.visibility = if (count > 0) View.VISIBLE else View.GONE
}

// Web je mezi listami: nahore konci horni lista (+ lista karet), dole zacina spodni (+ lista karet)
internal fun MainActivity.updateWebPadding() {
    webContainer.setPadding(0, lastSystemTopPx + 16 + dp(52) + stripTopPx, 0, lastSystemBottom + 16 + dp(48) + stripBottomPx)
}

private fun MainActivity.ensureStrip(top: Boolean): HorizontalScrollView {
    (if (top) stripTop else stripBottom)?.let { return it }
    val sv = HorizontalScrollView(this)
    sv.id = View.generateViewId()
    sv.isHorizontalScrollBarEnabled = false
    sv.overScrollMode = View.OVER_SCROLL_NEVER
    sv.visibility = View.GONE
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.setPadding(dp(8), 0, dp(8), 0)
    sv.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)))
    val lp = ConstraintLayout.LayoutParams(0, dp(30))
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    if (top) { lp.topToBottom = R.id.progressBar; lp.topMargin = dp(2) }
    else { lp.bottomToTop = R.id.bottomBar; lp.bottomMargin = dp(4) }
    (root as ConstraintLayout).addView(sv, lp)
    if (top) stripTop = sv else stripBottom = sv
    return sv
}

internal fun MainActivity.updateTabStrips() {
    pulseAnims.forEach { it.cancel() }
    pulseAnims.clear()
    val wantBottom = AppSettings.tabStripBottom.get()
    val wantTop = AppSettings.tabStripTop.get()
    val imeUp = lastImeShift != 0f
    val incog = tabs.active()?.incognito ?: false
    val list = buildItems(incog).flatMap { it.second }   // karty jedne skupiny jsou v liste vedle sebe (spojene)
    val bottomList: List<BrowserTab>
    val topList: List<BrowserTab>
    if (wantBottom && wantTop) {
        // dole 1-5 (posunem 6), nahore poslednich 5 (posunem o 1 dal); vychozi pet druhe strany tam neni videt
        val topBase = list.drop(5).takeLast(6)
        val base = list.take(6)
        // pripnuta karta zustava v te liste, kde byla pri pripnuti (na svem miste v poradi)
        val bottomSet = list.filter { it in base || (it.pinned && !it.pinnedTop && it !in topBase) }.toMutableSet()
        val topSet = list.filter { it in topBase || (it.pinned && it.pinnedTop && it !in base) }.toMutableSet()
        // skupina, ktera se dole nevejde cela: zacina vpravo na spodni liste a pokracuje vlevo na horni
        for (g in groups) {
            val ms = list.filter { it.id in g.members }
            val inBottom = ms.filter { it in bottomSet }
            if (inBottom.isNotEmpty() && inBottom.size < ms.size) {
                val rest = ms - inBottom.toSet()
                topSet.removeAll(inBottom.toSet())
                topSet.addAll(rest)
            }
        }
        bottomList = list.filter { it in bottomSet }
        topList = list.filter { it in topSet }
    }
    else if (wantBottom) { bottomList = list; topList = emptyList() }
    else if (wantTop) { bottomList = emptyList(); topList = list }
    else { bottomList = emptyList(); topList = emptyList() }
    shownTopIds = topList.map { it.id }.toSet(); shownBottomIds = bottomList.map { it.id }.toSet()
    fill(true, topList.takeIf { !imeUp } ?: emptyList(), incog)
    fill(false, bottomList.takeIf { !imeUp } ?: emptyList(), incog)
    // karty, ktere nejsou v zadne liste (uprostred pri 12+): "+N" nahore vlevo / dole vpravo
    val hidden = if (wantBottom && wantTop && !imeUp) list.size - (bottomList + topList).distinctBy { it.id }.size else 0
    if (hidden > 0 || hintTop != null) setHint(true, hidden)
    if (hidden > 0 || hintBottom != null) setHint(false, hidden)
    stripTopPx = if (topList.isNotEmpty() && !imeUp) dp(34) else 0
    stripBottomPx = if (bottomList.isNotEmpty() && !imeUp) dp(34) else 0
    updateWebPadding()
}

private fun MainActivity.fill(top: Boolean, list: List<BrowserTab>, incog: Boolean) {
    if (list.isEmpty()) {
        val old = if (top) stripTop else stripBottom
        (old?.getChildAt(0) as? LinearLayout)?.removeAllViews()   // dalsi sestaveni = zase vychozi pozice
        old?.visibility = View.GONE
        return
    }
    val sv = ensureStrip(top)
    val row = sv.getChildAt(0) as LinearLayout
    // pozice posunu pred prestavbou (horni lista se drzi od konce, spodni od zacatku)
    val firstBuild = row.isEmpty()
    val keepStart = sv.scrollX
    val keepFromEnd = (row.width - sv.width) - sv.scrollX
    row.removeAllViews()
    row.overlay.clear()   // stare obrysy skupin
    sv.visibility = View.VISIBLE
    val screenW = resources.displayMetrics.widthPixels - dp(16)
    val gap = dp(3)
    val tileW = (screenW - gap * 4) / 5          // 5 dlazdic na sirku, dalsi se posouvaji
    val all = tabs.list.filter { it.incognito == incog }
    var activeLeft = -1
    // skupina pokracujici na druhe liste: dole posledni karta dojede az na pravy okraj, nahore prvni az na levy
    val other = if (top) shownBottomIds else shownTopIds
    val edgeIdx = if (top) 0 else list.size - 1
    val edgeOpen = list.isNotEmpty() && groupOf(list[edgeIdx].id)?.members?.any { it in other } == true
    row.setPadding(if (top && edgeOpen) 0 else dp(8), 0, if (!top && edgeOpen) 0 else dp(8), 0)
    var contentW = 0
    list.forEachIndexed { i, tab ->
        val g = groupOf(tab.id)
        val jn = g != null && i < list.size - 1 && groupOf(list[i + 1].id) === g
        contentW += tileW + (if (jn) 0 else gap)
    }
    contentW -= gap   // za posledni kartou mezera neni potreba
    val fullW = resources.displayMetrics.widthPixels
    val edgeExtra = if (edgeOpen) (fullW - dp(8) - contentW).coerceAtLeast(0) else 0
    list.forEachIndexed { i, tab ->
        val active = tab.id == tabs.activeId
        val h = if (active) dp(30) else dp(26)
        val tile = FrameLayout(this)
        val bg = GradientDrawable()
        val grp = groupOf(tab.id)   // skupina ma jen obrys kolem svych karet, karty si nechavaji svou barvu
        bg.setColor(tab.color.takeIf { it != 0 } ?: tabColor(all, tab, incog).toColorInt())
        if (active) bg.setStroke((1.5f * resources.displayMetrics.density).toInt(), Color.WHITE)   // aktualni karta: tenky bily obrys
        // karty jedne skupiny jsou spojene dohromady: mezi nimi zadna mezera a rovne vnitrni rohy
        val joinPrev = grp != null && i > 0 && groupOf(list[i - 1].id) === grp
        val joinNext = grp != null && i < list.size - 1 && groupOf(list[i + 1].id) === grp
        val r = dp(8).toFloat()
        val atEdge = edgeOpen && i == edgeIdx
        val l = if (joinPrev || (atEdge && top)) 0f else r
        val rr = if (joinNext || (atEdge && !top)) 0f else r
        bg.cornerRadii = floatArrayOf(l, l, rr, rr, rr, rr, l, l)
        tile.background = bg
        if (joinPrev) {
            // tenka delici cara mezi kartami skupiny
            val sep = View(this)
            sep.setBackgroundColor("#55000000".toColorInt())
            tile.addView(sep, FrameLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        }
        // ikona webu na skoro cernem podkladu
        val box = FrameLayout(this)
        val bb = GradientDrawable()
        bb.setColor("#141414".toColorInt())
        bb.cornerRadius = dp(6).toFloat()
        box.background = bb
        val iv = ImageView(this)
        val fav = tab.favicon
        if (fav != null) iv.setImageBitmap(fav) else iv.setImageResource(R.drawable.ic_globe)
        box.addView(iv, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER))
        tile.addView(box, FrameLayout.LayoutParams(dp(30), h - dp(8), Gravity.CENTER))
        if (tab.pinned) {
            val pin = TextView(this)
            pin.text = "📌"
            pin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            tile.addView(pin, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        }
        attachTileTouch(tile, tab, list, row, sv)
        val lp = LinearLayout.LayoutParams(tileW + (if (atEdge) edgeExtra else 0), h)
        lp.gravity = if (top) Gravity.TOP else Gravity.BOTTOM
        lp.marginEnd = if (joinNext || (atEdge && !top)) 0 else gap
        row.addView(tile, lp)
        if (active) activeLeft = i * (tileW + gap)
    }
    sv.post {
        drawGroupOutlines(top, list, row)
        val maxScroll = (row.width - sv.width).coerceAtLeast(0)
        val target = when {
            firstBuild -> if (top) maxScroll else 0   // vychozi: horni lista na konci (poslednich 5), spodni na zacatku
            top -> (maxScroll - keepFromEnd).coerceIn(0, maxScroll)
            else -> keepStart.coerceIn(0, maxScroll)
        }
        sv.scrollTo(target, 0)
        // aktivni karta mimo viditelnou cast = dojet na ni (jinak zustane posun, jak ho uzivatel nechal)
        if (activeLeft >= 0 && (firstBuild || activeLeft < target || activeLeft + tileW > target + sv.width)) {
            sv.smoothScrollTo((activeLeft - (screenW - tileW) / 2).coerceIn(0, maxScroll), 0)
        }
    }
}


// Obrys kolem karet jedne skupiny v barve skupiny. Skupina rozdelena mezi listy je na tom konci otevrena:
// dole pokracuje doprava (za okraj), nahore prichazi zleva.
private fun MainActivity.drawGroupOutlines(top: Boolean, list: List<BrowserTab>, row: LinearLayout) {
    row.overlay.clear()
    val stroke = dp(2)
    val r = dp(9).toFloat()
    var i = 0
    while (i < list.size) {
        val g = groupOf(list[i].id)
        if (g == null) { i++; continue }
        var j = i
        while (j + 1 < list.size && groupOf(list[j + 1].id) === g) j++
        val first = row.getChildAt(i) ?: break
        val last = row.getChildAt(j) ?: break
        var t = Int.MAX_VALUE; var b = 0
        for (k in i..j) row.getChildAt(k)?.let { t = minOf(t, it.top); b = maxOf(b, it.bottom) }
        val other = if (top) shownBottomIds else shownTopIds
        val split = g.members.any { it in other }
        val openLeft = top && split
        val openRight = !top && split
        val d = GradientDrawable()
        d.setColor(Color.TRANSPARENT)
        d.setStroke(stroke, g.color)
        val l = if (openLeft) 0f else r
        val rr = if (openRight) 0f else r
        d.cornerRadii = floatArrayOf(l, l, rr, rr, rr, rr, l, l)
        val half = stroke / 2
        d.setBounds(
            if (openLeft) first.left - dp(6) else first.left + half,
            t + half,
            if (openRight) last.right + dp(6) else last.right - half,
            b - half
        )
        row.overlay.add(d)
        i = j + 1
    }
}

// Rychle menu karty z listy (dlouhy stisk na dlazdici)
internal fun MainActivity.showTabQuickMenu(tab: BrowserTab) {
    val title = tab.title.ifBlank { tab.url.ifBlank { "Karta" } }
    showStyledDialog(title = title, negativeText = "Zrušit") { dismiss ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        fun item(text: String, onClick: () -> Unit) {
            val b = styledButton(text, false)
            b.setOnClickListener { dismiss(); onClick() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            box.addView(b, lp)
        }
        item(if (tab.pinned) "Odepnout" else "Připnout") {
            if (!tab.pinned) tab.pinnedTop = tab.id in shownTopIds && tab.id !in shownBottomIds   // zustane v liste, kde je ted
            tab.pinned = !tab.pinned
            updateTabStrips()
            saveTabs()
            AppToast.show(this, if (tab.pinned) "Karta připnuta" else "Karta odepnuta")
        }
        item("Duplikovat") {
            val u = tab.pendingUrl ?: tab.liveUrl().ifBlank { tab.url }
            if (!isWebUrl(u)) { AppToast.show(this, "Nelze duplikovat: karta nemá adresu"); return@item }
            // nova karta se otevre na pozadi hned za puvodni
            val copy = createTab(u, activate = false, incognito = tab.incognito, fly = false)
            if (tab.desktop) applyDesktop(copy, true)
            val at = tabs.list.indexOfFirst { it.id == tab.id }
            if (at >= 0) { tabs.list.remove(copy); tabs.list.add(at + 1, copy) }
            updateTabStrips()
            AppToast.show(this, "Karta duplikována")
        }
        item("Přesunout do složky") { showMoveToFolder(tab) }
        item("Zavřít kartu") {
            if (!tab.pinned) closeTab(tab.id)
            else showStyledDialog(title = "Zavřít připnutou kartu?", positiveText = "Zavřít", onPositive = { closeTab(tab.id); true }) { _ ->
                val tv = TextView(this)
                tv.text = getString(R.string.pinned_close_confirm)
                tv.setTextColor("#CCCCCC".toColorInt())
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                tv
            }
        }
        box
    }
}


// Dlazdice v liste: klepnuti = prepnout, dvojklik = rychle menu, drzeni = presunout (zmena poradi karet)
@SuppressLint("ClickableViewAccessibility")
private fun MainActivity.attachTileTouch(tile: View, tab: BrowserTab, list: List<BrowserTab>, row: LinearLayout, sv: HorizontalScrollView) {
    val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
    val handler = Handler(Looper.getMainLooper())
    var downX = 0f
    var downY = 0f
    var moved = false
    var dragging = false
    val startDrag = Runnable {
        dragging = true
        tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        tile.elevation = dp(10).toFloat()
        tile.scaleX = 1.12f; tile.scaleY = 1.12f
        sv.requestDisallowInterceptTouchEvent(true)   // lista se behem tahu neposouva
    }
    // nahled, kam se karta vlozi: kopie dlazdice v 50% pruhlednosti na miste cile
    var ghost: android.graphics.drawable.Drawable? = null
    fun clearGhost() { ghost?.let { row.overlay.remove(it) }; ghost = null }
    fun targetAt(v: View): Pair<Int, View>? {
        val cx = v.left + v.width / 2f + v.translationX
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (c !== v && cx >= c.left && cx <= c.right) return i to c
        }
        return null
    }
    // dlazdice mezi puvodnim mistem a cilem se posunou o jedno misto (je videt, s cim se karta zameni)
    fun shiftOthers(v: View, ti: Int?) {
        val from = row.indexOfChild(v)
        for (j in 0 until row.childCount) {
            val c = row.getChildAt(j)
            if (c === v) continue
            val tx = when {
                ti != null && from < ti && j in (from + 1)..ti -> row.getChildAt(j - 1).left - c.left
                ti != null && from > ti && j in ti until from -> row.getChildAt(j + 1).left - c.left
                else -> 0
            }.toFloat()
            if (c.tag != tx) { c.tag = tx; c.animate().translationX(tx).setDuration(140).start() }
        }
    }
    fun showGhost(v: View) {
        val t = targetAt(v)
        shiftOthers(v, t?.first)
        if (t == null) { clearGhost(); return }
        val g = ghost ?: (v.background?.constantState?.newDrawable()?.mutate() ?: return).also {
            it.alpha = 128
            ghost = it
            row.overlay.add(it)
        }
        val c = t.second
        g.setBounds(c.left, v.top, c.left + v.width, v.bottom)
    }
    fun resetVisual() {
        clearGhost()
        for (j in 0 until row.childCount) row.getChildAt(j).let { it.animate().cancel(); it.translationX = 0f; it.tag = null }
        tile.translationX = 0f; tile.scaleX = 1f; tile.scaleY = 1f; tile.elevation = 0f
    }
    tile.setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY; moved = false; dragging = false
                handler.postDelayed(startDrag, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) { v.translationX = ev.rawX - downX; showGhost(v) }
                else if (!moved && (kotlin.math.abs(ev.rawX - downX) > slop || kotlin.math.abs(ev.rawY - downY) > slop)) {
                    moved = true
                    handler.removeCallbacks(startDrag)   // je to posun listy, ne drzeni
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(startDrag)
                if (dragging) {
                    // kam dlazdici pustil: na dlazdici jine karty = karta se presune na jeji misto v poradi
                    val target: BrowserTab? = targetAt(v)?.let { list.getOrNull(it.first) }
                    dragging = false
                    resetVisual()
                    if (target != null) {
                        // skupina: vytazeni ze skupiny jinam = karta opravdu skupinu opusti (i v prehledu karet);
                        // pusteni na kartu jine skupiny = karta se do ni prida na misto cile
                        val dg = groupOf(tab.id)
                        val tg = groupOf(target.id)
                        if (dg !== tg) {
                            tabManager.removeFromGroups(listOf(tab.id))
                            if (tg != null) tg.members.add(tg.members.indexOf(target.id).coerceAtLeast(0), tab.id)
                            pruneGroups()
                        } else if (dg != null) {
                            tabManager.moveInGroup(-dg.id, tab.id, target.id)
                        }
                        val from = tabs.list.indexOfFirst { it.id == tab.id }
                        val to = tabs.list.indexOfFirst { it.id == target.id }
                        if (from >= 0 && to >= 0 && from != to) tabs.list.add(to, tabs.list.removeAt(from))
                        saveTabs()
                        updateTabStrips()
                        if (tabsView.isVisible) refreshTiles()
                    }
                } else if (!moved) {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (lastTapId == tab.id && now - lastTapAt < 350) {
                        lastTapId = -1L
                        showTabQuickMenu(tab)   // dvojklik
                    } else {
                        lastTapId = tab.id; lastTapAt = now
                        if (tab.id != tabs.activeId) switchTo(tab)
                    }
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(startDrag)
                if (dragging) { dragging = false; resetVisual() }
                true
            }
            else -> true
        }
    }
}

// Presun karty do existujici slozky (skupiny karet) stejneho rezimu (bezne / anonymni)
private fun MainActivity.showMoveToFolder(tab: BrowserTab) {
    val options = groups.filter { g ->
        tab.id !in g.members && tabs.list.firstOrNull { it.id == g.members.firstOrNull() }?.incognito == tab.incognito
    }
    showStyledDialog(title = "Přesunout do složky", negativeText = "Zrušit") { dismiss ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        if (options.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.no_other_folder)
            tv.setTextColor("#CCCCCC".toColorInt())
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            box.addView(tv)
        }
        for (g in options) {
            val label = "● " + g.name.ifBlank { "Složka" } + " (" + g.members.size + ")"
            val sp = android.text.SpannableString(label)
            sp.setSpan(android.text.style.ForegroundColorSpan(g.color), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val b = styledButton(label, false)
            b.text = sp
            b.setOnClickListener {
                dismiss()
                tabManager.removeFromGroups(listOf(tab.id))
                g.members.add(tab.id)
                tabManager.prune(tabs.list.map { it.id }.toSet())   // zanikla puvodni slozka (mene nez 2 karty)
                saveTabs()
                updateTabStrips()
                AppToast.show(this, "Karta přesunuta do složky")
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            box.addView(b, lp)
        }
        box
    }
}


// Smazani karty pretazene na "Smazat kartu": zavre se hned a toast nabidne "Vratit zpet" (karta se otevre znovu na stejnem miste)
internal fun MainActivity.deleteCardWithUndo(id: Long) {
    val tab = tabs.list.firstOrNull { it.id == id } ?: return
    val idx = tabs.list.indexOf(tab)
    val url = tab.pendingUrl ?: tab.url.ifBlank { tab.liveUrl() }
    val incog = tab.incognito
    val desktop = tab.desktop
    val pinned = tab.pinned
    closeTabNow(id)
    refreshTiles()
    AppToast.showAction(this, "Karta smazána", "Vrátit zpět") {
        val back = createTab(if (isWebUrl(url)) url else null, activate = false, incognito = incog, fly = false)
        if (desktop) applyDesktop(back, true)
        back.pinned = pinned
        tabs.list.remove(back)
        tabs.list.add(idx.coerceIn(0, tabs.list.size), back)
        updateTabStrips()
        saveTabs()
        if (tabsView.isVisible) refreshTiles()
        AppToast.show(this, "Karta vrácena")
    }
}


// Skupiny -> "+ Nova skupina": vyber aspon 2 beznych karet, ktere jeste nejsou ve skupine
internal fun MainActivity.showNewGroupDialog() {
    val free = tabs.list.filter { !it.incognito && groupOf(it.id) == null }
    if (free.size < 2) {
        AppToast.show(this, "Na novou skupinu potřebuješ aspoň 2 karty mimo skupiny", true, AppToast.Kind.INFO)
        return
    }
    val chosen = LinkedHashSet<Long>()
    showStyledDialog(title = "Nová skupina", positiveText = "Vytvořit", onPositive = {
        if (chosen.size < 2) {
            AppToast.show(this, "Vyber aspoň 2 karty", false, AppToast.Kind.INFO)
            false
        } else {
            val g = tabManager.newGroup(tabs.list.filter { it.id in chosen }.map { it.id }.toMutableList())
            g.color = TabColors.random()
            g.name = "Skupina " + groups.size
            softBuzz()
            refreshTiles(); updateTabStrips(); saveTabs()
            AppToast.show(this, "Skupina vytvořena")
            true
        }
    }) { _ ->
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        for (t in free) {
            val row = TextView(this)
            fun paint() {
                val on = t.id in chosen
                row.text = (if (on) "☑  " else "☐  ") + t.title.ifBlank { t.url.ifBlank { "Nová karta" } }
                val bg = GradientDrawable()
                bg.setColor((if (on) "#14382A" else "#2A2A2A").toColorInt())
                bg.cornerRadius = dp(14).toFloat()
                if (on) bg.setStroke(dp(2), "#00B055".toColorInt())
                row.background = bg
            }
            row.setTextColor(Color.WHITE)
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            row.maxLines = 1
            row.ellipsize = android.text.TextUtils.TruncateAt.END
            row.setPadding(dp(14), dp(12), dp(14), dp(12))
            paint()
            row.setOnClickListener { if (!chosen.remove(t.id)) chosen.add(t.id); paint() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            list.addView(row, lp)
        }
        val scroll = ScrollView(this)
        scroll.addView(list)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(dp(52) * free.size, dp(360)))
        scroll
    }
}

// Tazeni prstem doleva / doprava po hornim nebo spodnim textovem poli = prepnuti na vedlejsi kartu
private class FieldSwipe { var x = 0f; var y = 0f; var done = false }
private val fieldSwipes = HashMap<Int, FieldSwipe>()

/** Vraci true, kdyz dotek patri posunu mezi kartami (pole ho pak nema zpracovat). */
internal fun MainActivity.handleFieldSwipe(v: View, ev: MotionEvent): Boolean {
    if (v.hasFocus()) return false   // v poli se pise: tah = vyber textu
    val st = fieldSwipes.getOrPut(v.id) { FieldSwipe() }
    when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> { st.x = ev.rawX; st.y = ev.rawY; st.done = false }
        MotionEvent.ACTION_MOVE -> if (!st.done) {
            val dx = ev.rawX - st.x
            val dy = ev.rawY - st.y
            if (kotlin.math.abs(dx) > dp(48) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2) {
                st.done = true
                v.parent?.requestDisallowInterceptTouchEvent(true)
                // pole dostane zruseni doteku (zadne klepnuti, zadny focus)
                val c = MotionEvent.obtain(ev)
                c.action = MotionEvent.ACTION_CANCEL
                v.onTouchEvent(c)
                c.recycle()
                swipeTab(if (dx < 0) 1 else -1)
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (st.done) { st.done = false; return true }
    }
    return st.done
}

// Prepne na kartu vlevo (-1) / vpravo (+1) v poradi listy karet
internal fun MainActivity.swipeTab(dir: Int) {
    val cur = tabs.active() ?: return
    val list = buildItems(cur.incognito).flatMap { it.second }
    val idx = list.indexOfFirst { it.id == cur.id }
    val next = list.getOrNull(idx + dir)
    if (idx < 0 || next == null) {
        softBuzz(30)
        AppToast.show(this, if (dir > 0) "Vpravo už žádná karta není" else "Vlevo už žádná karta není", false, AppToast.Kind.INFO)
        return
    }
    switchTo(next)
    if (AppSettings.animationsOn.get()) {
        // nova karta prijede ze strany, kam se tahlo
        webContainer.translationX = dir * dp(70).toFloat()
        webContainer.alpha = 0.4f
        webContainer.animate().translationX(0f).alpha(1f).setDuration(200).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupFieldSwipe() {
    etSearchMain.setOnTouchListener { v, ev -> handleFieldSwipe(v, ev) }
}
