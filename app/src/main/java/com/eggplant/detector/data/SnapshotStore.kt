package com.eggplant.detector.data

import android.content.Context
import android.graphics.Bitmap
import com.eggplant.detector.detection.RgbFrame
import java.io.File
import java.util.UUID

class SnapshotStore(context: Context) {
    private val cacheRoot = File(context.cacheDir, "pending-scans")
    private val savedRoot = File(context.noBackupFilesDir, "scan-images")

    fun stage(frame: RgbFrame): String {
        cacheRoot.mkdirs()
        val destination = File(cacheRoot, "${UUID.randomUUID()}.jpg")
        val colors = IntArray(frame.width * frame.height)
        var source = 0
        colors.indices.forEach { index ->
            val red = frame.rgbBytes[source++].toInt() and 0xff
            val green = frame.rgbBytes[source++].toInt() and 0xff
            val blue = frame.rgbBytes[source++].toInt() and 0xff
            colors[index] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val bitmap = Bitmap.createBitmap(colors, frame.width, frame.height, Bitmap.Config.ARGB_8888)
        try {
            destination.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "Could not encode scan snapshot."
                }
            }
        } finally {
            bitmap.recycle()
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
}
