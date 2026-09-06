package com.example.musicplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.example.musicplayer.data.MusicRepository
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song
import com.example.musicplayer.player.PlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    val filteredSongs: StateFlow<List<Song>> = combine(_songs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var repository: MusicRepository? = null
    private var playbackManager: PlaybackManager? = null
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    val controller: StateFlow<Player?> get() = playbackManager?.controller ?: MutableStateFlow(null)

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

    fun init(context: Context) {
        if (repository == null) {
            repository = MusicRepository(context)
            playbackManager = PlaybackManager(context)
            
            playbackManager?.init { player ->
                setupControllerListener(player)
            }
            loadStoredMetadata()
        }
    }

    private fun loadStoredMetadata() {
        viewModelScope.launch {
            repository?.let { repo ->
                _playlists.value = repo.getPlaylists().first()
                val recentIds = repo.getRecentlyPlayedIds().first()
                _recentlyPlayed.value = _songs.value.filter { it.id in recentIds }
            }
        }
    }

    private fun setupControllerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState(player)
                if (isPlaying) startProgressTracking(player) else stopProgressTracking()
            }
            override fun onMediaMetadataChanged(metadata: MediaMetadata) { updatePlaybackState(player) }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { updatePlaybackState(player) }
            override fun onRepeatModeChanged(mode: Int) { updatePlaybackState(player) }
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
        val updated = (_recentlyPlayed.value.toMutableList().apply { remove(song); add(0, song) }).take(20)
        _recentlyPlayed.value = updated
        viewModelScope.launch { repository?.saveRecentlyPlayedIds(updated.map { it.id }) }
    }

    private fun startProgressTracking(player: Player) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch { while (true) { updatePlaybackState(player); delay(1000) } }
    }

    private fun stopProgressTracking() { progressJob?.cancel() }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    fun toggleFavorite(song: Song) {
        val updated = _songs.value.map { if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it }
        _songs.value = updated
        viewModelScope.launch { repository?.saveFavorites(updated.filter { it.isFavorite }.map { it.id }.toSet()) }
    }

    fun playSong(song: Song) = playbackManager?.play(_songs.value, _songs.value.indexOf(song), false)
    fun shuffleAll() = playbackManager?.play(_songs.value, 0, true)
    
    fun playPlaylist(playlist: Playlist, song: Song? = null) {
        val list = _songs.value.filter { it.id in playlist.songIds }
        playbackManager?.play(list, if (song != null) list.indexOf(song).coerceAtLeast(0) else 0, false)
    }

    fun togglePlayPause() = playbackManager?.togglePlayPause()
    fun skipNext() = playbackManager?.skipNext()
    fun skipPrevious() = playbackManager?.skipPrevious()
    fun seekTo(pos: Long) = playbackManager?.seekTo(pos)
    fun toggleShuffle() = playbackManager?.toggleShuffle()
    fun setPlaybackSpeed(speed: Float) = playbackManager?.setSpeed(speed)
    fun toggleRepeat() = playbackManager?.toggleRepeat()

    fun createPlaylist(name: String) {
        val updated = _playlists.value + Playlist(name = name)
        _playlists.value = updated
        viewModelScope.launch { repository?.savePlaylists(updated) }
    }

    fun addSongToPlaylist(song: Song, playlistId: String) {
        val updated = _playlists.value.map { if (it.id == playlistId && song.id !in it.songIds) it.copy(songIds = it.songIds + song.id) else it }
        _playlists.value = updated
        viewModelScope.launch { repository?.savePlaylists(updated) }
    }

    fun loadSongs(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = repository?.fetchAllSongs() ?: emptyList()
            loadStoredMetadata() // Re-sync logic
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager?.release()
    }
}
