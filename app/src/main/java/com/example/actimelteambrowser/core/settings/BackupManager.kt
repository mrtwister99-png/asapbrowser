package com.example.actimelteambrowser.core.settings

import android.content.Context
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.data.BookmarkFolder
import com.example.actimelteambrowser.data.BrowserDb
import com.example.actimelteambrowser.data.HistoryVisit
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export / import VŠEHO do jednoho JSON souboru:
 * nastavení (AppSettings) + složky a záložky + historie.
 * Až přibude další věc (např. VPN, pozice tlačítek), přidá se sem jedna sekce.
 *
 * VOLAT Z POZADÍ VLÁKNA.
 */
object BackupManager {

    private const val APP = "actimelteam"
    private const val VERSION = 1
    private const val PREVIEW_DIR = "tab_previews"
    private const val MAX_PREVIEW_BYTES = 2_000_000
    private val LIST_NAMES = listOf("easylist.txt", "easyprivacy.txt")
    private const val MIN_LIST_CHARS = 100_000
    private const val MAX_LIST_CHARS = 15_000_000

    fun exportJson(context: Context): String {
        AppSettings.init(context)
        val db = BrowserDb.get(context)

        val folders = JSONArray()
        for (f in db.allFolders()) {
            folders.put(
                JSONObject().put("id", f.id).put("name", f.name)
                    .put("builtIn", f.isBuiltIn).put("order", f.sortOrder).put("parent", f.parentId)
            )
        }
        val bookmarks = JSONArray()
        for (b in db.allBookmarks()) {
            bookmarks.put(
                JSONObject().put("folderId", b.folderId).put("url", b.url)
                    .put("title", b.title).put("addedAt", b.addedAt).put("order", b.sortOrder)
            )
        }
        val history = JSONArray()
        for (h in db.allHistory()) {
            history.put(JSONObject().put("url", h.url).put("title", h.title).put("visitedAt", h.visitedAt))
        }

        // otevrene karty (ulozeny stav z posledniho opusteni hlavniho okna)
        val tabsObj = try {
            JSONObject(AppSettings.savedTabs.get())
        } catch (e: Exception) {
            JSONObject().put("active", 0).put("tabs", JSONArray())
        }

        // nahledy karet (JPEG jako text base64) - jen ty, ktere existuji
        val tabsArr = tabsObj.optJSONArray("tabs")
        if (tabsArr != null) {
            val dir = File(context.filesDir, PREVIEW_DIR)
            for (i in 0 until tabsArr.length()) {
                val entry = tabsArr.optJSONObject(i) ?: continue
                val f = File(dir, "p$i.jpg")
                if (f.exists() && f.length() in 1..MAX_PREVIEW_BYTES) {
                    entry.put("preview", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
            }
        }

        // stazene seznamy reklam (jen pokud uz byly stazeny; jinak se pouzije vestaveny)
        val lists = JSONObject()
        for (name in LIST_NAMES) {
            val f = File(context.filesDir, name)
            if (f.exists() && f.length() > MIN_LIST_CHARS) lists.put(name, f.readText())
        }

        val out = JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("settings", AppSettings.exportSettingsObject())
            .put("tabs", tabsObj)
            .put("folders", folders)
            .put("bookmarks", bookmarks)
            .put("history", history)
        if (lists.length() > 0) out.put("adblockLists", lists)
        return out.toString(2)
    }

    /**
     * Načte soubor. Nejdřív se vše zkontroluje a teprve potom se cokoliv změní.
     * Záložky a historie se NAHRADÍ obsahem souboru (jen pokud je v souboru sekce přítomná).
     * Vrací text pro uživatele. Při neplatném souboru vyhodí IllegalArgumentException.
     */
    fun importJson(context: Context, json: String): String {
        AppSettings.init(context)

        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Neplatný soubor")
        }
        if (root.optString("app") != APP) throw IllegalArgumentException("Tohle není soubor z této aplikace")

        // 1) zkontrolovat a připravit data
        val folders = ArrayList<BookmarkFolder>()
        root.optJSONArray("folders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                folders.add(BookmarkFolder(o.optLong("id"), name, o.optBoolean("builtIn"), o.optInt("order"), o.optLong("parent")))
            }
        }
        val folderIds = folders.map { it.id }.toSet()
        // rodic musi existovat a nesmi vzniknout cyklus, jinak se slozka da na nejvyssi uroven
        val parentOf = folders.associate { it.id to it.parentId }
        for (i in folders.indices) {
            val f = folders[i]
            var cur = f.parentId
            var steps = 0
            var bad = false
            while (cur != 0L) {
                if (cur !in folderIds || cur == f.id || steps > 50) { bad = true; break }
                cur = parentOf[cur] ?: 0L
                steps++
            }
            if (bad) folders[i] = f.copy(parentId = 0)
        }

        val bookmarks = ArrayList<Bookmark>()
        root.optJSONArray("bookmarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                val folderId = o.optLong("folderId")
                if (!isWebUrl(url) || folderId !in folderIds) continue
                bookmarks.add(Bookmark(0, folderId, url, o.optString("title"), o.optLong("addedAt"), o.optInt("order")))
            }
        }

        val history = ArrayList<HistoryVisit>()
        root.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (!isWebUrl(url)) continue
                history.add(HistoryVisit(0, url, o.optString("title"), o.optLong("visitedAt")))
            }
        }

        // otevrene karty: jen http/https, max 100
        var tabsCleaned: JSONArray? = null
        var tabsActive = 0
        val tabPreviews = ArrayList<ByteArray?>()   // stejne poradi jako tabsCleaned
        root.optJSONObject("tabs")?.let { o ->
            val cleaned = JSONArray()
            val arr = o.optJSONArray("tabs")
            if (arr != null) {
                for (i in 0 until minOf(arr.length(), 100)) {
                    val e = arr.optJSONObject(i) ?: continue
                    val url = e.optString("url")
                    if (!isWebUrl(url)) continue
                    cleaned.put(JSONObject().put("url", url).put("title", e.optString("title")))
                    tabPreviews.add(decodePreview(e.optString("preview")))
                }
            }
            tabsCleaned = cleaned
            tabsActive = o.optInt("active")
        }

        // stazene seznamy reklam: musi vypadat jako seznam (hlavicka) a mit rozumnou velikost
        val importedLists = HashMap<String, String>()
        root.optJSONObject("adblockLists")?.let { o ->
            for (name in LIST_NAMES) {
                val text = o.optString(name)
                if (text.startsWith("[Adblock") && text.length in (MIN_LIST_CHARS + 1)..MAX_LIST_CHARS) {
                    importedLists[name] = text
                }
            }
        }

        // 2) použít
        val db = BrowserDb.get(context)
        var settingsCount = 0
        root.optJSONObject("settings")?.let { settingsCount = AppSettings.importSettingsObject(it) }
        // domovská stránka musí být web (ne javascript: apod.)
        if (!isWebUrl(AppSettings.homeUrl.get())) AppSettings.homeUrl.reset()

        val tc = tabsCleaned
        if (tc != null && tc.length() > 0) {
            AppSettings.savedTabs.set(JSONObject().put("active", tabsActive).put("tabs", tc).toString())
            AppSettings.tabsImported.set(true)   // hlavni okno karty nacte pri navratu
            // stare nahledy uz nesedi: smazat a zapsat ty ze souboru (podle noveho poradi)
            val dir = File(context.filesDir, PREVIEW_DIR)
            dir.deleteRecursively()
            dir.mkdirs()
            for (i in tabPreviews.indices) {
                val bytes = tabPreviews[i] ?: continue
                try {
                    File(dir, "p$i.jpg").writeBytes(bytes)
                } catch (e: Exception) {
                }
            }
        }

        // seznamy reklam: zapsat bezpecne (nejdriv do .tmp); pouziji se pri dalsim spusteni appky
        var listsCount = 0
        for ((name, text) in importedLists) {
            try {
                val tmp = File(context.filesDir, "$name.tmp")
                tmp.writeText(text)
                val target = File(context.filesDir, name)
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
                listsCount++
            } catch (e: Exception) {
            }
        }

        val replaceBookmarks = root.has("folders") || root.has("bookmarks")
        if (replaceBookmarks) db.replaceBookmarks(folders, bookmarks)
        val replaceHistory = root.has("history")
        if (replaceHistory) db.replaceHistory(history)

        return "Import hotový: " + settingsCount + " nastavení, " +
            (if (replaceBookmarks) folders.size.toString() + " složek, " + bookmarks.size + " záložek" else "záložky beze změny") +
            ", " + (if (replaceHistory) history.size.toString() + " záznamů historie" else "historie beze změny") +
            (if (tc != null && tc.length() > 0) ", " + tc.length() + " karet" else "") +
            (if (listsCount > 0) ", " + listsCount + " seznamů reklam (platí po restartu)" else "")
    }

    // Base64 -> bajty JPEG, jen pokud je to opravdu obrazek rozumne velikosti
    private fun decodePreview(b64: String): ByteArray? {
        if (b64.isEmpty() || b64.length > MAX_PREVIEW_BYTES * 2) return null
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            if (bytes.size > MAX_PREVIEW_BYTES) return null
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) bytes else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")
}
