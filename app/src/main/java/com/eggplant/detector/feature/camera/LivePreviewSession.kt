package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import java.util.concurrent.atomic.AtomicLong

sealed interface LivePreviewOutcome {
    data class Disease(val scene: CameraScene, val primary: DetectionBox) : LivePreviewOutcome
    data class Healthy(val scene: CameraScene, val primary: DetectionBox) : LivePreviewOutcome
    data object NoStableDetection : LivePreviewOutcome
}

internal class LivePreviewSession {
    private val tokenGenerator = AtomicLong(0L)
    private var activeToken = 0L
    private var bestDiseaseScene: CameraScene? = null
    private var bestDisease: DetectionBox? = null
    private var bestHealthyScene: CameraScene? = null
    private var bestHealthy: DetectionBox? = null

    fun start(): Long {
        activeToken = tokenGenerator.incrementAndGet()
        bestDiseaseScene = null
        bestDisease = null
        bestHealthyScene = null
        bestHealthy = null
        return activeToken
    }

    fun record(token: Long, scene: CameraScene) {
        if (token != activeToken || activeToken == 0L) return
        scene.stability.confirmedDetections
            .filterNot { it.modelClass.isHealthy }
            .maxByOrNull { it.confidence }
            ?.let { confirmedDisease ->
                val previous = bestDisease
                if (previous == null || confirmedDisease.confidence > previous.confidence) {
                    bestDisease = confirmedDisease
                    bestDiseaseScene = scene
                }
            }
        scene.stability.confirmedDetections
            .filter { it.modelClass.isHealthy }
            .maxByOrNull { it.confidence }
            ?.let { confirmedHealthy ->
                val previous = bestHealthy
                if (previous == null || confirmedHealthy.confidence > previous.confidence) {
                    bestHealthy = confirmedHealthy
                    bestHealthyScene = scene
                }
            }
    }

    fun stop(allowHealthy: Boolean): LivePreviewOutcome {
        val disease = bestDisease
        val diseaseScene = bestDiseaseScene
        val healthy = bestHealthy
        val healthyScene = bestHealthyScene
        activeToken = 0L
        bestDisease = null
        bestDiseaseScene = null
        bestHealthy = null
        bestHealthyScene = null
        if (disease != null && diseaseScene != null) {
            return LivePreviewOutcome.Disease(diseaseScene, disease)
        }
        if (allowHealthy && healthy != null && healthyScene != null) {
            return LivePreviewOutcome.Healthy(healthyScene, healthy)
        }
        return LivePreviewOutcome.NoStableDetection
    }

    /** Cancels without exposing a retained result to a stale UI callback. */
    fun cancel() {
        activeToken = 0L
        bestDiseaseScene = null
        bestDisease = null
        bestHealthyScene = null
        bestHealthy = null
    }

    /** A low-quality frame must not let an older healthy confirmation win on release. */
    fun discardHealthy() {
        bestHealthyScene = null
        bestHealthy = null
    }
}
