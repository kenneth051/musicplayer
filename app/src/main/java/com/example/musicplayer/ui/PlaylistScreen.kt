package com.example.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToQueueDetail: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val queues by viewModel.queues.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val favoriteSongs = songs.filter { it.isFavorite }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Playlists") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            // 1. Static Favorites Playlist
            item {
                ListItem(
                    modifier = Modifier.clickable { onNavigateToPlaylistDetail("favorites") },
                    headlineContent = { Text("Favorites") },
                    supportingContent = { Text("${favoriteSongs.size} songs") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                HorizontalDivider()
            }

            // 2. Custom Playlists
            item {
                Text("Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            }
            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists created yet.")
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    ListItem(
                        modifier = Modifier.clickable { onNavigateToPlaylistDetail(playlist.id) },
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.songIds.size} songs") },
                        leadingContent = { Icon(Icons.Default.LibraryMusic, contentDescription = null) }
                    )
                    HorizontalDivider()
                }
            }

            // 3. Saved Queues
            item {
                Text("Queues", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
            }
            if (queues.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No queues created yet.")
                    }
                }
            } else {
                items(queues, key = { it.id }) { queue ->
                    ListItem(
                        modifier = Modifier.clickable { onNavigateToQueueDetail(queue.id) },
                        headlineContent = { Text(queue.name) },
                        supportingContent = { Text("${queue.songIds.size} songs") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
