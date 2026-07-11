package com.eggplant.detector.detection.tracking

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.StabilityResult
import com.eggplant.detector.detection.ncnn.ModelMetadata

class DetectionStabilityTracker(
    private val metadata: ModelMetadata = ModelMetadata.EGGPLANT_YOLO26M,
    private val minimumFrames: Int = 2,
    private val minimumStableMillis: Long = 0,
    private val minimumIoU: Float = 0.3f,
    private val maximumInterFrameGapMillis: Long = 750,
    private val confirmedHoldMillis: Long = 750,
    private val sceneResetMillis: Long = 2_000,
) {
    private data class Track(
        val detection: DetectionBox,
        val firstSeenAt: Long,
        val lastSeenAt: Long,
        val frameCount: Int,
    )

    private var tracks: List<Track> = emptyList()
    private var saveArmed = true
    private var savedSceneToken: Long? = null
    private var currentSceneToken: Long? = null
    private var lastDiseaseSeenAt: Long? = null
    private var currentStableDiseases: List<DetectionBox> = emptyList()
    private var lastConfirmedAt: Long? = null
    private var lastConfirmedDetections: List<DetectionBox> = emptyList()

    fun update(frame: DetectionFrame): StabilityResult {
        val visible = frame.detections.filter { it.confidence >= metadata.confidenceThreshold }
        val availablePrevious = tracks.toMutableList()
        tracks = visible.map { detection ->
            val previous = availablePrevious
                .filter { it.detection.modelClass.index == detection.modelClass.index }
                .maxByOrNull { it.detection.bounds.intersectionOverUnion(detection.bounds) }
                ?.takeIf {
                    frame.timestampMillis - it.lastSeenAt <= maximumInterFrameGapMillis &&
                        it.detection.bounds.intersectionOverUnion(detection.bounds) >= minimumIoU
                }
            if (previous == null) {
                Track(detection, frame.timestampMillis, frame.timestampMillis, frameCount = 1)
            } else {
                availablePrevious.remove(previous)
                Track(
                    detection = detection,
                    firstSeenAt = previous.firstSeenAt,
                    lastSeenAt = frame.timestampMillis,
                    frameCount = previous.frameCount + 1,
                )
            }
        }

        val stable = tracks.filter { track ->
            track.frameCount >= minimumFrames &&
                track.lastSeenAt - track.firstSeenAt >= minimumStableMillis
        }.map(Track::detection)
        val stableDiseases = stable.filterNot { it.modelClass.isHealthy }

        if (visible.any { !it.modelClass.isHealthy }) {
            lastDiseaseSeenAt = frame.timestampMillis
        }
        if (!saveArmed) {
            val isDifferentScene = stableDiseases.isNotEmpty() &&
                savedSceneToken != null &&
                sceneDistance(requireNotNull(savedSceneToken), frame.sceneToken) >= MINIMUM_CHANGED_BLOCKS
            val diseaseHasBeenAbsent = stableDiseases.isEmpty() &&
                lastDiseaseSeenAt?.let { frame.timestampMillis - it >= sceneResetMillis } == true
            if (isDifferentScene || diseaseHasBeenAbsent) {
                saveArmed = true
                savedSceneToken = null
            }
        }

        currentSceneToken = frame.sceneToken
        currentStableDiseases = stableDiseases
        if (stable.isNotEmpty()) {
            lastConfirmedAt = frame.timestampMillis
            lastConfirmedDetections = stable
        }
        val confirmed = when {
            stable.isNotEmpty() -> stable
            lastConfirmedDetections.isNotEmpty() &&
                lastConfirmedAt != null &&
                frame.timestampMillis - requireNotNull(lastConfirmedAt) <= confirmedHoldMillis -> {
                lastConfirmedDetections
            }
            else -> {
                lastConfirmedAt = null
                lastConfirmedDetections = emptyList()
                emptyList()
            }
        }
        val confirmedDiseases = confirmed.filterNot { it.modelClass.isHealthy }
        val confirmedHealthy = confirmed.any { it.modelClass.isHealthy }
        val status = when {
            confirmedDiseases.isNotEmpty() -> DetectionStatus.DISEASE_DETECTED
            confirmedHealthy -> DetectionStatus.HEALTHY
            else -> DetectionStatus.SEARCHING
        }
        return StabilityResult(
            status = status,
            stableDetections = stableDiseases,
            visibleDetections = visible,
            saveEligible = saveArmed && stableDiseases.isNotEmpty(),
            confirmedDetections = confirmed,
        )
    }

    fun markSaved() {
        if (saveArmed && currentStableDiseases.isNotEmpty()) {
            saveArmed = false
            savedSceneToken = currentSceneToken
        }
    }

    fun reset() {
        tracks = emptyList()
        saveArmed = true
        savedSceneToken = null
        currentSceneToken = null
        lastDiseaseSeenAt = null
        currentStableDiseases = emptyList()
        lastConfirmedAt = null
        lastConfirmedDetections = emptyList()
    }

    private fun sceneDistance(first: Long, second: Long): Int {
        var changed = 0
        repeat(16) { index ->
            val shift = index * 4
            if ((first ushr shift and 0xf) != (second ushr shift and 0xf)) changed += 1
        }
        return changed
    }

    private companion object {
        const val MINIMUM_CHANGED_BLOCKS = 6
    }
}
