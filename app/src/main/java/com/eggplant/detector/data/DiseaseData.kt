package com.eggplant.detector.data

import com.eggplant.detector.model.Disease
import com.eggplant.detector.model.DiseaseType

object DiseaseData {
    val diseases: List<Disease> = listOf(
        Disease(
            id = "leaf-spot",
            name = "Leaf Spot",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Brown or black spots on leaves that may cause yellowing.",
            signs = listOf("Circular brown spots", "Yellow tissue around spots", "Dry or brittle centers"),
            treatment = "This is mock educational guidance. Remove badly affected leaves, avoid wetting foliage, and consult a local agricultural specialist.",
            prevention = "Improve airflow, space plants well, and water near the soil rather than over the leaves.",
        ),
        Disease(
            id = "mosaic-virus",
            name = "Mosaic Virus",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Yellow-green mosaic patterns and distorted or curled leaves.",
            signs = listOf("Mottled yellow-green color", "Distorted leaves", "Reduced growth"),
            treatment = "This is mock educational guidance. Isolate suspicious plants and consult a local agricultural specialist before taking action.",
            prevention = "Use clean tools, monitor insects, and avoid handling healthy plants immediately after affected plants.",
        ),
        Disease(
            id = "white-molds",
            name = "White Molds",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "White, cottony mold growth on leaves, stems, or surface.",
            signs = listOf("White cotton-like growth", "Soft affected tissue", "Wilting near infected stems"),
            treatment = "This is mock educational guidance. Remove severely affected material and ask a local agricultural specialist about safe management.",
            prevention = "Reduce prolonged moisture, improve airflow, and avoid crowding plants.",
        ),
        Disease(
            id = "wilt",
            name = "Wilt",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Leaves suddenly droop and the plant may become weak.",
            signs = listOf("Persistent drooping", "Yellow lower leaves", "Weak stems"),
            treatment = "This is mock educational guidance. Check soil moisture and roots, then consult a local agricultural specialist for diagnosis.",
            prevention = "Use clean growing material, avoid waterlogging, and remove severely affected plant debris safely.",
        ),
        Disease(
            id = "insect-pest",
            name = "Insect Pest",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Chewed leaves, holes, or general insect damage on the plant.",
            signs = listOf("Irregular holes", "Chewed leaf edges", "Visible insects or eggs"),
            treatment = "This is mock educational guidance. Remove visible insects where safe and ask a local agricultural specialist which control is appropriate.",
            prevention = "Inspect leaf undersides regularly and keep the growing area clear of heavily damaged plant material.",
        ),
        Disease(
            id = "melon-thrips",
            name = "Melon Thrips",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Silver streaks, leaf curling, and tiny insects on leaves.",
            signs = listOf("Silvery streaks", "Curled young leaves", "Tiny insects underneath leaves"),
            treatment = "This is mock educational guidance. Inspect leaves closely and ask a local agricultural specialist for an appropriate control method.",
            prevention = "Monitor young leaves regularly and keep weeds and heavily damaged leaves away from the crop.",
        ),
        Disease(
            id = "fruit-rot",
            name = "Fruit Rot",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Soft, dark, and rotting areas on the fruit.",
            signs = listOf("Soft dark patches", "Sunken fruit tissue", "Rapidly enlarging decay"),
            treatment = "This is mock educational guidance. Separate affected fruit and consult a local agricultural specialist about crop-safe management.",
            prevention = "Keep fruit off wet soil, handle fruit gently, and improve airflow around plants.",
        ),
        Disease(
            id = "fruit-borer",
            name = "Fruit Borer",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Holes on the fruit with internal feeding and damage.",
            signs = listOf("Entry holes", "Material around the opening", "Internal feeding damage"),
            treatment = "This is mock educational guidance. Remove visibly affected fruit and ask a local agricultural specialist for suitable control options.",
            prevention = "Inspect developing fruit frequently and remove damaged fruit from the growing area.",
        ),
    )

    fun filter(query: String, type: DiseaseType?): List<Disease> {
        val normalizedQuery = query.trim()
        return diseases.filter { disease ->
            val matchesType = type == null || disease.type == type
            val matchesQuery = normalizedQuery.isEmpty() ||
                disease.name.contains(normalizedQuery, ignoreCase = true) ||
                disease.symptomPreview.contains(normalizedQuery, ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    fun byId(id: String): Disease? = diseases.firstOrNull { it.id == id }
}
