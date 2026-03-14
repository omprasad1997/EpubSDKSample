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
    var isPaused = false
        private set

    companion object {
        // If next clipBegin differs from current clipEnd by more than this,
        // we must seek rather than play continuously
        private const val CONTINUITY_THRESHOLD_MS = 300L
    }

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
     * Plays clips in SMIL order.
     *
     * For sequential clips (clipBeginN ≈ clipEndN-1): plays continuously, no seek.
     * For non-sequential clips (big gap or backward jump): seeks to next clipBegin.
     *
     * This handles pages like page 3 where the shopping list audio (25-46s)
     * comes after the narrative audio (46-55s) in SMIL order but is earlier
     * in the actual audio file.
     *
     * @param clips          Full list of SmilClips in SMIL order
     * @param startClipIndex Which clip to start from
     * @param onHighlight    Called with clip index when that clip starts playing
     * @param onAllDone      Called when all clips are done
     */
    fun playClipsInOrder(
        clips: List<com.example.omireadersdk.sdk.model.SmilClip>,
        startClipIndex: Int = 0,
        scope: CoroutineScope,
        onHighlight: (clipIndex: Int) -> Unit,
        onAllDone: () -> Unit
    ) {
        val player = exoPlayer ?: return
        trackingJob?.cancel()
        isPaused = false

        trackingJob = scope.launch(Dispatchers.Main) {
            var clipIndex = startClipIndex

            // Seek to first clip
            seekAndWait(player, clips[clipIndex].clipBeginMs)

            while (clipIndex < clips.size) {
                if (isPaused) {
                    delay(50)
                    continue
                }

                val clip = clips[clipIndex]

                // Highlight current word
                onHighlight(clipIndex)

                // Ensure audio is playing
                if (!player.isPlaying) player.play()

                // Wait until we reach clipEnd
                while (true) {
                    delay(16)
                    if (isPaused) break
                    val pos = player.currentPosition
                    if (pos >= clip.clipEndMs) break
                }

                if (isPaused) continue

                clipIndex++

                if (clipIndex >= clips.size) break

                val nextClip = clips[clipIndex]
                val gap = nextClip.clipBeginMs - clip.clipEndMs

                // If next clip is not sequential — seek to it
                if (gap > CONTINUITY_THRESHOLD_MS || gap < -CONTINUITY_THRESHOLD_MS) {
                    seekAndWait(player, nextClip.clipBeginMs)
                }
                // If sequential — just keep playing, no seek needed
            }

            player.pause()
            onAllDone()
        }
    }

    fun pause() {
        isPaused = true
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

    /**
     * Seeks ExoPlayer and waits for the seek to stabilize.
     * Uses onPositionDiscontinuity callback for accuracy.
     */
    private suspend fun seekAndWait(player: ExoPlayer, positionMs: Long) {
        var seekDone = false
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    seekDone = true
                }
            }
        }
        player.addListener(listener)
        player.seekTo(positionMs)
        player.playWhenReady = true

        // Wait for seek to complete, max 500ms
        var waited = 0
        while (!seekDone && waited < 500) {
            delay(16)
            waited += 16
        }
        player.removeListener(listener)

        // Extra stabilization frame
        delay(32)
    }
}