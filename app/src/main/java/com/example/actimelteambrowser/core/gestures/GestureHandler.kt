package com.example.actimelteambrowser.core.gestures

import android.annotation.SuppressLint
import com.example.actimelteambrowser.quiet
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.actimelteambrowser.core.settings.AppSettings
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Odezva po úspěšně dokončeném gestu podle nastavení: drobné zabrnění a/nebo zvuk (orb.mp3).
 * Vibrace jde přímo přes vibrátor (funguje i když má telefon vypnutou systémovou vibraci při dotyku).
 */
@Suppress("DEPRECATION")
fun gestureTick(context: Context) {
    AppSettings.init(context)
    if (AppSettings.gestureSound.get()) playGestureSound(context)
    if (!AppSettings.gestureHaptic.get() || !AppSettings.vibrationOn.get()) return
    quiet {
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
@SuppressLint("DiscouragedApi")   // nazev zvuku je dynamicky (res/raw/<name>)
fun playRawSound(context: Context, name: String) {
    AppSettings.init(context)
    if (!AppSettings.soundsOn.get()) return   // zvuky vypnute v Konfiguraci
    quiet {
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
    }
}
