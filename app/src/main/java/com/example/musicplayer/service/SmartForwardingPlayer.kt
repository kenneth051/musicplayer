package com.example.musicplayer.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Custom player wrapper that implements the "3-Second Rule" for headphone controls.
 */
@OptIn(UnstableApi::class)
class SmartForwardingPlayer(basePlayer: Player) : ForwardingPlayer(basePlayer) {

    override fun seekToPrevious() {
        processPreviousAction()
    }

    override fun seekToPreviousMediaItem() {
        processPreviousAction()
    }

    fun processPreviousAction() {
        // Use playWhenReady (not isPlaying) so buffering right at the moment of the click
        // doesn't get misread as "wasn't playing" and skip the resume-play step.
        val shouldBePlaying = playWhenReady
        val currentPos = currentPosition

        if (currentPos > 3000) {
            // Rule 1: > 3 seconds, just restart the song
            seekTo(0)
        } else {
            // Rule 2: < 3 seconds, go to actual previous song
            super.seekToPreviousMediaItem()
        }

        // Rule 3: Crucial Detail - force play state if it was playing
        if (shouldBePlaying) {
            play()
        }
    }

    override fun seekToNext() = keepingPlayState { super.seekToNext() }

    override fun seekToNextMediaItem() = keepingPlayState { super.seekToNextMediaItem() }

    private inline fun keepingPlayState(action: () -> Unit) {
        val wasPlaying = playWhenReady
        action()
        if (wasPlaying) play()
    }
}
