package com.example.actimelteambrowser

import androidx.appcompat.content.res.AppCompatResources
import android.text.TextUtils
import android.content.ClipboardManager
import android.content.ClipData
import androidx.core.graphics.toColorInt
import android.webkit.*
import android.widget.*
import kotlin.concurrent.thread
import android.content.Intent
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.util.TypedValue
import java.io.File
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup

// Dlouhé podržení na stránce: nabídka odkazu a obrázku, kopírování a sdílení (rozšíření MainActivity).
internal fun MainActivity.onWebLongPress(wv: WebView): Boolean {
    val hit = wv.hitTestResult
    val extra = hit.extra
    when (hit.type) {
        WebView.HitTestResult.IMAGE_TYPE -> {
            if (extra.isNullOrBlank()) return false
            showImageMenu(extra)
            return true
        }
        WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
            // adresu odkazu a jeho text da WebView jen pres zpravu
            val imageSrc = if (hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) extra else null
            val handler = Handler(Looper.getMainLooper()) { m ->
                val url = m.data.getString("url") ?: extra
                val text = m.data.getString("title")
                if (!url.isNullOrBlank()) showLinkMenu(url, text, imageSrc)
                true
            }
            wv.requestFocusNodeHref(handler.obtainMessage())
            return true
        }
    }
    return false   // ostatni (text, prazdne misto) nechat na systemu
}

internal fun MainActivity.openInNewTab(url: String, incognito: Boolean) {
    if (!isWebUrl(url)) {
        AppToast.show(this, "Tuhle adresu nelze otevřít")
        return
    }
    val active = tabs.active()
    val open = { createTab(url, incognito = incognito); Unit }
    if (active != null) capturePreview(active) { open() } else open()
}

internal fun MainActivity.copyToClipboard(label: String, text: String, msg: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    AppToast.show(this, msg)
}

internal fun MainActivity.shareText(text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND)
    send.type = "text/plain"
    send.putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, title))
}

// Nabidka s ikonami (tmavy styl appky)
internal fun MainActivity.showIconMenu(title: String, rows: List<MainActivity.MenuRow>) {
    showStyledDialog(title = title, negativeText = "Zrušit") { dismiss ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        for (r in rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(11), dp(14), dp(11))
            val bg = GradientDrawable()
            bg.setColor((if (r.highlight) "#00B055" else "#2A2A2A").toColorInt())   // zvyraznena nejcastejsi akce
            bg.cornerRadius = dp(14).toFloat()
            row.background = bg
            val iv = ImageView(this)
            if (r.icon != 0) iv.setImageDrawable(AppCompatResources.getDrawable(this, r.icon))
            row.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val tv = TextView(this)
            tv.text = r.label
            tv.setTextColor(Color.WHITE)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            col.addView(tv)
            if (r.sub.isNotBlank()) {
                val sub = TextView(this)
                sub.text = r.sub
                sub.setTextColor((if (r.highlight) "#D7FFE8" else "#888888").toColorInt())
                sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                sub.maxLines = 1
                sub.ellipsize = TextUtils.TruncateAt.MIDDLE
                col.addView(sub)
            }
            val clp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            clp.marginStart = dp(14)
            row.addView(col, clp)
            row.setOnClickListener {
                dismiss()
                r.action()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            box.addView(row, lp)
        }
        val scroll = ScrollView(this)
        scroll.addView(box)
        scroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        scroll
    }
}

// Nabidka po dlouhem podrzeni na odkazu: kratke nazvy, pod nimi adresa
internal fun MainActivity.showLinkMenu(url: String, text: String?, imageSrc: String?) {
    val addr = com.example.actimelteambrowser.ui.prettyUrl(url)
    val rows = ArrayList<MainActivity.MenuRow>()
    rows.add(MainActivity.MenuRow("Nová karta", addr, R.drawable.ic_new_tab, true) { openInNewTab(url, false) })
    rows.add(MainActivity.MenuRow("Anonymní karta", addr, R.drawable.ic_incognito) { openInNewTab(url, true) })
    rows.add(MainActivity.MenuRow("Uložit do Oblíbených", addr, R.drawable.ic_quick_favorite) { saveLinkToFavorites(url, text) })
    if (!text.isNullOrBlank()) {
        rows.add(MainActivity.MenuRow("Kopírovat text", text.trim(), R.drawable.ic_copy_url) { copyToClipboard("text", text.trim(), "Text zkopírován") })
    }
    rows.add(MainActivity.MenuRow("Kopírovat adresu", addr, R.drawable.ic_copy_url) { copyToClipboard("url", url, "Adresa zkopírována") })
    rows.add(MainActivity.MenuRow("Sdílet", addr, R.drawable.ic_share_url) { shareText(url, "Sdílet odkaz") })
    if (!imageSrc.isNullOrBlank()) {
        rows.add(MainActivity.MenuRow("Obrázek", com.example.actimelteambrowser.ui.prettyUrl(imageSrc), 0) { showImageMenu(imageSrc) })
    }
    showIconMenu("Odkaz", rows)
}

// Ulozi odkaz (bez otevreni) do vychozi slozky Oblibene
internal fun MainActivity.saveLinkToFavorites(url: String, text: String?) {
    if (!isWebUrl(url)) {
        AppToast.show(this, "Tenhle odkaz nelze uložit")
        return
    }
    thread(name = "bookmark-link") {
        quiet {
            bookmarksRepo.addBookmark(bookmarksRepo.defaultFolderId(), url, text?.trim().orEmpty())
        }
        runOnUiThread { softBuzz(35); AppToast.show(this, "Přidáno do Oblíbených") }
    }
}

internal fun MainActivity.showImageMenu(src: String) {
    val addr = com.example.actimelteambrowser.ui.prettyUrl(src)
    showIconMenu(
        "Obrázek",
        listOf(
            MainActivity.MenuRow("Nová karta", addr, R.drawable.ic_new_tab, true) { openInNewTab(src, false) },
            MainActivity.MenuRow("Stáhnout", addr, R.drawable.ic_import) { startDownload(src, webView.settings.userAgentString, null, null) },
            MainActivity.MenuRow("Sdílet", addr, R.drawable.ic_share_url) { shareImage(src) }
        )
    )
}

// Obrazek se stahne do cache (na pozadi) a nabidne se sdileni jako soubor; pri chybe se sdili aspon adresa
internal fun MainActivity.shareImage(src: String) {
    if (!isWebUrl(src)) {
        AppToast.show(this, "Tenhle obrázek nejde sdílet")
        return
    }
    val ua = webView.settings.userAgentString
    val cookie = cookiesFor(src, tabs.active()?.incognito == true)
    AppToast.show(this, "Připravuji obrázek…")
    thread(name = "share-image") {
        try {
            val conn = java.net.URL(src).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", ua)
            if (cookie != null) conn.setRequestProperty("Cookie", cookie)
            val mime = (conn.contentType ?: "image/jpeg").substringBefore(';').trim()
            val ext = when (mime) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "jpg" }
            val dir = File(cacheDir, ExportFileProvider.DIR)
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "obrazek-" + System.currentTimeMillis() + "." + ext)
            var total = 0L
            conn.inputStream.use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 20L * 1024 * 1024) throw IllegalStateException("Obrázek je moc velký")
                        out.write(buf, 0, n)
                    }
                }
            }
            val uri = ExportFileProvider.uriFor(this, f.name)
            val send = Intent(Intent.ACTION_SEND)
            send.type = if (mime.startsWith("image/")) mime else "image/jpeg"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.clipData = ClipData.newRawUri("", uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runOnUiThread { startActivity(Intent.createChooser(send, "Sdílet obrázek")) }
        } catch (e: Exception) {
            noteError(e)
            runOnUiThread { shareText(src, "Sdílet adresu obrázku") }
        }
    }
}
