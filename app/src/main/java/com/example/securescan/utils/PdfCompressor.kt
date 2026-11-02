package com.example.securescan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream

enum class CompressionQuality(
    val label: String,
    val subtitle: String,
    val jpegQuality: Int,
    val maxDimension: Int,
    val icon: String
) {
    HIGH(
        label = "High Quality",
        subtitle = "Best quality, larger file size",
        jpegQuality = 85,
        maxDimension = 1920,
        icon = "⭐"
    ),
    MEDIUM(
        label = "Medium Quality",
        subtitle = "Balanced quality and size",
        jpegQuality = 60,
        maxDimension = 1280,
        icon = "⚖️"
    ),
    LOW(
        label = "Low Size",
        subtitle = "Maximum compression, smaller file",
        jpegQuality = 35,
        maxDimension = 800,
        icon = "📦"
    )
}

data class CompressionResult(
    val originalSize: Long,
    val compressedSize: Long,
    val savedBytes: Long,
    val savedPercent: Int,
    val outputUri: Uri
)

object PdfCompressor {

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        quality: CompressionQuality,
        targetSizeMb: Float? = null
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context)

            // Get original file size
            val originalSize = getFileSize(context, sourceUri)

            // Determine jpeg quality based on target size or preset
            val jpegQuality = if (targetSizeMb != null) {
                calculateQualityForTargetSize(
                    context, sourceUri, targetSizeMb, originalSize
                )
            } else {
                quality.jpegQuality
            }

            val maxDim = if (targetSizeMb != null) {
                when {
                    targetSizeMb < 0.5f -> 600
                    targetSizeMb < 1f -> 800
                    targetSizeMb < 2f -> 1080
                    else -> quality.maxDimension
                }
            } else {
                quality.maxDimension
            }

            // Render PDF pages using Android PdfRenderer
            val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
                ?: error("Cannot open PDF file")

            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            if (pageCount == 0) error("PDF has no pages")

            // Create new compressed PDF using PDFBox
            val document = PDDocument()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)

                // Calculate scaled dimensions
                val pageWidth = page.width
                val pageHeight = page.height
                val scale = if (pageWidth > maxDim || pageHeight > maxDim) {
                    minOf(maxDim.toFloat() / pageWidth, maxDim.toFloat() / pageHeight)
                } else 1f

                val scaledWidth = (pageWidth * scale).toInt()
                val scaledHeight = (pageHeight * scale).toInt()

                // Render page to bitmap
                val bitmap = Bitmap.createBitmap(
                    scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888
                )

                // Fill white background first
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                // Compress bitmap to JPEG
                val jpegStream = ByteArrayOutputStream()
                val rgbBitmap = bitmap.copy(Bitmap.Config.RGB_565, false)
                bitmap.recycle()
                rgbBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegStream)
                rgbBitmap.recycle()

                val jpegBytes = jpegStream.toByteArray()

                // Add page to new PDF
                val pdfPage = PDPage(
                    PDRectangle(
                        PDRectangle.A4.width,
                        PDRectangle.A4.width * scaledHeight / scaledWidth
                    )
                )
                document.addPage(pdfPage)

                val pdImage = JPEGFactory.createFromByteArray(document, jpegBytes)
                val contentStream = PDPageContentStream(document, pdfPage)
                contentStream.drawImage(
                    pdImage,
                    0f, 0f,
                    pdfPage.mediaBox.width,
                    pdfPage.mediaBox.height
                )
                contentStream.close()
            }

            renderer.close()
            pfd.close()

            // Save compressed PDF
            val outputStream = ByteArrayOutputStream()
            document.save(outputStream)
            document.close()

            val compressedBytes = outputStream.toByteArray()
            val outputUri = saveCompressedFile(context, compressedBytes, sourceUri)
            val compressedSize = compressedBytes.size.toLong()
            val savedBytes = originalSize - compressedSize
            val savedPercent = if (originalSize > 0)
                ((savedBytes.toFloat() / originalSize) * 100).toInt() else 0

            CompressionResult(
                originalSize = originalSize,
                compressedSize = compressedSize,
                savedBytes = savedBytes.coerceAtLeast(0),
                savedPercent = savedPercent.coerceAtLeast(0),
                outputUri = outputUri
            )
        }
    }

    private fun calculateQualityForTargetSize(
        context: Context,
        sourceUri: Uri,
        targetSizeMb: Float,
        originalSize: Long
    ): Int {
        val targetBytes = (targetSizeMb * 1024 * 1024).toLong()
        val ratio = targetBytes.toFloat() / originalSize.toFloat()
        return when {
            ratio >= 0.9f -> 85
            ratio >= 0.6f -> 70
            ratio >= 0.4f -> 55
            ratio >= 0.25f -> 40
            ratio >= 0.15f -> 25
            else -> 15
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun saveCompressedFile(
        context: Context,
        bytes: ByteArray,
        sourceUri: Uri
    ): Uri {
        val originalName = getFileName(context, sourceUri)
            .removeSuffix(".pdf")
        val newName = "${originalName}_compressed.pdf"

        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOCUMENTS + "/SecureScanner"
            )
        }

        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Files.getContentUri("external"), values
        ) ?: error("Failed to create output file")

        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return uri
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "document"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
                )
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024f * 1024f))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
            else -> "$bytes B"
        }
    }
}