package com.example.actimelteambrowser

/**
 * Složka v přehledu karet: skupina karet (jejich id) s barvou. V přehledu se ukáže jako jedna dlaždice
 * na místě své první karty. Složka s méně než 2 kartami zaniká (viz TabManager.prune).
 */
class TabGroup(val id: Long, val members: MutableList<Long>, var color: Int = 0xFFCDA24D.toInt(), var name: String = "")

/**
 * Čistá logika složek a pořadí karet: žádný Android, žádný WebView, jen id karet.
 * Samotné karty (BrowserTab s WebView) zůstávají v BrowserTabs a v MainActivity; TabManager pracuje s jejich id
 * a s obecným seznamem karet (T + funkce idOf), takže se dá zkoušet i s obyčejnými čísly.
 *
 * Id dlaždice v přehledu: kladné = karta, záporné = složka (-id složky).
 */
class TabManager {
    val groups = ArrayList<TabGroup>()
    private var nextGroupId = 1L

    fun groupOf(tabId: Long): TabGroup? = groups.firstOrNull { tabId in it.members }

    /** Složka podle id dlaždice z přehledu (záporné číslo). */
    fun groupByTile(tileId: Long): TabGroup? = groups.firstOrNull { it.id == -tileId }

    /** Nová složka z daných karet (volající zajistí, že karty ještě v žádné složce nejsou). */
    fun newGroup(members: MutableList<Long>): TabGroup {
        val g = TabGroup(nextGroupId++, members)
        groups.add(g)
        return g
    }

    fun clear() = groups.clear()

    /** Odebere zaniklé karty a zruší složky, které mají méně než 2 karty. */
    fun prune(aliveIds: Set<Long>) {
        for (g in groups) g.members.retainAll(aliveIds)
        groups.removeAll { it.members.size < 2 }
    }

    /** Vyjme karty ze všech složek (složky s méně než 2 kartami je pak potřeba zrušit přes prune). */
    fun removeFromGroups(ids: Collection<Long>) {
        val set = ids.toSet()
        for (g in groups) g.members.removeAll(set)
    }

    /** Záporná id (složky) rozbalí na id jejich karet; kladná nechá. */
    fun expandIds(ids: List<Long>): Set<Long> {
        val all = HashSet<Long>()
        for (id in ids) {
            if (id < 0) groupByTile(id)?.let { all.addAll(it.members) } else all.add(id)
        }
        return all
    }

    /** Položky přehledu v pořadí karet: jedna karta, nebo složka (na místě své první karty). */
    fun <T> buildItems(modeTabs: List<T>, idOf: (T) -> Long): MutableList<Pair<TabGroup?, List<T>>> {
        val items = ArrayList<Pair<TabGroup?, List<T>>>()
        val seen = HashSet<Long>()
        for (t in modeTabs) {
            val id = idOf(t)
            if (id in seen) continue
            val g = groupOf(id)
            if (g == null) {
                items.add(Pair(null, listOf(t)))
                seen.add(id)
            } else {
                val ms = g.members.mapNotNull { mid -> modeTabs.firstOrNull { idOf(it) == mid } }
                items.add(Pair(g, ms))
                for (x in ms) seen.add(idOf(x))
            }
        }
        return items
    }

    /** Nové pořadí karet po přesunu dlaždice fromId na místo dlaždice toId; null = nic se nemění. */
    fun <T> reorderedFlat(modeTabs: List<T>, idOf: (T) -> Long, fromId: Long, toId: Long): List<T>? {
        val items = buildItems(modeTabs, idOf)
        fun idx(id: Long) = items.indexOfFirst { (g, ms) -> if (id < 0) g?.id == -id else g == null && idOf(ms[0]) == id }
        val from = idx(fromId)
        val to = idx(toId)
        if (from < 0 || to < 0 || from == to) return null
        val moved = items.removeAt(from)
        items.add(to, moved)
        return items.flatMap { it.second }
    }

    /** Přesun karty uvnitř složky; true = pořadí se změnilo. */
    fun moveInGroup(groupTileId: Long, fromId: Long, toId: Long): Boolean {
        val g = groupByTile(groupTileId) ?: return false
        val from = g.members.indexOf(fromId)
        val to = g.members.indexOf(toId)
        if (from < 0 || to < 0 || from == to) return false
        g.members.add(to, g.members.removeAt(from))
        return true
    }

    /**
     * Přetažení karty dragId na kartu (kladné id) nebo složku (záporné id) targetId.
     * Vrací členy výsledné složky, nebo null, když se nic nestalo.
     */
    fun merge(dragId: Long, targetId: Long): MutableList<Long>? {
        if (dragId < 0 || dragId == targetId) return null
        if (groupOf(dragId) != null) return null
        return if (targetId < 0) {
            val g = groupByTile(targetId) ?: return null
            g.members.add(dragId)
            g.members
        } else {
            if (groupOf(targetId) != null) return null
            newGroup(mutableListOf(targetId, dragId)).members
        }
    }
}
