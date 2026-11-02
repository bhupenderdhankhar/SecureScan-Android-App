package com.example.securescan.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageFilter {

    fun toGrayscale(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix().also { it.setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun toBlackAndWhite(src: Bitmap, threshold: Int = 128): Bitmap {
        val gray = toGrayscale(src)
        val result = gray.copy(Bitmap.Config.ARGB_8888, true)
        for (x in 0 until result.width) {
            for (y in 0 until result.height) {
                val px = result.getPixel(x, y)
                val lum = (Color.red(px) * 0.299 + Color.green(px) * 0.587 + Color.blue(px) * 0.114).toInt()
                result.setPixel(x, y, if (lum > threshold) Color.WHITE else Color.BLACK)
            }
        }
        return result
    }

    fun colorBoost(src: Bitmap, saturation: Float = 1.5f): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix().also { it.setSaturation(saturation) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun adjustBrightness(src: Bitmap, factor: Float): Bitmap {
        val matrix = ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, 0f,
            0f, factor, 0f, 0f, 0f,
            0f, 0f, factor, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(src, 0f, 0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return result
    }
}