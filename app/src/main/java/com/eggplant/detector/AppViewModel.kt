package com.eggplant.detector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eggplant.detector.camera.CameraScene
import com.eggplant.detector.data.DiseaseData
import com.eggplant.detector.data.LocalAppRepository
import com.eggplant.detector.data.local.AppSettingsEntity
import com.eggplant.detector.detection.DetectionBox
import com.eggplant.detector.detection.ModelMetadata
import com.eggplant.detector.model.DiseaseType
import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanDetectionResult
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

enum class ThemePreference(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System default"),
}

enum class UnitPreference(val displayName: String) {
    SYSTEM("System default"),
    METRIC("Metric"),
    IMPERIAL("Imperial"),
}

enum class LanguagePreference(val languageTag: String, val displayName: String) {
    ENGLISH("en", "English"),
    FILIPINO("fil", "Filipino (Tagalog)"),
}

enum class SaveState {
    IDLE,
    SAVING,
    FAILED,
}

class AppViewModel(
    initialHistory: List<ScanResult> = emptyList(),
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    private val repository: LocalAppRepository? = null,
    scanSaver: (suspend (ScanResult) -> Unit)? = null,
) : ViewModel() {
    private val scanSaver = scanSaver ?: repository?.let { localRepository ->
        suspend { result: ScanResult -> localRepository.saveScan(result) }
    }
    private val _history = MutableStateFlow(initialHistory)
    val history: StateFlow<List<ScanResult>> = _history.asStateFlow()

    private val _catalog = MutableStateFlow(DiseaseData.diseases)
    val catalog: StateFlow<List<com.eggplant.detector.model.Disease>> = _catalog.asStateFlow()

    private val _currentResult = MutableStateFlow<ScanResult?>(null)
    val currentResult: StateFlow<ScanResult?> = _currentResult.asStateFlow()

    private val _lastScan = MutableStateFlow(initialHistory.firstOrNull())
    val lastScan: StateFlow<ScanResult?> = _lastScan.asStateFlow()

    private val _themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _unitPreference = MutableStateFlow(UnitPreference.SYSTEM)
    val unitPreference: StateFlow<UnitPreference> = _unitPreference.asStateFlow()

    private val _languagePreference = MutableStateFlow(
        if (java.util.Locale.getDefault().language in setOf("fil", "tl")) LanguagePreference.FILIPINO
        else LanguagePreference.ENGLISH,
    )
    val languagePreference: StateFlow<LanguagePreference> = _languagePreference.asStateFlow()

    private val _autoSaveEnabled = MutableStateFlow(false)
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled.asStateFlow()

    private val _readNotificationKeys = MutableStateFlow<Set<String>>(emptySet())
    val readNotificationKeys: StateFlow<Set<String>> = _readNotificationKeys.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.IDLE)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    init {
        repository?.let { localRepository ->
            viewModelScope.launch {
                localRepository.history.collect { savedHistory ->
                    _history.value = savedHistory
                    _lastScan.value = savedHistory.firstOrNull()
                }
            }
            viewModelScope.launch {
                localRepository.settings.collect { settings ->
                    _themePreference.value = enumValueOrDefault(settings.theme, ThemePreference.SYSTEM)
                    _unitPreference.value = enumValueOrDefault(settings.unitSystem, UnitPreference.SYSTEM)
                    _languagePreference.value = LanguagePreference.entries.firstOrNull {
                        it.languageTag == settings.languageTag
                    } ?: LanguagePreference.ENGLISH
                    _autoSaveEnabled.value = settings.autoSaveEnabled
                }
            }
            viewModelScope.launch {
                localRepository.readNotificationKeys.collect { _readNotificationKeys.value = it }
            }
            viewModelScope.launch {
                _languagePreference.collectLatest { language ->
                    localRepository.catalog(language.languageTag)
                        .filter { it.isNotEmpty() }
                        .collect { _catalog.value = it }
                }
            }
        }
    }

    fun openDetectionScene(
        scene: CameraScene,
        primary: DetectionBox,
        onReady: () -> Unit = {},
    ) {
        _saveState.value = SaveState.IDLE
        val localRepository = repository
        if (localRepository == null) {
            _currentResult.value = scene.toScanResult(primary, imagePath = null, _catalog.value, nowProvider())
            onReady()
            return
        }
        viewModelScope.launch {
            val imagePath = localRepository.stageSnapshot(scene.rgbFrame)
            _currentResult.value = scene.toScanResult(primary, imagePath, _catalog.value, nowProvider())
            onReady()
        }
    }

    fun openHistoryResult(result: ScanResult) {
        _currentResult.value = result
    }

    fun saveCurrentResult(onComplete: (Boolean) -> Unit = {}): Boolean {
        val result = _currentResult.value
        if (result == null || _saveState.value == SaveState.SAVING || _history.value.any { it.id == result.id }) {
            if (result == null) _saveState.value = SaveState.FAILED
            onComplete(false)
            return false
        }

        val savedResult = result.copy(scannedAt = nowProvider())
        val persist = scanSaver
        if (persist == null) {
            commitSavedResult(savedResult)
            onComplete(true)
            return true
        }

        _saveState.value = SaveState.SAVING
        viewModelScope.launch {
            try {
                persist(savedResult)
                commitSavedResult(savedResult)
                onComplete(true)
            } catch (_: Exception) {
                _saveState.value = SaveState.FAILED
                onComplete(false)
            }
        }
        return true
    }

    private fun commitSavedResult(result: ScanResult) {
        if (_history.value.none { it.id == result.id }) {
            _history.value = listOf(result) + _history.value
        }
        _currentResult.value = result
        _lastScan.value = result
        _saveState.value = SaveState.IDLE
    }

    fun setTheme(preference: ThemePreference) {
        _themePreference.value = preference
        persistSettings()
    }

    fun setUnits(preference: UnitPreference) {
        _unitPreference.value = preference
        persistSettings()
    }

    fun setLanguage(preference: LanguagePreference) {
        _languagePreference.value = preference
        persistSettings()
    }

    fun setAutoSave(enabled: Boolean) {
        _autoSaveEnabled.value = enabled
        persistSettings()
    }

    fun markNotificationRead(key: String) {
        _readNotificationKeys.value = _readNotificationKeys.value + key
        repository?.let { localRepository ->
            viewModelScope.launch { localRepository.markNotificationRead(key) }
        }
    }

    fun markAllNotificationsRead(keys: Collection<String>) {
        keys.forEach(::markNotificationRead)
    }

    private fun persistSettings() {
        repository?.let { localRepository ->
            val settings = AppSettingsEntity(
                languageTag = _languagePreference.value.languageTag,
                theme = _themePreference.value.name,
                unitSystem = _unitPreference.value.name,
                autoSaveEnabled = _autoSaveEnabled.value,
            )
            viewModelScope.launch { localRepository.saveSettings(settings) }
        }
    }

    companion object {
        fun factory(repository: LocalAppRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(initialHistory = emptyList(), repository = repository) as T
            }
    }
}

private fun CameraScene.toScanResult(
    primary: DetectionBox,
    imagePath: String?,
    catalog: List<com.eggplant.detector.model.Disease>,
    timestamp: LocalDateTime,
): ScanResult {
    val allDiseases = stability.stableDetections.ifEmpty {
        detectionFrame.detections.filterNot { it.modelClass.isHealthy }
    }
    val primaryDiseaseId = requireNotNull(primary.modelClass.diseaseId)
    val primaryDisease = requireNotNull(catalog.firstOrNull { it.id == primaryDiseaseId } ?: DiseaseData.byId(primaryDiseaseId))
    return ScanResult(
        id = UUID.randomUUID().toString(),
        name = primaryDisease.name,
        category = when (primaryDisease.type) {
            DiseaseType.LEAF_DISEASE -> ScanCategory.LEAF_DISEASE
            DiseaseType.FRUIT_DISEASE -> ScanCategory.FRUIT_DISEASE
        },
        confidence = (primary.confidence * 100).roundToInt().coerceIn(0, 100),
        scannedAt = timestamp,
        signs = primaryDisease.signs,
        treatment = primaryDisease.treatment,
        diseaseId = primaryDiseaseId,
        source = rgbFrame.source.name.lowercase(),
        modelVersion = ModelMetadata.EGGPLANT_YOLO26M.modelVersion,
        imagePath = imagePath,
        detections = allDiseases.mapIndexed { index, detection ->
            val diseaseId = requireNotNull(detection.modelClass.diseaseId)
            ScanDetectionResult(
                id = "detection-$index-${UUID.randomUUID()}",
                diseaseId = diseaseId,
                name = catalog.firstOrNull { it.id == diseaseId }?.name
                    ?: DiseaseData.byId(diseaseId)?.name
                    ?: detection.modelClass.modelLabel,
                modelClassIndex = detection.modelClass.index,
                modelLabel = detection.modelClass.modelLabel,
                confidence = (detection.confidence * 100).roundToInt().coerceIn(0, 100),
                bounds = detection.bounds,
            )
        },
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
