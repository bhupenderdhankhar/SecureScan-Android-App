package com.example.securescan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrPageResult(
    val pageIndex: Int,
    val extractedText: String,
    val bitmap: Bitmap
)

object OcrUtils {

    suspend fun extractTextFromPdf(
        context: Context,
        uri: Uri,
        maxPages: Int = 10
    ): Result<List<OcrPageResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<OcrPageResult>()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open PDF")
            val renderer = PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, maxPages)

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                val result = Tasks.await(recognizer.process(image))
                results.add(OcrPageResult(i, result.text, bitmap))
            }

            renderer.close()
            pfd.close()
            recognizer.close()
            results
        }
    }

    suspend fun isScannedPdf(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext false
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount == 0) {
                    renderer.close(); pfd.close()
                    return@withContext false
                }
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(
                    page.width, page.height, Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()

                // Check if page is mostly image (high unique color count = scanned)
                var nonWhitePixels = 0
                val totalPixels = bitmap.width * bitmap.height
                for (x in 0 until bitmap.width step 10) {
                    for (y in 0 until bitmap.height step 10) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        if (r < 240 || g < 240 || b < 240) nonWhitePixels++
                    }
                }
                bitmap.recycle()
                val ratio = nonWhitePixels.toFloat() / (totalPixels / 100)
                ratio > 5f
            } catch (e: Exception) {
                false
            }
        }
}