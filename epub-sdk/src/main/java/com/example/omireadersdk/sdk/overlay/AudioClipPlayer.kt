package com.example.omireadersdk.sdk.overlay

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioClipPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var trackingJob: Job? = null
    private var isAudioReady = false
    private var isPaused = false
    private var pausedAtMs = 0L

    fun prepareAudio(audioFile: File, onReady: () -> Unit) {
        release()
        val player = ExoPlayer.Builder(context).build().also { exoPlayer = it }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !isAudioReady) {
                    isAudioReady = true
                    onReady()
                }
            }
        })
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(audioFile)))
        player.prepare()
    }

    /**
     * Starts continuous playback from [startMs].
     * Instead of seeking per clip, we play continuously and fire
     * [onClipBoundary] whenever we cross a clip boundary.
     *
     * @param startMs        Where to start in the audio file
     * @param clipBoundaries Sorted list of clipEndMs values for each clip
     * @param onClipBoundary Called with the index of the clip that just finished
     * @param onAllDone      Called when we pass the last clip's end
     */
    fun startContinuous(
        startMs: Long,
        clipBoundaries: List<Long>,
        scope: CoroutineScope,
        onClipBoundary: (clipIndex: Int) -> Unit,
        onAllDone: () -> Unit
    ) {
        val player = exoPlayer ?: return
        trackingJob?.cancel()
        isPaused = false

        val boundaries = clipBoundaries.sorted()
        var nextBoundaryIndex = 0

        // Listen for seek completion before starting tracking
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Seek completed — remove this listener and start tracking
                    player.removeListener(this)
                    startTracking(player, startMs, boundaries, scope, onClipBoundary, onAllDone)
                        .also { trackingJob = it }
                }
            }
        })

        // Seek and play
        player.seekTo(startMs)
        player.playWhenReady = true
    }

    private fun startTracking(
        player: ExoPlayer,
        startMs: Long,
        boundaries: List<Long>,
        scope: CoroutineScope,
        onClipBoundary: (clipIndex: Int) -> Unit,
        onAllDone: () -> Unit
    ): Job {
        return scope.launch(Dispatchers.Main) {

            // Wait one frame for position to stabilize after seek
            delay(32)

            // Use ACTUAL current position — not startMs
            // By the time tracking starts, player may have already
            // moved past several clips
            var nextBoundaryIndex = 0
            val actualPos = player.currentPosition

            // Skip all boundaries already passed based on real position
            while (nextBoundaryIndex < boundaries.size &&
                boundaries[nextBoundaryIndex] <= actualPos) {
                nextBoundaryIndex++
            }

            // Fire highlight for the clip we're currently inside
            if (nextBoundaryIndex > 0) {
                onClipBoundary(nextBoundaryIndex - 1)
            }

            // Now track forward from actual position
            while (nextBoundaryIndex < boundaries.size) {
                delay(16)
                if (isPaused) continue

                val pos = player.currentPosition

                // Safety: ignore stale position during buffering
                if (pos < startMs - 200) continue

                if (pos >= boundaries[nextBoundaryIndex]) {
                    onClipBoundary(nextBoundaryIndex)
                    nextBoundaryIndex++
                }
            }

            player.pause()
            onAllDone()
        }
    }

    fun pause() {
        isPaused = true
        pausedAtMs = exoPlayer?.currentPosition ?: 0L
        exoPlayer?.pause()
    }

    fun resume() {
        isPaused = false
        exoPlayer?.play()
    }

    fun stop() {
        trackingJob?.cancel()
        trackingJob = null
        isPaused = false
        exoPlayer?.stop()
    }

    fun release() {
        trackingJob?.cancel()
        trackingJob = null
        exoPlayer?.release()
        exoPlayer = null
        isAudioReady = false
        isPaused = false
    }

    val isReady: Boolean get() = isAudioReady
}