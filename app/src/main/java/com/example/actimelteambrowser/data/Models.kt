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
