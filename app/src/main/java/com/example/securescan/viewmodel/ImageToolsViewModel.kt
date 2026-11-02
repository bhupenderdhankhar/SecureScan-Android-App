package com.example.securescan.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.repository.ImageToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageToolsUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val outputUri: Uri? = null
)

@HiltViewModel
class ImageToolsViewModel @Inject constructor(
    private val repository: ImageToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToolsUiState())
    val uiState = _uiState.asStateFlow()

    fun rotateFlipImage(
        sourceUri: Uri,
        fileName: String,
        rotation: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.rotateFlipImage(sourceUri, fileName, rotation, flipHorizontal, flipVertical)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false, successMessage = "Image saved", outputUri = uri) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to edit image") }
                }
        }
    }

    fun resizeImage(
        sourceUri: Uri,
        fileName: String,
        width: Int,
        height: Int
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.resizeImage(sourceUri, fileName, width, height)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false, successMessage = "Image saved", outputUri = uri) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to resize image") }
                }
        }
    }

    fun cropImage(
        sourceUri: Uri,
        fileName: String,
        leftPercent: Float,
        topPercent: Float,
        widthPercent: Float,
        heightPercent: Float
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.cropImage(sourceUri, fileName, leftPercent, topPercent, widthPercent, heightPercent)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false, successMessage = "Image saved", outputUri = uri) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to crop image") }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
