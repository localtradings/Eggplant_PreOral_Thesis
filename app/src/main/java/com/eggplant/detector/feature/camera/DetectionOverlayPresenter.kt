package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox

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
): List<OverlayDetection> {
    val confirmedItems = confirmed.filter { detection ->
        visible.isEmpty() || visible.any { candidate -> candidate.matchesConfirmed(detection) }
    }.map { detection ->
        OverlayDetection(
            detection = detection,
            phase = OverlayPhase.CONFIRMED,
            label = displayName(detection),
        )
    }
    val tentativeItems = visible.filter { detection ->
        confirmedItems.none { item -> detection.matchesConfirmed(item.detection) }
    }.map { detection ->
        OverlayDetection(
            detection = detection,
            phase = OverlayPhase.TENTATIVE,
            label = null,
        )
    }
    return tentativeItems + confirmedItems
}

private fun DetectionBox.matchesConfirmed(confirmed: DetectionBox): Boolean =
    modelClass.index == confirmed.modelClass.index &&
        bounds.intersectionOverUnion(confirmed.bounds) >= CONFIRMED_MINIMUM_IOU

private const val CONFIRMED_MINIMUM_IOU = 0.5f
