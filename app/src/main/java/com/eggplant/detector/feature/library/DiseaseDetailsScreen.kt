package com.eggplant.detector.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.core.ui.components.DiseaseArtwork
import com.eggplant.detector.domain.model.Disease
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedButton
import com.eggplant.detector.core.ui.motion.LocalEggplantMotion
import com.eggplant.detector.core.ui.stablePageForId

@Composable
fun DiseaseDetailsScreen(disease: Disease?, onBack: () -> Unit, showTopBar: Boolean = true) {
    if (disease == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.disease_not_found))
            androidx.compose.material3.TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showTopBar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.disease_detail), style = MaterialTheme.typography.titleLarge)
            }
        }
        DiseaseArtwork(disease.id, Modifier.fillMaxWidth().height(240.dp))
        Text(disease.name, style = MaterialTheme.typography.headlineMedium)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                stringResource(if (disease.type.name.startsWith("LEAF")) R.string.leaf_disease else R.string.fruit_disease),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DetailSection(stringResource(R.string.about_disease, disease.name), disease.symptomPreview)
        DetailSection(stringResource(R.string.common_signs), disease.signs.joinToString("\n") { "• $it" })
        DetailSection(stringResource(R.string.recommended_action), disease.treatment)
        DetailSection(localized("Causes", "Mga sanhi"), disease.causes)
        DetailSection(stringResource(R.string.prevention), disease.prevention)
        DetailSection(localized("Guidance", "Gabay"), disease.guidance)
        DetailSection(localized("When to act", "Kailan kikilos"), disease.whenToAct)
        DetailSection(localized("References", "Mga sanggunian"), disease.references.joinToString("\n") { "${it.publisher}: ${it.title}\n${it.url}" })
        Text(
            disease.disclaimer.ifBlank { stringResource(R.string.educational_notice) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DiseaseDetailsPager(diseases: List<Disease>, initialId: String?, onBack: () -> Unit) {
    if (diseases.isEmpty()) { DiseaseDetailsScreen(null, onBack); return }
    val ids = diseases.map(Disease::id)
    var selectedId by rememberSaveable(initialId) { mutableStateOf(initialId?.takeIf(ids::contains) ?: ids.first()) }
    val initial = stablePageForId(ids, selectedId, 0)
    val pager = rememberPagerState(initialPage = initial) { diseases.size }
    val scope = rememberCoroutineScope()
    val motion = LocalEggplantMotion.current
    LaunchedEffect(ids) {
        val page = stablePageForId(ids, selectedId, pager.currentPage)
        if (page != pager.currentPage) pager.scrollToPage(page)
        selectedId = ids[page]
    }
    LaunchedEffect(pager.settledPage) { ids.getOrNull(pager.settledPage)?.let { selectedId = it } }
    fun moveTo(page: Int) = scope.launch {
        if (motion.spatialMovement) pager.animateScrollToPage(page) else pager.scrollToPage(page)
    }
    Column(Modifier.fillMaxSize()) {
        val current = diseases[pager.currentPage]
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column(Modifier.weight(1f)) {
                    Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        stringResource(if (current.type.name.startsWith("LEAF")) R.string.leaf_disease else R.string.fruit_disease),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.item_position, pager.currentPage + 1, diseases.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), key = { diseases[it].id }) { page ->
            DiseaseDetailsScreen(diseases[page], onBack, showTopBar = false)
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ moveTo(pager.currentPage - 1) }, Modifier.weight(1f), enabled = pager.currentPage > 0) { Text(stringResource(R.string.previous)) }
            OutlinedButton({ moveTo(pager.currentPage + 1) }, Modifier.weight(1f), enabled = pager.currentPage < diseases.lastIndex) { Text(stringResource(R.string.next)) }
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    if (body.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun localized(english: String, filipino: String): String {
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    return if (language in setOf("fil", "tl")) filipino else english
}
