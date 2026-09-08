package com.example.musicplayer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SongListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: MusicViewModel = mockk(relaxed = true)

    @Test
    fun songListScreen_displaysSongs() {
        val testSongs = listOf(
            Song(1, "Test Song 1", "Artist 1", mockk(relaxed = true), 100, "Album 1")
        )
        
        every { viewModel.filteredSongs } returns MutableStateFlow(testSongs)
        every { viewModel.recentlyPlayed } returns MutableStateFlow(emptyList())
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.searchQuery } returns MutableStateFlow("")
        every { viewModel.sortOrder } returns MutableStateFlow(MusicViewModel.SortOrder.TITLE)
        every { viewModel.folders } returns MutableStateFlow(emptyMap())
        every { viewModel.controller } returns MutableStateFlow(null)
        every { viewModel.playbackState } returns MutableStateFlow(MusicViewModel.PlaybackState())

        composeTestRule.setContent {
            SongListScreen(
                viewModel = viewModel,
                onNavigateToPlayer = {},
                onNavigateToPlaylists = {}
            )
        }

        composeTestRule.onNodeWithText("Test Song 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artist 1 • Album 1").assertIsDisplayed()
    }

    @Test
    fun songListScreen_showsLoadingIndicator() {
        every { viewModel.filteredSongs } returns MutableStateFlow(emptyList())
        every { viewModel.recentlyPlayed } returns MutableStateFlow(emptyList())
        every { viewModel.isLoading } returns MutableStateFlow(true)
        every { viewModel.searchQuery } returns MutableStateFlow("")
        every { viewModel.sortOrder } returns MutableStateFlow(MusicViewModel.SortOrder.TITLE)
        every { viewModel.folders } returns MutableStateFlow(emptyMap())
        every { viewModel.controller } returns MutableStateFlow(null)
        every { viewModel.playbackState } returns MutableStateFlow(MusicViewModel.PlaybackState())

        composeTestRule.setContent {
            SongListScreen(
                viewModel = viewModel,
                onNavigateToPlayer = {},
                onNavigateToPlaylists = {}
            )
        }

        composeTestRule.onNodeWithTag("loading_indicator").assertExists()
    }
}
