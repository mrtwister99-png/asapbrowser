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

// Barvy ve stejném duchu jako lišty v appce (kopie ze SettingsScreens.kt, v každém souboru soukromé)
private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF00B055)
private val MUTED = Color(0xFF888888)
private val DANGER = Color(0xFFB91C1C)

// Nastavení tlačítek: seznam funkcí, výběr funkce a modal nastavení tlačítka.

// ---------------------------------------------------------------- TLACITKA (nastaveni funkci)

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
            if (fn == ButtonFunction.GESTURES) {
                // bile kolecko se zelenou teckou uvnitr (vzdy stejne, bez ohnostroje)
                Canvas(Modifier.size(34.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color.White, radius = size.width * 0.3f, center = c)
                    drawCircle(GESTURE_GREEN, radius = size.width * 0.12f, center = c)
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
fun ButtonConfigModal(n: Int, onChanged: () -> Unit, onRemoved: () -> Unit, onDismiss: () -> Unit, onSaved: () -> Unit = onDismiss) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val version = tick   // cteni = prekresleni po zmene
    var picking by remember { mutableStateOf<Boolean?>(null) }   // null = nic, false = klepnuti, true = drzeni
    var confirm by remember { mutableStateOf(0) }                // 1 = smazat tlacitko, 2 = obnovit funkce

    fun fnOf(long: Boolean): ButtonFunction =
        ButtonFunction.fromKey((if (long) AppSettings.buttonLong[n - 1] else AppSettings.buttonTap[n - 1]).get())

    fun toast(text: String) = AppToast.show(ctx, text, true)

    fun assign(long: Boolean, fn: ButtonFunction) {
        if (n == 4) return   // tlacitko 4 (Nastaveni) je pevne
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
        // Gesta = tlacitko ma jen gesto (bez klepnuti a drzeni): obe pozice maji Gesta; zmena funkce je zase uvolni
        val curHere = (if (long) AppSettings.buttonLong[n - 1] else AppSettings.buttonTap[n - 1]).get()
        if (fn == ButtonFunction.GESTURES) {
            AppSettings.buttonTap[n - 1].set(fn.key); AppSettings.buttonLong[n - 1].set(fn.key)
        } else if (curHere == ButtonFunction.GESTURES.key) {
            AppSettings.buttonTap[n - 1].set(ButtonFunction.NONE.key); AppSettings.buttonLong[n - 1].set(ButtonFunction.NONE.key)
            if (long) AppSettings.buttonLong[n - 1].set(fn.key) else AppSettings.buttonTap[n - 1].set(fn.key)
        } else {
            if (long) AppSettings.buttonLong[n - 1].set(fn.key) else AppSettings.buttonTap[n - 1].set(fn.key)
        }
        tick++
        onChanged()
    }

    fun resetThis() {
        AppSettings.buttonTap[n - 1].set(ButtonDefaults.tap(n).key)
        AppSettings.buttonLong[n - 1].set(ButtonDefaults.long(n).key)
        AppSettings.buttonEnabled[n - 1].reset()
        AppSettings.buttonRetract[n - 1].reset()
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
        AppSettings.buttonRetract[n - 1].reset()
        onRemoved()
    }

    val tapFn = fnOf(false)
    val longFn = fnOf(true)
    val isSide = n == 5 || n == 6 || n >= 9

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // okoli se nezatmavuje: uzivatel vidi zmeny tlacitka na plose hned
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)
            ?.window?.setDimAmount(0f)
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(primary = ACCENT, background = BG, surface = PANEL)
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.62f)   // uzsi okno: vejde se mezi boční tlačítka
                    .heightIn(max = 620.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // hlavicka: nazev + vpravo [X smazat (jen pridana)] [obnovit] [hotovo]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tlačítko $n", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (n >= 9) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE53935)).clickable { confirm = 1 },
                            contentAlignment = Alignment.Center
                        ) { Text("X", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (n != 4) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(ROW).clickable { confirm = 2 },   // ctverec jako X a hotovo
                            contentAlignment = Alignment.Center
                        ) { Text("↻", color = Color(0xFF4FC3F7), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(ACCENT).clickable(onClick = onSaved),
                        contentAlignment = Alignment.Center
                    ) { Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
                // nahled: jak tlacitko vypada na plose (ikona klepnuti vlevo, ikona drzeni vpravo)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    for (fn in (if (tapFn == ButtonFunction.GESTURES) listOf(tapFn) else listOf(tapFn, longFn))) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E1E1E))
                                .border(2.dp, Color(0xFF3A3A3A), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (fn == ButtonFunction.GESTURES) {
                                Canvas(Modifier.size(34.dp)) {
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    drawCircle(Color.White, radius = size.width * 0.4f, center = c)
                                    drawCircle(ACCENT, radius = size.width * 0.14f, center = c)
                                }
                            } else if (fn != ButtonFunction.NONE) {
                                GlyphIcon(fn, 26.dp)
                            }
                        }
                    }
                }
                if (n in 5..8) {
                    var shown by remember(version) { mutableStateOf(AppSettings.buttonEnabled[n - 1].get()) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ROW).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("TLAČÍTKO JE AKTIVNÍ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (shown) "Ano – je vidět a funguje" else "Ne – je skryté (tady ho poznáš podle čísla)",
                                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(checked = shown, onCheckedChange = {
                            shown = it
                            AppSettings.buttonEnabled[n - 1].set(it)
                            onChanged()
                        })
                    }
                }
                if (tapFn == ButtonFunction.GESTURES) {
                    // tlacitko Gest: jen gesto, bez klepnuti a drzeni
                    FunctionRow("Funkce", tapFn, fixed = false) { picking = false }
                } else {
                    FunctionRow("Klepnutí", tapFn, fixed = n == 4) { picking = false }
                    FunctionRow("Držení", longFn, fixed = n == 4) { picking = true }
                }
                if (isSide) {
                    var retract by remember(version) { mutableStateOf(AppSettings.buttonRetract[n - 1].get()) }
                    Text("ZASUNUTÍ ZA OKRAJ", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleCard("50 %", "napůl vidět", retract != 70, Modifier.weight(1f)) {
                            retract = 50; AppSettings.buttonRetract[n - 1].set(50); onChanged()
                        }
                        StyleCard("70 %", "vidět jen kousek", retract == 70, Modifier.weight(1f)) {
                            retract = 70; AppSettings.buttonRetract[n - 1].set(70); onChanged()
                        }
                    }
                }
                if (n == 4) {
                    Text("Tlačítko Nastavení je pevné: nejde na něm nic měnit.", color = MUTED, fontSize = 12.sp)
                }
                if (n == 1) {
                    Text("Na tlačítku Domů jde vybrat funkce z Navigace a stránky a Gesta.", color = MUTED, fontSize = 12.sp)
                }
                if (n == 2 || n == 3) {
                    Text("Na tomto tlačítku jde vybrat funkce ze skupiny Záložky, historie, hledání a Gesta (nebo vrátit původní funkci).", color = MUTED, fontSize = 12.sp)
                }
                if (n in 7..8) {
                    Text("Tlačítka 7 a 8 mají jen funkce zap/vyp.", color = MUTED, fontSize = 12.sp)
                }
                if (n == 5 || n == 6) {
                    Text("Funkci „Boční lišta“ lze dát jen na tlačítko 5 nebo 6.", color = MUTED, fontSize = 12.sp)
                }
            }
        }

        // potvrzeni smazani / obnoveni
        if (confirm != 0) {
            val isDel = confirm == 1
            ConfirmDialog(
                title = if (isDel) "Opravdu chceš smazat tlačítko?" else "Obnovit funkce tlačítka?",
                text = "",
                yesColor = if (isDel) DANGER else ACCENT,
                onNo = { confirm = 0 },
                onYes = { confirm = 0; if (isDel) removeThis() else resetThis() }
            )
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
                        // Gesta a Boční lišta nejdou na jedno tlačítko spolu (klepnutí + držení): druhá z nich se v nabídce nenabízí
                        val otherKeyHere = (if (which) AppSettings.buttonTap[n - 1] else AppSettings.buttonLong[n - 1]).get()
                        val blocked = when (otherKeyHere) {
                            ButtonFunction.LEFT_BAR.key -> ButtonFunction.GESTURES
                            ButtonFunction.GESTURES.key -> ButtonFunction.LEFT_BAR
                            else -> null
                        }
                        val specials = listOf(ButtonFunction.GESTURES, ButtonFunction.LEFT_BAR).filter { it.allowedOn(n) && it.matches(query) && it != blocked }
                        val groups = ButtonFunction.values()
                            .filter { it != ButtonFunction.NONE && it !in specials && it != blocked && it.allowedOn(n) && it.matches(query) }
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
