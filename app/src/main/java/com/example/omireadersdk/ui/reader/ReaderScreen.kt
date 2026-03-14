package com.example.omireadersdk.ui.reader

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.omireadersdk.sdk.ReaderState
import com.example.omireadersdk.sdk.overlay.MediaOverlayEngine
import com.example.omireadersdk.viewmodel.ReaderViewModel
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext


@Composable
fun ReaderScreen(
    epubUri: android.net.Uri,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LockLandscape()

    val readerState by viewModel.readerState.collectAsState()
    val overlayState by viewModel.overlayState.collectAsState()

    var containerRef by remember { mutableStateOf<ViewGroup?>(null) }
    var epubLoaded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── WebView container ──────────────────────────────────
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).also { frame ->
                    frame.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    containerRef = frame
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Load EPUB once container is ready
        LaunchedEffect(containerRef) {
            val container = containerRef ?: return@LaunchedEffect
            if (!epubLoaded) {
                epubLoaded = true
                viewModel.loadEpub(epubUri, container)
            }
        }

        // ── Loading indicator ──────────────────────────────────
        if (readerState is ReaderState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // ── Error state ────────────────────────────────────────
        if (readerState is ReaderState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (readerState as ReaderState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // ── Overlay controls (bottom sheet style) ─────────────
        AnimatedVisibility(
            visible = readerState is ReaderState.Ready,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            OverlayControls(
                overlayState = overlayState,
                onPlay    = viewModel::playOverlay,
                onPause   = viewModel::pauseOverlay,
                onResume  = viewModel::resumeOverlay,
                onStop    = viewModel::stopOverlay,
                onNext    = viewModel::nextChapter,
                onPrev    = viewModel::prevChapter
            )
        }
    }
}

@Composable
private fun OverlayControls(
    overlayState: MediaOverlayEngine.State,
    onPlay:   () -> Unit,
    onPause:  () -> Unit,
    onResume: () -> Unit,
    onStop:   () -> Unit,
    onNext:   () -> Unit,
    onPrev:   () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous chapter
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Play / Pause / Resume
            when (overlayState) {
                MediaOverlayEngine.State.IDLE,
                MediaOverlayEngine.State.LOADING -> {
                    FilledIconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (overlayState == MediaOverlayEngine.State.LOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                MediaOverlayEngine.State.PLAYING -> {
                    FilledIconButton(
                        onClick = onPause,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Filled.Pause,
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                MediaOverlayEngine.State.PAUSED -> {
                    FilledIconButton(
                        onClick = onResume,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Stop
            IconButton(
                onClick = onStop,
                enabled = overlayState != MediaOverlayEngine.State.IDLE
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "Stop",
                    tint = if (overlayState != MediaOverlayEngine.State.IDLE)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            // Next chapter
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LockLandscape() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val original = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}