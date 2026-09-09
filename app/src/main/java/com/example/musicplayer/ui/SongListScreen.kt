package com.example.musicplayer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    viewModel: MusicViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val songs by viewModel.filteredSongs.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val excludeWhatsAppAudio by viewModel.excludeWhatsAppAudio.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Vibe Music Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onNavigateToPlaylists) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists")
                        }
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                            }
                            DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Exclude WhatsApp audio") },
                                    onClick = { viewModel.setExcludeWhatsAppAudio(!excludeWhatsAppAudio) },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = excludeWhatsAppAudio,
                                            onCheckedChange = { viewModel.setExcludeWhatsAppAudio(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
                
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search songs, artists, albums") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
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

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Songs") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Folders") })
                }
            }
        },
        bottomBar = {
            MusicBottomBar(viewModel = viewModel, onNavigateToPlayer = onNavigateToPlayer)
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                // Songs List with Sort Option
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${songs.size} tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sort: ${sortOrder.name.lowercase().replace('_', ' ').capitalize()}")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            MusicViewModel.SortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.name.lowercase().replace('_', ' ').capitalize()) },
                                    onClick = { viewModel.onSortOrderChanged(order); showSortMenu = false },
                                    trailingIcon = { if (sortOrder == order) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (isLoading && songs.isEmpty()) {
                        item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator")) } }
                    } else if (songs.isEmpty()) {
                        item { Box(modifier = Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("No music found.") } }
                    } else {
                        // Recently Played
                        if (recentlyPlayed.isNotEmpty() && searchQuery.isBlank()) {
                            item {
                                Text("Recently Played", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(recentlyPlayed) { song -> RecentSongCard(song = song, onClick = { viewModel.playSong(song) }) }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        item {
                            Button(onClick = { viewModel.shuffleAll() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp))
                                Text("Shuffle Play", modifier = Modifier.padding(start = 8.dp))
                            }
                        }

                        items(songs, key = { it.id }) { song ->
                            SongListItem(song = song, onClick = { viewModel.playSong(song) }, onAddToPlaylist = { showPlaylistDialog = song }, onToggleFavorite = { viewModel.toggleFavorite(song) })
                        }
                    }
                }
            } else {
                // Folders List
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(folders.keys.toList()) { folderName ->
                        val folderSongs = folders[folderName] ?: emptyList()
                        ListItem(
                            modifier = Modifier.clickable { /* We could navigate to a folder detail screen later */ },
                            headlineContent = { Text(folderName) },
                            supportingContent = { Text("${folderSongs.size} songs") },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                IconButton(onClick = { 
                                    // Play all songs in this folder
                                    viewModel.playPlaylist(Playlist(name = folderName, songIds = folderSongs.map { it.id }))
                                    onNavigateToPlayer()
                                }) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = "Play Folder")
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    showPlaylistDialog?.let { song ->
        AddToPlaylistDialog(viewModel = viewModel, song = song, onDismiss = { showPlaylistDialog = null })
    }
}

@Composable
fun RecentSongCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(100.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(100.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                AsyncImage(
                    model = song.albumArtUri, 
                    contentDescription = null, 
                    modifier = Modifier.fillMaxSize(), 
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_default_art),
                    placeholder = painterResource(R.drawable.ic_default_art)
                )
            }
            Text(text = song.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Medium)
            Text(text = song.artist, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
        }
    }
}

@Composable
fun SongListItem(song: Song, onClick: () -> Unit, onAddToPlaylist: () -> Unit, onToggleFavorite: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(song.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Surface(modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                AsyncImage(
                    model = song.albumArtUri, 
                    contentDescription = null, 
                    modifier = Modifier.fillMaxSize(), 
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_default_art),
                    placeholder = painterResource(R.drawable.ic_default_art)
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddToPlaylist) {
                    Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist")
                }
            }
        }
    )
}
