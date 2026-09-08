package com.example.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
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

/**
 * Manages the Media3 Controller and exposes simple playback methods.
 */
class PlaybackManager(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    fun init(onControllerReady: (MediaController) -> Unit) {
        if (_controller.value != null) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _controller.value = controller
            controller?.let { onControllerReady(it) }
        }, MoreExecutors.directExecutor())
    }

    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        val player = _controller.value ?: return
        
        val mediaItems = songs.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(s.albumArtUri)
                        // Providing duration here can help the system UI progress bar
                        .setExtras(Bundle().apply {
                            putLong("duration", s.duration.toLong())
                        })
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
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
}
