package com.eggplant.detector.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.AppViewModel
import com.eggplant.detector.components.DiseaseArtwork
import com.eggplant.detector.components.FilterChips
import com.eggplant.detector.components.HistoryCard
import com.eggplant.detector.components.SearchBar
import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanResult
import com.eggplant.detector.utils.DateFormatter
import java.time.LocalDateTime

@Composable
fun HistoryPage(viewModel: AppViewModel, onResultClick: (ScanResult) -> Unit) {
    val history by viewModel.history.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val now = LocalDateTime.now()
    val category = when (selectedFilter) {
        "Leaf Disease" -> ScanCategory.LEAF_DISEASE
        "Fruit Disease" -> ScanCategory.FRUIT_DISEASE
        "Healthy" -> ScanCategory.NO_DISEASE_DETECTED
        else -> null
    }
    val filtered = history.filter { result ->
        (category == null || result.category == category) &&
            (query.isBlank() || result.name.contains(query.trim(), ignoreCase = true))
    }
    val grouped = filtered.groupBy { DateFormatter.groupLabel(it.scannedAt, now) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Review your previous eggplant scans",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SearchBar(query, { query = it }, "Search scan history...") }
        item {
            FilterChips(
                options = listOf("All", "Leaf Disease", "Fruit Disease", "Healthy"),
                selected = selectedFilter,
                onSelected = { selectedFilter = it },
            )
        }
        if (filtered.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DiseaseArtwork("empty-history", Modifier.fillMaxWidth(.48f))
                    Text("No scan history found", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            listOf("Today", "Yesterday", "June 2026").forEach { label ->
                val results = grouped[label].orEmpty()
                if (results.isNotEmpty()) {
                    item(key = "header-$label") {
                        Text(
                            label,
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    items(results, key = { it.id }) { result ->
                        HistoryCard(result = result, onClick = { onResultClick(result) })
                    }
                }
            }
            grouped.filterKeys { it !in setOf("Today", "Yesterday", "June 2026") }.forEach { (label, results) ->
                item(key = "header-$label") {
                    Text(label, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
                }
                items(results, key = { it.id }) { result ->
                    HistoryCard(result = result, onClick = { onResultClick(result) })
                }
            }
        }
    }
}
