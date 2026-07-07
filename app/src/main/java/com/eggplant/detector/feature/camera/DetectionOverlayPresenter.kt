package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import kotlin.math.roundToInt

internal enum class OverlayPhase {
    TENTATIVE,
    CONFIRMED,
}

internal data class OverlayDetection(
    val detection: DetectionBox,
    val phase: OverlayPhase,
    val label: String?,
)

internal fun presentOverlayDetections(
    visible: List<DetectionBox>,
    confirmed: List<DetectionBox>,
    displayName: (DetectionBox) -> String,
): List<OverlayDetection> = confirmed.filter { detection ->
    visible.isEmpty() || visible.any { candidate ->
        candidate.modelClass.index == detection.modelClass.index &&
            candidate.bounds.intersectionOverUnion(detection.bounds) >= CONFIRMED_MINIMUM_IOU
    }
}.map { detection ->
    OverlayDetection(
        detection = detection,
        phase = OverlayPhase.CONFIRMED,
        label = "${displayName(detection)} · ${(detection.confidence * 100).roundToInt()}%",
    )
}

private const val CONFIRMED_MINIMUM_IOU = 0.5f
