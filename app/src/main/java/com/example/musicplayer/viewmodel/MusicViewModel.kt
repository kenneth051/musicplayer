package com.example.musicplayer.viewmodel

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song
import com.example.musicplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private var progressJob: Job? = null

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentTitle: String = "Not Playing",
        val currentArtist: String = "",
        val currentDuration: Long = 0L,
        val currentPosition: Long = 0L,
        val shuffleModeEnabled: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val playbackSpeed: Float = 1.0f,
        val currentSong: Song? = null
    )

    fun initController(context: Context) {
        if (_controller.value != null) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _controller.value = controller
            controller?.let { setupControllerListener(it) }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState(player)
                if (isPlaying) {
                    startProgressTracking(player)
                } else {
                    stopProgressTracking()
                }
            }

            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                updatePlaybackState(player)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePlaybackState(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updatePlaybackState(player)
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                updatePlaybackState(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updatePlaybackState(player)
            }
        })
        updatePlaybackState(player)
    }

    private fun updatePlaybackState(player: Player) {
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentSong = _songs.value.find { it.id.toString() == currentMediaId }

        _playbackState.value = _playbackState.value.copy(
            isPlaying = player.isPlaying,
            currentTitle = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Not Playing",
            currentArtist = player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "",
            currentDuration = player.duration.coerceAtLeast(0L),
            currentPosition = player.currentPosition.coerceAtLeast(0L),
            shuffleModeEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playbackSpeed = player.playbackParameters.speed,
            currentSong = currentSong
        )
    }

    private fun startProgressTracking(player: Player) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                updatePlaybackState(player)
                delay(1000)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
    }

    fun playSong(song: Song) {
        prepareAndPlay(songs.value.indexOf(song), false)
    }

    fun shuffleAll() {
        prepareAndPlay(0, true)
    }

    private fun prepareAndPlay(startIndex: Int, enableShuffle: Boolean) {
        val player = _controller.value ?: return
        val currentSongs = _songs.value
        if (currentSongs.isEmpty()) return

        val mediaItems = currentSongs.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.shuffleModeEnabled = enableShuffle
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        _controller.value?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() {
        _controller.value?.seekToNext()
    }

    fun skipPrevious() {
        _controller.value?.seekToPrevious()
    }

    fun seekTo(position: Long) {
        _controller.value?.seekTo(position)
    }

    fun toggleShuffle() {
        _controller.value?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _controller.value?.setPlaybackSpeed(speed)
    }

    fun toggleRepeat() {
        _controller.value?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun createPlaylist(name: String) {
        val newPlaylist = Playlist(name = name)
        _playlists.value = _playlists.value + newPlaylist
    }

    fun addSongToPlaylist(song: Song, playlistId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(songIds = playlist.songIds + song.id)
            } else {
                playlist
            }
        }
    }

    fun loadSongs(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = fetchAudioFiles(context)
            _isLoading.value = false
        }
    }

    private suspend fun fetchAudioFiles(context: Context): List<Song> {
        return withContext(Dispatchers.IO) {
            val songList = mutableListOf<Song>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val duration = cursor.getInt(durationColumn)
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songList.add(Song(id, title, artist, contentUri, duration, album))
                }
            }
            songList
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressTracking()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
