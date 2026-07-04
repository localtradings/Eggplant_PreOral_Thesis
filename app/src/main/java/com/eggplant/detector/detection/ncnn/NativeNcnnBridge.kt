package com.eggplant.detector.detection.ncnn

interface NcnnBridge {
    fun hasVulkan(): Boolean
    fun create(paramPath: String, binPath: String, useVulkan: Boolean): Long
    fun detect(
        handle: Long,
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        confidenceThreshold: Float,
    ): FloatArray
    fun destroy(handle: Long)
}

object NativeNcnnBridge : NcnnBridge {
    init {
        System.loadLibrary("eggplant_detector")
    }

    override external fun hasVulkan(): Boolean
    override external fun create(paramPath: String, binPath: String, useVulkan: Boolean): Long
    override external fun detect(
        handle: Long,
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        confidenceThreshold: Float,
    ): FloatArray
    override external fun destroy(handle: Long)
}
