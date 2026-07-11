package com.eggplant.detector.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggplant.detector.R
import com.eggplant.detector.domain.model.NavigationItem
import com.eggplant.detector.core.ui.motion.LocalEggplantMotion

@Composable
fun BottomNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val motion = LocalEggplantMotion.current
    val items = listOf(
        NavigationItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Filled.Home),
        NavigationItem(Routes.LIBRARY, stringResource(R.string.nav_library), Icons.AutoMirrored.Filled.MenuBook),
        NavigationItem(Routes.CAMERA, stringResource(R.string.nav_camera), Icons.Filled.CameraAlt),
        NavigationItem(Routes.HISTORY, stringResource(R.string.nav_history), Icons.Filled.Update),
        NavigationItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings),
    )
    val cameraDescription = stringResource(R.string.open_camera)

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val pillWidth by animateDpAsState(if (selected) 62.dp else 54.dp, tween(motion.fastMillis), label = "navPillWidth")
            val pillColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, tween(motion.fastMillis), label = "navPillColor")
            val iconSize by animateDpAsState(if (selected) 25.dp else 23.dp, tween(motion.fastMillis), label = "navIconSize")
            val description = if (item.route == Routes.CAMERA) cameraDescription else stringResource(R.string.navigate_to, item.label)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        onClick = { onNavigate(item.route) },
                        role = Role.Tab,
                    )
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                if (item.route == Routes.CAMERA) {
                    CameraIcon(item.icon)
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = pillColor,
                    ) {
                        Box(modifier = Modifier.size(width = pillWidth, height = 32.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }
                }
                Text(
                    item.label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CameraIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
