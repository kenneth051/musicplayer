package com.example.musicplayer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.viewmodel.MusicViewModel

@Composable
fun MusicBottomBar(
    viewModel: MusicViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val controller by viewModel.controller.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (controller != null && playbackState.currentTitle != "Not Playing") {
                MiniPlayer(viewModel = viewModel, onClick = onNavigateToPlayer)
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                AdBanner()
            }
        }
    }
}

@Composable
fun MiniPlayer(
    viewModel: MusicViewModel,
    onClick: () -> Unit
) {
    val state by viewModel.playbackState.collectAsState()
    val progress = if (state.currentDuration > 0) {
        state.currentPosition.toFloat() / state.currentDuration.toFloat()
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Highly visible progress bar (thick and bright)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp), // Thicker bar
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
}
