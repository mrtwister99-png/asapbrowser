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

// Nastavení tlačítek: seznam funkcí, výběr funkce a modal nastavení tlačítka.

// ---------------------------------------------------------------- TLACITKA (nastaveni funkci)

@Composable
private fun NumButton(
    n: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    special: Boolean = false,    // zvlastni tlacitko (6 = gesta): zeleny okraj
    glyph: ButtonFunction? = null        // funkce tlacitka: jeji ikona se ukaze misto cisla
) {
    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            // zvlastni tlacitko (6, gesta) je svetle seda (#D9D9D9) s malou zelenou teckou
            .background(if (selected) ACCENT else if (special) Color(0xFFD9D9D9) else ROW)
            .border(if (special) 2.dp else 1.dp, if (special) ACCENT else Color(0xFF3A3A3A), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (special) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (selected) Color(0xFF06281D) else ACCENT))
            Text(
                n.toString(),
                color = if (selected) Color(0xFF06281D) else Color(0xFF555555),
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 5.dp)
            )
        } else if (glyph != null) {
            // misto cisla je videt ikona funkce, cislo zustava maly v rohu
            GlyphIcon(glyph, 24.dp)
            Text(
                n.toString(),
                color = if (selected) Color(0xFF06281D) else MUTED,
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 2.dp)
            )
        } else {
            Text(
                n.toString(),
                color = if (selected) Color(0xFF06281D) else Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

// Tlacitko "+" pro pridani noveho bocniho tlacitka
@Composable
private fun PlusButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14382A))
            .border(1.dp, ACCENT, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("+", color = ACCENT, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Plocha s cislovanymi tlacitky (rozlozeni jako v prohlizeci). Klepnuti na cislo ukaze
 * aktualni funkce (klepnuti a drzeni), pod nimi se daji zmenit. Nad tlacitky 5 a 6 jsou "+",
 * kterymi se pridaji dalsi boci tlacitka (cisla 9-12, max 2 vlevo a 2 vpravo).
 */
@Composable
internal fun ButtonsConfigScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(1) }
    // aktualni funkce (klice) jako stav, ulozene v AppSettings
    val tapKeys = remember { mutableStateListOf<String>().apply { for (i in 1..12) add(AppSettings.buttonTap[i - 1].get()) } }
    val longKeys = remember { mutableStateListOf<String>().apply { for (i in 1..12) add(AppSettings.buttonLong[i - 1].get()) } }
    val slide = remember { mutableStateListOf<Boolean>().apply { for (i in 1..12) add(AppSettings.buttonSlide[i - 1].get()) } }
    val leftExtras = remember { mutableStateListOf<Int>().apply { addAll(AppSettings.extraButtons(true)) } }
    val rightExtras = remember { mutableStateListOf<Int>().apply { addAll(AppSettings.extraButtons(false)) } }
    var picking by remember { mutableStateOf<Boolean?>(null) }   // null = nic, false = klepnuti, true = drzeni

    val ctx = LocalContext.current
    val enabled = remember { mutableStateListOf<Boolean>().apply { for (i in 1..12) add(AppSettings.isEnabled(i)) } }
    var confirmReset by remember { mutableStateOf(false) }

    // Kolik tlacitek (klepnuti/drzeni) otevira Nastaveni, nepocitaje zadane misto (n, long)
    fun otherSettingsHolders(n: Int, long: Boolean): Int {
        var c = 0
        for (i in 1..12) {
            if (!enabled[i - 1]) continue   // skryte tlacitko do Nastaveni nevede
            if (!(i == n && !long) && tapKeys[i - 1] == ButtonFunction.OPEN_SETTINGS.key) c++
            if (!(i == n && long) && longKeys[i - 1] == ButtonFunction.OPEN_SETTINGS.key) c++
        }
        return c
    }

    fun warnNeedSettings() {
        android.widget.Toast.makeText(ctx, "Aspoň jedno tlačítko musí otevírat Nastavení", android.widget.Toast.LENGTH_LONG).show()
    }

    // Prirazeni funkce; odmitne, kdyby se tim ztratila posledni cesta do Nastaveni
    fun tryAssign(n: Int, long: Boolean, fn: ButtonFunction) {
        if (n <= 4) return   // tlacitka 1-4 jsou pevna
        val cur = ButtonFunction.fromKey(if (long) longKeys[n - 1] else tapKeys[n - 1])
        if (cur == ButtonFunction.OPEN_SETTINGS && fn != ButtonFunction.OPEN_SETTINGS && otherSettingsHolders(n, long) == 0) {
            warnNeedSettings()
            return
        }
        // Bocni lista a Gesta nesmi byt na jednom tlacitku spolu (klepnuti + drzeni)
        val pair = setOf(ButtonFunction.LEFT_BAR.key, ButtonFunction.GESTURES.key)
        val otherKey = if (long) tapKeys[n - 1] else longKeys[n - 1]
        if (fn.key in pair && otherKey in pair && otherKey != fn.key) {
            android.widget.Toast.makeText(ctx, "Boční lišta a Gesta nemohou být na jednom tlačítku zároveň (klepnutí + držení)", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        // Gesta (kulicka) i Bocni lista mohou byt jen 1x: pri prirazeni se z puvodniho mista odeberou
        if (fn == ButtonFunction.GESTURES || fn == ButtonFunction.LEFT_BAR) {
            var movedFrom = 0
            for (i in 1..12) {
                if (tapKeys[i - 1] == fn.key && !(i == n && !long)) {
                    AppSettings.buttonTap[i - 1].set(ButtonFunction.NONE.key); tapKeys[i - 1] = ButtonFunction.NONE.key; movedFrom = i
                }
                if (longKeys[i - 1] == fn.key && !(i == n && long)) {
                    AppSettings.buttonLong[i - 1].set(ButtonFunction.NONE.key); longKeys[i - 1] = ButtonFunction.NONE.key; movedFrom = i
                }
            }
            if (movedFrom != 0) {
                val what = if (fn == ButtonFunction.GESTURES) "Gesta" else "Boční lišta"
                android.widget.Toast.makeText(ctx, "$what přesunuta z tlačítka $movedFrom (tam teď není žádná funkce)", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        if (long) { AppSettings.buttonLong[n - 1].set(fn.key); longKeys[n - 1] = fn.key }
        else { AppSettings.buttonTap[n - 1].set(fn.key); tapKeys[n - 1] = fn.key }
    }

    // ikona funkce (klepnuti) pro nahled na plose - zmeni se hned po vyberu
    fun glyphFor(n: Int): ButtonFunction = ButtonFunction.fromKey(tapKeys[n - 1])

    // Tlacitko v nahledu: kulicka (svetle seda, zelena tecka) je u toho, ktere ma funkci Gesta; skryta jsou pruhlednejsi
    @Composable
    fun Nb(n: Int) {
        val orb = tapKeys[n - 1] == ButtonFunction.GESTURES.key
        val dim = if (n in 5..8 && !enabled[n - 1]) 0.35f else 1f
        NumButton(
            n, selected == n, { selected = n }, Modifier.alpha(dim),
            glyph = glyphFor(n), shape = if (orb) CircleShape else RoundedCornerShape(14.dp), special = orb
        )
    }

    // Pridani boci tlacitka: dostane nejmensi volne cislo od 9 (max 4 pridana celkem)
    fun addButton(left: Boolean) {
        val used = leftExtras + rightExtras
        val next = (9..12).firstOrNull { it !in used } ?: return
        val list = if (left) leftExtras else rightExtras
        if (list.size >= 2) return
        list.add(next)
        AppSettings.setExtraButtons(left, list.toList())
        AppSettings.highlightButton.set(next)   // hlavni okno ho po navratu zvyrazni
        selected = next
    }

    // Zobrazit / skryt tlacitko 5-8 (1-4 jsou stala). Skryti odmitne, kdyby tim zmizela posledni cesta do Nastaveni.
    fun setButtonShown(n: Int, shown: Boolean) {
        if (!shown) {
            val key = ButtonFunction.OPEN_SETTINGS.key
            val holds = tapKeys[n - 1] == key || longKeys[n - 1] == key
            val otherHolds = (0 until 12).any { it != n - 1 && enabled[it] && (tapKeys[it] == key || longKeys[it] == key) }
            if (holds && !otherHolds) {
                warnNeedSettings()
                return
            }
        }
        enabled[n - 1] = shown
        AppSettings.buttonEnabled[n - 1].set(shown)
    }

    // Vychozi funkce (a zobrazeni) jednoho tlacitka
    fun resetOne(n: Int) {
        AppSettings.buttonTap[n - 1].set(ButtonDefaults.tap(n).key)
        AppSettings.buttonLong[n - 1].set(ButtonDefaults.long(n).key)
        AppSettings.buttonEnabled[n - 1].reset()
        tapKeys[n - 1] = ButtonDefaults.tap(n).key
        longKeys[n - 1] = ButtonDefaults.long(n).key
        enabled[n - 1] = true
    }

    fun removeButton(n: Int) {
        // pridane tlacitko, ktere jako jedine otevira Nastaveni, nejde odebrat
        val key = ButtonFunction.OPEN_SETTINGS.key
        val holdsSettings = tapKeys[n - 1] == key || longKeys[n - 1] == key
        val otherHolds = (0 until 12).any { it != n - 1 && enabled[it] && (tapKeys[it] == key || longKeys[it] == key) }
        if (holdsSettings && !otherHolds) {
            warnNeedSettings()
            return
        }
        leftExtras.remove(n)
        rightExtras.remove(n)
        AppSettings.setExtraButtons(true, leftExtras.toList())
        AppSettings.setExtraButtons(false, rightExtras.toList())
        AppSettings.buttonTap[n - 1].reset()
        AppSettings.buttonLong[n - 1].reset()
        AppSettings.buttonSlide[n - 1].reset()
        tapKeys[n - 1] = ButtonDefaults.tap(n).key
        longKeys[n - 1] = ButtonDefaults.long(n).key
        slide[n - 1] = AppSettings.buttonSlide[n - 1].get()
        selected = 1
    }

    ScreenScaffold(title = "Tlačítka", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // --- plocha (maketa obrazovky)
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(24.dp))
            ) {
                // nahore: 7 - adresa - 8
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Nb(7)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ROW),
                        contentAlignment = Alignment.CenterStart
                    ) { Text("  adresa stránky", color = MUTED, fontSize = 13.sp) }
                    Nb(8)
                }
                // levy bok: [+] pridana (nejnovejsi nahore) a dole 5 - blizko spodni listy
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (leftExtras.size < 2 && (leftExtras.size + rightExtras.size) < 4) PlusButton({ addButton(true) })
                    for (n in leftExtras.reversed()) Nb(n)
                    Nb(5)
                }
                // pravy bok: [+] pridana a dole 6 (gesta) - zvlastni kulaty tvar
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (rightExtras.size < 2 && (leftExtras.size + rightExtras.size) < 4) PlusButton({ addButton(false) })
                    for (n in rightExtras.reversed()) Nb(n)
                    Nb(6)
                }
                // dole: 1 - hledani - 2 3 4
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Nb(1)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFD9D9D9)),
                        contentAlignment = Alignment.CenterStart
                    ) { Text("  Hledat nebo zadat URL", color = Color(0xFF888888), fontSize = 12.sp) }
                    Nb(2)
                    Spacer(Modifier.width(4.dp))
                    Nb(3)
                    Spacer(Modifier.width(4.dp))
                    Nb(4)
                }
            }

            // --- detail vybraneho tlacitka
            val tapFn = ButtonFunction.fromKey(tapKeys[selected - 1])
            val longFn = ButtonFunction.fromKey(longKeys[selected - 1])
            val isSide = selected == 5 || selected == 6 || selected >= 9
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PANEL)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Tlačítko $selected – ${ButtonDefaults.name(selected)}",
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                FunctionRow("Klepnutí", tapFn, fixed = selected <= 4) { picking = false }
                FunctionRow("Držení", longFn, fixed = selected <= 4) { picking = true }
                if (isSide) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ROW)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("VYSOUVACÍ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (slide[selected - 1]) "Ano – vidět jen z poloviny (50 %)" else "Ne – vidět celé (100 %)",
                                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = slide[selected - 1],
                            onCheckedChange = {
                                slide[selected - 1] = it
                                AppSettings.buttonSlide[selected - 1].set(it)
                            }
                        )
                    }
                    Text(
                        "Vysouvací: první klepnutí tlačítko vysune, další klepnutí spustí funkci a po chvíli se samo schová. " +
                            "Nevysouvací: je vidět celé a klepnutí spustí funkci hned.",
                        color = MUTED, fontSize = 12.sp
                    )
                }
                if (selected in 5..8) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ROW)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Zobrazit toto tlačítko", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = enabled[selected - 1], onCheckedChange = { setButtonShown(selected, it) })
                    }
                } else if (selected <= 4) {
                    Text("Tlačítka 1–4 jsou pevná: funkce ani zobrazení nejdou měnit.", color = MUTED, fontSize = 12.sp)
                }
                if (selected == 6) {
                    Text(
                        "Tlačítko 6 je výchozí místo pro Gesta. Když mu dáš jinou funkci, je to běžné boční tlačítko.",
                        color = MUTED, fontSize = 12.sp
                    )
                }
                if (tapFn == ButtonFunction.GESTURES) {
                    Text(
                        "Kulička s ohňostrojem je jen na tlačítku s funkcí Gesta (klepnutí). Gesta mohou být jen na jednom tlačítku, kromě 7 a 8.",
                        color = MUTED, fontSize = 12.sp
                    )
                }
                if (selected == 5 || selected == 6) {
                    Text("Funkci „Boční lišta“ lze dát jen na tlačítko 5 nebo 6.", color = MUTED, fontSize = 12.sp)
                }
                if (selected >= 9) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DANGER)
                            .clickable { removeButton(selected) },
                        contentAlignment = Alignment.Center
                    ) { Text("Odebrat toto tlačítko", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }

            // zmeny se ukladaji hned; tlacitko to potvrdi a vrati zpet
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ACCENT)
                    .clickable {
                        android.widget.Toast.makeText(ctx, "Uloženo", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) { Text("ULOŽIT", color = Color(0xFF06281D), fontSize = 16.sp, fontWeight = FontWeight.Bold) }

            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ROW)
                    .clickable { confirmReset = true },
                contentAlignment = Alignment.Center
            ) { Text("Obnovit výchozí funkce", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        }
    }

    // --- potvrzeni obnoveni: jen vybrane tlacitko, nebo vsechna
    if (confirmReset) {
        StyledDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Obnovit výchozí funkce") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Co chceš vrátit do výchozího stavu?", color = MUTED, fontSize = 13.sp)
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(ROW)
                            .clickable {
                                resetOne(selected)
                                confirmReset = false
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("Jen tlačítko $selected", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(DANGER)
                            .clickable {
                                for (i in 1..12) resetOne(i)
                                confirmReset = false
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("Všechna tlačítka", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušit") } }
        )
    }

    // --- vyber funkce
    val which = picking
    if (which != null) {
        val current = ButtonFunction.fromKey(if (which) longKeys[selected - 1] else tapKeys[selected - 1])
        StyledDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (which) "Držení – tlačítko $selected" else "Klepnutí – tlačítko $selected") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    // "Zadna funkce" nahore, pak skupiny
                    // Gesta a Bocni lista (max 1x) jsou uplne nahore; zelene jsou, dokud nejsou nikde pouzita
                    val specials = listOf(ButtonFunction.GESTURES, ButtonFunction.LEFT_BAR).filter { it.allowedOn(selected) }
                    val groups = ButtonFunction.values()
                        .filter { it != ButtonFunction.NONE && it !in specials && it.allowedOn(selected) }
                        .groupBy { it.group }
                    items(specials, key = { "s-" + it.key }) { fn ->
                        val free = (0 until 12).none { tapKeys[it] == fn.key || longKeys[it] == fn.key }
                        FunctionChoice(fn, current == fn, free) {
                            tryAssign(selected, which, fn)
                            picking = null
                        }
                    }
                    item {
                        FunctionChoice(ButtonFunction.NONE, current == ButtonFunction.NONE) {
                            tryAssign(selected, which, it)
                            picking = null
                        }
                    }
                    for ((group, list) in groups) {
                        item(key = "g-$group") {
                            Text(
                                group.uppercase(), color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { it.key }) { fn ->
                            FunctionChoice(fn, current == fn) {
                                tryAssign(selected, which, fn)
                                picking = null
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Zrušit") } }
        )
    }
}

@Composable
private fun FunctionRow(title: String, fn: ButtonFunction, fixed: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ROW)
            .then(if (fixed) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(fn, 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    fn.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!fixed) Text("›", color = MUTED, fontSize = 22.sp)
    }
}

/** Bez diakritiky a velkych pismen (hledani funkce "zavrit" najde i "Zavřít"). */
private fun normText(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Funkce vyhovuje hledani, kdyz dotaz je v jejim nazvu nebo popisu. */
internal fun ButtonFunction.matches(q: String): Boolean =
    q.isBlank() || normText(label).contains(normText(q.trim())) || normText(description()).contains(normText(q.trim()))

/** Pole "Hledat funkci" nad seznamem funkci. */
@Composable
internal fun FunctionSearchField(query: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(ROW)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (query.isEmpty()) Text("Hledat funkci…", color = MUTED, fontSize = 14.sp)
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(ACCENT),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun FunctionChoice(fn: ButtonFunction, isCurrent: Boolean, highlight: Boolean = false, onPick: (ButtonFunction) -> Unit) {
    // highlight = volna funkce (Gesta / Bocni lista nikde nepouzita): cele tlacitko zelene
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlight) GESTURE_GREEN else Color.Transparent)
            .clickable { onPick(fn) }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
            if (highlight && fn == ButtonFunction.GESTURES) {
                // bile kolecko a kolem nej maly ohnostroj
                Canvas(Modifier.size(34.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color.White, radius = size.width * 0.17f, center = c)
                    for (k in 0 until 8) {
                        val a = Math.toRadians(k * 45.0 + 22.5)
                        val dx = kotlin.math.cos(a).toFloat()
                        val dy = kotlin.math.sin(a).toFloat()
                        drawLine(
                            Color.White,
                            Offset(c.x + dx * size.width * 0.28f, c.y + dy * size.width * 0.28f),
                            Offset(c.x + dx * size.width * 0.42f, c.y + dy * size.width * 0.42f),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                }
            } else {
                GlyphIcon(fn, 22.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                fn.label, color = if (highlight) Color.White else if (isCurrent) ACCENT else Color.White, fontSize = 15.sp,
                fontWeight = if (isCurrent || highlight) FontWeight.Bold else FontWeight.Normal
            )
            // kratky popis, co funkce dela
            Text(fn.description(), color = if (highlight) Color(0xFFD7FFE8) else MUTED, fontSize = 12.sp)
        }
        if (isCurrent) Text("✓", color = if (highlight) Color.White else ACCENT, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- KONFIGURACNI MOD TLACITEK (modal na hlavni plose)

/**
 * Nastaveni jednoho tlacitka: otevre se z hlavni plochy v konfiguracnim modu.
 * Pravidla jsou stejna jako drive v Nastaveni -> Tlacitka. Zmeny se ukladaji hned,
 * onChanged se zavola po kazde zmene (hlavni okno obnovi tlacitka), onRemoved po odebrani pridaneho tlacitka.
 */
@Composable
fun ButtonConfigModal(n: Int, onChanged: () -> Unit, onRemoved: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val version = tick   // cteni = prekresleni po zmene
    var picking by remember { mutableStateOf<Boolean?>(null) }   // null = nic, false = klepnuti, true = drzeni

    fun fnOf(long: Boolean): ButtonFunction =
        ButtonFunction.fromKey((if (long) AppSettings.buttonLong[n - 1] else AppSettings.buttonTap[n - 1]).get())

    fun toast(text: String) = android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_LONG).show()

    fun assign(long: Boolean, fn: ButtonFunction) {
        if (n <= 4) return   // tlacitka 1-4 jsou pevna
        // Bocni lista a Gesta nesmi byt na jednom tlacitku spolu (klepnuti + drzeni)
        val pair = setOf(ButtonFunction.LEFT_BAR.key, ButtonFunction.GESTURES.key)
        val otherKey = (if (long) AppSettings.buttonTap[n - 1] else AppSettings.buttonLong[n - 1]).get()
        if (fn.key in pair && otherKey in pair && otherKey != fn.key) {
            toast("Boční lišta a Gesta nemohou být na jednom tlačítku zároveň (klepnutí + držení)")
            return
        }
        // Gesta i Bocni lista jen 1x: pri prirazeni se z puvodniho mista odeberou
        if (fn == ButtonFunction.GESTURES || fn == ButtonFunction.LEFT_BAR) {
            var movedFrom = 0
            for (i in 1..12) {
                if (AppSettings.buttonTap[i - 1].get() == fn.key && !(i == n && !long)) {
                    AppSettings.buttonTap[i - 1].set(ButtonFunction.NONE.key); movedFrom = i
                }
                if (AppSettings.buttonLong[i - 1].get() == fn.key && !(i == n && long)) {
                    AppSettings.buttonLong[i - 1].set(ButtonFunction.NONE.key); movedFrom = i
                }
            }
            if (movedFrom != 0) {
                val what = if (fn == ButtonFunction.GESTURES) "Gesta" else "Boční lišta"
                toast("$what přesunuta z tlačítka $movedFrom (tam teď není žádná funkce)")
            }
        }
        if (long) AppSettings.buttonLong[n - 1].set(fn.key) else AppSettings.buttonTap[n - 1].set(fn.key)
        tick++
        onChanged()
    }

    fun resetThis() {
        AppSettings.buttonTap[n - 1].set(ButtonDefaults.tap(n).key)
        AppSettings.buttonLong[n - 1].set(ButtonDefaults.long(n).key)
        AppSettings.buttonEnabled[n - 1].reset()
        AppSettings.fixButtons()   // kdyby se tim Gesta / Bocni lista objevily podruhe
        tick++
        onChanged()
    }

    fun removeThis() {
        AppSettings.setExtraButtons(true, AppSettings.extraButtons(true).filter { it != n })
        AppSettings.setExtraButtons(false, AppSettings.extraButtons(false).filter { it != n })
        AppSettings.buttonTap[n - 1].set(ButtonDefaults.tap(n).key)
        AppSettings.buttonLong[n - 1].set(ButtonDefaults.long(n).key)
        AppSettings.buttonSlide[n - 1].reset()
        onRemoved()
    }

    val tapFn = fnOf(false)
    val longFn = fnOf(true)
    val isSide = n == 5 || n == 6 || n >= 9

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(primary = ACCENT, background = BG, surface = PANEL)
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 620.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Tlačítko $n – ${ButtonDefaults.name(n)}",
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                FunctionRow("Klepnutí", tapFn, fixed = n <= 4) { picking = false }
                FunctionRow("Držení", longFn, fixed = n <= 4) { picking = true }
                if (isSide) {
                    var slide by remember(version) { mutableStateOf(AppSettings.buttonSlide[n - 1].get()) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ROW).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("VYSOUVACÍ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (slide) "Ano – vidět jen z poloviny (50 %)" else "Ne – vidět celé (100 %)",
                                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(checked = slide, onCheckedChange = {
                            slide = it
                            AppSettings.buttonSlide[n - 1].set(it)
                            onChanged()
                        })
                    }
                }
                if (n in 5..8) {
                    var shown by remember(version) { mutableStateOf(AppSettings.buttonEnabled[n - 1].get()) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ROW).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Zobrazit toto tlačítko", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = shown, onCheckedChange = {
                            shown = it
                            AppSettings.buttonEnabled[n - 1].set(it)
                            onChanged()
                        })
                    }
                }
                if (n <= 4) {
                    Text("Tlačítka 1–4 jsou pevná: funkce ani zobrazení nejdou měnit.", color = MUTED, fontSize = 12.sp)
                }
                if (n in 7..8) {
                    Text("Tlačítka 7 a 8 mají jen funkce zap/vyp.", color = MUTED, fontSize = 12.sp)
                }
                if (n == 5 || n == 6) {
                    Text("Funkci „Boční lišta“ lze dát jen na tlačítko 5 nebo 6.", color = MUTED, fontSize = 12.sp)
                }
                if (n >= 9) {
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(DANGER).clickable { removeThis() },
                        contentAlignment = Alignment.Center
                    ) { Text("Odebrat toto tlačítko", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
                if (n > 4) {
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(ROW).clickable { resetThis() },
                        contentAlignment = Alignment.Center
                    ) { Text("Obnovit výchozí funkce tlačítka", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(ACCENT).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text("Hotovo", color = Color(0xFF06281D), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // vyber funkce (druhe okno nad prvnim)
        val which = picking
        if (which != null) {
            Dialog(onDismissRequest = { picking = null }) {
                val current = fnOf(which)
                var query by remember { mutableStateOf("") }
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(PANEL).padding(16.dp)
                ) {
                    Text(
                        if (which) "Držení – tlačítko $n" else "Klepnutí – tlačítko $n",
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FunctionSearchField(query) { query = it }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        // Gesta a Bocni lista (max 1x) nahore; zelene, dokud nejsou nikde pouzita
                        val specials = listOf(ButtonFunction.GESTURES, ButtonFunction.LEFT_BAR).filter { it.allowedOn(n) && it.matches(query) }
                        val groups = ButtonFunction.values()
                            .filter { it != ButtonFunction.NONE && it !in specials && it.allowedOn(n) && it.matches(query) }
                            .groupBy { it.group }
                        items(specials, key = { "s-" + it.key }) { fn ->
                            val free = (1..12).none { AppSettings.buttonTap[it - 1].get() == fn.key || AppSettings.buttonLong[it - 1].get() == fn.key }
                            FunctionChoice(fn, current == fn, free) {
                                assign(which, fn)
                                picking = null
                            }
                        }
                        if (ButtonFunction.NONE.matches(query)) item {
                            FunctionChoice(ButtonFunction.NONE, current == ButtonFunction.NONE) {
                                assign(which, it)
                                picking = null
                            }
                        }
                        for ((group, list) in groups) {
                            item(key = "g-$group") {
                                Text(
                                    group.uppercase(), color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                                )
                            }
                            items(list, key = { it.key }) { fn ->
                                FunctionChoice(fn, current == fn) {
                                    assign(which, fn)
                                    picking = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
