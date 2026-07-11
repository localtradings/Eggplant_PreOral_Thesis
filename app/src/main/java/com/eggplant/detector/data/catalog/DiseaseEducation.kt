package com.eggplant.detector.data.catalog

import com.eggplant.detector.domain.model.DiseaseReference

internal data class DiseaseEducation(
    val causes: String,
    val guidance: String,
    val whenToAct: String,
    val disclaimer: String,
    val references: List<DiseaseReference>,
)

internal object DiseaseEducationCatalog {
    private const val UC_EGGPLANT = "https://ipm.ucanr.edu/agriculture/eggplant/"
    private const val UC_WILT = "https://ipm.ucanr.edu/agriculture/eggplant/verticillium-wilt/"
    private const val UC_THRIPS = "https://ipm.ucanr.edu/agriculture/eggplant/thrips/"
    private const val UMN_EGGPLANT = "https://apps.extension.umn.edu/garden/diagnose/plant/vegetable/eggplant/stemdiscolored.html"
    private const val UF_MELON_THRIPS = "https://edis.ifas.ufl.edu/publication/IN292"
    private const val PH_BORER = "https://rfo12.da.gov.ph/?p=5012"

    private val disclaimer = mapOf(
        "en" to "This result is a screening aid, not a definitive diagnosis. Similar symptoms can have different causes. Consult a qualified local agricultural specialist before applying any pesticide or treatment.",
        "fil" to "Pantulong lamang sa paunang pagsusuri ang resultang ito at hindi tiyak na diagnosis. Maaaring magkapareho ang sintomas ng magkaibang sanhi. Kumonsulta sa kwalipikadong lokal na espesyalista sa agrikultura bago gumamit ng pestisidyo o paggamot.",
    )

    fun get(diseaseId: String, languageTag: String): DiseaseEducation {
        val fil = languageTag == "fil"
        val content = when (diseaseId) {
            "leaf-spot" -> if (fil) Triple(
                "Maaaring dulot ng Phomopsis o ibang fungal/bacterial pathogens; mas lumalala sa basang dahon at mahalumigmig na kondisyon.",
                "Ihiwalay ang malubhang apektadong bahagi, panatilihing tuyo ang dahon, at linisin ang kagamitan. Ipakita ang halaman sa agricultural specialist para makumpirma ang sanhi.",
                "Kumilos kapag mabilis dumadami ang batik, nalalagas ang dahon, o may sugat din ang tangkay o bunga.",
            ) else Triple(
                "Leaf spots may be caused by Phomopsis or other fungal or bacterial pathogens and often worsen with prolonged leaf wetness and humid conditions.",
                "Isolate heavily affected material, keep foliage dry, and clean tools. Ask an agricultural specialist to confirm the cause before choosing a control.",
                "Act when spots spread quickly, leaves drop, or stems and fruit also develop lesions.",
            )
            "mosaic-virus" -> if (fil) Triple(
                "Ang mosaic symptoms ay maaaring dulot ng plant viruses na naipapasa sa kontaminadong binhi, kagamitan, halaman, o ilang insektong vector.",
                "Ihiwalay ang kahina-hinalang halaman, linisin ang kamay at kagamitan, at huwag kumuha ng pantanim mula rito. Walang lunas na direktang nag-aalis ng virus sa apektadong halaman.",
                "Kumilos agad kapag may mosaic pattern, baluktot na bagong dahon, o mabilis na paghina sa maraming halaman.",
            ) else Triple(
                "Mosaic symptoms can be caused by plant viruses spread through infected seed, tools, plant material, or certain insect vectors.",
                "Isolate suspicious plants, sanitize hands and tools, and do not propagate from them. There is no treatment that removes a virus from an infected plant.",
                "Act promptly when mosaic patterns, distorted new growth, or rapid decline appear across multiple plants.",
            )
            "white-molds" -> if (fil) Triple(
                "Karaniwang kaugnay ng Sclerotinia at matagal na lamig, halumigmig, siksik na tanim, o basang tisyu; maaaring mabuhay nang matagal ang sclerotia sa lupa at debris.",
                "Alisin at ibukod ang apektadong halaman nang hindi ikinakalat ang itim na sclerotia. Bawasan ang pagkabasa at pagandahin ang airflow; huwag basta i-compost ang apektadong materyal.",
                "Kumilos agad kapag may puting tila bulak, malambot na bulok na bahagi, biglang pagkalanta, o itim na sclerotia.",
            ) else Triple(
                "White mold is commonly associated with Sclerotinia and cool, humid, crowded, or persistently wet conditions; its sclerotia can persist in soil and debris.",
                "Remove and isolate affected plants without spreading black sclerotia. Reduce moisture and improve airflow; do not casually compost infected material.",
                "Act immediately when cottony growth, soft rot, sudden wilting, or black sclerotia appear.",
            )
            "wilt" -> if (fil) Triple(
                "Maaaring manggaling sa soilborne vascular fungi gaya ng Verticillium, root rot, pinsala sa ugat, o problema sa tubig; kailangan ng pagsusuri para matukoy ang sanhi.",
                "Suriin muna ang moisture at ugat. Ihiwalay ang apektadong halaman at iwasang ilipat ang lupa sa malusog na tanim habang kumokonsulta sa espesyalista.",
                "Kumilos kapag hindi bumabalik ang sigla matapos diligan nang tama, may paninilaw at brown vascular tissue, o kumakalat ang pagkalanta.",
            ) else Triple(
                "Wilt can result from soilborne vascular fungi such as Verticillium, root rot, root injury, or water stress; examination is needed to distinguish them.",
                "Check moisture and roots first. Isolate affected plants and avoid moving soil to healthy beds while seeking specialist confirmation.",
                "Act when plants do not recover after correct watering, yellowing and brown vascular tissue appear, or wilting spreads.",
            )
            "insect-pest" -> if (fil) Triple(
                "Maaaring dulot ng iba’t ibang ngumunguya o sumisipsip na insekto. Magkaiba ang ligtas na kontrol depende sa eksaktong peste at yugto nito.",
                "Suriin ang magkabilang panig ng dahon, usbong, tangkay, at bunga. Kunan ng malinaw na larawan ang insekto o itlog at gumamit muna ng sanitation at physical removal kung ligtas.",
                "Kumilos kapag mabilis dumadami ang pinsala, apektado ang bagong usbong o bunga, o nalalanta ang halaman.",
            ) else Triple(
                "Damage may come from several chewing or sap-feeding insects. Safe management differs by the exact pest and life stage.",
                "Inspect both leaf surfaces, shoots, stems, and fruit. Photograph insects or eggs clearly and start with sanitation or physical removal where safe.",
                "Act when damage spreads rapidly, new growth or fruit is affected, or the plant begins to wilt.",
            )
            "melon-thrips" -> if (fil) Triple(
                "Ang Thrips palmi ay napakaliit na insektong sumisipsip na maaaring magdulot ng bronze o pilak na peklat, pagkulubot, at pinsala sa dahon at bunga.",
                "Gumamit ng magnifying lens sa ilalim ng dahon at usbong. Alisin ang damo at malubhang apektadong bahagi at kumonsulta bago gumamit ng insecticide upang maprotektahan ang beneficial insects.",
                "Kumilos kapag maraming thrips ang nakikita, nababaluktot ang bagong tubo, o mabilis lumalala ang bronzing at scarring.",
            ) else Triple(
                "Thrips palmi is a tiny sap-feeding insect that can cause bronze or silver scarring, crinkling, and damage to leaves and fruit.",
                "Use magnification to inspect leaf undersides and shoots. Remove weeds and severely damaged material, and seek advice before insecticide use to protect beneficial insects.",
                "Act when thrips are numerous, new growth is deformed, or bronzing and scarring worsen quickly.",
            )
            "fruit-rot" -> if (fil) Triple(
                "Ang malambot o lumulubog na bulok ay maaaring fungal o oomycete disease at lumalala dahil sa sugat, basang bunga, maruming kagamitan, o mahinang airflow.",
                "Ihiwalay ang apektadong bunga, iwasang ilagay sa compost o malapit sa ani, linisin ang kagamitan, at panatilihing tuyo at maaliwalas ang bunga.",
                "Kumilos agad kapag mabilis lumalaki ang basang sugat, may amag o amoy, o maraming bunga ang sabay na naaapektuhan.",
            ) else Triple(
                "Soft or sunken fruit rot may be fungal or oomycete disease and can worsen after injury, persistent wetness, contaminated tools, or poor airflow.",
                "Separate affected fruit, keep it away from harvest and casual compost, sanitize tools, and keep fruit dry and ventilated.",
                "Act immediately when water-soaked lesions expand quickly, mold or odor develops, or several fruit are affected.",
            )
            "fruit-borer" -> if (fil) Triple(
                "Ang eggplant fruit and shoot borer ay nangingitlog sa halaman; ang larva ay bumubutas at nagtatago sa usbong o bunga kaya mahirap kontrolin kapag nasa loob na.",
                "Regular na inspeksyunin ang usbong at bunga, alisin at ligtas na itapon ang may butas o larva, panatilihin ang field sanitation, at gumamit ng integrated pest management.",
                "Kumilos kapag may sariwang butas, dumi sa pasukan, nalalantang usbong, o sunod-sunod na nasisirang bunga.",
            ) else Triple(
                "Eggplant fruit and shoot borer larvae tunnel into shoots or fruit and remain hidden, making established infestations difficult to manage.",
                "Inspect shoots and fruit frequently, remove and safely dispose of tunneled material, maintain field sanitation, and use integrated pest management.",
                "Act when fresh entry holes, frass, wilted shoots, or repeated fruit damage appear.",
            )
            else -> Triple("", "Consult a qualified local agricultural specialist.", "Act if symptoms spread or the plant declines.")
        }
        val references = when (diseaseId) {
            "wilt" -> listOf(reference("UC Statewide IPM", "Verticillium Wilt / Eggplant", UC_WILT))
            "melon-thrips" -> listOf(
                reference("University of Florida IFAS", "Melon Thrips", UF_MELON_THRIPS),
                reference("UC Statewide IPM", "Thrips / Eggplant", UC_THRIPS),
            )
            "fruit-borer" -> listOf(reference("Philippine Department of Agriculture", "Eggplant Fruit and Shoot Borer", PH_BORER))
            "leaf-spot", "fruit-rot", "white-molds" -> listOf(reference("University of Minnesota Extension", "Eggplant disease diagnosis", UMN_EGGPLANT))
            else -> listOf(reference("UC Statewide IPM", "Eggplant Pest Management Guidelines", UC_EGGPLANT))
        }
        return DiseaseEducation(content.first, content.second, content.third, disclaimer.getValue(if (fil) "fil" else "en"), references)
    }

    private fun reference(publisher: String, title: String, url: String) = DiseaseReference(publisher, title, url)
}
