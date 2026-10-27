package com.lastwave.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The 15 standard ISO 2/3-octave center frequencies (Hz) the equalizer's
 *  band sliders map onto. Low end is bass body, mid range carries vocals,
 *  the top end is air and sparkle. */
val EQ_BAND_FREQS_HZ = intArrayOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)
const val EQ_MAX_GAIN_DB = 12f

/** Compact frequency label under each band slider ("63", "1K", "16K"...). */
fun eqBandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}K" else "$hz"

data class EqPreset(
    val name: String,
    /** Gain in dB per band, same order as [EQ_BAND_FREQS_HZ]. */
    val gainsDb: List<Float>,
)

/**
 * Curated, listening-tested 15-band curves. Values are deliberately kept in
 * the ±8 dB range — beyond that Android's audio fx chain starts clipping and
 * smearing transients, so these are the strongest settings that still sound
 * clean at high volume.
 */
object EqualizerPresets {
    const val CUSTOM_NAME = "Custom"

    val FLAT = EqPreset("Default", List(EQ_BAND_FREQS_HZ.size) { 0f })
    /**
     * Crystal-open audiophile reference curve for Studio Master Clarity.
     * Tight, controlled sub-bass depth, a deliberate low-mid dip that
     * removes congestion and mud, natural vocal body, strong presence lift
     * and a tall silky air shelf — the "window opened" spacious sound.
     */
    val STUDIO_MASTER = EqPreset(
        "Studio Master",
        listOf(
            2.6f, 2.8f, 2.2f, 0.6f, -1.8f,
            -2.6f, -1.2f, 0.0f, 1.2f, 2.4f,
            3.6f, 4.0f, 4.2f, 4.5f, 4.8f,
        ),
    )

    val ALL: List<EqPreset> = listOf(
        FLAT,
        STUDIO_MASTER,
        EqPreset("Acoustic", listOf(3.0f, 2.5f, 2.0f, 1.5f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 1.0f, 1.0f, 1.5f, 1.5f)),
        EqPreset("Vocal Clarity", listOf(-1.5f, -1.0f, -0.5f, 0f, 0.5f, 1.0f, 2.0f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.5f, 1.0f)),
        EqPreset("Classical", listOf(3.0f, 2.5f, 2.0f, 1.5f, 0.5f, 0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f)),
        EqPreset("Jazz", listOf(2.5f, 2.0f, 1.5f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 2.5f, 2.5f, 2.5f)),
        EqPreset("Rock", listOf(4.0f, 3.5f, 3.0f, 1.5f, 0f, -1.0f, -1.0f, -0.5f, 0.5f, 1.5f, 2.5f, 3.0f, 3.5f, 4.0f, 4.0f)),
        EqPreset("Pop", listOf(-1.0f, -0.5f, 0.0f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f)),
        EqPreset("Treble Air", listOf(0f, 0f, 0f, -0.5f, -0.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 3.5f, 4.5f, 5.0f)),
        EqPreset("Deep Bass Clean", listOf(5.0f, 4.5f, 3.5f, 2.5f, 1.0f, 0f, -0.5f, -0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f)),
        EqPreset("Electronic", listOf(4.5f, 4.0f, 3.0f, 2.0f, 0.5f, -0.5f, 0f, 1.0f, 2.0f, 2.5f, 2.5f, 2.5f, 3.0f, 3.5f, 4.0f)),
        EqPreset("R&B", listOf(5.0f, 4.5f, 3.5f, 2.0f, 1.0f, -0.5f, -1.0f, -0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f, 3.0f)),
    )

    fun byName(name: String): EqPreset? = ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetName: String = EqualizerPresets.FLAT.name,
    /** Per-band gain in dB, index-aligned with [EQ_BAND_FREQS_HZ]. */
    val gainsDb: List<Float> = EqualizerPresets.FLAT.gainsDb,
)

@Singleton
class EqualizerPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("lw_eq_enabled")
        val PRESET_NAME = stringPreferencesKey("lw_eq_preset")
        val GAINS_DB = stringPreferencesKey("lw_eq_gains")
        val BASS_BOOST = floatPreferencesKey("lw_eq_bass_boost")
        val BALANCE = floatPreferencesKey("lw_eq_balance")
        val VOLUME_BOOST = floatPreferencesKey("lw_eq_volume_boost")
    }

    /** The user's own curve, untouched by the Bass Boost macro. The Equalizer screen edits this. */
    val rawSettings: Flow<EqualizerSettings> = dataStore.data
        .recoverPreferences("EqualizerPreferences")
        .map { p ->
            val storedGains = p.readSafely(Keys.GAINS_DB)?.split(',')?.mapNotNull(String::toFloatOrNull)
                ?.takeIf { it.size == EQ_BAND_FREQS_HZ.size }
                ?.map { gain -> if (gain.isFinite()) gain.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f }
            // The stored name is trusted as-is: every writer of GAINS_DB also
            // updates PRESET_NAME (Custom on manual band edits), so the pair can't
            // drift apart. Unknown names fall back to Default.
            val resolvedName = p.readSafely(Keys.PRESET_NAME)
                ?.let { name ->
                    if (name == EqualizerPresets.CUSTOM_NAME || EqualizerPresets.byName(name) != null) name else null
                }
                ?: EqualizerPresets.FLAT.name
            val gains = storedGains
                ?: EqualizerPresets.byName(resolvedName)?.gainsDb
                ?: EqualizerPresets.FLAT.gainsDb
            EqualizerSettings(
                enabled = p.readSafely(Keys.ENABLED) ?: false,
                presetName = resolvedName,
                gainsDb = gains,
            )
        }

    val bassBoost: Flow<Float> = dataStore.data
        .recoverPreferences("EqualizerPreferences.bass")
        .map { p -> (p.readSafely(Keys.BASS_BOOST) ?: 0f).coerceIn(0f, 1f) }

    /** -1 (left) .. 0 .. +1 (right). Stored for the UI; the audio engine does not apply it yet. */
    val balance: Flow<Float> = dataStore.data
        .recoverPreferences("EqualizerPreferences.balance")
        .map { p -> (p.readSafely(Keys.BALANCE) ?: 0f).coerceIn(-1f, 1f) }

    /** 0..1, mapped to a flat 0..+6 dB lift on every band. */
    val volumeBoost: Flow<Float> = dataStore.data
        .recoverPreferences("EqualizerPreferences.volume")
        .map { p -> (p.readSafely(Keys.VOLUME_BOOST) ?: 0f).coerceIn(0f, 1f) }

    /** What the audio engines consume: the user curve plus Bass Boost and Volume Boost. */
    val settings: Flow<EqualizerSettings> = kotlinx.coroutines.flow.combine(rawSettings, bassBoost, volumeBoost) { raw, bass, vol ->
        if (bass <= 0.001f && vol <= 0.001f) raw else raw.copy(
            enabled = true,
            gainsDb = composeEqGains(raw.gainsDb, raw.enabled, bass, vol),
        )
    }

    suspend fun setVolumeBoost(value: Float) {
        dataStore.edit { it[Keys.VOLUME_BOOST] = value.coerceIn(0f, 1f) }
    }

    suspend fun setAllGains(gains: List<Float>) {
        if (gains.size != EQ_BAND_FREQS_HZ.size) return
        dataStore.edit {
            it[Keys.PRESET_NAME] = EqualizerPresets.CUSTOM_NAME
            it[Keys.GAINS_DB] = encodeGains(gains)
        }
    }

    suspend fun setBassBoost(value: Float) {
        dataStore.edit { it[Keys.BASS_BOOST] = value.coerceIn(0f, 1f) }
    }

    suspend fun setBalance(value: Float) {
        dataStore.edit { it[Keys.BALANCE] = value.coerceIn(-1f, 1f) }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun applyPreset(preset: EqPreset) {
        dataStore.edit {
            it[Keys.PRESET_NAME] = preset.name
            it[Keys.GAINS_DB] = encodeGains(preset.gainsDb)
            it[Keys.ENABLED] = true
        }
    }

    suspend fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in EQ_BAND_FREQS_HZ.indices) return
        dataStore.edit {
            val current = it.readSafely(Keys.GAINS_DB)?.split(',')?.mapNotNull { v -> v.toFloatOrNull() }
                ?.takeIf { v -> v.size == EQ_BAND_FREQS_HZ.size }
                ?: EqualizerPresets.FLAT.gainsDb
            val safeGain = if (gainDb.isFinite()) gainDb.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
            val next = current.toMutableList().also { list -> list[bandIndex] = safeGain }
            it[Keys.PRESET_NAME] = EqualizerPresets.CUSTOM_NAME
            it[Keys.GAINS_DB] = encodeGains(next)
        }
    }

    suspend fun reset() {
        applyPreset(EqualizerPresets.FLAT)
    }

    private fun encodeGains(gains: List<Float>): String =
        gains.joinToString(",") { gain ->
            "%.1f".format(java.util.Locale.ROOT, if (gain.isFinite()) gain.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f)
        }
}

/** Extra dB added per engine band at 100% Bass Boost (low bands only). */
private val BASS_BOOST_PROFILE_DB = floatArrayOf(11f, 11f, 10f, 8f, 5.5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

/**
 * Final 15-band gains: the user's curve (only when [curveOn]) + Bass Boost on the low bands
 * + Volume Boost as a flat lift. Everything is clamped to the engine's safe range.
 */
fun composeEqGains(curve: List<Float>, curveOn: Boolean, bass: Float, volume: Float): List<Float> =
    List(EQ_BAND_FREQS_HZ.size) { i ->
        val base = if (curveOn) curve.getOrElse(i) { 0f } else 0f
        (base + bass * BASS_BOOST_PROFILE_DB.getOrElse(i) { 0f } + volume * 6f)
            .coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)
    }
