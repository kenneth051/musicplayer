package com.example.musicplayer.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.musicplayer.R
import com.example.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.playbackState.collectAsState()
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    
    val defaultPrimary = MaterialTheme.colorScheme.primaryContainer
    var dominantColor by remember { mutableStateOf(defaultPrimary) }
    
    LaunchedEffect(state.currentSong?.albumArtUri) {
        val uri = state.currentSong?.albumArtUri
        if (uri == null) {
            dominantColor = defaultPrimary
        } else {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    // Runs synchronously (off the main thread) inside this coroutine instead of
                    // Palette's async callback, so it's cancelled along with the effect when the
                    // song changes again and can't overwrite dominantColor with a stale result.
                    val palette = withContext(Dispatchers.Default) { Palette.from(b).generate() }
                    palette.dominantSwatch?.let { swatch -> dominantColor = Color(swatch.rgb) }
                }
            }
        }
    }

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
                        DropdownMenuItem(
                            text = { Text("Sleep Timer ${state.sleepTimerRemaining?.let { "($it min)" } ?: ""}") },
                            onClick = { showOptionsMenu = false; showSleepTimerMenu = true },
                            leadingIcon = { Icon(Icons.Default.Timer, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as Ringtone") },
                            onClick = {
                                showOptionsMenu = false
                                state.currentSong?.let { viewModel.setAsRingtone(context, it) }
                            },
                            leadingIcon = { Icon(Icons.Default.Notifications, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                AdBanner()
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        val surfaceColor = MaterialTheme.colorScheme.surface
        // Pick readable text color against the actual tinted background behind it, rather than
        // assuming it's always dark (light/pastel album art would make white text unreadable).
        val displayedTint = dominantColor.copy(alpha = 0.4f).compositeOver(surfaceColor)
        val onDominantColor = if (displayedTint.luminance() > 0.5f) Color.Black else Color.White

        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(dominantColor.copy(alpha = 0.4f), surfaceColor))
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
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(R.drawable.ic_default_art),
                        placeholder = painterResource(R.drawable.ic_default_art)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.currentTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = onDominantColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentArtist,
                    style = MaterialTheme.typography.titleLarge,
                    color = onDominantColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Column {
                Slider(
                    value = state.currentPosition.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..state.currentDuration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = dominantColor, activeTrackColor = dominantColor)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.currentPosition), style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(state.currentDuration), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val transportColor = onDominantColor
                val shuffleTooltipState = rememberTooltipState()
                val shuffleText = if (state.shuffleModeEnabled) "Shuffle On" else "Shuffle Off"
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(shuffleText) } }, state = shuffleTooltipState) {
                    IconButton(onClick = { viewModel.toggleShuffle(); scope.launch { shuffleTooltipState.show() } }) {
                        Icon(Icons.Default.Shuffle, shuffleText, tint = if (state.shuffleModeEnabled) transportColor else transportColor.copy(alpha = 0.55f))
                    }
                }

                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(40.dp), tint = transportColor)
                }

                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    containerColor = dominantColor,
                    contentColor = onDominantColor
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = onDominantColor
                    )
                }

                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(40.dp), tint = transportColor)
                }

                val repeatTooltipState = rememberTooltipState()
                val repeatText = when (state.repeatMode) { Player.REPEAT_MODE_OFF -> "Repeat Off"; Player.REPEAT_MODE_ONE -> "Repeat One"; else -> "Repeat All" }
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(repeatText) } }, state = repeatTooltipState) {
                    IconButton(onClick = { viewModel.toggleRepeat(); scope.launch { repeatTooltipState.show() } }) {
                        val icon = when (state.repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne; Player.REPEAT_MODE_ALL -> Icons.Default.Repeat; else -> Icons.Default.Repeat }
                        Icon(icon, repeatText, tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) transportColor else transportColor.copy(alpha = 0.55f))
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

    if (showSleepTimerMenu) {
        AlertDialog(
            onDismissRequest = { showSleepTimerMenu = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    listOf(null, 5, 15, 30, 60).forEach { mins ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setSleepTimer(mins); showSleepTimerMenu = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.sleepTimerRemaining == mins, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(mins?.let { "$it Minutes" } ?: "Off")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimerMenu = false }) { Text("Cancel") } }
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
