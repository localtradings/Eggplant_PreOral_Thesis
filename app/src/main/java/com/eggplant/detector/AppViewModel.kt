package com.eggplant.detector

import androidx.lifecycle.ViewModel
import com.eggplant.detector.data.MockDetectionProvider
import com.eggplant.detector.data.ScanHistoryData
import com.eggplant.detector.model.DetectionProvider
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreference(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System default"),
}

enum class UnitPreference(val displayName: String) {
    METRIC("Metric"),
    IMPERIAL("Imperial"),
}

class AppViewModel(
    private val detectionProvider: DetectionProvider = MockDetectionProvider(),
    initialHistory: List<ScanResult> = ScanHistoryData.seed(),
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {
    private val _history = MutableStateFlow(initialHistory)
    val history: StateFlow<List<ScanResult>> = _history.asStateFlow()

    private val _currentResult = MutableStateFlow<ScanResult?>(null)
    val currentResult: StateFlow<ScanResult?> = _currentResult.asStateFlow()

    private val _lastScan = MutableStateFlow(initialHistory.firstOrNull())
    val lastScan: StateFlow<ScanResult?> = _lastScan.asStateFlow()

    private val _themePreference = MutableStateFlow(ThemePreference.LIGHT)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _unitPreference = MutableStateFlow(UnitPreference.METRIC)
    val unitPreference: StateFlow<UnitPreference> = _unitPreference.asStateFlow()

    fun detectCapture() {
        _currentResult.value = detectionProvider.detectCapture()
    }

    fun detectGallery() {
        _currentResult.value = detectionProvider.detectGallery()
    }

    fun openHistoryResult(result: ScanResult) {
        _currentResult.value = result
    }

    fun saveCurrentResult(): Boolean {
        val result = _currentResult.value ?: return false
        if (_history.value.any { it.id == result.id }) return false

        val savedResult = result.copy(scannedAt = nowProvider())
        _history.value = listOf(savedResult) + _history.value
        _currentResult.value = savedResult
        _lastScan.value = savedResult
        return true
    }

    fun setTheme(preference: ThemePreference) {
        _themePreference.value = preference
    }

    fun setUnits(preference: UnitPreference) {
        _unitPreference.value = preference
    }
}
