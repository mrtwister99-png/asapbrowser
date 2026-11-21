package com.example.actimelteambrowser

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import androidx.core.graphics.toColorInt

/**
 * Nadpisy v textovych polich (prvotni nastaveni, demo videa): horni a dolni pole ukazou velky zluty text na tmavem
 * podkladu a nejde do nich psat. Pole jsou "cista": bez zamku a Sdilet nahore a bez sipek a lupy dole (vic mista pro text);
 * `keep` = ikony, ktere zustanou (ukazka prave tohoto tlacitka). clearFieldTitles vrati pole do bezne podoby.
 * topUrlText() vraci behem nadpisu nadpis, takze ho nacteni stranky neprepise.
 */
internal var fieldTitleTop: String? = null
private var fieldTitlesOn = false
private var savedSearchIcons: Array<Drawable?>? = null
private val hiddenIcons = ArrayList<View>()

internal fun MainActivity.showFieldTitles(top: String?, bottom: String?, keep: Set<Int> = emptySet()) {
    restoreFieldIcons()
    fieldTitlesOn = true
    fieldTitleTop = top
    val yellow = DEMO_YELLOW.toColorInt()
    fun style(et: EditText, text: String?, container: View) {
        et.clearFocus()
        et.isFocusable = false
        et.isFocusableInTouchMode = false
        et.setText(text.orEmpty())
        et.setTextColor(yellow)
        et.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        et.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setColor("#1A1A1A".toColorInt())
        bg.cornerRadius = dp(if (et === etTopUrl) 20 else 28).toFloat()
        bg.setStroke(dp(2), yellow)
        container.background = bg
    }
    hideKeyboard()
    style(etTopUrl, top, findViewById(R.id.urlContainer))
    style(etSearchMain, bottom, etSearchMain)

    // cista pole: ikony pryc (krome tech v keep)
    fun hide(v: View?) { if (v != null && v.visibility != View.GONE) { hiddenIcons.add(v); v.visibility = View.GONE } }
    if (R.id.ivUrlLock !in keep) hide(findViewById<View>(R.id.ivUrlLock).parent as? View)
    if (R.id.ivUrlShare !in keep) hide(findViewById(R.id.ivUrlShare))
    val arrowsKept = R.id.btnNavBack in keep || R.id.btnNavForward in keep
    if (!arrowsKept) {
        hide(findViewById(R.id.btnNavBack)); hide(findViewById(R.id.btnNavForward))
        if (savedSearchIcons == null) savedSearchIcons = etSearchMain.compoundDrawablesRelative
        etSearchMain.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        etSearchMain.setPaddingRelative(dp(16), etSearchMain.paddingTop, dp(16), etSearchMain.paddingBottom)
    }
}

private fun MainActivity.restoreFieldIcons() {
    hiddenIcons.forEach { it.visibility = View.VISIBLE }
    hiddenIcons.clear()
    savedSearchIcons?.let { d -> etSearchMain.setCompoundDrawablesRelativeWithIntrinsicBounds(d[0], d[1], d[2], d[3]) }
    savedSearchIcons = null
    etSearchMain.setPaddingRelative(dp(40), etSearchMain.paddingTop, dp(42), etSearchMain.paddingBottom)
}

internal fun MainActivity.clearFieldTitles() {
    if (!fieldTitlesOn) return
    fieldTitlesOn = false
    fieldTitleTop = null
    restoreFieldIcons()
    for (et in listOf(etTopUrl, etSearchMain)) {
        et.isFocusable = true
        et.isFocusableInTouchMode = true
        et.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        et.gravity = Gravity.CENTER_VERTICAL or Gravity.START
    }
    etTopUrl.setTypeface(Typeface.DEFAULT, Typeface.BOLD)   // horni pole je v rozlozeni tucne
    etTopUrl.setTextColor(Color.WHITE)
    etSearchMain.setTextColor(Color.BLACK)
    etSearchMain.setText("")
    setupFieldBackgrounds()
    if (isWebViewReady()) { etTopUrl.setText(topUrlText(webView.url)); updateLockIcon(webView.url) } else etTopUrl.setText("")
}
