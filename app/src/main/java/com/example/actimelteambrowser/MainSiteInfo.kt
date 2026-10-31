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

// Informace o stránce, oprávnění webů, sdílení a QR kód (rozšíření MainActivity).
// Rozhodne o zadosti webu: ulozena volba (povolit / zakazat) plati hned, jinak se uzivatel zepta.
// Povolene typy pak jeste projdou systemovym opravnenim Androidu. result dostane mnozinu skutecne povolenych typu.
internal fun MainActivity.handleSitePermission(host: String, types: List<String>, persist: Boolean, result: (Set<String>) -> Unit) {
    val allowed = types.filter { SiteRules.perm(host, it) == 1 }.toMutableSet()
    val ask = types.filter { SiteRules.perm(host, it) == -1 }
    fun finish() {
        // systemova opravneni, ktera aplikace jeste nema
        val missing = allowed.filter { !hasAndroidPerm(it) }
        if (missing.isEmpty()) { result(allowed); return }
        if (permCallback != null) { result(emptySet()); return }   // uz se nejake opravneni ptame
        permCallback = {
            for (t in missing) if (!hasAndroidPerm(t)) allowed.remove(t)
            result(allowed)
        }
        permLauncher.launch(missing.flatMap { androidPermsFor(it) }.distinct().toTypedArray())
    }
    if (ask.isEmpty()) { finish(); return }
    val names = ask.joinToString(", ") { when (it) { "geo" -> "polohu"; "cam" -> "kameru"; else -> "mikrofon" } }
    var answered = false
    AlertDialog.Builder(this)
        .setTitle(host.ifBlank { "Web" })
        .setMessage("Tento web chce použít: $names.\n\nVolbu si pamatuju, změníš ji v informacích o stránce (zámek).")
        .setPositiveButton("Povolit") { _, _ ->
            answered = true
            if (persist) for (t in ask) SiteRules.setPerm(host, t, 1)
            allowed.addAll(ask)
            finish()
        }
        .setNegativeButton("Zakázat") { _, _ ->
            answered = true
            if (persist) for (t in ask) SiteRules.setPerm(host, t, 0)
            finish()
        }
        .setOnDismissListener { if (!answered) { answered = true; finish() } }   // zavreno bokem = jen to, co uz bylo povolene
        .show()
}

// Smaze cookies webu (i nadrazenych domen) a jeho ulozena data; vrati pocet smazanych cookies
internal fun MainActivity.clearSiteCookies(url: String): Int {
    val host = try { Uri.parse(url).host } catch (e: Exception) { null } ?: return 0
    val cm = CookieManager.getInstance()
    val parts = host.split(".")
    val domains = ArrayList<String>()
    for (i in 0 until maxOf(1, parts.size - 1)) domains.add(parts.drop(i).joinToString("."))
    var n = 0
    for (d in domains) {
        for (scheme in listOf("https://", "http://")) {
            val raw = cm.getCookie(scheme + d) ?: continue
            for (c in raw.split(";")) {
                val name = c.substringBefore("=").trim()
                if (name.isEmpty()) continue
                n++
                for (dom in listOf("", "; Domain=$d", "; Domain=.$d")) {
                    cm.setCookie(scheme + d, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/$dom")
                }
            }
        }
    }
    cm.flush()
    try { WebStorage.getInstance().deleteOrigin("https://$host"); WebStorage.getInstance().deleteOrigin("http://$host") } catch (e: Exception) { }
    return n
}

internal fun MainActivity.updateLockIcon(url: String?) {
    // tecka na zamku = na tomhle webu je AdBlock vypnuty (vyjimka)
    val dot = findViewById<View>(R.id.vUrlLockDot)
    if (dot.background == null) {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.parseColor("#CDA24D"))
        g.setStroke(dp(1), Color.parseColor("#1E1E1E"))
        dot.background = g
    }
    val h = try { if (url != null && isWebUrl(url)) Uri.parse(url).host else null } catch (e: Exception) { null }
    dot.visibility = if (SiteRules.adBlockOff(h)) View.VISIBLE else View.GONE
    val iv = findViewById<ImageView>(R.id.ivUrlLock)
    when {
        url != null && url.startsWith("https://") -> iv.clearColorFilter()                        // zabezpecene
        url != null && url.startsWith("http://") -> iv.setColorFilter(Color.parseColor("#AC0001")) // nezabezpecene
        else -> iv.setColorFilter(Color.parseColor("#888888"))                                     // interni / prazdne
    }
}

// Zamek v adresnim radku: informace o aktualni strance + nastaveni pro tento web
internal fun MainActivity.showPageInfo() {
    val url = webView.url
    val tab = tabs.active()
    val secure = url != null && url.startsWith("https://")
    val plainHttp = url != null && url.startsWith("http://")
    val host = if (url != null && isWebUrl(url)) (try { Uri.parse(url).host } catch (e: Exception) { null }) else null
    val incognito = tab?.incognito == true
    showStyledDialog(title = "Informace o stránce", negativeText = "Zavřít") { _ ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        fun row(label: String, value: String, color: Int = Color.WHITE) {
            val l = dialogLabel(label)
            val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            llp.topMargin = dp(10)
            box.addView(l, llp)
            val v = TextView(this)
            v.text = value
            v.setTextColor(color)
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            v.maxLines = 3
            v.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            box.addView(v)
        }
        // radek s tlacitkem vpravo (hodnota se meni klepnutim)
        fun actionRow(label: String, value: String, onClick: (TextView) -> Unit) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val t = TextView(this)
            t.text = label
            t.setTextColor(Color.WHITE)
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val b = styledButton(value, false)
            b.setPadding(dp(14), dp(7), dp(14), dp(7))
            b.setOnClickListener { onClick(b) }
            r.addView(b)
            val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rlp.topMargin = dp(8)
            box.addView(r, rlp)
        }

        // varovani nahore u nesifrovane stranky
        if (plainHttp) {
            val w = TextView(this)
            w.text = "Pozor: stránka není šifrovaná (HTTP). Nezadávej na ní hesla ani platební údaje."
            w.setTextColor(Color.parseColor("#FF8A8A"))
            w.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            w.setTypeface(w.typeface, Typeface.BOLD)
            w.setPadding(dp(12), dp(10), dp(12), dp(10))
            val wb = GradientDrawable()
            wb.setColor(Color.parseColor("#3A1414"))
            wb.cornerRadius = dp(12).toFloat()
            wb.setStroke(dp(1), Color.parseColor("#AC0001"))
            w.background = wb
            box.addView(w)
        }

        row("Název", webView.title.orEmpty().ifBlank { "—" })
        row("Adresa", url ?: "—")
        when {
            secure -> row("Zabezpečení", "HTTPS – spojení je šifrované", Color.parseColor("#00B055"))
            plainHttp -> row("Zabezpečení", "HTTP – spojení NENÍ šifrované", Color.parseColor("#FF5A5A"))
            else -> row("Zabezpečení", "Interní stránka")
        }
        val cert = webView.certificate
        if (secure && cert != null) {
            val issuer = cert.issuedBy?.let { it.cName ?: it.oName }.orEmpty()
            if (issuer.isNotBlank()) row("Certifikát vydal", issuer)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val until = cert.validNotAfterDate
                if (until != null) row("Certifikát platí do", java.text.SimpleDateFormat("d. M. yyyy", java.util.Locale("cs")).format(until))
            }
        }
        row("Režim", (if (tab?.desktop == true) "Verze pro počítač" else "Mobilní verze") + (if (incognito) " · anonymní karta" else ""))

        // AdBlock: zablokovano + vyjimka pro tento web
        val n = tab?.blocked?.get() ?: 0
        if (!isAdBlockOn) {
            row("AdBlock", "Vypnutý (celkově)")
        } else {
            row("AdBlock – zablokováno na stránce", n.toString())
            if (host != null && !incognito) {
                fun lbl() = if (SiteRules.adBlockOff(host)) "Vypnutý" else "Zapnutý"
                actionRow("AdBlock na tomto webu", lbl()) { tv ->
                    SiteRules.setAdBlockOff(host, !SiteRules.adBlockOff(host))
                    tv.text = lbl()
                    updateLockIcon(webView.url)
                    webView.reload()
                }
            }
        }

        // opravneni webu a cookies (v anonymni karte se nic neuklada)
        if (host != null && !incognito) {
            val head = dialogLabel("Oprávnění webu")
            val hlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            hlp.topMargin = dp(16)
            box.addView(head, hlp)
            for ((type, name) in listOf("geo" to "Poloha", "cam" to "Kamera", "mic" to "Mikrofon")) {
                fun lbl() = when (SiteRules.perm(host, type)) { 1 -> "Povoleno"; 0 -> "Zakázáno"; else -> "Zeptat se" }
                actionRow(name, lbl()) { tv ->
                    // zeptat se -> povoleno -> zakazano -> zeptat se
                    val next = when (SiteRules.perm(host, type)) { -1 -> 1; 1 -> 0; else -> -1 }
                    SiteRules.setPerm(host, type, next)
                    tv.text = lbl()
                }
            }
            val clear = styledButton("Smazat cookies tohoto webu", false)
            clear.setOnClickListener {
                val count = if (url != null) clearSiteCookies(url) else 0
                Toast.makeText(this, if (count > 0) "Smazáno cookies: $count" else "Tento web nemá žádné cookies", Toast.LENGTH_SHORT).show()
                webView.reload()
            }
            val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clp.topMargin = dp(16)
            box.addView(clear, clp)
        } else if (incognito) {
            row("Oprávnění a cookies", "V anonymní kartě se nic neukládá")
        }

        // vysoke okno se posouva
        val scroll = ScrollView(this)
        scroll.addView(box)
        val dm = resources.displayMetrics
        box.measure(
            View.MeasureSpec.makeMeasureSpec((dm.widthPixels * 0.9f).toInt() - dp(44), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        scroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, minOf(box.measuredHeight, (dm.heightPixels * 0.6f).toInt())
        )
        scroll
    }
}

// Soubor s QR kodem v cache (sdili se pres ExportFileProvider)
internal fun MainActivity.qrFile(url: String): File {
    val dir = File(cacheDir, ExportFileProvider.DIR)
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val f = File(dir, "qr-" + System.currentTimeMillis() + ".png")
    val bmp = makeQr(url)
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return f
}

// Pravy symbol v adresnim radku: Sdilet - uzivatel si prepne, jestli posila/kopiruje odkaz, nebo QR kod
internal fun MainActivity.showShareDialog() {
    val url = webView.url
    if (url == null || !isWebUrl(url)) {
        Toast.makeText(this, "Není co sdílet", Toast.LENGTH_SHORT).show()
        return
    }
    var qrMode = false
    showStyledDialog(
        title = "Sdílet",
        positiveText = "Sdílet",
        onPositive = {
            if (!qrMode) {
                shareText(url, "Sdílet odkaz")
            } else {
                try {
                    val uri = ExportFileProvider.uriFor(this, qrFile(url).name)
                    val send = Intent(Intent.ACTION_SEND)
                    send.type = "image/png"
                    send.putExtra(Intent.EXTRA_STREAM, uri)
                    send.clipData = android.content.ClipData.newRawUri("", uri)
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "Sdílet QR kód"))
                } catch (e: Exception) {
                    Toast.makeText(this, "QR kód se nepodařilo vytvořit", Toast.LENGTH_SHORT).show()
                }
            }
            true
        },
        negativeText = "Zavřít"
    ) { _ ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        // prepinac Odkaz / QR kod
        val switchRow = LinearLayout(this)
        switchRow.orientation = LinearLayout.HORIZONTAL
        val tvLink = styledButton("Odkaz", true)
        val tvQr = styledButton("QR kód", false)
        val half = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        switchRow.addView(tvLink, half)
        val half2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        half2.marginStart = dp(8)
        switchRow.addView(tvQr, half2)
        box.addView(switchRow)

        // obsah: adresa, nebo QR kod
        val tvUrl = TextView(this)
        tvUrl.text = url
        tvUrl.setTextColor(Color.WHITE)
        tvUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tvUrl.maxLines = 4
        tvUrl.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        tvUrl.setPadding(dp(4), dp(14), dp(4), dp(4))
        box.addView(tvUrl)
        val ivQr = ImageView(this)
        ivQr.setBackgroundColor(Color.WHITE)   // bily podklad i v tmavem rezimu, jinak se kod nenaskenuje
        ivQr.setPadding(dp(8), dp(8), dp(8), dp(8))
        ivQr.visibility = View.GONE
        val qlp = LinearLayout.LayoutParams(dp(230), dp(230))
        qlp.gravity = Gravity.CENTER_HORIZONTAL
        qlp.topMargin = dp(14)
        box.addView(ivQr, qlp)

        fun restyle(tv: TextView, active: Boolean) {
            val bg = GradientDrawable()
            bg.cornerRadius = dp(14).toFloat()
            bg.setColor(if (active) Color.parseColor("#00B055") else Color.parseColor("#2A2A2A"))
            tv.background = bg
            tv.setTextColor(if (active) Color.parseColor("#06281D") else Color.parseColor("#CCCCCC"))
        }
        fun setMode(qr: Boolean) {
            if (qr && ivQr.drawable == null) {
                try {
                    ivQr.setImageBitmap(makeQr(url))
                } catch (e: Exception) {
                    Toast.makeText(this, "Adresa je moc dlouhá pro QR kód", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            qrMode = qr
            restyle(tvLink, !qr)
            restyle(tvQr, qr)
            tvUrl.visibility = if (qr) View.GONE else View.VISIBLE
            ivQr.visibility = if (qr) View.VISIBLE else View.GONE
        }
        tvLink.setOnClickListener { setMode(false) }
        tvQr.setOnClickListener { setMode(true) }

        // kopirovat to, co je prave vybrane (odkaz nebo obrazek QR)
        val copy = styledButton("Kopírovat", false)
        copy.setOnClickListener {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (!qrMode) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                Toast.makeText(this, "Adresa zkopírována", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val uri = ExportFileProvider.uriFor(this, qrFile(url).name)
                    cm.setPrimaryClip(android.content.ClipData.newUri(contentResolver, "QR", uri))
                    Toast.makeText(this, "QR kód zkopírován", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "QR kód se nepodařilo zkopírovat", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.topMargin = dp(14)
        box.addView(copy, clp)
        box
    }
}

// Vykresli QR (zxing); vyhodi vyjimku, kdyz se adresa nevejde
internal fun MainActivity.makeQr(text: String): Bitmap {
    val hints = HashMap<com.google.zxing.EncodeHintType, Any>()
    hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
    hints[com.google.zxing.EncodeHintType.MARGIN] = 2
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
    val scale = maxOf(1, 600 / maxOf(m.width, m.height))
    val w = m.width * scale
    val h = m.height * scale
    val px = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        px[y * w + x] = if (m.get(x / scale, y / scale)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}
