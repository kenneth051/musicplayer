package com.example.musicplayer.data

import java.util.UUID

/**
 * Represents a specific instance of a song in the playback queue.
 * This allows the same song to be in the queue multiple times with a unique stable ID.
 */
data class QueueItem(
    val queueId: String = UUID.randomUUID().toString(),
    val song: Song
)
