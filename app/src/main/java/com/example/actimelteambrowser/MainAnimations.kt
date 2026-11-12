package com.example.actimelteambrowser

import com.example.actimelteambrowser.data.DownloadsStore
import com.example.actimelteambrowser.data.DlItem
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.app.DownloadManager
import android.animation.AnimatorSet
import androidx.core.graphics.toColorInt
import android.view.View
import android.widget.*
import kotlin.concurrent.thread
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.util.TypedValue
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup

// Úvodní animace s hudbou, animace tlačítek a plnění tlačítek při stahování (rozšíření MainActivity).
// Uvodni animace: bile pozadi, sede logo se zviditelni, glitch do plneho loga (viz IntroView), hudba bez_zastaveni.mp3.
// Tlacitko Preskocit je vpravo dole. Po skonceni (nebo preskoceni) hudba doznije a ukaze se appka.
// Vypnuti: MainActivity.INTRO_ENABLED = false, nebo v Konfiguraci.
@Suppress("SimplifyBooleanWithConstants")   // INTRO_ENABLED je prepinac v kodu
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
    skip.setText(R.string.intro_skip)
    skip.setTextColor("#222222".toColorInt())
    skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    skip.setTypeface(skip.typeface, Typeface.BOLD)
    skip.setPadding(dp(16), dp(10), dp(16), dp(10))
    skip.background = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor("#1A000000".toColorInt())
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

internal fun MainActivity.startIntroMusic(): MediaPlayer? {
    if (!AppSettings.soundsOn.get()) return null
    return try {
        val resId = @Suppress("DiscouragedApi") resources.getIdentifier("bez_zastaveni", "raw", packageName)
        if (resId == 0) return null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val mp = MediaPlayer.create(applicationContext, resId, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
        mp?.setOnCompletionListener { it.release(); if (introPlayer === it) introPlayer = null }
        mp?.start()
        mp
    } catch (e: Exception) {
        noteError(e)
        null   // hudba je jen bonus, nesmi shodit appku
    }
}

// Hudba se na konci animace (nebo po Preskocit) za 0,4 s ztlumi a vypne
internal fun MainActivity.fadeOutIntroMusic() {
    val mp = introPlayer ?: return
    introPlayer = null
    val a = ValueAnimator.ofFloat(1f, 0f).setDuration(400)
    a.addUpdateListener { quiet { mp.setVolume(it.animatedValue as Float, it.animatedValue as Float) } }
    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            quiet { mp.stop() }
            quiet { mp.release() }
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
                val items = runCatching { DownloadsStore.query(this@startDownloadPoll) }.getOrDefault(emptyList())
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
internal fun MainActivity.applyDownloadFill(items: List<DlItem>): Boolean {
    val running = items.filter {
        it.status == DownloadManager.STATUS_RUNNING ||
            it.status == DownloadManager.STATUS_PENDING ||
            it.status == DownloadManager.STATUS_PAUSED
    }.sortedBy { it.id }   // od nejstarsiho
    // hotova stahovani: kratka zprava
    val nowIds = running.map { it.id }.toHashSet()
    for (id in dlActiveIds) {
        if (id in nowIds) continue
        items.firstOrNull { it.id == id }?.let {
            if (it.status == DownloadManager.STATUS_SUCCESSFUL) AppToast.show(this, "Staženo: " + it.name)
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
    showWelcomeOnce()   // jednorazova uvodni zprava (po uvodni animaci)
    val btn = findViewById<ImageView>(R.id.btnSettings)
    if (buttonFunction(4, false) != ButtonDefaults.tap(4) || !AppSettings.animationsOn.get()) return
    val gearTint = "#1E1E1E".toColorInt()   // kolecko je bile, na svetlem tlacitku by nebylo videt
    var swapped = false
    btn.setImageResource(R.drawable.ic_settings)
    btn.setColorFilter(gearTint)
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 1100
    a.interpolator = DecelerateInterpolator(1.6f)
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

// Drzeni tlacitka Nastaveni: logo se otoci o 180 stupnu, na chvili se zastavi a otoci se jeste o 180; pak se provede funkce
internal fun MainActivity.spinSettingsLogo(onDone: () -> Unit) {
    if (settingsOutroRunning) return
    settingsOutroRunning = true
    val btn = findViewById<ImageView>(R.id.btnSettings)
    val first = ObjectAnimator.ofFloat(btn, View.ROTATION, 0f, 180f).apply {
        duration = 450
        interpolator = AccelerateDecelerateInterpolator()
    }
    val second = ObjectAnimator.ofFloat(btn, View.ROTATION, 180f, 360f).apply {
        duration = 450
        startDelay = 300   // zastaveni mezi otockami
        interpolator = AccelerateDecelerateInterpolator()
    }
    val set = AnimatorSet()
    set.playSequentially(first, second)
    set.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            btn.rotation = 0f
            settingsOutroRunning = false
            onDone()
        }
    })
    set.start()
}

// Klepnuti na tlacitko Nastaveni: logo se otoci a promeni v kolecko (opak uvodni animace), pak se otevre Nastaveni
internal fun MainActivity.playSettingsOutro(onDone: () -> Unit) {
    if (settingsOutroRunning) return
    if (!AppSettings.animationsOn.get()) { onDone(); return }   // animace vypnute
    settingsOutroRunning = true
    val btn = findViewById<ImageView>(R.id.btnSettings)
    val gearTint = "#1E1E1E".toColorInt()
    var swapped = false
    btn.clearColorFilter()
    btn.setImageResource(R.drawable.asap_logo)
    val a = ValueAnimator.ofFloat(0f, 1f)
    a.duration = 600
    a.interpolator = AccelerateDecelerateInterpolator()
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
    val green = accentState.value
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
            tabCountBg?.setStroke(tabStrokePx, green)
        }
    }
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            label.rotation = 0f
            label.setTextColor(Color.WHITE)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            tabCountBg?.setStroke(tabStrokePx, Color.WHITE)
            updateTabCount()
            tabsAnimating = false
            onDone()
        }
    })
    anim.start()
}

// Jeden "puls": zvetsit 1,5x, chvili drzet, vratit zpet
@Suppress("UnusedReceiverParameter")
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

// Klepnuti na Oblibene: ikona se otoci o 90 stupnu, chvili stoji a otoci se o dalsich 180 stupnu (obrys 2x problikne zlute);
// pak chvili pocka a otoci se jeste o 134 stupnu (obrys mezitim zase 2x problikne). Funkce se spusti az po cele animaci.
internal fun MainActivity.spinFavorites(onDone: () -> Unit) {
    if (starAnimating) return
    starAnimating = true
    val icon = findViewById<ImageView>(R.id.ivBookmarks)
    val btn = findViewById<View>(R.id.btnBookmarks)
    val yellowBg = GradientDrawable()
    yellowBg.setColor("#2A2A2A".toColorInt())
    yellowBg.cornerRadius = dp(18).toFloat()
    yellowBg.setStroke(dp(2), "#FFD600".toColorInt())
    // blik obrysu: zlute / normalni / zlute / normalni
    fun blink() {
        var step = 0
        val r = object : Runnable {
            override fun run() {
                if (step >= 4) { btn.setBackgroundResource(R.drawable.btn_circle_dark); return }
                if (step % 2 == 0) btn.background = yellowBg else btn.setBackgroundResource(R.drawable.btn_circle_dark)
                step++
                btn.postDelayed(this, 130)
            }
        }
        r.run()
    }
    blink()
    val first = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 90f).setDuration(350)
    first.interpolator = DecelerateInterpolator()
    val second = ObjectAnimator.ofFloat(icon, View.ROTATION, 90f, 270f).setDuration(450)
    second.startDelay = 150
    second.interpolator = AccelerateDecelerateInterpolator()
    val third = ObjectAnimator.ofFloat(icon, View.ROTATION, 270f, 404f).setDuration(450)   // +134 stupnu
    third.startDelay = 450   // chvili pocka
    third.interpolator = AccelerateDecelerateInterpolator()
    third.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) { blink() }   // 2x problikne obrys behem tretiho otoceni
    })
    var called = false
    val set = AnimatorSet()
    set.playSequentially(first, second, third)
    set.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            icon.rotation = 0f
            btn.setBackgroundResource(R.drawable.btn_circle_dark)
            starAnimating = false
            if (!called) { called = true; onDone() }
        }
    })
    set.start()
}

// Klepnuti na Domu: cele tlacitko 3x problikne zelene
internal fun MainActivity.blinkHome() {
    if (homeAnimating) return
    homeAnimating = true
    val b = findViewById<View>(R.id.btnHome)
    val green = GradientDrawable()
    green.setColor(accentState.value)
    green.cornerRadius = dp(18).toFloat()
    green.setStroke(dp(1), Color.WHITE)
    var step = 0
    val r = object : Runnable {
        override fun run() {
            if (step >= 6) {   // 3x zelene (drive 2x)
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

// Klepnuti na Karty: ctverecek s cislem se otoci o 360 stupnu, obrys zezelena (v anonymnim rezimu zmodra)
// a ctverecek se zvetsi na 1,3x; pak se otevre prehled karet
internal fun MainActivity.spinTabs(onDone: () -> Unit) {
    if (tabsAnimating) return
    tabsAnimating = true
    val label = findViewById<TextView>(R.id.tvTabCount)
    val strokePx = tabStrokePx
    tabCountBg?.setStroke(strokePx, accentState.value)
    val anim = ObjectAnimator.ofFloat(label, View.ROTATION, 0f, 360f)
    anim.duration = 450
    anim.interpolator = AccelerateDecelerateInterpolator()
    anim.addUpdateListener {
        val s = 1f + 0.3f * minOf(1f, it.animatedFraction * 3f)   // rychle na 1,3x a tak zustane
        label.scaleX = s
        label.scaleY = s
    }
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            label.rotation = 0f
            label.scaleX = 1f
            label.scaleY = 1f
            tabCountBg?.setStroke(strokePx, Color.WHITE)
            tabsAnimating = false
            onDone()
        }
    })
    anim.start()
}

// Drzeni Domu: jen ikona - zespoda nahoru se vyplni (plna ikona), az je cela vyplnena, zvetsi se 1,5x;
// teprve potom se provede funkce (nastaveni domovske stranky). Tlacitko samo se nemeni.
internal fun MainActivity.animateHome(onDone: () -> Unit) {
    if (homeAnimating) return
    homeAnimating = true
    val icon = findViewById<ImageView>(R.id.ivHome)
    val green = findViewById<ImageView>(R.id.ivHomeGreen)
    val w = green.width
    val h = green.height
    if (w <= 0 || h <= 0) {
        homeAnimating = false
        onDone()
        return
    }
    green.imageTintList = ColorStateList.valueOf(accentState.value)
    green.visibility = View.VISIBLE
    green.clipBounds = Rect(0, h, w, h)
    val anim = ValueAnimator.ofInt(h, 0)
    anim.duration = 900
    anim.addUpdateListener { green.clipBounds = Rect(0, it.animatedValue as Int, w, h) }
    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            green.clipBounds = null
            // plna ikona (i obrys pod ni) se zvetsi 1,5x, pak se otevre okno a ikona se vrati
            for (v in listOf(icon, green)) v.animate().scaleX(1.5f).scaleY(1.5f).setDuration(260).start()
            green.postDelayed({
                onDone()
                green.postDelayed({
                    for (v in listOf(icon, green)) v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    green.postDelayed({
                        green.visibility = View.INVISIBLE
                        homeAnimating = false
                    }, 200)
                }, 250)
            }, 330)
        }
    })
    anim.start()
}


// Symboly "+" / "-", ktere pri otevreni / zavreni karty vyletí z leveho a praveho okraje: 3 z kazde strany
// v nahodne vysce, vletí smerem do stredu, pak padaji dolu a zapadnou kousek od okraje.
internal fun MainActivity.flyBurst(symbol: String, colorHex: String) {
    if (!AppSettings.animationsOn.get()) return
    val parent = root as? ViewGroup ?: return
    val w = parent.width.toFloat()
    val h = parent.height.toFloat()
    if (w <= 0f || h <= 0f) return
    val color = colorHex.toColorInt()
    val rnd = java.util.Random()
    val size = dp(34).toFloat()
    for (left in listOf(true, false)) {
        for (i in 0 until 3) {
            val tv = TextView(this)
            tv.text = symbol
            tv.setTextColor(color)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, floatArrayOf(20f, 30f, 42f)[rnd.nextInt(3)])   // tri velikosti = hloubka
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.gravity = Gravity.CENTER
            tv.elevation = dp(30).toFloat()
            tv.alpha = 0f
            parent.addView(tv, ViewGroup.LayoutParams((size * 1.4f).toInt(), (size * 1.4f).toInt()))
            val dir = if (left) 1f else -1f
            val edgeX = if (left) -size else w
            val midX = if (left) w * (0.30f + rnd.nextFloat() * 0.15f) else w * (0.55f + rnd.nextFloat() * 0.15f)
            val endX = if (left) w * (0.03f + rnd.nextFloat() * 0.08f) else w * (0.89f - rnd.nextFloat() * 0.08f)
            val y0 = h * (0.2f + rnd.nextFloat() * 0.5f)   // nahodna vyska startu
            val peak = y0 - dp(50 + rnd.nextInt(40))
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 1500
            a.startDelay = (i * 160 + rnd.nextInt(120)).toLong()
            a.addUpdateListener {
                val t = it.animatedValue as Float
                val split = 0.4f
                if (t < split) {
                    val k = t / split
                    val e = 1f - (1f - k) * (1f - k)       // doletí zpomalene
                    tv.translationX = edgeX + (midX - edgeX) * e
                    tv.translationY = y0 + (peak - y0) * e
                    tv.alpha = 1f
                } else {
                    val k = (t - split) / (1f - split)
                    tv.translationX = midX + (endX - midX) * (1f - (1f - k) * (1f - k))
                    tv.translationY = peak + (h + size - peak) * k * k   // pad se zrychlenim
                    tv.alpha = if (k > 0.85f) (1f - k) / 0.15f else 1f
                }
                tv.rotation = dir * 360f * t
            }
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { parent.removeView(tv) }
                override fun onAnimationCancel(animation: Animator) { parent.removeView(tv) }
            })
            a.start()
        }
    }
}

// Krok zpet / vpred ve spodnim poli: tvar sipky se zelene vyplni (od kolecka k hrotu), az potom se stranka prepne
internal fun MainActivity.setupNavArrows() {
    fun wire(btnId: Int, fillId: Int, forward: Boolean) {
        val btn = findViewById<View>(btnId)
        val fill = findViewById<ImageView>(fillId)
        var busy = false
        btn.setOnClickListener {
            if (busy) return@setOnClickListener
            val can = if (forward) webView.canGoForward() else webView.canGoBack()
            fun go() { if (forward) webView.goForward() else webView.goBack() }
            val w = fill.width; val h = fill.height
            if (!can) {
                // neni kam jit: cervena vypln + lehke zabrneni + hlaska
                softBuzz(30)
                AppToast.show(this, "Není kam jít", false, AppToast.Kind.ERROR)
            }
            if (!AppSettings.animationsOn.get() || w <= 0 || h <= 0) { if (can) go(); return@setOnClickListener }
            busy = true
            fill.imageTintList = ColorStateList.valueOf(if (can) accentState.value else 0xFFE53935.toInt())
            fill.visibility = View.VISIBLE
            // vyplneni zprava doleva (u vpred je obrazek zrcadleny, takze jde smerem k hrotu)
            val a = ValueAnimator.ofInt(w, 0)
            a.duration = 320
            a.interpolator = DecelerateInterpolator()
            a.addUpdateListener { fill.clipBounds = Rect(it.animatedValue as Int, 0, w, h) }
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fill.clipBounds = null
                    fill.postDelayed({
                        if (can) go()
                        fill.animate().alpha(0f).setDuration(200).withEndAction {
                            fill.visibility = View.INVISIBLE
                            fill.alpha = 1f
                            busy = false
                            updateNavArrows()
                        }.start()
                    }, 120)
                }
            })
            a.start()
        }
    }
    wire(R.id.btnNavBack, R.id.ivNavBackFill, false)
    wire(R.id.btnNavForward, R.id.ivNavForwardFill, true)
    updateNavArrows()
}

// Sipka, kterou nejde pouzit (neni kam jit), je svetle seda
internal fun MainActivity.updateNavArrows() {
    if (!isWebViewReady()) return
    val gray = ColorStateList.valueOf(0xFFC4C4C4.toInt())
    findViewById<ImageView>(R.id.ivNavBack)?.imageTintList = if (webView.canGoBack()) null else gray
    findViewById<ImageView>(R.id.ivNavForward)?.imageTintList = if (webView.canGoForward()) null else gray
}
