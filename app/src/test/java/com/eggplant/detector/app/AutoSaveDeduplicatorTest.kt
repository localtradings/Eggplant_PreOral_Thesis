package com.eggplant.detector.app

import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.domain.model.ScanOutcome
import com.eggplant.detector.domain.model.ScanResult
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSaveDeduplicatorTest {
    @Test
    fun `stable disease is saved once per scene`() {
        val deduplicator = AutoSaveDeduplicator()
        val result = result()

        assertTrue(deduplicator.shouldSave(true, true, result, 10L))
        deduplicator.record(result, 10L)
        assertFalse(deduplicator.shouldSave(true, true, result.copy(id = "second"), 10L))
        assertTrue(deduplicator.shouldSave(true, true, result.copy(id = "third"), 11L))
    }

    @Test
    fun `disabled unstable healthy and photo-less results never auto save`() {
        val deduplicator = AutoSaveDeduplicator()
        val disease = result()

        assertFalse(deduplicator.shouldSave(false, true, disease, 1L))
        assertFalse(deduplicator.shouldSave(true, false, disease, 1L))
        assertFalse(deduplicator.shouldSave(true, true, disease.copy(outcome = ScanOutcome.HEALTHY_CONFIRMED), 1L))
        assertFalse(deduplicator.shouldSave(true, true, disease.copy(imagePath = null), 1L))
    }

    private fun result() = ScanResult(
        id = "first",
        name = "Leaf Spot",
        category = ScanCategory.LEAF_DISEASE,
        outcome = ScanOutcome.DISEASE,
        confidence = 80,
        scannedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        signs = emptyList(),
        treatment = "",
        diseaseId = "leaf-spot",
        source = "capture",
        imagePath = "/private/photo.jpg",
    )
}
