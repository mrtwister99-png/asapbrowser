package com.example.actimelteambrowser

import android.annotation.SuppressLint
import androidx.core.graphics.toColorInt
import android.view.View
import android.widget.*
import com.example.actimelteambrowser.core.settings.AppSettings
import android.view.MotionEvent
import android.app.Dialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.util.TypedValue
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup

// Sjednocené dialogy appky (rozšíření MainActivity).
internal fun MainActivity.styledButton(text: String, primary: Boolean): TextView {
    val b = TextView(this)
    b.text = text
    b.gravity = Gravity.CENTER
    b.setPadding(dp(18), dp(10), dp(18), dp(10))
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    b.setTypeface(b.typeface, Typeface.BOLD)
    b.setTextColor(if (primary) "#06281D".toColorInt() else "#CCCCCC".toColorInt())
    val bg = GradientDrawable()
    bg.cornerRadius = dp(14).toFloat()
    bg.setColor(if (primary) "#00B055".toColorInt() else "#2A2A2A".toColorInt())
    b.background = bg
    return b
}

// Tmavy zaoblený panel jako zbytek appky; "vyskoci" (zvetseni + prolnuti) pri otevreni.
// onPositive vraci true = dialog se zavre. bodyBuilder dostane funkci pro zavreni dialogu.
// Posledni otevreny styled dialog (karta + zavreni) - pouziva ho ukazka prvniho tutorialu, aby klepala na skutecne prvky
@SuppressLint("StaticFieldLeak")   // drzi se jen po dobu zobrazeni dialogu
internal var lastDialogCard: View? = null
internal var lastDialogDismiss: (() -> Unit)? = null

internal fun MainActivity.showStyledDialog(
    title: String,
    positiveText: String? = null,
    onPositive: (() -> Boolean)? = null,
    negativeText: String? = "Zrušit",
    bodyBuilder: (dismiss: () -> Unit) -> View
) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val dismiss = { dialog.dismiss() }

    val card = LinearLayout(this)
    lastDialogCard = card
    lastDialogDismiss = dismiss
    card.orientation = LinearLayout.VERTICAL
    card.setPadding(dp(22), dp(20), dp(22), dp(16))
    val cardBg = GradientDrawable()
    cardBg.setColor("#1E1E1E".toColorInt())
    cardBg.cornerRadius = dp(24).toFloat()
    cardBg.setStroke(dp(1), "#2A2A2A".toColorInt())
    card.background = cardBg

    val titleView = TextView(this)
    titleView.text = title
    titleView.setTextColor(Color.WHITE)
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    titleView.setTypeface(titleView.typeface, Typeface.BOLD)
    card.addView(titleView)

    val bodyView = bodyBuilder(dismiss)
    val bodyLp = (bodyView.layoutParams as? LinearLayout.LayoutParams)
        ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    bodyLp.topMargin = dp(14)
    card.addView(bodyView, bodyLp)

    val buttons = LinearLayout(this)
    buttons.orientation = LinearLayout.HORIZONTAL
    buttons.gravity = Gravity.END
    if (negativeText != null) {
        val neg = styledButton(negativeText, false)
        neg.setOnClickListener { dismiss() }
        buttons.addView(neg)
    }
    if (positiveText != null) {
        val pos = styledButton(positiveText, true)
        pos.setOnClickListener { if (onPositive == null || onPositive()) dismiss() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(10)
        buttons.addView(pos, lp)
    }
    val btnLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    btnLp.topMargin = dp(16)
    card.addView(buttons, btnLp)

    dialog.setContentView(card)
    dialog.window?.let { w ->
        w.setBackgroundDrawableResource(android.R.color.transparent)
        w.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        w.setDimAmount(0.6f)
    }
    card.alpha = 0f
    card.scaleX = 0.85f
    card.scaleY = 0.85f
    dialog.show()
    card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
        .setInterpolator(OvershootInterpolator(1.3f)).start()
}

internal fun MainActivity.dialogLabel(text: String): TextView {
    val l = TextView(this)
    l.text = text.uppercase()
    l.setTextColor("#888888".toColorInt())
    l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
    l.setTypeface(l.typeface, Typeface.BOLD)
    return l
}

// Uvodni zprava po prvnim spusteni: modal o 2 strankach (texty zatim jen zastupne), tecky = strana, prepinani i prejetim prstu. Ukaze se jednou.
@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.showWelcomeOnce() {
    if (AppSettings.welcomeShown.get()) return
    AppSettings.welcomeShown.set(true)
    window.decorView.post {
        val pages = listOf("1. strana", "2. strana")   // TODO: skutecne texty
        var page = 0
        showStyledDialog(title = "Úvod", positiveText = null, negativeText = null) { dismiss ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL

            // obsah strany + tecky; prejeti prstem doleva = dalsi strana, doprava = predchozi
            val pager = LinearLayout(this)
            pager.orientation = LinearLayout.VERTICAL
            pager.minimumHeight = dp(140)
            val text = TextView(this)
            text.setTextColor(Color.WHITE)
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text.minHeight = dp(110)
            pager.addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val dotsRow = LinearLayout(this)
            dotsRow.orientation = LinearLayout.HORIZONTAL
            dotsRow.gravity = Gravity.CENTER
            val dots = pages.map {
                val d = View(this)
                d.background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
                val dlp = LinearLayout.LayoutParams(dp(8), dp(8))
                dlp.marginStart = dp(4); dlp.marginEnd = dp(4)
                dotsRow.addView(d, dlp)
                d
            }
            pager.addView(dotsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val next = styledButton("", true)
            fun render() {
                text.text = pages[page]
                for ((i, d) in dots.withIndex()) {
                    (d.background as GradientDrawable).setColor((if (i == page) "#00B055" else "#555555").toColorInt())
                }
                next.text = if (page == pages.size - 1) "Okay" else "Dále"
            }
            // zmena strany s kratkym posunem textu (dir: 1 = dalsi, -1 = predchozi)
            fun go(dir: Int) {
                val target = page + dir
                if (target !in pages.indices) return
                text.animate().alpha(0f).translationX(-dir * dp(30).toFloat()).setDuration(110).withEndAction {
                    page = target
                    render()
                    text.translationX = dir * dp(30).toFloat()
                    text.animate().alpha(1f).translationX(0f).setDuration(140).start()
                }.start()
            }
            var downX = 0f
            pager.setOnTouchListener { pv, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downX = ev.x
                    MotionEvent.ACTION_UP -> {
                        pv.performClick()   // pristupnost
                        val dx = ev.x - downX
                        if (dx < -dp(60)) go(1) else if (dx > dp(60)) go(-1)
                    }
                }
                true
            }
            next.setOnClickListener { if (page < pages.size - 1) go(1) else { dismiss(); startLiveTutorial() } }
            render()
            box.addView(pager)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.END
            lp.topMargin = dp(16)
            box.addView(next, lp)
            box
        }
    }
}
