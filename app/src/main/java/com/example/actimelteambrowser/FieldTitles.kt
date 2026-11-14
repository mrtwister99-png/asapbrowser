package com.example.actimelteambrowser

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import androidx.core.graphics.toColorInt

/**
 * Nadpisy v textovych polich (prvotni nastaveni, demo videa horni listy): horni a dolni pole ukazou velky zluty text
 * na tmavem podkladu a nejde do nich psat. clearFieldTitles vrati pole do bezne podoby (adresa stranky / prazdne).
 * topUrlText() vraci behem nadpisu nadpis, takze ho nacteni stranky neprepise.
 */
internal var fieldTitleTop: String? = null
private var fieldTitlesOn = false

internal fun MainActivity.showFieldTitles(top: String?, bottom: String?) {
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
        val bg = GradientDrawable()
        bg.setColor("#1A1A1A".toColorInt())
        bg.cornerRadius = dp(if (et === etTopUrl) 20 else 28).toFloat()
        bg.setStroke(dp(2), yellow)
        container.background = bg
    }
    hideKeyboard()
    style(etTopUrl, top, findViewById(R.id.urlContainer))
    style(etSearchMain, bottom, etSearchMain)
}

internal fun MainActivity.clearFieldTitles() {
    if (!fieldTitlesOn) return
    fieldTitlesOn = false
    fieldTitleTop = null
    for (et in listOf(etTopUrl, etSearchMain)) {
        et.isFocusable = true
        et.isFocusableInTouchMode = true
        et.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
    }
    etTopUrl.setTextColor(Color.WHITE)
    etSearchMain.setTextColor(Color.BLACK)
    etSearchMain.setText("")
    setupFieldBackgrounds()
    if (isWebViewReady()) { etTopUrl.setText(topUrlText(webView.url)); updateLockIcon(webView.url) } else etTopUrl.setText("")
}
