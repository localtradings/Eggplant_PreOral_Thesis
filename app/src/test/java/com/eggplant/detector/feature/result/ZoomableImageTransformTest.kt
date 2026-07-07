package com.eggplant.detector.feature.result

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableImageTransformTest {
    @Test
    fun `zoom scale is clamped to supported range`() {
        val transform = ZoomableImageTransform()

        val tooSmall = transform.applyGesture(zoomChange = 0.5f, panChange = Offset(24f, 18f))
        val zoomed = transform.applyGesture(zoomChange = 3f, panChange = Offset(24f, 18f))
        val tooLarge = zoomed.applyGesture(zoomChange = 4f, panChange = Offset.Zero)

        assertEquals(ZoomableImageTransform.MinScale, tooSmall.scale, 0.001f)
        assertEquals(Offset.Zero, tooSmall.offset)
        assertEquals(3f, zoomed.scale, 0.001f)
        assertEquals(ZoomableImageTransform.MaxScale, tooLarge.scale, 0.001f)
    }

    @Test
    fun `panning is disabled at minimum scale and reset after zooming out`() {
        val zoomed = ZoomableImageTransform()
            .applyGesture(zoomChange = 2f, panChange = Offset(10f, 12f))
        val moved = zoomed.applyGesture(zoomChange = 1f, panChange = Offset(30f, -8f))
        val reset = moved.applyGesture(zoomChange = 0.2f, panChange = Offset(100f, 100f))

        assertEquals(Offset(40f, 4f), moved.offset)
        assertEquals(ZoomableImageTransform.MinScale, reset.scale, 0.001f)
        assertEquals(Offset.Zero, reset.offset)
    }
}
