package com.eggplant.detector.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eggplant.detector.R
import com.eggplant.detector.model.ScanCategory

@Composable
fun DiseaseArtwork(
    artworkKey: String,
    modifier: Modifier = Modifier,
) {
    val resource = diseaseDrawable(artworkKey)
    val description = diseaseDescription(artworkKey)

    if (resource != null) {
        Image(
            painter = painterResource(resource),
            contentDescription = description,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFE9F5E8), Color(0xFFF1ECF8)),
                    ),
                ),
        )
    }
}

@Composable
fun ResultArtwork(category: ScanCategory, name: String, modifier: Modifier = Modifier) {
    val key = when (name.lowercase()) {
        "leaf spot", "early blight" -> "leaf-spot"
        "mosaic virus" -> "mosaic-virus"
        "white molds" -> "white-molds"
        "wilt" -> "wilt"
        "insect pest" -> "insect-pest"
        "melon thrips" -> "melon-thrips"
        "fruit rot" -> "fruit-rot"
        "fruit borer" -> "fruit-borer"
        else -> if (category == ScanCategory.FRUIT_DISEASE) "fruit-borer" else ""
    }
    DiseaseArtwork(artworkKey = key, modifier = modifier)
}

@DrawableRes
private fun diseaseDrawable(artworkKey: String): Int? = when (artworkKey) {
    "leaf-spot" -> R.drawable.disease_leaf_spot
    "mosaic-virus" -> R.drawable.disease_mosaic_virus
    "white-molds" -> R.drawable.disease_white_molds
    "wilt" -> R.drawable.disease_wilt
    "insect-pest" -> R.drawable.disease_insect_pest
    "melon-thrips" -> R.drawable.disease_melon_thrips
    "fruit-rot" -> R.drawable.disease_fruit_rot
    "fruit-borer" -> R.drawable.disease_fruit_borer
    else -> null
}

private fun diseaseDescription(artworkKey: String): String? = when (artworkKey) {
    "leaf-spot" -> "Leaf Spot disease photo"
    "mosaic-virus" -> "Mosaic Virus disease photo"
    "white-molds" -> "White Molds disease photo"
    "wilt" -> "Wilt disease photo"
    "insect-pest" -> "Insect Pest disease photo"
    "melon-thrips" -> "Melon Thrips disease photo"
    "fruit-rot" -> "Fruit Rot disease photo"
    "fruit-borer" -> "Fruit Borer disease photo"
    else -> null
}
