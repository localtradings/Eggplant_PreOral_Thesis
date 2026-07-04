package com.eggplant.detector.detection.tracking

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.ncnn.ModelMetadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionStabilityTrackerTest {
    private val leafSpot = detection(classIndex = 5, confidence = 0.87f)
    private val wilt = detection(classIndex = 9, confidence = 0.78f, left = 0.55f, right = 0.9f)
    private val healthyLeaf = detection(classIndex = 2, confidence = 0.91f)

    @Test
    fun `detection becomes stable only after three matching frames and 1250 milliseconds`() {
        val tracker = DetectionStabilityTracker()

        assertFalse(tracker.update(frame(0, leafSpot)).stableDetections.isNotEmpty())
        assertFalse(tracker.update(frame(700, leafSpot)).stableDetections.isNotEmpty())
        val stable = tracker.update(frame(1_250, leafSpot))

        assertEquals(listOf("leaf-spot"), stable.stableDetections.mapNotNull { it.modelClass.diseaseId })
        assertEquals(DetectionStatus.DISEASE_DETECTED, stable.status)
        assertTrue(stable.saveEligible)
    }

    @Test
    fun `low confidence and spatially unrelated boxes do not advance a track`() {
        val tracker = DetectionStabilityTracker()
        val lowConfidence = leafSpot.copy(confidence = 0.49f)
        val moved = leafSpot.copy(bounds = NormalizedBox(0.65f, 0.1f, 0.95f, 0.4f))

        tracker.update(frame(0, leafSpot))
        tracker.update(frame(700, lowConfidence))
        val result = tracker.update(frame(1_400, moved))

        assertTrue(result.stableDetections.isEmpty())
        assertEquals(DetectionStatus.SEARCHING, result.status)
    }

    @Test
    fun `healthy stability reports status but is never save eligible`() {
        val tracker = DetectionStabilityTracker()

        tracker.update(frame(0, healthyLeaf))
        tracker.update(frame(700, healthyLeaf))
        val result = tracker.update(frame(1_250, healthyLeaf))

        assertEquals(DetectionStatus.HEALTHY, result.status)
        assertFalse(result.saveEligible)
        assertTrue(result.stableDetections.isEmpty())
    }

    @Test
    fun `multiple stable diseases are grouped in one result`() {
        val tracker = DetectionStabilityTracker()

        tracker.update(frame(0, leafSpot, wilt))
        tracker.update(frame(700, leafSpot, wilt))
        val result = tracker.update(frame(1_250, leafSpot, wilt))

        assertEquals(setOf("leaf-spot", "wilt"), result.stableDetections.mapNotNull { it.modelClass.diseaseId }.toSet())
        assertTrue(result.saveEligible)
    }

    @Test
    fun `saved scene stays disarmed until detections disappear or scene token changes`() {
        val tracker = DetectionStabilityTracker()
        val firstScene = 0x1111111111111111L
        val smallLightingChange = 0x1111111111111112L
        val differentScene = -0x1111111111111112L
        tracker.update(frame(0, leafSpot, sceneToken = firstScene))
        tracker.update(frame(700, leafSpot, sceneToken = firstScene))
        tracker.update(frame(1_250, leafSpot, sceneToken = firstScene))
        tracker.markSaved()

        assertFalse(tracker.update(frame(1_500, leafSpot, sceneToken = smallLightingChange)).saveEligible)
        assertTrue(tracker.update(frame(1_600, leafSpot, sceneToken = differentScene)).saveEligible)

        tracker.markSaved()
        tracker.update(frame(2_000, sceneToken = differentScene))
        assertFalse(tracker.update(frame(3_900, sceneToken = differentScene)).saveEligible)
        tracker.update(frame(4_100, sceneToken = differentScene))
        tracker.update(frame(4_800, leafSpot, sceneToken = differentScene))
        tracker.update(frame(5_500, leafSpot, sceneToken = differentScene))
        assertTrue(tracker.update(frame(6_050, leafSpot, sceneToken = differentScene)).saveEligible)
    }

    private fun frame(
        timestampMillis: Long,
        vararg detections: DetectionBox,
        sceneToken: Long = 1,
    ) = DetectionFrame(
        detections = detections.toList(),
        timestampMillis = timestampMillis,
        inferenceMillis = 100,
        source = InputSource.LIVE,
        sceneToken = sceneToken,
    )

    private fun detection(
        classIndex: Int,
        confidence: Float,
        left: Float = 0.1f,
        right: Float = 0.45f,
    ) = DetectionBox(
        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classFor(classIndex)!!,
        confidence = confidence,
        bounds = NormalizedBox(left, 0.1f, right, 0.45f),
    )
}
