package com.example.musicplayer.data

import android.net.Uri

/**
 * Represents a music track on the device.
 * Equivalent to a Rails Model.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: Uri,
    val duration: Int,
    val album: String
)
