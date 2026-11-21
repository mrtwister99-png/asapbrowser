package com.example.actimelteambrowser

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import com.example.actimelteambrowser.core.settings.AppSettings

/**
 * Prvni vzhled pri prvnim spusteni (a pri novem pruchodu prvotnim nastavenim): tlacitka jsou jen tmave sede bez ikon,
 * textova pole svetle seda a misto list karet jsou sede dlazdice 1-5 (nahore i dole). Ikonu ma jen tlacitko listy a gest.
 * Prvotni nastaveni pak casti postupne odkryva (zlute 2x probliknou a ikony se pomalu objevi); konec / preskoceni = vse.
 * Zakryti je kresba v overlay tlacitka (dotyky jdou dal, prekresleni tlacitka ho nezrusi).
 */
private val covers = HashMap<View, GradientDrawable>()
private val stripPlaceholders = HashMap<Boolean, View>()   // true = horni, false = spodni
internal var firstLookOn = false
    private set

internal fun MainActivity.bottomButtonViews(): List<View> =
    listOf(R.id.btnHome, R.id.btnTabs, R.id.btnBookmarks, R.id.btnSettings).map { findViewById(it) }

internal fun MainActivity.bottomFieldView(): View = etSearchMain.parent as View

private fun MainActivity.cover(v: View, color: Int, radiusDp: Int) {
    if (covers.containsKey(v)) return
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(radiusDp).toFloat()
    covers[v] = d
    val place = { d.setBounds(0, 0, v.width, v.height); v.overlay.add(d) }
    if (v.width > 0) place() else v.post { if (covers[v] === d) place() }
}

private fun MainActivity.stripPlaceholder(top: Boolean) {
    if (stripPlaceholders.containsKey(top)) return
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.setPadding(dp(8), 0, dp(8), 0)
    row.elevation = dp(6).toFloat()   // nad pozadim s UFO (4) i nad skutecnou listou karet (5)
    val shades = listOf("#3A3A3A", "#474747", "#545454", "#616161", "#6E6E6E")
    for ((i, c) in shades.withIndex()) {
        val t = TextView(this)
        t.text = (i + 1).toString()
        t.setTextColor("#99FFFFFF".toColorInt())
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        t.gravity = Gravity.CENTER
        t.background = GradientDrawable().apply { setColor(c.toColorInt()); cornerRadius = dp(8).toFloat() }
        val lp = LinearLayout.LayoutParams(0, ViewGroup_MATCH, 1f)
        if (i > 0) lp.marginStart = dp(3)
        row.addView(t, lp)
    }
    val lp = ConstraintLayout.LayoutParams(0, dp(30))
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    if (top) { lp.topToBottom = R.id.progressBar; lp.topMargin = dp(2) } else { lp.bottomToTop = R.id.bottomBar; lp.bottomMargin = dp(4) }
    (root as ConstraintLayout).addView(row, lp)
    stripPlaceholders[top] = row
}

private const val ViewGroup_MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT

/** Zapne prvni vzhled (tlacitka bez ikon, seda pole a listy). */
internal fun MainActivity.applyFirstLook() {
    firstLookOn = true
    val dark = "#2E2E2E".toColorInt()
    val light = "#6B6B6B".toColorInt()
    for (v in bottomButtonViews()) cover(v, dark, 18)
    cover(findViewById(R.id.btnAdBlock), dark, 18)
    cover(findViewById(R.id.btnVpn), dark, 18)
    cover(findViewById(R.id.urlContainer), light, 20)
    cover(bottomFieldView(), light, 28)
    if (AppSettings.tabStripTop.get()) stripPlaceholder(true)
    if (AppSettings.tabStripBottom.get()) stripPlaceholder(false)
}

/** Pole maji v prvotnim nastaveni zlute nadpisy: jejich sede zakryti pryc hned. */
internal fun MainActivity.uncoverFields() {
    for (v in listOf(findViewById(R.id.urlContainer), bottomFieldView())) covers.remove(v)?.let { v.overlay.remove(it) }
}

/** Pomalu odkryje [views] (a sede listy karet [strips]: true = horni, false = spodni). */
internal fun MainActivity.revealFirstLook(views: List<View>, strips: List<Boolean> = emptyList(), ms: Long = 1400, onDone: (() -> Unit)? = null) {
    val ds = views.mapNotNull { v -> covers.remove(v)?.let { v to it } }
    val ph = strips.mapNotNull { stripPlaceholders.remove(it) }
    if (ds.isEmpty() && ph.isEmpty()) { onDone?.invoke(); return }
    val a = ValueAnimator.ofFloat(1f, 0f)
    a.duration = ms
    a.addUpdateListener { an ->
        val k = an.animatedValue as Float
        for ((v, d) in ds) { d.alpha = (255 * k).toInt(); v.invalidate() }
        for (p in ph) p.alpha = k
    }
    a.addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
            for ((v, d) in ds) v.overlay.remove(d)
            for (p in ph) (p.parent as? android.view.ViewGroup)?.removeView(p)
            if (covers.isEmpty() && stripPlaceholders.isEmpty()) firstLookOn = false
            onDone?.invoke()
        }
    })
    a.start()
}

/** Odkryje vse, co jeste zakryte (konec / preskoceni prvotniho nastaveni). */
internal fun MainActivity.revealAllFirstLook() {
    if (!firstLookOn && covers.isEmpty() && stripPlaceholders.isEmpty()) return
    revealFirstLook(covers.keys.toList(), stripPlaceholders.keys.toList(), 900)
}

/** Obdelnik sede listy karet (pro zlute probliknuti), null = neni. */
internal fun MainActivity.stripPlaceholderView(top: Boolean): View? = stripPlaceholders[top]

/**
 * Seda lista karet odjede: dlazdice (u [keepFirst] od 2. dal) postupne odjedou doprava za okraj,
 * prvni dlazdice pak pomalu zmizi a ukaze se pod ni skutecna karta c. 1.
 */
internal fun MainActivity.slideStripPlaceholder(top: Boolean, keepFirst: Boolean, onDone: (() -> Unit)? = null) {
    val row = stripPlaceholders[top] as? android.view.ViewGroup ?: run { onDone?.invoke(); return }
    val w = resources.displayMetrics.widthPixels.toFloat()
    val from = if (keepFirst) 1 else 0
    var last = 0L
    for (i in from until row.childCount) {
        val t = row.getChildAt(i)
        val delay = (i - from) * 120L
        last = delay + 520L
        t.animate().translationX(w).setStartDelay(delay).setDuration(520).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
    }
    row.postDelayed({
        stripPlaceholders.remove(top)
        row.animate().alpha(0f).setDuration(if (keepFirst) 700 else 150).withEndAction {
            (row.parent as? android.view.ViewGroup)?.removeView(row)
            if (covers.isEmpty() && stripPlaceholders.isEmpty()) firstLookOn = false
            onDone?.invoke()
        }.start()
    }, last + 80)
}

/** Pomalu odkryje prvky (alpha 0 -> 1), ktere setup na chvili schoval (sipky, zamek, Sdilet). */
internal fun fadeInViews(views: List<View>, ms: Long = 1400) {
    for (v in views) { v.alpha = 0f; v.animate().alpha(1f).setDuration(ms).start() }
}
