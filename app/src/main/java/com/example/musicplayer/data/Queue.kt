package com.example.musicplayer.data

import java.util.UUID

/**
 * Represents a named playback queue that can be saved and resumed later.
 */
data class Queue(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList()
)
