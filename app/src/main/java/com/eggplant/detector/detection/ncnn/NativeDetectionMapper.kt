package com.eggplant.detector.detection.ncnn

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.NormalizedBox

object NativeDetectionMapper {
    private const val VALUES_PER_DETECTION = 6

    fun map(
        values: FloatArray,
        imageWidth: Int,
        imageHeight: Int,
        metadata: ModelMetadata = ModelMetadata.EGGPLANT_YOLO26M,
    ): List<DetectionBox> {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }
        require(values.size % VALUES_PER_DETECTION == 0) { "Native detections must contain six values each." }
        return values.asList().chunked(VALUES_PER_DETECTION).mapNotNull { row ->
            val modelClass = metadata.classFor(row[0].toInt()) ?: return@mapNotNull null
            val confidence = row[1]
            if (!confidence.isFinite() || confidence < metadata.confidenceThreshold || confidence > 1f) {
                return@mapNotNull null
            }
            val left = row[2].coerceIn(0f, imageWidth.toFloat()) / imageWidth
            val top = row[3].coerceIn(0f, imageHeight.toFloat()) / imageHeight
            val right = row[4].coerceIn(0f, imageWidth.toFloat()) / imageWidth
            val bottom = row[5].coerceIn(0f, imageHeight.toFloat()) / imageHeight
            if (right <= left || bottom <= top) return@mapNotNull null
            DetectionBox(modelClass, confidence, NormalizedBox(left, top, right, bottom))
        }
    }
}
