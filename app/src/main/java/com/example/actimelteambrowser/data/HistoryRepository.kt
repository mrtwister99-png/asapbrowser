package com.example.actimelteambrowser.data

import android.content.Context
import java.util.Calendar

/** Ukládání a mazání historie navštívených stránek. VOLAT Z POZADÍ VLÁKNA. */
class HistoryRepository(context: Context) {
    private val db = BrowserDb.get(context)

    fun recordVisit(url: String, title: String) {
        if (url.isBlank()) return
        db.insertVisit(url, title)
        // promazani: pri prvni navsteve po spusteni a pak kazdych 200 navstev (ne pri kazde, je to zbytecna prace)
        if (visitsSincePrune++ % 200 == 0) runCatching {
            db.pruneHistory(System.currentTimeMillis() - KEEP_DAYS * ONE_DAY_MILLIS, MAX_ROWS)
        }.onFailure { com.example.actimelteambrowser.core.ErrorLog.note(it, "historie") }
    }

    fun deleteEntry(id: Long) = db.deleteVisitById(id)

    fun clearAll() = db.deleteAllHistory()

    /** "Dnes" */
    fun clearToday() = db.deleteHistorySince(dayStart(0))

    /** "Včera" */
    fun clearYesterday() = db.deleteHistoryRange(dayStart(-1), dayStart(0))

    /** "Posledních X dní" (7, 14, ...) */
    fun clearLastDays(days: Int) = db.deleteHistorySince(System.currentTimeMillis() - days * ONE_DAY_MILLIS)

    fun recent(limit: Int = 200): List<HistoryVisit> = db.recentHistory(limit)

    fun search(query: String, limit: Int = 20): List<HistoryVisit> = db.searchHistory(query, limit)

    fun mostVisited(limit: Int = 10): List<MostVisitedSite> = db.mostVisited(limit)

    // půlnoc dneška posunutá o "dayOffset" dní (0 = dnes, -1 = včera)
    private fun dayStart(dayOffset: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val KEEP_DAYS = 90L          // historie se drzi 90 dni (jako Chrome)
        private const val MAX_ROWS = 20_000         // a nejvys 20 000 navstev
        @Volatile private var visitsSincePrune = 0
    }
}
