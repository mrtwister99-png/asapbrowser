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
// Barvy ve stejném duchu jako lišty v appce
private val BG = Color(0xFF0A0A0A)
private val PANEL = Color(0xFF1E1E1E)
private val ROW = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF00B055)
private val MUTED = Color(0xFF888888)
private val DANGER = Color(0xFFB91C1C)

// Nastavení → Stahování.

// ---------------------------------------------------------------- STAHOVÁNÍ

private fun dlStatusText(ctx: android.content.Context, it: com.example.actimelteambrowser.data.DlItem): String {
    fun size(b: Long) = android.text.format.Formatter.formatShortFileSize(ctx, b)
    return when (it.status) {
        android.app.DownloadManager.STATUS_SUCCESSFUL -> "Staženo · " + size(it.total.coerceAtLeast(it.done))
        android.app.DownloadManager.STATUS_FAILED -> "Selhalo"
        android.app.DownloadManager.STATUS_PAUSED -> "Pozastaveno · čeká na připojení"
        android.app.DownloadManager.STATUS_PENDING -> "Čeká na zahájení…"
        else -> if (it.total > 0) "Stahuji " + (it.done * 100 / it.total) + " %  (" + size(it.done) + " / " + size(it.total) + ")"
        else "Stahuji · " + size(it.done)
    }
}

@Composable
internal fun DownloadsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<com.example.actimelteambrowser.data.DlItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    // obnovuje se kazdou vterinu, dokud je obrazovka otevrena (prubeh stahovani)
    LaunchedEffect(tick) {
        items = withContext(Dispatchers.IO) { com.example.actimelteambrowser.data.DownloadsStore.query(ctx) }
        loaded = true
        delay(1000)
        tick++
    }
    // misto ulozeni: vychozi Stazene nebo slozka, kterou si uzivatel vybere (pristup k ni se pamatuje)
    var folderLabel by remember { mutableStateOf(com.example.actimelteambrowser.data.DownloadsStore.folderLabel(ctx)) }
    val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // predchozi vybranou slozku uz nepotrebujeme
            com.example.actimelteambrowser.data.DownloadsStore.folder(ctx)?.let { old ->
                if (old != uri) runCatching { ctx.contentResolver.releasePersistableUriPermission(old,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
            com.example.actimelteambrowser.core.settings.AppSettings.downloadFolder.set(uri.toString())
            folderLabel = com.example.actimelteambrowser.data.DownloadsStore.folderLabel(ctx)
            AppToast.show(ctx, "Soubory se budou ukládat do: $folderLabel")
        }.onFailure { AppToast.show(ctx, "Tuhle složku nejde použít") }
    }

    ScreenScaffold(title = "Stahování", onBack = onBack) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(PANEL).padding(14.dp)
        ) {
            Text("Místo uložení", color = MUTED, fontSize = 12.sp)
            Text("📁  $folderLabel", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(ACCENT).clickable { runCatching { pickFolder.launch(null) } },
                    contentAlignment = Alignment.Center
                ) { Text("Vybrat složku", color = Color(0xFF06281D), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (com.example.actimelteambrowser.data.DownloadsStore.folder(ctx) != null) Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(ROW).clickable {
                        com.example.actimelteambrowser.core.settings.AppSettings.downloadFolder.set("")
                        folderLabel = com.example.actimelteambrowser.data.DownloadsStore.folderLabel(ctx)
                        AppToast.show(ctx, "Soubory se budou ukládat do Stažených")
                    },
                    contentAlignment = Alignment.Center
                ) { Text("Výchozí (Stažené)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (loaded && items.isEmpty()) {
            EmptyText("Zatím nic nestaženo.\nSoubory se stahují podržením na odkazu nebo obrázku, případně klepnutím na odkaz na soubor.")
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { d ->
                val running = d.status == android.app.DownloadManager.STATUS_RUNNING ||
                    d.status == android.app.DownloadManager.STATUS_PENDING ||
                    d.status == android.app.DownloadManager.STATUS_PAUSED
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PANEL)
                        .clickable(enabled = d.status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                            // otevrit stazeny soubor
                            try {
                                val uri = com.example.actimelteambrowser.data.DownloadsStore.openUri(ctx, d)
                                val view = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                view.setDataAndType(uri, d.mime ?: "*/*")
                                view.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(view)
                            } catch (e: Exception) {
                                AppToast.show(ctx, "Soubor nejde otevřít")
                            }
                        }
                        .padding(14.dp)
                ) {
                    Text(d.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(dlStatusText(ctx, d), color = if (d.status == android.app.DownloadManager.STATUS_FAILED) Color(0xFFEF5350) else MUTED, fontSize = 13.sp)
                    if (running && d.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ROW)) {
                            Box(Modifier.fillMaxWidth((d.done.toFloat() / d.total).coerceIn(0f, 1f)).height(6.dp).background(ACCENT))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (running) {
                            if (d.uri == null) Text("Zrušit", color = Color(0xFFEF5350), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    com.example.actimelteambrowser.data.DownloadsStore.delete(ctx, d)
                                    tick++
                                })
                        } else {
                            Text("Odebrat ze seznamu", color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    com.example.actimelteambrowser.data.DownloadsStore.forget(ctx, d.id)
                                    tick++
                                })
                            Text("Smazat soubor", color = Color(0xFFEF5350), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    com.example.actimelteambrowser.data.DownloadsStore.delete(ctx, d)
                                    tick++
                                })
                        }
                    }
                }
            }
        }
    }
}
