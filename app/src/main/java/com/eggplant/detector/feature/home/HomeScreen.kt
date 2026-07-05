package com.eggplant.detector.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.core.ui.components.LastScanCard
import com.eggplant.detector.core.ui.components.QuickActionCard
import com.eggplant.detector.core.ui.theme.EggplantLavender
import com.eggplant.detector.core.ui.theme.EggplantPurple
import com.eggplant.detector.core.ui.theme.Ink
import com.eggplant.detector.core.ui.theme.LeafGreen
import com.eggplant.detector.core.ui.theme.LeafGreenSoft

@Composable
fun HomeScreen(
    viewModel: EggplantAppViewModel,
    onScan: () -> Unit,
    onLibrary: () -> Unit,
    onHistory: () -> Unit,
    onNotifications: () -> Unit,
    onCareGuide: () -> Unit,
    onOfflineUse: () -> Unit,
    onLastScan: (String) -> Unit,
    listState: LazyListState,
) {
    val lastScan by viewModel.lastScan.collectAsState()
    val homeDescription = stringResource(R.string.home_content)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = homeDescription },
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HomeHeader(onNotifications) }
        item { HeroCard(onScan) }
        item { QuickActions(onLibrary, onHistory, onCareGuide, onOfflineUse) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.last_scan),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onHistory) {
                    Text(stringResource(R.string.view_all), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
        lastScan?.let { result ->
            item { LastScanCard(result = result, onClick = { onLastScan(result.id) }) }
        }
        item { ScanTip() }
    }
}

@Composable
private fun HomeHeader(onNotifications: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.eggplant_logo),
            contentDescription = stringResource(R.string.logo_description),
            modifier = Modifier.size(54.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_eggplant),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 27.sp),
            )
            Text(
                stringResource(R.string.home_detector),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = LeafGreen,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
        ) {
            IconButton(onClick = onNotifications, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    contentDescription = stringResource(R.string.open_notifications),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(onScan: () -> Unit) {
    val headline = stringResource(R.string.home_headline)
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.31f),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3E7)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.hero_leaf),
                contentDescription = stringResource(R.string.hero_description),
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
                        append(headline.substringBefore('\n'))
                        append("\n")
                        withStyle(SpanStyle(color = LeafGreen)) { append(headline.substringAfter('\n')) }
                    },
                    color = Ink,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp, lineHeight = 27.sp),
                )
                Text(
                    stringResource(R.string.home_description),
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
                    Text(stringResource(R.string.scan_leaf_now), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                        stringResource(R.string.home_status),
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
private fun QuickActions(
    onLibrary: () -> Unit,
    onHistory: () -> Unit,
    onCareGuide: () -> Unit,
    onOfflineUse: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(154.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickActionCard(stringResource(R.string.learn_diseases), stringResource(R.string.learn_diseases_body), Icons.AutoMirrored.Outlined.MenuBook, onLibrary, Modifier.weight(1f), LeafGreenSoft, LeafGreen)
            ActionDivider()
            QuickActionCard(stringResource(R.string.view_history), stringResource(R.string.view_history_body), Icons.Outlined.History, onHistory, Modifier.weight(1f), EggplantLavender, EggplantPurple)
            ActionDivider()
            QuickActionCard(stringResource(R.string.care_guide), stringResource(R.string.care_guide_body), Icons.Outlined.Spa, onCareGuide, Modifier.weight(1f), LeafGreenSoft, LeafGreen)
            ActionDivider()
            QuickActionCard(stringResource(R.string.offline_use), stringResource(R.string.offline_use_body), Icons.Outlined.CloudDownload, onOfflineUse, Modifier.weight(1f), EggplantLavender, EggplantPurple)
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
            stringResource(R.string.home_tip),
            modifier = Modifier.weight(1f),
            color = Ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
        )
        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = EggplantPurple, modifier = Modifier.size(31.dp))
    }
}
