package com.example.actimelteambrowser.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

/**
 * Databáze záložek a historie (čisté SQLite z Androidu, bez Room/KSP).
 * Všechny funkce jsou synchronní - VOLAT Z POZADÍ VLÁKNA (ne z UI vlákna).
 * Stejné tabulky a stejné dotazy jako ve starém projektu.
 */
class BrowserDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "actimelteam.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history_visits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "url TEXT NOT NULL, title TEXT NOT NULL, visitedAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_history_url ON history_visits(url)")
        db.execSQL("CREATE INDEX idx_history_time ON history_visits(visitedAt)")
        db.execSQL(
            "CREATE TABLE bookmark_folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, isBuiltIn INTEGER NOT NULL, sortOrder INTEGER NOT NULL, " +
                "parentId INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE bookmarks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "folderId INTEGER NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, addedAt INTEGER NOT NULL, " +
                "sortOrder INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_bookmarks_folder ON bookmarks(folderId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // verze 2: poradi zalozek a podslozky
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE bookmark_folders ADD COLUMN parentId INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ---------------- HISTORIE ----------------

    fun insertVisit(url: String, title: String) {
        val v = ContentValues()
        v.put("url", url)
        v.put("title", title)
        v.put("visitedAt", System.currentTimeMillis())
        writableDatabase.insert("history_visits", null, v)
    }

    fun deleteVisitById(id: Long) {
        writableDatabase.delete("history_visits", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllHistory() {
        writableDatabase.delete("history_visits", null, null)
    }

    /** Smaže vše OD daného času dál (pro "Dnes" / "Posledních X dní"). */
    fun deleteHistorySince(fromMillis: Long) {
        writableDatabase.delete("history_visits", "visitedAt >= ?", arrayOf(fromMillis.toString()))
    }

    /** Smaže jen konkrétní časové okno (pro "Včera"). */
    fun deleteHistoryRange(fromMillis: Long, toMillis: Long) {
        writableDatabase.delete(
            "history_visits", "visitedAt >= ? AND visitedAt < ?",
            arrayOf(fromMillis.toString(), toMillis.toString())
        )
    }

    fun recentHistory(limit: Int): List<HistoryVisit> =
        readVisits("SELECT id, url, title, visitedAt FROM history_visits ORDER BY visitedAt DESC LIMIT ?", arrayOf(limit.toString()))

    fun allHistory(): List<HistoryVisit> =
        readVisits("SELECT id, url, title, visitedAt FROM history_visits ORDER BY visitedAt DESC", null)

    fun searchHistory(query: String, limit: Int): List<HistoryVisit> {
        val like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return readVisits(
            "SELECT id, url, title, MAX(visitedAt) AS visitedAt FROM history_visits " +
                "WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' " +
                "GROUP BY url ORDER BY visitedAt DESC LIMIT ?",
            arrayOf(like, like, limit.toString())
        )
    }

    fun mostVisited(limit: Int): List<MostVisitedSite> {
        val out = ArrayList<MostVisitedSite>()
        readableDatabase.rawQuery(
            "SELECT url, MAX(title) AS title, COUNT(*) AS visitCount FROM history_visits " +
                "GROUP BY url ORDER BY visitCount DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(MostVisitedSite(c.getString(0), c.getString(1) ?: "", c.getInt(2)))
        }
        return out
    }

    private fun readVisits(sql: String, args: Array<String>?): List<HistoryVisit> {
        val out = ArrayList<HistoryVisit>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(visitFrom(c))
        }
        return out
    }

    private fun visitFrom(c: Cursor) = HistoryVisit(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getLong(3))

    // ---------------- ZÁLOŽKY ----------------

    /** Slozky primo v dane slozce (parentId = 0 -> slozky nejvyssi urovne). */
    fun folders(parentId: Long): List<BookmarkFolder> =
        readFolders("WHERE parentId = ?", arrayOf(parentId.toString()))

    /** Vsechny slozky ve vsech urovnich (export, vyber slozky). */
    fun allFolders(): List<BookmarkFolder> = readFolders("", null)

    private fun readFolders(where: String, args: Array<String>?): List<BookmarkFolder> {
        val out = ArrayList<BookmarkFolder>()
        readableDatabase.rawQuery(
            "SELECT id, name, isBuiltIn, sortOrder, parentId FROM bookmark_folders $where ORDER BY sortOrder ASC, id ASC", args
        ).use { c ->
            while (c.moveToNext()) {
                out.add(BookmarkFolder(c.getLong(0), c.getString(1), c.getInt(2) != 0, c.getInt(3), c.getLong(4)))
            }
        }
        return out
    }

    fun folderCount(): Int = count("SELECT COUNT(*) FROM bookmark_folders", null)

    /** Pocet zalozek + podslozek primo ve slozce. */
    fun countInFolder(folderId: Long): Int =
        count("SELECT COUNT(*) FROM bookmarks WHERE folderId = ?", arrayOf(folderId.toString())) +
            count("SELECT COUNT(*) FROM bookmark_folders WHERE parentId = ?", arrayOf(folderId.toString()))

    private fun count(sql: String, args: Array<String>?): Int =
        readableDatabase.rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun insertFolder(name: String, isBuiltIn: Boolean, sortOrder: Int, parentId: Long = 0): Long {
        val v = ContentValues()
        v.put("name", name)
        v.put("isBuiltIn", if (isBuiltIn) 1 else 0)
        v.put("sortOrder", sortOrder)
        v.put("parentId", parentId)
        return writableDatabase.insert("bookmark_folders", null, v)
    }

    /** Nova zalozka jde na zacatek slozky (nejmensi poradi). */
    fun insertBookmark(folderId: Long, url: String, title: String) {
        val db = writableDatabase
        val top = db.rawQuery(
            "SELECT COALESCE(MIN(sortOrder), 0) FROM bookmarks WHERE folderId = ?", arrayOf(folderId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val v = ContentValues()
        v.put("folderId", folderId)
        v.put("url", url)
        v.put("title", title)
        v.put("addedAt", System.currentTimeMillis())
        v.put("sortOrder", top - 1)
        db.insert("bookmarks", null, v)
    }

    fun bookmarksInFolder(folderId: Long): List<Bookmark> =
        readBookmarks(
            "SELECT id, folderId, url, title, addedAt, sortOrder FROM bookmarks WHERE folderId = ? " +
                "ORDER BY sortOrder ASC, addedAt DESC",
            arrayOf(folderId.toString())
        )

    fun allBookmarks(): List<Bookmark> =
        readBookmarks("SELECT id, folderId, url, title, addedAt, sortOrder FROM bookmarks ORDER BY addedAt DESC", null)

    /** Prejmenuje slozku. */
    fun renameFolder(folderId: Long, name: String) {
        val v = ContentValues()
        v.put("name", name)
        writableDatabase.update("bookmark_folders", v, "id = ?", arrayOf(folderId.toString()))
    }

    /** Presune zalozku do jine slozky (na zacatek). */
    fun moveBookmark(bookmarkId: Long, folderId: Long) {
        val db = writableDatabase
        val top = db.rawQuery(
            "SELECT COALESCE(MIN(sortOrder), 0) FROM bookmarks WHERE folderId = ?", arrayOf(folderId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val v = ContentValues()
        v.put("folderId", folderId)
        v.put("sortOrder", top - 1)
        db.update("bookmarks", v, "id = ?", arrayOf(bookmarkId.toString()))
    }

    /** Ulozi poradi zalozek ve slozce (podle seznamu ID). Jedna transakce. */
    fun reorderBookmarks(orderedIds: List<Long>) {
        val db = writableDatabase
        db.transaction {
            for ((index, id) in orderedIds.withIndex()) {
                val v = ContentValues()
                v.put("sortOrder", index)
                db.update("bookmarks", v, "id = ?", arrayOf(id.toString()))
            }
        }
    }

    /** Vytvori podslozku v dane slozce a vlozi do ni zadane zalozky. Vraci ID nove slozky. */
    fun createFolderWithBookmarks(parentId: Long, name: String, bookmarkIds: List<Long>): Long {
        val db = writableDatabase
        return db.transaction {
            val order = count("SELECT COUNT(*) FROM bookmark_folders WHERE parentId = ?", arrayOf(parentId.toString()))
            val fv = ContentValues()
            fv.put("name", name)
            fv.put("isBuiltIn", 0)
            fv.put("sortOrder", order)
            fv.put("parentId", parentId)
            val newId = db.insertOrThrow("bookmark_folders", null, fv)
            for ((index, id) in bookmarkIds.withIndex()) {
                val v = ContentValues()
                v.put("folderId", newId)
                v.put("sortOrder", index)
                db.update("bookmarks", v, "id = ?", arrayOf(id.toString()))
            }
            newId
        }
    }

    /** Smaze slozku, vsechny jeji podslozky i zalozky v nich (jedna transakce). */
    fun deleteFolder(folderId: Long) {
        val db = writableDatabase
        db.transaction {
            // sebrat slozku a vsechny potomky
            val ids = ArrayList<Long>()
            ids.add(folderId)
            var i = 0
            while (i < ids.size) {
                db.rawQuery("SELECT id FROM bookmark_folders WHERE parentId = ?", arrayOf(ids[i].toString())).use { c ->
                    while (c.moveToNext()) {
                        val child = c.getLong(0)
                        if (!ids.contains(child)) ids.add(child)   // ochrana proti cyklum
                    }
                }
                i++
            }
            for (id in ids) {
                db.delete("bookmarks", "folderId = ?", arrayOf(id.toString()))
                db.delete("bookmark_folders", "id = ?", arrayOf(id.toString()))
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(bookmarkId.toString()))
    }

    private fun readBookmarks(sql: String, args: Array<String>?): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out.add(Bookmark(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3) ?: "", c.getLong(4), c.getInt(5)))
            }
        }
        return out
    }

    // ---------------- IMPORT (nahradí obsah) ----------------

    /** Nahradí všechny složky a záložky. Vše v jedné transakci - při chybě se nic nezmění. */
    fun replaceBookmarks(folders: List<BookmarkFolder>, bookmarks: List<Bookmark>) {
        val db = writableDatabase
        db.transaction {
            db.delete("bookmarks", null, null)
            db.delete("bookmark_folders", null, null)
            for (f in folders) {
                val v = ContentValues()
                v.put("id", f.id)
                v.put("name", f.name)
                v.put("isBuiltIn", if (f.isBuiltIn) 1 else 0)
                v.put("sortOrder", f.sortOrder)
                v.put("parentId", f.parentId)
                db.insertOrThrow("bookmark_folders", null, v)
            }
            for (b in bookmarks) {
                val v = ContentValues()
                v.put("folderId", b.folderId)
                v.put("url", b.url)
                v.put("title", b.title)
                v.put("addedAt", b.addedAt)
                v.put("sortOrder", b.sortOrder)
                db.insertOrThrow("bookmarks", null, v)
            }
        }
    }

    /** Nahradí celou historii. Vše v jedné transakci. */
    fun replaceHistory(visits: List<HistoryVisit>) {
        val db = writableDatabase
        db.transaction {
            db.delete("history_visits", null, null)
            for (h in visits) {
                val v = ContentValues()
                v.put("url", h.url)
                v.put("title", h.title)
                v.put("visitedAt", h.visitedAt)
                db.insertOrThrow("history_visits", null, v)
            }
        }
    }

    companion object {
        @Volatile private var instance: BrowserDb? = null

        fun get(context: Context): BrowserDb =
            instance ?: synchronized(this) {
                instance ?: BrowserDb(context).also { instance = it }
            }
    }
}
