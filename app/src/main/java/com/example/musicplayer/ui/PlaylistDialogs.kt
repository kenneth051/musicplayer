package com.example.musicplayer.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.musicplayer.data.Queue
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel

@Composable
fun AddToPlaylistDialog(
    viewModel: MusicViewModel,
    song: Song,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text("No playlists yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(playlists) { playlist ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    viewModel.addSongToPlaylist(song, playlist.id)
                                    onDismiss()
                                },
                                headlineContent = { Text(playlist.name) },
                                supportingContent = { Text("${playlist.songIds.size} songs") }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Playlist")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddToQueueDialog(
    viewModel: MusicViewModel,
    song: Song,
    onDismiss: () -> Unit
) {
    val queues by viewModel.queues.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Queue") },
        text = {
            Column {
                if (queues.isEmpty()) {
                    Text("No queues yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(queues) { queue ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    viewModel.addSongToQueue(song, queue.id)
                                    Toast.makeText(context, "${song.title} added to ${queue.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                headlineContent = { Text(queue.name) },
                                supportingContent = { Text("${queue.songIds.size} songs") }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Queue")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCreateDialog) {
        CreateQueueDialog(
            onCreate = { name ->
                viewModel.createQueue(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun CreateQueueDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Queue") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Queue Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
