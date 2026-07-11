package com.eggplant.detector.feature.camera

import java.nio.ByteBuffer

data class RotatedRgb(
    val width: Int,
    val height: Int,
    val rgbBytes: ByteArray,
)

object CameraFrameConverter {
    /**
     * CameraX names the output format RGBA_8888, but its first analysis plane is
     * physically packed as alpha, red, green, blue. Keep this conversion next to
     * the CameraX-specific frame handling so Bitmap RGB paths cannot accidentally
     * inherit the camera byte order.
     */
    fun rgbaToRgb(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        cropLeft: Int = 0,
        cropTop: Int = 0,
        cropWidth: Int = width,
        cropHeight: Int = height,
    ): ByteArray {
        require(width > 0 && height > 0)
        require(rowStride >= width * 4)
        require(rgbaBytes.size >= rowStride * height)
        require(cropLeft >= 0 && cropTop >= 0 && cropWidth > 0 && cropHeight > 0)
        require(cropLeft + cropWidth <= width && cropTop + cropHeight <= height)
        val rgb = ByteArray(cropWidth * cropHeight * 3)
        var destination = 0
        repeat(cropHeight) { y ->
            var source = (cropTop + y) * rowStride + cropLeft * 4
            repeat(cropWidth) {
                rgb[destination++] = rgbaBytes[source + CAMERA_X_RED_OFFSET]
                rgb[destination++] = rgbaBytes[source + CAMERA_X_GREEN_OFFSET]
                rgb[destination++] = rgbaBytes[source + CAMERA_X_BLUE_OFFSET]
                source += 4
            }
        }
        return rgb
    }

    /**
     * Copies a CameraX plane only when a caller must use the non-direct/native
     * fallback. The duplicate keeps the borrowed ImageProxy buffer position
     * untouched, which is required before CameraX closes the frame.
     */
    fun copyRgbaPlane(buffer: ByteBuffer, byteCount: Int): ByteArray {
        require(byteCount >= 0)
        val source = buffer.duplicate()
        require(source.remaining() >= byteCount) { "Camera frame buffer is incomplete." }
        return ByteArray(byteCount).also(source::get)
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

    fun sceneTokenRgba(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        cropLeft: Int = 0,
        cropTop: Int = 0,
        cropWidth: Int = width,
        cropHeight: Int = height,
    ): Long {
        require(buffer.isDirect)
        require(rowStride >= width * 4)
        require(buffer.capacity() >= rowStride * height)
        require(cropLeft >= 0 && cropTop >= 0 && cropWidth > 0 && cropHeight > 0)
        require(cropLeft + cropWidth <= width && cropTop + cropHeight <= height)
        val bytes = buffer.duplicate()
        var token = 0L
        repeat(4) { blockY ->
            repeat(4) { blockX ->
                var luminance = 0
                var samples = 0
                repeat(2) { sampleY ->
                    repeat(2) { sampleX ->
                        val x = cropLeft + (((blockX * 2 + sampleX + 0.5f) / 8f) * cropWidth)
                            .toInt().coerceIn(0, cropWidth - 1)
                        val y = cropTop + (((blockY * 2 + sampleY + 0.5f) / 8f) * cropHeight)
                            .toInt().coerceIn(0, cropHeight - 1)
                        val offset = y * rowStride + x * 4
                        val red = bytes.get(offset + CAMERA_X_RED_OFFSET).toInt() and 0xff
                        val green = bytes.get(offset + CAMERA_X_GREEN_OFFSET).toInt() and 0xff
                        val blue = bytes.get(offset + CAMERA_X_BLUE_OFFSET).toInt() and 0xff
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

    private const val CAMERA_X_RED_OFFSET = 1
    private const val CAMERA_X_GREEN_OFFSET = 2
    private const val CAMERA_X_BLUE_OFFSET = 3
}
