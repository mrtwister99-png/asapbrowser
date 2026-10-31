package com.example.actimelteambrowser.data

import android.content.Context

/** Zalozky ve slozkach (slozky mohou mit podslozky). VOLAT Z POZADI VLAKNA. */
class BookmarksRepository(context: Context) {
    private val db = BrowserDb.get(context)

    /** Slozky primo v dane slozce s poctem polozek (parentId = 0 -> hlavni seznam slozek). */
    fun foldersWithCounts(parentId: Long = 0): List<FolderWithCount> {
        ensureDefaultFolders()
        return db.folders(parentId).map { FolderWithCount(it, db.countInFolder(it.id)) }
    }

    /** Podslozky dane slozky. */
    fun subfolders(parentId: Long): List<BookmarkFolder> = db.folders(parentId)

    /**
     * Vsechny slozky pro vyber ("Oblibene", "Oblibene / Videa"...). Nazev obsahuje cestu.
     * excludeId: tuhle slozku z nabidky vynechat.
     */
    fun foldersForPicker(excludeId: Long = -1): List<BookmarkFolder> {
        ensureDefaultFolders()
        val all = db.allFolders()
        val byId = all.associateBy { it.id }
        fun path(f: BookmarkFolder, depth: Int = 0): String {
            val parent = byId[f.parentId]
            return if (parent == null || f.parentId == 0L || depth > 20) f.name else path(parent, depth + 1) + " / " + f.name
        }
        return all.filter { it.id != excludeId }.map { it.copy(name = path(it)) }
    }

    fun createFolder(name: String, parentId: Long = 0): BookmarkFolder {
        val order = db.folders(parentId).size
        val id = db.insertFolder(name, false, order, parentId)
        return BookmarkFolder(id, name, false, order, parentId)
    }

    fun bookmarksIn(folderId: Long): List<Bookmark> = db.bookmarksInFolder(folderId)

    fun allBookmarks(): List<Bookmark> = db.allBookmarks()

    fun addBookmark(folderId: Long, url: String, title: String) = db.insertBookmark(folderId, url, title)

    fun deleteBookmark(bookmarkId: Long) = db.deleteBookmark(bookmarkId)

    /** Prejmenuje slozku (i vestavenou). */
    fun renameFolder(folder: BookmarkFolder, name: String) {
        if (name.isNotBlank()) db.renameFolder(folder.id, name.trim())
    }

    fun moveBookmark(bookmarkId: Long, folderId: Long) = db.moveBookmark(bookmarkId, folderId)

    /** Ulozi nove poradi zalozek (seznam ID odshora dolu). */
    fun reorderBookmarks(orderedIds: List<Long>) = db.reorderBookmarks(orderedIds)

    /** Pretazeni zalozky na zalozku: vznikne podslozka v dane slozce, obe zalozky se do ni vlozi. */
    fun mergeIntoNewFolder(parentId: Long, firstId: Long, secondId: Long, name: String = "Nová složka"): BookmarkFolder {
        val id = db.createFolderWithBookmarks(parentId, name, listOf(secondId, firstId))
        return BookmarkFolder(id, name, false, 0, parentId)
    }

    /** Smaze slozku (i s podslozkami a zalozkami). Vestavene slozky se mazat nedaji. */
    fun deleteFolder(folder: BookmarkFolder) {
        if (!folder.isBuiltIn) db.deleteFolder(folder.id)
    }

    /** Slozka "Oblibene" (prvni slozka nejvyssi urovne). */
    fun defaultFolderId(): Long {
        ensureDefaultFolders()
        return db.folders(0).first().id
    }

    private fun ensureDefaultFolders() {
        if (db.folderCount() == 0) {
            db.insertFolder("Oblíbené", true, 0)
            db.insertFolder("Další", true, 1)
        }
    }
}
