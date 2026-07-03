package com.eggplant.detector.utils

import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FormatterTest {
    private val now = LocalDateTime.of(2026, 7, 2, 12, 0)

    @Test
    fun `confidence formats as a percentage`() {
        assertEquals("87%", ConfidenceFormatter.format(87))
    }

    @Test
    fun `scan result rejects confidence outside zero to one hundred`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanResult(
                id = "invalid",
                name = "Leaf Spot",
                category = ScanCategory.LEAF_DISEASE,
                confidence = 101,
                scannedAt = now,
                signs = emptyList(),
                treatment = "Mock guidance",
            )
        }
    }

    @Test
    fun `date grouping distinguishes today yesterday and june 2026`() {
        assertEquals("Today", DateFormatter.groupLabel(now, now))
        assertEquals("Yesterday", DateFormatter.groupLabel(now.minusDays(1), now))
        assertEquals(
            "June 2026",
            DateFormatter.groupLabel(LocalDateTime.of(2026, 6, 15, 9, 0), now),
        )
    }
}
