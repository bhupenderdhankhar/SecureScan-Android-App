package com.example.securescan.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfEditorUtils {

    data class PageRenderResult(
        val bitmap: Bitmap,
        val pageIndex: Int,
        val pdfPageWidth: Int,
        val pdfPageHeight: Int,
        val renderWidth: Int,
        val renderHeight: Int
    )

    // ── Render pages ──────────────────────────────────────────────────────────

    suspend fun renderPdfPages(
        context: Context,
        uri: Uri,
        renderWidth: Int = 1080
    ): Result<List<PageRenderResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val results  = mutableListOf<PageRenderResult>()
            val pfd      = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open PDF")
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page         = renderer.openPage(i)
                val scale        = renderWidth.toFloat() / page.width
                val renderHeight = (page.height * scale).toInt()
                val bitmap       = Bitmap.createBitmap(
                    renderWidth, renderHeight, Bitmap.Config.ARGB_8888
                )
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                results.add(PageRenderResult(
                    bitmap        = bitmap,
                    pageIndex     = i,
                    pdfPageWidth  = page.width,
                    pdfPageHeight = page.height,
                    renderWidth   = renderWidth,
                    renderHeight  = renderHeight
                ))
                page.close()
            }
            renderer.close()
            pfd.close()
            results
        }
    }

    // ── Build paint ───────────────────────────────────────────────────────────

    /**
     * Builds Paint using the EXACT fontSize stored in PdfTextBlock.
     * fontSize = the Paint.textSize found by binary search in PdfBoxTextExtractor
     * that makes rendered line height == fontSizePt × scaleY pixels.
     */
    private fun buildPaint(block: PdfTextBlock): Paint {
        return Paint().apply {
            color       = Color.BLACK
            textSize    = block.effectiveFontSize
            isAntiAlias = true
            typeface    = when {
                block.effectiveIsBold && block.isItalic ->
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                block.effectiveIsBold ->
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                block.isItalic ->
                    Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
            style = Paint.Style.FILL
        }
    }

    // ── Expanded bounds ───────────────────────────────────────────────────────

    fun computeExpandedBounds(
        block: PdfTextBlock,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): androidx.compose.ui.geometry.Rect {
        val paint     = buildPaint(block)
        val origH     = block.bounds.bottom - block.bounds.top
        // Scale line height if user changed font size
        val lineH     = if (block.userFontSize != null && block.fontRecord.originalFontSize > 0f)
            origH * (block.effectiveFontSize / block.fontRecord.originalFontSize)
        else origH

        val maxW      = (bitmapWidth - block.finalBounds.left).coerceAtLeast(40f)
        val textW     = paint.measureText(block.editedText)

        return if (textW <= maxW) {
            androidx.compose.ui.geometry.Rect(
                left   = block.finalBounds.left,
                top    = block.finalBounds.top,
                right  = (block.finalBounds.left + textW + 2f)
                    .coerceAtMost(bitmapWidth.toFloat()),
                bottom = block.finalBounds.top + lineH
            )
        } else {
            val words = block.editedText.split(" ")
            var line  = ""
            var lines = 1
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) <= maxW) line = test
                else { lines++; line = word }
            }
            androidx.compose.ui.geometry.Rect(
                left   = block.finalBounds.left,
                top    = block.finalBounds.top,
                right  = block.finalBounds.left + maxW,
                bottom = block.finalBounds.top + lineH * lines
            )
        }
    }

    // ── Apply edit ────────────────────────────────────────────────────────────

    private fun applyTextEdit(
        canvas: Canvas,
        block: PdfTextBlock,
        bitmapWidth: Int,
        bitmapHeight: Int
    ) {
        val paint    = buildPaint(block)
        val rect     = computeExpandedBounds(block, bitmapWidth, bitmapHeight)
        val fm       = paint.fontMetrics
        val lineH    = rect.bottom - rect.top
        val maxW     = rect.right  - rect.left

        // 1. White out
        canvas.drawRect(
            rect.left - 2f, rect.top - 2f,
            rect.right + 2f, rect.bottom + 2f,
            Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        )

        // 2. Draw text — baseline = top of rect + ascent height
        val baseline = rect.top + (-fm.ascent)
        val words    = block.editedText.split(" ")
        var curLine  = ""
        var curBase  = baseline

        for (word in words) {
            val test = if (curLine.isEmpty()) word else "$curLine $word"
            if (paint.measureText(test) <= maxW) {
                curLine = test
            } else {
                if (curLine.isNotEmpty()) {
                    canvas.drawText(curLine, rect.left, curBase, paint)
                    curBase += lineH
                }
                curLine = word
            }
        }
        if (curLine.isNotEmpty()) {
            canvas.drawText(curLine, rect.left, curBase, paint)
        }
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    suspend fun renderPreviewWithEdits(
        originalPages: List<PageRenderResult>,
        textBlocks: Map<Int, List<PdfTextBlock>>
    ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            originalPages.map { pr ->
                val out = pr.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val c   = Canvas(out)
                (textBlocks[pr.pageIndex] ?: emptyList())
                    .filter { it.isEdited }
                    .forEach { applyTextEdit(c, it, out.width, out.height) }
                out
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    suspend fun saveEditedPdf(
        context: Context,
        sourceUri: Uri,
        textBlocks: Map<Int, List<PdfTextBlock>>,
        fileName: String,
        renderWidth: Int = 1080
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context)

            val pfd      = context.contentResolver.openFileDescriptor(sourceUri, "r")
                ?: error("Cannot open PDF")
            val renderer = PdfRenderer(pfd)
            val document = PDDocument()

            for (i in 0 until renderer.pageCount) {
                val page         = renderer.openPage(i)
                val previewScale = renderWidth.toFloat() / page.width
                val previewH     = (page.height * previewScale).toInt()

                // Render at 2× quality
                val hqW = renderWidth * 2
                val hqH = previewH   * 2

                val bitmap = Bitmap.createBitmap(hqW, hqH, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val edited = (textBlocks[i] ?: emptyList()).filter { it.isEdited }
                if (edited.isNotEmpty()) {
                    val hqFactor = hqW.toFloat() / renderWidth.toFloat()
                    val ec       = Canvas(bitmap)
                    edited.forEach { block ->
                        // Scale all pixel coords to HQ space
                        val hqBlock = block.copy(
                            bounds = androidx.compose.ui.geometry.Rect(
                                left   = block.bounds.left   * hqFactor,
                                top    = block.bounds.top    * hqFactor,
                                right  = block.bounds.right  * hqFactor,
                                bottom = block.bounds.bottom * hqFactor
                            ),
                            fontSize     = block.fontSize     * hqFactor,
                            offsetX      = block.offsetX      * hqFactor,
                            offsetY      = block.offsetY      * hqFactor,
                            userFontSize = block.userFontSize?.times(hqFactor),
                            fontRecord   = block.fontRecord.copy(
                                originalFontSize = block.fontRecord.originalFontSize * hqFactor
                            )
                        )
                        applyTextEdit(ec, hqBlock, hqW, hqH)
                    }
                }

                val pdfPage = PDPage(PDRectangle.A4)
                document.addPage(pdfPage)
                val js = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, js)
                bitmap.recycle()
                val pdImage = JPEGFactory.createFromByteArray(document, js.toByteArray())
                val cs      = PDPageContentStream(document, pdfPage)
                cs.drawImage(pdImage, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
                cs.close()
            }

            renderer.close()
            pfd.close()
            val out = ByteArrayOutputStream()
            document.save(out)
            document.close()
            saveFile(context, out.toByteArray(), fileName)
        }
    }

    private fun saveFile(context: Context, bytes: ByteArray, name: String): Uri {
        val clean   = name.trim().replace(" ", "_")
        val pdfName = if (clean.endsWith(".pdf")) clean else "$clean.pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pdfName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/SecureScanner")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"), values
            ) ?: error("MediaStore insert failed")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "SecureScanner")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, pdfName)
            FileOutputStream(file).use { it.write(bytes) }
            Uri.fromFile(file)
        }
    }
}