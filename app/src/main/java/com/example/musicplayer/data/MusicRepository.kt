package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val context: Context) {
    
    private val persistence = MusicPersistence(context)

    suspend fun fetchAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relativePathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albId = cursor.getLong(albIdCol)
                val rawPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null
                val parentFolder = when {
                    !rawPath.isNullOrBlank() -> runCatching { File(rawPath).parentFile?.name }.getOrNull() ?: "Unknown"
                    !relativePath.isNullOrBlank() -> relativePath.trim('/').substringAfterLast('/').ifBlank { "Unknown" }
                    else -> "Unknown"
                }

                val artUri = Uri.parse("content://media/external/audio/albumart/$albId")

                songList.add(Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown",
                    duration = cursor.getInt(durCol),
                    album = cursor.getString(albCol) ?: "Unknown",
                    contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    albumArtUri = artUri,
                    dateAdded = cursor.getLong(dateCol),
                    parentFolder = parentFolder
                ))
            }
        }

        val favIds = persistence.favorites.first()
        val playCounts = persistence.playCounts.first()
        songList.map { it.copy(isFavorite = it.id in favIds, playCount = playCounts[it.id] ?: 0) }
    }

    suspend fun saveFavorites(favIds: Set<Long>) = persistence.saveFavorites(favIds)
    fun getPlaylists() = persistence.playlists
    suspend fun savePlaylists(playlists: List<Playlist>) = persistence.savePlaylists(playlists)
    fun getQueues() = persistence.queues
    suspend fun saveQueues(queues: List<Queue>) = persistence.saveQueues(queues)
    fun getRecentlyPlayedIds() = persistence.recentlyPlayed
    suspend fun saveRecentlyPlayedIds(ids: List<Long>) = persistence.saveRecentlyPlayed(ids)
    fun getPlayCounts() = persistence.playCounts
    suspend fun savePlayCounts(counts: Map<Long, Int>) = persistence.savePlayCounts(counts)
    fun getExcludeWhatsAppAudio() = persistence.excludeWhatsAppAudio
    suspend fun saveExcludeWhatsAppAudio(exclude: Boolean) = persistence.saveExcludeWhatsAppAudio(exclude)
}
