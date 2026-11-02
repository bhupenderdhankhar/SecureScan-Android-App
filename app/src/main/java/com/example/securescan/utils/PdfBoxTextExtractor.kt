package com.example.securescan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Rect
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfBoxTextExtractor {

    private const val TAG = "PdfBoxExtractor"

    private class PositionCapture(
        targetPage: Int,
        private val sink: MutableList<TextPosition>
    ) : PDFTextStripper() {
        init {
            startPage      = targetPage + 1
            endPage        = targetPage + 1
            sortByPosition = true
        }

        override fun writeString(
            text: String,
            textPositions: MutableList<TextPosition>?
        ) {
            super.writeString(text, textPositions)
            textPositions?.forEach { tp ->
                if (!tp.unicode.isNullOrBlank()) sink.add(tp)
            }
        }
    }

    /**
     * Groups characters into lines.
     * Two chars are on the same line if their Y difference
     * is less than half the average character height.
     */
    private fun groupToLines(
        positions: List<TextPosition>
    ): List<List<TextPosition>> {
        if (positions.isEmpty()) return emptyList()
        val sorted  = positions.sortedWith(compareBy({ it.y }, { it.x }))
        val avgH    = sorted.map { it.height }.average().toFloat()
        val yTol    = (avgH * 0.4f).coerceIn(1f, 6f)
        val lines   = mutableListOf<MutableList<TextPosition>>()
        var current = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            if (kotlin.math.abs(sorted[i].y - sorted[i - 1].y) <= yTol)
                current.add(sorted[i])
            else { lines.add(current); current = mutableListOf(sorted[i]) }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    /**
     * Finds the Android Paint.textSize that renders
     * at exactly [targetPx] pixels tall on screen.
     *
     * We match against the full line box height
     * (descent - ascent) not just cap height.
     */
    private fun findPaintSize(targetPx: Float, isBold: Boolean): Float {
        if (targetPx <= 0f) return 12f
        val paint = Paint().apply {
            isAntiAlias = true
            typeface    = if (isBold)
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else Typeface.DEFAULT
        }
        var lo = targetPx * 0.3f
        var hi = targetPx * 2.5f
        repeat(30) {
            val mid = (lo + hi) / 2f
            paint.textSize = mid
            val fm         = paint.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            when {
                lineHeight < targetPx -> lo = mid
                lineHeight > targetPx -> hi = mid
                else -> return mid
            }
        }
        return (lo + hi) / 2f
    }

    suspend fun extractTextBlocks(
        context: Context,
        uri: Uri,
        renderWidth: Int = 1080
    ): Result<Map<Int, List<PdfTextBlock>>> = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context)

            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Cannot read PDF")
            val doc = PDDocument.load(stream)
            stream.close()

            val allBlocks = mutableMapOf<Int, List<PdfTextBlock>>()

            for (pageIndex in 0 until doc.numberOfPages) {
                val pdPage       = doc.getPage(pageIndex)
                val pageWidthPt  = pdPage.mediaBox.width
                val pageHeightPt = pdPage.mediaBox.height
                val scaleX       = renderWidth.toFloat() / pageWidthPt
                val renderHeight = (pageHeightPt * scaleX).toInt()
                val scaleY       = renderHeight.toFloat() / pageHeightPt

                // Render page to bitmap so we can verify positions visually
                val rawPositions = mutableListOf<TextPosition>()
                try {
                    PositionCapture(pageIndex, rawPositions).getText(doc)
                } catch (e: Exception) {
                    Log.w(TAG, "Page $pageIndex: ${e.message}")
                    allBlocks[pageIndex] = emptyList()
                    continue
                }

                if (rawPositions.isEmpty()) {
                    allBlocks[pageIndex] = emptyList()
                    continue
                }

                val lines  = groupToLines(rawPositions)
                val blocks = mutableListOf<PdfTextBlock>()

                for (line in lines) {
                    if (line.isEmpty()) continue

                    // Build text preserving spaces between characters
                    val lineText = buildString {
                        for (i in line.indices) {
                            append(line[i].unicode)
                            // Add space if gap between chars is significant
                            if (i < line.size - 1) {
                                val gap = line[i + 1].x - (line[i].x + line[i].width)
                                if (gap > line[i].width * 0.3f) append(" ")
                            }
                        }
                    }.trim()

                    if (lineText.isBlank()) continue

                    // Dominant font size in PDF points
                    val fontSizePt = line
                        .groupBy { kotlin.math.round(it.fontSize * 10) }
                        .maxByOrNull { it.value.size }
                        ?.value?.first()?.fontSize
                        ?: line.first().fontSize

                    // Font name for bold/italic detection
                    val fontName  = line
                        .groupBy { it.font?.name ?: "" }
                        .maxByOrNull { it.value.size }
                        ?.key ?: ""
                    val fn        = fontName.uppercase()
                    val isBold    = fn.contains("BOLD") || fn.contains("BLACK") ||
                            fn.contains("HEAVY") || fn.contains("DEMI")
                    val isItalic  = fn.contains("ITALIC") || fn.contains("OBLIQUE")

                    // ── PDF → Bitmap coordinate conversion ──────────────────
                    //
                    // PDF uses bottom-left origin, Y up.
                    // Bitmap uses top-left origin, Y down.
                    //
                    // TextPosition gives us:
                    //   x        = left edge in PDF points (from left)
                    //   y        = BASELINE in PDF points (from BOTTOM)
                    //   width    = character width in points
                    //   height   = character HEIGHT in points (ascent+descent)
                    //
                    // To convert:
                    //   bitmapX      = pdfX * scaleX
                    //   bitmapBaseline = (pageHeight - pdfY) * scaleY
                    //   bitmapTop    = bitmapBaseline - height * scaleY
                    //   bitmapBottom = bitmapBaseline

                    val leftPt      = line.minOf { it.x }
                    val rightPt     = line.maxOf { it.x + it.width }
                    val baselinePt  = line.first().y    // PDF Y = baseline from bottom
                    val heightPt    = line.maxOf { it.height }

                    val bitmapLeft     = leftPt  * scaleX
                    val bitmapRight    = rightPt * scaleX
                    val bitmapBaseline = (pageHeightPt - baselinePt) * scaleY
                    val bitmapTop      = bitmapBaseline - heightPt * scaleY
                    val bitmapBottom   = bitmapBaseline

                    if (bitmapTop >= bitmapBottom || bitmapLeft >= bitmapRight) continue
                    if (bitmapBottom < 0 || bitmapTop > renderHeight) continue
                    if (bitmapRight  < 0 || bitmapLeft > renderWidth) continue

                    val clampedRect = Rect(
                        left   = bitmapLeft.coerceIn(0f, renderWidth.toFloat()),
                        top    = bitmapTop.coerceIn(0f, renderHeight.toFloat()),
                        right  = bitmapRight.coerceIn(0f, renderWidth.toFloat()),
                        bottom = bitmapBottom.coerceIn(0f, renderHeight.toFloat())
                    )

                    if (clampedRect.width <= 2f || clampedRect.height <= 2f) continue

                    // Convert PDF font size to bitmap pixel height
                    // fontSizePt points × scaleY pixels/point = pixel height
                    val fontHeightPx = fontSizePt * scaleY

                    // Find Paint.textSize that renders at fontHeightPx tall
                    val paintSize = findPaintSize(fontHeightPx, isBold)

                    Log.d(TAG, "Line: '$lineText' | fontPt=$fontSizePt " +
                            "fontPx=$fontHeightPx paintSize=$paintSize " +
                            "rect=$clampedRect bold=$isBold")

                    blocks.add(
                        PdfTextBlock(
                            text       = lineText,
                            editedText = lineText,
                            bounds     = clampedRect,
                            pageIndex  = pageIndex,
                            fontSize   = paintSize,
                            fontName   = fontName,
                            isBold     = isBold,
                            isItalic   = isItalic,
                            fontRecord = FontRecord(
                                originalFontSize = paintSize,
                                originalIsBold   = isBold,
                                originalIsItalic = isItalic,
                                originalFontName = fontName
                            )
                        )
                    )
                }

                Log.d(TAG, "Page $pageIndex: ${blocks.size} blocks")
                allBlocks[pageIndex] = blocks
            }

            doc.close()
            allBlocks
        }
    }
}