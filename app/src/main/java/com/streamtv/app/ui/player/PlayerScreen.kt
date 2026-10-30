package com.streamtv.app.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.streamtv.app.ui.theme.TvPrimary
import com.streamtv.core.model.MediaItem
import com.streamtv.core.model.StreamResult
import com.streamtv.core.player.TvPlayerController
import com.streamtv.providers.api.StreamProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    mediaItem: MediaItem,
    streamProvider: StreamProvider,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { TvPlayerController(context, coroutineScope) }
    val playerState by controller.playerState.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    // Resolve stream and start playback on screen launch
    LaunchedEffect(mediaItem) {
        val result = streamProvider.resolveStream(mediaItem)
        when (result) {
            is StreamResult.Success -> {
                controller.playStream(mediaItem, result)
            }
            is StreamResult.Error -> {
                controller.setError(result.message)
            }
        }
    }

    // Auto-hide controls overlay after 5 seconds of inactivity
    LaunchedEffect(showControls, playerState.isPlaying) {
        if (showControls && playerState.isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Ensure resources are freed when exiting the screen
    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose {
            controller.release()
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    showControls = true
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_SPACE -> {
                            controller.togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            controller.seekForward(10_000)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            controller.seekBackward(10_000)
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Core ExoPlayer Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.getPlayer()
                    useController = false // We render our own 10-foot TV Compose HUD
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (playerState.isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Loading stream...",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Error Dialog / Banner
        if (playerState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF1E1E28), RoundedCornerShape(16.dp))
                    .padding(32.dp)
                    .width(450.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Playback Error",
                        color = TvPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = playerState.errorMessage ?: "Unknown error occurred",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(containerColor = TvPrimary)
                    ) {
                        Text(text = "Back to Menu", color = Color.White)
                    }
                }
            }
        }

        // TV Remote HUD Controls Overlay
        AnimatedVisibility(
            visible = showControls && playerState.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(48.dp)
            ) {
                // Top Header: Title & Subtitle
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Text(
                        text = playerState.title.ifEmpty { mediaItem.title },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (playerState.subtitle.isNotEmpty() || mediaItem.episodeTitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaItem.episodeTitle ?: playerState.subtitle,
                            fontSize = 16.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Bottom HUD: Time, Progress Bar, Remote Guide
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    // Remote Shortcuts Helper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "[OK] " + if (playerState.isPlaying) "Pause" else "Play",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = "[◄] -10s",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = "[►] +10s",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = "[BACK] Exit",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playerState.progressPercentage)
                                .height(6.dp)
                                .background(TvPrimary, RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(playerState.currentPositionMs),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (playerState.isLive) "LIVE" else formatTime(playerState.durationMs),
                            color = if (playerState.isLive) TvPrimary else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (playerState.isLive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
