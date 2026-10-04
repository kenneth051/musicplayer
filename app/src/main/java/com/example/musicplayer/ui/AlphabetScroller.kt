package com.example.musicplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AlphabetScroller(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val alphabet = remember { ('A'..'Z').map { it.toString() } + "#" }
    var sidebarHeight by remember { mutableStateOf(0) }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf<Float?>(null) }

    val handleTouchInput: (Float) -> Unit = { y ->
        if (sidebarHeight > 0) {
            val letterHeight = sidebarHeight / alphabet.size.toFloat()
            val index = (y / letterHeight).toInt().coerceIn(0, alphabet.lastIndex)
            val letter = alphabet[index]
            if (activeLetter != letter) {
                activeLetter = letter
                onLetterSelected(letter)
            }
        }
    }

    // Main Container with a slightly narrower width
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp) // Reduced from 44.dp
            .padding(vertical = 16.dp)
            .background(
                color = if (dragY != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                // Consume clicks to prevent them from reaching the list below
                detectTapGestures(onTap = { /* Just consuming */ })
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        dragY = down.position.y
                        handleTouchInput(down.position.y)
                        
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    dragY = change.position.y
                                    handleTouchInput(change.position.y)
                                    change.consume() // Critical: blocks underlying list
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        
                        activeLetter = null
                        dragY = null
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .onGloballyPositioned { sidebarHeight = it.size.height },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEachIndexed { index, letter ->
                // Calculate distance-based scaling (Magnifier effect)
                val isBeingTouched = dragY != null
                val itemCenterY = if (sidebarHeight > 0) {
                    (index + 0.5f) * (sidebarHeight / alphabet.size.toFloat())
                } else 0f
                
                val distance = if (dragY != null) abs(dragY!! - itemCenterY) else 1000f
                
                // Scale up based on proximity to finger
                val scale by animateFloatAsState(
                    targetValue = if (!isBeingTouched) 1f 
                                 else (1.8f - (distance / 80f)).coerceIn(1f, 1.8f),
                    label = "LetterScale"
                )

                Text(
                    text = letter,
                    fontSize = 11.sp,
                    fontWeight = if (activeLetter == letter) FontWeight.ExtraBold else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (activeLetter == letter) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            // Shift slightly to the left when scaling
                            translationX = if (isBeingTouched) -(scale - 1f) * 12.dp.toPx() else 0f
                        }
                )
            }
        }
    }

    // Large Center Overlay Bubble
    activeLetter?.let { letter ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
