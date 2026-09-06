package com.example.musicplayer.data

import java.util.UUID

/**
 * Represents a user-created playlist.
 * Equivalent to a Rails Model with a has_many :songs association (represented by songIds).
 */
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList()
)
