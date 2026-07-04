package com.eggplant.detector.detection.ncnn

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.eggplant.detector.BuildConfig
import com.eggplant.detector.detection.api.DetectionEngine
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.RgbFrame
import java.io.File
import java.security.MessageDigest

class NcnnDetectionEngine(
    context: Context,
    private val metadata: ModelMetadata = ModelMetadata.EGGPLANT_YOLO26M,
    private val bridge: NcnnBridge = NativeNcnnBridge,
    private val preferVulkan: Boolean = false,
) : DetectionEngine {
    private val appContext = context.applicationContext
    private var nativeHandle = 0L

    override var state: EngineState = EngineState.UNINITIALIZED
        private set

    var lastError: String? = null
        private set

    @Synchronized
    override fun initialize(): EngineState {
        if (state == EngineState.READY || state == EngineState.CLOSED) return state
        return try {
            val modelDir = File(appContext.noBackupFilesDir, "models/${metadata.modelVersion}")
            val param = verifiedAsset(
                assetPath = "$ASSET_ROOT/model.ncnn.param",
                destination = File(modelDir, "model.ncnn.param"),
                expectedSha256 = metadata.paramSha256,
            )
            val weights = verifiedAsset(
                assetPath = "$ASSET_ROOT/model.ncnn.bin",
                destination = File(modelDir, "model.ncnn.bin"),
                expectedSha256 = metadata.binSha256,
            )
            val useVulkan = preferVulkan && bridge.hasVulkan()
            nativeHandle = if (bridge === NativeNcnnBridge) {
                synchronized(sharedNativeLock) {
                    if (sharedNativeHandle == 0L) {
                        sharedNativeHandle = bridge.create(
                            paramPath = param.absolutePath,
                            binPath = weights.absolutePath,
                            useVulkan = useVulkan,
                        )
                    }
                    sharedNativeHandle
                }
            } else {
                bridge.create(
                    paramPath = param.absolutePath,
                    binPath = weights.absolutePath,
                    useVulkan = useVulkan,
                )
            }
            check(nativeHandle != 0L) { "NCNN rejected the packaged model." }
            lastError = null
            state = EngineState.READY
            debugLog("initialized backend=${if (useVulkan) "vulkan" else "cpu"} threshold=${metadata.confidenceThreshold}")
            state
        } catch (error: Throwable) {
            nativeHandle = 0L
            lastError = error.message ?: error::class.java.simpleName
            state = EngineState.FAILED
            state
        }
    }

    @Synchronized
    override fun detect(frame: RgbFrame): Result<DetectionFrame> {
        if (state != EngineState.READY || nativeHandle == 0L) {
            return Result.failure(IllegalStateException(lastError ?: "Detection engine is not ready."))
        }
        return runCatching {
            val started = SystemClock.elapsedRealtimeNanos()
            val inference = {
                bridge.detect(
                    handle = nativeHandle,
                    rgbBytes = frame.rgbBytes,
                    width = frame.width,
                    height = frame.height,
                    confidenceThreshold = metadata.confidenceThreshold,
                )
            }
            val nativeValues = if (bridge === NativeNcnnBridge) synchronized(sharedNativeLock) { inference() } else inference()
            val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
            val result = DetectionFrame(
                detections = NativeDetectionMapper.map(nativeValues, frame.width, frame.height, metadata),
                timestampMillis = frame.timestampMillis,
                inferenceMillis = elapsedMillis,
                source = frame.source,
                sceneToken = frame.sceneToken,
            )
            debugLog(
                "source=${frame.source} frame=${frame.width}x${frame.height} " +
                    "inferenceMs=$elapsedMillis detections=${result.detections.size}",
            )
            result
        }
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L && bridge !== NativeNcnnBridge) {
            bridge.destroy(nativeHandle)
        }
        nativeHandle = 0L
        state = EngineState.CLOSED
    }

    private fun verifiedAsset(
        assetPath: String,
        destination: File,
        expectedSha256: String,
    ): File {
        destination.parentFile?.mkdirs()
        if (!destination.isFile || destination.sha256() != expectedSha256) {
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            appContext.assets.open(assetPath).use { input ->
                temporary.outputStream().use(input::copyTo)
            }
            check(temporary.sha256() == expectedSha256) { "Packaged model checksum mismatch: $assetPath" }
            check(temporary.renameTo(destination)) { "Could not install packaged model: $assetPath" }
        }
        check(destination.sha256() == expectedSha256) { "Installed model checksum mismatch: $assetPath" }
        return destination
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message)
    }

    companion object {
        private const val ASSET_ROOT = "models/eggplant-yolo26m"
        private const val LOG_TAG = "EggplantDetection"
        private val sharedNativeLock = Any()
        private var sharedNativeHandle = 0L
    }
}
