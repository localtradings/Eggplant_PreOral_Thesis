package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult
import com.eggplant.detector.detection.ncnn.ModelMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePreviewSessionTest {
    @Test
    fun `release opens one disease result for the best confirmed live frame`() {
        val session = LivePreviewSession()
        val token = session.start()
        val lowConfidenceDisease = detection(classIndex = 5, confidence = 0.52f)
        val highConfidenceDisease = detection(classIndex = 9, confidence = 0.84f)
        val lowScene = scene(lowConfidenceDisease)
        val highScene = scene(highConfidenceDisease)

        session.record(token, lowScene)
        session.record(token, highScene)

        val outcome = session.stop(allowHealthy = false)
        assertTrue(outcome is LivePreviewOutcome.Disease)
        assertSame(highScene, (outcome as LivePreviewOutcome.Disease).scene)
        assertSame(highConfidenceDisease, outcome.primary)
        assertEquals(LivePreviewOutcome.NoStableDetection, session.stop(allowHealthy = false))
    }

    @Test
    fun `release ignores stale live frames from old sessions`() {
        val session = LivePreviewSession()
        val oldToken = session.start()
        val newToken = session.start()
        val oldDisease = detection(classIndex = 5, confidence = 0.91f)

        session.record(oldToken, scene(oldDisease))
        val outcome = session.stop(allowHealthy = false)

        assertEquals(LivePreviewOutcome.NoStableDetection, outcome)
        assertTrue(newToken > oldToken)
    }

    @Test
    fun `release stays on camera when no stable disease exists`() {
        val session = LivePreviewSession()
        val token = session.start()

        session.record(token, scene())

        assertEquals(LivePreviewOutcome.NoStableDetection, session.stop(allowHealthy = false))
    }

    @Test
    fun `healthy live release is explicit and never save eligible`() {
        val session = LivePreviewSession()
        val token = session.start()
        val healthyLeaf = detection(classIndex = 2, confidence = 0.90f)
        val healthyScene = scene(healthyLeaf, status = DetectionStatus.HEALTHY, stableDiseases = emptyList())

        session.record(token, healthyScene)

        assertEquals(LivePreviewOutcome.NoStableDetection, session.stop(allowHealthy = false))
        session.start().also { nextToken ->
            session.record(nextToken, healthyScene)
        }
        val outcome = session.stop(allowHealthy = true)

        assertTrue(outcome is LivePreviewOutcome.Healthy)
        assertSame(healthyScene, (outcome as LivePreviewOutcome.Healthy).scene)
        assertSame(healthyLeaf, outcome.primary)
        assertTrue(outcome.scene.stability.saveEligible == false)
    }

    @Test
    fun `lifecycle cancellation discards an already retained live result`() {
        val session = LivePreviewSession()
        val token = session.start()
        session.record(token, scene(detection(classIndex = 5, confidence = 0.91f)))

        session.cancel()

        assertEquals(LivePreviewOutcome.NoStableDetection, session.stop(allowHealthy = false))
    }

    @Test
    fun `poor quality can discard retained healthy without discarding a disease`() {
        val session = LivePreviewSession()
        val token = session.start()
        val healthy = detection(classIndex = 2, confidence = 0.92f)
        val disease = detection(classIndex = 5, confidence = 0.83f)
        session.record(token, scene(healthy, disease))

        session.discardHealthy()

        val outcome = session.stop(allowHealthy = true)
        assertTrue(outcome is LivePreviewOutcome.Disease)
        assertSame(disease, (outcome as LivePreviewOutcome.Disease).primary)
    }

    private fun scene(
        vararg detections: DetectionBox,
        status: DetectionStatus = if (detections.any { !it.modelClass.isHealthy }) {
            DetectionStatus.DISEASE_DETECTED
        } else {
            DetectionStatus.SEARCHING
        },
        stableDiseases: List<DetectionBox> = detections.filterNot { it.modelClass.isHealthy },
    ): CameraScene {
        val rgb = RgbFrame(1, 1, byteArrayOf(0, 0, 0), 1L, InputSource.LIVE, 1L)
        val frame = DetectionFrame(detections.toList(), 1L, 10L, InputSource.LIVE, 1L)
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
        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classFor(classIndex)!!,
        confidence = confidence,
        bounds = NormalizedBox(0.1f, 0.1f, 0.45f, 0.45f),
    )
}
