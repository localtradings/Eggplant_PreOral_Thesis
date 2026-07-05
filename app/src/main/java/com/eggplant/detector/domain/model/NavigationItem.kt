package com.eggplant.detector.domain.model

import androidx.compose.ui.graphics.vector.ImageVector

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
