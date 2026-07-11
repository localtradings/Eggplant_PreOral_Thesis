package com.eggplant.detector.feature.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer

class CameraFrameConverterTest {
    @Test
    fun `rgba rows with padding convert to tightly packed rgb`() {
        val rgba = byteArrayOf(
            99, 1, 2, 3, 99, 4, 5, 6, 0, 0, 0, 0,
            99, 7, 8, 9, 99, 10, 11, 12, 0, 0, 0, 0,
        )

        val rgb = CameraFrameConverter.rgbaToRgb(rgba, width = 2, height = 2, rowStride = 12)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), rgb)
    }

    @Test
    fun `camera crop is applied before rotation and inference`() {
        val rgba = byteArrayOf(
            99, 1, 2, 3, 99, 4, 5, 6, 99, 7, 8, 9,
            99, 10, 11, 12, 99, 13, 14, 15, 99, 16, 17, 18,
        )

        val rgb = CameraFrameConverter.rgbaToRgb(
            rgba,
            width = 3,
            height = 2,
            rowStride = 12,
            cropLeft = 1,
            cropTop = 0,
            cropWidth = 2,
            cropHeight = 2,
        )

        assertArrayEquals(byteArrayOf(4, 5, 6, 7, 8, 9, 13, 14, 15, 16, 17, 18), rgb)
    }

    @Test
    fun `CameraX ARGB conversion preserves RGB and ignores alpha`() {
        val transparentRed = byteArrayOf(0x00, 0x7f, 0x00, 0x00)
        val opaqueRed = byteArrayOf(0xff.toByte(), 0x7f, 0x00, 0x00)

        val transparentRgb = CameraFrameConverter.rgbaToRgb(transparentRed, width = 1, height = 1, rowStride = 4)
        val opaqueRgb = CameraFrameConverter.rgbaToRgb(opaqueRed, width = 1, height = 1, rowStride = 4)

        assertArrayEquals(byteArrayOf(0x7f, 0x00, 0x00), transparentRgb)
        assertArrayEquals(transparentRgb, opaqueRgb)
    }

    @Test
    fun `non direct CameraX plane copies without consuming the borrowed buffer`() {
        val plane = ByteBuffer.allocate(8).apply {
            put(byteArrayOf(0x11, 0x20, 0x40, 0x60, 0x22, 0x80.toByte(), 0x10, 0x30))
            flip()
        }

        val copied = CameraFrameConverter.copyRgbaPlane(plane, 8)
        val rgb = CameraFrameConverter.rgbaToRgb(copied, width = 2, height = 1, rowStride = 8)

        assertEquals(0, plane.position())
        assertArrayEquals(byteArrayOf(0x20, 0x40, 0x60, 0x80.toByte(), 0x10, 0x30), rgb)
    }

    @Test
    fun `clockwise rotation returns upright dimensions and pixels`() {
        val source = byteArrayOf(
            1, 0, 0, 2, 0, 0,
            3, 0, 0, 4, 0, 0,
            5, 0, 0, 6, 0, 0,
        )

        val rotated = CameraFrameConverter.rotateRgb(source, width = 2, height = 3, rotationDegrees = 90)

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

        val rotated = CameraFrameConverter.rotateRgb(source, width = 2, height = 1, rotationDegrees = 0)

        assertSame(source, rotated.rgbBytes)
    }

    @Test
    fun `scene token is deterministic and changes for a different frame`() {
        val dark = ByteArray(8 * 8 * 3)
        val bright = ByteArray(8 * 8 * 3) { 0xff.toByte() }

        assertEquals(CameraFrameConverter.sceneToken(dark, 8, 8), CameraFrameConverter.sceneToken(dark, 8, 8))
        org.junit.Assert.assertNotEquals(
            CameraFrameConverter.sceneToken(dark, 8, 8),
            CameraFrameConverter.sceneToken(bright, 8, 8),
        )
    }

    @Test
    fun `scene token reads camera rgba channels and ignores alpha`() {
        val dark = ByteBuffer.allocateDirect(8 * 8 * 4)
        val bright = ByteBuffer.allocateDirect(8 * 8 * 4)
        val transparentMidtone = ByteBuffer.allocateDirect(8 * 8 * 4)
        val opaqueMidtone = ByteBuffer.allocateDirect(8 * 8 * 4)
        repeat(8 * 8) { pixel ->
            val offset = pixel * 4
            dark.put(offset, 0xff.toByte())
            bright.put(offset, 0xff.toByte())
            bright.put(offset + 1, 0xff.toByte())
            bright.put(offset + 2, 0xff.toByte())
            bright.put(offset + 3, 0xff.toByte())
            transparentMidtone.put(offset, 0)
            transparentMidtone.put(offset + 1, 120)
            transparentMidtone.put(offset + 2, 80)
            transparentMidtone.put(offset + 3, 20)
            opaqueMidtone.put(offset, 0xff.toByte())
            opaqueMidtone.put(offset + 1, 120)
            opaqueMidtone.put(offset + 2, 80)
            opaqueMidtone.put(offset + 3, 20)
        }

        org.junit.Assert.assertNotEquals(
            CameraFrameConverter.sceneTokenRgba(dark, 8, 8, 32),
            CameraFrameConverter.sceneTokenRgba(bright, 8, 8, 32),
        )
        assertEquals(
            CameraFrameConverter.sceneTokenRgba(transparentMidtone, 8, 8, 32),
            CameraFrameConverter.sceneTokenRgba(opaqueMidtone, 8, 8, 32),
        )
    }
}
