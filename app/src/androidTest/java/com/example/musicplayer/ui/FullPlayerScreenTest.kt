package com.example.musicplayer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class FullPlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: MusicViewModel = mockk(relaxed = true)

    @Test
    fun fullPlayerScreen_displaysCurrentSongInfo() {
        val currentSong = Song(1, "Skyfall", "Adele", mockk(relaxed = true), 300, "Skyfall")
        val playbackState = MusicViewModel.PlaybackState(
            isPlaying = true,
            currentTitle = "Skyfall",
            currentArtist = "Adele",
            currentSong = currentSong
        )
        
        every { viewModel.playbackState } returns MutableStateFlow(playbackState)
        every { viewModel.controller } returns MutableStateFlow(null)

        composeTestRule.setContent {
            FullPlayerScreen(
                viewModel = viewModel,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Skyfall").assertIsDisplayed()
        composeTestRule.onNodeWithText("Adele").assertIsDisplayed()
    }

    @Test
    fun fullPlayerScreen_controlsAreClickable() {
        every { viewModel.playbackState } returns MutableStateFlow(MusicViewModel.PlaybackState())
        every { viewModel.controller } returns MutableStateFlow(null)

        composeTestRule.setContent {
            FullPlayerScreen(
                viewModel = viewModel,
                onBack = {}
            )
        }

        // Check if play/pause, next, and previous buttons exist
        composeTestRule.onNodeWithContentDescription("Play").assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("Next").assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("Previous").assertHasClickAction()
    }
}
