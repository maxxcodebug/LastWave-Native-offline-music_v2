package com.lastwave.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastwave.app.ui.common.maxxClickable

private const val GITHUB = "https://github.com/maxxcodebug"
private const val TELEGRAM = "https://t.me/AnshumanAhirwar"
private const val FORK_REPO = "https://github.com/maxxcodebug/LastWave-Native-offline-music_v2"
private const val ORIGINAL_REPO = "https://github.com/Clash-Projects/LastWave-native"

private val Contributions = listOf(
    "Offline mode and offline music player (folder scan, songs / artists / albums, search)",
    "Drag-to-reorder songs; the order you set is the order that plays",
    "MaxxEqualizer Lite: 10-band EQ, presets, stronger Bass Boost and Volume Boost",
    "Liquid-glass navigation pill with Maxx spring motion",
    "Offline switch in setup, and a way back online from inside the app",
)

/** Who built what. Opened from Settings → System & About. */
@Composable
fun MaxxContributionsDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        icon = {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Favorite, null, tint = scheme.onPrimaryContainer) }
        },
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Maxx Contributions", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(
                    "by Anshuman (Anshuman X)",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Android developer and custom ROM maintainer. Leads MaxxPixel OS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text("What I added here", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Contributions.forEach {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(scheme.primary))
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Find me", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LinkPill("GitHub • maxxcodebug") { uri.openUri(GITHUB) }
                LinkPill("Telegram • @AnshumanAhirwar") { uri.openUri(TELEGRAM) }
                LinkPill("This project • LastWave-Native-offline-music_v2") { uri.openUri(FORK_REPO) }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Based on LastWave by Clash-Projects",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.maxxClickable(pressedScale = 0.97f) { uri.openUri(ORIGINAL_REPO) },
                )
            }
        },
    )
}

@Composable
private fun LinkPill(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .maxxClickable(pressedScale = 0.96f, onClick = onClick)
            .clip(CircleShape)
            .background(scheme.secondaryContainer)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = scheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
