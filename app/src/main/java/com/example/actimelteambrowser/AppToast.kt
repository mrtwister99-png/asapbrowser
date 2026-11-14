package com.example.actimelteambrowser

import android.annotation.SuppressLint
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import android.widget.Toast
import android.view.WindowManager
import android.view.Window
import android.view.MotionEvent
import android.app.Dialog
import androidx.core.graphics.toColorInt
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.actimelteambrowser.core.settings.AppSettings

/**
 * Jednotny toast cele aplikace: seda dlazdice (ikona + text) vpravo nahore.
 * Vyjede zprava (zacina mimo obrazovku) a zastavi se tak, ze je videt 70 % sirky obrazovky (levy okraj na 30 %).
 * Nahrazuje vsechny Toast.makeText. Informacni zpravy jde vypnout v Nastaveni -> Konfigurace, chyby se ukazuji vzdy.
 */
@SuppressLint("StaticFieldLeak")   // current / currentDialog se drzi jen po dobu zobrazeni toastu
object AppToast {
    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var currentDialog: Dialog? = null   // toast je ve vlastnim okne = vzdy nad dialogy a modaly
    private var hideRunnable: Runnable? = null

    /**
     * Barva toastu: zelena = pozitivni (ulozeno, pridano...), cervena = chyba nebo zavreni / smazani / vypnuti,
     * zluta = oznameni (upozorneni, tip), bila = uplne neutralni zprava.
     */
    enum class Kind(val icon: String, val color: String) {
        OK("✓", "#00B055"), ERROR("!", "#E53935"), CLOSE("✕", "#E53935"), INFO("i", "#FFD600"), NEUTRAL("•", "#FFFFFF")
    }

    // druh zpravy podle textu
    private fun kindOf(t: String): Kind {
        val s = t.lowercase()
        val err = listOf("nepoved", "nelze", "nejde", "neplatn", "selhal", "nenašla", "nepodařilo", "není co", "neznámé", "musí", "nemohou", "moc dlouhá", "zhroutila", "vyžaduje")
        val close = listOf("zavřen", "smazán", "smazan", "vypnut", "odebrán", "odstraněn", "zrušen", "zapomenut", "zamčen")
        val ok = listOf("uložen", "přidáno", "staženo", "zkopírov", "vytvořena", "vytvořen", "přidána", "připnuta", "odepnuta", "duplikována", "přesunuta", "vráceno", "zapnut", "odemčen", "hotov", "díky", "obnoven")
        val info = listOf("není kam", "zatím", "připravuje", "pamatuje", "podrž", "klepni", "tip", "pozor", "upozorn", "vráceno na", "chvíli")
        return when {
            err.any { s.contains(it) } -> Kind.ERROR
            close.any { s.contains(it) } -> Kind.CLOSE
            info.any { s.contains(it) } -> Kind.INFO
            ok.any { s.contains(it) } -> Kind.OK
            else -> Kind.NEUTRAL
        }
    }

    private fun activityOf(c: Context): Activity? {
        var x: Context? = c
        while (x is ContextWrapper) {
            if (x is Activity) return x
            x = x.baseContext
        }
        return null
    }

    @JvmStatic
    @JvmOverloads
    fun show(ctx: Context, text: String, long: Boolean = false) = show(ctx, text, long, null)

    /** Toast s pevne danou barvou (kind); null = podle textu. */
    fun show(ctx: Context, text: String, long: Boolean, kindOverride: Kind?) {
        val kind = kindOverride ?: kindOf(text)
        // vypnute informacni toasty: potlaci se vse krome chyb
        if (kind != Kind.ERROR && !AppSettings.infoToasts.get()) return
        val act = activityOf(ctx)
        if (act == null) {   // bez okna (napr. sluzba) = bezny systemovy toast
            Toast.makeText(ctx, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            return
        }
        val run = Runnable { build(act, text, kind, long) }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else handler.post(run)
    }

    /** Toast s tlacitkem (napr. "Vratit zpet"): drzi se dele a klepnuti na tlacitko ho zavre a spusti akci. */
    @JvmStatic
    fun showAction(ctx: Context, text: String, actionLabel: String, onAction: () -> Unit) {
        val act = activityOf(ctx) ?: run { show(ctx, text, true); return }
        val run = Runnable { build(act, text, kindOf(text), true, actionLabel, onAction) }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else handler.post(run)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(act: Activity, text: String, kind: Kind, long: Boolean, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        if (act.isFinishing || act.isDestroyed) return
        remove(true)
        val d = act.resources.displayMetrics.density
        val screenW = act.resources.displayMetrics.widthPixels

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        val bg = GradientDrawable()
        bg.setColor("#F0383838".toColorInt())   // seda
        bg.cornerRadius = 16 * d
        bg.setStroke((1.5f * d).toInt().coerceAtLeast(1), kind.color.toColorInt())   // obrys v barve druhu zpravy
        row.background = bg
        row.elevation = 30 * d
        row.isClickable = false

        val icon = TextView(act)
        icon.text = kind.icon
        icon.setTextColor(kind.color.toColorInt())
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        icon.setTypeface(icon.typeface, Typeface.BOLD)
        icon.gravity = Gravity.CENTER
        row.addView(icon, LinearLayout.LayoutParams((26 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))

        val tv = TextView(act)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.maxLines = 4
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.marginStart = (8 * d).toInt()
        row.addView(tv, tlp)
        if (actionLabel != null && onAction != null) {
            val ab = TextView(act)
            ab.text = actionLabel
            ab.setTextColor("#FFD600".toColorInt())
            ab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            ab.setTypeface(ab.typeface, Typeface.BOLD)
            ab.setPadding((12 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
            ab.setOnClickListener { remove(false); onAction() }
            row.addView(ab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val w = (screenW * 0.70f).toInt()   // 30 % -> 100 % sirky = 70 % obrazovky
        // vlastni okno (Dialog) bez ztmaveni a bez fokusu: lezi nad aplikaci i nad otevrenymi dialogy; mimo toast dotyky prochazeji
        val inset = ViewCompat.getRootWindowInsets(act.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: (24 * d).toInt()
        val dlg = Dialog(act)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(row, ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        val win = dlg.window ?: return
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        win.setGravity(Gravity.TOP or Gravity.END)
        val wp = win.attributes
        wp.width = w
        wp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        // v prohlizeci: pod horni listou karet (nebo pod hornim polem, kdyz lista karet neni)
        wp.y = (if (act is MainActivity) act.toastTopY() else null) ?: (inset + (8 * d).toInt())
        wp.windowAnimations = 0
        win.attributes = wp
        runCatching { dlg.show() }.onFailure { return }
        current = row
        currentDialog = dlg

        // zavreni prejetim prstu doprava (behem tahu se casovac zastavi)
        var downX = 0f
        row.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    hideRunnable?.let { handler.removeCallbacks(it) }
                    v.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> { v.translationX = (ev.rawX - downX).coerceAtLeast(0f); true }
                else -> {
                    if (v.translationX > 60 * d) remove(false)
                    else {
                        v.animate().translationX(0f).setDuration(150).start()
                        val r2 = Runnable { remove(false) }
                        hideRunnable = r2
                        handler.postDelayed(r2, 1500L)
                    }
                    true
                }
            }
        }
        row.translationX = w.toFloat()   // zacatek: uplne mimo obrazovku vpravo
        row.animate().translationX(0f).setDuration(260).start()
        val r = Runnable { remove(false) }
        hideRunnable = r
        handler.postDelayed(r, if (actionLabel != null) 5000L else if (long) 3500L else 2000L)
    }

    private fun closeDialog(d: Dialog?) {
        quiet { d?.dismiss() }
    }

    private fun remove(immediately: Boolean) {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
        val v = current ?: return
        val dlg = currentDialog
        current = null
        currentDialog = null
        if (immediately || dlg == null) { closeDialog(dlg); return }
        v.animate().translationX(v.width.toFloat()).setDuration(220)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { closeDialog(dlg) }
            }).start()
    }
}
