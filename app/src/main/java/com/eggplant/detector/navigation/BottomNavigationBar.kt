package com.eggplant.detector.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggplant.detector.model.NavigationItem

private val items = listOf(
    NavigationItem(Routes.HOME, "Home", Icons.Filled.Home),
    NavigationItem(Routes.LIBRARY, "Library", Icons.AutoMirrored.Filled.MenuBook),
    NavigationItem(Routes.CAMERA, "Camera", Icons.Filled.CameraAlt),
    NavigationItem(Routes.HISTORY, "History", Icons.Filled.Update),
    NavigationItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(88.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .shadow(14.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {}
        Row(
            modifier = Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { item ->
                if (item.route == Routes.CAMERA) {
                    CameraNavigationItem(onNavigate, Modifier.weight(1f))
                } else {
                    StandardNavigationItem(
                        label = item.label,
                        icon = item.icon,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .height(72.dp)
            .semantics { contentDescription = "Navigate to $label" }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.height(4.dp).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier.size(width = 26.dp, height = 3.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {}
            }
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
        Text(
            label,
            color = color,
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CameraNavigationItem(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Surface(
            modifier = Modifier.offset(y = (-5).dp).size(66.dp),
            shape = RoundedCornerShape(50),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE8E5EE)),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                FloatingActionButton(
                    onClick = { onNavigate(Routes.CAMERA) },
                    modifier = Modifier
                        .size(56.dp)
                        .semantics { contentDescription = "Open mock camera" },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(27.dp))
                }
            }
        }
        Text(
            "Camera",
            modifier = Modifier.offset(y = (-3).dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
        )
    }
}
