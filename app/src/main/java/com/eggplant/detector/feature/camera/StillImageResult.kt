package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionStatus

sealed interface StillImageResult {
    val scene: CameraScene?

    data class Disease(
        override val scene: CameraScene,
        val primary: DetectionBox,
    ) : StillImageResult

    data class Healthy(override val scene: CameraScene) : StillImageResult

    data class NoMatch(override val scene: CameraScene) : StillImageResult

    data class Failure(val message: String) : StillImageResult {
        override val scene: CameraScene? = null
    }
}

fun Result<CameraScene>.toStillImageResult(fallbackError: String): StillImageResult = fold(
    onSuccess = { scene ->
        val primary = scene.stability.stableDetections.maxByOrNull { it.confidence }
        when {
            primary != null -> StillImageResult.Disease(scene, primary)
            scene.stability.status == DetectionStatus.HEALTHY -> StillImageResult.Healthy(scene)
            else -> StillImageResult.NoMatch(scene)
        }
    },
    onFailure = { error -> StillImageResult.Failure(error.message ?: fallbackError) },
)
