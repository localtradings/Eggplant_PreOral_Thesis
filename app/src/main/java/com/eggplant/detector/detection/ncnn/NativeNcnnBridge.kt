package com.eggplant.detector.detection.ncnn

import java.nio.ByteBuffer

interface NcnnBridge {
    fun hasVulkan(): Boolean
    fun create(
        paramPath: String,
        binPath: String,
        inputSize: Int,
        classCount: Int,
        useVulkan: Boolean,
    ): Long
    fun detect(
        handle: Long,
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        confidenceThreshold: Float,
    ): FloatArray
    fun detectRgba(
        handle: Long,
        rgbaBytes: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        rotationDegrees: Int,
        confidenceThreshold: Float,
    ): FloatArray = throw UnsupportedOperationException("Direct RGBA inference is unavailable.")
    fun destroy(handle: Long)
}

object NativeNcnnBridge : NcnnBridge {
    init {
        System.loadLibrary("eggplant_detector")
    }

    override external fun hasVulkan(): Boolean
    override external fun create(
        paramPath: String,
        binPath: String,
        inputSize: Int,
        classCount: Int,
        useVulkan: Boolean,
    ): Long
    override external fun detect(
        handle: Long,
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        confidenceThreshold: Float,
    ): FloatArray
    override external fun detectRgba(
        handle: Long,
        rgbaBytes: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        rotationDegrees: Int,
        confidenceThreshold: Float,
    ): FloatArray
    override external fun destroy(handle: Long)
}
