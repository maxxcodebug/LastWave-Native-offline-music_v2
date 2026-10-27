package com.lastwave.app.ui.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.SessionPreferences
import com.lastwave.app.data.offline.OfflineAlbum
import com.lastwave.app.data.offline.OfflineArtist
import com.lastwave.app.data.offline.OfflineLibraryRepository
import com.lastwave.app.data.offline.OfflinePreferences
import com.lastwave.app.data.offline.OfflineTrack
import com.lastwave.app.data.offline.ScanState
import com.lastwave.app.data.offline.toAlbums
import com.lastwave.app.data.offline.toArtists
import com.lastwave.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OfflineLibraryRepository,
    private val offlinePreferences: OfflinePreferences,
    private val sessionPreferences: SessionPreferences,
    private val musicPlayer: MusicPlayer,
) : ViewModel() {

    private fun <T> kotlinx.coroutines.flow.Flow<T>.ui(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val tracks: StateFlow<List<OfflineTrack>> = repository.tracks.ui(emptyList())
    val artists: StateFlow<List<OfflineArtist>> = repository.tracks.map { it.toArtists() }.ui(emptyList())
    val albums: StateFlow<List<OfflineAlbum>> = repository.tracks.map { it.toAlbums() }.ui(emptyList())
    val scanState: StateFlow<ScanState> = repository.scanState
    val folders: StateFlow<Set<String>> = offlinePreferences.folders
    val offlineMode: StateFlow<Boolean?> = offlinePreferences.offlineMode

    init {
        // Cheap when nothing changed: unchanged files come from the cache.
        repository.refresh()
    }

    fun refresh() = repository.refresh()

    fun commitOrder(ids: List<String>) = repository.setOrder(ids)

    fun resetOrder() = repository.setOrder(emptyList())

    fun addFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            offlinePreferences.addFolder(uri.toString())
            repository.refresh()
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            offlinePreferences.removeFolder(uri)
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            repository.refresh()
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && !sessionPreferences.currentSession.isAuthenticated) {
                // Leaving offline mode without an account: stay onboarded as a guest
                // so the next cold start doesn't bounce to the login screen.
                runCatching { sessionPreferences.enterGuestMode() }
            }
            offlinePreferences.setOfflineMode(enabled)
        }
    }

    fun play(track: OfflineTrack, queue: List<OfflineTrack>) {
        val list = queue.ifEmpty { listOf(track) }
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playList(list, index, shuffled = false)
    }

    fun playList(list: List<OfflineTrack>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (list.isEmpty()) return
        val start = if (shuffled) list.indices.random() else startIndex.coerceIn(0, list.lastIndex)
        runCatching {
            musicPlayer.playQueue(
                tracks = list.map { it.toPlayable() },
                startIndex = start,
                sourceLabel = "Offline",
                startShuffled = shuffled,
            )
        }
    }

    fun playNext(track: OfflineTrack) {
        runCatching { musicPlayer.playNext(track.toPlayable()) }
    }

    fun addToQueue(track: OfflineTrack) {
        runCatching { musicPlayer.addToQueue(track.toPlayable()) }
    }
}
