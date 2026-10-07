package com.example.musicplayer.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Custom player wrapper that implements headphone control shortcuts and enforces
 * priority for manually queued songs ("Play Next") even when Shuffle is ON.
 */
@OptIn(UnstableApi::class)
class SmartForwardingPlayer(basePlayer: Player) : ForwardingPlayer(basePlayer) {

    private fun isManualItem(index: Int): Boolean {
        if (index !in 0 until mediaItemCount) return false
        val item = getMediaItemAt(index)
        val tag = item.mediaId.split("|").getOrNull(2)
            ?: item.mediaMetadata.extras?.getString("tag")
        return tag == "manual"
    }

    override fun seekToNext() = keepingPlayState {
        val nextIndex = currentMediaItemIndex + 1
        if (shuffleModeEnabled && isManualItem(nextIndex)) {
            seekToDefaultPosition(nextIndex)
        } else {
            super.seekToNext()
        }
    }

    override fun seekToNextMediaItem() = keepingPlayState {
        val nextIndex = currentMediaItemIndex + 1
        if (shuffleModeEnabled && isManualItem(nextIndex)) {
            seekToDefaultPosition(nextIndex)
        } else {
            super.seekToNextMediaItem()
        }
    }

    override fun seekToPrevious() {
        processPreviousAction()
    }

    override fun seekToPreviousMediaItem() {
        val wasPlaying = playWhenReady
        super.seekToPreviousMediaItem()
        if (wasPlaying) play()
    }

    fun processPreviousAction() {
        val shouldBePlaying = playWhenReady
        val currentPos = currentPosition

        if (currentPos > 3000) {
            seekTo(0)
        } else {
            super.seekToPreviousMediaItem()
        }

        if (shouldBePlaying) {
            play()
        }
    }

    private inline fun keepingPlayState(action: () -> Unit) {
        val wasPlaying = playWhenReady
        action()
        if (wasPlaying) play()
    }
}
