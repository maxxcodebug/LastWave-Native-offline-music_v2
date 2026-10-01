package com.lastwave.app.ui.offline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastwave.app.ui.common.MaxxSpring

/**
 * Setup-screen switch. Turning it on springs open a "Continue in offline mode"
 * button; nothing is saved until that button is tapped.
 */
@Composable
fun OfflineModeSetupCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (checked) scheme.primaryContainer else scheme.surfaceContainerHigh,
        MaxxSpring.settle(), label = "offlineCardBg",
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.WifiOff, null, tint = scheme.onSecondaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Offline mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Play music saved on your phone. No account or internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checked) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    thumbContent = if (checked) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else null,
                )
            }
            AnimatedVisibility(
                visible = checked,
                enter = fadeIn(MaxxSpring.settle()) + expandVertically(MaxxSpring.bouncy()),
                exit = fadeOut(MaxxSpring.settle()) + shrinkVertically(MaxxSpring.snappy()),
            ) {
                Column(Modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onContinue,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Continue in offline mode") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You'll pick your music folder on the next screen. You can switch offline mode off any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
