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

// Kresli hvezdu z ikony Oblibene (vyplnovana od stredu) a jiskricky s malymi hvezdickami kolem ni
internal class StarBurstView(
    ctx: android.content.Context,
    private val cx: Float,
    private val cy: Float,
    private val size: Float
) : View(ctx) {
    var fill = 0f    // 0..1: kolik hvezdy je vyplnene zlute (od stredu)
    var burst = 0f   // 0..1: jiskricky a hvezdicky odletaji

    private val gold = Color.parseColor("#FFD400")
    private val star: android.graphics.Path
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }

    init {
        isClickable = false
        // stejna hvezda jako v ikone ic_bookmarks (viewport 24x24), vycentrovana kolem (0,0)
        val p = androidx.core.graphics.PathParser.createPathFromPathData(
            "M12.92 5.15L14.42 8.66A0.7 0.7 0 0 0 15 9.08L18.8 9.42A1 1 0 0 1 19.37 11.17L16.5 13.68A0.7 0.7 0 0 0 16.28 14.37L17.12 18.09A1 1 0 0 1 15.64 19.17L12.36 17.21A0.7 0.7 0 0 0 11.64 17.21L8.36 19.17A1 1 0 0 1 6.88 18.09L7.72 14.37A0.7 0.7 0 0 0 7.5 13.68L4.63 11.17A1 1 0 0 1 5.2 9.42L9 9.08A0.7 0.7 0 0 0 9.58 8.66L11.08 5.15A1 1 0 0 1 12.92 5.15Z"
        )
        val m = android.graphics.Matrix()
        m.postTranslate(-12f, -12.2f)
        m.postScale(size / 24f, size / 24f)
        p.transform(m)
        star = p
        outline.strokeWidth = size * 0.8f / 24f
    }

    // pravidelna petihrota hvezdicka o polomeru r kolem (0,0)
    private fun smallStar(r: Float): android.graphics.Path {
        val p = android.graphics.Path()
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) r else r * 0.45f
            val a = Math.toRadians(-90.0 + k * 36.0)
            val x = (kotlin.math.cos(a) * rad).toFloat()
            val y = (kotlin.math.sin(a) * rad).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(cx, cy)
        // vyplneni: kruh rostouci od stredu, oriznuty tvarem hvezdy
        if (fill > 0f) {
            canvas.save()
            canvas.clipPath(star)
            canvas.drawCircle(0f, 0f, size * 0.42f * fill, fillPaint)
            canvas.restore()
        }
        canvas.drawPath(star, outline)
        if (burst > 0f) {
            val fade = (1f - burst).coerceIn(0f, 1f)
            // jiskricky: kratke carky odletaji do vsech stran
            linePaint.strokeWidth = size * 0.07f
            linePaint.alpha = (255 * fade).toInt()
            for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0 + 8.0)
                val d0 = size * (0.45f + 0.55f * burst)
                val d1 = d0 + size * 0.22f * fade
                canvas.drawLine(
                    (kotlin.math.cos(a) * d0).toFloat(), (kotlin.math.sin(a) * d0).toFloat(),
                    (kotlin.math.cos(a) * d1).toFloat(), (kotlin.math.sin(a) * d1).toFloat(), linePaint
                )
            }
            // male hvezdicky: leti dal a otaci se
            fillPaint.alpha = (255 * fade).toInt()
            for (k in 0 until 6) {
                val a = Math.toRadians(k * 60.0 + 30.0)
                val d = size * (0.5f + 1.1f * burst)
                canvas.save()
                canvas.translate((kotlin.math.cos(a) * d).toFloat(), (kotlin.math.sin(a) * d).toFloat())
                canvas.rotate(burst * 120f)
                canvas.drawPath(smallStar(size * (0.17f - 0.06f * burst)), fillPaint)
                canvas.restore()
            }
            fillPaint.alpha = 255
        }
        canvas.restore()
    }
}
