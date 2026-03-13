package com.example.omireadersdk.sdk.overlay

import com.example.omireadersdk.sdk.model.SmilDocument
import com.example.omireadersdk.sdk.renderer.WebViewRenderer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaOverlayEngine @Inject constructor(
    private val renderer: WebViewRenderer
) {
    enum class State { IDLE, PLAYING, PAUSED }

    var state: State = State.IDLE
        private set

    fun start(smilDocument: SmilDocument) {
        // TODO (Week 2): ExoPlayer init + coroutine clip-advance loop
        state = State.PLAYING
    }

    fun pause() {
        // TODO (Week 2): exoPlayer.pause()
        state = State.PAUSED
    }

    fun resume() {
        // TODO (Week 2): exoPlayer.play()
        state = State.PLAYING
    }

    fun stop() {
        renderer.clearHighlight()
        state = State.IDLE
    }

    fun release() {
        // TODO (Week 2): exoPlayer.release()
        state = State.IDLE
    }
}