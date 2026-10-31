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

// Nastavení → Tutoriály: seznam tutoriálů a ukázkové animace.

// ---------------------------------------------------------------- TUTORIÁLY

// Druh ukázkové animace nad postupem
private enum class DemoKind { TAP_HOLD, GESTURE, FOLDER, ICON }

private class Tutorial(
    val title: String,
    val subtitle: String,
    val demo: DemoKind,
    val icon: ButtonFunction = ButtonFunction.NONE,   // ikona pro DemoKind.ICON
    val steps: List<String>,
    val tip: String? = null
)

// Seznam tutoriálů: nová funkce = jeden záznam sem (texty držet v souladu s docs/FUNKCE.md)
private val TUTORIALS = listOf(
    Tutorial(
        title = "Tlačítka: klepnutí a držení",
        subtitle = "Každé tlačítko umí dvě věci",
        demo = DemoKind.TAP_HOLD,
        steps = listOf(
            "Klepnutí spustí hlavní funkci tlačítka. Například Karty otevřou přehled karet.",
            "Držení spustí druhou funkci. Například držení Karet otevře novou kartu, držení Oblíbených přidá stránku do vybrané složky a držení Nastavení otevře hodnocení a hlášení chyb.",
            "Tlačítka 1–4 (Domů, Karty, Oblíbené, Nastavení) jsou pevná. Funkce ostatních tlačítek si nastavíš sám.",
            "Změna: Nastavení → Tlačítka, klepni na tlačítko a vyber funkci pro klepnutí a pro držení."
        ),
        tip = "Boční tlačítka 9–12 přidáš přes „+“ v nastavení tlačítek. Tlačítka 7 a 8 (AdBlock a VPN) mají jen funkce zap/vyp."
    ),
    Tutorial(
        title = "Gesta",
        subtitle = "Funkce jedním tahem prstu po ploše 3×3",
        demo = DemoKind.GESTURE,
        steps = listOf(
            "Gesta ovládáš kuličkou. Je na tlačítku, které má funkci „Gesta“ (ve výchozím stavu tlačítko 6).",
            "Polož prst na kuličku a bez zvednutí táhni po ploše 3×3. Políčka jsou očíslovaná 1–9: 1 vlevo nahoře, 9 vpravo dole.",
            "Po zvednutí prstu se gesto vyhodnotí a spustí se jeho funkce. Telefon zabrní a ozve se zvuk (vypnout jde v Konfiguraci).",
            "Výchozí gesta: 9-8-7-4-1 nová karta, 9-8-7-4-7 zavřít kartu, 9-8-5-4 zpět, 9-8-5-6 vpřed.",
            "Vlastní gesta, velikost a polohu plochy upravíš v Nastavení → Gesta."
        ),
        tip = "Gesta nejdou dát na tlačítka 7 a 8 a nesmí být na stejném tlačítku jako Boční lišta."
    ),
    Tutorial(
        title = "Karty a složky",
        subtitle = "Přehled karet, skládání do složek, hromadné zavření",
        demo = DemoKind.FOLDER,
        steps = listOf(
            "Tlačítko Karty otevře přehled karet s náhledy.",
            "Podrž kartu za horní část (hlavičku) a táhni ji na hlavičku jiné karty. Vznikne složka. Když ji pustíš na tělo jiné karty, karta se jen přesune.",
            "Složka má barvu (z 8 barev) a klepnutím se otevře. Kartu z ní vyjmeš tlačítkem nebo tažením nad mřížku.",
            "Podrž kartu za tělo a vyber další. Vybrané karty zavřeš najednou (appka se před tím zeptá)."
        ),
        tip = "Složka s méně než 2 kartami sama zanikne. Složky se po restartu appky obnoví."
    ),
    Tutorial(
        title = "Anonymní karta",
        subtitle = "Surfování bez stop",
        demo = DemoKind.ICON,
        icon = ButtonFunction.NEW_INCOGNITO,
        steps = listOf(
            "Otevři novou kartu a zvol Anonymní. Funguje i funkce „Nové anonymní okno“, ve výchozím stavu gesto 9-8-7-5-2.",
            "Anonymní karta má vlastní cookies a úložiště, neukládá historii ani náhledy a nejde z ní udělat screenshot.",
            "Po zavření poslední anonymní karty se její data smažou. Po zavření appky se anonymní karty neobnoví."
        ),
        tip = "Na starším WebView bez podpory profilů se cookies sdílejí s normálními kartami."
    ),
    Tutorial(
        title = "AdBlock",
        subtitle = "Blokování reklam a výjimky pro weby",
        demo = DemoKind.ICON,
        icon = ButtonFunction.ADBLOCK_TOGGLE,
        steps = listOf(
            "Tlačítko AdBlock vlevo nahoře blokování zapíná a vypíná. Zelené znamená zapnuto.",
            "Počet zablokovaných reklam uvidíš na úvodní stránce nové karty.",
            "Když ti nějaký web po blokování nefunguje, klepni na zámek v adresním řádku (Informace o stránce) a vypni AdBlock jen pro tento web. Na zámku pak svítí tečka.",
            "Cookie lišty se odmítají automaticky a seznamy reklam se aktualizují samy."
        )
    ),
    Tutorial(
        title = "Boční lišta",
        subtitle = "Rychlé nástroje po straně",
        demo = DemoKind.ICON,
        icon = ButtonFunction.LEFT_BAR,
        steps = listOf(
            "Funkci „Boční lišta“ mívá tlačítko 5 (vlevo). Jde ji dát i tlačítku 6 (vpravo).",
            "Klepnutím se lišta otevře na straně tlačítka. Najdeš v ní screenshot, sdílení odkazu, QR kód, verzi pro počítač a hledání na stránce."
        ),
        tip = "Boční lišta může být jen na jednom tlačítku a ne na stejném jako Gesta."
    ),
    Tutorial(
        title = "Screenshot",
        subtitle = "Obrazovka, výřez nebo celá stránka",
        demo = DemoKind.ICON,
        icon = ButtonFunction.SCREENSHOT,
        steps = listOf(
            "Screenshot najdeš v boční liště. Funkci můžeš dát i libovolnému tlačítku nebo gestu.",
            "Nabídka: Celá obrazovka, Výběr části (táhni prstem a doladíš úchyty) a Celá stránka (scrollovací).",
            "„Rychlý screenshot“ vyfotí celou stránku rovnou, bez nabídky.",
            "Snímky jsou ve složce Pictures/ASAP Browser. Po uložení klepni na malý náhled a otevře se galerie."
        )
    ),
    Tutorial(
        title = "Adresní řádek, sdílení a QR kód",
        subtitle = "Zámek, Sdílet a nabídka na odkazech",
        demo = DemoKind.ICON,
        icon = ButtonFunction.SHARE_URL,
        steps = listOf(
            "Ikona vpravo v adresním řádku je Sdílet. Přepínačem zvolíš Odkaz nebo QR kód, pak Sdílet nebo Kopírovat.",
            "Zámek vlevo: zelený je HTTPS, červený HTTP (nezabezpečeno), šedý interní stránka. Klepnutím otevřeš Informace o stránce: certifikát, AdBlock, oprávnění webu a smazání cookies.",
            "Dlouhé podržení odkazu nebo obrázku na stránce nabídne otevřít na nové kartě, anonymně, zkopírovat, sdílet nebo stáhnout."
        )
    ),
    Tutorial(
        title = "Oblíbené a Záložky",
        subtitle = "Ukládání stránek do složek",
        demo = DemoKind.ICON,
        icon = ButtonFunction.OPEN_BOOKMARKS,
        steps = listOf(
            "Tlačítko Oblíbené: klepnutí otevře Záložky, držení přidá aktuální stránku do vybrané složky.",
            "Záložky jsou ve složkách a podsložkách. Stránku přidáš i přímo ve složce tlačítkem „přidat sem“.",
            "Funkce „Rychle přidat do Oblíbených“ uloží stránku jedním krokem. Ve výchozím stavu ji spouští gesto 9-8-7-4-1-2-3-6-9.",
            "Na úvodní stránce nové karty přepínáš mezi Nejnavštěvovanější a Oblíbené."
        )
    ),
    Tutorial(
        title = "Export a import",
        subtitle = "Přenos nastavení na jiný telefon",
        demo = DemoKind.ICON,
        icon = ButtonFunction.EXPORT,
        steps = listOf(
            "Nastavení → Export uloží vše do jednoho souboru: tlačítka, gesta, záložky, historii, otevřené karty i seznamy reklam.",
            "Soubor můžeš uložit do telefonu nebo poslat e-mailem jako přílohu.",
            "Na novém telefonu otevři Nastavení → Import a vyber soubor."
        ),
        tip = "Import nahradí záložky a historii. Před „Restartovat vše“ si vždy udělej Export."
    )
)

@Composable
internal fun TutorialsScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val sel = selected
    // zpet v detailu = zpet na seznam tutorialu (ma prednost pred zpetem z Nastaveni)
    BackHandler(enabled = sel != null) { selected = null }

    if (sel == null) {
        ScreenScaffold(title = "Tutoriály", onBack = onBack) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Vyber funkci a uvidíš ukázku i postup.", color = MUTED, fontSize = 13.sp)
                TUTORIALS.forEachIndexed { i, t -> SettingsRow(t.title, t.subtitle) { selected = i } }
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        val t = TUTORIALS[sel]
        ScreenScaffold(title = t.title, onBack = { selected = null }) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TutorialDemo(t)
                Text("POSTUP", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                t.steps.forEachIndexed { i, text -> TutorialStep(i + 1, text) }
                if (t.tip != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF14382A))
                            .border(1.dp, ACCENT, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text("Tip: " + t.tip, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TutorialStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(ACCENT),
            contentAlignment = Alignment.Center
        ) { Text(number.toString(), color = Color(0xFF06281D), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color(0xFFDDDDDD), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TutorialDemo(t: Tutorial) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141414))
            .border(1.dp, ROW, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (t.demo) {
            DemoKind.TAP_HOLD -> TapHoldDemo()
            DemoKind.GESTURE -> GestureDemo()
            DemoKind.FOLDER -> FolderDemo()
            DemoKind.ICON -> IconDemo(t.icon)
        }
    }
}

// Hodiny ukazky: hodnota 0..1 dokola za `millis` ms (cteni az v kresleni = bez zbytecne rekompozice)
@Composable
private fun demoClock(millis: Int): State<Float> {
    val tr = rememberInfiniteTransition(label = "demo")
    return tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = "demoT"
    )
}

// Pomerny postup t v useku a..b jako 0..1
private fun demoSeg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

// Text na stred bodu (cislice v gestech, odznak u slozky)
private fun DrawScope.demoText(text: String, cx: Float, cy: Float, sizePx: Float, argb: Int) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = argb
        textSize = sizePx
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy + sizePx * 0.35f, p)
}

// Ukazka 1: klepnuti (kratce) a drzeni (kruh se plni a "pukne")
@Composable
private fun TapHoldDemo() {
    val clock = demoClock(5200)
    val label by remember { derivedStateOf { if (clock.value < 0.4f) "Klepnutí" else "Držení" } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(170.dp, 130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                val c = center
                val r = 32.dp.toPx()
                val tapPhase = t < 0.4f
                val tap = demoSeg(t, 0.10f, 0.15f) - demoSeg(t, 0.18f, 0.26f)
                val hold = demoSeg(t, 0.48f, 0.85f)
                val pop = demoSeg(t, 0.85f, 0.95f)
                val press = if (tapPhase) tap else if (t < 0.85f) 1f else 1f - pop
                val finger = if (tapPhase) demoSeg(t, 0f, 0.10f) - demoSeg(t, 0.20f, 0.28f)
                else demoSeg(t, 0.40f, 0.48f) - demoSeg(t, 0.92f, 1f)

                val rr = r * (1f - 0.10f * press)
                drawCircle(ROW, rr, c)
                drawCircle(Color(0xFF555555), rr, c, style = Stroke(2.dp.toPx()))
                if (tapPhase && tap > 0f) {
                    drawCircle(ACCENT.copy(alpha = tap), rr + 6.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                }
                if (!tapPhase && t < 0.85f && hold > 0f) {
                    val ar = r + 10.dp.toPx()
                    drawArc(
                        ACCENT, -90f, 360f * hold, false,
                        Offset(c.x - ar, c.y - ar), Size(ar * 2, ar * 2),
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                if (pop > 0f && pop < 1f) {
                    drawCircle(ACCENT.copy(alpha = 1f - pop), r + 10.dp.toPx() + 22.dp.toPx() * pop, c, style = Stroke(3.dp.toPx()))
                }
                if (finger > 0f) {
                    val fc = Offset(c.x + 8.dp.toPx(), c.y + 14.dp.toPx() + 40.dp.toPx() * (1f - finger))
                    drawCircle(Color.White.copy(alpha = 0.30f * finger), 16.dp.toPx(), fc)
                    drawCircle(Color.White.copy(alpha = 0.55f * finger), 16.dp.toPx(), fc, style = Stroke(2.dp.toPx()))
                }
            }
            Image(painterResource(ButtonFunction.HOME.iconRes), contentDescription = null, modifier = Modifier.size(26.dp))
        }
        Text(label, color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// Ukazka 2: gesto 9-8-7-4-1 (vychozi "Nova karta") na plose 3x3
@Composable
private fun GestureDemo() {
    val clock = demoClock(4600)
    val code = "98741"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(150.dp)) {
            val t = clock.value
            val pad = 24.dp.toPx()
            val step = (size.width - 2 * pad) / 2f
            fun pos(k: Int) = Offset(pad + ((k - 1) % 3) * step, pad + ((k - 1) / 3) * step)
            val pts = code.map { pos(it - '0') }
            val prog = demoSeg(t, 0.10f, 0.75f) * (pts.size - 1)   // kolik useku je hotovo
            val fade = 1f - demoSeg(t, 0.90f, 1f)
            val reached = (prog.toInt() + 1).coerceAtMost(pts.size)

            for (i in 0 until pts.size - 1) {
                val f = (prog - i).coerceIn(0f, 1f)
                if (f > 0f) {
                    val a = pts[i]
                    val b = pts[i + 1]
                    drawLine(
                        ACCENT.copy(alpha = fade), a, Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f),
                        strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round
                    )
                }
            }
            for (k in 1..9) {
                val p = pos(k)
                val lit = code.indexOf('0' + k) in 0 until reached
                drawCircle(if (lit) ACCENT.copy(alpha = 0.35f + 0.65f * fade) else ROW, 14.dp.toPx(), p)
                drawCircle(if (lit) ACCENT else Color(0xFF555555), 14.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                demoText(k.toString(), p.x, p.y, 13.sp.toPx(), 0xFFDDDDDD.toInt())
            }
            val head = prog.toInt().coerceAtMost(pts.size - 2)
            val hf = (prog - head).coerceIn(0f, 1f)
            val a = pts[head]
            val b = pts[head + 1]
            val fa = demoSeg(t, 0.04f, 0.10f) * fade
            if (fa > 0f) {
                val fc = Offset(a.x + (b.x - a.x) * hf, a.y + (b.y - a.y) * hf)
                drawCircle(Color.White.copy(alpha = 0.30f * fa), 16.dp.toPx(), fc)
                drawCircle(Color.White.copy(alpha = 0.55f * fa), 16.dp.toPx(), fc, style = Stroke(2.dp.toPx()))
            }
        }
        Text("9 → 8 → 7 → 4 → 1 = Nová karta", color = MUTED, fontSize = 13.sp)
    }
}

// Ukazka 3: podrzet kartu za hlavicku, pretahnout na jinou -> vznikne slozka
@Composable
private fun FolderDemo() {
    val clock = demoClock(5600)
    val label by remember { derivedStateOf { if (clock.value < 0.62f) "Podrž za hlavičku a táhni" else "Vznikla složka" } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(230.dp, 120.dp)) {
            val t = clock.value
            val cw = 64.dp.toPx()
            val ch = 84.dp.toPx()
            val head = 16.dp.toPx()
            val corner = 10.dp.toPx()
            val g = demoSeg(t, 0f, 0.06f) * (1f - demoSeg(t, 0.92f, 1f))   // celkova pruhlednost (zacatek a konec smycky)
            val aC = Offset(size.width * 0.34f, size.height / 2)
            val bStart = Offset(size.width * 0.76f, size.height / 2 + 4.dp.toPx())
            val bEnd = Offset(aC.x + 9.dp.toPx(), aC.y + 7.dp.toPx())
            val drag = demoSeg(t, 0.28f, 0.58f)
            val bC = Offset(bStart.x + (bEnd.x - bStart.x) * drag, bStart.y + (bEnd.y - bStart.y) * drag)

            fun card(c: Offset) {
                val tl = Offset(c.x - cw / 2, c.y - ch / 2)
                drawRoundRect(Color(0xFF2E2E2E).copy(alpha = g), tl, Size(cw, ch), CornerRadius(corner))
                drawRoundRect(Color(0xFF4A4A4A).copy(alpha = g), tl, Size(cw, head), CornerRadius(corner))
                drawRect(Color(0xFF4A4A4A).copy(alpha = g), Offset(tl.x, tl.y + head / 2), Size(cw, head / 2))
                drawRoundRect(Color(0xFF666666).copy(alpha = g), tl, Size(cw, ch), CornerRadius(corner), style = Stroke(1.5.dp.toPx()))
            }
            card(aC)
            card(bC)

            // slozka: barevny ramecek kolem obou karet + odznak s poctem
            val m = demoSeg(t, 0.60f, 0.70f) * g
            if (m > 0f) {
                val left = aC.x - cw / 2 - 6.dp.toPx()
                val top = aC.y - ch / 2 - 6.dp.toPx()
                val w = (bEnd.x + cw / 2 + 6.dp.toPx()) - left
                val h = (bEnd.y + ch / 2 + 6.dp.toPx()) - top
                drawRoundRect(ACCENT.copy(alpha = m), Offset(left, top), Size(w, h), CornerRadius(14.dp.toPx()), style = Stroke(3.dp.toPx()))
                val bc = Offset(left + w, top)
                drawCircle(ACCENT.copy(alpha = m), 11.dp.toPx(), bc)
                demoText("2", bc.x, bc.y, 13.sp.toPx(), android.graphics.Color.argb((255 * m).toInt(), 6, 40, 29))
            }

            // prst na hlavicce presouvane karty (+ kruh "drzeni" na zacatku)
            val fa = (demoSeg(t, 0.12f, 0.18f) - demoSeg(t, 0.62f, 0.70f)) * g
            if (fa > 0f) {
                val fc = Offset(bC.x + 6.dp.toPx(), bC.y - ch / 2 + head / 2 + 8.dp.toPx())
                val lp = demoSeg(t, 0.16f, 0.28f)
                if (lp < 1f) {
                    val ar = 22.dp.toPx()
                    drawArc(
                        ACCENT.copy(alpha = fa), -90f, 360f * lp, false,
                        Offset(fc.x - ar, fc.y - ar), Size(ar * 2, ar * 2),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.30f * fa), 14.dp.toPx(), fc)
                drawCircle(Color.White.copy(alpha = 0.55f * fa), 14.dp.toPx(), fc, style = Stroke(2.dp.toPx()))
            }
        }
        Text(label, color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// Ukazka 4: tlacitko s ikonou funkce a dve vlny (obecna ukazka pro ostatni tutorialy)
@Composable
private fun IconDemo(fn: ButtonFunction) {
    val clock = demoClock(2400)
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val c = center
            val r = 34.dp.toPx()
            for (k in 0..1) {
                val p = (t + k * 0.5f) % 1f
                drawCircle(ACCENT.copy(alpha = 0.55f * (1f - p)), r + 26.dp.toPx() * p, c, style = Stroke(3.dp.toPx()))
            }
            drawCircle(ROW, r, c)
            drawCircle(ACCENT, r, c, style = Stroke(2.dp.toPx()))
        }
        Image(painterResource(fn.iconRes), contentDescription = null, modifier = Modifier.size(30.dp))
    }
}
