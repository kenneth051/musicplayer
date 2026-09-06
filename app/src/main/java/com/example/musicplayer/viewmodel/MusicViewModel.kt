package com.example.musicplayer.viewmodel

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.MusicPersistence
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song
import com.example.musicplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed

    private var persistence: MusicPersistence? = null

    val filteredSongs: StateFlow<List<Song>> = combine(_songs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        if (persistence == null) {
            persistence = MusicPersistence(context)
            loadStoredData()
        }
        if (_controller.value != null) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _controller.value = controller
            controller?.let { setupControllerListener(it) }
        }, MoreExecutors.directExecutor())
    }

    private fun loadStoredData() {
        viewModelScope.launch {
            persistence?.let { p ->
                val favs = p.favorites.first()
                val plists = p.playlists.first()
                val recentIds = p.recentlyPlayed.first()

                _playlists.value = plists
                
                // We'll update the favorite status once songs are loaded
                updateFavoriteStatus(favs)
                
                // We'll update recently played once songs are loaded
                updateRecentlyPlayed(recentIds)
            }
        }
    }

    private fun updateFavoriteStatus(favIds: Set<Long>) {
        _songs.value = _songs.value.map { song ->
            song.copy(isFavorite = song.id in favIds)
        }
    }

    private fun updateRecentlyPlayed(recentIds: List<Long>) {
        val loadedSongs = _songs.value
        _recentlyPlayed.value = recentIds.mapNotNull { id -> loadedSongs.find { it.id == id } }
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
            override fun onMediaMetadataChanged(metadata: MediaMetadata) { updatePlaybackState(player) }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updatePlaybackState(player) }
            override fun onRepeatModeChanged(repeatMode: Int) { updatePlaybackState(player) }
            override fun onPlaybackParametersChanged(params: PlaybackParameters) { updatePlaybackState(player) }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) { updatePlaybackState(player) }
        })
        updatePlaybackState(player)
    }

    private fun updatePlaybackState(player: Player) {
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentSong = _songs.value.find { it.id.toString() == currentMediaId }

        if (currentSong != null && currentSong != _playbackState.value.currentSong) {
            addToRecentlyPlayed(currentSong)
        }

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

    private fun addToRecentlyPlayed(song: Song) {
        val currentList = _recentlyPlayed.value.toMutableList()
        currentList.remove(song)
        currentList.add(0, song)
        val updated = currentList.take(20)
        _recentlyPlayed.value = updated
        
        viewModelScope.launch {
            persistence?.saveRecentlyPlayed(updated.map { it.id })
        }
    }

    private fun startProgressTracking(player: Player) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                updatePlaybackState(player)
                delay(1000)
            }
        }
    }

    private fun stopProgressTracking() { progressJob?.cancel() }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun toggleFavorite(song: Song) {
        val updatedSongs = _songs.value.map {
            if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        _songs.value = updatedSongs
        
        viewModelScope.launch {
            val favIds = updatedSongs.filter { it.isFavorite }.map { it.id }.toSet()
            persistence?.saveFavorites(favIds)
        }
    }

    fun playSong(song: Song) { prepareAndPlay(_songs.value, _songs.value.indexOf(song), false) }
    fun shuffleAll() { prepareAndPlay(_songs.value, 0, true) }

    fun playPlaylist(playlist: Playlist, song: Song? = null) {
        val playlistSongs = _songs.value.filter { it.id in playlist.songIds }
        if (playlistSongs.isEmpty()) return
        val startIndex = if (song != null) playlistSongs.indexOf(song) else 0
        prepareAndPlay(playlistSongs, startIndex.coerceAtLeast(0), false)
    }

    private fun prepareAndPlay(songList: List<Song>, startIndex: Int, enableShuffle: Boolean) {
        val player = _controller.value ?: return
        if (songList.isEmpty()) return

        val mediaItems = songList.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(s.albumArtUri)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.shuffleModeEnabled = enableShuffle
        player.prepare()
        player.play()
    }

    fun togglePlayPause() = _controller.value?.let { if (it.isPlaying) it.pause() else it.play() }
    fun skipNext() = _controller.value?.seekToNext()
    fun skipPrevious() = _controller.value?.seekToPrevious()
    fun seekTo(position: Long) = _controller.value?.seekTo(position)
    fun toggleShuffle() = _controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun setPlaybackSpeed(speed: Float) = _controller.value?.setPlaybackSpeed(speed)
    fun toggleRepeat() = _controller.value?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun createPlaylist(name: String) {
        val updated = _playlists.value + Playlist(name = name)
        _playlists.value = updated
        viewModelScope.launch { persistence?.savePlaylists(updated) }
    }

    fun addSongToPlaylist(song: Song, playlistId: String) {
        val updated = _playlists.value.map { 
            if (it.id == playlistId && song.id !in it.songIds) it.copy(songIds = it.songIds + song.id) else it
        }
        _playlists.value = updated
        viewModelScope.launch { persistence?.savePlaylists(updated) }
    }

    fun loadSongs(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val fetched = fetchAudioFiles(context)
            _songs.value = fetched
            
            // Re-apply favorites and recently played after fetch
            persistence?.let { p ->
                val favIds = p.favorites.first()
                updateFavoriteStatus(favIds)
                val recentIds = p.recentlyPlayed.first()
                updateRecentlyPlayed(recentIds)
            }
            
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
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED
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

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albId = cursor.getLong(albIdCol)
                    val artUri = Uri.parse("content://media/external/audio/albumart/$albId")
                    
                    songList.add(Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        duration = cursor.getInt(durCol),
                        album = cursor.getString(albCol) ?: "Unknown",
                        contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        albumArtUri = artUri,
                        dateAdded = cursor.getLong(dateCol)
                    ))
                }
            }
            songList
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressTracking()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
