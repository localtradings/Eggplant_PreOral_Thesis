package com.eggplant.detector.detection.ncnn

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
