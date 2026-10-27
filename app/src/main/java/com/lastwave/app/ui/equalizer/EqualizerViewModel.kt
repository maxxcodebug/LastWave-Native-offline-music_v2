package com.lastwave.app.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.EQ_BAND_FREQS_HZ
import com.lastwave.app.data.local.EQ_MAX_GAIN_DB
import com.lastwave.app.data.local.EqualizerPreferences
import com.lastwave.app.data.local.EqualizerPresets
import com.lastwave.app.data.local.composeEqGains
import com.lastwave.app.playback.NativeAudioEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.roundToInt

/** Volume Boost at 100%, in dB. Applied as a real gain stage ahead of the limiter. */
const val VOLUME_BOOST_MAX_DB = 12f

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

/**
 * Drag = audio changes immediately through a conflated preview channel (no disk).
 * The value is saved once, when the finger lifts.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val prefs: EqualizerPreferences,
    private val audioEngine: dagger.Lazy<NativeAudioEngine>,
) : ViewModel() {

    private var dragging = false
    private var releaseJob: Job? = null
    private var boostSaveJob: Job? = null

    private val _bands = MutableStateFlow(List(UI_BAND_FREQS.size) { 0f })
    val bands: StateFlow<List<Float>> = _bands.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _presetName = MutableStateFlow(EqualizerPresets.FLAT.name)
    val presetName: StateFlow<String> = _presetName.asStateFlow()

    private val _bass = MutableStateFlow(0f)
    val bass: StateFlow<Float> = _bass.asStateFlow()

    private val _volume = MutableStateFlow(0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val previews = Channel<Pair<Boolean, FloatArray>>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for ((on, gains) in previews) {
                try {
                    audioEngine.get().setEqualizer(on, gains)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                } catch (_: LinkageError) {
                }
            }
        }
        viewModelScope.launch {
            prefs.rawSettings.collect {
                if (!dragging) {
                    _enabled.value = it.enabled
                    _presetName.value = it.presetName
                    _bands.value = sample(it.gainsDb)
                }
            }
        }
        viewModelScope.launch { _bass.value = prefs.bassBoost.first() }
        viewModelScope.launch { _volume.value = prefs.volumeBoost.first() }
    }

    private fun pushPreview() {
        val gains = composeEqGains(
            curve = expand(_bands.value),
            curveOn = _enabled.value,
            bass = _bass.value,
            volume = _volume.value,
        )
        val on = _enabled.value || _bass.value > 0.001f || _volume.value > 0.001f
        previews.trySend(on to gains.toFloatArray())
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        pushPreview()
        viewModelScope.launch { runCatching { prefs.setEnabled(on) } }
    }

    fun onBand(index: Int, gainDb: Float) {
        if (index !in _bands.value.indices) return
        releaseJob?.cancel()
        if (!dragging) {
            dragging = true
            if (!_enabled.value) {
                _enabled.value = true
                viewModelScope.launch { runCatching { prefs.setEnabled(true) } }
            }
        }
        val next = _bands.value.toMutableList()
        next[index] = ((gainDb * 10f).roundToInt() / 10f).coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)
        _bands.value = next
        _presetName.value = EqualizerPresets.CUSTOM_NAME
        pushPreview()
    }

    fun onBandDragEnd() {
        val toSave = expand(_bands.value)
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            runCatching { prefs.setAllGains(toSave) }
            delay(250)
            dragging = false
        }
    }

    fun applyPreset(name: String) {
        EqualizerPresets.byName(name)?.let { preset ->
            dragging = false
            _enabled.value = true
            _presetName.value = preset.name
            _bands.value = sample(preset.gainsDb)
            pushPreview()
            viewModelScope.launch { runCatching { prefs.applyPreset(preset) } }
        }
    }

    /** "Custom" / "Save": keep exactly what is on screen as the Custom curve. */
    fun saveCustom() {
        _presetName.value = EqualizerPresets.CUSTOM_NAME
        val toSave = expand(_bands.value)
        viewModelScope.launch { runCatching { prefs.setAllGains(toSave) } }
    }

    fun resetAll() {
        _bass.value = 0f
        _volume.value = 0f
        viewModelScope.launch {
            runCatching {
                prefs.setBassBoost(0f)
                prefs.setVolumeBoost(0f)
            }
        }
        applyPreset(EqualizerPresets.FLAT.name)
    }

    fun setBass(v: Float) {
        _bass.value = v
        pushPreview()
        saveBoostsSoon()
    }

    fun setVolume(v: Float) {
        _volume.value = v
        pushPreview()
        saveBoostsSoon()
    }

    /** Boost sliders save 300 ms after the last movement, never per frame. */
    private fun saveBoostsSoon() {
        boostSaveJob?.cancel()
        boostSaveJob = viewModelScope.launch {
            delay(300)
            runCatching {
                prefs.setBassBoost(_bass.value)
                prefs.setVolumeBoost(_volume.value)
            }
        }
    }
}
