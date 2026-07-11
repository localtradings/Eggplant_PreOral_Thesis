package com.eggplant.detector.data.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object SafeJpeg {
    fun validate(file: File): Boolean {
        if (!file.isFile || file.length() !in 4..MAXIMUM_JPEG_BYTES) return false
        val signature = file.inputStream().use { input -> ByteArray(3).also { input.read(it) } }
        if (signature[0] != 0xff.toByte() || signature[1] != 0xd8.toByte() || signature[2] != 0xff.toByte()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        if (bounds.outWidth > MAXIMUM_JPEG_DIMENSION || bounds.outHeight > MAXIMUM_JPEG_DIMENSION) return false
        return bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAXIMUM_JPEG_PIXELS
    }

    fun decodeSampled(file: File, targetMaximumDimension: Int): Bitmap? {
        if (targetMaximumDimension <= 0 || !validate(file)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > targetMaximumDimension) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            },
        )
    }
}

const val MAXIMUM_JPEG_BYTES = 8_388_608L
private const val MAXIMUM_JPEG_DIMENSION = 8_192
private const val MAXIMUM_JPEG_PIXELS = 32_000_000L
