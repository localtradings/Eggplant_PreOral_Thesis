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
    fun `overlay renders only confirmed detections`() {
        val tentative = presentOverlayDetections(
            visible = listOf(leafSpot),
            confirmed = emptyList(),
            displayName = { "Leaf Spot" },
        )

        assertTrue(tentative.isEmpty())

        val confirmed = presentOverlayDetections(
            visible = listOf(leafSpot),
            confirmed = listOf(leafSpot),
            displayName = { "Leaf Spot" },
        ).single()

        assertEquals(OverlayPhase.CONFIRMED, confirmed.phase)
        assertEquals("Leaf Spot · 87%", confirmed.label)
    }

    @Test
    fun `confirmation requires the same class in the same spatial region`() {
        val otherRegion = leafSpot.copy(bounds = NormalizedBox(0.7f, 0.7f, 0.9f, 0.9f))

        val items = presentOverlayDetections(
            visible = listOf(leafSpot),
            confirmed = listOf(otherRegion),
            displayName = { "Leaf Spot" },
        )

        assertTrue(items.isEmpty())
    }
}
