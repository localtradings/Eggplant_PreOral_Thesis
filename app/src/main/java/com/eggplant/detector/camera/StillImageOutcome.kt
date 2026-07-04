package com.eggplant.detector.camera

import com.eggplant.detector.detection.DetectionBox
import com.eggplant.detector.detection.DetectionStatus

sealed interface StillImageOutcome {
    val scene: CameraScene?

    data class Disease(
        override val scene: CameraScene,
        val primary: DetectionBox,
    ) : StillImageOutcome

    data class Healthy(override val scene: CameraScene) : StillImageOutcome

    data class NoMatch(override val scene: CameraScene) : StillImageOutcome

    data class Failure(val message: String) : StillImageOutcome {
        override val scene: CameraScene? = null
    }
}

fun Result<CameraScene>.toStillImageOutcome(fallbackError: String): StillImageOutcome = fold(
    onSuccess = { scene ->
        val primary = scene.stability.stableDetections.maxByOrNull { it.confidence }
        when {
            primary != null -> StillImageOutcome.Disease(scene, primary)
            scene.stability.status == DetectionStatus.HEALTHY -> StillImageOutcome.Healthy(scene)
            else -> StillImageOutcome.NoMatch(scene)
        }
    },
    onFailure = { error -> StillImageOutcome.Failure(error.message ?: fallbackError) },
)
