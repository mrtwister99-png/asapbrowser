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

// Sjednocené dialogy appky (rozšíření MainActivity).
internal fun MainActivity.styledButton(text: String, primary: Boolean): TextView {
    val b = TextView(this)
    b.text = text
    b.gravity = Gravity.CENTER
    b.setPadding(dp(18), dp(10), dp(18), dp(10))
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    b.setTypeface(b.typeface, Typeface.BOLD)
    b.setTextColor(if (primary) Color.parseColor("#06281D") else Color.parseColor("#CCCCCC"))
    val bg = GradientDrawable()
    bg.cornerRadius = dp(14).toFloat()
    bg.setColor(if (primary) Color.parseColor("#00B055") else Color.parseColor("#2A2A2A"))
    b.background = bg
    return b
}

// Tmavy zaoblený panel jako zbytek appky; "vyskoci" (zvetseni + prolnuti) pri otevreni.
// onPositive vraci true = dialog se zavre. bodyBuilder dostane funkci pro zavreni dialogu.
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
    card.orientation = LinearLayout.VERTICAL
    card.setPadding(dp(22), dp(20), dp(22), dp(16))
    val cardBg = GradientDrawable()
    cardBg.setColor(Color.parseColor("#1E1E1E"))
    cardBg.cornerRadius = dp(24).toFloat()
    cardBg.setStroke(dp(1), Color.parseColor("#2A2A2A"))
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
    l.setTextColor(Color.parseColor("#888888"))
    l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
    l.setTypeface(l.typeface, Typeface.BOLD)
    return l
}
