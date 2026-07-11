package com.eggplant.detector.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.eggplant.detector.BuildConfig
import com.eggplant.detector.detection.api.RgbFrame
import java.io.File
import java.util.UUID
import androidx.exifinterface.media.ExifInterface
import kotlin.math.roundToInt

class ScanSnapshotStore(context: Context) {
    private val appContext = context.applicationContext
    private val cacheRoot = File(context.cacheDir, "pending-scans")
    private val savedRoot = File(context.noBackupFilesDir, "scan-images")
    private val outboxRoot = File(context.noBackupFilesDir, "cloud-outbox")

    fun stage(frame: RgbFrame): String {
        val totalStartedAt = SystemClock.elapsedRealtimeNanos()
        cacheRoot.mkdirs()
        val destination = File(cacheRoot, "${UUID.randomUUID()}.jpg")
        val conversionStartedAt = SystemClock.elapsedRealtimeNanos()
        val bitmap = debugTrace("eggplant.snapshot.convert") {
            val colors = IntArray(frame.width * frame.height)
            var source = 0
            colors.indices.forEach { index ->
                val red = frame.rgbBytes[source++].toInt() and 0xff
                val green = frame.rgbBytes[source++].toInt() and 0xff
                val blue = frame.rgbBytes[source++].toInt() and 0xff
                colors[index] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
            Bitmap.createBitmap(colors, frame.width, frame.height, Bitmap.Config.ARGB_8888)
        }
        val conversionMillis = elapsedMillis(conversionStartedAt)
        val encodeStartedAt = SystemClock.elapsedRealtimeNanos()
        try {
            debugTrace("eggplant.snapshot.encode") {
                destination.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                        "Could not encode scan snapshot."
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                LOG_TAG,
                "snapshot_stage frame=${frame.width}x${frame.height} " +
                    "conversion_ms=$conversionMillis encode_ms=${elapsedMillis(encodeStartedAt)} " +
                    "total_ms=${elapsedMillis(totalStartedAt)} bytes=${destination.length()}",
            )
        }
        return destination.absolutePath
    }

    fun stageRequestPhoto(uri: Uri): String {
        cacheRoot.mkdirs()
        val destination = File(cacheRoot, "request-${UUID.randomUUID()}.jpg")
        val bitmap = decodeImportedBitmap(uri)
        try {
            destination.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "Could not encode the selected request photo."
                }
            }
            check(destination.length() in 1..MAXIMUM_REQUEST_PHOTO_BYTES) {
                "The selected request photo is too large."
            }
            return destination.absolutePath
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    fun commit(stagedPath: String?, sessionId: String): String? {
        if (stagedPath == null) return null
        val staged = File(stagedPath).canonicalFile
        check(staged.isFile && staged.parentFile == cacheRoot.canonicalFile) {
            "Snapshot is not a staged app-private file."
        }
        savedRoot.mkdirs()
        val destination = File(savedRoot, "$sessionId.jpg")
        check(!destination.exists()) { "A snapshot already exists for this session." }
        val temporary = File(savedRoot, "$sessionId.tmp")
        staged.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
        check(temporary.renameTo(destination)) { "Could not commit scan snapshot." }
        staged.delete()
        return destination.absolutePath
    }

    fun discard(path: String?) {
        if (path == null) return
        val file = File(path).canonicalFile
        if (file.parentFile == cacheRoot.canonicalFile) file.delete()
    }

    fun removeCommitted(path: String?) {
        if (path == null) return
        val file = File(path).canonicalFile
        if (file.parentFile == savedRoot.canonicalFile) file.delete()
    }

    fun copyForOutbox(sourcePath: String, purpose: String, id: String, position: Int = 0): String {
        require(purpose in setOf("global", "request")) { "Unsupported outbox purpose." }
        val source = File(sourcePath).canonicalFile
        check(source.isFile && (source.parentFile == cacheRoot.canonicalFile || source.parentFile == savedRoot.canonicalFile)) {
            "Cloud photo must be an app-private scan image."
        }
        val destinationRoot = File(outboxRoot, purpose).apply { mkdirs() }.canonicalFile
        val destination = File(destinationRoot, "$id-$position.jpg")
        val temporary = File(destinationRoot, "$id-$position.tmp")
        source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
        check(temporary.renameTo(destination)) { "Could not stage cloud photo." }
        return destination.absolutePath
    }

    fun removeOutboxPhoto(path: String?) {
        if (path == null) return
        val file = File(path).canonicalFile
        if (file.toPath().startsWith(outboxRoot.canonicalFile.toPath())) file.delete()
    }

    private inline fun <T> debugTrace(name: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000

    private fun decodeImportedBitmap(uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, info, _ ->
                validateSourceDimensions(info.size.width, info.size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDimension = maxOf(info.size.width, info.size.height)
                if (maxDimension > MAXIMUM_IMPORTED_DIMENSION) {
                    val scale = MAXIMUM_IMPORTED_DIMENSION.toFloat() / maxDimension
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            validateSourceDimensions(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAXIMUM_IMPORTED_DIMENSION) sample *= 2
            val decoded = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("The selected request photo could not be decoded.")
            val rotation = appContext.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            if (rotation == 0) decoded else Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true,
            ).also { decoded.recycle() }
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
        }
    }

    private fun validateSourceDimensions(width: Int, height: Int) {
        check(width > 0 && height > 0) { "The selected request photo is invalid." }
        check(width.toLong() * height.toLong() <= MAXIMUM_IMPORTED_PIXELS) {
            "The selected request photo has unsafe dimensions."
        }
    }

    private companion object {
        const val LOG_TAG = "EggplantDetection"
        const val MAXIMUM_IMPORTED_DIMENSION = 2_048
        const val MAXIMUM_IMPORTED_PIXELS = 100_000_000L
        const val MAXIMUM_REQUEST_PHOTO_BYTES = 8_388_608L
    }
}
