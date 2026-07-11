package com.eggplant.detector.data.catalog

import com.eggplant.detector.domain.model.DiseaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiseaseCatalogTest {
    @Test
    fun `library contains exactly the approved diseases`() {
        assertEquals(
            setOf(
                "Insect Pest",
                "Leaf Spot",
                "Mosaic Virus",
                "White Molds",
                "Wilt",
                "Melon Thrips",
                "Fruit Rot",
                "Fruit Borer",
            ),
            DiseaseCatalog.diseases.map { it.name }.toSet(),
        )
        assertEquals(8, DiseaseCatalog.diseases.size)
        assertEquals(
            listOf(
                "Leaf Spot",
                "Mosaic Virus",
                "White Molds",
                "Wilt",
                "Insect Pest",
                "Melon Thrips",
                "Fruit Rot",
                "Fruit Borer",
            ),
            DiseaseCatalog.diseases.map { it.name },
        )
        assertEquals(5, DiseaseCatalog.diseases.count { it.type == DiseaseType.LEAF_DISEASE })
        assertEquals(3, DiseaseCatalog.diseases.count { it.type == DiseaseType.FRUIT_DISEASE })
        assertEquals(DiseaseType.FRUIT_DISEASE, DiseaseCatalog.byId("melon-thrips")?.type)
    }

    @Test
    fun `search is case insensitive and combines with category`() {
        val leafMatches = DiseaseCatalog.filter(
            query = "MoSaIc",
            type = DiseaseType.LEAF_DISEASE,
        )
        val fruitMatches = DiseaseCatalog.filter(
            query = "rot",
            type = DiseaseType.LEAF_DISEASE,
        )

        assertEquals(listOf("Mosaic Virus"), leafMatches.map { it.name })
        assertTrue(fruitMatches.isEmpty())
    }

    @Test
    fun `search includes symptom previews`() {
        val matches = DiseaseCatalog.filter(query = "silver streaks", type = null)

        assertEquals(listOf("Melon Thrips"), matches.map { it.name })
    }

    @Test
    fun `filter uses the catalog supplied by the database layer`() {
        val localizedCatalog = DiseaseCatalog.forLanguage("fil")

        val matches = DiseaseCatalog.filter(
            diseases = localizedCatalog,
            query = "Puting Amag",
            type = DiseaseType.LEAF_DISEASE,
        )

        assertEquals(listOf("white-molds"), matches.map { it.id })
    }
}
