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
        val wasPlaying = isPlaying
        val currentPos = currentPosition
        
        if (currentPos > 3000) {
            // Rule 1: > 3 seconds, just restart the song
            seekTo(0)
        } else {
            // Rule 2: < 3 seconds, go to actual previous song
            seekToPreviousMediaItem()
        }

        // Rule 3: Crucial Detail - keep playing if it was playing
        if (wasPlaying) {
            play()
        }
    }

    override fun seekToPreviousMediaItem() {
        val wasPlaying = isPlaying
        super.seekToPreviousMediaItem()
        if (wasPlaying) {
            play()
        }
    }

    override fun seekToNextMediaItem() {
        val wasPlaying = isPlaying
        super.seekToNextMediaItem()
        if (wasPlaying) {
            play()
        }
    }
}
