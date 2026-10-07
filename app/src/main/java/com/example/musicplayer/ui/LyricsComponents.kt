package com.example.musicplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.LrcParser

@Composable
fun LyricsView(
    lyricsText: String,
    currentPositionMs: Long,
    contentColor: Color,
    isFetching: Boolean = false,
    isSyncEnabled: Boolean = true,
    onToggleSync: ((Boolean) -> Unit)? = null,
    onSeekTo: (Long) -> Unit,
    onEditLyrics: () -> Unit,
    onSearchOnline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isFetching) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = contentColor)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Fetching lyrics online...",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    } else if (lyricsText.isBlank()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No lyrics added yet.",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEditLyrics) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Lyrics")
                }
                onSearchOnline?.let { onSearch ->
                    OutlinedButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Online")
                    }
                }
            }
        }
    } else {
        val isLrc = remember(lyricsText) { LrcParser.isLrcFormat(lyricsText) }

        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLrc && isSyncEnabled) {
                    val parsedLines = remember(lyricsText) { LrcParser.parse(lyricsText) }
                    val activeIndex = remember(parsedLines, currentPositionMs) {
                        LrcParser.getActiveIndex(parsedLines, currentPositionMs)
                    }
                    val listState = rememberLazyListState()

                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0) {
                            listState.animateScrollToItem(
                                index = activeIndex,
                                scrollOffset = -120
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(parsedLines) { index, line ->
                            val isActive = index == activeIndex

                            val scale by animateFloatAsState(
                                targetValue = if (isActive) 1.1f else 1.0f,
                                animationSpec = tween(durationMillis = 250),
                                label = "scale"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isActive) contentColor else contentColor.copy(alpha = 0.45f),
                                animationSpec = tween(durationMillis = 250),
                                label = "color"
                            )

                            Text(
                                text = line.text,
                                fontSize = if (isActive) 20.sp else 16.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 20.dp)
                                    .scale(scale)
                                    .clickable { onSeekTo(line.timestampMs) }
                            )
                        }
                    }
                } else {
                    // Plain text or sync disabled: strip timestamp tags if LRC, and show as scrollable text
                    val displayLyrics = remember(lyricsText, isLrc) {
                        if (isLrc) {
                            LrcParser.parse(lyricsText).joinToString("\n") { it.text }
                        } else {
                            lyricsText
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = displayLyrics,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                    }
                }
            }

            // Action Toolbar below the lyrics container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLrc && onToggleSync != null) {
                    FilterChip(
                        selected = isSyncEnabled,
                        onClick = { onToggleSync(!isSyncEnabled) },
                        label = { Text(if (isSyncEnabled) "Sync On" else "Sync Off", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSyncEnabled) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                contentDescription = "Toggle Lyrics Sync",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = contentColor.copy(alpha = 0.2f),
                            selectedLabelColor = contentColor,
                            selectedLeadingIconColor = contentColor,
                            containerColor = Color.Transparent,
                            labelColor = contentColor.copy(alpha = 0.6f),
                            iconColor = contentColor.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onEditLyrics) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Lyrics", tint = contentColor)
                }
            }
        }
    }
}

@Composable
fun EditLyricsDialog(
    songTitle: String,
    songArtist: String = "",
    initialLyrics: String,
    isFetching: Boolean = false,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onSearchOnline: (() -> Unit)? = null
) {
    var lyricsInput by remember { mutableStateOf(initialLyrics) }

    // Keep input synced if lyrics arrive while dialog is open
    LaunchedEffect(initialLyrics) {
        if (initialLyrics.isNotBlank()) {
            lyricsInput = initialLyrics
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Lyrics for \"$songTitle\"") },
        text = {
            Column {
                if (onSearchOnline != null) {
                    Button(
                        onClick = onSearchOnline,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFetching
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching Lyrics...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto-Fetch Lyrics Online")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = "Tip: You can paste plain text or LRC format timestamps like [01:23.45] Lyrics line for auto-sync playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = lyricsInput,
                    onValueChange = { lyricsInput = it },
                    placeholder = { Text("Paste or type lyrics here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(lyricsInput)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
