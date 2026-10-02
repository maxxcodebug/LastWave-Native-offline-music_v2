package com.lastwave.app.ui.offline

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.offline.OfflineAlbum
import com.lastwave.app.data.offline.OfflineArtist
import com.lastwave.app.ui.common.MaxxSpring
import com.lastwave.app.ui.common.maxxAppear
import com.lastwave.app.ui.common.maxxClickable
import com.lastwave.app.ui.shell.FloatingNavDefaults
import kotlin.math.PI
import kotlin.math.sin

private enum class OfflineView(val label: String) { SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums") }

/**
 * Offline tab. Hero card + source row follow the Dolby-style layout; controls,
 * chips and track rows follow the artist-page style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    onOpenArtist: (artistName: String, albumTitle: String?) -> Unit,
    viewModel: OfflineViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()

    var view by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addFolder(uri)
    }

    val q = query.trim().lowercase()
    val shownTracks = remember(tracks, q) {
        if (q.isEmpty()) tracks
        else tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
    }
    val shownArtists = remember(artists, q) {
        if (q.isEmpty()) artists else artists.filter { it.name.lowercase().contains(q) }
    }
    val shownAlbums = remember(albums, q) {
        if (q.isEmpty()) albums else albums.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // Soft tinted wash behind the header, like the Dolby screen.
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.45f), Color.Transparent))),
        )

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = FloatingNavDefaults.contentBottomPadding() + 8.dp,
            ),
        ) {
            item(key = "header") {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Offline", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Your music, no internet needed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (scan.scanning) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        }
                    } else {
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, "Rescan") }
                    }
                    IconButton(onClick = { showSheet = true }) { Icon(Icons.Filled.FolderOpen, "Folders and offline mode") }
                }
            }

            if (offlineMode == true) {
                item(key = "exitOffline") {
                    Surface(
                        shape = CircleShape,
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .maxxClickable(pressedScale = 0.97f) { confirmExit = true },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.WifiOff, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Offline mode is on",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.Wifi, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Go online", color = scheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item(key = "hero") {
                HeroCard(
                    songCount = tracks.size,
                    artistCount = artists.size,
                    onShuffle = { viewModel.playList(tracks, shuffled = true) },
                    onAddFolder = { pickFolder.launch(null) },
                    onPlay = { viewModel.playList(tracks) },
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .maxxAppear(0),
                )
            }

            item(key = "source") {
                SourceRow(
                    folderCount = folders.size,
                    scanning = scan.scanning,
                    found = scan.found,
                    onClick = { showSheet = true },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .maxxAppear(1),
                )
            }

            if (tracks.isEmpty() && !scan.scanning) {
                item(key = "empty") {
                    EmptyState(
                        onAddFolder = { pickFolder.launch(null) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .maxxAppear(2),
                    )
                }
            } else {
                item(key = "chips") {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OfflineView.entries.forEachIndexed { i, v ->
                            OfflineChip(v.label, selected = view == i, onClick = { view = i })
                        }
                    }
                }
                item(key = "search") {
                    SearchPill(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                    )
                }

                when (OfflineView.entries[view]) {
                    OfflineView.SONGS -> itemsIndexed(shownTracks, key = { _, t -> t.id }) { i, t ->
                        OfflineTrackRow(
                            track = t,
                            index = i,
                            onClick = { viewModel.play(t, shownTracks) },
                            onPlayNext = { viewModel.playNext(t) },
                            onAddToQueue = { viewModel.addToQueue(t) },
                        )
                    }
                    OfflineView.ARTISTS -> itemsIndexed(shownArtists, key = { _, a -> "a:" + a.name }) { i, a ->
                        ArtistRow(a, i) { onOpenArtist(a.name, null) }
                    }
                    OfflineView.ALBUMS -> itemsIndexed(shownAlbums.chunked(2), key = { _, row -> "al:" + row.first().title + row.first().artist }) { i, row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            row.forEach { al ->
                                AlbumCard(al, i, Modifier.weight(1f)) { onOpenArtist(al.artist, al.title) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave offline mode?") },
            text = { Text("Feed, Stats and online features come back. If you're not signed in, you'll go to the sign-in screen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    viewModel.setOfflineMode(false)
                }) { Text("Go online") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay offline") } },
        )
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            FoldersSheetContent(
                folders = folders.toList().sorted(),
                offlineMode = offlineMode == true,
                onAdd = { pickFolder.launch(null) },
                onRemove = viewModel::removeFolder,
                onOfflineModeChange = viewModel::setOfflineMode,
            )
        }
    }
}

@Composable
private fun HeroCard(
    songCount: Int,
    artistCount: Int,
    enabled: Boolean,
    onShuffle: () -> Unit,
    onAddFolder: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow))),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            WaveBars(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                scheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Your library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Surface(shape = CircleShape, color = scheme.surfaceContainerHighest.copy(alpha = 0.7f)) {
                Text(
                    "$songCount songs • $artistCount artists",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Filled.Shuffle, "Shuffle all", onShuffle, container = scheme.surfaceContainerHighest.copy(alpha = 0.8f))
                Spacer(Modifier.width(12.dp))
                RoundAction(Icons.Filled.CreateNewFolder, "Add music folder", onAddFolder, container = scheme.surfaceContainerHighest.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                RoundAction(
                    Icons.Filled.PlayArrow, "Play all", { if (enabled) onPlay() },
                    size = 72.dp,
                    container = if (enabled) scheme.primary else scheme.surfaceContainerHighest,
                    tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
                    iconSize = 34.dp,
                )
            }
        }
    }
}

/** Bars swell in the middle and wobble on a slow loop (same idea as the Dolby card). */
@Composable
private fun WaveBars(modifier: Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val n = 41
        val slot = size.width / n
        val barW = slot * 0.46f
        for (i in 0 until n) {
            val u = i / (n - 1f)
            val swell = sin(PI * u).toFloat()
            val env = 0.16f + 0.84f * swell * swell
            val wob = 0.55f + 0.45f * sin(phase + i * 0.55f)
            val h = (size.height * env * wob).coerceAtLeast(6f)
            drawRoundRect(
                color = color.copy(alpha = 0.30f + 0.70f * env),
                topLeft = Offset(slot * (i + 0.5f) - barW / 2, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}

@Composable
private fun SourceRow(
    folderCount: Int,
    scanning: Boolean,
    found: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .maxxClickable(pressedScale = 0.97f, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.LibraryMusic, null, tint = scheme.onSecondaryContainer) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Library source", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Text("Downloads + folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        scanning -> "Scanning… $found found"
                        folderCount == 0 -> "No folder added yet"
                        folderCount == 1 -> "1 folder"
                        else -> "$folderCount folders"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search your music", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Filled.Close, "Clear",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .maxxClickable { onQueryChange("") },
                )
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: OfflineArtist, index: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .maxxAppear(index)
            .maxxClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OfflineArt(artist.artworkUrl, Modifier.size(60.dp), CircleShape, iconSize = 26.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val songs = if (artist.tracks.size == 1) "1 song" else "${artist.tracks.size} songs"
            val al = when (artist.albumCount) { 0 -> ""; 1 -> " • 1 album"; else -> " • ${artist.albumCount} albums" }
            Text(songs + al, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlbumCard(album: OfflineAlbum, index: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .maxxAppear(index)
            .maxxClickable(pressedScale = 0.95f, onClick = onClick),
    ) {
        OfflineArt(album.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(24.dp), iconSize = 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(onAddFolder: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.LibraryMusic, null, Modifier.size(40.dp), tint = scheme.onPrimaryContainer) }
            Spacer(Modifier.height(18.dp))
            Text("No music yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pick the folder where your songs live. Songs you download in LastWave show up here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAddFolder, shape = CircleShape) {
                Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add music folder")
            }
        }
    }
}

@Composable
private fun FoldersSheetContent(
    folders: List<String>,
    offlineMode: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Music folders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (folders.isEmpty()) {
            Text("No folders yet.", color = scheme.onSurfaceVariant)
        }
        folders.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, null, tint = scheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(folderLabel(f), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(f) }) { Icon(Icons.Filled.Delete, "Remove folder") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add folder")
        }
        Spacer(Modifier.height(20.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WifiOff, null, tint = scheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Offline mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (offlineMode) "On. Feed and Stats are hidden." else "Off. Hides Feed and Stats when on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = offlineMode, onCheckedChange = onOfflineModeChange)
            }
        }
    }
}
