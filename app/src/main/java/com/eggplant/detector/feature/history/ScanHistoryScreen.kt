package com.eggplant.detector.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.core.ui.components.DiseaseArtwork
import com.eggplant.detector.core.ui.components.FilterChips
import com.eggplant.detector.core.ui.components.HistoryCard
import com.eggplant.detector.core.ui.components.SearchBar
import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.domain.model.ScanResult

@Composable
fun ScanHistoryScreen(viewModel: EggplantAppViewModel, onResultClick: (ScanResult) -> Unit) {
    val history by viewModel.history.collectAsState()
    val allLabel = stringResource(R.string.all)
    val leafLabel = stringResource(R.string.leaf_disease)
    val fruitLabel = stringResource(R.string.fruit_disease)
    val healthyLabel = localized("Healthy", "Malusog")
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable(allLabel) { mutableStateOf(allLabel) }
    val category = when (selectedFilter) {
        leafLabel -> ScanCategory.LEAF_DISEASE
        fruitLabel -> ScanCategory.FRUIT_DISEASE
        healthyLabel -> ScanCategory.NO_DISEASE_DETECTED
        else -> null
    }
    fun filtered(results: List<ScanResult>) = results.filter { result ->
        (category == null || result.category == category) &&
            (query.isBlank() || result.name.contains(query.trim(), ignoreCase = true))
    }
    val userResults = filtered(history)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.history_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SearchBar(query, { query = it }, stringResource(R.string.history_search)) }
        item {
            FilterChips(
                options = listOf(allLabel, leafLabel, fruitLabel, healthyLabel),
                selected = selectedFilter,
                onSelected = { selectedFilter = it },
            )
        }
        if (userResults.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.your_scans)) }
            items(userResults, key = { "user-${it.id}" }) { result ->
                HistoryCard(result = result, onClick = { onResultClick(result) })
            }
        }
        if (userResults.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DiseaseArtwork("empty-history", Modifier.fillMaxWidth(.48f))
                    Text(stringResource(R.string.no_history), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun localized(english: String, filipino: String): String {
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    return if (language == "fil" || language == "tl") filipino else english
}
