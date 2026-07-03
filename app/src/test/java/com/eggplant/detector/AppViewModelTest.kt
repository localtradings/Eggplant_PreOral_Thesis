package com.eggplant.detector

import com.eggplant.detector.data.MockDetectionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelTest {
    @Test
    fun `saving current result is idempotent`() {
        val viewModel = AppViewModel(
            detectionProvider = MockDetectionProvider(),
            initialHistory = emptyList(),
        )

        viewModel.detectCapture()
        assertTrue(viewModel.saveCurrentResult())
        assertTrue(!viewModel.saveCurrentResult())
        assertEquals(1, viewModel.history.value.size)
    }

    @Test
    fun `latest saved result becomes last scan`() {
        val viewModel = AppViewModel(
            detectionProvider = MockDetectionProvider(),
            initialHistory = emptyList(),
        )

        viewModel.detectGallery()
        viewModel.saveCurrentResult()

        assertEquals("Fruit Borer", viewModel.lastScan.value?.name)
    }
}
