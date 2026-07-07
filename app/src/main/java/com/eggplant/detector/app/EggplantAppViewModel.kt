package com.eggplant.detector.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eggplant.detector.feature.camera.CameraScene
import com.eggplant.detector.data.catalog.DiseaseCatalog
import com.eggplant.detector.data.repository.EggplantRepository
import com.eggplant.detector.data.database.entity.AppSettingsEntity
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.domain.model.DiseaseType
import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.domain.model.ScanDetectionResult
import com.eggplant.detector.domain.model.ScanOutcome
import com.eggplant.detector.domain.model.ScanResult
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

enum class LanguagePreference(val languageTag: String, val displayName: String) {
    ENGLISH("en", "English"),
    FILIPINO("fil", "Filipino (Tagalog)"),
}

enum class SaveState {
    IDLE,
    SAVING,
    FAILED,
}

enum class ResultWarning {
    SNAPSHOT_UNAVAILABLE,
}

class EggplantAppViewModel(
    initialHistory: List<ScanResult> = emptyList(),
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    private val repository: EggplantRepository? = null,
    scanSaver: (suspend (ScanResult) -> Unit)? = null,
    private val snapshotStager: (suspend (com.eggplant.detector.detection.api.RgbFrame) -> String?)? = null,
) : ViewModel() {
    private val scanSaver = scanSaver ?: repository?.let { localRepository ->
        suspend { result: ScanResult -> localRepository.saveScan(result) }
    }
    private val _history = MutableStateFlow(initialHistory)
    val history: StateFlow<List<ScanResult>> = _history.asStateFlow()

    private val _catalog = MutableStateFlow(DiseaseCatalog.diseases)
    val catalog: StateFlow<List<com.eggplant.detector.domain.model.Disease>> = _catalog.asStateFlow()

    private val _currentResult = MutableStateFlow<ScanResult?>(null)
    val currentResult: StateFlow<ScanResult?> = _currentResult.asStateFlow()

    private val _lastScan = MutableStateFlow(initialHistory.firstOrNull())
    val lastScan: StateFlow<ScanResult?> = _lastScan.asStateFlow()

    private val _themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private var legacyUnitSystem = "SYSTEM"

    private val _languagePreference = MutableStateFlow(
        if (java.util.Locale.getDefault().language in setOf("fil", "tl")) LanguagePreference.FILIPINO
        else LanguagePreference.ENGLISH,
    )
    val languagePreference: StateFlow<LanguagePreference> = _languagePreference.asStateFlow()

    private val _autoSaveEnabled = MutableStateFlow(false)
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled.asStateFlow()

    private val _detectHealthyLeafEnabled = MutableStateFlow(false)
    val detectHealthyLeafEnabled: StateFlow<Boolean> = _detectHealthyLeafEnabled.asStateFlow()

    private val _detectHealthyPlantEnabled = MutableStateFlow(false)
    val detectHealthyPlantEnabled: StateFlow<Boolean> = _detectHealthyPlantEnabled.asStateFlow()

    private val _readNotificationKeys = MutableStateFlow<Set<String>>(emptySet())
    val readNotificationKeys: StateFlow<Set<String>> = _readNotificationKeys.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.IDLE)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _resultWarning = MutableStateFlow<ResultWarning?>(null)
    val resultWarning: StateFlow<ResultWarning?> = _resultWarning.asStateFlow()

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
                    legacyUnitSystem = settings.unitSystem
                    _languagePreference.value = LanguagePreference.entries.firstOrNull {
                        it.languageTag == settings.languageTag
                    } ?: LanguagePreference.ENGLISH
                    _autoSaveEnabled.value = settings.autoSaveEnabled
                    _detectHealthyLeafEnabled.value = settings.detectHealthyLeafEnabled
                    _detectHealthyPlantEnabled.value = settings.detectHealthyPlantEnabled
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
        _resultWarning.value = null
        val localRepository = repository
        val stageSnapshot = snapshotStager ?: localRepository?.let { repo ->
            suspend { frame: com.eggplant.detector.detection.api.RgbFrame -> repo.stageSnapshot(frame) }
        }
        if (stageSnapshot == null) {
            _currentResult.value = scene.toScanResult(
                primary,
                imagePath = null,
                _catalog.value,
                nowProvider(),
                _languagePreference.value.languageTag,
            )
            onReady()
            return
        }
        viewModelScope.launch {
            val imagePath = runCatching { stageSnapshot(scene.rgbFrame) }
                .onFailure { _resultWarning.value = ResultWarning.SNAPSHOT_UNAVAILABLE }
                .getOrNull()
            _currentResult.value = scene.toScanResult(
                primary,
                imagePath,
                _catalog.value,
                nowProvider(),
                _languagePreference.value.languageTag,
            )
            onReady()
        }
    }

    fun openHistoryResult(result: ScanResult) {
        _resultWarning.value = null
        _currentResult.value = result
    }

    fun openNoMatchScene(
        scene: CameraScene,
        onReady: () -> Unit = {},
    ) {
        _saveState.value = SaveState.IDLE
        _resultWarning.value = null
        val localRepository = repository
        val stageSnapshot = snapshotStager ?: localRepository?.let { repo ->
            suspend { frame: com.eggplant.detector.detection.api.RgbFrame -> repo.stageSnapshot(frame) }
        }
        if (stageSnapshot == null) {
            _currentResult.value = scene.toNoMatchScanResult(
                imagePath = null,
                timestamp = nowProvider(),
                languageTag = _languagePreference.value.languageTag,
            )
            onReady()
            return
        }
        viewModelScope.launch {
            val imagePath = runCatching { stageSnapshot(scene.rgbFrame) }
                .onFailure { _resultWarning.value = ResultWarning.SNAPSHOT_UNAVAILABLE }
                .getOrNull()
            _currentResult.value = scene.toNoMatchScanResult(
                imagePath = imagePath,
                timestamp = nowProvider(),
                languageTag = _languagePreference.value.languageTag,
            )
            onReady()
        }
    }

    fun saveCurrentResult(onComplete: (Boolean) -> Unit = {}): Boolean {
        val result = _currentResult.value
        if (
            result == null ||
            result.category == ScanCategory.NO_DISEASE_DETECTED ||
            _saveState.value == SaveState.SAVING ||
            _history.value.any { it.id == result.id }
        ) {
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

    fun setLanguage(preference: LanguagePreference) {
        _languagePreference.value = preference
        persistSettings()
    }

    fun setAutoSave(enabled: Boolean) {
        _autoSaveEnabled.value = enabled
        persistSettings()
    }

    fun setDetectHealthyLeaf(enabled: Boolean) {
        _detectHealthyLeafEnabled.value = enabled
        persistSettings()
    }

    fun setDetectHealthyPlant(enabled: Boolean) {
        _detectHealthyPlantEnabled.value = enabled
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
                unitSystem = legacyUnitSystem,
                autoSaveEnabled = _autoSaveEnabled.value,
                detectHealthyLeafEnabled = _detectHealthyLeafEnabled.value,
                detectHealthyPlantEnabled = _detectHealthyPlantEnabled.value,
            )
            viewModelScope.launch { localRepository.saveSettings(settings) }
        }
    }

    companion object {
        fun factory(repository: EggplantRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EggplantAppViewModel(initialHistory = emptyList(), repository = repository) as T
            }
    }
}

private fun CameraScene.toScanResult(
    primary: DetectionBox,
    imagePath: String?,
    catalog: List<com.eggplant.detector.domain.model.Disease>,
    timestamp: LocalDateTime,
    languageTag: String,
): ScanResult {
    val displayDetections = stability.confirmedDetections.ifEmpty {
        detectionFrame.detections
    }
    val primaryDiseaseId = primary.modelClass.diseaseId
    val primaryDisease = primaryDiseaseId?.let { diseaseId ->
        catalog.firstOrNull { it.id == diseaseId } ?: DiseaseCatalog.byId(diseaseId)
    }
    val primaryName = primaryDisease?.name ?: if (primary.modelClass.isHealthy) {
        healthyDisplayName(primary.modelClass.index, languageTag)
    } else {
        primary.modelClass.modelLabel.replace('_', ' ').replace('-', ' ')
    }
    return ScanResult(
        id = UUID.randomUUID().toString(),
        name = primaryName,
        category = when (primaryDisease?.type) {
            DiseaseType.LEAF_DISEASE -> ScanCategory.LEAF_DISEASE
            DiseaseType.FRUIT_DISEASE -> ScanCategory.FRUIT_DISEASE
            null -> ScanCategory.NO_DISEASE_DETECTED
        },
        outcome = if (primary.modelClass.isHealthy) ScanOutcome.HEALTHY_CONFIRMED else ScanOutcome.DISEASE,
        confidence = (primary.confidence * 100).roundToInt().coerceIn(0, 100),
        scannedAt = timestamp,
        signs = primaryDisease?.signs.orEmpty(),
        treatment = primaryDisease?.treatment.orEmpty(),
        diseaseId = primaryDiseaseId ?: healthyClassId(primary.modelClass.index),
        source = rgbFrame.source.name.lowercase(),
        modelVersion = ModelMetadata.EGGPLANT_YOLO26M.modelVersion,
        imagePath = imagePath,
        detections = displayDetections.mapIndexed { index, detection ->
            val diseaseId = detection.modelClass.diseaseId ?: healthyClassId(detection.modelClass.index)
            ScanDetectionResult(
                id = "detection-$index-${UUID.randomUUID()}",
                diseaseId = diseaseId,
                name = catalog.firstOrNull { it.id == diseaseId }?.name
                    ?: DiseaseCatalog.byId(diseaseId)?.name
                    ?: if (detection.modelClass.isHealthy) {
                        healthyDisplayName(detection.modelClass.index, languageTag)
                    } else {
                        detection.modelClass.modelLabel.replace('_', ' ').replace('-', ' ')
                    },
                modelClassIndex = detection.modelClass.index,
                modelLabel = detection.modelClass.modelLabel,
                confidence = (detection.confidence * 100).roundToInt().coerceIn(0, 100),
                bounds = detection.bounds,
            )
        },
    )
}

private fun CameraScene.toNoMatchScanResult(
    imagePath: String?,
    timestamp: LocalDateTime,
    languageTag: String,
): ScanResult = ScanResult(
    id = UUID.randomUUID().toString(),
    name = if (languageTag in setOf("fil", "tl")) {
        "Walang suportadong sakit na natukoy"
    } else {
        "No supported disease detected"
    },
    category = ScanCategory.NO_DISEASE_DETECTED,
    outcome = ScanOutcome.NO_MATCH,
    confidence = 0,
    scannedAt = timestamp,
    signs = emptyList(),
    treatment = "",
    diseaseId = "no-match",
    source = rgbFrame.source.name.lowercase(),
    modelVersion = ModelMetadata.EGGPLANT_YOLO26M.modelVersion,
    imagePath = imagePath,
    detections = emptyList(),
)

private fun healthyClassId(classIndex: Int): String = when (classIndex) {
    ModelMetadata.HEALTHY_LEAF_CLASS_INDEX -> "healthy-leaf"
    ModelMetadata.HEALTHY_PLANT_CLASS_INDEX -> "healthy-plant"
    else -> error("Unknown healthy model class index: $classIndex")
}

private fun healthyDisplayName(classIndex: Int, languageTag: String): String {
    val filipino = languageTag in setOf("fil", "tl")
    return when (classIndex) {
        ModelMetadata.HEALTHY_LEAF_CLASS_INDEX -> if (filipino) "Malusog na Dahon" else "Healthy Leaf"
        ModelMetadata.HEALTHY_PLANT_CLASS_INDEX -> if (filipino) "Malusog na Halaman" else "Healthy Plant"
        else -> error("Unknown healthy model class index: $classIndex")
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
