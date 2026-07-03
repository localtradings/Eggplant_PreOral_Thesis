package com.eggplant.detector.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggplant.detector.AppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.components.LastScanCard
import com.eggplant.detector.components.QuickActionCard
import com.eggplant.detector.theme.EggplantLavender
import com.eggplant.detector.theme.EggplantPurple
import com.eggplant.detector.theme.Ink
import com.eggplant.detector.theme.LeafGreen
import com.eggplant.detector.theme.LeafGreenSoft

@Composable
fun HomePage(
    viewModel: AppViewModel,
    onScan: () -> Unit,
    onLibrary: () -> Unit,
    onHistory: () -> Unit,
) {
    val lastScan by viewModel.lastScan.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HomeHeader() }
        item { HeroCard(onScan) }
        item { QuickActions(onLibrary, onHistory) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Last Scan",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "View All  ›",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
        lastScan?.let { result ->
            item { LastScanCard(result = result, onClick = onHistory) }
        }
        item { ScanTip() }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EggplantLogo(Modifier.size(54.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Eggplant",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 27.sp),
            )
            Text(
                "Disease Detector",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = LeafGreen,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
        ) {
            IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    contentDescription = "Notifications display only",
                    tint = Ink,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
    }
}

@Composable
private fun EggplantLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawOval(
            brush = Brush.linearGradient(listOf(Color(0xFF744CA0), Color(0xFF2B163F))),
            topLeft = Offset(size.width * .18f, size.height * .28f),
            size = Size(size.width * .58f, size.height * .62f),
        )
        val leaf = Path().apply {
            moveTo(size.width * .6f, size.height * .32f)
            lineTo(size.width * .82f, size.height * .18f)
            lineTo(size.width * .72f, size.height * .42f)
            close()
        }
        drawPath(leaf, color = Color(0xFF74B947))
        drawCircle(Color(0xFF91C95F), size.minDimension * .12f, Offset(size.width * .25f, size.height * .33f))
    }
}

@Composable
private fun HeroCard(onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.31f),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3E7)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.hero_leaf),
                contentDescription = "Eggplant leaf disease hero photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(.64f)
                    .padding(start = 22.dp, top = 30.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        append("Detect problems.\n")
                        withStyle(SpanStyle(color = LeafGreen)) { append("Protect") }
                        append(" your crop.")
                    },
                    color = Ink,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, lineHeight = 27.sp),
                )
                Text(
                    "Scan your eggplant leaves\nto detect diseases early and\nget treatment advice.",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                )
                Button(
                    onClick = onScan,
                    modifier = Modifier.width(160.dp).height(49.dp),
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EggplantPurple),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Leaf Now", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = LeafGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Fast  •  Accurate  •  Easy to Use",
                        color = Color(0xFF5F6471),
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onLibrary: () -> Unit, onHistory: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(134.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickActionCard("Learn Diseases", "Identify and learn\nabout diseases", Icons.AutoMirrored.Outlined.MenuBook, onLibrary, Modifier.weight(1f), LeafGreenSoft, LeafGreen)
            ActionDivider()
            QuickActionCard("View History", "Check your\npast scans", Icons.Outlined.History, onHistory, Modifier.weight(1f), EggplantLavender, EggplantPurple)
            ActionDivider()
            QuickActionCard("Care Guide", "Treatment and\nprevention tips", Icons.Outlined.Spa, null, Modifier.weight(1f), LeafGreenSoft, LeafGreen)
            ActionDivider()
            QuickActionCard("Offline Mode", "Use the app\nwithout internet", Icons.Outlined.CloudDownload, null, Modifier.weight(1f), EggplantLavender, EggplantPurple)
        }
    }
}

@Composable
private fun ActionDivider() {
    Box(Modifier.width(1.dp).height(92.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun ScanTip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFFE5F6E5), Color(0xFFF2F7EE))),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Tip: Take clear photos in good lighting for\nbetter and more accurate results.",
            modifier = Modifier.weight(1f),
            color = Ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
        )
        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = EggplantPurple, modifier = Modifier.size(31.dp))
    }
}
