package com.example.actimelteambrowser.core.adblock

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Aktualizace EasyListu z internetu. Stazena verze se pouzije pri DALSIM spusteni appky
// (Rust engine se da nacist jen jednou za beh).
object ListUpdater {

    private const val MAX_LIST_BYTES = 20 * 1024 * 1024
    private const val MAX_AGE_MS = 4L * 24 * 60 * 60 * 1000   // seznamy se obnovuji cca po 4 dnech

    private val lists = mapOf(
        "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
        "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt"
    )

    // Cte nejdriv stazenou verzi (filesDir), jinak vestaveny soubor z assets
    fun read(context: Context, name: String): String {
        val local = File(context.filesDir, name)
        if (local.exists() && local.length() > 100_000) {
            return runCatching { local.readText() }.getOrElse { readAsset(context, name) }
        }
        return readAsset(context, name)
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    // Volat na pozadi vlakne
    fun updateIfStale(context: Context) {
        for ((name, url) in lists) {
            val target = File(context.filesDir, name)
            if (target.exists() && System.currentTimeMillis() - target.lastModified() < MAX_AGE_MS) continue
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                val text = try {
                    if (conn.responseCode != 200) throw IllegalStateException("HTTP " + conn.responseCode)
                    // nejvys 20 MB (EasyList ma ~2 MB): podvrzeny obri soubor nezahlti pamet
                    String(conn.inputStream.use { com.example.actimelteambrowser.core.readLimited(it, MAX_LIST_BYTES) }, Charsets.UTF_8)
                } finally {
                    conn.disconnect()
                }
                // kontrola, ze je to opravdu seznam a ne chybova stranka
                if (!text.startsWith("[Adblock") || text.length < 100_000) throw IllegalStateException("neplatny seznam")
                val tmp = File(context.filesDir, "$name.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                Log.i("AdBlock", "Seznam $name aktualizovan (" + text.length + " znaku)")
            } catch (e: Exception) {
                Log.w("AdBlock", "Aktualizace $name selhala: " + e.message)
            }
        }
    }
}
