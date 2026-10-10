package com.example.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.Song
import com.example.musicplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Manages the Media3 Controller and exposes simple playback methods.
 */
class PlaybackManager(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    fun init(onControllerReady: (MediaController) -> Unit) {
        if (_controller.value != null) return

        try {
            val serviceIntent = Intent(context, PlaybackService::class.java)
            context.startService(serviceIntent)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "PlaybackService start was rejected; continuing without a bound session", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "PlaybackService start was denied; continuing without a bound session", e)
        }

        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    val controller = controllerFuture?.get()
                    _controller.value = controller
                    controller?.let { onControllerReady(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Media controller did not connect; startup will continue without playback controls", e)
                    _controller.value = null
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.w(TAG, "Unable to create MediaController during startup", e)
        }
    }

    private fun songToMediaItem(song: Song, tag: String = "context"): MediaItem {
        val uniqueId = UUID.randomUUID().toString()
        return MediaItem.Builder()
            .setMediaId("${song.id}|$uniqueId|$tag")
            .setUri(song.contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .setExtras(Bundle().apply {
                        putLong("duration", song.duration.toLong())
                        putLong("songId", song.id)
                        putString("tag", tag)
                    })
                    .build()
            )
            .build()
    }

    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean, tag: String = "context") {
        val player = _controller.value ?: return
        if (songs.isEmpty()) return

        // Safety Window: For massive song lists (>1,000 items), window the list around startIndex
        // to prevent Binder IPC limits (TransactionTooLargeException) and memory bloat.
        val (windowedSongs, adjustedStartIndex) = if (songs.size > MAX_QUEUE_ITEMS) {
            val start = (startIndex - 200).coerceAtLeast(0)
            val end = (startIndex + 800).coerceAtMost(songs.size)
            val subset = songs.subList(start, end)
            Pair(subset, (startIndex - start).coerceIn(0, subset.lastIndex))
        } else {
            Pair(songs, startIndex)
        }

        val mediaItems = windowedSongs.map { songToMediaItem(it, tag) }
        val actualStartIndex = if (shuffle) windowedSongs.indices.random() else adjustedStartIndex.coerceIn(0, windowedSongs.lastIndex)

        player.setMediaItems(mediaItems, actualStartIndex, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
    }

    fun appendToQueue(song: Song, tag: String = "manual") {
        val player = _controller.value ?: return
        
        // Find the index to insert: after current song and after any existing manual items
        var insertIndex = player.currentMediaItemIndex + 1
        if (insertIndex > player.mediaItemCount) insertIndex = player.mediaItemCount
        
        // Find the boundary where manual items end and context items begin
        for (i in insertIndex until player.mediaItemCount) {
            val itemTag = player.getMediaItemAt(i).mediaId.substringAfter('|').substringAfter('|')
            if (itemTag == "manual") {
                insertIndex = i + 1
            } else {
                break
            }
        }

        player.addMediaItem(insertIndex, songToMediaItem(song, tag))
        
        if (!player.isPlaying && player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    fun appendToQueue(songs: List<Song>, tag: String = "manual") {
        val player = _controller.value ?: return
        if (songs.isEmpty()) return
        
        var insertIndex = player.currentMediaItemIndex + 1
        if (insertIndex > player.mediaItemCount) insertIndex = player.mediaItemCount
        
        for (i in insertIndex until player.mediaItemCount) {
            val itemTag = player.getMediaItemAt(i).mediaId.substringAfter('|').substringAfter('|')
            if (itemTag == "manual") {
                insertIndex = i + 1
            } else {
                break
            }
        }

        val items = songs.map { songToMediaItem(it, tag) }
        player.addMediaItems(insertIndex, items)
        
        if (!player.isPlaying && player.mediaItemCount == items.size) {
            player.prepare()
            player.play()
        }
    }

    fun playNext(song: Song, tag: String = "manual") {
        val player = _controller.value ?: return
        val index = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(index, songToMediaItem(song, tag))
        if (!player.isPlaying && player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    fun playUri(uri: Uri) {
        val player = _controller.value ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(uri.lastPathSegment ?: "External Audio")
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun removeFromQueue(index: Int) {
        val player = _controller.value ?: return
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
        }
    }

    fun removeSongFromQueue(songId: Long) {
        val player = _controller.value ?: return
        val count = player.mediaItemCount
        val indicesToRemove = mutableListOf<Int>()
        var isCurrentPlayingRemoved = false

        val currentItemIndex = player.currentMediaItemIndex

        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            val mediaId = item.mediaId
            val id = mediaId.split("|").firstOrNull()?.toLongOrNull()
                ?: item.mediaMetadata.extras?.getLong("songId")
            if (id == songId) {
                indicesToRemove.add(i)
                if (i == currentItemIndex) {
                    isCurrentPlayingRemoved = true
                }
            }
        }

        if (indicesToRemove.isEmpty()) {
            val currentItem = player.currentMediaItem
            val currentId = currentItem?.mediaId?.split("|")?.firstOrNull()?.toLongOrNull()
                ?: currentItem?.mediaMetadata?.extras?.getLong("songId")
            if (currentId == songId) {
                player.stop()
                player.clearMediaItems()
            }
            return
        }

        if (isCurrentPlayingRemoved) {
            if (count > indicesToRemove.size) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.stop()
                }
            } else {
                player.stop()
                player.clearMediaItems()
                return
            }
        }

        for (index in indicesToRemove.sortedDescending()) {
            if (index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
            }
        }
    }

    fun clearQueue() {
        val player = _controller.value ?: return
        player.clearMediaItems()
    }

    fun togglePlayPause() = _controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    fun pause() = _controller.value?.pause()
    fun skipNext() = _controller.value?.seekToNextMediaItem()
    fun skipPrevious() = _controller.value?.seekToPreviousMediaItem()
    fun seekTo(pos: Long) = _controller.value?.seekTo(pos)
    fun setSpeed(speed: Float) = _controller.value?.setPlaybackSpeed(speed)
    fun toggleShuffle() = _controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun toggleRepeat() = _controller.value?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        private const val TAG = "PlaybackManager"
        private const val MAX_QUEUE_ITEMS = 1000
    }
}
