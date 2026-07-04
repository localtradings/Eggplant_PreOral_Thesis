package com.eggplant.detector.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlin.math.roundToInt

internal fun Context.decodeGalleryBitmap(uri: Uri): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDimension = maxOf(info.size.width, info.size.height)
            if (maxDimension > 1600) {
                val scale = 1600f / maxDimension
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
    } else {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Selected image could not be read.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Selected image could not be decoded.")
        val orientation = contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (orientation == 0) decoded else Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(orientation.toFloat()) },
            true,
        ).also { decoded.recycle() }
    }
    return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
}
