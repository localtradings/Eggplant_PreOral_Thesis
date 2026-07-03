package com.eggplant.detector.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CameraPage(
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
) {
    var flashEnabled by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF172E20), Color(0xFF07100B))))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        CameraLeafPreview(Modifier.fillMaxSize())
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CameraControl(Icons.AutoMirrored.Outlined.ArrowBack, "Close mock camera", onBack)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            CameraControl(
                if (flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                "Toggle mock flash",
            ) { flashEnabled = !flashEnabled }
            CameraControl(Icons.AutoMirrored.Outlined.HelpOutline, "Open camera help") { showHelp = true }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 294.dp, height = 390.dp)
                .border(3.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(34.dp)),
        )
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 86.dp, start = 28.dp, end = 28.dp),
            color = Color.Black.copy(alpha = .42f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                "Place one clear eggplant leaf inside the frame",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(46.dp),
        ) {
            CameraControl(Icons.Outlined.Collections, "Choose mock gallery image", onGallery)
            FloatingActionButton(
                onClick = onCapture,
                modifier = Modifier
                    .size(84.dp)
                    .semantics { contentDescription = "Capture mock scan" }
                    .border(5.dp, Color.White.copy(alpha = .7f), CircleShape),
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(38.dp))
            }
            Surface(color = Color.White.copy(alpha = .16f), shape = CircleShape) {
                Text(
                    "MOCK",
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Scan quality tips") },
            text = { Text("Use one clear leaf, even lighting, a steady angle, and a simple background. This camera is a UI-only mock.") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun CameraControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = .35f), shape = CircleShape) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }
}

@Composable
private fun CameraLeafPreview(modifier: Modifier) {
    Canvas(modifier) {
        repeat(18) { index ->
            drawCircle(
                color = Color(0xFF6A9B59).copy(alpha = .08f + (index % 4) * .03f),
                radius = size.minDimension * (.07f + (index % 3) * .02f),
                center = Offset(
                    size.width * ((index % 5) / 4f),
                    size.height * ((index / 5) / 3f),
                ),
            )
        }
        val leaf = Path().apply {
            moveTo(size.width * .25f, size.height * .63f)
            cubicTo(size.width * .28f, size.height * .3f, size.width * .72f, size.height * .23f, size.width * .78f, size.height * .48f)
            cubicTo(size.width * .72f, size.height * .77f, size.width * .39f, size.height * .8f, size.width * .25f, size.height * .63f)
        }
        drawPath(leaf, brush = Brush.linearGradient(listOf(Color(0xFF65A457), Color(0xFF285E34))))
        drawLine(
            color = Color(0xFFB7D792),
            start = Offset(size.width * .28f, size.height * .62f),
            end = Offset(size.width * .69f, size.height * .42f),
            strokeWidth = 6f,
        )
        repeat(10) { index ->
            drawCircle(
                color = Color(0xFF7F5834),
                radius = size.minDimension * .013f,
                center = Offset(size.width * (.39f + (index % 4) * .075f), size.height * (.45f + (index / 4) * .08f)),
            )
        }
    }
}
