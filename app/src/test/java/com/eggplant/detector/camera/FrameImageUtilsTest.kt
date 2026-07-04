package com.eggplant.detector.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FrameImageUtilsTest {
    @Test
    fun `rgba rows with padding convert to tightly packed rgb`() {
        val rgba = byteArrayOf(
            1, 2, 3, 99, 4, 5, 6, 99, 0, 0, 0, 0,
            7, 8, 9, 99, 10, 11, 12, 99, 0, 0, 0, 0,
        )

        val rgb = FrameImageUtils.rgbaToRgb(rgba, width = 2, height = 2, rowStride = 12)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), rgb)
    }

    @Test
    fun `clockwise rotation returns upright dimensions and pixels`() {
        val source = byteArrayOf(
            1, 0, 0, 2, 0, 0,
            3, 0, 0, 4, 0, 0,
            5, 0, 0, 6, 0, 0,
        )

        val rotated = FrameImageUtils.rotateRgb(source, width = 2, height = 3, rotationDegrees = 90)

        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertArrayEquals(
            byteArrayOf(
                5, 0, 0, 3, 0, 0, 1, 0, 0,
                6, 0, 0, 4, 0, 0, 2, 0, 0,
            ),
            rotated.rgbBytes,
        )
    }

    @Test
    fun `zero degree rotation reuses the already upright rgb buffer`() {
        val source = byteArrayOf(1, 2, 3, 4, 5, 6)

        val rotated = FrameImageUtils.rotateRgb(source, width = 2, height = 1, rotationDegrees = 0)

        assertSame(source, rotated.rgbBytes)
    }

    @Test
    fun `scene token is deterministic and changes for a different frame`() {
        val dark = ByteArray(8 * 8 * 3)
        val bright = ByteArray(8 * 8 * 3) { 0xff.toByte() }

        assertEquals(FrameImageUtils.sceneToken(dark, 8, 8), FrameImageUtils.sceneToken(dark, 8, 8))
        org.junit.Assert.assertNotEquals(
            FrameImageUtils.sceneToken(dark, 8, 8),
            FrameImageUtils.sceneToken(bright, 8, 8),
        )
    }
}
