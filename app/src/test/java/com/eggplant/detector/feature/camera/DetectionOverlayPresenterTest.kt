package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.ncnn.ModelMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionOverlayPresenterTest {
    private val leafSpot = DetectionBox(
        ModelMetadata.EGGPLANT_YOLO26M.classes[5],
        0.87f,
        NormalizedBox(0.1f, 0.1f, 0.2f, 0.2f),
    )

    @Test
    fun `overlay renders tentative detections immediately and labels confirmed detections`() {
        val tentative = presentOverlayDetections(
            visible = listOf(leafSpot),
            confirmed = emptyList(),
            displayName = { "Leaf Spot" },
        ).single()

        assertEquals(OverlayPhase.TENTATIVE, tentative.phase)
        assertEquals("Possible Leaf Spot", tentative.label)

        val confirmed = presentOverlayDetections(
            visible = listOf(leafSpot),
            confirmed = listOf(leafSpot),
            displayName = { "Leaf Spot" },
        ).single()

        assertEquals(OverlayPhase.CONFIRMED, confirmed.phase)
        assertEquals("Leaf Spot", confirmed.label)
    }

    @Test
    fun `held confirmation renders when no current visible detection exists`() {
        val confirmed = presentOverlayDetections(
            visible = emptyList(),
            confirmed = listOf(leafSpot),
            displayName = { "Leaf Spot" },
        ).single()

        assertEquals(OverlayPhase.CONFIRMED, confirmed.phase)
        assertEquals("Leaf Spot", confirmed.label)
    }

    @Test
    fun `stale confirmation is replaced by a current tentative detection`() {
        val otherRegion = leafSpot.copy(bounds = NormalizedBox(0.7f, 0.7f, 0.9f, 0.9f))

        val items = presentOverlayDetections(
            visible = listOf(leafSpot),
            confirmed = listOf(otherRegion),
            displayName = { "Leaf Spot" },
        )

        assertEquals(1, items.size)
        assertEquals(OverlayPhase.TENTATIVE, items.single().phase)
    }
}
