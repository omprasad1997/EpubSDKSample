package com.example.omireadersdk.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omireadersdk.sdk.model.EpubBook
import com.example.omireadersdk.sdk.parser.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epubParser: EpubParser
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState

    fun loadEpub(uri: Uri) {
        _uiState.value = MainUiState.Loading
        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) {
                    val tempFile = copyUriToTemp(uri)
                    epubParser.parse(tempFile).also { tempFile.delete() }
                }
                _uiState.value = MainUiState.Loaded(book)
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Failed to load EPUB")
            }
        }
    }

    private fun copyUriToTemp(uri: Uri): File {
        val tempFile = File(context.cacheDir, "omireader_${System.currentTimeMillis()}.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open input stream for $uri")
        return tempFile
    }
}

sealed class MainUiState {
    object Idle    : MainUiState()
    object Loading : MainUiState()
    data class Loaded(val book: EpubBook) : MainUiState()
    data class Error(val message: String) : MainUiState()
}