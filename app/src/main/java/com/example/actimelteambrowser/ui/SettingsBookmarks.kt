package com.example.actimelteambrowser.ui

import com.example.actimelteambrowser.softBuzz
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


// Nastavení → Záložky: seznam složek a detail složky.

// ---------------------------------------------------------------- ZÁLOŽKY

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderListScreen(onOpenFolder: (BookmarkFolder) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BookmarksRepository(context) }
    var folders by remember { mutableStateOf<List<FolderWithCount>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FolderWithCount?>(null) }
    var renameTarget by remember { mutableStateOf<FolderWithCount?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        folders = withContext(Dispatchers.IO) { repo.foldersWithCounts() }
    }

    ScreenScaffold(title = "Záložky", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(folders, key = { it.folder.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PANEL)
                        .combinedClickable(
                            onClick = { onOpenFolder(item.folder) },
                            // dlouhy stisk = prejmenovat (i vestavene slozky)
                            onLongClick = { renameTarget = item }
                        )
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.folder.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text("${item.count}", color = MUTED, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    // vestavene slozky (Oblibene, Dalsi) se mazat nedaji
                    if (!item.folder.isBuiltIn) {
                        Box(
                            Modifier.size(40.dp).clickable { deleteTarget = item },
                            contentAlignment = Alignment.Center
                        ) { Text("🗑", fontSize = 16.sp) }
                    }
                }
            }
        }
        BigButton("+ NOVÁ SLOŽKA", ACCENT, Color(0xFF06281D), Modifier.demoMark("new_folder")) { showNew = true }
    }

    renameTarget?.let { target ->
        var newName by remember(target.folder.id) { mutableStateOf(target.folder.name) }
        StyledDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Přejmenovat složku") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Název složky") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newName.trim()
                    renameTarget = null
                    if (n.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.renameFolder(target.folder, n) }
                            reloadKey += 1
                        }
                    }
                }) { Text("Uložit") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Zrušit") } }
        )
    }

    deleteTarget?.let { target ->
        StyledDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Smazat složku?") },
            text = { Text("Složka \"${target.folder.name}\" a všechny záložky v ní (${target.count}) se smažou.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteFolder(target.folder) }
                        reloadKey += 1
                    }
                }) { Text("Smazat") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Zrušit") } }
        )
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        StyledDialog(
            onDismissRequest = { showNew = false },
            title = { Text("Nová složka") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Název složky") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    showNew = false
                    if (n.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.createFolder(n) }
                            reloadKey += 1
                        }
                    }
                }) { Text("Vytvořit") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Zrušit") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderDetailScreen(
    folder: BookmarkFolder,
    currentUrl: String?,
    currentTitle: String,
    onOpenFolder: (BookmarkFolder) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { BookmarksRepository(context) }
    var subs by remember { mutableStateOf<List<BookmarkFolder>>(emptyList()) }
    var marks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var otherFolders by remember { mutableStateOf<List<BookmarkFolder>>(emptyList()) }
    var moveTarget by remember { mutableStateOf<Bookmark?>(null) }
    var renameTarget by remember { mutableStateOf<BookmarkFolder?>(null) }
    var deleteTarget by remember { mutableStateOf<BookmarkFolder?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // --- pretahovani zalozek: dlouhy stisk + tah ---
    val listState = rememberLazyListState()
    var dragKey by remember { mutableStateOf<String?>(null) }      // "b<ID>"
    var fingerY by remember { mutableStateOf(0f) }
    var grabY by remember { mutableStateOf(0f) }
    var hoverKey by remember { mutableStateOf<String?>(null) }     // cil, nad kterym prst "visi"
    var mergeReady by remember { mutableStateOf(false) }           // visi dost dlouho -> vznikne slozka
    var lastSwap by remember { mutableStateOf<String?>(null) }
    var justDragged by remember { mutableStateOf(false) }

    LaunchedEffect(folder.id, reloadKey) {
        subs = withContext(Dispatchers.IO) { repo.subfolders(folder.id) }
        marks = withContext(Dispatchers.IO) { repo.bookmarksIn(folder.id) }
        otherFolders = withContext(Dispatchers.IO) { repo.foldersForPicker(folder.id) }
        loaded = true
    }

    // zalozka musi nad cilem chvili viset (0,45 s), aby se omylem nevytvorila slozka
    LaunchedEffect(hoverKey) {
        mergeReady = false
        if (hoverKey != null) {
            delay(450)
            mergeReady = true
        }
    }

    moveTarget?.let { target ->
        StyledDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Přesunout do složky") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    otherFolders.forEach { f ->
                        TextButton(
                            onClick = {
                                moveTarget = null
                                marks = marks.filterNot { it.id == target.id }
                                scope.launch(Dispatchers.IO) { repo.moveBookmark(target.id, f.id) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(f.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Zrušit") } }
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        StyledDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Název složky") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Název složky") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newName.trim()
                    renameTarget = null
                    if (n.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.renameFolder(target, n) }
                            reloadKey += 1
                        }
                    }
                }) { Text("Uložit") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Později") } }
        )
    }

    deleteTarget?.let { target ->
        StyledDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Smazat složku?") },
            text = { Text("Složka \"${target.name}\" a všechny záložky v ní se smažou.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteFolder(target) }
                        reloadKey += 1
                    }
                }) { Text("Smazat") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Zrušit") } }
        )
    }

    ScreenScaffold(title = folder.name, onBack = onBack) {
        if (loaded && subs.isEmpty() && marks.isEmpty()) {
            EmptyText("Ve složce zatím nic není")
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                pos.y >= it.offset && pos.y < it.offset + it.size
                            }
                            val key = hit?.key as? String
                            // pretahovat se daji jen zalozky (klic zacina "b")
                            if (hit != null && key != null && key.startsWith("b")) {
                                dragKey = key
                                fingerY = pos.y
                                grabY = pos.y - hit.offset
                                hoverKey = null
                                lastSwap = null
                                justDragged = true
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val dk = dragKey
                            if (dk != null) {
                                fingerY += amount.y
                                val under = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    fingerY >= it.offset && fingerY < it.offset + it.size
                                }
                                val uk = under?.key as? String
                                if (under == null || uk == null || uk == dk) {
                                    hoverKey = null
                                    lastSwap = null
                                } else if (uk.startsWith("f")) {
                                    // nad slozkou: po pusteni se zalozka do ni vlozi
                                    hoverKey = uk
                                } else {
                                    val rel = (fingerY - under.offset) / under.size
                                    if (rel in 0.3f..0.7f) {
                                        // stred jine zalozky: po chvili vznikne slozka
                                        hoverKey = uk
                                    } else {
                                        hoverKey = null
                                        // okraj jine zalozky: prohodit poradi
                                        if (uk != lastSwap) {
                                            val from = marks.indexOfFirst { "b" + it.id == dk }
                                            val to = marks.indexOfFirst { "b" + it.id == uk }
                                            if (from >= 0 && to >= 0) {
                                                val m = marks.toMutableList()
                                                val moved = m.removeAt(from)
                                                m.add(to, moved)
                                                marks = m
                                                lastSwap = uk
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val dk = dragKey
                            val hk = hoverKey
                            val ready = mergeReady
                            dragKey = null
                            hoverKey = null
                            mergeReady = false
                            if (dk != null) {
                                val dragId = dk.substring(1).toLong()
                                if (hk != null && hk.startsWith("f")) {
                                    val targetId = hk.substring(1).toLong()
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repo.moveBookmark(dragId, targetId) }
                                        reloadKey += 1
                                    }
                                } else if (hk != null && hk.startsWith("b") && ready) {
                                    val otherId = hk.substring(1).toLong()
                                    scope.launch {
                                        val created = withContext(Dispatchers.IO) {
                                            repo.mergeIntoNewFolder(folder.id, dragId, otherId)
                                        }
                                        reloadKey += 1
                                        renameTarget = created   // hned nabidnout pojmenovani
                                    }
                                } else {
                                    val ids = marks.map { it.id }
                                    scope.launch(Dispatchers.IO) { repo.reorderBookmarks(ids) }
                                }
                            }
                            scope.launch {
                                delay(250)
                                justDragged = false
                            }
                        },
                        onDragCancel = {
                            dragKey = null
                            hoverKey = null
                            mergeReady = false
                            reloadKey += 1
                            scope.launch {
                                delay(250)
                                justDragged = false
                            }
                        }
                    )
                },
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            // podslozky nahore
            items(subs, key = { "f" + it.id }) { sub ->
                val key = "f" + sub.id
                val hovered = hoverKey == key
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (hovered) Color(0xFF14382A) else PANEL)
                        .then(if (hovered) Modifier.border(2.dp, ACCENT, RoundedCornerShape(14.dp)) else Modifier)
                        .combinedClickable(
                            onClick = { if (!justDragged) onOpenFolder(sub) },
                            onLongClick = { renameTarget = sub }
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        sub.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.size(40.dp).clickable { deleteTarget = sub },
                        contentAlignment = Alignment.Center
                    ) { Text("🗑", fontSize = 16.sp) }
                }
            }
            // zalozky (pretahovatelne)
            items(marks, key = { "b" + it.id }) { b ->
                val key = "b" + b.id
                val dragged = dragKey == key
                val hovered = hoverKey == key
                val content: @Composable () -> Unit = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (hovered) Modifier
                                .background(if (mergeReady) Color(0xFF14382A) else Color(0xFF1B1B1B))
                                .border(2.dp, ACCENT)
                            else Modifier
                        )
                        .clickable { if (!justDragged) onOpenUrl(b.url) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteIconLite(b.url)   // ve slozce bez nahledu stranky (rychle), jen ikona webu
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            b.title.ifBlank { prettyUrl(b.url) }, color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(prettyUrl(b.url), color = MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (otherFolders.isNotEmpty()) {
                        Box(
                            Modifier.size(40.dp).clickable { moveTarget = b },
                            contentAlignment = Alignment.Center
                        ) { Text("⇄", color = MUTED, fontSize = 16.sp) }
                    }
                    Box(
                        Modifier.size(40.dp).clickable {
                            marks = marks.filterNot { it.id == b.id }
                            scope.launch(Dispatchers.IO) { repo.deleteBookmark(b.id) }
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("✕", color = MUTED, fontSize = 16.sp) }
                }
                }
                if (dragged) {
                    // zalozka se za prstem nese (nahore) a v seznamu zustava pulprusvitne misto, kam dopadne
                    Box(Modifier.zIndex(1f)) {
                        Box(Modifier.alpha(0.5f)) { content() }
                        Box(
                            Modifier
                                .graphicsLayer {
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                    if (info != null) translationY = fingerY - grabY - info.offset
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    alpha = 0.92f
                                }
                                .background(Color(0xFF2F2F2F))
                        ) { content() }
                    }
                } else {
                    Box(Modifier.animateItem()) { content() }
                }
            }
        }
        // pridat aktualni stranku primo do teto slozky
        if (currentUrl != null && (currentUrl.startsWith("https://") || currentUrl.startsWith("http://"))) {
            BigButton("+ PŘIDAT AKTUÁLNÍ STRÁNKU SEM", ACCENT, Color(0xFF06281D)) {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.addBookmark(folder.id, currentUrl, currentTitle) }
                    context.softBuzz(35)   // ulozeni do Oblibenych: lehce zabrni
                    reloadKey += 1
                }
            }
        }
    }
}
