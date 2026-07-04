package com.eggplant.detector

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eggplant.detector.detection.EngineState
import com.eggplant.detector.detection.InputSource
import com.eggplant.detector.detection.ModelMetadata
import com.eggplant.detector.detection.NcnnDetectionEngine
import com.eggplant.detector.detection.RgbFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NcnnDetectionEngineInstrumentedTest {
    @Test
    fun packagedModelLoadsAndRunsARealImageThroughNativeNcnn() {
        assumeTrue(
            "Run separately with -Pandroid.testInstrumentationRunnerArguments.realModel=true",
            InstrumentationRegistry.getArguments().getString("realModel") == "true",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.hero_leaf))
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rgb = ByteArray(pixels.size * 3)
        var offset = 0
        pixels.forEach { color ->
            rgb[offset++] = (color shr 16 and 0xff).toByte()
            rgb[offset++] = (color shr 8 and 0xff).toByte()
            rgb[offset++] = (color and 0xff).toByte()
        }
        bitmap.recycle()
        // The emulator exposes software SwiftShader as Vulkan; use CPU so this
        // fixture verifies NCNN itself instead of benchmarking a software GPU.
        var engine = NcnnDetectionEngine(context, preferVulkan = false)

        try {
            assertEquals(EngineState.READY, engine.initialize())
            val result = engine.detect(
                RgbFrame(
                    width = width,
                    height = height,
                    rgbBytes = rgb,
                    timestampMillis = 1L,
                    source = InputSource.GALLERY,
                    sceneToken = 1L,
                ),
            ).getOrThrow()

            assertTrue(result.inferenceMillis >= 0L)
            assertFalse("The packaged positive fixture must produce a detection.", result.detections.isEmpty())
            assertTrue(
                "The positive leaf fixture must include Leaf-Spot.",
                result.detections.any { it.modelClass.diseaseId == "leaf-spot" },
            )
            assertTrue(result.detections.all { it.modelClass in ModelMetadata.EGGPLANT_YOLO26M.classes })
            engine.close()

            engine = NcnnDetectionEngine(context, preferVulkan = false)
            assertEquals(EngineState.READY, engine.initialize())
            assertTrue(
                engine.detect(RgbFrame(1, 1, byteArrayOf(0, 0, 0), 2L, InputSource.GALLERY, 2L)).isSuccess,
            )
        } finally {
            engine.close()
        }
    }
}
