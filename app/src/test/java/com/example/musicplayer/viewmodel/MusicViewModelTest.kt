package com.example.musicplayer.viewmodel

import com.example.musicplayer.data.MusicRepository
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song
import com.example.musicplayer.player.PlaybackManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {

    private val repository: MusicRepository = mockk()
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private lateinit var viewModel: MusicViewModel
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        coEvery { repository.getPlaylists() } returns flowOf(emptyList())
        coEvery { repository.getRecentlyPlayedIds() } returns flowOf(emptyList())
        coEvery { repository.getExcludeWhatsAppAudio() } returns flowOf(true)
        coEvery { repository.fetchAllSongs() } returns emptyList()
        
        viewModel = MusicViewModel(repository, playbackManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSongs updates songs and isLoading state`() = runTest {
        val testSongs = listOf(
            Song(1, "Title 1", "Artist 1", mockk(), 100, "Album 1")
        )
        coEvery { repository.fetchAllSongs() } returns testSongs
        
        viewModel.loadSongs(mockk())
        advanceUntilIdle()
        
        assertEquals(testSongs, viewModel.songs.value)
        assertEquals(false, viewModel.isLoading.value)
    }

    @Test
    fun `onSearchQueryChanged updates filtered songs`() = runTest {
        val testSongs = listOf(
            Song(1, "Alpha", "Artist 1", mockk(), 100, "Album 1"),
            Song(2, "Beta", "Artist 2", mockk(), 100, "Album 2")
        )
        coEvery { repository.fetchAllSongs() } returns testSongs
        
        val collectJob = backgroundScope.launch {
            viewModel.filteredSongs.collect {}
        }
        
        viewModel.loadSongs(mockk())
        advanceUntilIdle()
        
        viewModel.onSearchQueryChanged("Alpha")
        advanceUntilIdle()
        
        val filtered = viewModel.filteredSongs.value
        assertEquals(1, filtered.size)
        assertEquals("Alpha", filtered[0].title)
        
        collectJob.cancel()
    }

    @Test
    fun `toggleFavorite calls repository to save`() = runTest {
        val song = Song(1, "Title", "Artist", mockk(), 100, "Album", isFavorite = false)
        
        coEvery { repository.fetchAllSongs() } returns listOf(song)
        coEvery { repository.saveFavorites(any()) } just Runs
        
        viewModel.loadSongs(mockk())
        advanceUntilIdle()
        
        viewModel.toggleFavorite(viewModel.songs.value[0])
        advanceUntilIdle()
        
        coVerify { repository.saveFavorites(setOf(1L)) }
        assertEquals(true, viewModel.songs.value[0].isFavorite)
    }

    @Test
    fun `createPlaylist adds to list and saves`() = runTest {
        coEvery { repository.savePlaylists(any()) } just Runs
        
        viewModel.createPlaylist("New Party Mix")
        advanceUntilIdle()
        
        assertEquals(1, viewModel.playlists.value.size)
        assertEquals("New Party Mix", viewModel.playlists.value[0].name)
        coVerify { repository.savePlaylists(any()) }
    }

    @Test
    fun `setSleepTimer updates state and then pauses playback after time`() = runTest {
        // Use a smaller time for testing if possible or mock delay
        viewModel.setSleepTimer(1) // 1 minute
        
        assertEquals(1, viewModel.playbackState.value.sleepTimerRemaining)
        
        // Advance time by 61 seconds to trigger the loop
        advanceTimeBy(61000)
        
        coVerify { playbackManager.pause() }
        assertNull(viewModel.playbackState.value.sleepTimerRemaining)
    }
}
