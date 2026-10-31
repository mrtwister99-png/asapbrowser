package com.example.actimelteambrowser.core.gestures

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.actimelteambrowser.core.settings.AppSettings
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.GestureDetector
import android.view.MotionEvent

class GestureHandler(
    private val onSwipeBack: () -> Unit,
    private val onSwipeForward: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (e1 == null) return false
        val diffX = e2.x - e1.x
        if (diffX > 150) onSwipeBack()
        if (diffX < -150) onSwipeForward()
        return true
    }
}

/**
 * Odezva po úspěšně dokončeném gestu podle nastavení: drobné zabrnění a/nebo zvuk (orb.mp3).
 * Vibrace jde přímo přes vibrátor (funguje i když má telefon vypnutou systémovou vibraci při dotyku).
 */
@Suppress("DEPRECATION")
fun gestureTick(context: Context) {
    AppSettings.init(context)
    if (AppSettings.gestureSound.get()) playGestureSound(context)
    if (!AppSettings.gestureHaptic.get() || !AppSettings.vibrationOn.get()) return
    try {
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib == null || !vib.hasVibrator()) return
        when {
            Build.VERSION.SDK_INT >= 29 -> vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            Build.VERSION.SDK_INT >= 26 -> vib.vibrate(VibrationEffect.createOneShot(20, 100))
            else -> vib.vibrate(20)
        }
    } catch (e: Exception) {
        // vibrace je jen bonus, nesmí nikdy shodit appku
    }
}

/**
 * Zvuk úspěšného gesta: soubor res/raw/orb.mp3.
 * Když soubor v projektu chybí, použije se systémové kliknutí (appka se i tak sestaví).
 */
private fun playGestureSound(context: Context) = playRawSound(context, "orb")

/**
 * Prehraje zvuk z res/raw/<name>.mp3 (kratky, jednorazovy). Kdyz soubor chybi, pouzije se systemove kliknuti.
 */
fun playRawSound(context: Context, name: String) {
    AppSettings.init(context)
    if (!AppSettings.soundsOn.get()) return   // zvuky vypnute v Konfiguraci
    try {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resId == 0) {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            return
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val mp = MediaPlayer.create(context.applicationContext, resId, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE) ?: return
        mp.setOnCompletionListener { it.release() }
        mp.setOnErrorListener { player, _, _ -> player.release(); true }
        mp.start()
    } catch (e: Exception) {
        // zvuk je jen bonus, nesmí nikdy shodit appku
    }
}
