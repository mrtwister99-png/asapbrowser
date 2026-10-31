package com.example.actimelteambrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Uvodni animace: bile pozadi, sede logo (drawable-nodpi/sede_logo.png) se pomalu zviditelni,
 * pak 2x "glitch" do plneho loga (asap_logo_full.png) a zpet, nakonec zustane plne logo.
 * Casovani je v GLITCHES (od ms, do ms, zdroj, cil; 0 = sede, 1 = plne) a DURATION_MS.
 * Kdyz sede_logo.png v projektu chybi, pouzije se plne logo (appka se i tak sestavi a spusti).
 */
class IntroView(context: Context) : View(context) {
    private val startNs = System.nanoTime()
    private var running = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ghostRed = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.rgb(255, 70, 90), PorterDuff.Mode.MULTIPLY)
    }
    private val ghostCyan = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.rgb(60, 200, 255), PorterDuff.Mode.MULTIPLY)
    }
    private val full: Bitmap = load("asap_logo_full") ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val sede: Bitmap = load("sede_logo") ?: full
    private val pad = 32f * resources.displayMetrics.density   // okraj kolem loga (stejny jako driv na splashi)

    private fun load(name: String): Bitmap? = try {
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) null else BitmapFactory.decodeResource(resources, id)
    } catch (e: Exception) {
        null
    }

    fun stop() { running = false }

    // Logo vepsane na stred obrazovky s okrajem, pomer stran zustava
    private fun fit(b: Bitmap): RectF {
        val aw = (width - 2 * pad).coerceAtLeast(1f)
        val ah = (height - 2 * pad).coerceAtLeast(1f)
        val s = minOf(aw / b.width, ah / b.height)
        val w = b.width * s
        val h = b.height * s
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        return RectF(l, t, l + w, t + h)
    }

    private fun pick(which: Int): Bitmap = if (which == 0) sede else full

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - startNs) / 1_000_000L
        canvas.drawColor(Color.WHITE)

        // ktery obrazek je "zakladni" v tomhle case (po dokonceni kazdeho glitche je to jeho cil)
        var base = 0
        var active: LongArray? = null
        var activeIdx = 0
        for ((i, g) in GLITCHES.withIndex()) {
            if (t >= g[1]) base = g[3].toInt()
            else if (t >= g[0]) { active = g; activeIdx = i }
        }

        if (active == null) {
            // klid: plne logo, nebo sede logo (na zacatku se pomalu zviditelni)
            val fade = if (t < FADE_IN_MS) {
                val x = t.toFloat() / FADE_IN_MS
                x * x * (3f - 2f * x)   // plynuly start i konec
            } else 1f
            paint.alpha = (255 * fade).toInt()
            val b = pick(base)
            canvas.drawBitmap(b, null, fit(b), paint)
        } else {
            drawGlitch(canvas, t, active, activeIdx)
        }
        if (running) postInvalidateOnAnimation()
    }

    // Glitch: obrazek je rozdeleny na vodorovne pasy; kazdy pas se posune do strany a ukazuje bud puvodni,
    // nebo cilovy obrazek (s postupem je cilovych pasu vic). K tomu cervene a modre "duchy" okolo.
    private fun drawGlitch(canvas: Canvas, t: Long, g: LongArray, idx: Int) {
        val p = ((t - g[0]).toFloat() / (g[1] - g[0])).coerceIn(0f, 1f)
        val strength = sin(PI * p).toFloat()               // 0 -> 1 -> 0
        val frame = (t / 45L).toInt()                      // pasy se prehazuji asi 22x za sekundu
        val rnd = java.util.Random(frame * 7919L + idx * 104729L)
        val from = pick(g[2].toInt())
        val to = pick(g[3].toInt())
        val bands = 11
        // nahodne vysky pasu, dohromady cela vyska obrazovky
        val weights = FloatArray(bands) { 0.4f + rnd.nextFloat() }
        val sum = weights.sum()
        var y = 0f
        for (i in 0 until bands) {
            val h = height * weights[i] / sum
            val showTarget = p > 0.9f || rnd.nextFloat() < p * 1.15f
            val b = if (showTarget) to else from
            val dx = (rnd.nextFloat() * 2f - 1f) * strength * width * 0.07f
            val ghost = strength * width * 0.018f
            val dst = fit(b)
            canvas.save()
            canvas.clipRect(0f, y, width.toFloat(), y + h + 1f)
            if (strength > 0.05f) {
                ghostRed.alpha = (150 * strength).toInt()
                ghostCyan.alpha = (150 * strength).toInt()
                canvas.save(); canvas.translate(dx - ghost, 0f); canvas.drawBitmap(b, null, dst, ghostRed); canvas.restore()
                canvas.save(); canvas.translate(dx + ghost, 0f); canvas.drawBitmap(b, null, dst, ghostCyan); canvas.restore()
            }
            paint.alpha = 255
            canvas.translate(dx, 0f)
            canvas.drawBitmap(b, null, dst, paint)
            canvas.restore()
            y += h
        }
    }

    companion object {
        const val DURATION_MS = 5000L   // po tomhle case je animace hotova (zustava plne logo)
        private const val FADE_IN_MS = 1300L
        // od ms, do ms, zdroj, cil (0 = sede logo, 1 = plne logo)
        private val GLITCHES = arrayOf(
            longArrayOf(1800, 2250, 0, 1),
            longArrayOf(3000, 3450, 1, 0),
            longArrayOf(4000, 4450, 0, 1)
        )
    }
}
