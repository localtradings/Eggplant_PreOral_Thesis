package com.eggplant.detector.data.catalog

import com.eggplant.detector.domain.model.Disease
import com.eggplant.detector.domain.model.DiseaseType
import java.util.Locale

object DiseaseCatalog {
    internal val englishDiseases: List<Disease> = listOf(
        Disease(
            id = "leaf-spot",
            name = "Leaf Spot",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Brown or black spots on leaves that may cause yellowing.",
            signs = listOf("Circular brown spots", "Yellow tissue around spots", "Dry or brittle centers"),
            treatment = "Remove badly affected leaves, avoid wetting foliage, and consult a local agricultural specialist.",
            prevention = "Improve airflow, space plants well, and water near the soil rather than over the leaves.",
        ),
        Disease(
            id = "mosaic-virus",
            name = "Mosaic Virus",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Yellow-green mosaic patterns and distorted or curled leaves.",
            signs = listOf("Mottled yellow-green color", "Distorted leaves", "Reduced growth"),
            treatment = "Isolate suspicious plants and consult a local agricultural specialist before taking action.",
            prevention = "Use clean tools, monitor insects, and avoid handling healthy plants immediately after affected plants.",
        ),
        Disease(
            id = "white-molds",
            name = "White Molds",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "White, cottony mold growth on leaves, stems, or surface.",
            signs = listOf("White cotton-like growth", "Soft affected tissue", "Wilting near infected stems"),
            treatment = "Remove severely affected material and ask a local agricultural specialist about safe management.",
            prevention = "Reduce prolonged moisture, improve airflow, and avoid crowding plants.",
        ),
        Disease(
            id = "wilt",
            name = "Wilt",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Leaves suddenly droop and the plant may become weak.",
            signs = listOf("Persistent drooping", "Yellow lower leaves", "Weak stems"),
            treatment = "Check soil moisture and roots, then consult a local agricultural specialist for diagnosis.",
            prevention = "Use clean growing material, avoid waterlogging, and remove severely affected plant debris safely.",
        ),
        Disease(
            id = "insect-pest",
            name = "Insect Pest",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Chewed leaves, holes, or general insect damage on the plant.",
            signs = listOf("Irregular holes", "Chewed leaf edges", "Visible insects or eggs"),
            treatment = "Remove visible insects where safe and ask a local agricultural specialist which control is appropriate.",
            prevention = "Inspect leaf undersides regularly and keep the growing area clear of heavily damaged plant material.",
        ),
        Disease(
            id = "melon-thrips",
            name = "Melon Thrips",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Silver streaks and tiny insects that can scar leaves, shoots, and fruit.",
            signs = listOf("Silvery streaks", "Curled young leaves", "Bronzed or scarred fruit", "Tiny insects underneath leaves"),
            treatment = "Inspect leaves closely and ask a local agricultural specialist for an appropriate control method.",
            prevention = "Monitor young leaves regularly and keep weeds and heavily damaged leaves away from the crop.",
        ),
        Disease(
            id = "fruit-rot",
            name = "Fruit Rot",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Soft, dark, and rotting areas on the fruit.",
            signs = listOf("Soft dark patches", "Sunken fruit tissue", "Rapidly enlarging decay"),
            treatment = "Separate affected fruit and consult a local agricultural specialist about crop-safe management.",
            prevention = "Keep fruit off wet soil, handle fruit gently, and improve airflow around plants.",
        ),
        Disease(
            id = "fruit-borer",
            name = "Fruit Borer",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Holes on the fruit with internal feeding and damage.",
            signs = listOf("Entry holes", "Material around the opening", "Internal feeding damage"),
            treatment = "Remove visibly affected fruit and ask a local agricultural specialist for suitable control options.",
            prevention = "Inspect developing fruit frequently and remove damaged fruit from the growing area.",
        ),
    )

    internal val filipinoDiseases: List<Disease> = listOf(
        Disease(
            id = "leaf-spot",
            name = "Leaf Spot",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Kayumanggi o itim na batik sa dahon na maaaring magdulot ng paninilaw.",
            signs = listOf("Bilog na kayumangging batik", "Dilaw na bahagi sa paligid", "Tuyo o marupok na gitna"),
            treatment = "Alisin ang malubhang apektadong dahon, iwasang basain ang mga dahon, at kumonsulta sa lokal na espesyalista sa agrikultura.",
            prevention = "Pagandahin ang daloy ng hangin, bigyan ng tamang pagitan ang halaman, at diligan malapit sa lupa."),
        Disease(
            id = "mosaic-virus",
            name = "Mosaic Virus",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Dilaw-berdeng mosaic na pattern at baluktot o kulubot na dahon.",
            signs = listOf("Halo-halong dilaw at berde", "Baluktot na dahon", "Mahinang paglaki"),
            treatment = "Ihiwalay ang kahina-hinalang halaman at kumonsulta sa espesyalista bago gumawa ng hakbang.",
            prevention = "Gumamit ng malinis na kagamitan, bantayan ang mga insekto, at linisin ang kamay matapos humawak ng apektadong halaman."),
        Disease(
            id = "white-molds",
            name = "Puting Amag",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Maputi at parang bulak na amag sa dahon, tangkay, o ibabaw.",
            signs = listOf("Puting tila bulak", "Malambot na apektadong bahagi", "Pagkalanta malapit sa tangkay"),
            treatment = "Alisin ang malubhang apektadong bahagi at humingi ng payo tungkol sa ligtas na pangangasiwa.",
            prevention = "Bawasan ang matagal na pagkabasa, pagandahin ang hangin, at iwasang magsiksikan ang halaman."),
        Disease(
            id = "wilt",
            name = "Pagkalanta",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Biglang yumuyuko ang dahon at maaaring manghina ang halaman.",
            signs = listOf("Patuloy na pagyuko", "Dilaw na ibabang dahon", "Mahinang tangkay"),
            treatment = "Suriin ang halumigmig ng lupa at ugat, saka kumonsulta sa espesyalista para sa wastong diagnosis.",
            prevention = "Gumamit ng malinis na materyal, iwasan ang sobrang tubig, at ligtas na alisin ang apektadong tira ng halaman."),
        Disease(
            id = "insect-pest",
            name = "Pesteng Insekto",
            type = DiseaseType.LEAF_DISEASE,
            symptomPreview = "Nguyang dahon, mga butas, o pinsala ng insekto sa halaman.",
            signs = listOf("Hindi pantay na butas", "Nguyang gilid ng dahon", "Nakikitang insekto o itlog"),
            treatment = "Ligtas na alisin ang nakikitang insekto at itanong sa espesyalista ang angkop na paraan ng pagkontrol.",
            prevention = "Regular na suriin ang ilalim ng dahon at linisin ang paligid ng malubhang napinsalang bahagi."),
        Disease(
            id = "melon-thrips",
            name = "Melon Thrips",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Pilak na guhit at maliliit na insektong maaaring makapinsala sa dahon, usbong, at bunga.",
            signs = listOf("Pilak na guhit", "Kulot na batang dahon", "Bronze o peklat sa bunga", "Maliit na insekto sa ilalim ng dahon"),
            treatment = "Suriing mabuti ang dahon at humingi ng payo para sa angkop na paraan ng pagkontrol.",
            prevention = "Regular na bantayan ang batang dahon at alisin ang damo at malubhang napinsalang dahon."),
        Disease(
            id = "fruit-rot",
            name = "Pagkabulok ng Bunga",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Malambot, maitim, at nabubulok na bahagi ng bunga.",
            signs = listOf("Malambot na maitim na bahagi", "Lumulubog na laman", "Mabilis lumaking pagkabulok"),
            treatment = "Ihiwalay ang apektadong bunga at kumonsulta tungkol sa ligtas na pangangasiwa ng pananim.",
            prevention = "Huwag hayaang dumikit sa basang lupa ang bunga, hawakan nang maingat, at pagandahin ang daloy ng hangin."),
        Disease(
            id = "fruit-borer",
            name = "Fruit Borer",
            type = DiseaseType.FRUIT_DISEASE,
            symptomPreview = "Mga butas sa bunga na may pinsala at pagkain sa loob.",
            signs = listOf("Butas na pasukan", "Dumi sa paligid ng butas", "Pinsala sa loob ng bunga"),
            treatment = "Alisin ang apektadong bunga at humingi ng payo tungkol sa angkop na paraan ng pagkontrol.",
            prevention = "Madalas na suriin ang umuunlad na bunga at alisin ang napinsalang bunga sa taniman."),
    )

    val diseases: List<Disease>
        get() = forLanguage(Locale.getDefault().language)

    fun forLanguage(languageTag: String): List<Disease> =
        if (languageTag in setOf("fil", "tl")) filipinoDiseases else englishDiseases

    fun filter(query: String, type: DiseaseType?): List<Disease> =
        filter(diseases, query, type)

    fun filter(diseases: List<Disease>, query: String, type: DiseaseType?): List<Disease> {
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
