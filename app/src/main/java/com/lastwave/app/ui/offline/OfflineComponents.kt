package com.lastwave.app.ui.offline

import android.provider.DocumentsContract
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import coil.compose.AsyncImage
import com.lastwave.app.data.offline.OfflineTrack
import com.lastwave.app.ui.common.maxxAppear
import com.lastwave.app.ui.common.maxxClickable
import com.lastwave.app.ui.common.MaxxSpring
import java.io.File

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    bytes > 0 -> "%.0f KB".format(bytes / 1024.0)
    else -> ""
}

/** "primary:Music/Spotify" -> "Internal storage › Music/Spotify". */
internal fun folderLabel(treeUri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
    val volume = id.substringBefore(':')
    val rel = id.substringAfter(':', "").trim('/')
    val root = if (volume == "primary") "Internal storage" else "SD card"
    if (rel.isBlank()) root else "$root › $rel"
}.getOrDefault(treeUri)

/** Cover with a gradient + note placeholder underneath, so offline / missing art still looks designed. */
@Composable
internal fun OfflineArt(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    iconSize: Dp = 24.dp,
    icon: ImageVector = Icons.Filled.MusicNote,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = scheme.onPrimaryContainer.copy(alpha = 0.55f), modifier = Modifier.size(iconSize))
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = if (url.startsWith("/")) File(url) else url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal class ReorderCallbacks(
    val onStart: () -> Unit,
    val onDelta: (Float) -> Unit,
    val onEnd: () -> Unit,
)

@Composable
internal fun OfflineTrackRow(
    track: OfflineTrack,
    index: Int,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    showAlbumArtist: Boolean = true,
    reorder: ReorderCallbacks? = null,
    dragging: Boolean = false,
    dragOffset: () -> Float = { 0f },
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val callbacks by rememberUpdatedState(reorder)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                if (dragging) {
                    translationY = dragOffset()
                    scaleX = 1.03f
                    scaleY = 1.03f
                    shadowElevation = 24f
                    shape = RoundedCornerShape(20.dp)
                    clip = false
                }
            }
            .then(if (dragging) Modifier.background(scheme.surfaceContainerHighest, RoundedCornerShape(20.dp)) else Modifier)
            .maxxClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = if (reorder != null) 8.dp else 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reorder != null) {
            // Grab this and slide the song up or down. The new order is what plays.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { callbacks?.onStart?.invoke() },
                            onDragEnd = { callbacks?.onEnd?.invoke() },
                            onDragCancel = { callbacks?.onEnd?.invoke() },
                            onDrag = { change, amount ->
                                change.consume()
                                callbacks?.onDelta?.invoke(amount.y)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle, "Hold and drag to reorder",
                    tint = if (dragging) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
        OfflineArt(track.artworkUrl, Modifier.size(56.dp), RoundedCornerShape(16.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                if (showAlbumArtist) add(track.artist)
                formatDuration(track.durationMs).takeIf { it.isNotEmpty() }?.let { add(it) }
                track.format.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" • ")
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; onAddToQueue() })
            }
        }
    }
}

/** Outlined chip that fills with a spring when selected (like the genre chips). */
@Composable
internal fun OfflineChip(
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.primaryContainer else Color.Transparent,
        MaxxSpring.settle(), label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        MaxxSpring.settle(), label = "chipFg",
    )
    Surface(
        shape = CircleShape,
        color = bg,
        border = if (selected) null else BorderStroke(1.5.dp, scheme.outlineVariant),
        modifier = if (onClick != null) Modifier.maxxClickable(pressedScale = 0.9f, onClick = onClick) else Modifier,
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
        )
    }
}

@Composable
internal fun RoundAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .maxxClickable(pressedScale = 0.88f, onClick = onClick)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
