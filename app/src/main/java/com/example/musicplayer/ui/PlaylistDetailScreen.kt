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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: MusicViewModel,
    playlistId: String,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    
    val (title, playlistSongs, playlistObj) = if (playlistId == "favorites") {
        val favs = songs.filter { it.isFavorite }
        Triple("Favorites", favs, Playlist(id = "favorites", name = "Favorites", songIds = favs.map { it.id }))
    } else {
        val p = playlists.find { it.id == playlistId }
        val s = if (p != null) songs.filter { it.id in p.songIds } else emptyList()
        Triple(p?.name ?: "Playlist", s, p)
    }

    if (playlistObj == null && playlistId != "favorites") {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
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
            if (playlistSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No songs here yet.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(playlistSongs) { song ->
                        PlaylistSongListItem(
                            song = song,
                            onClick = { 
                                viewModel.playPlaylist(playlistObj!!, song)
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

@Composable
fun PlaylistSongListItem(song: Song, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(song.title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(song.artist) },
        overlineContent = { Text(song.album) }
    )
}
