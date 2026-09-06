package com.example.musicplayer.data

import android.net.Uri

/**
 * Represents a music track on the device.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: Uri,
    val duration: Int,
    val album: String,
    val albumArtUri: Uri? = null,
    val dateAdded: Long = 0L,
    var isFavorite: Boolean = false
)
