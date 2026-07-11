package com.eggplant.detector.feature.camera

import java.nio.ByteBuffer

enum class FrameQualityHint { LOW_LIGHT, OVEREXPOSED, HOLD_STEADY, TOO_CLOSE }

internal object FrameQualityEvaluator {
    fun evaluateRgba(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        cropLeft: Int = 0,
        cropTop: Int = 0,
        cropWidth: Int = width,
        cropHeight: Int = height,
    ): FrameQualityHint? {
        require(width > 0 && height > 0)
        require(rowStride >= width * 4)
        // Direct buffers use the zero-copy NCNN path, but CameraX/OEM changes
        // must still have a correct Kotlin fallback when the plane is heap-backed.
        require(buffer.remaining() >= rowStride * height)
        require(cropLeft >= 0 && cropTop >= 0 && cropWidth > 0 && cropHeight > 0)
        require(cropLeft + cropWidth <= width && cropTop + cropHeight <= height)
        val source = buffer.duplicate()
        val startOffset = source.position()
        var luminanceSum = 0L
        var contrastSum = 0L
        var count = 0
        var previous = -1
        val stepX = (cropWidth / 24).coerceAtLeast(1)
        val stepY = (cropHeight / 18).coerceAtLeast(1)
        var y = cropTop
        while (y < cropTop + cropHeight) {
            var x = cropLeft
            while (x < cropLeft + cropWidth) {
                val offset = startOffset + y * rowStride + x * 4
                if (offset + CAMERA_X_BLUE_OFFSET >= source.limit()) break
                // CameraX RGBA_8888's first plane is physically A/R/G/B.
                val red = source.get(offset + CAMERA_X_RED_OFFSET).toInt() and 0xff
                val green = source.get(offset + CAMERA_X_GREEN_OFFSET).toInt() and 0xff
                val blue = source.get(offset + CAMERA_X_BLUE_OFFSET).toInt() and 0xff
                val luminance = (red * 54 + green * 183 + blue * 19) shr 8
                luminanceSum += luminance
                if (previous >= 0) contrastSum += kotlin.math.abs(luminance - previous)
                previous = luminance
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return null
        val mean = luminanceSum / count
        val contrast = contrastSum / count.coerceAtLeast(1)
        return when {
            mean < 48 -> FrameQualityHint.LOW_LIGHT
            mean > 225 -> FrameQualityHint.OVEREXPOSED
            contrast < 7 -> FrameQualityHint.HOLD_STEADY
            else -> null
        }
    }

    private const val CAMERA_X_RED_OFFSET = 1
    private const val CAMERA_X_GREEN_OFFSET = 2
    private const val CAMERA_X_BLUE_OFFSET = 3
}
