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
        require(buffer.isDirect)
        require(buffer.capacity() >= rowStride * height)
        require(cropLeft >= 0 && cropTop >= 0 && cropWidth > 0 && cropHeight > 0)
        require(cropLeft + cropWidth <= width && cropTop + cropHeight <= height)
        val source = buffer.duplicate()
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
                val offset = y * rowStride + x * 4
                if (offset + 3 >= source.capacity()) break
                val red = source.get(offset).toInt() and 0xff
                val green = source.get(offset + 1).toInt() and 0xff
                val blue = source.get(offset + 2).toInt() and 0xff
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
}
