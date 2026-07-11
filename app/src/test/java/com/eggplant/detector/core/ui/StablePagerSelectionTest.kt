package com.eggplant.detector.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StablePagerSelectionTest {
    @Test
    fun `selected item follows its id when items are inserted or reordered`() {
        assertEquals(2, stablePageForId(listOf("new", "a", "b"), "b", 1))
        assertEquals(0, stablePageForId(listOf("b", "a"), "b", 2))
    }

    @Test
    fun `removed selection falls back to a valid page`() {
        assertEquals(1, stablePageForId(listOf("a", "c"), "b", 1))
        assertEquals(1, stablePageForId(listOf("a", "c"), "b", 9))
        assertEquals(0, stablePageForId(emptyList(), "b", 9))
    }
}
