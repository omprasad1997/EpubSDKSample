package com.example.omireadersdk.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omireadersdk.sdk.OmiReader
import com.example.omireadersdk.sdk.ReaderState
import com.example.omireadersdk.sdk.overlay.MediaOverlayEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val reader: OmiReader
) : ViewModel() {

    val readerState: StateFlow<ReaderState> = reader.readerState
    val overlayState: StateFlow<MediaOverlayEngine.State> = reader.overlayState
    val currentHighlight: StateFlow<String?> = reader.currentHighlightId

    fun loadEpub(uri: Uri, container: android.view.ViewGroup) {
        viewModelScope.launch {
            val file = copyUriToTemp(uri)
            reader.load(file, container)
        }
    }

    fun playOverlay()  = reader.playMediaOverlay()
    fun pauseOverlay() = reader.pauseMediaOverlay()
    fun resumeOverlay() = reader.resumeMediaOverlay()
    fun stopOverlay()  = reader.stopMediaOverlay()
    fun nextChapter()  = reader.nextChapter()
    fun prevChapter()  = reader.previousChapter()

    private fun copyUriToTemp(uri: Uri): File {
        val tempFile = File(context.cacheDir, "omireader_${System.currentTimeMillis()}.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    override fun onCleared() {
        super.onCleared()
        reader.release()
    }
}