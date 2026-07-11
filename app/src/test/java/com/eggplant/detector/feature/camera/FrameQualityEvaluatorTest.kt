package com.eggplant.detector.feature.camera

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameQualityEvaluatorTest {
    @Test
    fun `dark and overexposed frames return guidance instead of a healthy result`() {
        assertEquals(FrameQualityHint.LOW_LIGHT, FrameQualityEvaluator.evaluateRgba(frame(24, 18) { 20 }, 24, 18, 96))
        assertEquals(FrameQualityHint.OVEREXPOSED, FrameQualityEvaluator.evaluateRgba(frame(24, 18) { 245 }, 24, 18, 96))
    }

    @Test
    fun `flat midtone frame asks the user to hold steady`() {
        assertEquals(FrameQualityHint.HOLD_STEADY, FrameQualityEvaluator.evaluateRgba(frame(24, 18) { 128 }, 24, 18, 96))
    }

    @Test
    fun `well exposed detailed frame has no quality warning`() {
        val detailed = frame(24, 18) { index -> if ((index + index / 24) % 2 == 0) 60 else 190 }

        assertNull(FrameQualityEvaluator.evaluateRgba(detailed, 24, 18, 96))
    }

    @Test
    fun `quality evaluation only inspects the CameraX viewport crop`() {
        val buffer = frame(8, 8) { 240 }
        repeat(4) { y ->
            repeat(4) { x -> putRgba(buffer, x + 2, y + 2, 8, 20) }
        }

        assertEquals(
            FrameQualityHint.LOW_LIGHT,
            FrameQualityEvaluator.evaluateRgba(
                buffer,
                width = 8,
                height = 8,
                rowStride = 32,
                cropLeft = 2,
                cropTop = 2,
                cropWidth = 4,
                cropHeight = 4,
            ),
        )
    }

    @Test
    fun `quality evaluation ignores the rgba alpha channel`() {
        val transparent = frame(24, 18, alpha = 0) { 128 }
        val opaque = frame(24, 18, alpha = 255) { 128 }

        assertEquals(
            FrameQualityEvaluator.evaluateRgba(transparent, 24, 18, 96),
            FrameQualityEvaluator.evaluateRgba(opaque, 24, 18, 96),
        )
    }

    @Test
    fun `quality evaluation uses CameraX ARGB order for heap backed fallback planes`() {
        val plane = ByteBuffer.allocate(24 * 18 * 4)
        repeat(24 * 18) { pixel ->
            val offset = pixel * 4
            plane.put(offset, 0)
            plane.put(offset + 1, 20)
            plane.put(offset + 2, 20)
            plane.put(offset + 3, 20)
        }
        plane.position(0)
        plane.limit(plane.capacity())

        assertEquals(FrameQualityHint.LOW_LIGHT, FrameQualityEvaluator.evaluateRgba(plane, 24, 18, 96))
        assertEquals(0, plane.position())
    }

    private fun frame(width: Int, height: Int, alpha: Int = 255, value: (Int) -> Int): ByteBuffer =
        ByteBuffer.allocateDirect(width * height * 4).also { buffer ->
            repeat(width * height) { index ->
                val component = value(index)
                val x = index % width
                val y = index / width
                putRgba(buffer, x, y, width, component, alpha)
            }
        }

    private fun putRgba(buffer: ByteBuffer, x: Int, y: Int, width: Int, component: Int, alpha: Int = 255) {
        val offset = (y * width + x) * 4
        // CameraX OUTPUT_IMAGE_FORMAT_RGBA_8888 is packed as A/R/G/B.
        buffer.put(offset, alpha.toByte())
        buffer.put(offset + 1, component.toByte())
        buffer.put(offset + 2, component.toByte())
        buffer.put(offset + 3, component.toByte())
    }
}
