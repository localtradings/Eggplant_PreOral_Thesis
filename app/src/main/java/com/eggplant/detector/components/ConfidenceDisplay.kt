package com.eggplant.detector.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eggplant.detector.utils.ConfidenceFormatter
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

@Composable
fun ConfidenceDisplay(confidence: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { confidence / 100f },
                modifier = Modifier.size(84.dp),
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                ConfidenceFormatter.format(confidence),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(stringResource(R.string.confidence_label), style = MaterialTheme.typography.bodyMedium)
    }
}
