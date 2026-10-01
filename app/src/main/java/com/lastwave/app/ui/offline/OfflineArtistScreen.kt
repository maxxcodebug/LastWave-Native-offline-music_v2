package com.lastwave.app.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.offline.OfflineTrack
import com.lastwave.app.ui.common.maxxAppear
import com.lastwave.app.ui.player.LocalMiniPlayerScrollClearance

/**
 * Artist page (or album page when [albumTitle] is set), in the same layout as the
 * reference: hero card with name + count chip, shuffle / queue / play controls,
 * info chips, About, then the song list.
 */
@Composable
fun OfflineArtistScreen(
    artistName: String,
    albumTitle: String?,
    onBack: () -> Unit,
    viewModel: OfflineViewModel = hiltViewModel(),
) {
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val tracks: List<OfflineTrack> = remember(allTracks, artistName, albumTitle) {
        allTracks.filter { t ->
            t.artist.trim().equals(artistName.trim(), ignoreCase = true) &&
                (albumTitle == null || t.album.trim().equals(albumTitle.trim(), ignoreCase = true))
        }.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
    }
    val title = albumTitle ?: artistName
    val scheme = MaterialTheme.colorScheme
    val cover = tracks.firstNotNullOfOrNull { it.artworkUrl }
    val albumCount = tracks.map { it.album.trim().lowercase() }.filter { it.isNotBlank() }.distinct().size
    val formats = tracks.map { it.format.substringBefore(' ').trim() }.filter { it.isNotBlank() }.distinct().take(3)
    val totalMs = tracks.sumOf { it.durationMs }
    val totalBytes = tracks.sumOf { it.sizeBytes }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = LocalMiniPlayerScrollClearance.current +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp,
            ),
        ) {
            item(key = "top") {
                Box(Modifier.statusBarsPadding().padding(start = 8.dp, top = 8.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            }
            item(key = "hero") {
                Box(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .maxxAppear(0)
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(36.dp)),
                ) {
                    OfflineArt(cover, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), iconSize = 72.dp)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                        )
                        if (albumTitle != null) {
                            Text(artistName, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
                            Text(
                                if (tracks.size == 1) "1 song" else "${tracks.size} songs",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            item(key = "controls") {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp).maxxAppear(1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundAction(Icons.Filled.Shuffle, "Shuffle", { viewModel.playList(tracks, shuffled = true) })
                    Spacer(Modifier.width(12.dp))
                    RoundAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add all to queue", { tracks.forEach(viewModel::addToQueue) })
                    Spacer(Modifier.weight(1f))
                    RoundAction(
                        Icons.Filled.PlayArrow, "Play",
                        { viewModel.playList(tracks) },
                        size = 76.dp,
                        container = scheme.primaryContainer,
                        tint = scheme.onPrimaryContainer,
                        iconSize = 36.dp,
                    )
                }
            }
            item(key = "chips") {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .maxxAppear(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OfflineChip("offline")
                    formats.forEach { OfflineChip(it.lowercase()) }
                    if (albumCount > 1) OfflineChip("$albumCount albums")
                }
            }
            item(key = "about") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp).maxxAppear(3)) {
                    Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.primary)
                    Spacer(Modifier.height(6.dp))
                    val parts = buildList {
                        add(if (tracks.size == 1) "1 song" else "${tracks.size} songs")
                        if (albumCount > 0) add(if (albumCount == 1) "1 album" else "$albumCount albums")
                        val mins = totalMs / 60_000
                        if (mins > 0) add(if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min")
                        formatSize(totalBytes).takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                    Text(
                        parts.joinToString(" • ") + ", saved on this device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "songs-header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Songs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (tracks.size == 1) "1 track" else "${tracks.size} tracks",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                OfflineTrackRow(
                    track = t,
                    index = i,
                    showAlbumArtist = false,
                    onClick = { viewModel.play(t, tracks) },
                    onPlayNext = { viewModel.playNext(t) },
                    onAddToQueue = { viewModel.addToQueue(t) },
                )
            }
        }
    }
}
