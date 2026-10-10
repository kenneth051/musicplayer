package com.example.musicplayer.ui

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
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
    val lyricsMap by viewModel.lyricsMap.collectAsState()
    val isFetchingLyrics by viewModel.isFetchingLyrics.collectAsState()
    val syncLyricsEnabled by viewModel.syncLyricsEnabled.collectAsState()
    
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasNavigatedBack by remember { mutableStateOf(false) }
    val safeOnBack = {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            onBack()
        }
    }

    val deleteSong = rememberDeleteSongHandler(viewModel, onDeleted = {
        if (viewModel.playbackState.value.currentSong == null) {
            safeOnBack()
        }
    })

    LaunchedEffect(state.currentSong) {
        if (state.currentSong == null && state.currentTitle == "Not Playing") {
            safeOnBack()
        }
    }

    val currentLyrics = state.currentSong?.let { lyricsMap[it.id] } ?: ""
    
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

    val surfaceColor = MaterialTheme.colorScheme.surface
    val bottomBgColor = dominantColor.copy(alpha = 0.4f).compositeOver(surfaceColor)
    val isLightBackground = surfaceColor.luminance() > 0.5f || dominantColor.luminance() > 0.5f || bottomBgColor.luminance() > 0.5f
    val controlColor = if (isLightBackground) Color.Black else Color.White

    val fabContainer = if (dominantColor.luminance() > 0.5f) Color.Black else dominantColor
    val fabIconColor = if (fabContainer.luminance() > 0.5f) Color.Black else Color.White

    val topGradientColor = if (isLightBackground) dominantColor.copy(alpha = 0.7f) else dominantColor.copy(alpha = 0.4f)
    val bottomGradientColor = if (isLightBackground) Color.White.copy(alpha = 0.85f).compositeOver(surfaceColor) else surfaceColor

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium, color = controlColor) },
                navigationIcon = {
                    IconButton(onClick = safeOnBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = controlColor)
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = controlColor)
                    }
                    DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("View / Edit Lyrics") },
                            onClick = { showOptionsMenu = false; showEditLyricsDialog = true },
                            leadingIcon = { Icon(Icons.Default.Lyrics, null) }
                        )
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
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showOptionsMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
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
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(topGradientColor, bottomGradientColor))
        ))

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (showLyricsView) {
                val fetchOnlineLambda: () -> Unit = {
                    state.currentSong?.let { song ->
                        viewModel.fetchLyricsOnline(song) { success ->
                            if (success) {
                                Toast.makeText(context, "Lyrics loaded!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No lyrics found. Please verify the song title and artist name.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .aspectRatio(1f)
                ) {
                    LyricsView(
                        lyricsText = currentLyrics,
                        currentPositionMs = state.currentPosition,
                        contentColor = controlColor,
                        songTitle = state.currentSong?.title ?: "",
                        songArtist = state.currentSong?.artist ?: "",
                        isFetching = isFetchingLyrics,
                        isSyncEnabled = syncLyricsEnabled,
                        onToggleSync = { viewModel.setSyncLyricsEnabled(it) },
                        onSeekTo = { viewModel.seekTo(it) },
                        onEditLyrics = { showEditLyricsDialog = true },
                        onSearchOnline = fetchOnlineLambda
                    )
                }
            } else {
                Card(
                    modifier = Modifier.size(320.dp).aspectRatio(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Crossfade(targetState = state.currentSong, label = "AlbumArt") { song ->
                        AsyncImage(
                            model = song?.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = painterResource(R.drawable.ic_default_art)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.currentTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = controlColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.currentArtist,
                    style = MaterialTheme.typography.titleLarge,
                    color = controlColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !showLyricsView,
                        onClick = { showLyricsView = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Cover Art", style = MaterialTheme.typography.labelMedium)
                    }
                    SegmentedButton(
                        selected = showLyricsView,
                        onClick = { showLyricsView = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Lyrics", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Column {
                val sliderColor = controlColor
                Slider(
                    value = state.currentPosition.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..state.currentDuration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = sliderColor,
                        activeTrackColor = sliderColor,
                        inactiveTrackColor = sliderColor.copy(alpha = 0.24f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.currentPosition), style = MaterialTheme.typography.bodySmall, color = controlColor.copy(alpha = 0.7f))
                    Text(formatTime(state.currentDuration), style = MaterialTheme.typography.bodySmall, color = controlColor.copy(alpha = 0.7f))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val transportColor = controlColor
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
                    containerColor = fabContainer,
                    contentColor = fabIconColor
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = fabIconColor
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

    if (showDeleteDialog) {
        state.currentSong?.let { song ->
            DeleteSongConfirmDialog(
                song = song,
                onConfirm = { deleteSong(song) },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }

    if (showEditLyricsDialog) {
        state.currentSong?.let { song ->
            EditLyricsDialog(
                songTitle = song.title,
                songArtist = song.artist,
                initialLyrics = currentLyrics,
                isFetching = isFetchingLyrics,
                onSave = { newLyrics ->
                    viewModel.saveLyrics(song.id, newLyrics)
                },
                onDismiss = { showEditLyricsDialog = false },
                onSearchOnline = {
                    viewModel.fetchLyricsOnline(song) { success ->
                        if (success) {
                            Toast.makeText(context, "Lyrics loaded!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No lyrics found online for this track", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
