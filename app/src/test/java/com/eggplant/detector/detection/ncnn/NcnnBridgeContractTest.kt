package com.eggplant.detector.detection.ncnn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NcnnBridgeContractTest {
    @Test
    fun `native create receives the model input size and class count`() {
        val create = NcnnBridge::class.java.methods.single { it.name == "create" }

        assertEquals(
            listOf(
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ),
            create.parameterTypes.toList(),
        )
    }

    @Test
    fun `native bridge keeps ncnn optimized packing and extracts unpacked fp32 output`() {
        val source = File("src/main/cpp/detection/eggplant_ncnn_bridge.cpp").readText()

        assertFalse(source.contains("use_fp16_packed = false"))
        assertFalse(source.contains("use_fp16_storage = false"))
        assertFalse(source.contains("use_fp16_arithmetic = false"))
        assertFalse(source.contains("use_bf16_packed = false"))
        assertFalse(source.contains("use_bf16_storage = false"))
        assertFalse(source.contains("use_packing_layout = false"))
        assertFalse(source.contains("extract(\"out0\", output, 1)"))
    }

    @Test
    fun `native bridge uses bounded cpu threading instead of forcing single threaded inference`() {
        val source = File("src/main/cpp/detection/eggplant_ncnn_bridge.cpp").readText()

        assertTrue(source.contains("kMaximumCpuInferenceThreads = 4"))
        assertTrue(source.contains("bounded_inference_thread_count()"))
        assertFalse(source.contains("options.num_threads = 1;"))
        assertFalse(source.contains("set_omp_num_threads(1)"))
        assertFalse(source.contains("OMP_NUM_THREADS\", \"1\""))
    }
}
