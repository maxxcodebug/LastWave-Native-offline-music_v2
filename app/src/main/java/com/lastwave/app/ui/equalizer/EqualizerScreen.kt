package com.lastwave.app.ui.equalizer

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.local.EQ_MAX_GAIN_DB
import com.lastwave.app.data.local.EqualizerPresets
import com.lastwave.app.ui.common.MaxxSpring
import com.lastwave.app.ui.common.maxxAppear
import com.lastwave.app.ui.common.maxxClickable
import com.lastwave.app.ui.offline.OfflineChip
import com.lastwave.app.ui.shell.FloatingNavDefaults
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(32.dp)

private fun fmtDb(v: Float): String =
    if (abs(v) < 0.05f) "0" else String.format(Locale.US, "%+.1f", v)

/**
 * MaxxEqualizer Lite. Material 3 Expressive: big rounded containers, thick
 * pill sliders with a detached handle, tonal colour roles, spring motion.
 * Every colour comes from the app theme, so it follows light/dark/dynamic colour.
 */
@Composable
fun EqualizerScreen(
    onOpenSettings: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val preset by viewModel.presetName.collectAsStateWithLifecycle()
    // States are passed down un-read, so a slider drag only redraws the slider.
    val bandsState = viewModel.bands.collectAsStateWithLifecycle()
    val bassState = viewModel.bass.collectAsStateWithLifecycle()
    val volumeState = viewModel.volume.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = FloatingNavDefaults.contentBottomPadding() + 8.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MaxxEqualizer",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onBackground,
                )
                Text(
                    "Lite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .maxxClickable(pressedScale = 0.86f, onClick = onOpenSettings)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Settings, "Settings", tint = scheme.onSecondaryContainer) }
        }

        PowerCard(
            enabled = enabled,
            presetLabel = preset,
            onToggle = viewModel::setEnabled,
            modifier = Modifier.padding(horizontal = 16.dp).maxxAppear(0),
        )

        Spacer(Modifier.height(12.dp))

        // Bands
        Surface(
            shape = CardShape,
            color = scheme.surfaceContainerLow,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().maxxAppear(1),
        ) {
            BandsPanel(
                bands = bandsState,
                enabled = enabled,
                onChange = viewModel::onBand,
                onEnd = viewModel::onBandDragEnd,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Presets
        Text(
            "Presets",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        val names = remember { listOf(EqualizerPresets.CUSTOM_NAME) + EqualizerPresets.ALL.map { it.name } }
        val rowState = rememberLazyListState()
        LaunchedEffect(preset) {
            val i = names.indexOf(preset)
            if (i >= 0) rowState.animateScrollToItem(i)
        }
        LazyRow(
            state = rowState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(names, key = { it }) { name ->
                OfflineChip(
                    label = name,
                    selected = name == preset,
                    onClick = {
                        if (name == EqualizerPresets.CUSTOM_NAME) viewModel.saveCustom() else viewModel.applyPreset(name)
                    },
                )
            }
        }

        // Save / Reset
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .maxxClickable(pressedScale = 0.95f) {
                        viewModel.saveCustom()
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    .clip(CircleShape)
                    .background(scheme.primary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = scheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Save", color = scheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .size(56.dp)
                    .maxxClickable(pressedScale = 0.86f) { viewModel.resetAll() }
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.RestartAlt, "Reset", tint = scheme.onSurface) }
        }

        Spacer(Modifier.height(8.dp))

        // Boosts
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).maxxAppear(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoostCard(
                title = "Bass Boost",
                icon = Icons.Filled.Waves,
                value = bassState,
                container = scheme.tertiaryContainer,
                onContainer = scheme.onTertiaryContainer,
                accent = scheme.tertiary,
                onChange = viewModel::setBass,
                modifier = Modifier.weight(1f),
            )
            BoostCard(
                title = "Volume Boost",
                icon = Icons.Filled.VolumeUp,
                value = volumeState,
                container = scheme.secondaryContainer,
                onContainer = scheme.onSecondaryContainer,
                accent = scheme.secondary,
                onChange = viewModel::setVolume,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PowerCard(enabled: Boolean, presetLabel: String, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (enabled) scheme.primaryContainer else scheme.surfaceContainerHigh,
        MaxxSpring.settle(), label = "powerBg",
    )
    val fg by animateColorAsState(
        if (enabled) scheme.onPrimaryContainer else scheme.onSurface,
        MaxxSpring.settle(), label = "powerFg",
    )
    val iconBg by animateColorAsState(
        if (enabled) scheme.primary else scheme.surfaceContainerHighest,
        MaxxSpring.settle(), label = "powerIconBg",
    )
    val iconPop by animateFloatAsState(if (enabled) 1.12f else 1f, MaxxSpring.bouncy(), label = "powerPop")
    Row(
        modifier
            .fillMaxWidth()
            .maxxClickable(pressedScale = 0.97f) { onToggle(!enabled) }
            .clip(CardShape)
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.GraphicEq, null,
                tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp).scale(iconPop),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
            Text(
                if (enabled) "On • $presetLabel" else "Off",
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = 0.75f),
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun BoostCard(
    title: String,
    icon: ImageVector,
    value: State<Float>,
    container: Color,
    onContainer: Color,
    accent: Color,
    onChange: (Float) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = onContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = onContainer)
        }
        Text(
            "${(value.value * 100).roundToInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = onContainer,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ExpressiveSlider(value = value, onChange = onChange, active = accent, inactive = onContainer.copy(alpha = 0.18f), gap = container)
    }
}

/** Thick pill slider with a detached handle bar (Material 3 Expressive). */
@Composable
private fun ExpressiveSlider(
    value: State<Float>,
    onChange: (Float) -> Unit,
    active: Color,
    inactive: Color,
    gap: Color,
    modifier: Modifier = Modifier,
) {
    val latest by rememberUpdatedState(onChange)
    val pressed = remember { mutableIntStateOf(0) }
    val pop = animateFloatAsState(if (pressed.intValue > 0) 1.18f else 1f, MaxxSpring.bouncy(), label = "sliderPop")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed.intValue = 1
                    fun apply(x: Float) = latest(((x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f))
                    apply(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        apply(change.position.x)
                    }
                    pressed.intValue = 0
                }
            },
    ) {
        val pad = 14.dp.toPx()
        val trackH = 28.dp.toPx()
        val cy = size.height / 2f
        val v = value.value.coerceIn(0f, 1f)
        val x = pad + (size.width - 2f * pad) * v
        val hGap = 5.dp.toPx()
        val handleW = 7.dp.toPx() * pop.value
        val handleH = 44.dp.toPx().coerceAtMost(size.height)
        val r = CornerRadius(trackH / 2f)
        // inactive part
        val inLeft = (x + handleW / 2f + hGap).coerceAtMost(size.width)
        if (inLeft < size.width) {
            drawRoundRect(inactive, Offset(inLeft, cy - trackH / 2f), Size(size.width - inLeft, trackH), r)
        }
        // active part
        val actRight = (x - handleW / 2f - hGap).coerceAtLeast(0f)
        if (actRight > 0f) {
            drawRoundRect(active, Offset(0f, cy - trackH / 2f), Size(actRight, trackH), r)
        }
        // handle
        drawRoundRect(active, Offset(x - handleW / 2f, cy - handleH / 2f), Size(handleW, handleH), CornerRadius(handleW / 2f))
    }
}

@Composable
private fun BandsPanel(
    bands: State<List<Float>>,
    enabled: Boolean,
    onChange: (Int, Float) -> Unit,
    onEnd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val latestChange by rememberUpdatedState(onChange)
    val latestEnd by rememberUpdatedState(onEnd)
    val count = UI_BAND_FREQS.size
    // -1 = no finger down. Held in state so only the draw phase reads it.
    val activeBand = remember { mutableIntStateOf(-1) }
    val pop = animateFloatAsState(if (activeBand.intValue >= 0) 1f else 0f, MaxxSpring.bouncy(), label = "bandPop")

    val trackColor = scheme.surfaceContainerHighest
    val activeColor = if (enabled) scheme.primary else scheme.outline
    val lineColor = scheme.tertiary.copy(alpha = if (enabled) 0.65f else 0.3f)
    val zeroTick = scheme.outlineVariant
    val cardBg = scheme.surfaceContainerLow

    Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            UI_BAND_FREQS.forEach {
                Text(
                    uiBandLabel(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .padding(vertical = 8.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        // The band is chosen once, where the finger lands, and stays locked.
                        val colW = size.width / count
                        val band = (down.position.x / colW).toInt().coerceIn(0, count - 1)
                        activeBand.intValue = band
                        val r = 14.dp.toPx()
                        fun apply(y: Float) {
                            val frac = ((y - r) / (size.height - 2f * r)).coerceIn(0f, 1f)
                            latestChange(band, EQ_MAX_GAIN_DB - 2f * EQ_MAX_GAIN_DB * frac)
                        }
                        apply(down.position.y)
                        drag(down.id) { change ->
                            change.consume()
                            apply(change.position.y)
                        }
                        activeBand.intValue = -1
                        latestEnd()
                    }
                },
        ) {
            val colW = size.width / count
            val trackW = 24.dp.toPx().coerceAtMost(colW - 4.dp.toPx())
            val r = 14.dp.toPx()
            val top = r
            val bottom = size.height - r
            val mid = (top + bottom) / 2f
            val list = bands.value
            val handleH = 10.dp.toPx()
            val gap = 3.dp.toPx()
            val xs = FloatArray(count) { colW * (it + 0.5f) }
            val ys = FloatArray(count) { i ->
                val g = list.getOrElse(i) { 0f }
                val f = ((EQ_MAX_GAIN_DB - g) / (2f * EQ_MAX_GAIN_DB)).coerceIn(0f, 1f)
                top + (bottom - top) * f
            }
            for (i in 0 until count) {
                val x = xs[i]
                drawRoundRect(trackColor, Offset(x - trackW / 2f, 0f), Size(trackW, size.height), CornerRadius(trackW / 2f))
                // bipolar fill: from the 0 dB line to the handle
                val y = ys[i]
                val a = minOf(mid, y)
                val b = maxOf(mid, y)
                if (b - a > 0.5f) {
                    drawRoundRect(activeColor, Offset(x - trackW / 2f, a), Size(trackW, b - a), CornerRadius(trackW / 2f))
                }
            }
            // 0 dB tick
            drawLine(zeroTick, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            // curve through the handles
            val path = Path()
            for (i in 0 until count) if (i == 0) path.moveTo(xs[i], ys[i]) else path.lineTo(xs[i], ys[i])
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // handles (with a gap ring so they read as detached)
            for (i in 0 until count) {
                val grow = if (activeBand.intValue == i) 1f + 0.35f * pop.value else 1f
                val hw = (trackW + 8.dp.toPx()) * grow
                val hh = handleH * (if (activeBand.intValue == i) 1.2f else 1f)
                drawRoundRect(cardBg, Offset(xs[i] - hw / 2f - gap, ys[i] - hh / 2f - gap), Size(hw + 2 * gap, hh + 2 * gap), CornerRadius((hh + 2 * gap) / 2f))
                drawRoundRect(activeColor, Offset(xs[i] - hw / 2f, ys[i] - hh / 2f), Size(hw, hh), CornerRadius(hh / 2f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until count) {
                val g = bands.value.getOrElse(i) { 0f }
                Text(
                    fmtDb(g),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (abs(g) >= 0.05f) FontWeight.Bold else FontWeight.Normal,
                    color = if (abs(g) >= 0.05f && enabled) scheme.primary else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
