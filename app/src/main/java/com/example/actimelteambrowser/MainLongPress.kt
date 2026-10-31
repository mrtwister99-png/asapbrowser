package com.example.actimelteambrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.example.actimelteambrowser.core.adblock.AdBlocker
import com.example.actimelteambrowser.core.adblock.AdBlockRules
import java.io.ByteArrayInputStream
import com.example.actimelteambrowser.core.engine.RustBridge
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import com.example.actimelteambrowser.core.adblock.CookieReject
import com.example.actimelteambrowser.core.adblock.ListUpdater
import com.example.actimelteambrowser.core.settings.AppSettings
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.example.actimelteambrowser.data.BookmarksRepository
import com.example.actimelteambrowser.data.HistoryRepository
import com.example.actimelteambrowser.data.HistoryVisit
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.ui.SearchSuggestion
import com.example.actimelteambrowser.ui.ButtonConfigModal
import com.example.actimelteambrowser.ui.SearchSuggestionsList
import com.example.actimelteambrowser.ui.buildSearchSuggestions
import android.graphics.Bitmap
import android.view.MotionEvent
import com.example.actimelteambrowser.ui.ExternalTouch
import com.example.actimelteambrowser.core.settings.Gestures
import com.example.actimelteambrowser.core.gestures.gestureTick
import com.example.actimelteambrowser.core.gestures.playRawSound
import com.example.actimelteambrowser.core.settings.GlyphIcons
import com.example.actimelteambrowser.core.settings.SiteRules
import com.example.actimelteambrowser.core.settings.SearchEngines
import com.example.actimelteambrowser.ui.StartPage
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.actimelteambrowser.ui.StartSite
import com.example.actimelteambrowser.ui.GestureOverlay
import com.example.actimelteambrowser.ui.GestureArea
import com.example.actimelteambrowser.ui.GestureAreaEditor
import com.example.actimelteambrowser.ui.GesturePanel
import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.view.Choreographer
import androidx.constraintlayout.widget.ConstraintLayout
import android.graphics.Paint
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.util.TypedValue
import android.transition.TransitionManager
import android.net.Uri
import android.graphics.BitmapFactory
import android.text.InputType
import java.io.File
import java.io.FileOutputStream
import androidx.activity.OnBackPressedCallback
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.example.actimelteambrowser.ui.TabTile
import com.example.actimelteambrowser.ui.TabsScreen

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
        Toast.makeText(this, "Tuhle adresu nelze otevřít", Toast.LENGTH_SHORT).show()
        return
    }
    val active = tabs.active()
    val open = { createTab(url, incognito = incognito); Unit }
    if (active != null) capturePreview(active) { open() } else open()
}

internal fun MainActivity.copyToClipboard(label: String, text: String, msg: String) {
    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
            bg.setColor(Color.parseColor(if (r.highlight) "#00B055" else "#2A2A2A"))   // zvyraznena nejcastejsi akce
            bg.cornerRadius = dp(14).toFloat()
            row.background = bg
            val iv = ImageView(this)
            if (r.icon != 0) iv.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(this, r.icon))
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
                sub.setTextColor(Color.parseColor(if (r.highlight) "#D7FFE8" else "#888888"))
                sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                sub.maxLines = 1
                sub.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
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
        Toast.makeText(this, "Tenhle odkaz nelze uložit", Toast.LENGTH_SHORT).show()
        return
    }
    thread(name = "bookmark-link") {
        try {
            bookmarksRepo.addBookmark(bookmarksRepo.defaultFolderId(), url, text?.trim().orEmpty())
        } catch (e: Exception) {
        }
        runOnUiThread { Toast.makeText(this, "Přidáno do Oblíbených", Toast.LENGTH_SHORT).show() }
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
        Toast.makeText(this, "Tenhle obrázek nejde sdílet", Toast.LENGTH_SHORT).show()
        return
    }
    val ua = webView.settings.userAgentString
    val cookie = cookiesFor(src, tabs.active()?.incognito == true)
    Toast.makeText(this, "Připravuji obrázek…", Toast.LENGTH_SHORT).show()
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
            send.clipData = android.content.ClipData.newRawUri("", uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runOnUiThread { startActivity(Intent.createChooser(send, "Sdílet obrázek")) }
        } catch (e: Exception) {
            runOnUiThread { shareText(src, "Sdílet adresu obrázku") }
        }
    }
}
