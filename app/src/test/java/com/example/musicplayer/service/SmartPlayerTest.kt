package com.example.musicplayer.service

import androidx.media3.common.Player
import io.mockk.*
import org.junit.Before
import org.junit.Test

class SmartPlayerTest {

    private val mockBasePlayer: Player = mockk(relaxed = true)
    private lateinit var smartPlayer: SmartForwardingPlayer

    @Before
    fun setup() {
        smartPlayer = SmartForwardingPlayer(mockBasePlayer)
    }

    @Test
    fun `when position is greater than 3 seconds, seekToPrevious restarts current song`() {
        // GIVEN: Song is at 5 seconds (5000ms) and playing
        every { mockBasePlayer.currentPosition } returns 5000
        every { mockBasePlayer.playWhenReady } returns true

        // WHEN: User triple-clicks (triggers seekToPrevious)
        smartPlayer.seekToPrevious()

        // THEN: It should seek to 0 and KEEP PLAYING
        verify { mockBasePlayer.seekTo(0) }
        verify { mockBasePlayer.play() }
        verify(exactly = 0) { mockBasePlayer.seekToPreviousMediaItem() }
    }

    @Test
    fun `when position is less than 3 seconds, seekToPrevious skips to previous song`() {
        // GIVEN: Song is at 1 second (1000ms) and playing
        every { mockBasePlayer.currentPosition } returns 1000
        every { mockBasePlayer.playWhenReady } returns true

        // WHEN: User triple-clicks
        smartPlayer.seekToPrevious()

        // THEN: It should go to previous track and KEEP PLAYING
        verify { mockBasePlayer.seekToPreviousMediaItem() }
        verify { mockBasePlayer.play() }
        verify(exactly = 0) { mockBasePlayer.seekTo(0) }
    }

    @Test
    fun `when skipping to next, player should continue playing`() {
        // GIVEN: Music is playing
        every { mockBasePlayer.playWhenReady } returns true

        // WHEN: Skipping to next
        smartPlayer.seekToNextMediaItem()

        // THEN: Should play next and keep playing
        verify { mockBasePlayer.play() }
    }
}
