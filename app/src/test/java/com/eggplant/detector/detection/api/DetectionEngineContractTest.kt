package com.eggplant.detector.detection.api

import com.eggplant.detector.detection.ncnn.NativeDetectionMapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineContractTest {
    @Test
    fun `engine fails closed before initialization and after close`() {
        val engine = FakeDetectionEngine { emptyList() }
        val frame = rgbFrame()

        assertTrue(engine.detect(frame).isFailure)
        assertEquals(EngineState.READY, engine.initialize())
        assertTrue(engine.detect(frame).isSuccess)
        engine.close()
        assertEquals(EngineState.CLOSED, engine.state)
        assertTrue(engine.detect(frame).isFailure)
    }

    @Test
    fun `native result mapper rejects unknown classes and clips pixel bounds`() {
        val mapped = NativeDetectionMapper.map(
            values = floatArrayOf(
                5f, 0.87f, -10f, 20f, 660f, 700f,
                42f, 0.99f, 0f, 0f, 20f, 20f,
            ),
            imageWidth = 640,
            imageHeight = 640,
        )

        assertEquals(1, mapped.size)
        assertEquals("leaf-spot", mapped.single().modelClass.diseaseId)
        assertEquals(NormalizedBox(0f, 20f / 640f, 1f, 1f), mapped.single().bounds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rgb frame requires exactly three bytes per pixel`() {
        RgbFrame(
            width = 2,
            height = 2,
            rgbBytes = ByteArray(11),
            timestampMillis = 0,
            source = InputSource.LIVE,
            sceneToken = 1,
        )
    }

    private fun rgbFrame() = RgbFrame(
        width = 2,
        height = 2,
        rgbBytes = ByteArray(12),
        timestampMillis = 10,
        source = InputSource.LIVE,
        sceneToken = 1,
    )
}
