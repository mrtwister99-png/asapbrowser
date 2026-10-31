package com.example.actimelteambrowser.ui

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
    val folderColor: Int = 0xFFCDA24D.toInt()   // barva slozky (jediné, co se u slozky da menit)
)

private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val GREEN = Color(0xFF00B055)
private val RED = Color(0xFFAC0001)
private val CARAMEL = Color(0xFFCDA24D)
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
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val headerPx = with(LocalDensity.current) { HEADER_H.toPx() }

    // otevrena slozka (zaporne id); kdyz slozka zanikne, vrati se hlavni prehled
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val openFolder = tiles.firstOrNull { it.folder && it.id == openFolderId }
    val openFolderNow by rememberUpdatedState(openFolder)
    val shown = openFolder?.members ?: tiles

    // oznacovani vice karet
    val selected = remember { mutableStateListOf<Long>() }
    var selectMode by remember { mutableStateOf(false) }
    var confirmIds by remember { mutableStateOf<List<Long>?>(null) }
    fun endSelect() { selected.clear(); selectMode = false }
    var colorPickFor by remember { mutableStateOf<Long?>(null) }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            // pohlti klepnuti, aby neprosla do stranky pod prehledem
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // prepinac: bezne karty / anonymni karty (druha skupina je skryta)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip("Běžné ($normalCount)", !incognito, Modifier.weight(1f)) {
                endSelect(); openFolderId = null; onToggleIncognito(false)
            }
            ModeChip("🕶 Anonymní ($incognitoCount)", incognito, Modifier.weight(1f)) {
                endSelect(); openFolderId = null; onToggleIncognito(true)
            }
        }

        // uzka listu: zpet ze slozky / pocet oznacenych
        if (openFolder != null || selectMode) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dropOut = openFolder != null && draggingId != null && finger.y < 0f
                Text(
                    when {
                        dropOut -> "Pusť sem pro vyjmutí"
                        openFolder != null -> "‹  Složka (${openFolder.members.size})"
                        else -> "Označeno: ${selected.size}"
                    },
                    color = if (dropOut) CARAMEL else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (openFolder != null) Modifier.clickable { endSelect(); openFolderId = null } else Modifier)
                )
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                            draggingId = null
                            mergeTarget = null
                            if (id != null && outOfFolder) requestExtractNow(listOf(id))
                            else if (id != null && mt != null) onMergeNow(id, mt)
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
                    modifier = itemModifier
                )
            }
        }

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
        } else {
            Box(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GREEN)
                    .clickable(onClick = onNew),
                contentAlignment = Alignment.Center
            ) {
                Text("+ NOVÁ KARTA", color = Color(0xFF06281D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // vyber barvy slozky
    val pickFor = colorPickFor
    if (pickFor != null) {
        Dialog(onDismissRequest = { colorPickFor = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .padding(22.dp)
            ) {
                Text("Barva složky", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                for (rowColors in FOLDER_COLORS.chunked(4)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (c in rowColors) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable {
                                        onFolderColor(pickFor, c.toInt())
                                        colorPickFor = null
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }

    // potvrzeni zavreni oznacenych karet / slozky
    val ids = confirmIds
    if (ids != null) {
        val count = ids.sumOf { id -> if (id < 0) (tiles.firstOrNull { it.id == id }?.members?.size ?: 0) else 1 }
        Dialog(onDismissRequest = { confirmIds = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(PANEL)
                    .padding(22.dp)
            ) {
                Text("Zavřít karty", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Opravdu chcete zavřít ${kartyText(count)}?", color = Color(0xFFCCCCCC), fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Text(
                        "Zrušit", color = Color(0xFFCCCCCC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(ROW)
                            .clickable { confirmIds = null }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                    Text(
                        "Zavřít", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(RED)
                            .clickable {
                                onCloseMany(ids)
                                confirmIds = null
                                endSelect()
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val folderColor = Color(tile.folderColor)
    val borderMod = when {
        mergeHit -> Modifier.border(3.dp, CARAMEL, shape)
        selected -> Modifier.border(3.dp, GREEN, shape)
        tile.folder -> Modifier.border(if (tile.active) 3.dp else 2.dp, folderColor, shape)
        tile.active -> Modifier.border(2.dp, GREEN, shape)
        else -> Modifier
    }
    Column(
        modifier
            .fillMaxWidth()
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
            val title = if (tile.folder) "Složka (${tile.members.size})"
            else (if (tile.incognito) "🕶 " else "") + tile.title.ifBlank { prettyUrl(tile.url).ifBlank { "Nová karta" } }
            Text(
                title,
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (!selectMode) {
                if (!tile.folder) {
                    Box(
                        Modifier.size(40.dp).clickable { onSave(tile.id) },
                        contentAlignment = Alignment.Center
                    ) { Text("★", color = GREEN, fontSize = 16.sp) }
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
                        .background(if (selected) GREEN else Color(0x99000000))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (selected) Text("✓", color = Color(0xFF06281D), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            if (mergeHit) {
                Text(
                    if (tile.folder) "Přidat do složky" else "Vytvořit složku",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
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
private fun ModeChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) GREEN else ROW)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color(0xFF06281D) else Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.Bold
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
            ) { Text("Normální surfování", color = Color(0xFF06281D), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E6BFF))
                    .clickable(onClick = onIncognito),
                contentAlignment = Alignment.Center
            ) { Text("Anonymní", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
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
                    color = GREEN, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1
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
                        Text(
                            label, color = if (on) Color(0xFF06281D) else Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
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
