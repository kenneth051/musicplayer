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

    private fun songToMediaItem(song: Song): MediaItem {
        val uniqueId = UUID.randomUUID().toString()
        return MediaItem.Builder()
            .setMediaId("${song.id}|$uniqueId")
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
                    })
                    .build()
            )
            .build()
    }

    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        val player = _controller.value ?: return

        val mediaItems = songs.map { songToMediaItem(it) }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
    }

    fun appendToQueue(song: Song) {
        val player = _controller.value ?: return
        player.addMediaItem(songToMediaItem(song))
        if (!player.isPlaying && player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    fun appendToQueue(songs: List<Song>) {
        val player = _controller.value ?: return
        val items = songs.map { songToMediaItem(it) }
        if (items.isEmpty()) return
        player.addMediaItems(items)
        if (!player.isPlaying && player.mediaItemCount == items.size) {
            player.prepare()
            player.play()
        }
    }

    fun playNext(song: Song) {
        val player = _controller.value ?: return
        val index = player.currentMediaItemIndex + 1
        player.addMediaItem(index.coerceAtLeast(0), songToMediaItem(song))
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

    fun clearQueue() {
        val player = _controller.value ?: return
        player.clearMediaItems()
    }

    fun togglePlayPause() = _controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    fun pause() = _controller.value?.pause()
    fun skipNext() = _controller.value?.seekToNext()
    fun skipPrevious() = _controller.value?.seekToPrevious()
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
    }
}
