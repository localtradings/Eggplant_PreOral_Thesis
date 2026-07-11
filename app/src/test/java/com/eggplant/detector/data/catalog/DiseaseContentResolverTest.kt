package com.eggplant.detector.data.catalog

import com.eggplant.detector.domain.model.DiseaseType
import org.junit.Assert.assertEquals
import org.junit.Test

class DiseaseContentResolverTest {
    @Test
    fun `synchronized catalog wins over bundled fallback`() {
        val bundled = DiseaseCatalog.forLanguage("en").first { it.id == "fruit-rot" }
        val changed = bundled.copy(name = "Updated Fruit Rot", type = DiseaseType.FRUIT_DISEASE)

        assertEquals(
            "Updated Fruit Rot",
            DiseaseContentResolver.resolve("fruit-rot", listOf(changed), "en")?.name,
        )
    }

    @Test
    fun `fallback honors requested language`() {
        assertEquals(
            "Pagkabulok ng Bunga",
            DiseaseContentResolver.resolve("fruit-rot", emptyList(), "fil")?.name,
        )
    }
}
