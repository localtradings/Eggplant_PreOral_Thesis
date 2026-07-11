package com.eggplant.detector.feature.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.R
import com.eggplant.detector.domain.model.GlobalScan
import com.eggplant.detector.domain.model.ScanResult
import com.eggplant.detector.data.cloud.SafeJpeg
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.eggplant.detector.core.ui.motion.LocalEggplantMotion
import com.eggplant.detector.core.ui.stablePageForId

@Composable
fun MyScanDetailPager(results: List<ScanResult>, initialId: String?, onBack: () -> Unit) {
    if (results.isEmpty()) { ScanHistoryDetailsScreen(null, onBack); return }
    val ids = results.map(ScanResult::id)
    var selectedId by rememberSaveable(initialId) { mutableStateOf(initialId?.takeIf(ids::contains) ?: ids.first()) }
    val initial = stablePageForId(ids, selectedId, 0)
    val state = rememberPagerState(initialPage = initial) { results.size }
    val scope = rememberCoroutineScope()
    val motion = LocalEggplantMotion.current
    LaunchedEffect(ids) {
        val page = stablePageForId(ids, selectedId, state.currentPage)
        if (page != state.currentPage) state.scrollToPage(page)
        selectedId = ids[page]
    }
    LaunchedEffect(state.settledPage) { ids.getOrNull(state.settledPage)?.let { selectedId = it } }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.item_position, state.currentPage + 1, results.size), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        HorizontalPager(state, Modifier.weight(1f), key = { results[it].id }) { page -> ScanHistoryDetailsScreen(results[page], onBack) }
        DetailControls(
            state.currentPage,
            results.size,
            { scope.launch { if (motion.spatialMovement) state.animateScrollToPage(state.currentPage - 1) else state.scrollToPage(state.currentPage - 1) } },
            { scope.launch { if (motion.spatialMovement) state.animateScrollToPage(state.currentPage + 1) else state.scrollToPage(state.currentPage + 1) } },
        )
    }
}

@Composable
fun GlobalScanDetailPager(scans: List<GlobalScan>, initialId: String?, onBack: () -> Unit, onReport: (String) -> Unit) {
    if (scans.isEmpty()) { Column(Modifier.padding(24.dp)) { Text(stringResource(R.string.global_scans_empty)); Button(onClick = onBack) { Text(stringResource(R.string.back)) } }; return }
    val ids = scans.map(GlobalScan::id)
    var selectedId by rememberSaveable(initialId) { mutableStateOf(initialId?.takeIf(ids::contains) ?: ids.first()) }
    val initial = stablePageForId(ids, selectedId, 0)
    val state = rememberPagerState(initialPage = initial) { scans.size }
    val scope = rememberCoroutineScope()
    val motion = LocalEggplantMotion.current
    LaunchedEffect(ids) {
        val page = stablePageForId(ids, selectedId, state.currentPage)
        if (page != state.currentPage) state.scrollToPage(page)
        selectedId = ids[page]
    }
    LaunchedEffect(state.settledPage) { ids.getOrNull(state.settledPage)?.let { selectedId = it } }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.item_position, state.currentPage + 1, scans.size), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        HorizontalPager(state, Modifier.weight(1f), key = { scans[it].id }) { page -> GlobalScanDetail(scans[page], onBack, onReport) }
        DetailControls(
            state.currentPage,
            scans.size,
            { scope.launch { if (motion.spatialMovement) state.animateScrollToPage(state.currentPage - 1) else state.scrollToPage(state.currentPage - 1) } },
            { scope.launch { if (motion.spatialMovement) state.animateScrollToPage(state.currentPage + 1) else state.scrollToPage(state.currentPage + 1) } },
        )
    }
}

@Composable
private fun DetailControls(page: Int, total: Int, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(previous, Modifier.weight(1f), enabled = page > 0) { Text(stringResource(R.string.previous)) }
        OutlinedButton(next, Modifier.weight(1f), enabled = page < total - 1) { Text(stringResource(R.string.next)) }
    }
}

@Composable
private fun GlobalScanDetail(scan: GlobalScan, onBack: () -> Unit, onReport: (String) -> Unit) {
    var showReport by remember(scan.id) { mutableStateOf(false) }
    val bitmap = produceState<android.graphics.Bitmap?>(null, scan.photoPath) {
        value = withContext(Dispatchers.IO) {
            scan.photoPath?.let(::File)?.let { SafeJpeg.decodeSampled(it, 1_280) }
        }
    }.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.shared_eggplant_photo), Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop)
        Text(scan.diseaseName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.global_scan_confidence_published, scan.confidence, scan.publishedAt.take(16).replace('T', ' ')),
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        DetailSection(stringResource(R.string.symptoms), scan.symptoms.joinToString("\n") { "• $it" })
        DetailSection(stringResource(R.string.causes), scan.causes)
        DetailSection(stringResource(R.string.prevention), scan.prevention)
        DetailSection(stringResource(R.string.guidance), scan.guidance)
        DetailSection(stringResource(R.string.when_to_act), scan.whenToAct)
        DetailSection(stringResource(R.string.disclaimer), scan.disclaimer)
        DetailSection(stringResource(R.string.references), scan.references.joinToString("\n") { "${it.publisher}: ${it.title}\n${it.url}" })
        OutlinedButton(onClick = { showReport = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_incorrect_scan)) }
    }
    if (showReport) AlertDialog(
        onDismissRequest = { showReport = false },
        title = { Text(stringResource(R.string.report_scan_title)) },
        text = { Text(stringResource(R.string.report_scan_message)) },
        confirmButton = { TextButton(onClick = { onReport(scan.id); showReport = false }) { Text(stringResource(R.string.submit_report)) } },
        dismissButton = { TextButton(onClick = { showReport = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DetailSection(title: String, body: String) { if (body.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(body) } }
