package com.example.actimelteambrowser.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log chyb do souboru jen v telefonu (files/logs/chyby.txt), bez cizi sluzby. Zapisuje spolknute chyby (quiet, noteError)
 * a pady aplikace. Uzivatel ho muze sam poslat z "O aplikaci" -> "Poslat log". Soubor ma nejvys ~2 x 200 kB.
 */
object ErrorLog {
    private const val MAX_BYTES = 200 * 1024
    private const val MAX_PER_RUN = 300            // jedna chyba v cyklu nezahlti soubor
    @Volatile private var file: File? = null
    private var written = 0
    private val lock = Any()
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Volat co nejdriv (ContentProvider.onCreate bezi pred aktivitami); opakovane volani nic nedela. */
    fun init(context: Context) {
        if (file != null) return
        val dir = File(context.applicationContext.filesDir, "logs")
        dir.mkdirs()
        file = File(dir, "chyby.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("PAD (vlakno ${t.name})", e, full = true)
            prev?.uncaughtException(t, e)
        }
    }

    /** Spolknuta chyba: kratky zaznam (typ, zprava, 4 radky zasobniku). */
    fun note(t: Throwable, where: String = "chyba") {
        Log.d("quiet", t.toString())
        write(where, t, full = false)
    }

    private fun write(where: String, t: Throwable, full: Boolean) {
        val f = file ?: return
        synchronized(lock) {
            if (!full && written >= MAX_PER_RUN) return
            written++
            runCatching {
                if (f.length() > MAX_BYTES) {
                    val old = File(f.parentFile, "chyby-starsi.txt")
                    old.delete(); f.renameTo(old)
                }
                val sb = StringBuilder()
                sb.append(time.format(Date())).append("  ").append(where).append(": ").append(t.toString()).append('\n')
                val stack = t.stackTrace
                for (i in 0 until minOf(stack.size, if (full) 40 else 4)) sb.append("    at ").append(stack[i]).append('\n')
                if (full) t.cause?.let { sb.append("  pricina: ").append(it.toString()).append('\n') }
                f.appendText(sb.toString())
            }
        }
    }

    /** Cely log (starsi cast + novejsi) s hlavickou o zarizeni; null = zadna chyba zapsana. */
    fun text(context: Context): String? {
        init(context)
        val f = file ?: return null
        val old = File(f.parentFile, "chyby-starsi.txt")
        val body = synchronized(lock) {
            (if (old.exists()) old.readText() else "") + (if (f.exists()) f.readText() else "")
        }
        if (body.isBlank()) return null
        val ver = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return "ASAP Browser $ver, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n\n$body"
    }

    fun clear() {
        val f = file ?: return
        synchronized(lock) { f.delete(); File(f.parentFile, "chyby-starsi.txt").delete(); written = 0 }
    }
}
