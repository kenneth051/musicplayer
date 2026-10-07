package com.example.musicplayer.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel

/**
 * Wires up MediaStore's delete-consent flow behind a single callback: the system shows its own
 * confirmation dialog on API 30+ (MediaStore.createDeleteRequest) or a one-time write-grant
 * dialog on API 29 (RecoverableSecurityException), and on API 28 and below it deletes directly
 * if permitted. Register once per screen and call the returned lambda to delete a song.
 */
@Composable
fun rememberDeleteSongHandler(viewModel: MusicViewModel, onDeleted: () -> Unit = {}): (Song) -> Unit {
    val context = LocalContext.current
    var pendingSong by remember { mutableStateOf<Song?>(null) }
    var pendingSongId by rememberSaveable { mutableStateOf<Long?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val targetSong = pendingSong ?: pendingSongId?.let { id -> viewModel.songs.value.find { it.id == id } }
        pendingSong = null
        pendingSongId = null
        if (result.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // On API 30+ (Android 11+), MediaStore's system dialog already performed the deletion.
                // Re-attempting contentResolver.delete will fail or throw errors. Just refresh state.
                viewModel.onSongDeleted(context)
                onDeleted()
            } else if (targetSong != null) {
                // On API 29 (Android 10), one-time write consent was granted, so retrying deletes the file.
                viewModel.deleteSong(context, targetSong, onIntentSenderRequired = {}, onDeleted = onDeleted)
            } else {
                viewModel.onSongDeleted(context)
                onDeleted()
            }
        } else {
            viewModel.loadSongs(context)
        }
    }

    return { song ->
        pendingSong = song
        pendingSongId = song.id
        viewModel.deleteSong(
            context, song,
            onIntentSenderRequired = { intentSender ->
                try {
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onDeleted = onDeleted
        )
    }
}

@Composable
fun DeleteSongConfirmDialog(song: Song, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete song?") },
        text = { Text("\"${song.title}\" will be permanently deleted from your device. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
