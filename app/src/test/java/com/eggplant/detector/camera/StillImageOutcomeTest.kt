package com.eggplant.detector.camera

import com.eggplant.detector.detection.DetectionBox
import com.eggplant.detector.detection.DetectionFrame
import com.eggplant.detector.detection.DetectionStatus
import com.eggplant.detector.detection.InputSource
import com.eggplant.detector.detection.ModelMetadata
import com.eggplant.detector.detection.NormalizedBox
import com.eggplant.detector.detection.RgbFrame
import com.eggplant.detector.detection.StabilityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StillImageOutcomeTest {
    @Test
    fun `disease scene selects the highest confidence disease`() {
        val lower = detection(classIndex = 5, confidence = 0.42f)
        val higher = detection(classIndex = 9, confidence = 0.81f)
        val scene = scene(DetectionStatus.DISEASE_DETECTED, lower, higher)

        val outcome = Result.success(scene).toStillImageOutcome("fallback")

        assertTrue(outcome is StillImageOutcome.Disease)
        assertSame(higher, (outcome as StillImageOutcome.Disease).primary)
        assertSame(scene, outcome.scene)
    }

    @Test
    fun `healthy scene remains a successful observable outcome`() {
        val scene = scene(DetectionStatus.HEALTHY)

        val outcome = Result.success(scene).toStillImageOutcome("fallback")

        assertTrue(outcome is StillImageOutcome.Healthy)
        assertSame(scene, outcome.scene)
    }

    @Test
    fun `empty scene reports no supported match`() {
        val scene = scene(DetectionStatus.SEARCHING)

        val outcome = Result.success(scene).toStillImageOutcome("fallback")

        assertTrue(outcome is StillImageOutcome.NoMatch)
        assertSame(scene, outcome.scene)
    }

    @Test
    fun `inference failure keeps its useful message`() {
        val outcome = Result.failure<CameraScene>(IllegalStateException("model unavailable"))
            .toStillImageOutcome("fallback")

        assertEquals("model unavailable", (outcome as StillImageOutcome.Failure).message)
    }

    private fun scene(status: DetectionStatus, vararg detections: DetectionBox): CameraScene {
        val rgb = RgbFrame(1, 1, byteArrayOf(0, 0, 0), 1L, InputSource.GALLERY, 1L)
        val frame = DetectionFrame(detections.toList(), 1L, 10L, InputSource.GALLERY, 1L)
        return CameraScene(
            rgbFrame = rgb,
            detectionFrame = frame,
            stability = StabilityResult(status, detections.toList(), detections.toList(), detections.isNotEmpty()),
        )
    }

    private fun detection(classIndex: Int, confidence: Float) = DetectionBox(
        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classes[classIndex],
        confidence = confidence,
        bounds = NormalizedBox(0.1f, 0.1f, 0.8f, 0.8f),
    )
}
