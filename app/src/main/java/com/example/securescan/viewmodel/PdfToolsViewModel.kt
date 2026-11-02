package com.example.securescan.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfToolsUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val outputUris: List<Uri> = emptyList()
)

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfToolsUiState())
    val uiState = _uiState.asStateFlow()

    fun mergePdfs(pdfUris: List<Uri>, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.mergePdfs(pdfUris, fileName)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false, successMessage = "Merged PDF saved", outputUris = listOf(uri)) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to merge PDFs") }
                }
        }
    }

    fun splitPdf(sourceUri: Uri, baseFileName: String, pagesPerSplit: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.splitPdf(sourceUri, baseFileName, pagesPerSplit)
                .onSuccess { uris ->
                    _uiState.update { it.copy(isLoading = false, successMessage = "${uris.size} files created", outputUris = uris) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to split PDF") }
                }
        }
    }

    fun extractPages(sourceUri: Uri, fileName: String, pageSpec: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.extractPages(sourceUri, fileName, pageSpec)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false, successMessage = "Pages extracted successfully", outputUris = listOf(uri)) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to extract pages") }
                }
        }
    }

    fun redactPdf(
        sourceUri: Uri,
        fileName: String,
        pageNumber: Int,
        leftPercent: Float,
        topPercent: Float,
        widthPercent: Float,
        heightPercent: Float
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            repository.redactPdfRegion(
                sourceUri = sourceUri,
                fileName = fileName,
                pageNumber = pageNumber,
                leftPercent = leftPercent,
                topPercent = topPercent,
                widthPercent = widthPercent,
                heightPercent = heightPercent
            ).onSuccess { uri ->
                _uiState.update { it.copy(isLoading = false, successMessage = "Redacted PDF saved", outputUris = listOf(uri)) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Failed to redact PDF") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
