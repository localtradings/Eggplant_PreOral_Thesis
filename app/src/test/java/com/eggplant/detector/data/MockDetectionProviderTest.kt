package com.eggplant.detector.data

import com.eggplant.detector.model.ScanCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class MockDetectionProviderTest {
    private val provider = MockDetectionProvider()

    @Test
    fun `capture returns deterministic leaf spot result`() {
        val result = provider.detectCapture()

        assertEquals("capture-leaf-spot", result.id)
        assertEquals("Leaf Spot", result.name)
        assertEquals(ScanCategory.LEAF_DISEASE, result.category)
        assertEquals(87, result.confidence)
    }

    @Test
    fun `gallery returns deterministic fruit borer result`() {
        val result = provider.detectGallery()

        assertEquals("gallery-fruit-borer", result.id)
        assertEquals("Fruit Borer", result.name)
        assertEquals(ScanCategory.FRUIT_DISEASE, result.category)
        assertEquals(91, result.confidence)
    }
}
