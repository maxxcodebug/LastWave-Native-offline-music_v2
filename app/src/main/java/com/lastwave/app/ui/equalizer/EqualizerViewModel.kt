package com.lastwave.app.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.EQ_BAND_FREQS_HZ
import com.lastwave.app.data.local.EqualizerPreferences
import com.lastwave.app.data.local.EqualizerPresets
import com.lastwave.app.playback.LOUDNESS_PREAMP_MAX_DB
import com.lastwave.app.playback.LoudnessPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.roundToInt

/** The 10 sliders on screen. The audio engine has 15 bands; see [expand]. */
val UI_BAND_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
private val UI_TO_ENGINE = intArrayOf(0, 2, 3, 5, 6, 8, 10, 11, 13, 14)

fun uiBandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else "$hz"

private fun sample(engine: List<Float>): List<Float> =
    UI_TO_ENGINE.map { engine.getOrElse(it) { 0f } }

/** 10 slider values -> 15 engine bands. Bands between two sliders are interpolated in log-frequency. */
private fun expand(ui: List<Float>): List<Float> {
    val out = FloatArray(EQ_BAND_FREQS_HZ.size)
    for (j in out.indices) {
        val exact = UI_TO_ENGINE.indexOf(j)
        if (exact >= 0) {
            out[j] = ui[exact]
            continue
        }
        var k = 0
        while (k < UI_TO_ENGINE.size - 1 && UI_TO_ENGINE[k + 1] < j) k++
        val lo = UI_TO_ENGINE[k]
        val hi = UI_TO_ENGINE[(k + 1).coerceAtMost(UI_TO_ENGINE.lastIndex)]
        val fLo = ln(EQ_BAND_FREQS_HZ[lo].toFloat())
        val fHi = ln(EQ_BAND_FREQS_HZ[hi].toFloat())
        val t = if (fHi == fLo) 0f else (ln(EQ_BAND_FREQS_HZ[j].toFloat()) - fLo) / (fHi - fLo)
        out[j] = ui[k] + (ui[(k + 1).coerceAtMost(ui.lastIndex)] - ui[k]) * t
    }
    return out.toList()
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val prefs: EqualizerPreferences,
    private val loudnessPrefs: LoudnessPrefs,
) : ViewModel() {

    private var dragging = false
    private var releaseJob: Job? = null

    private val _bands = MutableStateFlow(List(UI_BAND_FREQS.size) { 0f })
    val bands: StateFlow<List<Float>> = _bands.asStateFlow()

    val enabled: StateFlow<Boolean> = prefs.rawSettings.map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val presetName: StateFlow<String> = prefs.rawSettings.map { it.presetName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EqualizerPresets.FLAT.name)

    private val _bass = MutableStateFlow(0f)
    val bass: StateFlow<Float> = _bass.asStateFlow()
    private val _volume = MutableStateFlow(0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    private val _balance = MutableStateFlow(0f)
    val balance: StateFlow<Float> = _balance.asStateFlow()

    private val gainsWriter = writer<List<Float>> { prefs.setAllGains(it) }
    private val bassWriter = writer<Float> { prefs.setBassBoost(it) }
    private val volumeWriter = writer<Float> { loudnessPrefs.setPreampDb(it * LOUDNESS_PREAMP_MAX_DB) }
    private val balanceWriter = writer<Float> { prefs.setBalance(it) }

    init {
        viewModelScope.launch {
            prefs.rawSettings.collect { if (!dragging) _bands.value = sample(it.gainsDb) }
        }
        viewModelScope.launch { _bass.value = prefs.bassBoost.first() }
        viewModelScope.launch { _balance.value = prefs.balance.first() }
        viewModelScope.launch {
            _volume.value = (loudnessPrefs.preampDb.value / LOUDNESS_PREAMP_MAX_DB).coerceIn(0f, 1f)
        }
    }

    /** Conflated: a fast drag writes the latest value, never a backlog. */
    private fun <T> writer(block: suspend (T) -> Unit): Channel<T> {
        val ch = Channel<T>(Channel.CONFLATED)
        viewModelScope.launch {
            for (v in ch) {
                try {
                    block(v)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
        return ch
    }

    fun setEnabled(on: Boolean) {
        viewModelScope.launch { runCatching { prefs.setEnabled(on) } }
    }

    fun onBand(index: Int, gainDb: Float) {
        if (index !in _bands.value.indices) return
        dragging = true
        releaseJob?.cancel()
        val next = _bands.value.toMutableList()
        next[index] = ((gainDb * 10f).roundToInt() / 10f).coerceIn(-8f, 8f)
        _bands.value = next
        gainsWriter.trySend(expand(next))
    }

    fun onBandDragEnd() {
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            delay(350)
            dragging = false
        }
    }

    fun applyPreset(name: String) {
        EqualizerPresets.byName(name)?.let { preset ->
            dragging = false
            viewModelScope.launch { runCatching { prefs.applyPreset(preset) } }
        }
    }

    /** Custom / Save: keep what's on screen and mark it as the Custom curve. */
    fun saveCustom() {
        gainsWriter.trySend(expand(_bands.value))
    }

    fun setBass(v: Float) {
        _bass.value = v
        bassWriter.trySend(v)
        if (v > 0f && !enabled.value) setEnabled(true)
    }

    fun setVolume(v: Float) {
        _volume.value = v
        volumeWriter.trySend(v)
    }

    fun setBalance(v: Float) {
        val snapped = if (kotlin.math.abs(v) < 0.04f) 0f else v
        _balance.value = snapped
        balanceWriter.trySend(snapped)
    }

    fun resetBalance() = setBalance(0f)
}
