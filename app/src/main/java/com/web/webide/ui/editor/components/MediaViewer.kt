/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.web.webide.ui.editor.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.DecimalFormat
import com.web.webide.R

enum class MediaType {
    IMAGE, VIDEO, SVG,
}

@Composable
fun MediaViewer(file: File, type: MediaType) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (type) {
            MediaType.IMAGE, MediaType.SVG -> ImageViewer(file, type)
            MediaType.VIDEO -> VideoPlayer(file)
        }
    }
}

@Composable
private fun ImageViewer(file: File, type: MediaType) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Image Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.1f, 10f)
                        scale = newScale
                        offset += pan
                    }
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .apply {
                        if (type == MediaType.SVG) {
                            decoderFactory(SvgDecoder.Factory())
                        }
                    }
                    .crossfade(true)
                    .build(),
                contentDescription = file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
        }

        // Controls Toolbar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            shape = CircleShape,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom Out
                FilledTonalIconButton(onClick = {
                    scale = (scale / 1.2f).coerceAtLeast(0.1f)
                }) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.media_zoom_out))
                }

                // Reset / Fit Screen
                FilledTonalIconButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) {
                    Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.media_fit_screen))
                }

                // Zoom In
                FilledTonalIconButton(onClick = {
                    scale = (scale * 1.2f).coerceAtMost(10f)
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.media_zoom_in))
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(file: File) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Player State
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true // Auto play
        }
    }
    
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoSize by remember { mutableStateOf<androidx.media3.common.VideoSize?>(null) }
    
    // UI State
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showResizeMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLooping by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    
    // Zoom/Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Listeners and Polling
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration
                    videoSize = exoPlayer.videoSize
                }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration
                currentPosition = player.currentPosition
                videoSize = player.videoSize
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(500)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000)
            isControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val width = size.width
                        if (tapOffset.x > width * 0.66f) {
                             // Forward 10s
                             val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                             exoPlayer.seekTo(newPos)
                        } else if (tapOffset.x < width * 0.33f) {
                             // Rewind 10s
                             val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                             exoPlayer.seekTo(newPos)
                        } else {
                            // Center Double Tap -> Toggle Zoom (Existing Logic)
                            if (scale > 1.1f) {
                                // Reset
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                // Zoom In (2x at tap point)
                                scale = 2.5f
                                // Calculate offset to keep tap point centered
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val pan = center - tapOffset
                                offset = pan * (scale - 1) // Approximation
                            }
                        }
                    },
                    onTap = {
                        isControlsVisible = !isControlsVisible
                    },
                    onPress = { pressOffset ->
                        // Long Press Logic
                        val width = size.width
                        val isRight = pressOffset.x > width * 0.66f
                        val isLeft = pressOffset.x < width * 0.33f
                        
                        if (isRight || isLeft) {
                            try {
                                // Wait for long press threshold
                                withTimeout(500) {
                                    tryAwaitRelease()
                                }
                                // Released before timeout -> Tap/Double Tap handled by detector
                            } catch (_: TimeoutCancellationException) {
                                // Held > 500ms
                                isLongPressing = true
                                if (isRight) {
                                    // Speed Up (2.0x or 2x current speed?)
                                    // User said "Accumulating to become speed fast forward"
                                    // Simple implementation: Set to 2.0x
                                    val originalSpeed = exoPlayer.playbackParameters.speed
                                    exoPlayer.setPlaybackSpeed(2.0f)
                                    tryAwaitRelease()
                                    exoPlayer.setPlaybackSpeed(originalSpeed)
                                } else {
                                    // Rewind Loop (Simulated Fast Rewind)
                                    // Since we can't play backwards, we seek back repeatedly
                                    val job = scope.launch {
                                        while(isActive) {
                                            val newPos = (exoPlayer.currentPosition - 1000).coerceAtLeast(0)
                                            exoPlayer.seekTo(newPos)
                                            delay(50) // 20x speed roughly
                                        }
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                }
                                isLongPressing = false
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceAtLeast(1f)
                    scale = newScale
                    // Adjust offset
                    offset += pan
                    
                    // Optional: limit pan to keep image in view? 
                    // User said "No limit", so we allow free movement.
                }
            }
    ) {
        // Video Surface
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false // Use custom controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
        
        // Controls Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isControlsVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                
                // Top Bar
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Info Button
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.media_info_title), tint = Color.White)
                        }

                        // Resize Mode
                        Box {
                            IconButton(onClick = { showResizeMenu = true }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.media_aspect_ratio), tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showResizeMenu,
                                onDismissRequest = { showResizeMenu = false }
                            ) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.media_fit_screen)) }, onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; showResizeMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.media_fill_screen)) }, onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; showResizeMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.media_fixed_height)) }, onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT; showResizeMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.media_fixed_width)) }, onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH; showResizeMenu = false })
                            }
                        }
                    }
                }

                // Center Play/Pause & Speed Indicator
                Box(modifier = Modifier.align(Alignment.Center)) {
                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    
                    if (isLongPressing) {
                        Surface(
                            modifier = Modifier.padding(top = 80.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = CircleShape
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                Icon(Icons.Default.FastForward, stringResource(R.string.media_fast_forward), tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("2.0x", color = Color.White)
                            }
                        }
                    }
                }

                // Bottom Bar (Seekbar + Controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Time and Extra Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        
                        // Extra Controls (Speed, Loop)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             // Loop
                                 IconButton(onClick = {
                                     isLooping = !isLooping
                                     exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                 }) {
                                 Icon(
                                     Icons.Default.Loop, 
                                     contentDescription = stringResource(R.string.media_loop),
                                     tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                                 )
                             }

                             // Speed
                             Box {
                                 IconButton(onClick = { showSpeedMenu = true }) {
                                     Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.media_speed), tint = Color.White)
                                 }
                                 DropdownMenu(
                                     expanded = showSpeedMenu,
                                     onDismissRequest = { showSpeedMenu = false }
                                 ) {
                                     listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                         DropdownMenuItem(
                                             text = { Text("${speed}x") },
                                             onClick = {
                                                 playbackSpeed = speed
                                                 exoPlayer.setPlaybackSpeed(speed)
                                                 showSpeedMenu = false
                                             },
                                             leadingIcon = if (playbackSpeed == speed) {
                                                 { Icon(Icons.Default.PlayArrow, null) } // Checkmark replacement
                                             } else null
                                         )
                                     }
                                 }
                             }
                        }

                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    androidx.compose.material3.Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            currentPosition = it.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(currentPosition)
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
        
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text(stringResource(R.string.media_info_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.media_file_format, file.name))
                        Text(stringResource(R.string.media_size_format, DecimalFormat("#.##").format(file.length() / 1024.0 / 1024.0)))
                        if (videoSize != null) {
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.media_resolution_format, videoSize!!.width, videoSize!!.height))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) { Text(stringResource(R.string.action_close)) }
                }
            )
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
