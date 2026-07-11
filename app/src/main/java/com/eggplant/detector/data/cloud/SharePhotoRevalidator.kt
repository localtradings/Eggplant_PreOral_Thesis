package com.eggplant.detector.data.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.eggplant.detector.detection.api.DetectionEngine
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionGate
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.api.RgbFrame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface SharePhotoRevalidator {
    suspend fun revalidate(photoPath: String, expectedDiseaseId: String): Float?
}

class NcnnSharePhotoRevalidator(
    private val engine: DetectionEngine,
) : SharePhotoRevalidator {
    override suspend fun revalidate(photoPath: String, expectedDiseaseId: String): Float? =
        withContext(Dispatchers.Default) {
            val photo = File(photoPath)
            check(photo.isFile) { "The share photo is unavailable." }
            check(engine.initialize() == EngineState.READY) { "The detection model is unavailable." }
            val bitmap = decodeSampledBitmap(photo)
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val rgb = ByteArray(pixels.size * 3)
                var offset = 0
                pixels.forEach { color ->
                    rgb[offset++] = (color shr 16 and 0xff).toByte()
                    rgb[offset++] = (color shr 8 and 0xff).toByte()
                    rgb[offset++] = (color and 0xff).toByte()
                }
                val frame = RgbFrame(
                    width = bitmap.width,
                    height = bitmap.height,
                    rgbBytes = rgb,
                    timestampMillis = SystemClock.elapsedRealtime(),
                    source = InputSource.CAPTURE,
                    sceneToken = 0L,
                )
                ShareRevalidationPolicy.acceptedConfidence(
                    engine.detect(frame).getOrThrow(),
                    expectedDiseaseId,
                )
            } finally {
                bitmap.recycle()
            }
        }
}

internal object ShareRevalidationPolicy {
    private const val MINIMUM_SHARE_CONFIDENCE = 0.50f

    fun acceptedConfidence(frame: DetectionFrame, expectedDiseaseId: String): Float? =
        DetectionGate.filter(frame.copy(source = InputSource.CAPTURE)).detections
            .asSequence()
            .filter { it.modelClass.diseaseId == expectedDiseaseId }
            .maxOfOrNull { it.confidence }
            ?.takeIf { it >= MINIMUM_SHARE_CONFIDENCE }
}

private fun decodeSampledBitmap(photo: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photo.absolutePath, bounds)
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "The share photo is not a decodable image." }
    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_SHARE_IMAGE_DIMENSION) {
        sampleSize *= 2
    }
    return checkNotNull(
        BitmapFactory.decodeFile(
            photo.absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            },
        ),
    ) { "The share photo could not be decoded." }
}

private const val MAX_SHARE_IMAGE_DIMENSION = 1_024
