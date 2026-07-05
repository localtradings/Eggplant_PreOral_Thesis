package com.eggplant.detector.feature.camera

data class RotatedRgb(
    val width: Int,
    val height: Int,
    val rgbBytes: ByteArray,
)

object CameraFrameConverter {
    fun rgbaToRgb(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
    ): ByteArray {
        require(width > 0 && height > 0)
        require(rowStride >= width * 4)
        require(rgbaBytes.size >= rowStride * height)
        val rgb = ByteArray(width * height * 3)
        var destination = 0
        repeat(height) { y ->
            var source = y * rowStride
            repeat(width) {
                rgb[destination++] = rgbaBytes[source]
                rgb[destination++] = rgbaBytes[source + 1]
                rgb[destination++] = rgbaBytes[source + 2]
                source += 4
            }
        }
        return rgb
    }

    fun rotateRgb(
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): RotatedRgb {
        require(rgbBytes.size == width * height * 3)
        require(rotationDegrees in setOf(0, 90, 180, 270))
        if (rotationDegrees == 0) return RotatedRgb(width, height, rgbBytes)
        val outputWidth = if (rotationDegrees in setOf(90, 270)) height else width
        val outputHeight = if (rotationDegrees in setOf(90, 270)) width else height
        val output = ByteArray(rgbBytes.size)
        repeat(height) { sourceY ->
            repeat(width) { sourceX ->
                val (targetX, targetY) = when (rotationDegrees) {
                    90 -> (height - 1 - sourceY) to sourceX
                    180 -> (width - 1 - sourceX) to (height - 1 - sourceY)
                    else -> sourceY to (width - 1 - sourceX)
                }
                val sourceOffset = (sourceY * width + sourceX) * 3
                val targetOffset = (targetY * outputWidth + targetX) * 3
                output[targetOffset] = rgbBytes[sourceOffset]
                output[targetOffset + 1] = rgbBytes[sourceOffset + 1]
                output[targetOffset + 2] = rgbBytes[sourceOffset + 2]
            }
        }
        return RotatedRgb(outputWidth, outputHeight, output)
    }

    fun sceneToken(rgbBytes: ByteArray, width: Int, height: Int): Long {
        require(rgbBytes.size == width * height * 3)
        var token = 0L
        repeat(4) { blockY ->
            repeat(4) { blockX ->
                var luminance = 0
                var samples = 0
                repeat(2) { sampleY ->
                    repeat(2) { sampleX ->
                        val x = (((blockX * 2 + sampleX + 0.5f) / 8f) * width).toInt().coerceIn(0, width - 1)
                        val y = (((blockY * 2 + sampleY + 0.5f) / 8f) * height).toInt().coerceIn(0, height - 1)
                        val offset = (y * width + x) * 3
                        val red = rgbBytes[offset].toInt() and 0xff
                        val green = rgbBytes[offset + 1].toInt() and 0xff
                        val blue = rgbBytes[offset + 2].toInt() and 0xff
                        luminance += (red * 299 + green * 587 + blue * 114) / 1000
                        samples += 1
                    }
                }
                val bucket = ((luminance / samples) * 15 / 255).coerceIn(0, 15)
                token = (token shl 4) or bucket.toLong()
            }
        }
        return token
    }
}
