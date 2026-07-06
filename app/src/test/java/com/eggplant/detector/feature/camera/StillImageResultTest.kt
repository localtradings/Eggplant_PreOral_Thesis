package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StillImageResultTest {
    @Test
    fun `disease scene selects the highest confidence disease`() {
        val lower = detection(classIndex = 5, confidence = 0.42f)
        val higher = detection(classIndex = 9, confidence = 0.81f)
        val scene = scene(DetectionStatus.DISEASE_DETECTED, lower, higher)

        val outcome = Result.success(scene).toStillImageResult("fallback")

        assertTrue(outcome is StillImageResult.Disease)
        assertSame(higher, (outcome as StillImageResult.Disease).primary)
        assertSame(scene, outcome.scene)
    }

    @Test
    fun `healthy scene remains a successful observable outcome`() {
        val healthyLeaf = detection(classIndex = 2, confidence = 0.91f)
        val scene = scene(DetectionStatus.HEALTHY, healthyLeaf, stableDiseases = emptyList())

        val outcome = Result.success(scene).toStillImageResult("fallback")

        assertTrue(outcome is StillImageResult.Healthy)
        assertSame(scene, outcome.scene)
        assertSame(healthyLeaf, (outcome as StillImageResult.Healthy).primary)
    }

    @Test
    fun `empty scene reports no supported match`() {
        val scene = scene(DetectionStatus.SEARCHING)

        val outcome = Result.success(scene).toStillImageResult("fallback")

        assertTrue(outcome is StillImageResult.NoMatch)
        assertSame(scene, outcome.scene)
    }

    @Test
    fun `inference failure keeps its useful message`() {
        val outcome = Result.failure<CameraScene>(IllegalStateException("model unavailable"))
            .toStillImageResult("fallback")

        assertEquals("model unavailable", (outcome as StillImageResult.Failure).message)
    }

    private fun scene(
        status: DetectionStatus,
        vararg detections: DetectionBox,
        stableDiseases: List<DetectionBox> = detections.toList(),
    ): CameraScene {
        val rgb = RgbFrame(1, 1, byteArrayOf(0, 0, 0), 1L, InputSource.GALLERY, 1L)
        val frame = DetectionFrame(detections.toList(), 1L, 10L, InputSource.GALLERY, 1L)
        return CameraScene(
            rgbFrame = rgb,
            detectionFrame = frame,
            stability = StabilityResult(
                status = status,
                stableDetections = stableDiseases,
                visibleDetections = detections.toList(),
                saveEligible = stableDiseases.isNotEmpty(),
                confirmedDetections = detections.toList(),
            ),
        )
    }

    private fun detection(classIndex: Int, confidence: Float) = DetectionBox(
        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classes[classIndex],
        confidence = confidence,
        bounds = NormalizedBox(0.1f, 0.1f, 0.8f, 0.8f),
    )
}
