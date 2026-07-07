package com.eggplant.detector.detection.api

import com.eggplant.detector.detection.ncnn.ModelMetadata

data class DetectionGateDecision(
    val accepted: Boolean,
    val reason: String,
)

object DetectionGate {
    const val LIVE_CONFIDENCE_THRESHOLD = 0.15f
    const val CAPTURE_CONFIDENCE_THRESHOLD = 0.20f
    const val GALLERY_CONFIDENCE_THRESHOLD = 0.25f
    const val FRUIT_BORER_CONFIDENCE_THRESHOLD = 0.35f
    const val LIVE_MIN_BOX_AREA = 0.0025f
    const val LIVE_MAX_BOX_AREA = 0.90f
    const val STILL_MIN_BOX_AREA = 0.005f
    const val STILL_MAX_BOX_AREA = 0.85f

    fun filter(frame: DetectionFrame, onRejected: ((DetectionBox, DetectionGateDecision) -> Unit)? = null): DetectionFrame {
        val accepted = frame.detections.filter { detection ->
            val decision = evaluate(detection, frame.source)
            if (!decision.accepted) onRejected?.invoke(detection, decision)
            decision.accepted
        }
        return frame.copy(detections = accepted)
    }

    fun evaluate(detection: DetectionBox, source: InputSource): DetectionGateDecision {
        val sourceThreshold = thresholdFor(source)
        val fruitBorerThreshold = if (detection.modelClass.index == ModelMetadata.FRUIT_BORER_CLASS_INDEX) {
            FRUIT_BORER_CONFIDENCE_THRESHOLD
        } else {
            null
        }
        val requiredConfidence = fruitBorerThreshold ?: sourceThreshold
        if (detection.confidence < requiredConfidence) {
            return DetectionGateDecision(
                accepted = false,
                reason = if (fruitBorerThreshold != null) {
                    "confidence_below_fruit_borer_override"
                } else {
                    "confidence_below_${source.name.lowercase()}_threshold"
                },
            )
        }

        val area = detection.bounds.area()
        val (minArea, maxArea) = areaLimitsFor(source)
        return when {
            area < minArea -> DetectionGateDecision(false, "box_area_too_small")
            area > maxArea -> DetectionGateDecision(false, "box_area_too_large")
            else -> DetectionGateDecision(true, "accepted")
        }
    }

    fun thresholdFor(source: InputSource): Float = when (source) {
        InputSource.LIVE -> LIVE_CONFIDENCE_THRESHOLD
        InputSource.CAPTURE -> CAPTURE_CONFIDENCE_THRESHOLD
        InputSource.GALLERY -> GALLERY_CONFIDENCE_THRESHOLD
    }

    fun areaLimitsFor(source: InputSource): Pair<Float, Float> = when (source) {
        InputSource.LIVE -> LIVE_MIN_BOX_AREA to LIVE_MAX_BOX_AREA
        InputSource.CAPTURE,
        InputSource.GALLERY -> STILL_MIN_BOX_AREA to STILL_MAX_BOX_AREA
    }

    private fun NormalizedBox.area(): Float = (right - left) * (bottom - top)
}
