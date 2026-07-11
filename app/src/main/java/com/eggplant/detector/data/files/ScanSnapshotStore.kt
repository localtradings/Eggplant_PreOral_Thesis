package com.eggplant.detector.data.files

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.eggplant.detector.BuildConfig
import com.eggplant.detector.detection.api.RgbFrame
import java.io.File
import java.util.UUID

class ScanSnapshotStore(context: Context) {
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

    private companion object {
        const val LOG_TAG = "EggplantDetection"
    }
}
