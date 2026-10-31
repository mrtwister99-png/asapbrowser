package com.example.actimelteambrowser.data

/** Jeden záznam návštěvy stránky (jedno otevření = jeden řádek). */
data class HistoryVisit(
    val id: Long,
    val url: String,
    val title: String,
    val visitedAt: Long // System.currentTimeMillis()
)

/** Jedna uložená stránka v konkrétní složce. */
data class Bookmark(
    val id: Long,
    val folderId: Long,
    val url: String,
    val title: String,
    val addedAt: Long,
    val sortOrder: Int = 0     // poradi ve slozce (mensi = vys)
)

/** Složka záložek. "Oblíbené" a "Další" jsou vestavěné (isBuiltIn). */
data class BookmarkFolder(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
    val parentId: Long = 0     // 0 = slozka nejvyssi urovne, jinak podslozka jine slozky
)

/** Výsledek dotazu "nejpoužívanější stránky". */
data class MostVisitedSite(
    val url: String,
    val title: String,
    val visitCount: Int
)

data class FolderWithCount(val folder: BookmarkFolder, val count: Int)


// ---------------------------------------------------------------- STAHOVANI

/** Jedno stahovani pro zobrazeni (z DownloadManageru). status = DownloadManager.STATUS_* */
data class DlItem(
    val id: Long, val name: String, val status: Int,
    val done: Long, val total: Long, val mime: String?
)

/**
 * Seznam stahovani appky: ID ze systemoveho DownloadManageru si pamatujeme sami (JSON v nastaveni),
 * stav a velikosti se pokazde dotazuji ze systemu. VOLAT Z POZADI (query).
 */
object DownloadsStore {
    private fun load(): MutableList<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        try {
            val arr = org.json.JSONArray(com.example.actimelteambrowser.core.settings.AppSettings.downloadList.get().ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(o.getLong("id") to o.optString("name"))
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun save(list: List<Pair<Long, String>>) {
        val arr = org.json.JSONArray()
        for ((id, name) in list.takeLast(100)) arr.put(org.json.JSONObject().put("id", id).put("name", name))
        com.example.actimelteambrowser.core.settings.AppSettings.downloadList.set(arr.toString())
    }

    fun track(context: android.content.Context, id: Long, name: String) {
        com.example.actimelteambrowser.core.settings.AppSettings.init(context)
        val l = load(); l.add(id to name); save(l)
    }

    fun forget(context: android.content.Context, id: Long) {
        com.example.actimelteambrowser.core.settings.AppSettings.init(context)
        save(load().filter { it.first != id })
    }

    /** Stahovani od nejnovejsiho. Zaznamy, ktere uz system nezna (smazane jinde), se z nasi evidence odstrani. */
    fun query(context: android.content.Context): List<DlItem> {
        com.example.actimelteambrowser.core.settings.AppSettings.init(context)
        val tracked = load()
        if (tracked.isEmpty()) return emptyList()
        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val byId = HashMap<Long, DlItem>()
        try {
            val q = android.app.DownloadManager.Query().setFilterById(*tracked.map { it.first }.toLongArray())
            dm.query(q).use { c ->
                val iId = c.getColumnIndex(android.app.DownloadManager.COLUMN_ID)
                val iSt = c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                val iDone = c.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val iTot = c.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val iMime = c.getColumnIndex(android.app.DownloadManager.COLUMN_MEDIA_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val name = tracked.firstOrNull { it.first == id }?.second ?: ""
                    byId[id] = DlItem(id, name, c.getInt(iSt), c.getLong(iDone), c.getLong(iTot), c.getString(iMime))
                }
            }
        } catch (e: Exception) {
        }
        val known = tracked.filter { byId.containsKey(it.first) }
        if (known.size != tracked.size) save(known)
        return known.map { byId.getValue(it.first) }.reversed()
    }
}
