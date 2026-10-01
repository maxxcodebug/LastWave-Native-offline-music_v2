package com.lastwave.app.data.offline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lastwave.app.data.local.readSafely
import com.lastwave.app.data.local.recoverPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline mode + the user's music folders.
 *
 * [offlineMode] is null until DataStore has produced its first value, so the
 * launch gate can wait instead of flashing the login screen.
 */
@Singleton
class OfflinePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    externalScope: CoroutineScope,
) {
    private object Keys {
        val OFFLINE_MODE = booleanPreferencesKey("lw_offline_mode")
        val FOLDERS = stringSetPreferencesKey("lw_offline_folders")
    }

    val offlineMode: StateFlow<Boolean?> = dataStore.data
        .recoverPreferences("OfflinePreferences.mode")
        .map { p -> p.readSafely(Keys.OFFLINE_MODE) ?: false }
        .stateIn(externalScope, SharingStarted.Eagerly, null)

    val folders: StateFlow<Set<String>> = dataStore.data
        .recoverPreferences("OfflinePreferences.folders")
        .map { p -> p.readSafely(Keys.FOLDERS) ?: emptySet() }
        .stateIn(externalScope, SharingStarted.Eagerly, emptySet())

    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { it[Keys.OFFLINE_MODE] = enabled }
    }

    suspend fun addFolder(treeUri: String) {
        dataStore.edit { it[Keys.FOLDERS] = (it[Keys.FOLDERS] ?: emptySet()) + treeUri }
    }

    suspend fun removeFolder(treeUri: String) {
        dataStore.edit { it[Keys.FOLDERS] = (it[Keys.FOLDERS] ?: emptySet()) - treeUri }
    }
}
