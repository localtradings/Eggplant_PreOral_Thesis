package com.eggplant.detector.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.eggplant.detector.data.database.dao.DiseaseCatalogDao
import com.eggplant.detector.data.database.dao.NotificationDao
import com.eggplant.detector.data.database.dao.LegacyScanRecordDao
import com.eggplant.detector.data.database.dao.ScanSessionDao
import com.eggplant.detector.data.database.dao.SettingsDao
import com.eggplant.detector.data.database.entity.AppSettingsEntity
import com.eggplant.detector.data.database.entity.DiseaseEntity
import com.eggplant.detector.data.database.entity.DiseaseLocalizationEntity
import com.eggplant.detector.data.database.entity.DiseaseSignEntity
import com.eggplant.detector.data.database.entity.NotificationStateEntity
import com.eggplant.detector.data.database.entity.ScanDetectionEntity
import com.eggplant.detector.data.database.entity.LegacyScanRecordEntity
import com.eggplant.detector.data.database.entity.ScanSessionEntity
import com.eggplant.detector.data.database.entity.TreatmentEntity

@Database(
    entities = [
        LegacyScanRecordEntity::class,
        AppSettingsEntity::class,
        NotificationStateEntity::class,
        DiseaseEntity::class,
        DiseaseLocalizationEntity::class,
        DiseaseSignEntity::class,
        TreatmentEntity::class,
        ScanSessionEntity::class,
        ScanDetectionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class EggplantDatabase : RoomDatabase() {
    abstract fun scanDao(): LegacyScanRecordDao
    abstract fun settingsDao(): SettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun catalogDao(): DiseaseCatalogDao
    abstract fun scanSessionDao(): ScanSessionDao
}
