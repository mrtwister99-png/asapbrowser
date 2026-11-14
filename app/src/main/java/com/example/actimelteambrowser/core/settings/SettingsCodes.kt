package com.example.actimelteambrowser.core.settings

import com.example.actimelteambrowser.noteError
import android.content.Context
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.data.BookmarkFolder
import com.example.actimelteambrowser.data.BrowserDb
import com.example.actimelteambrowser.data.HistoryVisit
import com.example.actimelteambrowser.core.vault.VaultManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kratky textovy export / import: kazda vec ma kod, jedna vec = jeden radek.
 *   [fu]  legenda funkci tlacitek (kod=klic): li = lista, ge = gesta, 00 = zadna, 001... = ostatni
 *   [tl]  tlacitka 1-12:  a = klepnuti, b = drzeni, c = zasunuti v %, d = aktivni (1/0)
 *   [nas] ostatni nastaveni  klic=hodnota (ano/ne = 1/0)
 *   [za]  zalozky: f = slozka (id|vestavena|poradi|rodic|nazev), z = zalozka (slozka|adresa|pridano|poradi|nazev)
 *   [ka]  otevrene karty: aktivni + radky adresa|nazev
 *   \[hi]  historie: cas|adresa|nazev
 *   [va]  zasifrovany trezor hesel (salt, zasifrovany klic, data) - bez 12 slov necitelne; obnovuje se zvlast po zadani slov
 * Nahledy karet a stazene seznamy reklam se neexportuji (stahnou se / vytvori znovu).
 * VOLAT Z POZADI VLAKNA.
 */
object SettingsCodes {

    private const val HEAD = "# ASAP Browser nastaveni v1"
    private val SPECIAL = listOf(ButtonFunction.NONE, ButtonFunction.LEFT_BAR, ButtonFunction.GESTURES)

    private fun code(fn: ButtonFunction): String = when (fn) {
        ButtonFunction.NONE -> "00"
        ButtonFunction.LEFT_BAR -> "li"
        ButtonFunction.GESTURES -> "ge"
        else -> "%03d".format(ButtonFunction.entries.filter { it !in SPECIAL }.indexOf(fn) + 1)
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(if (s[i + 1] == 'n') '\n' else s[i + 1])
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun oneLine(s: String) = s.replace("\n", " ").replace("\r", "")

    fun looksLikeCodes(text: String) = text.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("# ASAP Browser nastaveni")

    /** parts: nas = tlacitka + nastaveni, za = zalozky, ka = otevrene karty, hi = historie, va = zasifrovany trezor hesel (vychozi vse krome va) */
    fun export(context: Context, parts: Set<String> = setOf("nas", "za", "ka", "hi")): String {
        AppSettings.init(context)
        val db = BrowserDb.get(context)
        val sb = StringBuilder()
        sb.append(HEAD).append('\n')
        sb.append("# tlacitka: a=klepnuti b=drzeni c=zasunuti v % d=aktivni (1/0)\n")

        if ("nas" in parts) {
        sb.append("[fu]\n")
        for (fn in ButtonFunction.entries) sb.append(code(fn)).append('=').append(fn.key).append('\n')

        sb.append("[tl]\n")
        for (n in 1..12) {
            val a = ButtonFunction.fromKey(AppSettings.buttonTap[n - 1].get())
            val b = ButtonFunction.fromKey(AppSettings.buttonLong[n - 1].get())
            sb.append(n).append(" a=").append(code(a)).append(" b=").append(code(b))
                .append(" c=").append(AppSettings.buttonRetract[n - 1].get())
                .append(" d=").append(if (AppSettings.buttonEnabled[n - 1].get()) 1 else 0).append('\n')
        }

        sb.append("[nas]\n")
        for (s in AppSettings.allSettings) {
            if (s.key.startsWith("btn")) continue   // tlacitka jsou v [tl]
            val text = when (val v = s.toJsonValue()) {
                is Boolean -> if (v) "1" else "0"
                is String -> esc(v)
                else -> v.toString()
            }
            sb.append(s.key).append('=').append(text).append('\n')
        }
        // kontrolni kod k poctu reklam a Ctvereckum: rucni prepsani v souboru se pri importu pozna
        sb.append(SIG_KEY).append('=').append(rewardSig(AppSettings.totalBlocked.get(), AppSettings.squareStyleOwned.get(), AppSettings.starStyleOwned.get(), AppSettings.gestureColorsOwned.get())).append('\n')
        }

        if ("za" in parts) {
        sb.append("[za]\n")
        for (f in db.allFolders()) {
            sb.append("f ").append(f.id).append('|').append(if (f.isBuiltIn) 1 else 0).append('|')
                .append(f.sortOrder).append('|').append(f.parentId).append('|').append(oneLine(f.name)).append('\n')
        }
        for (b in db.allBookmarks()) {
            sb.append("z ").append(b.folderId).append('|').append(oneLine(b.url)).append('|')
                .append(b.addedAt).append('|').append(b.sortOrder).append('|').append(oneLine(b.title)).append('\n')
        }

        }

        if ("ka" in parts) {
        sb.append("[ka]\n")
        try {
            val o = JSONObject(AppSettings.savedTabs.get())
            sb.append("active=").append(o.optInt("active")).append('\n')
            val arr = o.optJSONArray("tabs")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                sb.append(oneLine(e.optString("url"))).append('|').append(oneLine(e.optString("title"))).append('\n')
            }
            // doplnky (starsi verze appky je preskoci): m poradi|pripnuta|nahore|barva, g barva|poradi karet|nazev skupiny
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                sb.append("m ").append(i).append('|').append(if (e.optBoolean("pin")) 1 else 0).append('|')
                    .append(if (e.optBoolean("pt")) 1 else 0).append('|').append(e.optInt("col", 0)).append('\n')
            }
            val garr = o.optJSONArray("groups")
            if (garr != null) for (i in 0 until garr.length()) {
                val g = garr.optJSONObject(i) ?: continue
                val m = g.optJSONArray("m") ?: continue
                if (m.length() < 2) continue   // prazdna / jednokaretni skupina se neexportuje
                val idx = (0 until m.length()).joinToString(",") { m.optInt(it).toString() }
                sb.append("g ").append(g.optInt("c")).append('|').append(idx).append('|').append(oneLine(g.optString("n"))).append('\n')
            }
        } catch (e: Exception) {
        noteError(e)
        }
        }

        if ("hi" in parts) {
        sb.append("[hi]\n")
        for (h in db.allHistory()) {
            sb.append(h.visitedAt).append('|').append(oneLine(h.url)).append('|').append(oneLine(h.title)).append('\n')
        }
        }

        if ("va" in parts) VaultManager.exportSection(context)?.let { sb.append(it) }
        return sb.toString()
    }

    /** Ktere casti soubor obsahuje (nas / za / ka / hi) - pro vyber pri importu. */
    fun sections(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (!(l.startsWith("[") && l.endsWith("]"))) continue
            when (l.substring(1, l.length - 1)) {
                "tl", "nas", "fu" -> out.add("nas")
                "za" -> out.add("za")
                "ka" -> out.add("ka")
                "hi" -> out.add("hi")
                "va" -> out.add("va")
            }
        }
        return out
    }

    /** parts = ktere casti se maji nacist (vychozi vse, co je v souboru) */
    fun import(context: Context, text: String, parts: Set<String> = setOf("nas", "za", "ka", "hi"), merge: Set<String> = emptySet()): String {
        AppSettings.init(context)
        if (!looksLikeCodes(text)) throw IllegalArgumentException("Neplatný soubor")
        val sec = LinkedHashMap<String, MutableList<String>>()
        var cur = ""
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                cur = line.substring(1, line.length - 1); sec.getOrPut(cur) { ArrayList() }; continue
            }
            sec.getOrPut(cur) { ArrayList() }.add(line)
        }

        // nevybrane casti se zahodi (zustanou v aplikaci beze zmeny)
        if ("nas" !in parts) { sec.remove("fu"); sec.remove("tl"); sec.remove("nas") }
        if ("za" !in parts) sec.remove("za")
        if ("ka" !in parts) sec.remove("ka")
        if ("hi" !in parts) sec.remove("hi")

        // legenda kodu funkci (kdyz chybi, pouzije se aktualni)
        val legend = HashMap<String, String>()
        for (fn in ButtonFunction.entries) legend[code(fn)] = fn.key
        sec["fu"]?.forEach { l ->
            val i = l.indexOf('=')
            if (i > 0) legend[l.substring(0, i)] = l.substring(i + 1)
        }
        fun fnKey(c: String): String? = legend[c]?.takeIf { k -> ButtonFunction.entries.any { it.key == k } }

        // 1) priprava zalozek a historie (nic se nemeni, dokud neni vse v poradku)
        val folders = ArrayList<BookmarkFolder>()
        val bookmarks = ArrayList<Bookmark>()
        val hasBookmarks = sec.containsKey("za")
        sec["za"]?.let { lines ->
            for (l in lines) {
                if (l.startsWith("f ")) {
                    val p = l.substring(2).split('|', limit = 5)
                    if (p.size < 5 || p[4].isBlank()) continue
                    val id = p[0].toLongOrNull() ?: continue
                    folders.add(BookmarkFolder(id, p[4], p[1] == "1", p[2].toIntOrNull() ?: 0, p[3].toLongOrNull() ?: 0L))
                }
            }
            val ids = folders.map { it.id }.toSet()
            val parentOf = folders.associate { it.id to it.parentId }
            for (i in folders.indices) {
                val f = folders[i]
                var c = f.parentId
                var steps = 0
                var bad = false
                while (c != 0L) {
                    if (c !in ids || c == f.id || steps > 50) { bad = true; break }
                    c = parentOf[c] ?: 0L
                    steps++
                }
                if (bad) folders[i] = f.copy(parentId = 0)
            }
            for (l in lines) {
                if (l.startsWith("z ")) {
                    val p = l.substring(2).split('|', limit = 5)
                    if (p.size < 5) continue
                    val fid = p[0].toLongOrNull() ?: continue
                    if (!isWebUrl(p[1]) || fid !in ids) continue
                    bookmarks.add(Bookmark(0, fid, p[1], p[4], p[2].toLongOrNull() ?: 0L, p[3].toIntOrNull() ?: 0))
                }
            }
        }
        val hasHistory = sec.containsKey("hi")
        val history = ArrayList<HistoryVisit>()
        sec["hi"]?.forEach { l ->
            val p = l.split('|', limit = 3)
            if (p.size >= 3 && isWebUrl(p[1])) history.add(HistoryVisit(0, p[1], p[2], p[0].toLongOrNull() ?: 0L))
        }
        val tabsArr = JSONArray()
        var tabsActive = 0
        val tabMeta = ArrayList<List<String>>()
        val groupsArr = JSONArray()
        sec["ka"]?.forEach { l ->
            if (l.startsWith("active=")) tabsActive = l.substring(7).toIntOrNull() ?: 0
            else if (l.startsWith("m ")) tabMeta.add(l.substring(2).split('|'))
            else if (l.startsWith("g ")) {
                val p = l.substring(2).split('|', limit = 3)
                val idx = p.getOrNull(1)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                if (idx.size >= 2) groupsArr.put(JSONObject().put("m", JSONArray(idx)).put("c", p[0].toIntOrNull() ?: 0xFFCDA24D.toInt()).put("n", p.getOrNull(2).orEmpty()))
            }
            else {
                val p = l.split('|', limit = 2)
                if (p.isNotEmpty() && isWebUrl(p[0]) && tabsArr.length() < 100)
                    tabsArr.put(JSONObject().put("url", p[0]).put("title", if (p.size > 1) p[1] else ""))
            }
        }

        // pripnuti a barvy karet
        for (m in tabMeta) {
            val e = m.getOrNull(0)?.toIntOrNull()?.let { tabsArr.optJSONObject(it) } ?: continue
            e.put("pin", m.getOrNull(1) == "1").put("pt", m.getOrNull(2) == "1")
            m.getOrNull(3)?.toIntOrNull()?.takeIf { it != 0 }?.let { e.put("col", it) }
        }

        // 2) pouzit
        var count = 0
        sec["tl"]?.forEach { l ->
            val parts = l.trim().split(' ')
            val n = parts[0].toIntOrNull() ?: return@forEach
            if (n !in 1..12) return@forEach
            for (p in parts.drop(1)) {
                val i = p.indexOf('=')
                if (i != 1) continue
                val v = p.substring(2)
                when (p[0]) {
                    'a' -> fnKey(v)?.let { AppSettings.buttonTap[n - 1].set(it); count++ }
                    'b' -> fnKey(v)?.let { AppSettings.buttonLong[n - 1].set(it); count++ }
                    'c' -> v.toIntOrNull()?.let { AppSettings.buttonRetract[n - 1].set(it); count++ }
                    'd' -> { AppSettings.buttonEnabled[n - 1].set(v == "1"); count++ }
                }
            }
        }
        val byKey = AppSettings.allSettings.associateBy { it.key }
        // pocet reklam a Ctverecky se nactou jen s platnym kontrolnim kodem (jinak zustane, co je v telefonu)
        var fileBlocked: Int? = null
        var fileSquare: Boolean? = null
        var fileSig: String? = null
        var fileStar = false
        var fileColors = false
        sec["nas"]?.forEach { l ->
            val i = l.indexOf('=')
            if (i <= 0) return@forEach
            val k = l.substring(0, i)
            val v = l.substring(i + 1)
            when (k) {
                AppSettings.totalBlocked.key -> { fileBlocked = v.toIntOrNull(); return@forEach }
                AppSettings.squareStyleOwned.key -> { fileSquare = v == "1"; return@forEach }
                AppSettings.starStyleOwned.key -> { fileStar = v == "1"; return@forEach }
                AppSettings.gestureColorsOwned.key -> { fileColors = v == "1"; return@forEach }
                SIG_KEY -> { fileSig = v.trim(); return@forEach }
            }
            val s = byKey[k] ?: return@forEach
            val ok = when (s) {
                is AppSettings.BoolSetting -> s.fromJsonValue(v == "1")
                is AppSettings.IntSetting -> v.toIntOrNull()?.let { s.fromJsonValue(it) } ?: false
                is AppSettings.StringSetting -> s.fromJsonValue(unesc(v))
                else -> false
            }
            if (ok) count++
        }
        var rewardNote = ""
        if (fileBlocked != null || fileSquare != null) {
            val b = fileBlocked ?: 0
            val q = fileSquare ?: false
            if (fileSig != null && fileSig == rewardSig(b, q, fileStar, fileColors)) {
                // import pocet nikdy nesnizi a koupene Ctverecky nevezme
                RewardGuard.setBlocked(maxOf(AppSettings.totalBlocked.get(), b))
                RewardGuard.unlock(square = q, star = fileStar, colors = fileColors)
                count += 2
            } else rewardNote = " (počet zablokovaných reklam nebyl načten – soubor byl ručně upraven)"
        }
        AppSettings.fixButtons()
        if (!isWebUrl(AppSettings.homeUrl.get())) AppSettings.homeUrl.reset()

        if (tabsArr.length() > 0) {
            if ("ka" in merge) {
                // pridat k aktualnim: stavajici karty zustanou (a aktivni), importovane se pripoji za ne (max 100)
                val old = try { JSONObject(AppSettings.savedTabs.get()) } catch (e: Exception) { noteError(e); JSONObject() }
                val oldArr = old.optJSONArray("tabs") ?: JSONArray()
                val have = HashSet<String>()
                for (i in 0 until oldArr.length()) have.add(oldArr.optJSONObject(i)?.optString("url").orEmpty())
                val all = JSONArray()
                for (i in 0 until oldArr.length()) all.put(oldArr.get(i))
                for (i in 0 until tabsArr.length()) {
                    val e = tabsArr.getJSONObject(i)
                    if (all.length() < 100 && e.optString("url") !in have) all.put(e)
                }
                AppSettings.savedTabs.set(JSONObject().put("active", old.optInt("active")).put("tabs", all).toString())
            } else {
                AppSettings.savedTabs.set(JSONObject().put("active", tabsActive).put("tabs", tabsArr).put("groups", groupsArr).toString())
            }
            AppSettings.tabsImported.set(true)
        }
        val db = BrowserDb.get(context)
        if (hasBookmarks) {
            if ("za" in merge) {
                // pridat ke stavajicim: slozky se spoji podle nazvu (a nadrazene slozky), nove se vytvori, zalozky se stejnou adresou ve slozce se preskoci
                val allF = ArrayList(db.allFolders())
                val allB = ArrayList(db.allBookmarks())
                val idMap = HashMap<Long, Long>()
                var pending = ArrayList(folders)
                var progress = true
                while (pending.isNotEmpty() && progress) {
                    progress = false
                    val rest = ArrayList<BookmarkFolder>()
                    for (f in pending) {
                        val parent = if (f.parentId == 0L) 0L else idMap[f.parentId]
                        if (parent == null) { rest.add(f); continue }
                        val match = allF.firstOrNull { it.name == f.name && it.parentId == parent }
                        if (match != null) idMap[f.id] = match.id
                        else {
                            val nid = (allF.maxOfOrNull { it.id } ?: 0L) + 1L
                            allF.add(BookmarkFolder(nid, f.name, false, f.sortOrder, parent))
                            idMap[f.id] = nid
                        }
                        progress = true
                    }
                    pending = rest
                }
                val have = allB.map { it.folderId.toString() + "|" + it.url }.toHashSet()
                for (b in bookmarks) {
                    val fid = idMap[b.folderId] ?: continue
                    if (have.add(fid.toString() + "|" + b.url)) allB.add(Bookmark(0, fid, b.url, b.title, b.addedAt, b.sortOrder))
                }
                db.replaceBookmarks(allF, allB)
            } else db.replaceBookmarks(folders, bookmarks)
        }
        if (hasHistory) {
            if ("hi" in merge) {
                // pridat ke stavajici: jen zaznamy, ktere tam jeste nejsou (stejna adresa + cas)
                val existing = db.allHistory()
                val have = existing.map { it.url + "|" + it.visitedAt }.toHashSet()
                db.replaceHistory(existing + history.filter { (it.url + "|" + it.visitedAt) !in have })
            } else db.replaceHistory(history)
        }

        return "Import hotový: $count nastavení" +
            (if (hasBookmarks) ", ${folders.size} složek, ${bookmarks.size} záložek" else "") +
            (if (hasHistory) ", ${history.size} záznamů historie" else "") +
            (if (tabsArr.length() > 0) ", ${tabsArr.length()} karet" else "") + rewardNote
    }

    // ---- kontrolni kod (podpis) poctu reklam a Ctverecku ----
    private const val SIG_KEY = "podpis"

    /** HMAC-SHA256 z hodnot, zkraceny na 24 hex znaku. Kdo zmeni cislo v souboru, nema jak spocitat novy kod. */
    private fun rewardSig(blocked: Int, square: Boolean, star: Boolean = false, colors: Boolean = false): String {
        var msg = "asap|total_blocked=$blocked|square_style_owned=${if (square) 1 else 0}"
        // Hvezdicky a Barvy: pridaji se jen kdyz jsou odemcene (starsi soubory bez nich maji kod dal platny)
        if (star || colors) msg += "|star_style_owned=${if (star) 1 else 0}|gesture_colors_owned=${if (colors) 1 else 0}"
        return RewardGuard.hmac(msg)
    }

    private fun isWebUrl(url: String) = url.startsWith("https://") || url.startsWith("http://")
}
