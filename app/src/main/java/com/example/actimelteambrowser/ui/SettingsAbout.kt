package com.example.actimelteambrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.actimelteambrowser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.actimelteambrowser.core.settings.GestureDef
import com.example.actimelteambrowser.core.gestures.gestureTick
import com.example.actimelteambrowser.core.settings.Gestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.actimelteambrowser.core.settings.SearchEngines
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.ButtonDefaults
import com.example.actimelteambrowser.core.settings.ButtonFunction
import com.example.actimelteambrowser.data.BookmarkFolder
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.data.BookmarksRepository
import com.example.actimelteambrowser.data.FolderWithCount
import com.example.actimelteambrowser.data.HistoryRepository
import com.example.actimelteambrowser.data.HistoryVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Barvy ve stejném duchu jako lišty v appce (kopie ze SettingsScreens.kt, v každém souboru soukromé)
// Barvy ve stejném duchu jako lišty v appce
private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF00B055)
private val MUTED = Color(0xFF888888)
private val DANGER = Color(0xFFB91C1C)

// Nastavení → O aplikaci.

// ---------------------------------------------------------------- O APLIKACI

// Verze z manifestu/gradle (versionName a versionCode)
@Suppress("DEPRECATION")
internal fun appVersionText(ctx: android.content.Context): String = try {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    "Verze ${pi.versionName} (build $code)"
} catch (e: Exception) {
    "Verze neznámá"
}

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { appVersionText(ctx) }
    // hudba bez_zastaveni_full.mp3 hraje po dobu, co je obrazovka otevrena; po odchodu se zastavi (prepinac zvuku se nekontroluje).
    // Vpravo nahore cas prehravani a tlacitko pauza / prehrat.
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0) }
    var durMs by remember { mutableStateOf(0) }
    DisposableEffect(Unit) {
        var mp: MediaPlayer? = null
        try {
            @Suppress("DiscouragedApi")
            val resId = ctx.resources.getIdentifier("bez_zastaveni_full", "raw", ctx.packageName)
            if (resId != 0) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                mp = MediaPlayer.create(ctx.applicationContext, resId, attrs, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
                mp?.setOnCompletionListener { playing = false; posMs = 0; runCatching { it.seekTo(0) } }
                mp?.start()
                durMs = mp?.duration ?: 0
                playing = mp != null
                player = mp
            }
        } catch (e: Exception) {
            android.util.Log.d("about", e.toString())   // hudba je jen bonus, nesmi shodit obrazovku
        }
        onDispose {
            player = null
            try { mp?.stop() } catch (_: Exception) { }
            mp?.release()
            mp = null
        }
    }
    // casovac: obnovuje se 2x za sekundu, jen kdyz hraje
    LaunchedEffect(playing) {
        while (playing) {
            posMs = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            kotlinx.coroutines.delay(500)
        }
    }
    fun mmss(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    val musicControl: (@Composable () -> Unit)? = if (player == null) null else ({
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xD92A2A2A)).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(mmss(posMs) + (if (durMs > 0) " / " + mmss(durMs) else ""), color = Color(0xFFDDDDDD), fontSize = 13.sp)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable {
                    val mp = player ?: return@clickable
                    runCatching { if (playing) mp.pause() else mp.start() }
                    playing = runCatching { mp.isPlaying }.getOrDefault(false)
                },
                contentAlignment = Alignment.Center
            ) {
                Text(if (playing) "❚❚" else "▶", color = Color(0xFF00B055), fontSize = if (playing) 14.sp else 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    })
    // pozadi UFO Field pod celou obrazovkou, obsah je pres nej (karty jsou polopruhledne)
    UfoBackground {
    ScreenScaffold(title = "O aplikaci", onBack = onBack, containerColor = Color.Transparent, trailing = musicControl) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.asap_logo), contentDescription = null, modifier = Modifier.width(150.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(10.dp))
                Text("ASAP Browser", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(version, color = MUTED, fontSize = 13.sp)
            }
            Text(
                "Rychlý a příjemný prohlížeč s jednoduchou, ale propracovanou konfigurací: blokování reklam, gesta a ovládání, které si celé přizpůsobíš.",
                color = Color(0xFFDDDDDD), fontSize = 14.sp, lineHeight = 20.sp
            )
            AboutCard(
                "Co umí", listOf(
                    "Blokování reklam (i na YouTube) a automatické odmítání cookie lišt",
                    "Gesta: kódy kreslené prstem po ploše 3×3",
                    "Tlačítka s vlastními funkcemi pro klepnutí a držení",
                    "Karty ve složkách a anonymní karty",
                    "Záložky, historie a stahování",
                    "Screenshoty včetně celé stránky",
                    "Export a import celého nastavení"
                )
            )
            AboutCard("Připravuje se", listOf("VPN", "Profily (oddělená nastavení, záložky a přihlášení)"))
            AboutCard(
                "Soukromí", listOf(
                    "Bez účtu a bez sledování používání aplikace",
                    "Anonymní karty neukládají historii ani náhledy a jejich cookies se po zavření poslední anonymní karty smažou"
                )
            )
            AboutCard(
                "Použité součásti", listOf(
                    "Android WebView (Chromium) jako prohlížecí jádro",
                    "EasyList a EasyPrivacy: seznamy filtrů pro blokování reklam (easylist.to)",
                    "ZXing: tvorba QR kódů (Apache License 2.0)",
                    "AndroidX, Jetpack Compose a Material Components (Apache License 2.0)",
                    "Vlastní jádro v Rustu pro rychlé filtrování požadavků"
                )
            )
            Spacer(Modifier.height(16.dp))
        }
    }
    }
}

@Composable
private fun AboutCard(title: String, lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xD91E1E1E))   // polopruhledne, aby bylo videt pozadi
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        lines.forEach { Text("•  $it", color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 19.sp) }
    }
}
