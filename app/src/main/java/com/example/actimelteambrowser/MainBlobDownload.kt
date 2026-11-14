package com.example.actimelteambrowser

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import com.example.actimelteambrowser.data.DownloadsStore
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Stahovani souboru, ktere nemaji http(s) adresu:
 *  - data: (soubor primo v odkazu),
 *  - blob: (soubor, ktery si stranka vytvorila v pameti - tak stahuje PDF vetsina webu s tlacitkem "Stahnout").
 * Blob umi precist jen stranka, ktera ho vytvorila: appka do ni vlozi kratky skript, ten soubor cte po kouscich
 * (bez omezeni velikosti) a posila je appce pres most AtbDl. Most prijme jen soubor s jednorazovym klicem,
 * ktery appka prave vydala (cizi stranka si tak nemuze sama ukladat soubory do telefonu).
 * Ulozi se do vybrane slozky (Nastaveni → Stahovani) nebo do Stazenych a objevi se v seznamu stahovani.
 */

private const val CHUNK = 512 * 1024

private class BlobJob(val name: String, val mime: String, val issued: Long, val incognito: Boolean) {
    var writer: DownloadsStore.Writer? = null
}

private val blobJobs = HashMap<String, BlobJob>()   // jednorazovy klic -> stahovani

class BlobDownloadBridge(private val act: MainActivity) {
    private fun job(token: String): BlobJob? = synchronized(blobJobs) { blobJobs[token] }

    /** Zacatek: vraci true, kdyz appka soubor prijme. */
    @JavascriptInterface
    fun begin(token: String, type: String, size: Double): Boolean {
        val j = job(token) ?: return false
        if (j.writer != null || System.currentTimeMillis() - j.issued > 120_000) return false
        val mime = type.ifBlank { j.mime }.ifBlank { "application/octet-stream" }
        j.writer = DownloadsStore.begin(act, fixName(j.name, mime), mime, size.toLong(), temp = j.incognito)
        if (j.writer == null) { synchronized(blobJobs) { blobJobs.remove(token) }; act.runOnUiThread { AppToast.show(act, "Soubor se nepodařilo uložit") } }
        else act.runOnUiThread { act.startDownloadPoll() }
        return j.writer != null
    }

    /** Dalsi kousek souboru (base64). */
    @JavascriptInterface
    fun chunk(token: String, b64: String): Boolean {
        val w = job(token)?.writer ?: return false
        return runCatching { w.write(Base64.decode(b64, Base64.DEFAULT)) }.isSuccess
    }

    @JavascriptInterface
    fun end(token: String, ok: Boolean) {
        val j = synchronized(blobJobs) { blobJobs.remove(token) } ?: return
        val w = j.writer
        val name = if (w != null) DownloadsStore.end(act, w, ok) else null
        act.runOnUiThread {
            if (name != null) { act.softBuzz(); AppToast.show(act, "Staženo: $name") }
            else AppToast.show(act, "Soubor se nepodařilo stáhnout")
        }
    }
}

/** Pripona podle skutecneho typu, kdyz chybi nebo je .bin. */
private fun fixName(nameIn: String, mime: String?): String {
    var name = nameIn.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "soubor" }
    val ext = mime?.substringBefore(';')?.trim()?.lowercase()?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (ext != null) {
        val dot = name.lastIndexOf('.')
        val cur = if (dot > 0) name.substring(dot + 1).lowercase() else ""
        if (cur.isEmpty() || cur == "bin") name = (if (dot > 0) name.substring(0, dot) else name) + "." + ext
    }
    return name
}

private fun fileNameFor(url: String, contentDisposition: String?, mime: String?): String =
    fixName(URLUtil.guessFileName(if (url.startsWith("http")) url else "", contentDisposition, mime), mime)

/** data: nebo blob: odkaz. Vraci false, kdyz to neni ani jedno. */
internal fun MainActivity.startSpecialDownload(url: String, contentDisposition: String?, mimeType: String?, wv: WebView?): Boolean {
    when {
        url.startsWith("data:", true) -> {
            val head = url.substringBefore(',', "")
            val mime = head.removePrefix("data:").substringBefore(';').ifBlank { mimeType ?: "application/octet-stream" }
            val name = fileNameFor(url, contentDisposition, mime)
            AppToast.show(this, "Stahuji: $name")
            val incognito = tabs.active()?.incognito == true
            thread(name = "dl-data") {
                val payload = url.substringAfter(',', "")
                val bytes = runCatching {
                    if (head.contains(";base64", true)) Base64.decode(payload, Base64.DEFAULT)
                    else java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
                }.getOrNull()
                val w = if (bytes != null) DownloadsStore.begin(this, name, mime, bytes.size.toLong(), temp = incognito) else null
                val ok = w != null && runCatching { w.write(bytes!!) }.isSuccess
                val done = if (w != null) DownloadsStore.end(this, w, ok) else null
                runOnUiThread {
                    if (done != null) { softBuzz(); AppToast.show(this, "Staženo: $done") }
                    else AppToast.show(this, "Soubor se nepodařilo uložit")
                }
            }
            return true
        }
        url.startsWith("blob:", true) -> {
            if (wv == null) { AppToast.show(this, "Soubor se nepodařilo stáhnout"); return true }
            val token = UUID.randomUUID().toString()
            val name = fileNameFor(url, contentDisposition, mimeType)
            synchronized(blobJobs) { blobJobs[token] = BlobJob(name, mimeType ?: "", System.currentTimeMillis(), tabs.active()?.incognito == true) }
            val t = JSONObject.quote(token)
            // soubor se cte po 512 kB: v pameti je vzdy jen jeden kousek, velikost neni omezena
            val js = """
                (async function(){
                  var ok = false;
                  try {
                    const r = await fetch(${JSONObject.quote(url)});
                    const b = await r.blob();
                    if (!AtbDl.begin($t, b.type || "", b.size)) return;
                    for (let off = 0; off < b.size; off += $CHUNK) {
                      const buf = new Uint8Array(await b.slice(off, off + $CHUNK).arrayBuffer());
                      let s = "";
                      for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode.apply(null, buf.subarray(i, i + 0x8000));
                      if (!AtbDl.chunk($t, btoa(s))) throw new Error("write");
                    }
                    ok = true;
                  } catch (e) { }
                  AtbDl.end($t, ok);
                })();
            """.trimIndent()
            AppToast.show(this, "Stahuji: $name")
            wv.evaluateJavascript(js, null)
            return true
        }
    }
    return false
}
