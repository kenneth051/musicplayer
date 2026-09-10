package com.example.musicplayer.viewmodel

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
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

/**
 * Refactored to allow dependency injection for testing.
 */
class MusicViewModel(
    private var repository: MusicRepository? = null,
    private var playbackManager: PlaybackManager? = null
) : ViewModel() {

    enum class SortOrder { DATE_ADDED, TITLE, ARTIST, MOST_PLAYED }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())

    private val _excludeWhatsAppAudio = MutableStateFlow(true)
    val excludeWhatsAppAudio: StateFlow<Boolean> = _excludeWhatsAppAudio

    // Songs the user actually wants to see/play, after applying the WhatsApp audio exclusion -
    // everything downstream (search/sort/folders/shuffle) builds on this rather than the raw
    // scan result, so anything excluded from the library isn't just hidden from one list while
    // still turning up in another. Matches both "WhatsApp Voice Notes" and "WhatsApp Audio"
    // (shared audio files), and the "WhatsApp Business" variant of either.
    private val effectiveSongs: StateFlow<List<Song>> = combine(_songs, _excludeWhatsAppAudio) { songs, exclude ->
        if (exclude) songs.filterNot { it.parentFolder.contains("WhatsApp", ignoreCase = true) } else songs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSongs: StateFlow<List<Song>> = combine(effectiveSongs, _searchQuery, _sortOrder) { songs, query, sort ->
        val filtered = if (query.isBlank()) songs
        else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }

        when (sort) {
            SortOrder.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.TITLE -> filtered.sortedBy { it.title }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist }
            SortOrder.MOST_PLAYED -> filtered.sortedByDescending { it.playCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<Map<String, List<Song>>> = effectiveSongs.map { songs ->
        songs.groupBy { it.parentFolder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recentlyPlayed: StateFlow<List<Song>> = combine(_recentlyPlayed, effectiveSongs) { recent, songs ->
        val effectiveIds = songs.map { it.id }.toSet()
        recent.filter { it.id in effectiveIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _controller = MutableStateFlow<Player?>(null)
    val controller: StateFlow<Player?> = _controller.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var refreshJob: Job? = null

    private var appContext: Context? = null
    private var mediaStoreObserver: ContentObserver? = null

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentTitle: String = "Not Playing",
        val currentArtist: String = "",
        val currentDuration: Long = 0L,
        val currentPosition: Long = 0L,
        val shuffleModeEnabled: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val playbackSpeed: Float = 1.0f,
        val currentSong: Song? = null,
        val sleepTimerRemaining: Int? = null
    )

    fun init(context: Context) {
        if (repository == null) {
            repository = MusicRepository(context)
        }
        if (playbackManager == null) {
            playbackManager = PlaybackManager(context)
            playbackManager?.init { player ->
                _controller.value = player
                setupControllerListener(player)
            }
        }
        if (mediaStoreObserver == null) {
            appContext = context.applicationContext
            registerMediaStoreObserver()
        }
        loadStoredMetadata()
    }

    // Catches library changes made outside the app - a rename, an ID3 tag edit, a file added
    // or deleted via another app - so the song list stays in sync without the user having to
    // force-restart the app. MediaStore can fire several notifications in quick succession for
    // a single change, so the actual reload is debounced.
    private fun registerMediaStoreObserver() {
        val context = appContext ?: return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshJob?.cancel()
                refreshJob = viewModelScope.launch {
                    delay(800)
                    loadSongs(context)
                }
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        mediaStoreObserver = observer
    }

    private fun loadStoredMetadata() {
        viewModelScope.launch {
            repository?.let { repo ->
                _playlists.value = repo.getPlaylists().first()
                val recentIds = repo.getRecentlyPlayedIds().first()
                _recentlyPlayed.value = _songs.value.filter { it.id in recentIds }
                _excludeWhatsAppAudio.value = repo.getExcludeWhatsAppAudio().first()
            }
        }
    }

    fun setExcludeWhatsAppAudio(exclude: Boolean) {
        _excludeWhatsAppAudio.value = exclude
        viewModelScope.launch { repository?.saveExcludeWhatsAppAudio(exclude) }
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

        // Compare by id, not full equality: incrementPlayCount()/toggleFavorite() replace the
        // Song in _songs with a copy that differs in those fields, which would otherwise make
        // this look like "a new song started" on every progress tick and re-fire below.
        if (currentSong != null && currentSong.id != _playbackState.value.currentSong?.id) {
            addToRecentlyPlayed(currentSong)
            incrementPlayCount(currentSong)
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
        val updated = (_recentlyPlayed.value.toMutableList().apply { removeAll { it.id == song.id }; add(0, song) }).take(20)
        _recentlyPlayed.value = updated
        viewModelScope.launch { repository?.saveRecentlyPlayedIds(updated.map { it.id }) }
    }

    private fun incrementPlayCount(song: Song) {
        _songs.value = _songs.value.map { if (it.id == song.id) it.copy(playCount = it.playCount + 1) else it }
        viewModelScope.launch {
            repository?.let { repo ->
                val counts = repo.getPlayCounts().first().toMutableMap()
                counts[song.id] = (counts[song.id] ?: 0) + 1
                repo.savePlayCounts(counts)
            }
        }
    }

    private fun startProgressTracking(player: Player) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch { while (true) { updatePlaybackState(player); delay(1000) } }
    }

    private fun stopProgressTracking() { progressJob?.cancel() }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onSortOrderChanged(order: SortOrder) { _sortOrder.value = order }

    fun toggleFavorite(song: Song) {
        val updated = _songs.value.map { if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it }
        _songs.value = updated
        viewModelScope.launch { repository?.saveFavorites(updated.filter { it.isFavorite }.map { it.id }.toSet()) }
    }

    fun playSong(song: Song) = effectiveSongs.value.let { songs -> playbackManager?.play(songs, songs.indexOf(song), false) }
    fun shuffleAll() = playbackManager?.play(effectiveSongs.value, 0, true)
    
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

    fun setAsRingtone(context: Context, song: Song) {
        if (Settings.System.canWrite(context)) {
            try {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.contentUri)
            } catch (e: Exception) { e.printStackTrace() }
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemaining = null)
            return
        }

        _playbackState.value = _playbackState.value.copy(sleepTimerRemaining = minutes)
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60000)
                remaining--
                _playbackState.value = _playbackState.value.copy(sleepTimerRemaining = remaining)
            }
            playbackManager?.pause()
            _playbackState.value = _playbackState.value.copy(sleepTimerRemaining = null)
        }
    }

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

    fun playExternalUri(uri: Uri) { playbackManager?.playUri(uri) }

    fun loadSongs(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = repository?.fetchAllSongs() ?: emptyList()
            loadStoredMetadata()
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager?.release()
        sleepTimerJob?.cancel()
        refreshJob?.cancel()
        mediaStoreObserver?.let { appContext?.contentResolver?.unregisterContentObserver(it) }
    }
}
