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

// Screenshoty: nabídka, výřez, celá stránka, uložení a náhled (rozšíření MainActivity).
// Klepnuti na foťáček v levé liště / funkce Screenshot: zeptá se, jaký screenshot
internal fun MainActivity.showScreenshotChooser() {
    showStyledDialog(title = "Screenshot", negativeText = "Zrušit") { dismiss ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        fun opt(text: String, run: () -> Unit) {
            val b = styledButton(text, false)
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setPadding(dp(18), dp(15), dp(18), dp(15))
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            b.setTextColor(Color.WHITE)
            b.setOnClickListener {
                dismiss()
                window.decorView.postDelayed({ run() }, 400)   // pockat, az okno zmizi
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            box.addView(b, lp)
        }
        opt("Celá obrazovka") { takeScreenshot() }
        opt("Výběr části – tažením prstu") { startRegionScreenshot() }
        opt("Celá stránka – scrollovací") { captureFullPage() }
        box
    }
}

// Foťáček v liste na chvili zezelena (potvrzeni klepnuti)
internal fun MainActivity.blinkGreen(v: View) {
    val g = GradientDrawable()
    g.setColor(Color.parseColor("#00B055"))
    g.cornerRadius = dp(18).toFloat()
    g.setStroke(dp(1), Color.WHITE)
    v.background = g
    v.postDelayed({ v.setBackgroundResource(R.drawable.btn_circle_dark) }, 300)
}

// Snimek cele okna appky (vcetne listy); vysledek null = nepovedlo se
internal fun MainActivity.grabWindow(onDone: (Bitmap?) -> Unit) {
    val decor = window.decorView
    if (decor.width <= 0 || decor.height <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { onDone(null); return }
    val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(window, bmp, { result -> onDone(if (result == PixelCopy.SUCCESS) bmp else null) }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) {
        onDone(null)
    }
}

// Vyber casti: porizeni snimku, pak se tazenim prstu oznaci obdelnik; po pusteni se ulozi vyrez
internal fun MainActivity.startRegionScreenshot() {
    if (leftBarOpen) closeLeftBarAndHide()
    window.decorView.postDelayed({
        grabWindow { shot ->
            if (shot == null) {
                Toast.makeText(this, "Screenshot se nepovedl", Toast.LENGTH_SHORT).show()
                return@grabWindow
            }
            val view = RegionSelectView(this, shot) { rect ->
                removeRegionOverlay()
                if (rect != null) {
                    val crop = Bitmap.createBitmap(shot, rect.left, rect.top, rect.width(), rect.height())
                    flashScreen()
                    saveScreenshot(crop)
                }
            }
            (window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(-1, -1))
            regionView = view
        }
    }, 450)
}

internal fun MainActivity.removeRegionOverlay() {
    regionView?.let { (it.parent as? ViewGroup)?.removeView(it) }
    regionView = null
}

// Celá stránka (scrollovací): stranka se po kouscich posouva, kazdy kousek se vyfoti a slepi se do jednoho obrazku.
// Omezeni: max. ~12 000 px vysky; fixni hlavicky stranek se mohou opakovat; stranky s vlastnim posouvanim uvnitr (napr. YouTube) nemusi jit.
@Suppress("DEPRECATION")
internal fun MainActivity.captureFullPage() {
    if (capturing) return
    val tab = tabs.active()
    if (tab == null || tab.startPage) {
        Toast.makeText(this, "Na úvodní stránce to nejde", Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(this, "Screenshot celé stránky vyžaduje Android 8+", Toast.LENGTH_SHORT).show()
        return
    }
    if (leftBarOpen) closeLeftBarAndHide()
    val wv = webView
    val viewH = wv.height
    val w = wv.width
    if (viewH <= 0 || w <= 0) return
    val maxH = 12000
    val total = (wv.contentHeight * wv.scale).toInt().coerceIn(viewH, maxH)
    val origY = wv.scrollY
    capturing = true
    Toast.makeText(this, "Zachytávám celou stránku…", Toast.LENGTH_SHORT).show()

    // schovat vse mimo web (listy, tlacitka), aby nebylo na snimku
    val keep = run {
        var v: View = wv
        var top: View = wv
        while (v.parent != null && v.parent !== root) { v = v.parent as View; top = v }
        top
    }
    val hidden = ArrayList<Pair<View, Float>>()
    val rootGroup = root as ViewGroup
    for (i in 0 until rootGroup.childCount) {
        val c = rootGroup.getChildAt(i)
        if (c !== keep && c.alpha > 0f) { hidden.add(Pair(c, c.alpha)); c.alpha = 0f }
    }
    val out = Bitmap.createBitmap(w, total, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val handler = Handler(Looper.getMainLooper())
    var reached = 0       // dokud je obrazek hotovy (px odshora)
    var lastScroll = -1

    fun finish(ok: Boolean) {
        wv.scrollTo(0, origY)
        for ((v, a) in hidden) v.alpha = a
        capturing = false
        if (!ok || reached <= 0) {
            Toast.makeText(this, "Screenshot se nepovedl", Toast.LENGTH_SHORT).show()
            return
        }
        val result = if (reached < out.height) Bitmap.createBitmap(out, 0, 0, w, reached) else out
        flashScreen()
        saveScreenshot(result)
    }

    fun step(y: Int) {
        wv.scrollTo(0, y)
        handler.postDelayed({
            val sy = wv.scrollY
            if (sy <= lastScroll && lastScroll >= 0) { finish(true); return@postDelayed }   // dal se uz neposouva
            val loc = IntArray(2)
            wv.getLocationInWindow(loc)
            val slice = Bitmap.createBitmap(w, viewH, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, Rect(loc[0], loc[1], loc[0] + w, loc[1] + viewH), slice, { res ->
                    if (res != PixelCopy.SUCCESS) { finish(reached > 0); return@request }
                    canvas.drawBitmap(slice, 0f, sy.toFloat(), null)
                    slice.recycle()
                    lastScroll = sy
                    reached = minOf(total, sy + viewH)
                    if (reached >= total) finish(true) else step(sy + viewH)
                }, handler)
            } catch (e: Exception) {
                finish(reached > 0)
            }
        }, 300)
    }
    step(0)
}

internal fun MainActivity.takeScreenshot() {
    if (leftBarOpen) closeLeftBarAndHide()
    Handler(Looper.getMainLooper()).postDelayed({ captureScreenshot() }, 450)
}

internal fun MainActivity.captureScreenshot() {
    val decor = window.decorView
    if (decor.width <= 0 || decor.height <= 0) return
    val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            PixelCopy.request(window, bmp, { result ->
                if (result == PixelCopy.SUCCESS) { flashScreen(); saveScreenshot(bmp) }
                else Toast.makeText(this, "Screenshot se nepovedl", Toast.LENGTH_SHORT).show()
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Toast.makeText(this, "Screenshot se nepovedl", Toast.LENGTH_SHORT).show()
        }
    } else {
        try {
            decor.draw(Canvas(bmp))
            flashScreen()
            saveScreenshot(bmp)
        } catch (e: Exception) {
            Toast.makeText(this, "Screenshot se nepovedl", Toast.LENGTH_SHORT).show()
        }
    }
}

// Probliknuti obrazovky + zvuk (res/raw/gotmyphoto.mp3) - da vedet, ze se screenshot povedl.
// Vola se az po porizeni snimku, takze blesk na obrazku neni.
internal fun MainActivity.flashScreen() {
    playRawSound(this, "gotmyphoto")
    val flash = View(this)
    flash.setBackgroundColor(Color.WHITE)
    flash.alpha = 0f
    flash.elevation = dp(200).toFloat()
    val host = root as ViewGroup
    host.addView(flash, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    flash.animate().alpha(0.9f).setDuration(70).withEndAction {
        flash.animate().alpha(0f).setDuration(300).withEndAction { host.removeView(flash) }.start()
    }.start()
}

// Android 10+: rovnou do galerie (slozka Pictures/ASAP Browser). Starsi: nabidne sdileni.
internal fun MainActivity.saveScreenshot(bmp: Bitmap) {
    thread(name = "screenshot") {
        val name = "ASAP-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues()
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ASAP Browser")
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Nelze vytvořit soubor")
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: throw IllegalStateException("Nelze zapsat")
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                runOnUiThread {
                    Toast.makeText(this, "Screenshot uložen do galerie", Toast.LENGTH_SHORT).show()
                    showShotPreview(bmp, uri)
                }
            } else {
                val dir = File(cacheDir, ExportFileProvider.DIR)
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, name)
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = ExportFileProvider.uriFor(this, name)
                val send = Intent(Intent.ACTION_SEND)
                send.type = "image/png"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.clipData = android.content.ClipData.newRawUri("", uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runOnUiThread { startActivity(Intent.createChooser(send, "Screenshot")) }
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Screenshot se nepovedl", Toast.LENGTH_SHORT).show() }
        }
    }
}

// Maly nahled ulozeneho screenshotu v levem dolnim rohu; klepnuti ho otevre v galerii, po chvili sam zmizi
internal fun MainActivity.showShotPreview(bmp: Bitmap, uri: Uri) {
    shotPreview?.let { (it.parent as? ViewGroup)?.removeView(it) }
    val tw = dp(96)
    val th = (tw.toFloat() * bmp.height / bmp.width).toInt().coerceIn(dp(70), dp(170))
    val thumb = Bitmap.createScaledBitmap(bmp, tw, maxOf(1, (bmp.height.toFloat() * tw / bmp.width).toInt()), true)
    val iv = ImageView(this)
    iv.setImageBitmap(thumb)
    iv.scaleType = ImageView.ScaleType.CENTER_CROP
    val bg = GradientDrawable()
    bg.setColor(Color.parseColor("#1E1E1E"))
    bg.cornerRadius = dp(14).toFloat()
    bg.setStroke(dp(2), Color.WHITE)
    iv.background = bg
    iv.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    iv.clipToOutline = true
    iv.elevation = dp(150).toFloat()
    iv.setOnClickListener {
        try {
            val view = Intent(Intent.ACTION_VIEW)
            view.setDataAndType(uri, "image/png")
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(view)
        } catch (e: Exception) {
            Toast.makeText(this, "Nenašla se aplikace pro obrázky", Toast.LENGTH_SHORT).show()
        }
        (iv.parent as? ViewGroup)?.removeView(iv)
    }
    val lp = ConstraintLayout.LayoutParams(tw, th)
    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.leftMargin = dp(12)
    lp.bottomMargin = dp(90)
    (root as ViewGroup).addView(iv, lp)
    shotPreview = iv
    iv.alpha = 0f
    iv.translationY = dp(24).toFloat()
    iv.animate().alpha(1f).translationY(0f).setDuration(220).start()
    iv.postDelayed({
        iv.animate().alpha(0f).setDuration(300).withEndAction { (iv.parent as? ViewGroup)?.removeView(iv) }.start()
    }, 4500)
}
