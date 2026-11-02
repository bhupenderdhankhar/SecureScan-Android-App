package com.example.securescan.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.utils.CompressionQuality
import com.example.securescan.utils.CompressionResult
import com.example.securescan.utils.PdfCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReducePdfUiState(
    val isLoading: Boolean = false,
    val selectedUri: Uri? = null,
    val selectedFileName: String = "",
    val selectedFileSizeBytes: Long = 0L,
    val result: CompressionResult? = null,
    val errorMessage: String? = null,
    val selectedQuality: CompressionQuality = CompressionQuality.MEDIUM,
    val customSizeMb: String = "",
    val useCustomSize: Boolean = false
)

@HiltViewModel
class ReducePdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReducePdfUiState())
    val uiState = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri, fileName: String, fileSize: Long) {
        _uiState.update {
            it.copy(
                selectedUri = uri,
                selectedFileName = fileName,
                selectedFileSizeBytes = fileSize,
                result = null,
                errorMessage = null
            )
        }
    }

    fun onQualitySelected(quality: CompressionQuality) {
        _uiState.update {
            it.copy(selectedQuality = quality, useCustomSize = false)
        }
    }

    fun onCustomSizeToggled(enabled: Boolean) {
        _uiState.update { it.copy(useCustomSize = enabled) }
    }

    fun onCustomSizeChanged(value: String) {
        _uiState.update { it.copy(customSizeMb = value) }
    }

    fun compress() {
        val state = _uiState.value
        val uri = state.selectedUri ?: run {
            _uiState.update { it.copy(errorMessage = "Please select a PDF first") }
            return
        }

        // Validate custom size
        val targetSizeMb: Float? = if (state.useCustomSize) {
            val parsed = state.customSizeMb.toFloatOrNull()
            if (parsed == null || parsed <= 0f) {
                _uiState.update { it.copy(errorMessage = "Enter a valid size in MB") }
                return
            }
            parsed
        } else null

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, result = null) }

            PdfCompressor.compressPdf(
                context = context,
                sourceUri = uri,
                quality = state.selectedQuality,
                targetSizeMb = targetSizeMb
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(isLoading = false, result = result)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Compression failed. Try another file."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reset() {
        _uiState.update { ReducePdfUiState() }
    }
}