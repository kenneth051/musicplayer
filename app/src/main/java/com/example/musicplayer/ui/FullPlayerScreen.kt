package com.example.musicplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.playbackState.collectAsState()
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            onClick = { showOptionsMenu = false; showPlaylistDialog = true },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Playback Speed (${state.playbackSpeed}x)") },
                            onClick = { showOptionsMenu = false; showSpeedMenu = true },
                            leadingIcon = { Icon(Icons.Default.Speed, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), MaterialTheme.colorScheme.surface))
        ))

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                modifier = Modifier.size(320.dp).aspectRatio(1f),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Crossfade(targetState = state.currentSong?.albumArtUri, label = "AlbumArt") { uri ->
                    if (uri != null) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.currentTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentArtist,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Column {
                Slider(
                    value = state.currentPosition.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..state.currentDuration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.currentPosition), style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(state.currentDuration), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val shuffleTooltipState = rememberTooltipState()
                val shuffleText = if (state.shuffleModeEnabled) "Shuffle On" else "Shuffle Off"
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(shuffleText) } }, state = shuffleTooltipState) {
                    IconButton(onClick = { viewModel.toggleShuffle(); scope.launch { shuffleTooltipState.show() } }) {
                        Icon(Icons.Default.Shuffle, shuffleText, tint = if (state.shuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(40.dp))
                }

                LargeFloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(48.dp))
                }

                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(40.dp))
                }

                val repeatTooltipState = rememberTooltipState()
                val repeatText = when (state.repeatMode) {
                    Player.REPEAT_MODE_OFF -> "Repeat Off"
                    Player.REPEAT_MODE_ONE -> "Repeat One"
                    Player.REPEAT_MODE_ALL -> "Repeat All"
                    else -> "Repeat Off"
                }
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(repeatText) } }, state = repeatTooltipState) {
                    IconButton(onClick = { viewModel.toggleRepeat(); scope.launch { repeatTooltipState.show() } }) {
                        val icon = when (state.repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne; Player.REPEAT_MODE_ALL -> Icons.Default.Repeat; else -> Icons.Default.Repeat }
                        Icon(icon, repeatText, tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showSpeedMenu) {
        AlertDialog(
            onDismissRequest = { showSpeedMenu = false },
            title = { Text("Playback Speed") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setPlaybackSpeed(speed); showSpeedMenu = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.playbackSpeed == speed, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text("${speed}x")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeedMenu = false }) { Text("Close") } }
        )
    }

    if (showPlaylistDialog) {
        state.currentSong?.let { song ->
            AddToPlaylistDialog(viewModel = viewModel, song = song, onDismiss = { showPlaylistDialog = false })
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
