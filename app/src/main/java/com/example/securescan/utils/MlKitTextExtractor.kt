package com.example.securescan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MlKitTextExtractor {

    private fun isDarkPixel(bitmap: Bitmap, x: Int, y: Int): Boolean {
        val p   = bitmap.getPixel(x, y)
        val lum = 0.299 * Color.red(p) +
                0.587 * Color.green(p) +
                0.114 * Color.blue(p)
        return lum < 160.0
    }

    private fun measureInkHeight(
        bitmap: Bitmap,
        box: android.graphics.Rect
    ): Float {
        val left   = box.left.coerceIn(0, bitmap.width - 1)
        val top    = box.top.coerceIn(0, bitmap.height - 1)
        val right  = box.right.coerceIn(left + 1, bitmap.width - 1)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height - 1)

        if (left >= right || top >= bottom) return (box.bottom - box.top).toFloat()

        var firstDark = -1
        var lastDark  = -1

        for (row in top..bottom) {
            for (col in left..right step 2) {
                if (isDarkPixel(bitmap, col, row)) {
                    if (firstDark == -1) firstDark = row
                    lastDark = row
                    break
                }
            }
        }

        if (firstDark == -1 || lastDark == -1) {
            return (box.bottom - box.top).toFloat()
        }
        return (lastDark - firstDark + 1).toFloat().coerceAtLeast(4f)
    }

    private fun simulateDensity(
        text: String,
        inkHeight: Float,
        bold: Boolean
    ): Float {
        if (text.isBlank() || inkHeight < 1f) return 0f
        val paint = Paint().apply {
            textSize    = inkHeight * 1.2f
            typeface    = if (bold)
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else Typeface.DEFAULT
            isAntiAlias = true
            color       = android.graphics.Color.BLACK
        }
        val fm = paint.fontMetrics
        val w  = paint.measureText(text).toInt().coerceIn(1, 2000)
        val h  = (-fm.top + fm.bottom).toInt().coerceIn(1, 500)
        if (w * h > 400 * 400) return 0f
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).apply {
                drawColor(android.graphics.Color.WHITE)
                drawText(text, 0f, -fm.ascent, paint)
            }
            var d = 0
            var t = 0
            for (row in 0 until h step 2)
                for (col in 0 until w step 2) {
                    val px  = bmp.getPixel(col, row)
                    val lum = 0.299 * Color.red(px) +
                            0.587 * Color.green(px) +
                            0.114 * Color.blue(px)
                    if (lum < 160) d++
                    t++
                }
            bmp.recycle()
            if (t == 0) 0f else d.toFloat() / t
        } catch (e: Exception) {
            0f
        }
    }

    private fun detectBold(
        bitmap: Bitmap,
        box: android.graphics.Rect,
        text: String,
        inkHeight: Float
    ): Boolean {
        val left   = box.left.coerceIn(0, bitmap.width - 1)
        val top    = box.top.coerceIn(0, bitmap.height - 1)
        val right  = box.right.coerceIn(left + 1, bitmap.width - 1)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height - 1)

        if (left >= right || top >= bottom || text.isBlank()) return false

        val strokes = mutableListOf<Int>()
        for (row in top..bottom step 2) {
            var inStroke = false
            var start    = 0
            for (col in left..right) {
                val dark = isDarkPixel(bitmap, col, row)
                if (dark && !inStroke) {
                    inStroke = true
                    start    = col
                } else if (!dark && inStroke) {
                    inStroke = false
                    val w = col - start
                    if (w in 1..50) strokes.add(w)
                }
            }
            if (inStroke) {
                val w = right - start
                if (w in 1..50) strokes.add(w)
            }
        }
        val avgStroke = if (strokes.isEmpty()) 0f else strokes.average().toFloat()

        var darkPx  = 0
        var totalPx = 0
        for (row in top..bottom step 2)
            for (col in left..right step 2) {
                if (isDarkPixel(bitmap, col, row)) darkPx++
                totalPx++
            }

        val actualDensity  = if (totalPx == 0) 0f else darkPx.toFloat() / totalPx
        val regularDensity = simulateDensity(text, inkHeight, bold = false)
        val boldDensity    = simulateDensity(text, inkHeight, bold = true)
        val diffRegular    = kotlin.math.abs(actualDensity - regularDensity)
        val diffBold       = kotlin.math.abs(actualDensity - boldDensity)

        return avgStroke > 2.8f && diffBold < diffRegular
    }

    suspend fun extractTextBlocksWithPositions(
        context: Context,
        uri: Uri,
        renderWidth: Int = 1080
    ): Result<Map<Int, List<PdfTextBlock>>> = withContext(Dispatchers.IO) {
        runCatching {
            val recognizer    = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
            val allPageBlocks = mutableMapOf<Int, List<PdfTextBlock>>()

            val pfd      = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open PDF")
            val renderer = PdfRenderer(pfd)

            for (pageIndex in 0 until renderer.pageCount) {
                val page         = renderer.openPage(pageIndex)
                val scale        = renderWidth.toFloat() / page.width
                val renderHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(
                    renderWidth, renderHeight, Bitmap.Config.ARGB_8888
                )
                android.graphics.Canvas(bitmap).drawColor(
                    android.graphics.Color.WHITE
                )
                page.render(
                    bitmap, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()

                val result = Tasks.await(
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                )
                val blocks = mutableListOf<PdfTextBlock>()

                result.textBlocks.forEach { textBlock ->
                    textBlock.lines.forEach { line ->
                        val box = line.boundingBox
                        if (box != null && line.text.isNotBlank()) {
                            val inkHeight = measureInkHeight(bitmap, box)
                            val bold      = detectBold(
                                bitmap, box, line.text.trim(), inkHeight
                            )
                            val boxHeight = (box.bottom - box.top).toFloat()
                            val paintSize = computePaintTextSize(boxHeight, bold)

                            blocks.add(
                                PdfTextBlock(
                                    text       = line.text.trim(),
                                    editedText = line.text.trim(),
                                    bounds     = Rect(
                                        left   = box.left.toFloat(),
                                        top    = box.top.toFloat(),
                                        right  = box.right.toFloat(),
                                        bottom = box.bottom.toFloat()
                                    ),
                                    pageIndex  = pageIndex,
                                    fontSize   = paintSize,
                                    isBold     = bold,
                                    fontRecord = FontRecord(
                                        originalFontSize = paintSize,
                                        originalIsBold   = bold,
                                        originalIsItalic = false
                                    )
                                )
                            )
                        }
                    }
                }

                allPageBlocks[pageIndex] = blocks
                bitmap.recycle()
            }

            renderer.close()
            pfd.close()
            recognizer.close()
            allPageBlocks
        }
    }

    private fun computePaintTextSize(
        boundingBoxHeightPx: Float,
        isBold: Boolean
    ): Float {
        val inkRatio  = if (isBold) 0.70f else 0.68f
        val inkHeight = boundingBoxHeightPx * inkRatio
        return (inkHeight / 0.72f).coerceIn(6f, 300f)
    }
}