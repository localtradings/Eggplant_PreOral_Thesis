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
): List<OverlayDetection> = visible.map { detection ->
    val isConfirmed = confirmed.any { stable ->
        stable.modelClass.index == detection.modelClass.index &&
            stable.bounds.intersectionOverUnion(detection.bounds) >= CONFIRMED_MINIMUM_IOU
    }
    OverlayDetection(
        detection = detection,
        phase = if (isConfirmed) OverlayPhase.CONFIRMED else OverlayPhase.TENTATIVE,
        label = if (isConfirmed) {
            "${displayName(detection)} · ${(detection.confidence * 100).roundToInt()}%"
        } else {
            null
        },
    )
}

private const val CONFIRMED_MINIMUM_IOU = 0.5f
