package com.example.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: MusicViewModel,
    folderName: String,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val folderSongs = folders[folderName]
    var searchQuery by remember { mutableStateOf("") }

    if (folderSongs == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val visibleSongs = if (searchQuery.isBlank()) folderSongs else folderSongs.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(folderName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.playPlaylist(Playlist(name = folderName, songIds = folderSongs.map { it.id }))
                            onNavigateToPlayer()
                        }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Play folder")
                        }
                    }
                )

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search in $folderName") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.medium),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
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
            if (folderSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No songs here yet.")
                }
            } else if (visibleSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No songs match \"$searchQuery\".")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleSongs, key = { it.id }) { song ->
                        PlaylistSongListItem(
                            song = song,
                            onClick = {
                                viewModel.playPlaylist(Playlist(name = folderName, songIds = folderSongs.map { it.id }), song)
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
