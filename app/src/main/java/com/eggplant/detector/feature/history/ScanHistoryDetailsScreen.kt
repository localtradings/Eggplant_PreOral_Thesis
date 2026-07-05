package com.eggplant.detector.feature.history

import androidx.compose.runtime.Composable
import com.eggplant.detector.domain.model.ScanResult
import com.eggplant.detector.feature.result.ResultReport
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

@Composable
fun ScanHistoryDetailsScreen(result: ScanResult?, onBack: () -> Unit) {
    ResultReport(
        result = result,
        title = stringResource(R.string.history_detail),
        onBack = onBack,
    )
}
