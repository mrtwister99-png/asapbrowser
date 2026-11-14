package com.example.actimelteambrowser.data

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.quiet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/** Jedno stahovani pro zobrazeni. status = DownloadManager.STATUS_*; uri = soubor ulozeny appkou (jinak null = DownloadManager). */
data class DlItem(
    val id: Long, val name: String, val status: Int,
    val done: Long, val total: Long, val mime: String?,
    val uri: String? = null
)

/**
 * Seznam vsech stazenych souboru appky (JSON v nastaveni):
 *  - bezne stahovani pres systemovy DownloadManager (stav a velikost se ptame systemu),
 *  - soubory, ktere appka ulozila sama (blob: / data: odkazy z webu) nebo presunula do vybrane slozky.
 * Vybrana slozka (AppSettings.downloadFolder): DownloadManager do ni psat neumi, proto stahuje do slozky appky
 * a po dokonceni se soubor presune. VOLAT Z POZADI (query, copy).
 */
object DownloadsStore {
    private class Rec(
        val id: Long, var name: String,
        var uri: String? = null,       // != null: soubor spravuje appka (ne DownloadManager)
        var mime: String? = null,
        var size: Long = 0,
        var status: Int = 0,           // jen u souboru appky
        var moveTo: String? = null,    // DownloadManager: po dokonceni presunout do teto slozky
        val temp: Boolean = false      // z anonymni karty: jen v pameti, do nastaveni se neuklada (po restartu appky zmizi)
    )

    private var tempRecs: List<Rec> = emptyList()   // zaznamy anonymnich stahovani (meni se jen uvnitr edit)

    private val progress = ConcurrentHashMap<Long, Long>()   // soubory appky, ktere se prave zapisuji: bajty
    private val moving = HashSet<Long>()
    private var nextLocal = 0L

    private fun load(): MutableList<Rec> {
        val out = ArrayList<Rec>()
        quiet {
            val arr = JSONArray(AppSettings.downloadList.get().ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Rec(o.getLong("id"), o.optString("name"),
                    o.optString("uri").ifBlank { null }, o.optString("mime").ifBlank { null },
                    o.optLong("size"), o.optInt("st"), o.optString("mv").ifBlank { null }))
            }
        }
        out.addAll(tempRecs)
        return out
    }

    private fun save(list: List<Rec>) {
        tempRecs = list.filter { it.temp }
        val arr = JSONArray()
        for (r in list.filter { !it.temp }.takeLast(200)) {
            val o = JSONObject().put("id", r.id).put("name", r.name)
            r.uri?.let { o.put("uri", it) }
            r.mime?.let { o.put("mime", it) }
            if (r.size > 0) o.put("size", r.size)
            if (r.uri != null) o.put("st", r.status)
            r.moveTo?.let { o.put("mv", it) }
            arr.put(o)
        }
        AppSettings.downloadList.set(arr.toString())
    }

    private inline fun <T> edit(context: Context, block: (MutableList<Rec>) -> T): T = synchronized(this) {
        AppSettings.init(context)
        val l = load()
        val r = block(l)
        save(l)
        r
    }

    /** Stahovani pres DownloadManager; [moveTo] = po dokonceni presunout do vybrane slozky. */
    fun track(context: Context, id: Long, name: String, moveTo: String? = null, temp: Boolean = false) {
        edit(context) { it.add(Rec(id, name, moveTo = moveTo, temp = temp)) }
    }

    fun forget(context: Context, id: Long) {
        progress.remove(id)
        edit(context) { l -> l.removeAll { it.id == id } }
    }

    // ---------------- soubory, ktere uklada appka sama ----------------

    /** Vybrana slozka (null = vychozi Stazene). */
    fun folder(context: Context): Uri? {
        AppSettings.init(context)
        return AppSettings.downloadFolder.get().ifBlank { null }?.let { Uri.parse(it) }
    }

    /** Citelny nazev vybrane slozky. */
    fun folderLabel(context: Context): String {
        val u = folder(context) ?: return "Stažené (výchozí)"
        val doc = runCatching { DocumentsContract.getTreeDocumentId(u) }.getOrNull() ?: return "Vybraná složka"
        val path = doc.substringAfter(':', "")
        return if (path.isBlank()) "Úložiště telefonu" else path
    }

    /** Novy soubor ve vybrane slozce / ve Stazenych. Vraci (uri, proud) nebo null. */
    private fun createTarget(context: Context, name: String, mime: String): Pair<Uri, OutputStream>? {
        val cr = context.contentResolver
        folder(context)?.let { tree ->
            runCatching {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(cr, parent, mime, name) ?: error("no doc")
                return doc to (cr.openOutputStream(doc) ?: error("no stream"))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues()
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
            cv.put(MediaStore.Downloads.MIME_TYPE, mime)
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            cv.put(MediaStore.Downloads.IS_PENDING, 1)
            val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            return uri to (cr.openOutputStream(uri) ?: return null)
        }
        // Android 7-9 bez vybrane slozky: slozka appky, po dokonceni se preda DownloadManageru (je pak videt v systemu)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val f = uniqueFile(dir, name)
        return Uri.fromFile(f) to f.outputStream()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isNotEmpty()) ".$it" else "" }
        while (f.exists()) { f = File(dir, "$base ($n)$ext"); n++ }
        return f
    }

    /** Zapis souboru po kouscich (bez omezeni velikosti). */
    class Writer internal constructor(val id: Long, val uri: Uri, private val out: OutputStream) {
        var written = 0L
            private set
        fun write(bytes: ByteArray) { out.write(bytes); written += bytes.size; progress[id] = written }
        internal fun close() = runCatching { out.close() }
    }

    /** Zacne ukladat soubor appky; zaznam se hned objevi v seznamu (stahuji…). */
    fun begin(context: Context, name: String, mime: String, total: Long, temp: Boolean = false): Writer? {
        val (uri, out) = runCatching { createTarget(context, name, mime) }.getOrNull() ?: return null
        val id = synchronized(this) { nextLocal = minOf(nextLocal - 1, -System.currentTimeMillis()); nextLocal }
        progress[id] = 0L
        edit(context) { it.add(Rec(id, name, uri.toString(), mime, total, DownloadManager.STATUS_RUNNING, temp = temp)) }
        return Writer(id, uri, out)
    }

    /** Konec zapisu: ok = hotovo, jinak se rozepsany soubor smaze. Vraci nazev hotoveho souboru. */
    fun end(context: Context, w: Writer, ok: Boolean): String? {
        w.close()
        progress.remove(w.id)
        val cr = context.contentResolver
        if (!ok) {
            deleteUri(context, w.uri)
            edit(context) { l -> l.firstOrNull { it.id == w.id }?.status = DownloadManager.STATUS_FAILED }
            return null
        }
        if (w.uri.authority == MediaStore.AUTHORITY) quiet {
            val cv = ContentValues(); cv.put(MediaStore.Downloads.IS_PENDING, 0); cr.update(w.uri, cv, null, null)
        }
        return edit(context) { l ->
            val r = l.firstOrNull { it.id == w.id } ?: return@edit null
            r.size = w.written
            r.status = DownloadManager.STATUS_SUCCESSFUL
            // Android 7-9: soubor ze slozky appky se ohlasi systemu (je pak videt i ve Stazenych telefonu)
            if (w.uri.scheme == "file") quiet {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                @Suppress("DEPRECATION")
                val dmId = dm.addCompletedDownload(r.name, r.name, true, r.mime ?: "application/octet-stream", w.uri.path, w.written, false)
                l.remove(r)
                l.add(Rec(dmId, r.name, temp = r.temp))
            }
            r.name
        }
    }

    private fun deleteUri(context: Context, uri: Uri) = quiet {
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).delete() }
            else -> if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
                else context.contentResolver.delete(uri, null, null)
        }
    }

    /** Smaze soubor z telefonu i ze seznamu. */
    fun delete(context: Context, item: DlItem) {
        if (item.uri != null) deleteUri(context, Uri.parse(item.uri))
        else quiet { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(item.id) }
        forget(context, item.id)
    }

    /** Adresa souboru pro otevreni jinou aplikaci. */
    fun openUri(context: Context, item: DlItem): Uri? =
        item.uri?.let { Uri.parse(it) }
            ?: runCatching { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(item.id) }.getOrNull()

    // ---------------- seznam ----------------

    /** Vsechna stahovani od nejnovejsiho. Zaznamy DownloadManageru, ktere system uz nezna, se odstrani. */
    fun query(context: Context): List<DlItem> {
        AppSettings.init(context)
        val tracked = synchronized(this) { load() }
        if (tracked.isEmpty()) return emptyList()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val byId = HashMap<Long, DlItem>()
        val dmIds = tracked.filter { it.uri == null }.map { it.id }
        if (dmIds.isNotEmpty()) quiet {
            dm.query(DownloadManager.Query().setFilterById(*dmIds.toLongArray())).use { c ->
                val iId = c.getColumnIndex(DownloadManager.COLUMN_ID)
                val iSt = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val iDone = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val iTot = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val iMime = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val name = tracked.firstOrNull { it.id == id }?.name ?: ""
                    byId[id] = DlItem(id, name, c.getInt(iSt), c.getLong(iDone), c.getLong(iTot), c.getString(iMime))
                }
            }
        }
        // hotova stahovani urcena do vybrane slozky: presunout (jen jednou, i kdyz se pta vic mist naraz)
        for (r in tracked) {
            val mv = r.moveTo ?: continue
            val d = byId[r.id] ?: continue
            if (d.status != DownloadManager.STATUS_SUCCESSFUL) continue
            val claim = synchronized(moving) { moving.add(r.id) }
            if (claim) try { moveToFolder(context, dm, r, d, Uri.parse(mv)) } finally { synchronized(moving) { moving.remove(r.id) } }
        }
        val fresh = synchronized(this) { load() }
        val gone = fresh.filter { it.uri == null && it.id in dmIds && !byId.containsKey(it.id) }.map { it.id }.toSet()
        if (gone.isNotEmpty()) edit(context) { l -> l.removeAll { it.id in gone } }
        return fresh.filter { it.id !in gone }.map { r ->
            if (r.uri != null) {
                val done = progress[r.id] ?: r.size
                val st = if (r.status == DownloadManager.STATUS_RUNNING && !progress.containsKey(r.id)) DownloadManager.STATUS_FAILED else r.status
                DlItem(r.id, r.name, st, done, r.size, r.mime, r.uri)
            } else byId[r.id] ?: DlItem(r.id, r.name, DownloadManager.STATUS_PENDING, 0, 0, null)
        }.reversed()
    }

    // Soubor stazeny DownloadManagerem do slozky appky se zkopiruje do vybrane slozky a original se smaze
    private fun moveToFolder(context: Context, dm: DownloadManager, r: Rec, d: DlItem, tree: Uri) {
        val cr = context.contentResolver
        val src = runCatching { dm.getUriForDownloadedFile(r.id) }.getOrNull() ?: return
        val mime = d.mime ?: "application/octet-stream"
        val doc = runCatching {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(cr, parent, mime, r.name)
        }.getOrNull()
        if (doc == null) {
            // slozka uz neni dostupna: soubor zustane ve slozce appky
            edit(context) { l -> l.firstOrNull { it.id == r.id }?.moveTo = null }
            return
        }
        val size = runCatching {
            cr.openInputStream(src)!!.use { i -> cr.openOutputStream(doc)!!.use { o -> i.copyTo(o, 256 * 1024) } }
        }.getOrNull()
        if (size == null) { quiet { DocumentsContract.deleteDocument(cr, doc) }; return }
        edit(context) { l ->
            val rec = l.firstOrNull { it.id == r.id } ?: return@edit
            rec.uri = doc.toString(); rec.mime = mime; rec.size = size; rec.status = DownloadManager.STATUS_SUCCESSFUL; rec.moveTo = null
        }
        quiet { dm.remove(r.id) }   // smaze original ve slozce appky
    }
}
