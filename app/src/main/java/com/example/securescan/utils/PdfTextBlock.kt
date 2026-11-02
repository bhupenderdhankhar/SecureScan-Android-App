package com.example.securescan.utils

import androidx.compose.ui.geometry.Rect

data class FontRecord(
    val originalFontSize: Float,
    val originalIsBold: Boolean,
    val originalIsItalic: Boolean,
    val originalFontName: String = "Helvetica"
)

data class PdfTextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val editedText: String = text,
    // bounds in BITMAP pixels (at renderWidth=1080)
    val bounds: Rect,
    val pageIndex: Int,
    // paintTextSize = exact Android Paint.textSize to match original
    val fontSize: Float = 12f,
    val fontName: String = "Helvetica",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val userFontSize: Float? = null,
    val userIsBold: Boolean? = null,
    val fontRecord: FontRecord = FontRecord(fontSize, isBold, false)
) {
    val isEdited: Boolean
        get() = editedText != text ||
                offsetX != 0f ||
                offsetY != 0f ||
                userFontSize != null ||
                userIsBold != null

    val effectiveFontSize: Float
        get() = userFontSize ?: fontSize

    val effectiveIsBold: Boolean
        get() = userIsBold ?: isBold

    val hasFontSizeChanged: Boolean
        get() = userFontSize != null &&
                kotlin.math.abs(userFontSize - fontRecord.originalFontSize) > 0.5f

    val finalBounds: Rect
        get() = Rect(
            left   = bounds.left   + offsetX,
            top    = bounds.top    + offsetY,
            right  = bounds.right  + offsetX,
            bottom = bounds.bottom + offsetY
        )
}