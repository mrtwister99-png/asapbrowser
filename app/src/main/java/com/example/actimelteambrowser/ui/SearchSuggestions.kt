package com.example.actimelteambrowser.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.actimelteambrowser.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.actimelteambrowser.core.settings.SearchEngines
import com.example.actimelteambrowser.data.Bookmark
import com.example.actimelteambrowser.data.HistoryVisit
import com.example.actimelteambrowser.data.MostVisitedSite

data class SearchSuggestion(
    val title: String,    // co se zobrazí
    val subtitle: String, // url nebo popis
    val url: String,      // kam jít
    val type: Type,
    val score: Int = 0,   // pro řazení podle relevance
    val icon: Bitmap? = null   // ikona webu (favicon), jen u aktuální stránky
) {
    enum class Type { BOOKMARK, HISTORY, TOP, WEB, SEARCH_QUERY, CURRENT }
}

/**
 * Našeptávač (port ze starého projektu).
 * - prázdný dotaz: nejnavštěvovanější + poslední historie
 * - jinak: "Hledat ..." první, pak záložky, historie, nejnavštěvovanější a známé weby
 *   (y -> youtube, r -> reddit, g -> google/github/gmail ...)
 */
private val DEFAULT_WEB = listOf(
    "google.com" to "Google",
    "github.com" to "GitHub",
    "gmail.com" to "Gmail",
    "youtube.com" to "YouTube",
    "seznam.cz" to "Seznam",
    "mapy.cz" to "Mapy.cz",
    "facebook.com" to "Facebook",
    "x.com" to "X",
    "reddit.com" to "Reddit",
    "wikipedia.org" to "Wikipedia",
    "chat.openai.com" to "ChatGPT",
    "drive.google.com" to "Google Drive",
    "translate.google.com" to "Překladač",
    "instagram.com" to "Instagram",
    "amazon.com" to "Amazon",
    "heureka.cz" to "Heureka"
)

fun buildSearchSuggestions(
    query: String,
    history: List<HistoryVisit>,
    bookmarks: List<Bookmark>,
    mostVisited: List<MostVisitedSite>
): List<SearchSuggestion> {
    val q = query.trim()
    val qLower = q.lowercase()

    if (q.isEmpty()) {
        val top = mostVisited.sortedByDescending { it.visitCount }.take(6).map {
            SearchSuggestion(it.title.ifBlank { it.url }, it.url, it.url, SearchSuggestion.Type.TOP, it.visitCount)
        }
        val recent = history.take(6).map {
            SearchSuggestion(it.title.ifBlank { it.url }, it.url, it.url, SearchSuggestion.Type.HISTORY)
        }
        return (top + recent).distinctBy { it.url }.take(10)
    }

    val out = mutableListOf<SearchSuggestion>()

    // 0. HLEDAT - vždy první
    out.add(
        SearchSuggestion(
            title = "Hledat \"$q\"",
            subtitle = "Hledat: " + SearchEngines.current().name,
            url = SearchEngines.searchUrl(q),
            type = SearchSuggestion.Type.SEARCH_QUERY,
            score = 1000
        )
    )

    // 1. ZÁLOŽKY - nejvyšší relevance
    bookmarks.filter { it.title.lowercase().contains(qLower) || it.url.lowercase().contains(qLower) }
        .take(4)
        .forEach { out.add(SearchSuggestion(it.title.ifBlank { it.url }, it.url, it.url, SearchSuggestion.Type.BOOKMARK, 900)) }

    // 2. HISTORIE - výš ty, které začínají na dotaz
    history.filter { it.url.lowercase().contains(qLower) || it.title.lowercase().contains(qLower) }
        .distinctBy { it.url }
        .map { h ->
            val starts = h.url.lowercase().startsWith(qLower) || h.title.lowercase().startsWith(qLower)
            h to (if (starts) 800 else 500)
        }
        .sortedByDescending { it.second }
        .take(6)
        .forEach { (h, score) ->
            out.add(SearchSuggestion(h.title.ifBlank { h.url }, h.url, h.url, SearchSuggestion.Type.HISTORY, score))
        }

    // 3. NEJNAVŠTĚVOVANĚJŠÍ
    mostVisited.filter { it.url.lowercase().contains(qLower) || it.title.lowercase().contains(qLower) }
        .sortedByDescending { it.visitCount }
        .take(3)
        .forEach { out.add(SearchSuggestion(it.title.ifBlank { it.url }, it.url, it.url, SearchSuggestion.Type.TOP, it.visitCount)) }

    // 4. ZNÁMÉ WEBY - jen pro krátký dotaz
    if (qLower.length <= 5) {
        DEFAULT_WEB.filter { (domain, name) ->
            domain.contains(qLower) || name.lowercase().contains(qLower)
        }
            .take(4)
            .forEach { (domain, name) ->
                if (out.none { it.url.contains(domain) }) {
                    out.add(SearchSuggestion(name, domain, "https://$domain", SearchSuggestion.Type.WEB, 400))
                }
            }
    }

    return out.sortedByDescending { it.score }.distinctBy { it.url }.take(10)
}

private fun typeMark(type: SearchSuggestion.Type): String = when (type) {
    SearchSuggestion.Type.SEARCH_QUERY -> "🔍"
    SearchSuggestion.Type.BOOKMARK -> "★"
    SearchSuggestion.Type.HISTORY -> "🕘"
    SearchSuggestion.Type.TOP -> "🔥"
    SearchSuggestion.Type.WEB -> "🌐"
    SearchSuggestion.Type.CURRENT -> "•"
}

/**
 * Seznam návrhů nad spodní lištou / pod horní (tmavý styl appky).
 * pinned = aktuální stránka: zelený řádek s ikonou webu, vpravo ikona pro zkopírování adresy.
 *   U horního pole je nahoře (nejblíž poli), u dolního pole dole (taky nejblíž poli) - pinnedAtBottom.
 * compact = prázdné pole: ostatní návrhy (nejnavštěvovanější) ukážou jen 3 řádky, dál se posouvá.
 */
@Composable
fun SearchSuggestionsList(
    suggestions: List<SearchSuggestion>,
    pinned: SearchSuggestion? = null,
    pinnedAtBottom: Boolean = false,
    compact: Boolean = false,
    onCopy: (SearchSuggestion) -> Unit = {},
    onPinned: (SearchSuggestion) -> Unit = {},
    onEdit: (SearchSuggestion) -> Unit = {},
    onPick: (SearchSuggestion) -> Unit
) {
    if (suggestions.isEmpty() && pinned == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        if (pinned != null && !pinnedAtBottom) PinnedRow(pinned, onCopy, onPinned, onEdit)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // prazdne pole: 3 radky (~58 dp), dal se posouva; pri psani vic
                .heightIn(max = if (compact) 174.dp else 360.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(suggestions, key = { it.url + it.type.name }) { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(typeMark(s.type), fontSize = 16.sp, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            prettyUrl(s.subtitle), color = Color(0xFF888888), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (pinned != null && pinnedAtBottom) PinnedRow(pinned, onCopy, onPinned, onEdit)
    }
}

/** Aktuální stránka: zeleně zvýrazněný řádek s ikonou webu a tlačítkem Kopírovat. */
@Composable
private fun PinnedRow(pinned: SearchSuggestion, onCopy: (SearchSuggestion) -> Unit, onPinned: (SearchSuggestion) -> Unit, onEdit: (SearchSuggestion) -> Unit) {
    // po zkopirovani se misto ikony na chvili ukaze "Zkopirovano"
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1300)
            copied = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF00B055))
            .clickable { onPinned(pinned) }
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ikona webu (favicon); kdyz zatim neni, prvni pismeno adresy
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                val ic = pinned.icon
                if (ic != null) {
                    Image(
                        bitmap = ic.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        prettyUrl(pinned.url).firstOrNull()?.uppercase() ?: "•",
                        color = Color(0xFF00B055), fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                pinned.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                prettyUrl(pinned.subtitle), color = Color(0xFFD7FFE8), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        // upravit adresu aktualni stranky: vlozi ji do pole, kurzor na konec
        Image(
            painter = painterResource(R.drawable.ic_edit_url),
            contentDescription = "Upravit adresu",
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onEdit(pinned) }
                .padding(10.dp)
        )
        // kopirovat adresu aktualni stranky
        if (copied) {
            Text(
                "Zkopírováno ✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_copy_url),
                contentDescription = "Kopírovat adresu",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onCopy(pinned)
                        copied = true
                    }
                    .padding(10.dp)
            )
        }
    }
}

/** Adresa bez https:// a www. a bez koncového lomítka. */
fun prettyUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
