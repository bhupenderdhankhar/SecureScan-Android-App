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

data class ScannerUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val savedPdfUri: Uri? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val pdfRepository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()

    private val _scannedUris = MutableStateFlow<List<Uri>>(emptyList())
    val scannedUris = _scannedUris.asStateFlow()

    fun setScannedUris(uris: List<Uri>) {
        _scannedUris.value = uris
    }

    fun createPdf(
        fileName: String,
        password: String?,
        highQuality: Boolean,
        autoEnhance: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            pdfRepository.createPdf(
                imageUris = _scannedUris.value,
                fileName = fileName,
                password = password,
                highQuality = highQuality,
                autoEnhance = autoEnhance
            ).onSuccess { uri ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "PDF saved!",
                        savedPdfUri = uri
                    )
                }
                _scannedUris.value = emptyList()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to create PDF"
                    )
                }
            }
        }
    }

    fun applyPasswordToExistingPdf(sourceUri: Uri, fileName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            pdfRepository.applyPasswordToExistingPdf(sourceUri, fileName, password)
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Protected PDF saved!",
                            savedPdfUri = uri
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to protect PDF"
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
