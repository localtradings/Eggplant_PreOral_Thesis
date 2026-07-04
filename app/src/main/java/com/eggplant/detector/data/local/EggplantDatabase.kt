package com.eggplant.detector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanRecordEntity::class,
        AppSettingsEntity::class,
        NotificationStateEntity::class,
        DiseaseEntity::class,
        DiseaseLocalizationEntity::class,
        DiseaseSignEntity::class,
        TreatmentEntity::class,
        ScanSessionEntity::class,
        ScanDetectionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class EggplantDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun settingsDao(): SettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun catalogDao(): CatalogDao
    abstract fun scanSessionDao(): ScanSessionDao
}
