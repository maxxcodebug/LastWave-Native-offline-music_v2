package com.lastwave.app.ui.equalizer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.local.EQ_MAX_GAIN_DB
import com.lastwave.app.data.local.EqualizerPresets
import com.lastwave.app.ui.common.maxxClickable
import com.lastwave.app.ui.shell.FloatingNavDefaults
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Gold = Color(0xFFFFC65B)
private val CardBg = Color(0xFF121212)
private val TrackGrey = Color(0xFF474747)
private val CardShape = RoundedCornerShape(20.dp)

private fun fmtDb(v: Float): String =
    if (abs(v) < 0.05f) "0.0" else String.format(Locale.US, "%+.1f", v)

/** Equalizer tab. Fixed dark + gold palette on purpose: it mirrors the reference design 1:1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onOpenSettings: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val preset by viewModel.presetName.collectAsStateWithLifecycle()
    val bands by viewModel.bands.collectAsStateWithLifecycle()
    val bass by viewModel.bass.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    var showPresets by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isCustom = preset == EqualizerPresets.CUSTOM_NAME

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = FloatingNavDefaults.contentBottomPadding() + 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 14.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Equalizer", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, "Settings", tint = Gold, modifier = Modifier.size(30.dp))
            }
        }

        // Bands card
        Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Equalizer", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = viewModel::setEnabled,
                    modifier = Modifier.scale(1.1f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Gold,
                        checkedBorderColor = Gold,
                        uncheckedThumbColor = Color(0xFFBDBDBD),
                        uncheckedTrackColor = Color(0xFF333333),
                        uncheckedBorderColor = Color(0xFF333333),
                    ),
                )
            }
            EqBands(
                bands = bands,
                enabled = enabled,
                onChange = viewModel::onBand,
                onEnd = viewModel::onBandDragEnd,
            )
        }

        // Presets / Custom / Save card
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(CardShape).background(CardBg).padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 1.dp)) {
                ModeButton("Presets", selected = !isCustom, modifier = Modifier.weight(1f)) { showPresets = true }
                ModeButton("Custom", selected = isCustom, modifier = Modifier.weight(1f)) { viewModel.saveCustom() }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .maxxClickable(pressedScale = 0.92f) {
                        viewModel.saveCustom()
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    .clip(CircleShape)
                    .background(Gold)
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bass Boost + Volume Boost
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoostCard("Bass Boost", bass, viewModel::setBass, Modifier.weight(1f))
            BoostCard("Volume Boost", volume, viewModel::setVolume, Modifier.weight(1f))
        }

        // Audio balance
        Column(
            Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Audio Balance", color = Color.White, fontSize = 22.sp)
            Text(
                "Reset",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier.clickable(onClick = viewModel::resetBalance).padding(horizontal = 18.dp, vertical = 2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("L", color = Color.White, fontSize = 22.sp)
                GoldSlider(
                    value = (balance + 1f) / 2f,
                    onChange = { viewModel.setBalance(it * 2f - 1f) },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("R", color = Color.White, fontSize = 22.sp)
            }
        }
    }

    if (showPresets) {
        ModalBottomSheet(
            onDismissRequest = { showPresets = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF181818),
        ) {
            Text("Presets", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(EqualizerPresets.ALL, key = { it.name }) { p ->
                    val selected = p.name == preset
                    Text(
                        p.name,
                        color = if (selected) Gold else Color.White,
                        fontSize = 18.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.applyPreset(p.name)
                                showPresets = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(BorderStroke(1.5.dp, Gold), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 21.sp)
    }
}

@Composable
private fun BoostCard(title: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(CardShape).background(CardBg).padding(top = 4.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 22.sp)
        Text("${(value * 100).roundToInt()}%", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        GoldSlider(value = value, onChange = onChange, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** Horizontal slider: gold up to the thumb, grey after (matches the reference). */
@Composable
private fun GoldSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                val r = 10.5.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun apply(x: Float) = latest(((x - r) / (size.width - 2f * r)).coerceIn(0f, 1f))
                    apply(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        apply(change.position.x)
                    }
                }
            },
    ) {
        val r = 10.5.dp.toPx()
        val w = 5.dp.toPx()
        val cy = size.height / 2f
        val x0 = r
        val x1 = size.width - r
        val tx = x0 + (x1 - x0) * value.coerceIn(0f, 1f)
        drawLine(TrackGrey, Offset(tx, cy), Offset(x1, cy), w, StrokeCap.Round)
        drawLine(Gold, Offset(x0, cy), Offset(tx, cy), w, StrokeCap.Round)
        drawCircle(Gold, r, Offset(tx, cy))
    }
}

@Composable
private fun EqBands(
    bands: List<Float>,
    enabled: Boolean,
    onChange: (Int, Float) -> Unit,
    onEnd: () -> Unit,
) {
    val latestChange by rememberUpdatedState(onChange)
    val latestEnd by rememberUpdatedState(onEnd)
    val count = UI_BAND_FREQS.size
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            UI_BAND_FREQS.forEach {
                Text(uiBandLabel(it), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(178.dp)
                .padding(top = 6.dp)
                .pointerInput(Unit) {
                    val r = 9.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun apply(p: Offset) {
                            val colW = size.width / count
                            val i = (p.x / colW).toInt().coerceIn(0, count - 1)
                            val frac = ((p.y - r) / (size.height - 2f * r)).coerceIn(0f, 1f)
                            latestChange(i, EQ_MAX_GAIN_DB - 2f * EQ_MAX_GAIN_DB * frac)
                        }
                        apply(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            apply(change.position)
                        }
                        latestEnd()
                    }
                },
        ) {
            val colW = size.width / count
            val r = 9.dp.toPx()
            val stroke = 4.dp.toPx()
            val top = r
            val bottom = size.height - r
            val gold = if (enabled) Gold else Gold.copy(alpha = 0.45f)
            val pts = bands.mapIndexed { i, g ->
                val f = (EQ_MAX_GAIN_DB - g) / (2f * EQ_MAX_GAIN_DB)
                Offset(colW * (i + 0.5f), top + (bottom - top) * f.coerceIn(0f, 1f))
            }
            pts.forEach { p ->
                drawLine(TrackGrey, Offset(p.x, top), Offset(p.x, p.y), stroke, StrokeCap.Round)
                drawLine(gold, Offset(p.x, p.y), Offset(p.x, bottom), stroke, StrokeCap.Round)
            }
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, gold, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.forEach { p -> drawCircle(gold, r, p) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
            bands.forEach {
                Text(fmtDb(it), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
