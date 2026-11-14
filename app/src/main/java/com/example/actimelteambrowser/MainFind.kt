package com.example.actimelteambrowser

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt

// Najit na strance: vlastni panel nahore pod adresnim radkem (stejny vzhled jako v ukazce prvniho tutorialu).
// Lupa | hledany text | pocet "1 / 4" | ▲ ▼ | ✕. Hleda se primo ve strance (findAllAsync), nic se neuklada.

@SuppressLint("StaticFieldLeak")   // panel patri hlavnimu oknu, po zavreni se vynuluje
private var findBar: LinearLayout? = null
@SuppressLint("StaticFieldLeak")
private var findEdit: EditText? = null
@SuppressLint("StaticFieldLeak")
private var findCount: TextView? = null
private var findDemo = false   // panel ukazky: nehleda ve strance

internal fun MainActivity.isFindBarOpen(): Boolean = findBar != null

internal fun MainActivity.findBarView(): View? = findBar

/** Otevre panel; demo = jen vzhled pro ukazku (bez klavesnice a bez hledani ve strance). */
internal fun MainActivity.showFindBar(demo: Boolean = false) {
    if (findBar != null) { if (!demo) focusFind(); return }
    findDemo = demo
    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(dp(14), dp(6), dp(8), dp(6))
    val bg = GradientDrawable()
    bg.setColor("#242424".toColorInt())
    bg.cornerRadius = dp(18).toFloat()
    bg.setStroke(dp(1), "#444444".toColorInt())
    bar.background = bg
    bar.elevation = dp(12).toFloat()
    bar.isClickable = true   // dotyky neprojdou do stranky

    val lens = ImageView(this)
    lens.setImageResource(R.drawable.ic_search)
    lens.setColorFilter(Color.WHITE)
    bar.addView(lens, LinearLayout.LayoutParams(dp(20), dp(20)))

    val edit = EditText(this)
    edit.background = null
    edit.setTextColor(Color.WHITE)
    edit.setHintTextColor("#888888".toColorInt())
    edit.hint = "Hledat na stránce"
    edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
    edit.isSingleLine = true
    edit.imeOptions = EditorInfo.IME_ACTION_SEARCH
    edit.setPadding(dp(10), dp(8), dp(6), dp(8))
    bar.addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    val count = TextView(this)
    count.setTextColor("#CCCCCC".toColorInt())
    count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    count.setPadding(dp(4), 0, dp(6), 0)
    bar.addView(count)

    fun arrow(t: String, onClick: () -> Unit): TextView {
        val a = TextView(this)
        a.text = t
        a.setTextColor(Color.WHITE)
        a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        a.setTypeface(a.typeface, Typeface.BOLD)
        a.gravity = Gravity.CENTER
        a.setOnClickListener { onClick() }
        bar.addView(a, LinearLayout.LayoutParams(dp(38), dp(40)))
        return a
    }
    arrow("▲") { if (!findDemo) { hideFindKeyboard(); webView.findNext(false) } }
    arrow("▼") { if (!findDemo) { hideFindKeyboard(); webView.findNext(true) } }
    arrow("✕") { hideFindBar() }

    edit.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (findDemo) return
            val q = s?.toString().orEmpty()
            if (q.isEmpty()) { webView.clearMatches(); count.text = "" } else webView.findAllAsync(q)
        }
    })
    edit.setOnEditorActionListener { _, id, ev ->
        if (id == EditorInfo.IME_ACTION_SEARCH || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!findDemo) { webView.findNext(true); hideFindKeyboard() }
            true
        } else false
    }
    if (!demo) {
        webView.setFindListener { active, total, done ->
            if (done) count.text = if (total == 0) "0 / 0" else "${active + 1} / $total"
        }
    }

    val lp = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT)
    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
    lp.topToBottom = R.id.topBar
    lp.marginStart = dp(20); lp.marginEnd = dp(20); lp.topMargin = dp(10)
    (root as ConstraintLayout).addView(bar, lp)
    findBar = bar; findEdit = edit; findCount = count
    if (!demo) focusFind()
}

/** Ukazka: nastavi text a pocet (nic se nehleda). */
@Suppress("UnusedReceiverParameter")
internal fun MainActivity.setFindDemo(text: String, counter: String) {
    findEdit?.setText(text)
    findEdit?.setSelection(text.length)
    findCount?.text = counter
}

private fun MainActivity.focusFind() {
    val e = findEdit ?: return
    e.requestFocus()
    e.post { (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(e, 0) }
}

private fun MainActivity.hideFindKeyboard() {
    val e = findEdit ?: return
    (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(e.windowToken, 0)
}

internal fun MainActivity.hideFindBar() {
    val bar = findBar ?: return
    hideFindKeyboard()
    if (!findDemo && isWebViewReady()) {
        webView.clearMatches()
        webView.setFindListener(null)
    }
    (bar.parent as? android.view.ViewGroup)?.removeView(bar)
    findBar = null; findEdit = null; findCount = null; findDemo = false
}
