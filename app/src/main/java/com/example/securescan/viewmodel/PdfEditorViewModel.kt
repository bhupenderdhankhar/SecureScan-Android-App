package com.example.securescan.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.utils.MlKitTextExtractor
import com.example.securescan.utils.PdfBoxTextExtractor
import com.example.securescan.utils.PdfEditorUtils
import com.example.securescan.utils.PdfTextBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PdfEditScreen { EDIT, PREVIEW }
enum class EditorMode { NONE, ADD_TEXT, DRAW, HIGHLIGHT, OCR_EDIT, DIGITAL_EDIT }
enum class PdfType { UNKNOWN, DIGITAL, SCANNED }

data class FontSizeConflict(
    val blockId: String,
    val originalSize: Float,
    val userSize: Float
)

data class PdfEditorUiState(
    val isLoadingPdf: Boolean = false,
    val isExtractingText: Boolean = false,
    val isSaving: Boolean = false,
    val isGeneratingPreview: Boolean = false,
    val selectedUri: Uri? = null,
    val selectedFileName: String = "",
    val renderedPages: List<PdfEditorUtils.PageRenderResult> = emptyList(),
    val textBlocks: Map<Int, List<PdfTextBlock>> = emptyMap(),
    val selectedBlockId: String? = null,
    val currentScreen: PdfEditScreen = PdfEditScreen.EDIT,
    val previewPages: List<Bitmap> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val renderWidth: Int = 1080,
    val fontSizeConflicts: List<FontSizeConflict> = emptyList(),
    val showFontSizeConflictDialog: Boolean = false
) {
    val selectedBlock: PdfTextBlock?
        get() = textBlocks.values.flatten().find { it.id == selectedBlockId }

    val hasEdits: Boolean
        get() = textBlocks.values.flatten().any { it.isEdited }

    val totalEdits: Int
        get() = textBlocks.values.flatten().count { it.isEdited }
}

@HiltViewModel
class PdfEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfEditorUiState())
    val uiState = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        var fileName = "document.pdf"
        context.contentResolver.query(
            uri, null, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) fileName = cursor.getString(idx)
            }
        }

        _uiState.update { state ->
            state.copy(
                selectedUri      = uri,
                selectedFileName = fileName,
                isLoadingPdf     = true,
                errorMessage     = null,
                textBlocks       = emptyMap(),
                renderedPages    = emptyList(),
                selectedBlockId  = null
            )
        }

        viewModelScope.launch {
            // Step 1: Render pages for display
            val renderResult = PdfEditorUtils.renderPdfPages(context, uri)

            renderResult.onFailure { e ->
                _uiState.update { state ->
                    state.copy(
                        isLoadingPdf = false,
                        errorMessage = "Cannot open PDF: ${e.message}"
                    )
                }
                return@launch
            }

            val pages = renderResult.getOrElse { emptyList() }
            _uiState.update { state ->
                state.copy(
                    renderedPages    = pages,
                    isLoadingPdf     = false,
                    isExtractingText = true
                )
            }

            // Step 2: Try PDFBox first (exact font metrics from PDF structure)
            val pdfBoxResult = PdfBoxTextExtractor.extractTextBlocks(context, uri)
            val pdfBoxBlocks: Map<Int, List<PdfTextBlock>>? = pdfBoxResult.getOrNull()
            val totalPdfBoxBlocks = pdfBoxBlocks?.values?.sumOf { list -> list.size } ?: 0

            if (totalPdfBoxBlocks > 0 && pdfBoxBlocks != null) {
                // PDFBox succeeded — use exact font data
                _uiState.update { state ->
                    state.copy(
                        textBlocks       = pdfBoxBlocks,
                        isExtractingText = false
                    )
                }
            } else {
                // PDFBox returned nothing (scanned PDF) — fall back to ML Kit
                fallbackToMlKit(uri)
            }
        }
    }

    private suspend fun fallbackToMlKit(uri: Uri) {
        val mlResult = MlKitTextExtractor.extractTextBlocksWithPositions(
            context = context,
            uri     = uri
        )

        val blocks: Map<Int, List<PdfTextBlock>>? = mlResult.getOrNull()

        if (blocks != null) {
            _uiState.update { state ->
                state.copy(
                    textBlocks       = blocks,
                    isExtractingText = false
                )
            }
        } else {
            val error = mlResult.exceptionOrNull()
            _uiState.update { state ->
                state.copy(
                    isExtractingText = false,
                    errorMessage     = "Text detection failed: ${error?.message}"
                )
            }
        }
    }

    fun selectBlock(blockId: String) {
        _uiState.update { state ->
            state.copy(
                selectedBlockId = if (state.selectedBlockId == blockId) null else blockId
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state -> state.copy(selectedBlockId = null) }
    }

    fun updateBlockText(blockId: String, newText: String) {
        _uiState.update { state ->
            state.copy(
                textBlocks = state.textBlocks.mapValues { (_, blocks) ->
                    blocks.map { block ->
                        if (block.id == blockId) block.copy(editedText = newText) else block
                    }
                },
                selectedBlockId = null
            )
        }
    }

    fun setUserFontSize(blockId: String, size: Float) {
        _uiState.update { state ->
            state.copy(
                textBlocks = state.textBlocks.mapValues { (_, blocks) ->
                    blocks.map { block ->
                        if (block.id == blockId)
                            block.copy(userFontSize = size.coerceIn(4f, 200f))
                        else block
                    }
                }
            )
        }
    }

    fun toggleUserBold(blockId: String) {
        _uiState.update { state ->
            state.copy(
                textBlocks = state.textBlocks.mapValues { (_, blocks) ->
                    blocks.map { block ->
                        if (block.id == blockId) {
                            val currentBold = block.userIsBold ?: block.isBold
                            block.copy(userIsBold = !currentBold)
                        } else block
                    }
                }
            )
        }
    }

    fun resetFontSizeToOriginal(blockId: String) {
        _uiState.update { state ->
            state.copy(
                textBlocks = state.textBlocks.mapValues { (_, blocks) ->
                    blocks.map { block ->
                        if (block.id == blockId)
                            block.copy(userFontSize = block.fontRecord.originalFontSize)
                        else block
                    }
                }
            )
        }
    }

    fun moveBlock(blockId: String, deltaX: Float, deltaY: Float) {
        _uiState.update { state ->
            state.copy(
                textBlocks = state.textBlocks.mapValues { (_, blocks) ->
                    blocks.map { block ->
                        if (block.id == blockId)
                            block.copy(
                                offsetX = block.offsetX + deltaX,
                                offsetY = block.offsetY + deltaY
                            )
                        else block
                    }
                }
            )
        }
    }

    fun generatePreview() {
        val state = _uiState.value
        if (state.renderedPages.isEmpty()) return

        val conflicts = state.textBlocks.values
            .flatten()
            .filter { block -> block.hasFontSizeChanged }
            .map { block ->
                FontSizeConflict(
                    blockId      = block.id,
                    originalSize = block.fontRecord.originalFontSize,
                    userSize     = block.userFontSize!!
                )
            }

        if (conflicts.isNotEmpty()) {
            _uiState.update { s ->
                s.copy(
                    fontSizeConflicts          = conflicts,
                    showFontSizeConflictDialog = true
                )
            }
            return
        }

        proceedWithPreview()
    }

    fun resolveConflictsAndPreview(useOriginalSize: Boolean) {
        if (useOriginalSize) {
            _uiState.update { state ->
                val conflicts = state.fontSizeConflicts
                state.copy(
                    textBlocks = state.textBlocks.mapValues { (_, blocks) ->
                        blocks.map { block ->
                            if (conflicts.any { c -> c.blockId == block.id })
                                block.copy(userFontSize = block.fontRecord.originalFontSize)
                            else block
                        }
                    },
                    showFontSizeConflictDialog = false,
                    fontSizeConflicts          = emptyList()
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    showFontSizeConflictDialog = false,
                    fontSizeConflicts          = emptyList()
                )
            }
        }
        proceedWithPreview()
    }

    fun dismissConflictDialog() {
        _uiState.update { state ->
            state.copy(
                showFontSizeConflictDialog = false,
                fontSizeConflicts          = emptyList()
            )
        }
    }

    private fun proceedWithPreview() {
        val state = _uiState.value
        _uiState.update { s ->
            s.copy(
                isGeneratingPreview = true,
                currentScreen       = PdfEditScreen.PREVIEW
            )
        }
        viewModelScope.launch {
            PdfEditorUtils.renderPreviewWithEdits(
                originalPages = state.renderedPages,
                textBlocks    = state.textBlocks
            ).onSuccess { bitmaps ->
                _uiState.update { s ->
                    s.copy(
                        previewPages        = bitmaps,
                        isGeneratingPreview = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { s ->
                    s.copy(
                        isGeneratingPreview = false,
                        currentScreen       = PdfEditScreen.EDIT,
                        errorMessage        = "Preview failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun goBackToEditing() {
        _uiState.value.previewPages.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        _uiState.update { state ->
            state.copy(
                currentScreen = PdfEditScreen.EDIT,
                previewPages  = emptyList()
            )
        }
    }

    fun savePdf() {
        val state = _uiState.value
        val uri   = state.selectedUri ?: run {
            _uiState.update { s -> s.copy(errorMessage = "No file selected") }
            return
        }
        _uiState.update { s -> s.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val outputName = state.selectedFileName.removeSuffix(".pdf") + "_edited"
            PdfEditorUtils.saveEditedPdf(
                context     = context,
                sourceUri   = uri,
                textBlocks  = state.textBlocks,
                fileName    = outputName,
                renderWidth = state.renderWidth
            ).onSuccess {
                _uiState.update { s ->
                    s.copy(
                        isSaving       = false,
                        successMessage = "PDF saved to Documents/SecureScanner"
                    )
                }
            }.onFailure { e ->
                _uiState.update { s ->
                    s.copy(
                        isSaving     = false,
                        errorMessage = "Save failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { state ->
            state.copy(errorMessage = null, successMessage = null)
        }
    }

    // ── Compatibility stubs ───────────────────────────────────────────────────
    fun setEditorMode(mode: EditorMode) {}
    fun setSelectedColor(color: androidx.compose.ui.graphics.Color) {}
    fun addPathPoint(offset: Offset) {}
    fun finishDrawing() {}
    fun addHighlight(pos: Offset, w: Float, h: Float) {}
    fun clearOverlays() {}
    fun setCanvasSize(w: Int, h: Int) {}
    fun runOcr() {}
    fun saveDigitalPdf() { savePdf() }
    fun saveOcrPdf() { savePdf() }
    fun saveWithOverlaysOnly() { savePdf() }
    fun onDigitalTextEdited(text: String) {}
    fun onOcrTextEdited(page: Int, text: String) {}
}