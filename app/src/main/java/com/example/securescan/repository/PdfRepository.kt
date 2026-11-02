package com.example.securescan.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun createPdf(
        imageUris: List<Uri>,
        fileName: String,
        password: String? = null,
        highQuality: Boolean = false,
        autoEnhance: Boolean = true
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PDDocument()
            var addedCount = 0
            val jpegQuality = if (highQuality) 95 else 70

            imageUris.forEach { uri ->
                val sourceBitmap = loadBitmap(uri) ?: return@forEach
                val workingBitmap = sourceBitmap.prepareForPdf(
                    autoEnhance = autoEnhance,
                    highQuality = highQuality
                )

                val page = PDPage(PDRectangle.A4)
                document.addPage(page)

                val baos = ByteArrayOutputStream()
                workingBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                val pdImage = JPEGFactory.createFromByteArray(document, baos.toByteArray())

                val contentStream = PDPageContentStream(document, page)
                val pageWidth = PDRectangle.A4.width
                val pageHeight = PDRectangle.A4.height
                val margin = if (highQuality) 0f else 12f
                val maxWidth = pageWidth - (margin * 2)
                val maxHeight = pageHeight - (margin * 2)

                val imgRatio = workingBitmap.height.toFloat() / workingBitmap.width.toFloat()
                var imgWidth = maxWidth
                var imgHeight = imgWidth * imgRatio

                if (imgHeight > maxHeight) {
                    imgHeight = maxHeight
                    imgWidth = imgHeight / imgRatio
                }

                val xPos = (pageWidth - imgWidth) / 2
                val yPos = (pageHeight - imgHeight) / 2

                contentStream.drawImage(pdImage, xPos, yPos, imgWidth, imgHeight)
                contentStream.close()

                if (workingBitmap !== sourceBitmap) {
                    workingBitmap.recycle()
                }
                sourceBitmap.recycle()
                addedCount++
            }

            if (addedCount == 0) {
                document.close()
                throw Exception("No valid images found to create PDF")
            }

            if (!password.isNullOrBlank()) {
                applyEncryption(document, password)
            }

            saveDocument(document, fileName)
        }
    }

    suspend fun applyPasswordToExistingPdf(
        sourceUri: Uri,
        fileName: String,
        password: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val document = loadPdf(sourceUri)
            applyEncryption(document, password)
            val cleanName = fileName.removeSuffix(".pdf").trim()
            saveDocument(document, "${cleanName}_protected")
        }
    }

    suspend fun mergePdfs(
        pdfUris: List<Uri>,
        fileName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (pdfUris.size < 2) throw IllegalArgumentException("Select at least 2 PDFs")

            val merger = PDFMergerUtility()
            val output = ByteArrayOutputStream()
            val openedStreams = mutableListOf<java.io.InputStream>()

            try {
                pdfUris.forEach { uri ->
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Cannot read selected PDF")
                    openedStreams += input
                    merger.addSource(input)
                }

                merger.destinationStream = output
                merger.mergeDocuments(null)

                saveFile(output.toByteArray(), fileName)
            } finally {
                openedStreams.forEach { stream ->
                    try {
                        stream.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    suspend fun splitPdf(
        sourceUri: Uri,
        baseFileName: String,
        pagesPerSplit: Int
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        runCatching {
            if (pagesPerSplit <= 0) throw IllegalArgumentException("Pages per split must be greater than 0")

            val source = loadPdf(sourceUri)
            try {
                if (source.numberOfPages == 0) throw IllegalArgumentException("Selected PDF has no pages")

                val outputs = mutableListOf<Uri>()
                var partIndex = 1
                var start = 0

                while (start < source.numberOfPages) {
                    val end = min(start + pagesPerSplit, source.numberOfPages)
                    val chunk = PDDocument()
                    try {
                        for (pageIndex in start until end) {
                            chunk.importPage(source.getPage(pageIndex))
                        }
                        outputs += saveDocument(chunk, "${baseFileName}_part_$partIndex")
                    } finally {
                        chunk.close()
                    }
                    start = end
                    partIndex++
                }

                outputs
            } finally {
                source.close()
            }
        }
    }

    suspend fun extractPages(
        sourceUri: Uri,
        fileName: String,
        pageSpec: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val source = loadPdf(sourceUri)
            try {
                val pages = parsePageSpec(pageSpec, source.numberOfPages)
                if (pages.isEmpty()) throw IllegalArgumentException("No valid pages selected")

                val output = PDDocument()
                try {
                    pages.forEach { pageIndex ->
                        output.importPage(source.getPage(pageIndex))
                    }
                    saveDocument(output, fileName)
                } finally {
                    output.close()
                }
            } finally {
                source.close()
            }
        }
    }

    suspend fun redactPdfRegion(
        sourceUri: Uri,
        fileName: String,
        pageNumber: Int,
        leftPercent: Float,
        topPercent: Float,
        widthPercent: Float,
        heightPercent: Float
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val document = loadPdf(sourceUri)
            try {
                if (pageNumber !in 1..document.numberOfPages) {
                    throw IllegalArgumentException("Invalid page number")
                }

                val page = document.getPage(pageNumber - 1)
                val box = page.mediaBox

                val rectX = box.lowerLeftX + (box.width * leftPercent)
                val rectWidth = (box.width * widthPercent).coerceAtLeast(8f)
                val rectHeight = (box.height * heightPercent).coerceAtLeast(8f)

                val topOffset = box.height * topPercent
                val rectY = box.upperRightY - topOffset - rectHeight

                val stream = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )
                try {
                    stream.setNonStrokingColor(0f, 0f, 0f)
                    stream.addRect(rectX, rectY, rectWidth, rectHeight)
                    stream.fill()
                } finally {
                    stream.close()
                }

                saveDocument(document, fileName)
            } catch (e: Exception) {
                document.close()
                throw e
            }
        }
    }

    private fun applyEncryption(document: PDDocument, password: String) {
        val permission = AccessPermission()
        val policy = StandardProtectionPolicy(password, password, permission)
        policy.encryptionKeyLength = 128
        document.protect(policy)
    }

    private fun loadPdf(uri: Uri): PDDocument {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot read selected PDF")
        return stream.use { PDDocument.load(it) }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e("PdfRepository", "Error loading bitmap from URI: $uri", e)
            null
        }
    }

    private fun Bitmap.prepareForPdf(autoEnhance: Boolean, highQuality: Boolean): Bitmap {
        val scaled = if (highQuality) {
            this
        } else {
            val maxDimension = 1600
            val longestSide = maxOf(width, height)
            if (longestSide <= maxDimension) {
                this
            } else {
                val scale = maxDimension.toFloat() / longestSide.toFloat()
                Bitmap.createScaledBitmap(
                    this,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
        }

        if (!autoEnhance) return scaled

        val enhanced = Bitmap.createBitmap(
            scaled.width,
            scaled.height,
            Bitmap.Config.ARGB_8888
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val matrix = ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.08f, 0f, 0f, 0f, 8f,
                        0f, 1.08f, 0f, 0f, 8f,
                        0f, 0f, 1.08f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            colorFilter = ColorMatrixColorFilter(matrix)
        }

        Canvas(enhanced).drawBitmap(scaled, 0f, 0f, paint)

        if (scaled !== this) scaled.recycle()
        return enhanced
    }

    private fun parsePageSpec(spec: String, pageCount: Int): List<Int> {
        val result = linkedSetOf<Int>()

        spec.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { token ->
                if (token.contains("-")) {
                    val parts = token.split("-").map { it.trim() }
                    if (parts.size != 2) throw IllegalArgumentException("Invalid range: $token")
                    val start = parts[0].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid page: ${parts[0]}")
                    val end = parts[1].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid page: ${parts[1]}")
                    val rangeStart = min(start, end)
                    val rangeEnd = maxOf(start, end)
                    for (page in rangeStart..rangeEnd) {
                        if (page in 1..pageCount) result += (page - 1)
                    }
                } else {
                    val page = token.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid page: $token")
                    if (page in 1..pageCount) result += (page - 1)
                }
            }

        return result.toList()
    }

    private fun saveDocument(document: PDDocument, name: String): Uri {
        val outputStream = ByteArrayOutputStream()
        document.save(outputStream)
        document.close()
        return saveFile(outputStream.toByteArray(), name)
    }

    private fun saveFile(bytes: ByteArray, name: String): Uri {
        val cleanName = name.trim().replace(" ", "_")
        val pdfName = if (cleanName.endsWith(".pdf")) cleanName else "$cleanName.pdf"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pdfName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/SecureScanner"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val uri = context.contentResolver.insert(collection, values)
                ?: error("MediaStore insert failed")

            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            values.put(MediaStore.MediaColumns.SIZE, bytes.size)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SecureScanner"
            )
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, pdfName)
            FileOutputStream(file).use { it.write(bytes) }
            Uri.fromFile(file)
        }
    }
}
