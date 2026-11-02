package com.example.securescan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfTextMatch(
    val pageIndex: Int,
    val excerpt: String
)

object PdfUtils {

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun renderPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        width: Int = 1080,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext null
            val renderer = PdfRenderer(pfd)
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return@withContext null
            }

            val page = renderer.openPage(pageIndex)
            val height = (page.height.toFloat() / page.width.toFloat() * width).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchTextInBitmaps(
        pages: List<Bitmap?>,
        query: String
    ): List<PdfTextMatch> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val lowerQuery = query.lowercase()
        val matches = mutableListOf<PdfTextMatch>()

        try {
            pages.forEachIndexed { index, bitmap ->
                if (bitmap == null) return@forEachIndexed

                try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = Tasks.await(recognizer.process(image))
                    val text = result.text
                    val lowerText = text.lowercase()
                    val matchIndex = lowerText.indexOf(lowerQuery)

                    if (matchIndex >= 0) {
                        val start = (matchIndex - 40).coerceAtLeast(0)
                        val end = (matchIndex + query.length + 40).coerceAtMost(text.length)
                        val excerpt = text.substring(start, end)
                            .trim()
                            .ifBlank { "Match found on this page" }

                        matches += PdfTextMatch(
                            pageIndex = index,
                            excerpt = excerpt
                        )
                    }
                } catch (_: Exception) {
                }
            }
        } finally {
            recognizer.close()
        }

        matches
    }

    fun getPageCount(
        context: Context,
        uri: Uri,
        password: String? = null
    ): Int {
        return try {
            if (password != null) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return 0
                val doc = PDDocument.load(stream, password)
                val count = doc.numberOfPages
                doc.close()
                stream.close()
                count
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return 0
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                renderer.close()
                pfd.close()
                count
            }
        } catch (e: Exception) {
            0
        }
    }

    fun isPasswordProtected(context: Context, uri: Uri): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return false
            val renderer = PdfRenderer(pfd)
            renderer.close()
            pfd.close()
            false
        } catch (e: Exception) {
            true
        }
    }
}
