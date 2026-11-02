package com.example.securescan.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageToolsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun rotateFlipImage(
        sourceUri: Uri,
        fileName: String,
        rotation: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadBitmap(sourceUri) ?: error("Unable to load image")
            val matrix = Matrix()

            val scaleX = if (flipHorizontal) -1f else 1f
            val scaleY = if (flipVertical) -1f else 1f
            matrix.postScale(scaleX, scaleY, bitmap.width / 2f, bitmap.height / 2f)

            if (rotation != 0) {
                matrix.postRotate(rotation.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
            }

            val transformed = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            if (transformed != bitmap) bitmap.recycle()

            saveBitmap(transformed, fileName, sourceUri)
        }
    }

    suspend fun resizeImage(
        sourceUri: Uri,
        fileName: String,
        width: Int,
        height: Int
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (width <= 0 || height <= 0) error("Width and height must be greater than 0")
            val bitmap = loadBitmap(sourceUri) ?: error("Unable to load image")
            val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (resized != bitmap) bitmap.recycle()
            saveBitmap(resized, fileName, sourceUri)
        }
    }

    suspend fun cropImage(
        sourceUri: Uri,
        fileName: String,
        leftPercent: Float,
        topPercent: Float,
        widthPercent: Float,
        heightPercent: Float
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadBitmap(sourceUri) ?: error("Unable to load image")

            val cropLeft = (bitmap.width * leftPercent).toInt().coerceIn(0, bitmap.width - 1)
            val cropTop = (bitmap.height * topPercent).toInt().coerceIn(0, bitmap.height - 1)
            val cropWidth = max(1, (bitmap.width * widthPercent).toInt())
            val cropHeight = max(1, (bitmap.height * heightPercent).toInt())

            val safeWidth = cropWidth.coerceAtMost(bitmap.width - cropLeft)
            val safeHeight = cropHeight.coerceAtMost(bitmap.height - cropTop)

            val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, safeWidth, safeHeight)
            if (cropped != bitmap) bitmap.recycle()

            saveBitmap(cropped, fileName, sourceUri)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }

    private fun saveBitmap(
        bitmap: Bitmap,
        baseName: String,
        sourceUri: Uri
    ): Uri {
        val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
        val extension = if (mimeType == "image/png") "png" else "jpg"
        val displayName = if (baseName.endsWith(".$extension")) baseName else "$baseName.$extension"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SecureScanner")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: error("Failed to create output file")

            context.contentResolver.openOutputStream(uri)?.use { out ->
                if (mimeType == "image/png") {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            bitmap.recycle()
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "SecureScanner"
            )
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, displayName)
            FileOutputStream(file).use { out ->
                if (mimeType == "image/png") {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            bitmap.recycle()
            Uri.fromFile(file)
        }
    }
}
