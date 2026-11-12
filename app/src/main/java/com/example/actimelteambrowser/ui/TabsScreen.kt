package com.example.actimelteambrowser.ui

import com.example.actimelteambrowser.demoMark
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.actimelteambrowser.AppToast
import com.example.actimelteambrowser.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jedna dlaždice v přehledu karet. Složka (folder = true) má záporné id (-id skupiny)
 * a v members jsou dlaždice karet, které obsahuje.
 */
data class TabTile(
    val id: Long,
    val title: String,
    val url: String,
    val preview: Bitmap?,
    val active: Boolean,
    val incognito: Boolean = false,
    val folder: Boolean = false,
    val members: List<TabTile> = emptyList(),
    val folderColor: Int = 0xFFCDA24D.toInt(),  // barva slozky
    val folderName: String = "",                // nazev slozky (prazdny = "Složka")
    val color: Int = 0                          // barva karty (kolecko vpravo dole); 0 = zadna
)

/** Titulek slozky: nazev (nebo "Složka") a pocet karet. */
private fun folderTitle(t: TabTile) = "${t.folderName.ifBlank { "Složka" }} (${t.members.size})"

private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val GREEN = Color(0xFF00B055)
private val BLUE = Color(0xFF006AFF)   // hlavni barva v anonymnim rezimu
private val RED = Color(0xFFAC0001)
private val CARAMEL = Color(0xFFCDA24D)
private val YELLOW = Color(0xFFFFD600)
private val MUTED = Color(0xFF888888)
/** Barvy slozek na vyber. */
private val FOLDER_COLORS = listOf(
    0xFFCDA24D, 0xFF00B055, 0xFF1E6BFF, 0xFFAC0001, 0xFFD9FF00, 0xFF9C27B0, 0xFFFF7A00, 0xFFE91E63
)
private val HEADER_H = 40.dp   // vyska hlavicky dlazdice (nazev + hvezda + krizek)

/** Karty: 1 karta / 2-4 karty / 5 a vice karet (4. pad: "zavrit 1 kartu", "3 karty", "5 karet"). */
private fun kartyText(n: Int): String = when {
    n == 1 -> "1 kartu"
    n in 2..4 -> "$n karty"
    else -> "$n karet"
}

/**
 * Přehled otevřených karet jako dlaždice.
 *  - klepnutí = otevřít kartu (složku), držení za TĚLO = označování více karet,
 *  - držení za HLAVIČKU + tah: na hlavičku jiné karty = vytvoří složku (nebo přidá do složky),
 *    na tělo karty = přesune kartu na její místo.
 */
@Composable
fun TabsScreen(
    tiles: List<TabTile>,
    incognito: Boolean,
    normalCount: Int,
    incognitoCount: Int,
    onToggleIncognito: (Boolean) -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onSave: (Long) -> Unit,
    onMove: (Long, Long) -> Unit,
    onMoveInFolder: (Long, Long, Long) -> Unit,   // (složka, přesouvaná karta, karta na jejímž místě skončí)
    onMerge: (Long, Long) -> Unit,                // (přetažená karta, cíl = karta nebo složka)
    onCloseMany: (List<Long>) -> Unit,
    onExtract: (List<Long>) -> Unit,
    onFolderColor: (Long, Int) -> Unit,           // (složka, nová barva)
    onFolderRename: (Long, String) -> Unit,       // (složka, nový název)
    onNew: (Long?) -> Unit,                       // nova karta; id otevrene slozky = karta se prida do ni
    onDismiss: () -> Unit,
    session: Int = 0,                             // kazde otevreni prehledu = nova hodnota (slozka se zavre)
    groupsMode: Boolean = false,                  // zalozka Skupiny: jen skupiny karet
    onGroupsMode: () -> Unit = {},
    onNewGroup: () -> Unit = {},
    onTabColor: (Long, Int) -> Unit = { _, _ -> },   // (karta, nova barva)
    onDeleteDragged: ((Long) -> Unit)? = null     // karta pusteno na "Smazat kartu": smaze hned (toast s Vratit zpet)
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val accent = if (incognito) BLUE else GREEN   // zelena, v anonymnim rezimu modra
    val onAccent = if (incognito) Color.White else Color(0xFF06281D)
    val scope = rememberCoroutineScope()
    val headerPx = with(LocalDensity.current) { HEADER_H.toPx() }

    // otevrena slozka (zaporne id); kdyz slozka zanikne, vrati se hlavni prehled
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val openFolder = tiles.firstOrNull { it.folder && it.id == openFolderId }
    val openFolderNow by rememberUpdatedState(openFolder)
    val shown = openFolder?.members ?: tiles
    LaunchedEffect(session) { openFolderId = null }   // prehled se vzdy otevre na hlavni urovni, ne ve slozce

    // tazeni panelu slozky dolu = zavrit (jako v Chromu)
    var sheetDrag by remember { mutableFloatStateOf(0f) }
    var sheetDragging by remember { mutableStateOf(false) }
    val sheetOffset by animateFloatAsState(sheetDrag, if (sheetDragging) snap() else tween(180), label = "sheetDrag")
    val closePx = with(LocalDensity.current) { 110.dp.toPx() }
    LaunchedEffect(openFolderId) { sheetDrag = 0f }

    // oznacovani vice karet
    val selected = remember { mutableStateListOf<Long>() }
    var selectMode by remember { mutableStateOf(false) }
    var confirmIds by remember { mutableStateOf<List<Long>?>(null) }
    var deleteGroupId by remember { mutableStateOf<Long?>(null) }   // skupina pretazena na "Smazat skupinu karet" (ceka na potvrzeni)
    var gridW by remember { mutableFloatStateOf(0f) }               // sirka mrizky (pravy dolni roh = Smazat kartu)
    var gridH by remember { mutableFloatStateOf(0f) }               // vyska mrizky: prst pod ni = nad dolnim tlacitkem
    fun endSelect() { selected.clear(); selectMode = false }
    var colorPickFor by remember { mutableStateOf<Long?>(null) }
    var renameFor by remember { mutableStateOf<Long?>(null) }
    // po vytvoreni nove slozky (pusteni karty na kartu) se hned nabidne zadani nazvu
    var awaitNewFolder by remember { mutableStateOf(false) }
    var knownFolderIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(tiles) {
        val ids = tiles.filter { it.folder }.map { it.id }.toSet()
        if (awaitNewFolder) {
            val created = ids - (knownFolderIds ?: emptySet())
            if (created.isNotEmpty()) {
                renameFor = created.first()
                awaitNewFolder = false
            }
        }
        knownFolderIds = ids
    }
    val backgroundGridState = rememberLazyGridState()   // prehled karet za otevrenou slozkou (jen nahled)

    // vyjmuti karet ze slozky: kdyz by ve slozce zbyla jen 1 karta, slozka se nejdriv zmensi a vybledne (zanikne)
    var dissolving by remember { mutableStateOf(false) }
    var pendingExtract by remember { mutableStateOf<List<Long>>(emptyList()) }
    val vanish by animateFloatAsState(if (dissolving) 0f else 1f, tween(350), label = "folderVanish")
    fun requestExtract(ids: List<Long>) {
        val f = openFolder ?: return
        if (dissolving) return
        if (f.members.count { it.id !in ids } < 2) {
            pendingExtract = ids
            dissolving = true
        } else {
            onExtract(ids)
            endSelect()
        }
    }
    val requestExtractNow by rememberUpdatedState<(List<Long>) -> Unit>({ ids -> requestExtract(ids) })
    LaunchedEffect(dissolving) {
        if (dissolving) {
            delay(380)
            onExtract(pendingExtract)
            dissolving = false
            endSelect()
        }
    }
    fun toggleSel(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
        if (selected.isEmpty()) selectMode = false
    }
    LaunchedEffect(openFolder, openFolderId) {
        if (openFolderId != null && openFolder == null) {
            openFolderId = null
            endSelect()
        }
    }
    BackHandler(enabled = selectMode || openFolder != null) {
        if (selectMode) endSelect() else openFolderId = null
    }
    // uchopitelna cast panelu (drzadlo + hlavicka): tah dolu zavre slozku, kratky tah se vrati
    val sheetDragMod = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { sheetDragging = openFolderNow != null && !selectMode },
            onDragEnd = {
                if (sheetDragging && sheetDrag > closePx) {
                    endSelect()
                    openFolderId = null
                } else {
                    sheetDragging = false
                    sheetDrag = 0f
                }
                sheetDragging = false
            },
            onDragCancel = { sheetDragging = false; sheetDrag = 0f },
            onVerticalDrag = { change, amount ->
                if (sheetDragging) {
                    change.consume()
                    sheetDrag = (sheetDrag + amount).coerceAtLeast(0f)
                }
            }
        )
    }

    // --- pretahovani karet (drzeni za hlavicku + tah) ---
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }   // poloha prstu v mrizce
    var grab by remember { mutableStateOf(Offset.Zero) }     // kde v dlazdici jsme ji chytili
    var lastTarget by remember { mutableStateOf<Long?>(null) }
    var mergeTarget by remember { mutableStateOf<Long?>(null) }   // hlavicka, nad kterou prst je (vytvoreni slozky)
    var justDragged by remember { mutableStateOf(false) }    // po pusteni chvili ignorovat klepnuti
    val onMoveNow by rememberUpdatedState(onMove)
    val onMoveInFolderNow by rememberUpdatedState(onMoveInFolder)
    val onMergeNow by rememberUpdatedState(onMerge)

    fun hit(p: Offset) = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
        p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
            p.y >= it.offset.y && p.y < it.offset.y + it.size.height
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BG)
            // pohlti klepnuti, aby neprosla do stranky pod prehledem
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // otevrena slozka = jako v Chrome: za ni je videt (ztmaveny) prehled karet, klepnutim mimo se slozka zavre
        if (openFolder != null) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChip("Běžné ($normalCount)", !incognito && !groupsMode, Modifier.weight(1f)) {}
                    ModeChip("Skupiny", groupsMode, Modifier.weight(1f), yellow = true) {}
                    ModeChip("🕶 Anonymní ($incognitoCount)", incognito && !groupsMode, Modifier.weight(1f), blue = true) {}
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = backgroundGridState,
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tiles, key = { it.id }) { tile ->
                        TabTileCard(
                            tile = tile, selectMode = false, selected = false, mergeHit = false,
                            onClick = {}, onClose = {}, onSave = {}, onColor = {}
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000))
                    .pointerInput(Unit) { detectTapGestures { endSelect(); openFolderId = null } }
            )
        }
        Column(
            if (openFolder != null) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .graphicsLayer { translationY = sheetOffset }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF141414))
            } else Modifier.fillMaxSize()
        ) {
            // prepinac: bezne karty / anonymni karty (druha skupina je skryta); ve slozce je videt az za ni
            if (openFolder == null) Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip("Běžné ($normalCount)", !incognito && !groupsMode, Modifier.weight(1f).demoMark("tabs_normal")) {
                    endSelect(); openFolderId = null; onToggleIncognito(false)
                }
                ModeChip("Skupiny", groupsMode, Modifier.weight(1f), yellow = true) {
                    endSelect(); openFolderId = null; onGroupsMode()
                }
                ModeChip("🕶 Anonymní ($incognitoCount)", incognito && !groupsMode, Modifier.weight(1f).demoMark("tabs_incog"), blue = true) {
                    endSelect(); openFolderId = null; onToggleIncognito(true)
                }
            }

            // drzadlo panelu slozky (za nej jde panel stahnout dolu)
            if (openFolder != null) {
                Box(sheetDragMod.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF555555)))
                }
            }
            // uzka listu: zpet ze slozky / pocet oznacenych
            if (openFolder != null || selectMode) {
                Row(
                    sheetDragMod.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dropOut = openFolder != null && draggingId != null && finger.y < 0f
                    if (openFolder != null && !selectMode && !dropOut) {
                        // zpet ze slozky
                        Box(
                            Modifier.size(40.dp).clickable { endSelect(); openFolderId = null },
                            contentAlignment = Alignment.Center
                        ) { Image(painterResource(R.drawable.ic_back_chevron), contentDescription = "Zpět", modifier = Modifier.size(22.dp)) }
                    }
                    Text(
                        when {
                            dropOut -> "Pusť sem pro vyjmutí"
                            openFolder != null -> folderTitle(openFolder)
                            else -> "Označeno: ${selected.size}"
                        },
                        color = if (dropOut) CARAMEL else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            // klepnuti na nazev = prejmenovat slozku
                            .then(if (openFolder != null && !selectMode) Modifier.clickable { renameFor = openFolder.id } else Modifier)
                    )
                    if (openFolder != null && !selectMode && !dropOut) {
                        // ikona upravy nazvu (stejna jako "Upravit adresu" v naseptavaci)
                        Image(
                            painter = painterResource(R.drawable.ic_edit_url),
                            contentDescription = "Přejmenovat složku",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { renameFor = openFolder.id }
                                .padding(9.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (openFolder != null && !selectMode) {
                        // barva slozky
                        Box(
                            Modifier.size(40.dp).clickable { colorPickFor = openFolder.id },
                            contentAlignment = Alignment.Center
                        ) { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(openFolder.folderColor)).border(1.dp, Color.White, CircleShape)) }
                    }
                    if (selectMode) {
                        if (openFolder != null) {
                            Text(
                                "Vyjmout (${selected.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ROW)
                                    .clickable(enabled = selected.isNotEmpty()) {
                                        requestExtract(selected.toList())
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            "Vše", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ROW)
                                .clickable { selected.clear(); selected.addAll(shown.map { it.id }); AppToast.show(ctx, "Označeno: ${selected.size}") }   // oznacit vse
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Zrušit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ROW)
                                .clickable { endSelect() }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            if (tiles.isEmpty()) {
                Text(
                    if (incognito) "Žádné anonymní karty. Klepni na + NOVÁ KARTA." else "Žádné běžné karty. Klepni na + NOVÁ KARTA.",
                    color = MUTED, fontSize = 14.sp, modifier = Modifier.padding(16.dp)
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { gridH = it.height.toFloat(); gridW = it.width.toFloat() }
                    .graphicsLayer {
                        // zanik slozky po vyjmuti: mrizka se zmensi a vybledne
                        alpha = vanish
                        scaleX = 0.6f + 0.4f * vanish
                        scaleY = 0.6f + 0.4f * vanish
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                val h = hit(pos) ?: return@detectDragGesturesAfterLongPress
                                val key = h.key as? Long ?: return@detectDragGesturesAfterLongPress
                                val inHeader = pos.y < h.offset.y + headerPx
                                justDragged = true
                                if (selectMode || !inHeader) {
                                    // drzeni za telo (nebo uz oznacujeme): oznacit / odznacit kartu
                                    if (!selectMode) selectMode = true
                                    toggleSel(key)
                                    return@detectDragGesturesAfterLongPress
                                }
                                // drzeni za hlavicku: karta se chyta a tahne
                                draggingId = key
                                finger = pos
                                grab = Offset(pos.x - h.offset.x, pos.y - h.offset.y)
                                lastTarget = null
                                mergeTarget = null
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val id = draggingId
                                if (id != null) {
                                    finger += amount
                                    val under = hit(finger)
                                    val underKey = under?.key as? Long
                                    if (under == null || underKey == null || underKey == id) {
                                        lastTarget = null
                                        mergeTarget = null
                                    } else {
                                        val overHeader = finger.y < under.offset.y + headerPx
                                        // na hlavicku jine karty (slozky) jde pustit jen obycejnou kartu v hlavnim prehledu
                                        val canMerge = openFolderNow == null && id >= 0
                                        if (overHeader && canMerge) {
                                            mergeTarget = underKey
                                            lastTarget = null
                                        } else {
                                            mergeTarget = null
                                            if (underKey != lastTarget) {
                                                lastTarget = underKey
                                                val f = openFolderNow
                                                if (f != null) onMoveInFolderNow(f.id, id, underKey) else onMoveNow(id, underKey)
                                            }
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                val id = draggingId
                                val mt = mergeTarget
                                val outOfFolder = openFolderNow != null && finger.y < 0f   // pusteno nad mrizkou = vyjmout ze slozky
                                val toDelete = id != null && id < 0 && openFolderNow == null && finger.y > gridH   // skupina pusteno na dolni tlacitko
                                val toDeleteCard = id != null && id >= 0 && openFolderNow == null && mt == null && finger.y > gridH && finger.x > gridW / 2f   // karta pusteno na "Smazat kartu"
                                draggingId = null
                                mergeTarget = null
                                if (id != null && toDeleteCard) (onDeleteDragged ?: onClose).invoke(id)
                                else if (id != null && toDelete) deleteGroupId = id
                                else if (id != null && outOfFolder) requestExtractNow(listOf(id))
                                else if (id != null && mt != null) {
                                    if (mt >= 0) awaitNewFolder = true   // vznikne nova slozka
                                    onMergeNow(id, mt)
                                    AppToast.show(ctx, if (mt >= 0) "Složka vytvořena" else "Karta přidána do složky")
                                }
                                scope.launch {
                                    delay(250)
                                    justDragged = false
                                }
                            },
                            onDragCancel = {
                                draggingId = null
                                mergeTarget = null
                                scope.launch {
                                    delay(250)
                                    justDragged = false
                                }
                            }
                        )
                    },
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown, key = { it.id }) { tile ->
                    val dragged = tile.id == draggingId
                    val itemModifier = if (dragged) {
                        // taha se prstem: poloha se pocita z aktualniho rozlozeni mrizky
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tile.id }
                                if (info != null) {
                                    translationX = finger.x - grab.x - info.offset.x
                                    translationY = finger.y - grab.y - info.offset.y
                                }
                                scaleX = 1.05f
                                scaleY = 1.05f
                                alpha = 0.92f
                            }
                    } else {
                        Modifier.animateItem()
                    }
                    TabTileCard(
                        tile = tile,
                        selectMode = selectMode,
                        selected = tile.id in selected,
                        mergeHit = tile.id == mergeTarget,
                        onClick = {
                            if (!justDragged) {
                                if (selectMode) toggleSel(tile.id)
                                else if (tile.folder) openFolderId = tile.id
                                else onSelect(tile.id)
                            }
                        },
                        onClose = { id -> if (tile.folder) confirmIds = listOf(tile.id) else onClose(id) },
                        onSave = onSave,
                        onColor = { colorPickFor = tile.id },
                        onRename = { renameFor = tile.id },
                        modifier = itemModifier
                    )
                }
            }

            // stitek "Vytvorit slozku" / "Pridat do slozky": zluty a uplne nahore (nad pretahovanou kartou)
            val mt = mergeTarget
            if (mt != null && draggingId != null) {
                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mt }
                if (info != null) {
                    val tw = with(LocalDensity.current) { info.size.width.toDp() }
                    Box(
                        Modifier
                            .zIndex(10f)
                            .offset { androidx.compose.ui.unit.IntOffset(info.offset.x, info.offset.y + info.size.height - 56.dp.roundToPx()) }
                            .width(tw),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (mt < 0) "Přidat do složky" else "Vytvořit složku",
                            color = Color(0xFF1A1A1A), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(YELLOW)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            }   // konec Boxu s mrizkou

            if (selectMode) {
                // oznacovani: tlacitko se zmeni na cervene "Zavrit" s kosem
                Row(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected.isEmpty()) ROW else RED)
                        .clickable(enabled = selected.isNotEmpty()) { confirmIds = selected.toList() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.ic_close_all_tabs), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (selected.isEmpty()) "Označ karty" else "Zavřít (${selected.size})",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            } else if (draggingId.let { it != null && it < 0 } && openFolder == null) {
                // taha se skupina karet: tlacitko "Nova karta" se zmeni na cervene "Smazat skupinu karet" (pusteni na nej = dotaz)
                val over = finger.y > gridH
                Row(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (over) Color(0xFFE53935) else RED)
                        .then(if (over) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.ic_close_all_tabs), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Smazat skupinu karet", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else if (draggingId.let { it != null && it >= 0 } && openFolder == null) {
                // taha se karta: "Nova karta" se zmeni na dve tlacitka - vlevo co se stane po pusteni na kartu, vpravo Smazat kartu
                val mtNow = mergeTarget
                val overDel = finger.y > gridH && finger.x > gridW / 2f
                LaunchedEffect(overDel) { if (overDel) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
                val delScale by animateFloatAsState(if (overDel) 1.07f else 1f, tween(120), label = "delScale")
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().height(54.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (mtNow != null) YELLOW else ROW),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (mtNow != null && mtNow < 0) "Přidat do složky" else "+ Vytvořit složku",
                            color = if (mtNow != null) Color(0xFF1A1A1A) else MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        Modifier
                            .weight(1f).fillMaxHeight()
                            .graphicsLayer { scaleX = delScale; scaleY = delScale }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (overDel) Color(0xFFE53935) else RED)
                            .then(if (overDel) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painterResource(R.drawable.ic_close_all_tabs), contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Smazat kartu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (groupsMode) YELLOW else accent)
                        .clickable { if (groupsMode) onNewGroup() else onNew(openFolderId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (groupsMode) "+ NOVÁ SKUPINA" else "+ NOVÁ KARTA",
                        color = if (groupsMode) Color(0xFF1A1A1A) else onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // prejmenovani slozky
    val renameId = renameFor
    if (renameId != null) {
        // kurzor na konci stavajiciho nazvu
        var field by remember(renameId) {
            val cur = tiles.firstOrNull { it.id == renameId }?.folderName.orEmpty()
            mutableStateOf(TextFieldValue(cur, TextRange(cur.length)))
        }
        val text = field.text
        val focus = remember { FocusRequester() }
        LaunchedEffect(renameId) { focus.requestFocus() }
        Dialog(onDismissRequest = { renameFor = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .padding(22.dp)
            ) {
                Text("Název složky", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ROW)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { if (it.text.length <= 40) field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                    if (text.isEmpty()) Text("Složka", color = MUTED, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Text(
                        "Zrušit", color = Color(0xFFCCCCCC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(ROW)
                            .clickable { renameFor = null }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                    Text(
                        "Uložit", color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent)
                            .clickable {
                                onFolderRename(renameId, text)
                                renameFor = null
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // vyber barvy slozky / karty: prvni stranka 5 + 5 nahodnych barev, posunem doprava dalsi (vsechny barvy)
    val pickFor = colorPickFor
    if (pickFor != null) {
        val pages = remember(pickFor) { com.example.actimelteambrowser.TabColors.pickerPages() }
        val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
        Dialog(onDismissRequest = { colorPickFor = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .padding(vertical = 22.dp)
            ) {
                Text(
                    if (pickFor < 0) "Barva složky" else "Barva karty",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp)
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (rowColors in pages[page].chunked(5)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                for (c in rowColors) {
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(c))
                                            .border(2.dp, Color.White, CircleShape)
                                            .clickable {
                                                if (pickFor < 0) onFolderColor(pickFor, c) else onTabColor(pickFor, c)
                                                colorPickFor = null
                                            }
                                    )
                                }
                                repeat(5 - rowColors.size) { Spacer(Modifier.size(42.dp)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // tecky stranek
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (i in pages.indices) {
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) Color.White else MUTED)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Posuň doprava pro další barvy", color = MUTED, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    // potvrzeni zavreni oznacenych karet / slozky
    // potvrzeni smazani skupiny (po pretazeni skupiny na cervene tlacitko)
    val delId = deleteGroupId
    if (delId != null) {
        val group = tiles.firstOrNull { it.id == delId }
        ConfirmDialog(
            title = "Smazat skupinu",
            // pocet karet, ktere se smazou spolu se skupinou
            text = "Opravdu chcete smazat skupinu ${group?.folderName?.ifBlank { "Složka" } ?: "Složka"}? Smaže se ${kartyText(group?.members?.size ?: 0)}.",
            onNo = { deleteGroupId = null },
            onYes = { onCloseMany(listOf(delId)); deleteGroupId = null }
        )
    }

    val ids = confirmIds
    if (ids != null) {
        val count = ids.sumOf { id -> if (id < 0) (tiles.firstOrNull { it.id == id }?.members?.size ?: 0) else 1 }
        ConfirmDialog(
            title = "Zavřít karty",
            text = "Opravdu chcete zavřít ${kartyText(count)}?",
            noLabel = "Zrušit", yesLabel = "Zavřít",
            onNo = { confirmIds = null },
            onYes = { onCloseMany(ids); confirmIds = null; endSelect() }
        )
    }
}

@Composable
private fun TabTileCard(
    tile: TabTile,
    selectMode: Boolean,
    selected: Boolean,
    mergeHit: Boolean,
    onClick: () -> Unit,
    onClose: (Long) -> Unit,
    onSave: (Long) -> Unit,
    onColor: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    val folderColor = Color(tile.folderColor)
    val accent = if (tile.incognito) BLUE else GREEN
    val borderMod = when {
        mergeHit -> Modifier.border(3.dp, YELLOW, shape)
        selected -> Modifier.border(3.dp, accent, shape)
        tile.folder -> Modifier.border(if (tile.active) 3.dp else 2.dp, folderColor, shape)
        tile.active -> Modifier.border(2.dp, accent, shape)
        else -> Modifier
    }
    val stretch by animateFloatAsState(if (mergeHit && tile.folder) 1.05f else 1f, tween(160), label = "folderStretch")   // cil-slozka se jemne roztahne
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = stretch; scaleY = stretch }
            .clip(shape)
            .background(PANEL)
            .then(borderMod)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(HEADER_H)
                .background(if (tile.folder) folderColor.copy(alpha = 0.28f) else Color.Transparent)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = if (tile.folder) folderTitle(tile)
            else (if (tile.incognito) "🕶 " else "") + tile.title.ifBlank { prettyUrl(tile.url).ifBlank { "Nová karta" } }
            Text(
                title,
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    // klepnuti na nazev slozky = prejmenovat
                    .then(if (tile.folder && !selectMode) Modifier.clickable { onRename() } else Modifier)
            )
            if (!selectMode) {
                if (!tile.folder) {
                    Box(
                        Modifier.size(40.dp).clickable { onSave(tile.id) },
                        contentAlignment = Alignment.Center
                    ) { Text("★", color = accent, fontSize = 16.sp) }
                } else {
                    // barva slozky: klepnuti otevre vyber
                    Box(
                        Modifier.size(40.dp).clickable { onColor() },
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(16.dp).clip(CircleShape).background(folderColor).border(1.dp, Color.White, CircleShape)) }
                }
                Box(
                    Modifier.size(40.dp).clickable { onClose(tile.id) },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = MUTED, fontSize = 15.sp) }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(ROW),
            contentAlignment = Alignment.Center
        ) {
            if (tile.folder) {
                FolderBody(tile.members)
            } else {
                if (tile.color != 0 && !selectMode) {
                    // barva karty: klepnuti otevre vyber (zmena je videt i v liste karet)
                    Box(
                        Modifier.align(Alignment.BottomEnd).zIndex(1f).padding(4.dp).size(36.dp).clip(CircleShape).clickable { onColor() },
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(tile.color)).border(1.5.dp, Color.White, CircleShape)) }
                }
                val p = tile.preview
                if (p != null) {
                    Image(
                        bitmap = p.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        prettyUrl(tile.url).firstOrNull()?.uppercase() ?: "•",
                        color = MUTED, fontSize = 40.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            if (selectMode) {
                // kolecko vpravo nahore: vyplnene zelene s fajfkou = oznaceno
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else Color(0x99000000))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (selected) Text("✓", color = Color(0xFF06281D), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Telo slozky: nahledy prvnich 4 karet v mrizce 2x2. */
@Composable
private fun FolderBody(members: List<TabTile>) {
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (r in 0 until 2) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 2) {
                    val m = members.getOrNull(r * 2 + c)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (m != null) PANEL else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        val p = m?.preview
                        if (p != null) {
                            Image(
                                bitmap = p.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (m != null) {
                            Text(
                                prettyUrl(m.url).firstOrNull()?.uppercase() ?: "•",
                                color = MUTED, fontSize = 22.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, modifier: Modifier, blue: Boolean = false, yellow: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) (if (blue) BLUE else if (yellow) YELLOW else GREEN) else ROW)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) (if (blue) Color.White else if (yellow) Color(0xFF1A1A1A) else Color(0xFF06281D)) else Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}


// ---------------------------------------------------------------- UVODNI STRANKA NOVE KARTY

/** Jedna dlazdice nejnavstevovanejsich stranek: host pro zobrazeni, adresa pro otevreni a nahled stranky (kdyz je). */
data class StartSite(val host: String, val url: String, val thumb: Bitmap? = null, val icon: Bitmap? = null)

/**
 * Uvodni stranka nove karty: nahore zelene tlacitko "Normalni surfovani" a modre "Anonymni",
 * pod nimi pocitadlo zablokovanych reklam a mrizka nejnavstevovanejsich stranek (3 sloupce, s nahledem stranky).
 */
@Composable
fun StartPage(
    sites: List<StartSite>,
    favorites: List<StartSite>,
    showFavorites: Boolean,
    onShowFavorites: (Boolean) -> Unit,
    blockedTotal: Int,
    onNormal: () -> Unit,
    onIncognito: () -> Unit,
    onOpen: (StartSite) -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .weight(2.2f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF00B055))
                    .clickable(onClick = onNormal),
                contentAlignment = Alignment.Center
            ) {
                // ikonka surfu nad textem
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(R.drawable.ic_surf), contentDescription = null, modifier = Modifier.size(28.dp))
                    Text("Normální surfování", color = Color(0xFF06281D), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E6BFF))
                    .clickable(onClick = onIncognito),
                contentAlignment = Alignment.Center
            ) {
                // ikonka anonymu (stejna jako v tutorialech) nad textem
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(R.drawable.ic_incognito), contentDescription = null, modifier = Modifier.size(26.dp))
                    Text("Anonymní", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // pocitadlo zablokovanych reklam (40 % sirky) + prepinac Nejnavstevovanejsi / Oblibene
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .weight(0.4f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PANEL)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("CELKEM ZABLOKOVÁNO REKLAM", color = MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    java.text.NumberFormat.getInstance(java.util.Locale("cs")).format(blockedTotal),
                    color = Color(0xFFFFC107), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1   // zlata
                )
            }
            Column(Modifier.weight(0.6f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, fav) in listOf("Nejnavštěvovanější" to false, "Oblíbené" to true)) {
                    val on = showFavorites == fav
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) GREEN else PANEL)
                            .clickable { onShowFavorites(fav) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // ikonka pred nazvem: hodiny (nejnavstevovanejsi) / zluta hvezdicka (oblibene)
                            if (fav) Text("★", color = Color(0xFFFFC107), fontSize = 16.sp)
                            else Text("🕒", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label, color = if (on) Color(0xFF06281D) else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        val list = if (showFavorites) favorites else sites
        Text(if (showFavorites) "OBLÍBENÉ" else "NEJNAVŠTĚVOVANĚJŠÍ", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (showFavorites && favorites.isEmpty()) {
            Text("Zatím nemáš žádné oblíbené stránky.", color = MUTED, fontSize = 14.sp)
        }
        for (row in list.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (site in row) {
                    // stejny vzhled jako dlazdice v prehledu karet: nahled stranky + nazev
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PANEL)
                            .clickable { onOpen(site) }
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.8f).background(ROW),
                            contentAlignment = Alignment.Center
                        ) {
                            val t = site.thumb
                            if (t != null) {
                                Image(
                                    bitmap = t.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (site.icon != null) {
                                // bez nahledu stranky: aspon ikona webu
                                Image(
                                    bitmap = site.icon.asImageBitmap(), contentDescription = null,
                                    contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp)
                                )
                            } else {
                                Text(
                                    site.host.firstOrNull()?.uppercase() ?: "?",
                                    color = GREEN, fontSize = 30.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ikona webu (favicon) pred nazvem
                            if (site.icon != null) {
                                Image(
                                    bitmap = site.icon.asImageBitmap(), contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                site.host, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // doplnit prazdna mista v posledni rade, aby dlazdice mely stejnou sirku
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
