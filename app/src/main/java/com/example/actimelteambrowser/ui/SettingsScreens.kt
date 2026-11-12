package com.example.actimelteambrowser.ui

import com.example.actimelteambrowser.demoMark
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
import com.example.actimelteambrowser.core.vault.VaultManager
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

// Kořen Nastavení: trasy, SettingsApp, hlavní seznam, společné prvky (dialog, záhlaví, řádky).

// Barvy ve stejném duchu jako lišty v appce
private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF00B055)
private val MUTED = Color(0xFF888888)
private val DANGER = Color(0xFFB91C1C)

sealed class Route {
    object Main : Route()
    object Bookmarks : Route()
    // trail = nadrazene slozky (pro tlacitko zpet v podslozkach)
    data class Folder(val folder: BookmarkFolder, val trail: List<BookmarkFolder> = emptyList()) : Route()
    object History : Route()
    object Buttons : Route()
    object Gestures : Route()
    object Downloads : Route()
    object Other : Route()
    object Tutorials : Route()
    object About : Route()
    object Feedback : Route()
    object Vault : Route()
}

/** Kořen obrazovek Nastavení: Záložky, Historie, Export, Import. */
@Composable
fun SettingsApp(
    startRoute: Route,
    currentUrl: String?,
    currentTitle: String,
    onOpenUrl: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetAll: (Boolean) -> Unit,   // true = pred smazanim ulozit zalohu trezoru
    onButtonConfig: () -> Unit,
    onGestureConfig: () -> Unit,
    isDefaultBrowser: Boolean,
    onDefaultBrowser: () -> Unit,
    onClose: () -> Unit
) {
    var route by remember { mutableStateOf(startRoute) }

    // Zpět: z detailu složky do složek, ze složek/historie do nastavení, z nastavení pryč
    BackHandler(enabled = route != Route.Main) {
        val r = route
        // trezor otevreny z leve listy: zpet zavre Nastaveni a vrati do prohlizece
        if (r == Route.Vault && startRoute == Route.Vault) onClose() else route = if (r is Route.Folder) backFrom(r) else Route.Main
    }

    when (val r = route) {
        is Route.Main -> SettingsRoot(
            onBookmarks = { route = Route.Bookmarks },
            onHistory = { route = Route.History },
            onButtons = onButtonConfig,   // tlacitka se ted nastavuji primo na hlavni plose (konfiguracni mod)
            onGestures = onGestureConfig,   // gesta se nastavuji na hlavni plose (konfiguracni mod gest)
            onExport = onExport,
            onImport = onImport,
            onDownloads = { route = Route.Downloads },
            onOther = { route = Route.Other },
            onTutorials = { route = Route.Tutorials },
            onAbout = { route = Route.About },
            onVault = { route = Route.Vault },
            isDefaultBrowser = isDefaultBrowser,
            onDefaultBrowser = onDefaultBrowser,
            onResetAll = onResetAll,
            onBack = onClose
        )
        is Route.Bookmarks -> FolderListScreen(
            onOpenFolder = { route = Route.Folder(it, emptyList()) },
            onBack = { route = Route.Main }
        )
        is Route.Folder -> FolderDetailScreen(
            folder = r.folder,
            currentUrl = currentUrl,
            currentTitle = currentTitle,
            onOpenFolder = { sub -> route = Route.Folder(sub, r.trail + r.folder) },
            onOpenUrl = onOpenUrl,
            onBack = { route = backFrom(r) }
        )
        is Route.History -> HistoryScreen(
            onOpenUrl = onOpenUrl,
            onBack = { route = Route.Main }
        )
        is Route.Buttons -> ButtonsConfigScreen(onBack = { route = Route.Main })
        is Route.Gestures -> GesturesScreen(onBack = { route = Route.Main })
        is Route.Downloads -> DownloadsScreen(onBack = { route = Route.Main })
        is Route.Other -> OtherSettingsScreen(onBack = { route = Route.Main })
        is Route.Tutorials -> TutorialsScreen(onBack = { route = Route.Main })
        is Route.About -> AboutScreen(onBack = { route = Route.Main })
        is Route.Vault -> VaultScreen(onBack = { if (startRoute == Route.Vault) onClose() else route = Route.Main })
        // otevreno drzenim tlacitka Nastaveni: zpet zavre Nastaveni a vrati do prohlizece
        is Route.Feedback -> FeedbackScreen(onBack = { if (startRoute == Route.Feedback) onClose() else route = Route.Main })
    }
}

// Zpet z (pod)slozky: do nadrazene slozky, nebo do seznamu slozek
private fun backFrom(r: Route.Folder): Route =
    if (r.trail.isEmpty()) Route.Bookmarks else Route.Folder(r.trail.last(), r.trail.dropLast(1))

// Sjednoceny vzhled dialogu (tmavy panel, zaoblene rohy, zelene tlacitka)
@Composable
internal fun StyledDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = PANEL,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCCCCC),
        shape = RoundedCornerShape(24.dp)
    )
}

/** Jednotny potvrzovaci dialog (smazat skupinu, zavrit karty, smazat / obnovit tlacitko): titulek, text, Ne / Ano. */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    noLabel: String = "Ne",
    yesLabel: String = "Ano",
    yesColor: Color = Color(0xFFB91C1C),
    onNo: () -> Unit,
    onYes: () -> Unit
) {
    Dialog(onDismissRequest = onNo) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(PANEL).padding(22.dp)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(text, color = Color(0xFFCCCCCC), fontSize = 15.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                Text(
                    noLabel, color = Color(0xFFCCCCCC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(ROW).clickable(onClick = onNo)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
                Text(
                    yesLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(yesColor).clickable(onClick = onYes)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
internal fun ScreenScaffold(title: String, onBack: () -> Unit, containerColor: Color = BG, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(containerColor)
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .demoMark("back")   // sipka zpet (ukazka prvniho tutorialu na ni ukazuje)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ROW)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(painterResource(com.example.actimelteambrowser.R.drawable.ic_back_chevron), contentDescription = "Zpět", modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
internal fun BigButton(text: String, color: Color, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(16.dp)
            .then(modifier)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MUTED, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------- NASTAVENÍ

@Composable
private fun SettingsRoot(
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onButtons: () -> Unit,
    onGestures: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDownloads: () -> Unit,
    onOther: () -> Unit,
    onTutorials: () -> Unit,
    onAbout: () -> Unit,
    onVault: () -> Unit,
    isDefaultBrowser: Boolean,
    onDefaultBrowser: () -> Unit,
    onResetAll: (Boolean) -> Unit,   // true = pred smazanim ulozit zalohu trezoru
    onBack: () -> Unit
) {
    var resetStep by remember { mutableStateOf(0) }   // 0 = nic, 1 = prvni potvrzeni, 2 = druhe
    val resetContext = LocalContext.current
    val hasVault = remember { VaultManager.isCreated(resetContext) }
    var keepVault by remember { mutableStateOf(true) }   // zaloha trezoru pred smazanim
    ScreenScaffold(title = "Nastavení", onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),   // aby se vsechno vlezlo i na male displeje
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsRow("Záložky", "Složky a uložené stránky", onBookmarks)
            SettingsRow("Historie", "Navštívené stránky, mazání", onHistory)
            SettingsRow("Stahování", "Stažené a probíhající soubory", onDownloads)
            SettingsRow("Tlačítka", "Funkce tlačítek (klepnutí a držení), přidání bočních tlačítek", onButtons)
            SettingsRow("Gesta", "Plocha pro gesta, vzhled, velikost a kódy gest", onGestures)
            SettingsRow("Export", "Uložit nastavení, záložky a historii do telefonu nebo poslat na e-mail", onExport)
            SettingsRow("Import", "Načíst nastavení ze souboru (nahradí záložky a historii)", onImport)
            SettingsRow(
                "Výchozí prohlížeč",
                if (isDefaultBrowser) "ASAP Browser je výchozí prohlížeč. Odkazy z jiných aplikací se otevírají tady." else "Nastavit ASAP Browser jako výchozí, odkazy z jiných aplikací se pak otevřou tady",
                onDefaultBrowser
            )
            SettingsRow("Tutoriály", "Krátké ukázky, jak jednotlivé funkce fungují", onTutorials)
            // konfigurace: vsechno zbyle (animace, zvuky, vibrace, vyhledavac, ...)
            Column(
                Modifier
                    .fillMaxWidth()
                    .demoMark("row:Konfigurace")
                    .clip(RoundedCornerShape(16.dp))
                    .background(ACCENT)
                    .clickable(onClick = onOther)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("Konfigurace", color = Color(0xFF06281D), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Animace, zvuky, vibrace, vyhledávač a další nastavení", color = Color(0xFF0B3D2A), fontSize = 13.sp)
            }
            SettingsRow("O aplikaci", "Verze, popis, co umí a použité součásti", onAbout)
            SettingsRow("Trezor hesel", "Hesla zašifrovaná jen v tomto telefonu, odemyká se PINem nebo 12 slovy", onVault)
            // nebezpecna akce uplne dole, cervene; vyzaduje dve potvrzeni
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DANGER)
                    .clickable { resetStep = 1 }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("Restartovat vše", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Smazat všechna data a nastavení a začít od nuly", color = Color(0xFFFFCDD2), fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (resetStep == 1) {
        StyledDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text("Restartovat vše?") },
            text = {
                Text(
                    "Všechno bude smazáno: nastavení, tlačítka, gesta, záložky, historie, otevřené karty, přihlášení na webech i seznam stahování." +
                        (if (hasVault) " Smaže se i trezor hesel a bez zálohy a 12 slov ho nejde vrátit." else "") +
                        " Chceš pokračovat?"
                )
            },
            confirmButton = { TextButton(onClick = { resetStep = 2 }) { Text("Ano") } },
            dismissButton = { TextButton(onClick = { resetStep = 0 }) { Text("Ne") } }
        )
    }
    if (resetStep == 2) {
        StyledDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text("Opravdu smazat?") },
            text = {
                Column {
                    Text("Tohle už nejde vrátit zpět. Appka se po smazání zavře a při dalším spuštění bude jako nová. Tip: nejdřív si udělej Export.")
                    if (hasVault) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(ROW)
                                .clickable { keepVault = !keepVault }.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(checked = keepVault, onCheckedChange = { keepVault = it })
                            Text("Před smazáním uložit zálohu trezoru (zašifrovanou, k obnově potřebuješ 12 slov)", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { resetStep = 0; onResetAll(hasVault && keepVault) }) { Text("Smazat", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { resetStep = 0 }) { Text("Zrušit") } }
        )
    }
}

/**
 * Výběr, kam exportovat: do telefonu (soubor) nebo na e-mail (sdílení souboru jako příloha).
 * Vedle e-mailu je malý otazníček: po klepnutí se na chvíli ukáže nápověda.
 */
@Composable
fun ExportChooserDialog(onSave: (Set<String>) -> Unit, onEmail: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var showHint by remember { mutableStateOf(false) }
    // co se bude exportovat (vychozi = vse): nas = tlacitka a nastaveni, za = zalozky, ka = otevrene karty, hi = historie
    val vaultContext = LocalContext.current
    val hasVault = remember { VaultManager.isCreated(vaultContext) }   // "Trezor hesel" se nabidne jen kdyz existuje
    var parts by remember { mutableStateOf(if (hasVault) setOf("nas", "za", "ka", "hi", "va") else setOf("nas", "za", "ka", "hi")) }
    // nápověda zmizí sama po 8 s (nový klik časovač restartuje)
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(8000)
            showHint = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(PANEL)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Export", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Co exportovat a kam to uložit?", color = MUTED, fontSize = 13.sp)
            for ((code, label) in listOf("nas" to "Tlačítka a nastavení", "za" to "Záložky a složky", "ka" to "Otevřené karty", "hi" to "Historie") + (if (hasVault) listOf("va" to "Trezor hesel (zašifrovaný)") else emptyList())) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ROW)
                        .clickable { parts = if (code in parts) parts - code else parts + code }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = code in parts,
                        onCheckedChange = { parts = if (it) parts + code else parts - code }
                    )
                    Text(label, color = Color.White, fontSize = 15.sp)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ACCENT)
                    .clickable { if (parts.isNotEmpty()) onSave(parts) },
                contentAlignment = Alignment.Center
            ) { Text("Uložit do telefonu", color = Color(0xFF06281D), fontSize = 15.sp, fontWeight = FontWeight.Bold) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ROW)
                        .clickable { if (parts.isNotEmpty()) onEmail(parts) },
                    contentAlignment = Alignment.Center
                ) { Text("Odeslat na e-mail", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                // malý otazníček
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, MUTED, CircleShape)
                        .clickable { showHint = true },
                    contentAlignment = Alignment.Center
                ) { Text("?", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }

            if (showHint) {
                Text(
                    "Pošli si svoje nastavení na e-mail → na novém telefonu soubor stáhneš → " +
                        "naimportuješ (Nastavení → Import) → a pokračuješ tam, kde jsi naposledy skončil.",
                    color = Color.White, fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ROW)
                        .padding(12.dp)
                )
            }
        }
    }
}

/** Vyber casti souboru, ktere se maji nacist (zobrazi se jen ty, co soubor obsahuje; vychozi vse). */
@Composable
fun ImportChooserDialog(available: Set<String>, onImport: (Set<String>, Set<String>) -> Unit, onDismiss: () -> Unit) {
    var parts by remember { mutableStateOf(available) }
    var merge by remember { mutableStateOf(setOf("za", "ka", "hi")) }   // pridat ke stavajicim (vychozi) / nahradit
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(PANEL).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Co ze souboru načíst? Nevybrané části zůstanou v aplikaci beze změny.", color = MUTED, fontSize = 13.sp)
            for ((code, label) in listOf("nas" to "Tlačítka a nastavení", "za" to "Záložky a složky", "ka" to "Otevřené karty", "hi" to "Historie", "va" to "Trezor hesel (potřebuješ 12 slov)")) {
                if (code !in available) continue
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ROW)
                        .clickable { parts = if (code in parts) parts - code else parts + code }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(checked = code in parts, onCheckedChange = { parts = if (it) parts + code else parts - code })
                    Text(label, color = Color.White, fontSize = 15.sp)
                }
                if (code in parts && code != "nas" && code != "va") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp).clip(RoundedCornerShape(12.dp)).background(ROW)
                            .clickable { merge = if (code in merge) merge - code else merge + code }
                            .padding(horizontal = 10.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(checked = code in merge, onCheckedChange = { merge = if (it) merge + code else merge - code })
                        Text("přidat ke stávajícím (jinak nahradit)", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(ACCENT)
                    .clickable { if (parts.isNotEmpty()) onImport(parts, merge) },
                contentAlignment = Alignment.Center
            ) { Text("Načíst vybrané", color = Color(0xFF06281D), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .demoMark("row:$title")
            .clip(RoundedCornerShape(16.dp))
            .background(PANEL)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = MUTED, fontSize = 13.sp)
    }
}

/** Ikona funkce tlacitka (bila vektorova ikona ze sady ikon). */
@Composable
internal fun GlyphIcon(fn: ButtonFunction, size: androidx.compose.ui.unit.Dp = 22.dp) {
    androidx.compose.foundation.Image(painter = painterResource(fn.iconRes), contentDescription = null, modifier = Modifier.size(size))
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MUTED, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
