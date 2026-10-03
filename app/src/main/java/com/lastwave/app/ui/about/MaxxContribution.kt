package com.lastwave.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastwave.app.BuildConfig
import com.lastwave.app.ui.common.maxxClickable

private const val MY_NAME = "Anshuman X"
private const val MY_GITHUB = "https://github.com/maxxcodebug"
private const val MY_TELEGRAM = "https://t.me/AnshumanAhirwar"

private const val UPSTREAM_REPO = "https://github.com/Clash-Projects/LastWave-native"
private const val UPSTREAM_DUXTAMI = "https://github.com/duxtami"
private const val UPSTREAM_AJISTH = "https://github.com/ajisth69"

private val OFFLINE_APP_WORK = listOf(
    "The whole app: offline library, folder scanner and song pages",
    "Drag-to-reorder songs, and the order is what plays",
    "MaxxEqualizer Lite: 10-band EQ, bass boost and a real volume boost stage",
    "Liquid-glass navigation pill with Maxx spring motion",
    "Material 3 Expressive design throughout",
)

private val ONLINE_APP_WORK = listOf(
    "Offline tab: your own folders and downloads, with artist and album pages",
    "Drag-to-reorder songs, and the order is what plays",
    "MaxxEqualizer Lite: 10-band EQ, bass boost and a real volume boost stage",
    "Offline mode switch in setup, and a way back to online",
    "Liquid-glass navigation pill with Maxx spring motion",
)

/** Button for the About card. Self-contained so the settings file only needs one line. */
@Composable
fun MaxxContributionEntry() {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.maxxClickable(pressedScale = 0.94f) { open = true },
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Contribution and credits",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    if (open) MaxxContributionDialog(onDismiss = { open = false })
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MaxxContributionDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val offlineOnly = BuildConfig.OFFLINE_ONLY
    val work = if (offlineOnly) OFFLINE_APP_WORK else ONLINE_APP_WORK

    fun open(url: String) = runCatching { uri.openUri(url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = {
            Column {
                Text(
                    if (offlineOnly) "Made by $MY_NAME" else "Offline edition by $MY_NAME",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (offlineOnly) "Everything you see in this app was built by me." else "What I added on top of LastWave:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                work.forEach {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•", color = scheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkChip("GitHub", Icons.Filled.Code) { open(MY_GITHUB) }
                    LinkChip("Telegram", Icons.Filled.Send) { open(MY_TELEGRAM) }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (offlineOnly) "Built on LastWave by Duxtami and Ajisth." else "LastWave itself is by Duxtami and Ajisth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallLink("Source") { open(UPSTREAM_REPO) }
                    SmallLink("Duxtami") { open(UPSTREAM_DUXTAMI) }
                    SmallLink("Ajisth") { open(UPSTREAM_AJISTH) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LinkChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.maxxClickable(pressedScale = 0.92f, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SmallLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .maxxClickable(pressedScale = 0.92f, onClick = onClick)
            .padding(end = 14.dp, top = 6.dp, bottom = 6.dp),
    )
}
