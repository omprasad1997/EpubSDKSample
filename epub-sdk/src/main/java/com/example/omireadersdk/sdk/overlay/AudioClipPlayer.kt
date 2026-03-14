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

/**
 * Wraps ExoPlayer to play precise audio clip ranges from the EPUB audio file.
 *
 * Each [SmilClip] has a clipBeginMs and clipEndMs — we seek ExoPlayer
 * to clipBeginMs and poll currentPosition every 50ms until clipEndMs
 * is reached, then call [onClipFinished] to advance to the next clip.
 */
@Singleton
class AudioClipPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var clipEndMs: Long = 0L
    private var pollJob: Job? = null
    private var onClipFinished: (() -> Unit)? = null
    private var isAudioReady = false

    // ─────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────

    /**
     * Prepares the ExoPlayer with the audio file.
     * Must be called once before [playClip].
     * For vivek_basket.epub this is "OEBPS/sample_audio.mp4"
     * which we extract to a temp cache file first.
     */
    fun prepareAudio(audioFile: File, onReady: () -> Unit) {
        release()
        val player = ExoPlayer.Builder(context).build().also {
            exoPlayer = it
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !isAudioReady) {
                    isAudioReady = true
                    onReady()
                }
            }
        })

        val mediaItem = MediaItem.fromUri(
            android.net.Uri.fromFile(audioFile)
        )
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    // ─────────────────────────────────────────────────────────────
    // Playback
    // ─────────────────────────────────────────────────────────────

    /**
     * Seeks to [clipBeginMs] and plays until [clipEndMs].
     * Calls [onClipFinished] when the clip ends so the engine
     * can advance to the next [SmilClip].
     */
    fun playClip(
        clipBeginMs: Long,
        clipEndMs: Long,
        scope: CoroutineScope,
        onClipFinished: () -> Unit
    ) {
        val player = exoPlayer ?: return
        this.clipEndMs = clipEndMs
        this.onClipFinished = onClipFinished

        // Cancel any previous poll
        pollJob?.cancel()

        // Seek and play
        player.seekTo(clipBeginMs)
        player.play()

        // Poll every 50ms — fire callback when we reach clipEndMs
        pollJob = scope.launch(Dispatchers.Main) {
            while (true) {
                delay(50)
                val pos = player.currentPosition
                if (pos >= clipEndMs) {
                    player.pause()
                    pollJob = null
                    onClipFinished()
                    break
                }
            }
        }
    }

    fun pause() {
        pollJob?.cancel()
        pollJob = null
        exoPlayer?.pause()
    }

    fun resume(
        clipBeginMs: Long,
        clipEndMs: Long,
        scope: CoroutineScope,
        onClipFinished: () -> Unit
    ) {
        // Resume means replay current clip from its beginning
        playClip(clipBeginMs, clipEndMs, scope, onClipFinished)
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        exoPlayer?.stop()
        exoPlayer?.seekTo(0)
    }

    fun release() {
        pollJob?.cancel()
        pollJob = null
        exoPlayer?.release()
        exoPlayer = null
        isAudioReady = false
    }

    val isReady: Boolean get() = isAudioReady
}