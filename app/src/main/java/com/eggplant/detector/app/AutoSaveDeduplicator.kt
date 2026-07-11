package com.eggplant.detector.app

import com.eggplant.detector.domain.model.ScanOutcome
import com.eggplant.detector.domain.model.ScanResult

internal class AutoSaveDeduplicator {
    private var lastSavedKey: AutoSaveKey? = null

    fun shouldSave(enabled: Boolean, stable: Boolean, result: ScanResult, sceneToken: Long): Boolean {
        if (!enabled || !stable || result.outcome != ScanOutcome.DISEASE || result.imagePath == null) return false
        return AutoSaveKey(result.diseaseId, result.source, sceneToken) != lastSavedKey
    }

    fun record(result: ScanResult, sceneToken: Long) {
        lastSavedKey = AutoSaveKey(result.diseaseId, result.source, sceneToken)
    }
}

private data class AutoSaveKey(val diseaseId: String, val source: String, val sceneToken: Long)
