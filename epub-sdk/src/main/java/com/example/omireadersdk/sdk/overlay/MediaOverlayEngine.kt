package com.example.omireadersdk.sdk.overlay

import android.content.Context
import com.example.omireadersdk.sdk.model.EpubBook
import com.example.omireadersdk.sdk.model.SmilClip
import com.example.omireadersdk.sdk.model.SmilDocument
import com.example.omireadersdk.sdk.parser.EpubExtractor
import com.example.omireadersdk.sdk.parser.SmilParser
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
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaOverlayEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: WebViewRenderer,
    private val audioPlayer: AudioClipPlayer,
    private val smilParser: SmilParser
) {
    enum class State { IDLE, LOADING, PLAYING, PAUSED }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<State>(State.IDLE)
    val state: StateFlow<State> = _state

    private val _currentFragmentId = MutableStateFlow<String?>(null)
    val currentFragmentId: StateFlow<String?> = _currentFragmentId

    private var clips = listOf<SmilClip>()
    private var currentClipIndex = 0
    private var currentBook: EpubBook? = null
    private var currentSpineIndex = 0
    private var cachedAudioFile: File? = null
    private var activeClass = "-epub-media-overlay-active"

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    fun start(
        book: EpubBook,
        spineIndex: Int,
        epubFile: File
    ) {
        currentBook = book
        currentSpineIndex = spineIndex
        activeClass = book.metadata.activeClass
        _state.value = State.LOADING

        engineScope.launch {
            try {
                val smilDoc = loadSmilDocument(book, spineIndex, epubFile)
                if (smilDoc == null || smilDoc.clips.isEmpty()) {
                    _state.value = State.IDLE
                    return@launch
                }
                clips = smilDoc.clips
                currentClipIndex = 0

                val audioFile = extractAudioToCache(book, smilDoc, epubFile)
                if (audioFile == null) {
                    _state.value = State.IDLE
                    return@launch
                }

                audioPlayer.prepareAudio(audioFile) {
                    _state.value = State.PLAYING

                    audioPlayer.playClipsInOrder(
                        clips = clips,
                        startClipIndex = 0,
                        scope = engineScope,
                        onHighlight = { index ->
                            currentClipIndex = index
                            val clip = clips.getOrNull(index) ?: return@playClipsInOrder
                            _currentFragmentId.value = clip.textFragmentId
                            renderer.highlight(clip.textFragmentId, activeClass)
                        },
                        onAllDone = {
                            renderer.clearHighlight(activeClass)
                            _currentFragmentId.value = null
                            _state.value = State.IDLE
                        }
                    )
                }
            } catch (e: Exception) {
                _state.value = State.IDLE
            }
        }
    }

    private fun highlightClip(index: Int) {
        val clip = clips.getOrNull(index) ?: return
        currentClipIndex = index
        _currentFragmentId.value = clip.textFragmentId
        renderer.highlight(clip.textFragmentId, activeClass)
    }

    fun pause() {
        if (_state.value != State.PLAYING) return
        audioPlayer.pause()
        _state.value = State.PAUSED
    }

    fun resume() {
        if (_state.value != State.PAUSED) return
        _state.value = State.PLAYING
        audioPlayer.resume()
    }

    fun stop() {
        audioPlayer.stop()
        renderer.clearHighlight(activeClass)
        _currentFragmentId.value = null
        _state.value = State.IDLE
    }

    fun release() {
        audioPlayer.release()
        engineScope.cancel()
        cachedAudioFile?.delete()
        cachedAudioFile = null
        _state.value = State.IDLE
    }

    // ─────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────

    private suspend fun loadSmilDocument(
        book: EpubBook,
        spineIndex: Int,
        epubFile: File
    ): SmilDocument? = withContext(Dispatchers.IO) {
        val spineItem = book.spine.getOrNull(spineIndex) ?: return@withContext null
        val manifestItem = book.manifest[spineItem.manifestItemId] ?: return@withContext null
        val overlayId = manifestItem.mediaOverlayId ?: return@withContext null
        val smilItem = book.manifest[overlayId] ?: return@withContext null

        val extractor = EpubExtractor(epubFile)
        extractor.use {
            val stream = it.openEntry(smilItem.href) ?: return@withContext null
            val smilBase = smilItem.href.substringBeforeLast("/", "")
                .let { b -> if (b.isEmpty()) "" else "$b/" }
            smilParser.parse(stream, spineItem.manifestItemId, smilBase)
        }
    }

    private suspend fun extractAudioToCache(
        book: EpubBook,
        smilDoc: SmilDocument,
        epubFile: File
    ): File? = withContext(Dispatchers.IO) {
        // All clips in vivek_basket use the same audio file
        val audioZipPath = smilDoc.clips.firstOrNull()?.audioSrc
            ?: return@withContext null

        val cacheFile = File(
            context.cacheDir,
            "omireader_audio_${audioZipPath.substringAfterLast("/")}"
        )

        // Use cached version if already extracted
        if (cacheFile.exists() && cacheFile.length() > 0) return@withContext cacheFile

        val extractor = EpubExtractor(epubFile)
        extractor.use {
            val stream = it.openEntry(audioZipPath) ?: return@withContext null
            FileOutputStream(cacheFile).use { out -> stream.copyTo(out) }
        }
        cacheFile
    }

    fun highlightFirstWord(book: EpubBook, spineIndex: Int, epubFile: File) {
        engineScope.launch {
            try {
                val smilDoc = loadSmilDocument(book, spineIndex, epubFile) ?: return@launch
                val firstClip = smilDoc.clips.firstOrNull() ?: return@launch
                renderer.highlight(firstClip.textFragmentId, book.metadata.activeClass)
                _currentFragmentId.value = firstClip.textFragmentId
            } catch (_: Exception) { }
        }
    }
}