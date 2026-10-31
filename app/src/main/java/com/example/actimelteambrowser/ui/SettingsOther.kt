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

// Nastavení → Konfigurace (animace, zvuky, vibrace, vyhledávač, úklid).

/** Konfigurace: zbyla nastaveni na jednom miste (animace, zvuky, vibrace, vyhledavac, skryvani listy, uklid). */
@Composable
internal fun OtherSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var animations by remember { mutableStateOf(AppSettings.animationsOn.get()) }
    var intro by remember { mutableStateOf(AppSettings.introOn.get()) }
    var sounds by remember { mutableStateOf(AppSettings.soundsOn.get()) }
    var vibration by remember { mutableStateOf(AppSettings.vibrationOn.get()) }
    var hideBars by remember { mutableStateOf(AppSettings.hideBarsOnScroll.get()) }
    var thumbsOn by remember { mutableStateOf(AppSettings.thumbsOn.get()) }
    var sizeTick by remember { mutableStateOf(0) }
    // misto, ktere zabiraji ulozene nahledy a ikony webu
    val usage by produceState("Počítám…", sizeTick) {
        value = withContext(Dispatchers.IO) {
            var bytes = 0L
            var count = 0
            for (d in listOf("site_thumbs", "site_icons")) {
                java.io.File(ctx.filesDir, d).listFiles()?.forEach { bytes += it.length(); count++ }
            }
            val size = if (bytes < 1024L * 1024) "%d kB".format(bytes / 1024) else "%.1f MB".format(java.util.Locale("cs"), bytes / 1048576.0)
            "Uloženo teď: $size ($count souborů)"
        }
    }
    var engine by remember { mutableStateOf(SearchEngines.current().key) }
    var clearStep by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Konfigurace", onBack = onBack) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ANIMACE", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            SwitchRow("Animace tlačítek", "Probliknutí, otočení, hvězdička, kolečko → logo", animations) {
                animations = it; AppSettings.animationsOn.set(it)
            }
            SwitchRow("Úvodní animace při spuštění", "Logo s hudbou při startu aplikace", intro) {
                intro = it; AppSettings.introOn.set(it)
            }

            Text("ZVUKY A VIBRACE", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            SwitchRow("Zvuky", "Gesta, screenshot", sounds) { sounds = it; AppSettings.soundsOn.set(it) }
            SwitchRow("Vibrace", "Odezva při držení tlačítek a po gestu", vibration) { vibration = it; AppSettings.vibrationOn.set(it) }

            Text("PROCHÁZENÍ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            SwitchRow("Skrývat lišty při posouvání", "Horní, dolní lišta i boční tlačítka se schovají, když čteš", hideBars) {
                hideBars = it; AppSettings.hideBarsOnScroll.set(it)
            }

            SwitchRow("Ukládat náhledy a ikony webů", usage, thumbsOn) {
                thumbsOn = it; AppSettings.thumbsOn.set(it)
            }
            Text("Vypnuto: nové náhledy se neukládají a nezobrazují (nová karta, Historie, Oblíbené).", color = MUTED, fontSize = 12.sp)

            Text("VYHLEDÁVAČ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            for (e in SearchEngines.list) {
                val on = engine == e.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color(0xFF14382A) else PANEL)
                        .then(if (on) Modifier.border(2.dp, ACCENT, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { engine = e.key; AppSettings.searchEngine.set(e.key) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(e.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (on) Text("✓", color = ACCENT, fontSize = 16.sp)
                }
            }

            Text("ÚKLID", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable { clearStep = true },
                contentAlignment = Alignment.Center
            ) { Text("Smazat uložené náhledy a ikony webů", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable {
                    AppSettings.desktopSites.set("")
                    android.widget.Toast.makeText(ctx, "Weby s verzí pro počítač zapomenuty", android.widget.Toast.LENGTH_SHORT).show()
                },
                contentAlignment = Alignment.Center
            ) { Text("Zapomenout weby s verzí pro počítač", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (clearStep) {
        StyledDialog(
            onDismissRequest = { clearStep = false },
            title = { Text("Smazat náhledy?") },
            text = { Text("Smažou se náhledy stránek a ikony webů z nové karty, Historie a Oblíbených. Znovu se uloží při dalších návštěvách.") },
            confirmButton = {
                TextButton(onClick = {
                    clearStep = false
                    java.io.File(ctx.filesDir, "site_thumbs").deleteRecursively()
                    java.io.File(ctx.filesDir, "site_icons").deleteRecursively()
                    sizeTick++
                    android.widget.Toast.makeText(ctx, "Náhledy a ikony smazány", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Smazat", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { clearStep = false }) { Text("Zrušit") } }
        )
    }
}
