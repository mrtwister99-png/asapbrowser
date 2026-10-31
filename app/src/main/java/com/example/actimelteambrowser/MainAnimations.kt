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

// Úvodní animace s hudbou, animace tlačítek a plnění tlačítek při stahování (rozšíření MainActivity).
// Uvodni animace: bile pozadi, sede logo se zviditelni, glitch do plneho loga (viz IntroView), hudba bez_zastaveni.mp3.
// Tlacitko Preskocit je vpravo dole. Po skonceni (nebo preskoceni) hudba doznije a ukaze se appka.
// Vypnuti: MainActivity.INTRO_ENABLED = false, nebo v Konfiguraci.
internal fun MainActivity.startIntro() {
    // uvodni animace jde vypnout v Konfiguraci (pak se rovnou ukaze appka)
    if (!MainActivity.INTRO_ENABLED || !AppSettings.introOn.get() || !AppSettings.animationsOn.get()) { playSettingsIntro(); return }
    val host = root as ViewGroup
    val layer = FrameLayout(this)
    layer.setBackgroundColor(Color.WHITE)
    layer.isClickable = true
    layer.elevation = dp(120).toFloat()
    val iv = IntroView(this)
    layer.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    val skip = TextView(this)
    skip.text = "Přeskočit  ›"
    skip.setTextColor(Color.parseColor("#222222"))
    skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    skip.setTypeface(skip.typeface, Typeface.BOLD)
    skip.setPadding(dp(16), dp(10), dp(16), dp(10))
    skip.background = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(Color.parseColor("#1A000000"))
    }
    val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
    slp.bottomMargin = dp(56)
    slp.rightMargin = dp(16)
    layer.addView(skip, slp)
    introPlayer = startIntroMusic()
    var done = false
    fun finish() {
        if (done) return
        done = true
        iv.stop()
        fadeOutIntroMusic()
        layer.animate().alpha(0f).setDuration(300).withEndAction {
            host.removeView(layer)
            playSettingsIntro()
        }.start()
    }
    skip.setOnClickListener { finish() }
    layer.postDelayed({ finish() }, IntroView.DURATION_MS + 1700)   // po animaci zustane plne logo 1,7 s (hudba hraje do konce)
    host.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

internal fun MainActivity.startIntroMusic(): android.media.MediaPlayer? {
    if (!AppSettings.soundsOn.get()) return null
    return try {
        val resId = resources.getIdentifier("bez_zastaveni", "raw", packageName)
        if (resId == 0) return null
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val mp = android.media.MediaPlayer.create(applicationContext, resId, attrs, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
        mp?.setOnCompletionListener { it.release(); if (introPlayer === it) introPlayer = null }
        mp?.start()
        mp
    } catch (e: Exception) {
        null   // hudba je jen bonus, nesmi shodit appku
    }
}

// Hudba se na konci animace (nebo po Preskocit) za 0,4 s ztlumi a vypne
internal fun MainActivity.fadeOutIntroMusic() {
    val mp = introPlayer ?: return
    introPlayer = null
    val a = ValueAnimator.ofFloat(1f, 0f).setDuration(400)
    a.addUpdateListener { try { mp.setVolume(it.animatedValue as Float, it.animatedValue as Float) } catch (e: Exception) { } }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            try { mp.stop() } catch (e: Exception) { }
            try { mp.release() } catch (e: Exception) { }
        }
    })
    a.start()
}

// Dokud neco stahujeme, ~1,4x za sekundu se zjisti stav a prekresli plneni tlacitek.
// Zadna souvisla animace: kazde tlacitko dostane jen novou vysku zelene vrstvy.
internal fun MainActivity.startDownloadPoll() {
    if (dlPolling) return
    dlPolling = true
    dlHandler.post(object : Runnable {
        override fun run() {
            thread(name = "dl-poll") {
                val items = try { com.example.actimelteambrowser.data.DownloadsStore.query(this@startDownloadPoll) } catch (e: Exception) { emptyList() }
                runOnUiThread {
                    val active = applyDownloadFill(items)
                    if (active) dlHandler.postDelayed(this, 700) else dlPolling = false
                }
            }
        }
    })
}

// 1. probihajici stahovani plni tlacitko 1, 2. tlacitko 2 atd. (0 -> 100 % zdola nahoru).
// Vraci true, kdyz neco dal bezi.
internal fun MainActivity.applyDownloadFill(items: List<com.example.actimelteambrowser.data.DlItem>): Boolean {
    val running = items.filter {
        it.status == android.app.DownloadManager.STATUS_RUNNING ||
            it.status == android.app.DownloadManager.STATUS_PENDING ||
            it.status == android.app.DownloadManager.STATUS_PAUSED
    }.sortedBy { it.id }   // od nejstarsiho
    // hotova stahovani: kratka zprava
    val nowIds = running.map { it.id }.toHashSet()
    for (id in dlActiveIds) {
        if (id in nowIds) continue
        items.firstOrNull { it.id == id }?.let {
            if (it.status == android.app.DownloadManager.STATUS_SUCCESSFUL) Toast.makeText(this, "Staženo: " + it.name, Toast.LENGTH_SHORT).show()
        }
    }
    dlActiveIds = nowIds
    // tlacitka, ktera lze plnit: zobrazena a ne ta s kulickou
    val orbN = gestureButtonN()
    val targets = (1..12).filter { AppSettings.isEnabled(it) && buttonView(it) != null && it != orbN }
    val used = HashSet<Int>()
    for ((i, d) in running.withIndex()) {
        val n = targets.getOrNull(i) ?: break
        val v = buttonView(n) ?: continue
        used.add(n)
        val fill = dlFills.getOrPut(n) {
            DownloadFillDrawable(dp(16).toFloat()).also { v.foreground = it }
        }
        if (v.foreground !== fill) v.foreground = fill
        fill.fraction = if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f)
        else 0.15f + 0.15f * (0.5f + 0.5f * kotlin.math.sin(System.currentTimeMillis() / 400.0).toFloat())   // velikost neznama
    }
    // ostatni tlacitka se vycisti
    val iter = dlFills.entries.iterator()
    while (iter.hasNext()) {
        val e = iter.next()
        if (e.key !in used) {
            buttonView(e.key)?.foreground = null
            iter.remove()
        }
    }
    return running.isNotEmpty()
}

// Jednorazova animace tlacitka Nastaveni po spusteni: ozubene kolecko se protoci a promeni v logo appky.
// Hraje se jen kdyz ma tlacitko vychozi funkci (jinak tam je ikona vlastni funkce).
internal fun MainActivity.playSettingsIntro() {
    val btn = findViewById<ImageView>(R.id.btnSettings)
    if (buttonFunction(4, false) != ButtonDefaults.tap(4) || !AppSettings.animationsOn.get()) return
    val gearTint = Color.parseColor("#1E1E1E")   // kolecko je bile, na svetlem tlacitku by nebylo videt
    var swapped = false
    btn.setImageResource(R.drawable.ic_settings)
    btn.setColorFilter(gearTint)
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 1100
    a.interpolator = android.view.animation.DecelerateInterpolator(1.6f)
    a.addUpdateListener {
        val p = it.animatedValue as Float
        btn.rotation = 720f * p                    // dve otocky, na konci zpomali
        if (p < 0.5f) {
            btn.alpha = 1f - p * 2f * 0.9f          // kolecko pomalu bledne
        } else {
            if (!swapped) {
                swapped = true
                btn.clearColorFilter()
                btn.setImageResource(R.drawable.asap_logo)
            }
            btn.alpha = 0.1f + (p - 0.5f) * 2f * 0.9f   // logo se objevuje
        }
    }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            btn.rotation = 0f
            btn.alpha = 1f
            btn.clearColorFilter()
            btn.setImageResource(R.drawable.asap_logo)
        }
    })
    a.start()
}

// Klepnuti na tlacitko Nastaveni: logo se otoci a promeni v kolecko (opak uvodni animace), pak se otevre Nastaveni
internal fun MainActivity.playSettingsOutro(onDone: () -> Unit) {
    if (settingsOutroRunning) return
    if (!AppSettings.animationsOn.get()) { onDone(); return }   // animace vypnute
    settingsOutroRunning = true
    val btn = findViewById<ImageView>(R.id.btnSettings)
    val gearTint = Color.parseColor("#1E1E1E")
    var swapped = false
    btn.clearColorFilter()
    btn.setImageResource(R.drawable.asap_logo)
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 600
    a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
    a.addUpdateListener {
        val p = it.animatedValue as Float
        btn.rotation = 360f * p
        if (p < 0.5f) {
            btn.alpha = 1f - p * 2f * 0.9f
        } else {
            if (!swapped) {
                swapped = true
                btn.setImageResource(R.drawable.ic_settings)
                btn.setColorFilter(gearTint)
            }
            btn.alpha = 0.1f + (p - 0.5f) * 2f * 0.9f
        }
    }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            btn.rotation = 0f
            btn.alpha = 1f
            settingsOutroRunning = false
            onDone()
            // kdyz se Nastaveni neotevrelo (zamek proti dvojitemu otevreni), vrati se logo
            btn.postDelayed({
                if (!settingsOpen) {
                    btn.clearColorFilter()
                    btn.setImageResource(R.drawable.asap_logo)
                }
            }, 900)
        }
    })
    a.start()
}

// Karty: cislo se zmeni na zelene plus a ctverecek se otoci o 720 stupnu (dve otocky)
internal fun MainActivity.animateTabsButton(onDone: () -> Unit) {
    if (tabsAnimating) return
    tabsAnimating = true
    val green = Color.parseColor("#00B055")
    val density = resources.displayMetrics.density
    val label = findViewById<TextView>(R.id.tvTabCount)
    var switched = false
    val anim = ObjectAnimator.ofFloat(label, View.ROTATION, 0f, 720f)   // dve otocky
    anim.duration = 1200
    anim.addUpdateListener {
        // v polovine otoceni se obsah zmeni na plus
        if (!switched && it.animatedFraction >= 0.5f) {
            switched = true
            label.text = "+"
            label.setTextColor(green)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            tabCountBg?.setStroke((2 * density).toInt(), green)
        }
    }
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            label.rotation = 0f
            label.setTextColor(Color.WHITE)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            tabCountBg?.setStroke((2 * density).toInt(), Color.WHITE)
            updateTabCount()
            tabsAnimating = false
            onDone()
        }
    })
    anim.start()
}

// Jeden "puls": zvetsit 1,5x, chvili drzet, vratit zpet
internal fun MainActivity.pulse(view: View, onEnd: () -> Unit) {
    view.animate().scaleX(1.5f).scaleY(1.5f).setDuration(400).withEndAction {
        view.postDelayed({
            view.animate().scaleX(1f).scaleY(1f).setDuration(400).withEndAction { onEnd() }.start()
        }, 120)
    }.start()
}

// Oblibene: hvezdicka se od stredu k okrajum vyplni zlute (0-100 %), pak kolem ni vyleti jiskricky a mala hvezdicka.
// Funkce tlacitka (klepnuti: Zalozky, drzeni: ulozit do slozky) se spusti, jakmile je hvezda cela zluta.
internal fun MainActivity.animateStar(onDone: () -> Unit) {
    if (starAnimating) return
    starAnimating = true
    val icon = findViewById<ImageView>(R.id.ivBookmarks)
    val loc = IntArray(2)
    val rl = IntArray(2)
    icon.getLocationInWindow(loc)
    root.getLocationInWindow(rl)
    val cx = loc[0] - rl[0] + icon.width / 2f
    val cy = loc[1] - rl[1] + icon.height / 2f
    val view = StarBurstView(this, cx, cy, icon.width.toFloat().coerceAtLeast(dp(24).toFloat()))
    view.elevation = dp(70).toFloat()   // nad spodni listou
    (root as ViewGroup).addView(view, ViewGroup.LayoutParams(-1, -1))
    var called = false
    val anim = ValueAnimator.ofFloat(0f, 1f)
    anim.duration = 1300
    anim.addUpdateListener {
        val t = it.animatedValue as Float
        view.fill = (t / 0.4f).coerceIn(0f, 1f)
        view.burst = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
        view.invalidate()
        if (!called && t >= 0.45f) {
            called = true
            onDone()
        }
    }
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            (root as ViewGroup).removeView(view)
            starAnimating = false
            if (!called) { called = true; onDone() }
        }
    })
    anim.start()
}

// Klepnuti na Domu: cele tlacitko 2x problikne zelene
internal fun MainActivity.blinkHome() {
    if (homeAnimating) return
    homeAnimating = true
    val b = findViewById<View>(R.id.btnHome)
    val green = GradientDrawable()
    green.setColor(Color.parseColor("#00B055"))
    green.cornerRadius = dp(18).toFloat()
    green.setStroke(dp(1), Color.WHITE)
    var step = 0
    val r = object : Runnable {
        override fun run() {
            if (step >= 4) {
                b.setBackgroundResource(R.drawable.btn_circle_dark)
                homeAnimating = false
                return
            }
            if (step % 2 == 0) b.background = green else b.setBackgroundResource(R.drawable.btn_circle_dark)
            step++
            b.postDelayed(this, 120)
        }
    }
    r.run()
}

// Klepnuti na Karty: ctverecek s cislem se otoci o 360 stupnu, pak se otevre prehled karet
internal fun MainActivity.spinTabs(onDone: () -> Unit) {
    if (tabsAnimating) return
    tabsAnimating = true
    val label = findViewById<TextView>(R.id.tvTabCount)
    val anim = ObjectAnimator.ofFloat(label, View.ROTATION, 0f, 360f)
    anim.duration = 450
    anim.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            label.rotation = 0f
            tabsAnimating = false
            onDone()
        }
    })
    anim.start()
}

// Domu: jen ikona - zelena ikona se odkryva zespoda nahoru, tlacitko samo se nemeni
internal fun MainActivity.animateHome(onDone: () -> Unit) {
    if (homeAnimating) return
    homeAnimating = true
    val green = findViewById<ImageView>(R.id.ivHomeGreen)
    val w = green.width
    val h = green.height
    if (w <= 0 || h <= 0) {
        homeAnimating = false
        onDone()
        return
    }
    green.visibility = View.VISIBLE
    green.clipBounds = Rect(0, h, w, h)
    val anim = ValueAnimator.ofInt(h, 0)
    anim.duration = 900
    anim.addUpdateListener { green.clipBounds = Rect(0, it.animatedValue as Int, w, h) }
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            // chvili zustane cela zelena, az potom se otevre okno
            green.postDelayed({
                onDone()
                green.postDelayed({
                    green.visibility = View.INVISIBLE
                    green.clipBounds = null
                    homeAnimating = false
                }, 250)
            }, 250)
        }
    })
    anim.start()
}
