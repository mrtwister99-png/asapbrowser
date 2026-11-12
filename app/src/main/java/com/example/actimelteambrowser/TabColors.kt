package com.example.actimelteambrowser

import androidx.core.graphics.toColorInt

/**
 * Vsechny barvy karet a skupin na jednom miste (barvy slozek, lista karet a dalsi odstiny).
 * Kazda nova karta dostane nahodnou barvu, uzivatel ji zmeni koleckem v prehledu karet (vyber 5 + 5, dalsi stranky po 10).
 */
object TabColors {
    val ALL: List<Int> = listOf(
        // barvy slozek
        "#CDA24D", "#00B055", "#1E6BFF", "#AC0001", "#D9FF00", "#9C27B0", "#FF7A00", "#E91E63",
        // lista karet
        "#FFEB3B", "#E53935", "#2962FF", "#4CE620", "#8D5A33", "#9EA3A8", "#B0156A", "#FF80FF", "#008055", "#FF5A00",
        "#0D47A1", "#1565C0", "#1976D2", "#1E88E5", "#2196F3", "#1B5E20", "#2E7D32", "#388E3C", "#43A047", "#4CAF50",
        // dalsi odstiny
        "#00ff1a", "#00ff84", "#9dfcce", "#024726", "#36baa4", "#00ffd5", "#00ffff", "#94e0e0",
        "#00c8f5", "#1776a6", "#0084ff", "#004cff", "#0800ff", "#5d00ff", "#9900ff", "#dd00ff",
        "#ff00c3", "#ff0059", "#ba5b7c", "#52323d", "#5b4585", "#bfc4e3", "#7d65a6", "#ff69f2",
        "#d4ff00", "#616161", "#2e2e2e", "#c2c2c2", "#969696", "#707070", "#e0d83a",
        "#222b63", "#910000", "#ff0000", "#992c2c", "#3d1212", "#968080", "#d9a275", "#f59445",
        "#ff7300", "#bf5600", "#ff4d00", "#913f1c", "#f0a400", "#fae8c3", "#7a6026", "#abb00e",
        "#f7ff0f", "#8e8f7b", "#c6fa0a", "#779605", "#a0ba43", "#98e802", "#527d02", "#2f7d02",
        "#64d622", "#71de5b"
    ).map { it.toColorInt() }.distinct()

    private val rnd = java.util.Random()

    /** Nahodna barva pro novou kartu / skupinu (jina nez [avoid], kdyz to jde). */
    fun random(avoid: Int = 0): Int {
        var c = ALL[rnd.nextInt(ALL.size)]
        if (c == avoid) c = ALL[(ALL.indexOf(c) + 1) % ALL.size]
        return c
    }

    /** Stranky vyberu: prvni = 10 nahodnych (5 + 5), dalsi po 10 ze zbytku, dohromady vsechny barvy. */
    fun pickerPages(): List<List<Int>> {
        val shuffled = ALL.shuffled(rnd)
        return shuffled.chunked(10)
    }
}
