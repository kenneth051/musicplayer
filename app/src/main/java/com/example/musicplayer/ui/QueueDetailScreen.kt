package com.example.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDetailScreen(
    viewModel: MusicViewModel,
    queueId: String,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val queues by viewModel.queues.collectAsState()
    val songs by viewModel.songs.collectAsState()
    
    val queue = queues.find { it.id == queueId }
    val queueSongs = if (queue != null) songs.filter { it.id in queue.songIds } else emptyList()

    if (queue == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(queue.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            MusicBottomBar(viewModel = viewModel, onNavigateToPlayer = onNavigateToPlayer)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (queueSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No songs in this queue.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(queueSongs) { song ->
                        PlaylistSongListItem(
                            song = song,
                            onClick = { 
                                viewModel.playQueue(queue, song)
                                onNavigateToPlayer()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
