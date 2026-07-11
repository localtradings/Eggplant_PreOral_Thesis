package com.eggplant.detector.feature.history

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.R
import com.eggplant.detector.app.CloudActionState
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.core.ui.components.DiseaseArtwork
import com.eggplant.detector.core.ui.components.FilterChips
import com.eggplant.detector.core.ui.components.HistoryCard
import com.eggplant.detector.core.ui.components.SearchBar
import com.eggplant.detector.core.ui.motion.LocalEggplantMotion
import com.eggplant.detector.domain.model.GlobalScan
import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.domain.model.ScanResult
import com.eggplant.detector.data.cloud.SafeJpeg
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScanHistoryScreen(
    viewModel: EggplantAppViewModel,
    onResultClick: (ScanResult) -> Unit,
    onGlobalClick: (GlobalScan) -> Unit,
) {
    val motion = LocalEggplantMotion.current
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val tabs = listOf(stringResource(R.string.my_scans), stringResource(R.string.global_scans))
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.history_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { if (motion.spatialMovement) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index) } },
                    text = { Text(title, fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), key = { it }) { page ->
            if (page == 0) MyScansPage(viewModel, onResultClick) else GlobalScansPage(viewModel, onGlobalClick)
        }
    }
}

@Composable
private fun MyScansPage(viewModel: EggplantAppViewModel, onResultClick: (ScanResult) -> Unit) {
    val history by viewModel.history.collectAsState()
    val requests by viewModel.diseaseRequests.collectAsState()
    val allLabel = stringResource(R.string.all)
    val leafLabel = stringResource(R.string.leaf_disease)
    val fruitLabel = stringResource(R.string.fruit_disease)
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(allLabel) }
    val category = when (selectedFilter) { leafLabel -> ScanCategory.LEAF_DISEASE; fruitLabel -> ScanCategory.FRUIT_DISEASE; else -> null }
    val results = history.filter { result -> (category == null || result.category == category) && (query.isBlank() || result.name.contains(query.trim(), true)) }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SearchBar(query, { query = it }, stringResource(R.string.history_search)) }
        item { FilterChips(listOf(allLabel, leafLabel, fruitLabel), selectedFilter, { selectedFilter = it }) }
        if (results.isNotEmpty()) {
            items(results, key = { "scan-${it.id}" }) { result -> HistoryCard(result, { onResultClick(result) }, Modifier.animateItem()) }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DiseaseArtwork("empty-history", Modifier.fillMaxWidth(.46f))
                    Text(stringResource(R.string.no_history), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (requests.isNotEmpty()) {
            item { Text(stringResource(R.string.request_this_disease), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            items(requests, key = { "request-${it.id}" }) { request ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(request.requestedName ?: stringResource(R.string.request_name_not_provided), fontWeight = FontWeight.Bold)
                                Text(request.notes.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            Text(request.status.replace('_', ' '), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        if (request.status in setOf("QUEUED", "UPLOADING", "RETRY")) {
                            LinearProgressIndicator(
                                progress = { request.uploadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        request.adminNote?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (request.status in setOf("FAILED", "RETRY")) {
                                TextButton(onClick = { viewModel.retryDiseaseRequest(request.id) }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                            if (request.status in setOf("QUEUED", "RETRY")) {
                                TextButton(onClick = { viewModel.cancelDiseaseRequest(request.id) }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalScansPage(viewModel: EggplantAppViewModel, onGlobalClick: (GlobalScan) -> Unit) {
    val scans by viewModel.globalScans.collectAsState()
    val rankings by viewModel.globalRankings.collectAsState()
    val action by viewModel.cloudActionState.collectAsState()
    val feedState by viewModel.globalFeedState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = scans.filter { query.isBlank() || it.diseaseName.contains(query.trim(), true) }
    PullToRefreshBox(
        isRefreshing = feedState.isLoading,
        onRefresh = viewModel::refreshGlobalScans,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SearchBar(query, { query = it }, stringResource(R.string.library_search)) }
            if (rankings.isNotEmpty()) {
                item { Text(stringResource(R.string.community_rankings), style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rankings.take(3).forEachIndexed { index, ranking ->
                            Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(12.dp)) { Text("#${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(ranking.diseaseName, maxLines = 1); Text("${ranking.scanCount}", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.global_scans), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = viewModel::refreshGlobalScans) { Icon(Icons.Outlined.Refresh, null); Text(stringResource(R.string.refresh)) }
                }
            }
            when {
                scans.isEmpty() && feedState.isLoading -> items(3) { GlobalSkeleton() }
                filtered.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (scans.isEmpty()) Icons.Outlined.CloudOff else Icons.Outlined.Public, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.global_scans_empty), modifier = Modifier.padding(top = 12.dp))
                        Button(onClick = viewModel::refreshGlobalScans, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.refresh)) }
                    }
                }
                else -> items(filtered, key = { "global-${it.id}" }) { scan -> GlobalScanCard(scan) { onGlobalClick(scan) } }
            }
            if (feedState.lastErrorCode != null) {
                item {
                    Text(
                        stringResource(R.string.global_scans_refresh_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (feedState.hasMore && query.isBlank()) {
                item {
                    TextButton(
                        onClick = viewModel::loadMoreGlobalScans,
                        enabled = !feedState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (feedState.isLoading) stringResource(R.string.loading_more) else stringResource(R.string.load_more))
                    }
                }
            }
            item {
                Text(
                    if (feedState.lastUpdatedAt == null) stringResource(R.string.global_scans_not_loaded)
                    else stringResource(R.string.last_updated_cached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GlobalScanCard(scan: GlobalScan, onClick: () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, scan.photoPath) {
        value = withContext(Dispatchers.IO) {
            scan.photoPath?.let(::File)?.let { SafeJpeg.decodeSampled(it, 640) }
        }
    }
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), stringResource(R.string.shared_eggplant_photo), Modifier.fillMaxWidth().aspectRatio(16 / 10f), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxWidth().aspectRatio(16 / 10f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Public, null) }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(scan.diseaseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(scan.publishedAt.take(10), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("${scan.confidence}%", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GlobalSkeleton() {
    val motion = LocalEggplantMotion.current
    val alpha = if (motion.spatialMovement) rememberInfiniteTransition(label = "globalSkeleton").animateFloat(.45f, .85f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "alpha").value else .6f
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .16f), RoundedCornerShape(16.dp)))
        Box(Modifier.fillMaxWidth(.55f).height(20.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .16f), RoundedCornerShape(8.dp)))
    }
}
