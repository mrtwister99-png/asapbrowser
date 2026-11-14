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


// Nastavení → Historie (s náhledy stránek).

// ---------------------------------------------------------------- HISTORIE

private enum class ClearRange(val label: String) {
    TODAY("Dnes"),
    YESTERDAY("Včera"),
    LAST_7_DAYS("Posledních 7 dní"),
    LAST_14_DAYS("Posledních 14 dní"),
    ALL("Celou historii")
}

@Composable
internal fun HistoryScreen(onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }
    var visits by remember { mutableStateOf<List<HistoryVisit>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var showClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        visits = withContext(Dispatchers.IO) { repo.recent(500) }
        loaded = true
    }

    ScreenScaffold(title = "Historie", onBack = onBack) {
        if (loaded && visits.isEmpty()) {
            EmptyText("Zatím žádná historie")
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
                groupByDay(visits).forEach { (label, dayVisits) ->
                    item(key = "header-$label") {
                        Text(
                            label.uppercase(), color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }
                    items(dayVisits, key = { it.id }) { v ->
                        HistoryRow(
                            visit = v,
                            onClick = { onOpenUrl(v.url) },
                            onDelete = {
                                visits = visits.filterNot { it.id == v.id }
                                scope.launch(Dispatchers.IO) { repo.deleteEntry(v.id) }
                            }
                        )
                    }
                }
            }
            if (visits.isNotEmpty()) BigButton("VYMAZAT HISTORII", DANGER, Color.White) { showClear = true }
        }
    }

    if (showClear) {
        StyledDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Vymazat historii") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClearRange.entries.forEach { range ->
                        TextButton(
                            onClick = {
                                showClear = false
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        when (range) {
                                            ClearRange.TODAY -> repo.clearToday()
                                            ClearRange.YESTERDAY -> repo.clearYesterday()
                                            ClearRange.LAST_7_DAYS -> repo.clearLastDays(7)
                                            ClearRange.LAST_14_DAYS -> repo.clearLastDays(14)
                                            ClearRange.ALL -> repo.clearAll()
                                        }
                                    }
                                    reloadKey += 1
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(range.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Zrušit") } }
        )
    }
}

/** Ulozene nahledy a ikony webu (vznikaji pri navsteve stranky, viz MainActivity: site_thumbs / site_icons). */
private object SiteAssets {
    private fun name(host: String) = host.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    private fun decode(f: java.io.File): android.graphics.Bitmap? =
        try { if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path) else null } catch (e: Exception) { null }
    fun thumb(ctx: android.content.Context, host: String): android.graphics.Bitmap? = if (!AppSettings.thumbsOn.get()) null else decode(java.io.File(java.io.File(ctx.filesDir, "site_thumbs"), name(host) + ".jpg"))
    fun icon(ctx: android.content.Context, host: String): android.graphics.Bitmap? = if (!AppSettings.thumbsOn.get()) null else decode(java.io.File(java.io.File(ctx.filesDir, "site_icons"), name(host) + ".png"))
}

/**
 * Nejlehci varianta pro dlouhe seznamy: jen mala ikona webu (bez nacitani velkeho nahledu stranky), jinak prvni pismeno.
 * Ikona se nacte na pozadi, a to jen jednou pro kazdy host.
 */
@Composable
internal fun SiteIconLite(url: String) {
    val ctx = LocalContext.current
    val host = remember(url) { try { android.net.Uri.parse(url).host?.removePrefix("www.").orEmpty() } catch (e: Exception) { "" } }
    val icon by produceState<android.graphics.Bitmap?>(null, host) {
        value = if (host.isBlank()) null else withContext(Dispatchers.IO) { SiteAssets.icon(ctx, host) }
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ROW),
        contentAlignment = Alignment.Center
    ) {
        val ic = icon
        if (ic != null) {
            Image(bitmap = ic.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
        } else {
            Text(
                prettyUrl(url).firstOrNull()?.uppercase() ?: "•",
                color = ACCENT, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Maly nahled stranky s ikonou webu v rohu; bez nahledu jen ikona, bez ikony prvni pismeno adresy. */
@Composable
internal fun SiteThumb(url: String) {
    val ctx = LocalContext.current
    val host = remember(url) { try { android.net.Uri.parse(url).host?.removePrefix("www.").orEmpty() } catch (e: Exception) { "" } }
    val assets by produceState<Pair<android.graphics.Bitmap?, android.graphics.Bitmap?>>(Pair(null, null), host) {
        value = if (host.isBlank()) Pair(null, null)
        else withContext(Dispatchers.IO) { Pair(SiteAssets.thumb(ctx, host), SiteAssets.icon(ctx, host)) }
    }
    val (thumb, icon) = assets
    Box(
        Modifier.size(width = 52.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(ROW),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()
            )
            if (icon != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(3.dp).size(20.dp).clip(RoundedCornerShape(5.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Image(bitmap = icon.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(15.dp)) }
            }
        } else if (icon != null) {
            Image(bitmap = icon.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
        } else {
            Text(
                prettyUrl(url).firstOrNull()?.uppercase() ?: "•",
                color = ACCENT, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HistoryRow(visit: HistoryVisit, onClick: () -> Unit, onDelete: () -> Unit) {
    val time = remember(visit.visitedAt) {
        SimpleDateFormat("HH:mm", Locale.forLanguageTag("cs")).format(Date(visit.visitedAt))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(time, color = MUTED, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        SiteThumb(visit.url)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                visit.title.ifBlank { prettyUrl(visit.url) }, color = Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(prettyUrl(visit.url), color = MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Text("✕", color = MUTED, fontSize = 16.sp)
        }
    }
}

private fun groupByDay(visits: List<HistoryVisit>): List<Pair<String, List<HistoryVisit>>> {
    val today = dayStart(System.currentTimeMillis())
    val yesterday = today - 24 * 60 * 60 * 1000
    val fmt = SimpleDateFormat("d. M. yyyy", Locale.forLanguageTag("cs"))
    return visits.groupBy { v ->
        when (dayStart(v.visitedAt)) {
            today -> "Dnes"
            yesterday -> "Včera"
            else -> fmt.format(Date(v.visitedAt))
        }
    }.toList()
}

private fun dayStart(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
