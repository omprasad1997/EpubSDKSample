package com.example.omireadersdk.sdk

import android.content.Context
import android.view.ViewGroup
import com.example.omireadersdk.sdk.model.EpubBook
import com.example.omireadersdk.sdk.overlay.MediaOverlayEngine
import com.example.omireadersdk.sdk.parser.EpubParser
import com.example.omireadersdk.sdk.parser.SmilParser
import com.example.omireadersdk.sdk.renderer.EpubResourceServer
import com.example.omireadersdk.sdk.renderer.WebViewRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class OmiReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epubParser: EpubParser,
    private val smilParser: SmilParser,
    private val renderer: WebViewRenderer,
    private val overlayEngine: MediaOverlayEngine,
    private val resourceServer: EpubResourceServer
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    private val _readerState = MutableStateFlow<ReaderState>(ReaderState.Idle)
    val readerState: StateFlow<ReaderState> = _readerState

    val overlayState: StateFlow<MediaOverlayEngine.State> = overlayEngine.state

    private val _currentHighlightId = MutableStateFlow<String?>(null)
    val currentHighlightId: StateFlow<String?> = _currentHighlightId

    private var currentBook: EpubBook? = null
    private var currentSpineIndex = 0
    private var currentEpubFile: File? = null

    fun load(
        epubFile: File,
        container: ViewGroup,
        onLoaded: (EpubBook) -> Unit = {}
    ) {
        _readerState.value = ReaderState.Loading
        scope.launch {
            try {
                val book = withContext(Dispatchers.IO) { epubParser.parse(epubFile) }
                currentEpubFile = epubFile
                resourceServer.open(epubFile)
                // Warm up audio cache in background
                launch(Dispatchers.IO) {
                    preloadAudio(book, epubFile)
                }
                currentBook = book
                currentSpineIndex = 1
                renderer.attach(container)
                renderChapter(currentSpineIndex)
                _readerState.value = ReaderState.Ready(book)
                onLoaded(book)
            } catch (e: Exception) {
                _readerState.value = ReaderState.Error(e.message ?: "Failed to load EPUB")
            }
        }
    }

    private suspend fun preloadAudio(book: EpubBook, epubFile: File) {
        // Find first SMIL with audio and extract it to cache
        val firstSmilItem = book.manifest.values
            .firstOrNull { it.isSmil } ?: return

        try {
            val extractor = com.example.omireadersdk.sdk.parser.EpubExtractor(epubFile)
            extractor.use {
                val stream = it.openEntry(firstSmilItem.href) ?: return
                val smilBase = firstSmilItem.href.substringBeforeLast("/", "")
                    .let { b -> if (b.isEmpty()) "" else "$b/" }
                val smilDoc = smilParser.parse(stream, firstSmilItem.id, smilBase)
                val audioPath = smilDoc.clips.firstOrNull()?.audioSrc ?: return
                val cacheFile = java.io.File(
                    context.cacheDir,
                    "omireader_audio_${audioPath.substringAfterLast("/")}"
                )
                if (!cacheFile.exists()) {
                    val audioStream = it.openEntry(audioPath) ?: return
                    java.io.FileOutputStream(cacheFile).use { out -> audioStream.copyTo(out) }
                }
            }
        } catch (_: Exception) { }
    }

    fun goToChapter(index: Int) {
        val book = currentBook ?: return
        if (index !in book.spine.indices) return
        overlayEngine.stop()
        currentSpineIndex = index
        renderChapter(index)
    }

    fun nextChapter()     = goToChapter(currentSpineIndex + 1)
    fun previousChapter() = goToChapter(currentSpineIndex - 1)

    fun playMediaOverlay() {
        val book = currentBook ?: return
        val file = currentEpubFile ?: return
        overlayEngine.start(
            book = book,
            spineIndex = currentSpineIndex,
            epubFile = file
        )
    }

    fun pauseMediaOverlay()  = overlayEngine.pause()
    fun resumeMediaOverlay() = overlayEngine.resume()
    fun stopMediaOverlay()   = overlayEngine.stop()

    fun release() {
        overlayEngine.release()
        renderer.release()
        resourceServer.close()   // ← add this
        scope.cancel()
    }

    private fun renderChapter(index: Int) {
        val book = currentBook ?: return
        val spineItem = book.spine.getOrNull(index) ?: return
        val manifestItem = book.manifest[spineItem.manifestItemId] ?: return
        renderer.loadContent(manifestItem.href)
    }
}

sealed class ReaderState {
    object Idle : ReaderState()
    object Loading : ReaderState()
    data class Ready(val book: EpubBook) : ReaderState()
    data class Error(val message: String) : ReaderState()
}