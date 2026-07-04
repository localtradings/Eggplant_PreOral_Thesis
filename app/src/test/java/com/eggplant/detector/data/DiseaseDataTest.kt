package com.eggplant.detector.data

import com.eggplant.detector.model.DiseaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiseaseDataTest {
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
            DiseaseData.diseases.map { it.name }.toSet(),
        )
        assertEquals(8, DiseaseData.diseases.size)
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
            DiseaseData.diseases.map { it.name },
        )
        assertEquals(6, DiseaseData.diseases.count { it.type == DiseaseType.LEAF_DISEASE })
        assertEquals(2, DiseaseData.diseases.count { it.type == DiseaseType.FRUIT_DISEASE })
    }

    @Test
    fun `search is case insensitive and combines with category`() {
        val leafMatches = DiseaseData.filter(
            query = "MoSaIc",
            type = DiseaseType.LEAF_DISEASE,
        )
        val fruitMatches = DiseaseData.filter(
            query = "rot",
            type = DiseaseType.LEAF_DISEASE,
        )

        assertEquals(listOf("Mosaic Virus"), leafMatches.map { it.name })
        assertTrue(fruitMatches.isEmpty())
    }

    @Test
    fun `search includes symptom previews`() {
        val matches = DiseaseData.filter(query = "silver streaks", type = null)

        assertEquals(listOf("Melon Thrips"), matches.map { it.name })
    }

    @Test
    fun `filter uses the catalog supplied by the database layer`() {
        val localizedCatalog = DiseaseData.forLanguage("fil")

        val matches = DiseaseData.filter(
            diseases = localizedCatalog,
            query = "Puting Amag",
            type = DiseaseType.LEAF_DISEASE,
        )

        assertEquals(listOf("white-molds"), matches.map { it.id })
    }
}
