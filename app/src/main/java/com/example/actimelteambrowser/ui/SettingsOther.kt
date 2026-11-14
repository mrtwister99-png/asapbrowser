package com.example.actimelteambrowser.ui

import com.example.actimelteambrowser.AppToast
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


// Nastavení → Konfigurace (animace, zvuky, vibrace, vyhledávač, úklid).

/** Konfigurace: zbyla nastaveni na jednom miste (animace, zvuky, vibrace, vyhledavac, skryvani listy, uklid). */
@Composable
internal fun OtherSettingsScreen(section: String, isDefaultBrowser: Boolean, onDefaultBrowser: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var animations by remember { mutableStateOf(AppSettings.animationsOn.get()) }
    var tabAnim by remember { mutableStateOf(AppSettings.tabAnimOn.get()) }
    var gestureFx by remember { mutableStateOf(AppSettings.gestureFxOn.get()) }
    var infoToasts by remember { mutableStateOf(AppSettings.infoToasts.get()) }
    var stripBottom by remember { mutableStateOf(AppSettings.tabStripBottom.get()) }
    var stripTop by remember { mutableStateOf(AppSettings.tabStripTop.get()) }
    var intro by remember { mutableStateOf(AppSettings.introOn.get()) }
    var sounds by remember { mutableStateOf(AppSettings.soundsOn.get()) }
    var gestureSound by remember { mutableStateOf(AppSettings.gestureSound.get()) }
    var shotSound by remember { mutableStateOf(AppSettings.screenshotSound.get()) }
    var vibration by remember { mutableStateOf(AppSettings.vibrationOn.get()) }
    var hideBars by remember { mutableStateOf(AppSettings.hideBarsOnScroll.get()) }
    var pullOn by remember { mutableStateOf(AppSettings.pullRefreshOn.get()) }
    var pullDist by remember { mutableStateOf(AppSettings.pullRefreshDistance.get().coerceIn(60, 240)) }
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
            val size = if (bytes < 1024L * 1024) "%d kB".format(bytes / 1024) else "%.1f MB".format(java.util.Locale.forLanguageTag("cs"), bytes / 1048576.0)
            "Uloženo teď: $size ($count souborů)"
        }
    }
    var engine by remember { mutableStateOf(SearchEngines.current().key) }
    var clearStep by remember { mutableStateOf(false) }
    var restoreTabs by remember { mutableStateOf(AppSettings.startupRestoreTabs.get()) }
    var startFav by remember { mutableStateOf(AppSettings.startShowFavorites.get()) }
    var home by remember { mutableStateOf(AppSettings.homeUrl.get()) }
    val title = when (section) {
        "home" -> "Domovská stránka"; "search" -> "Vyhledávač"; "tabs" -> "Záložky"; "strip" -> "Lišta záložek"
        "anim" -> "Animace"; "sounds" -> "Zvuky"; "vibra" -> "Vibrace"; else -> "Nastavení"
    }

    ScreenScaffold(title = title, onBack = onBack) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (section == "home") {
                // domovska stranka: adresa (uklada se po kazde zmene, kdyz je platna)
                OutlinedTextField(
                    value = home,
                    onValueChange = {
                        home = it.take(300)
                        var u = home.trim()
                        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                        if (u.substringAfter("://").substringBefore('/').contains('.')) AppSettings.homeUrl.set(u)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Adresa domovské stránky") }
                )
                SettingsRow(
                    "Výchozí prohlížeč",
                    if (isDefaultBrowser) "ASAP Browser je výchozí prohlížeč. Odkazy z jiných aplikací se otevírají tady." else "Nastavit ASAP Browser jako výchozí, odkazy z jiných aplikací se pak otevřou tady",
                    onDefaultBrowser
                )
            }
            if (section == "tabs") {
                SwitchRow("Po spuštění obnovit poslední karty", "Vypnuto: otevře se jedna nová karta s domovskou stránkou", restoreTabs) {
                    restoreTabs = it; AppSettings.startupRestoreTabs.set(it)
                }
                SwitchRow("Nová karta: Oblíbené", "Na nové kartě ukazovat Oblíbené místo nejnavštěvovanějších stránek", startFav) {
                    startFav = it; AppSettings.startShowFavorites.set(it)
                }
            }
            if (section == "anim") {
            SwitchRow("Animace tlačítek", "Probliknutí, otočení, hvězdička, kolečko → logo", animations) {
                animations = it; AppSettings.animationsOn.set(it)
            }
            SwitchRow("Animace karet", "Nová karta (+), zavření karty (−, vtažení dolů), přejetí na vedlejší kartu", tabAnim) {
                tabAnim = it; AppSettings.tabAnimOn.set(it)
            }
            SwitchRow("Efekt gest", "Efekt při průchodu klávesou a ohňostroj po úspěšném gestu", gestureFx) {
                gestureFx = it; AppSettings.gestureFxOn.set(it)
            }
            SwitchRow("Úvodní animace při spuštění", "Logo s hudbou při startu aplikace", intro) {
                intro = it; AppSettings.introOn.set(it)
            }

            SwitchRow("Zobrazovat informační toasty", "Krátké zprávy vpravo nahoře (uloženo, přidáno…). Chyby se ukazují vždy", infoToasts) {
                infoToasts = it; AppSettings.infoToasts.set(it)
            }
            }

            if (section == "strip") {
            SwitchRow("Spodní lišta karet", "Barevné dlaždice s ikonami webů nad spodním polem (první 5 karet)", stripBottom) {
                stripBottom = it; AppSettings.tabStripBottom.set(it)
            }
            SwitchRow("Horní lišta karet", "Dlaždice karet pod horním polem (karty od šesté). Jde mít jen jednu z lišt", stripTop) {
                stripTop = it; AppSettings.tabStripTop.set(it)
            }
            SwitchRow("Skrývat lišty při posouvání", "Horní, dolní lišta i boční tlačítka se schovají, když čteš", hideBars) {
                hideBars = it; AppSettings.hideBarsOnScroll.set(it)
            }
            }

            if (section == "sounds") {
                SwitchRow("Gesta", "Zvuk po dokončeném gestu", gestureSound) { gestureSound = it; AppSettings.gestureSound.set(it) }
                SwitchRow("Screenshot", "Zvuk po vyfocení obrazovky nebo stránky", shotSound) { shotSound = it; AppSettings.screenshotSound.set(it) }
                SwitchRow("Úvodní animace", "Hudba při spuštění aplikace", sounds) { sounds = it; AppSettings.soundsOn.set(it) }
            }
            if (section == "vibra") SwitchRow("Vibrace", "Odezva při držení tlačítek, po gestu, nová karta, uložení do Oblíbených", vibration) { vibration = it; AppSettings.vibrationOn.set(it) }

            if (section == "home") {
            SwitchRow("Obnovit stránku tažením dolů", "Na vrcholu stránky přetáhni prstem dolů, kroužek se zaplní a stránka se načte znovu", pullOn) {
                pullOn = it; AppSettings.pullRefreshOn.set(it)
            }
            if (pullOn) {
                Text("CITLIVOST TAŽENÍ: $pullDist dp (méně = citlivější)", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = pullDist.toFloat(),
                    onValueChange = { pullDist = it.toInt() },
                    valueRange = 60f..240f,
                    onValueChangeFinished = { AppSettings.pullRefreshDistance.set(pullDist) }
                )
                // nahled: prst (tecka) se tahne dolu o nastavenou vzdalenost az ke kruzku
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Canvas(Modifier.width(60.dp).height((pullDist + 32).dp)) {
                        val cx = size.width / 2f
                        val r = 20.dp.toPx()
                        val start = 6.dp.toPx()
                        val end = start + pullDist.dp.toPx()
                        drawCircle(Color.White, 4.dp.toPx(), Offset(cx, start))
                        drawLine(ACCENT, Offset(cx, start), Offset(cx, end), 2.dp.toPx(), StrokeCap.Round)
                        drawCircle(Color(0xFF2A2A2A), r, Offset(cx, end))
                        drawCircle(ACCENT, r * 0.62f, Offset(cx, end), style = Stroke(width = 3.dp.toPx()))
                    }
                    Text(
                        "Tak daleko ($pullDist dp) přetáhneš prst dolů, než se kroužek zaplní a stránka se načte znovu.",
                        color = MUTED, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable {
                    AppSettings.desktopSites.set("")
                    AppToast.show(ctx, "Weby s verzí pro počítač zapomenuty")
                },
                contentAlignment = Alignment.Center
            ) { Text("Zapomenout weby s verzí pro počítač", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            if (section == "tabs") {
            SwitchRow("Ukládat náhledy a ikony webů", usage, thumbsOn) {
                thumbsOn = it; AppSettings.thumbsOn.set(it)
            }
            Text("Vypnuto: nové náhledy se neukládají a nezobrazují (nová karta, Historie, Oblíbené).", color = MUTED, fontSize = 12.sp)
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable { clearStep = true },
                contentAlignment = Alignment.Center
            ) { Text("Smazat uložené náhledy a ikony webů", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }

            if (section == "search") for (e in SearchEngines.list) {
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
                    AppToast.show(ctx, "Náhledy a ikony smazány")
                }) { Text("Smazat", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { clearStep = false }) { Text("Zrušit") } }
        )
    }
}
