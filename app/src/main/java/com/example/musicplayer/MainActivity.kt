package com.example.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.data.Song
import com.example.musicplayer.ui.AddToPlaylistDialog
import com.example.musicplayer.ui.FullPlayerScreen
import com.example.musicplayer.ui.PlaylistScreen
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import com.example.musicplayer.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                showMusicPlayer()
            } else {
                showPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAudioPermission()
    }

    private fun checkAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showMusicPlayer()
        } else {
            requestPermission.launch(permission)
        }
    }

    private fun showMusicPlayer() {
        setContent {
            MusicPlayerTheme {
                val viewModel: MusicViewModel = viewModel()
                MusicPlayerScreen(viewModel)
            }
        }
    }

    private fun showPermissionDenied() {
        setContent {
            MusicPlayerTheme {
                PermissionDeniedScreen(
                    onTryAgain = { checkAudioPermission() },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(viewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        viewModel.loadSongs(context)
        viewModel.initController(context)
    }

    NavHost(navController = navController, startDestination = "song_list") {
        composable("song_list") {
            SongListScreen(
                viewModel = viewModel,
                onNavigateToPlayer = { navController.navigate("full_player") },
                onNavigateToPlaylists = { navController.navigate("playlists") }
            )
        }
        composable("full_player") {
            FullPlayerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("playlists") {
            PlaylistScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    viewModel: MusicViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val controller by viewModel.controller.collectAsState()
    
    var showPlaylistDialog by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Music") },
                actions = {
                    IconButton(onClick = onNavigateToPlaylists) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists")
                    }
                }
            )
        },
        bottomBar = {
            controller?.let {
                MiniPlayer(
                    viewModel = viewModel,
                    onClick = onNavigateToPlayer
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (songs.isEmpty()) {
                Text("No music found on this device.")
            } else {
                Button(
                    onClick = { viewModel.shuffleAll() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Shuffle Play", modifier = Modifier.padding(start = 8.dp))
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(songs) { song ->
                        SongListItem(
                            song = song,
                            onClick = { viewModel.playSong(song) },
                            onAddToPlaylist = { showPlaylistDialog = song }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    showPlaylistDialog?.let { song ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            song = song,
            onDismiss = { showPlaylistDialog = null }
        )
    }
}

@Composable
fun SongListItem(song: Song, onClick: () -> Unit, onAddToPlaylist: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(song.title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(song.artist) },
        overlineContent = { Text(song.album) },
        trailingContent = {
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Default.Add, contentDescription = "Add to Playlist")
            }
        }
    )
}

@Composable
fun MiniPlayer(
    viewModel: MusicViewModel,
    onClick: () -> Unit
) {
    val state by viewModel.playbackState.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.currentArtist.isNotEmpty()) {
                    Text(
                        text = state.currentArtist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.skipPrevious() }) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous")
                }

                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = { viewModel.skipNext() }) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen(onTryAgain: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Music permission is required to list your songs.")

        Button(
            onClick = onTryAgain,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Try Again")
        }

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Open Settings")
        }
    }
}
