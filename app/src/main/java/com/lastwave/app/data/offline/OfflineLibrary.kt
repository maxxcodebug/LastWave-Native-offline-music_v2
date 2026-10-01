package com.lastwave.app.data.offline

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.playback.PlayableTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class OfflineSource { DOWNLOAD, FOLDER }

/** One playable offline song, whether it was downloaded in-app or found in a user folder. */
@Serializable
data class OfflineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val uri: String,
    val mimeType: String = "audio/*",
    val artworkUrl: String? = null,
    val source: OfflineSource = OfflineSource.FOLDER,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val format: String = "",
) {
    fun toPlayable() = PlayableTrack(
        title = title,
        artist = artist,
        album = album.takeIf { it.isNotBlank() },
        artworkUrl = artworkUrl,
        playbackUrl = uri,
        playbackMimeType = mimeType,
        durationMs = durationMs.takeIf { it > 0L },
    )
}

data class OfflineArtist(
    val name: String,
    val tracks: List<OfflineTrack>,
    val albumCount: Int,
    val artworkUrl: String?,
)

data class OfflineAlbum(
    val title: String,
    val artist: String,
    val tracks: List<OfflineTrack>,
    val artworkUrl: String?,
)

const val UNKNOWN_ARTIST = "Unknown Artist"

fun List<OfflineTrack>.toArtists(): List<OfflineArtist> =
    groupBy { it.artist.trim().lowercase() }
        .map { (_, list) ->
            val name = list.first().artist.trim().ifBlank { UNKNOWN_ARTIST }
            OfflineArtist(
                name = name,
                tracks = list.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() })),
                albumCount = list.map { it.album.trim().lowercase() }.filter { it.isNotBlank() }.distinct().size,
                artworkUrl = list.firstNotNullOfOrNull { it.artworkUrl },
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<OfflineTrack>.toAlbums(): List<OfflineAlbum> =
    filter { it.album.isNotBlank() }
        .groupBy { it.album.trim().lowercase() + "\u0000" + it.artist.trim().lowercase() }
        .map { (_, list) ->
            OfflineAlbum(
                title = list.first().album.trim(),
                artist = list.first().artist.trim().ifBlank { UNKNOWN_ARTIST },
                tracks = list.sortedBy { it.title.lowercase() },
                artworkUrl = list.firstNotNullOfOrNull { it.artworkUrl },
            )
        }
        .sortedBy { it.title.lowercase() }

private fun DownloadedTrackEntity.toOffline(): OfflineTrack {
    val best = when {
        filePath.startsWith("/") && File(filePath).exists() -> filePath
        !mediaStoreUri.isNullOrBlank() -> mediaStoreUri
        else -> filePath
    }
    val mime = when {
        filePath.endsWith(".flac", true) || formatBadge.contains("FLAC") -> "audio/flac"
        filePath.endsWith(".m4a", true) || filePath.endsWith(".mp4", true) || formatBadge.contains("M4A") -> "audio/mp4"
        filePath.endsWith(".opus", true) || formatBadge.contains("OPUS") -> "audio/ogg"
        filePath.endsWith(".mp3", true) || formatBadge.contains("MP3") -> "audio/mpeg"
        else -> "audio/flac"
    }
    return OfflineTrack(
        id = "dl:$id",
        title = title,
        artist = artist.trim().ifBlank { UNKNOWN_ARTIST },
        album = album,
        durationMs = durationMs,
        uri = best,
        mimeType = mime,
        artworkUrl = artworkUrl,
        source = OfflineSource.DOWNLOAD,
        sizeBytes = fileSizeBytes,
        modifiedAt = downloadedAtMillis,
        format = formatBadge,
    )
}

data class ScanState(val scanning: Boolean = false, val found: Int = 0)

/** Walks SAF trees and reads tags. Unchanged files are served from the cache. */
@Singleton
class OfflineLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioExt = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma", "oga", "mka")

    private data class Doc(val docId: String, val name: String, val mime: String, val size: Long, val modified: Long)

    private fun listChildren(tree: Uri, parentId: String): List<Doc> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = ArrayList<Doc>()
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += Doc(
                        docId = c.getString(0).orEmpty(),
                        name = c.getString(1).orEmpty(),
                        mime = c.getString(2).orEmpty(),
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        modified = if (c.isNull(4)) 0L else c.getLong(4),
                    )
                }
            }
        }
        return out
    }

    private fun isAudio(d: Doc): Boolean =
        d.mime.startsWith("audio/") || d.mime == "application/ogg" ||
            d.name.substringAfterLast('.', "").lowercase() in audioExt

    suspend fun scan(
        trees: Set<String>,
        cache: Map<String, OfflineTrack>,
        onProgress: (Int) -> Unit,
    ): List<OfflineTrack> = withContext(Dispatchers.IO) {
        val files = ArrayList<Pair<Uri, Doc>>()
        for (raw in trees) {
            val tree = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            val stack = ArrayDeque<String>()
            stack.addLast(rootId)
            while (stack.isNotEmpty()) {
                val parent = stack.removeLast()
                for (d in listChildren(tree, parent)) {
                    if (d.mime == DocumentsContract.Document.MIME_TYPE_DIR) stack.addLast(d.docId)
                    else if (isAudio(d)) files += tree to d
                }
            }
        }
        var found = 0
        val gate = Semaphore(4)
        coroutineScope {
            files.map { (tree, d) ->
                async {
                    gate.withPermit {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, d.docId)
                        val id = "fs:$docUri"
                        val cached = cache[id]
                        val track = if (cached != null && cached.sizeBytes == d.size && cached.modifiedAt == d.modified) {
                            cached
                        } else {
                            readTrack(docUri, id, d)
                        }
                        synchronized(this@OfflineLibraryScanner) { found++ ; onProgress(found) }
                        track
                    }
                }
            }.awaitAll()
        }.filterNotNull()
    }

    private fun readTrack(docUri: Uri, id: String, d: Doc): OfflineTrack? {
        val ext = d.name.substringAfterLast('.', "").lowercase()
        val mime = when {
            d.mime.startsWith("audio/") -> d.mime
            ext == "m4a" || ext == "aac" -> "audio/mp4"
            ext == "flac" -> "audio/flac"
            ext == "mp3" -> "audio/mpeg"
            ext == "ogg" || ext == "opus" || ext == "oga" -> "audio/ogg"
            ext == "wav" -> "audio/wav"
            else -> "audio/*"
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, docUri)
            fun tag(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            val title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: d.name.substringBeforeLast('.')
            val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: UNKNOWN_ARTIST
            val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val dur = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrateK = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.div(1000)
            OfflineTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = dur,
                uri = docUri.toString(),
                mimeType = mime,
                artworkUrl = saveArtwork(r, artist, album, title),
                source = OfflineSource.FOLDER,
                sizeBytes = d.size,
                modifiedAt = d.modified,
                format = if (ext.isNotEmpty()) ext.uppercase() + (bitrateK?.let { " • ${it}k" } ?: "") else "",
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Embedded cover → one jpg per album/artist in cacheDir; returns the file path. */
    private fun saveArtwork(r: MediaMetadataRetriever, artist: String, album: String, title: String): String? {
        val bytes = runCatching { r.embeddedPicture }.getOrNull() ?: return null
        val key = if (album.isNotBlank()) "$artist|$album" else "$artist|$title"
        val dir = File(context.cacheDir, "offline_art").apply { mkdirs() }
        val f = File(dir, "${key.lowercase().hashCode().toUInt()}.jpg")
        if (!f.exists()) runCatching { f.writeBytes(bytes) }
        return f.takeIf { it.exists() }?.absolutePath
    }
}

/** Single source of truth for the Offline tab: downloads + scanned folders. */
@Singleton
class OfflineLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: OfflineLibraryScanner,
    private val prefs: OfflinePreferences,
    downloadedTrackDao: DownloadedTrackDao,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile get() = File(context.filesDir, "offline_library.json")
    private val folderTracks = MutableStateFlow<List<OfflineTrack>>(loadCache())
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private var scanJob: Job? = null

    private val downloads: Flow<List<OfflineTrack>> = downloadedTrackDao.getAll()
        .catch { emit(emptyList()) }
        .map { list -> list.map { it.toOffline() } }

    /** Downloads win over a folder copy of the same song. */
    val tracks: Flow<List<OfflineTrack>> = combine(downloads, folderTracks) { dl, fs ->
        fun key(t: OfflineTrack) = t.title.trim().lowercase() + "\u0000" + t.artist.trim().lowercase()
        val seen = dl.mapTo(HashSet(), ::key)
        (dl + fs.filter { seen.add(key(it)) })
            .sortedBy { it.title.lowercase() }
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = scope.launch {
            val trees = prefs.folders.value
            if (trees.isEmpty()) {
                folderTracks.value = emptyList()
                saveCache(emptyList())
                _scanState.value = ScanState()
                return@launch
            }
            _scanState.value = ScanState(scanning = true, found = 0)
            val cache = folderTracks.value.associateBy { it.id }
            val result = runCatching {
                scanner.scan(trees, cache) { n -> _scanState.value = ScanState(true, n) }
            }.getOrNull()
            if (result != null) {
                folderTracks.value = result
                saveCache(result)
            }
            _scanState.value = ScanState(scanning = false, found = result?.size ?: folderTracks.value.size)
        }
    }

    private fun loadCache(): List<OfflineTrack> = runCatching {
        json.decodeFromString<List<OfflineTrack>>(cacheFile.readText())
    }.getOrDefault(emptyList())

    private fun saveCache(list: List<OfflineTrack>) {
        runCatching { cacheFile.writeText(json.encodeToString(list)) }
    }
}
