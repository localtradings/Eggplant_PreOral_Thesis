package com.eggplant.detector.detection.api

import com.eggplant.detector.detection.ncnn.ModelClass

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "Detection bounds must be normalized."
        }
        require(right > left && bottom > top) { "Detection bounds must have positive area." }
    }

    fun intersectionOverUnion(other: NormalizedBox): Float {
        val intersectionLeft = maxOf(left, other.left)
        val intersectionTop = maxOf(top, other.top)
        val intersectionRight = minOf(right, other.right)
        val intersectionBottom = minOf(bottom, other.bottom)
        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val area = (right - left) * (bottom - top)
        val otherArea = (other.right - other.left) * (other.bottom - other.top)
        val union = area + otherArea - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}

data class DetectionBox(
    val modelClass: ModelClass,
    val confidence: Float,
    val bounds: NormalizedBox,
) {
    init {
        require(confidence in 0f..1f) { "Detection confidence must be normalized." }
    }
}

enum class InputSource {
    LIVE,
    CAPTURE,
    GALLERY,
}

data class DetectionFrame(
    val detections: List<DetectionBox>,
    val timestampMillis: Long,
    val inferenceMillis: Long,
    val source: InputSource,
    val sceneToken: Long,
)

enum class DetectionStatus {
    SEARCHING,
    HEALTHY,
    DISEASE_DETECTED,
}

data class StabilityResult(
    val status: DetectionStatus,
    val stableDetections: List<DetectionBox>,
    val visibleDetections: List<DetectionBox>,
    val saveEligible: Boolean,
    val confirmedDetections: List<DetectionBox> = stableDetections,
)
