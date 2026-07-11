package com.eggplant.detector.app

import android.app.Application
import androidx.room.Room
import com.eggplant.detector.data.repository.EggplantRepository
import com.eggplant.detector.data.files.ScanSnapshotStore
import com.eggplant.detector.data.database.EggplantDatabase
import com.eggplant.detector.data.database.migration.MIGRATION_1_TO_2
import com.eggplant.detector.data.database.migration.MIGRATION_2_TO_3
import com.eggplant.detector.data.database.migration.MIGRATION_3_TO_4
import com.eggplant.detector.detection.ncnn.NcnnDetectionEngine
import com.eggplant.detector.data.cloud.CloudApiClient
import com.eggplant.detector.data.cloud.CloudSyncScheduler
import com.eggplant.detector.data.cloud.NcnnSharePhotoRevalidator

class EggplantApplication : Application() {
    val detectionEngine: NcnnDetectionEngine by lazy { NcnnDetectionEngine(applicationContext) }
    val cloudApiClient: CloudApiClient by lazy { CloudApiClient(applicationContext) }

    val database: EggplantDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            EggplantDatabase::class.java,
            "eggplant_detector.db",
        ).addMigrations(MIGRATION_1_TO_2, MIGRATION_2_TO_3, MIGRATION_3_TO_4).build()
    }

    val repository: EggplantRepository by lazy {
        EggplantRepository(
            database = database,
            snapshotStore = ScanSnapshotStore(applicationContext),
            cloudSync = { CloudSyncScheduler.refresh(this) },
            sharePhotoRevalidator = NcnnSharePhotoRevalidator(detectionEngine),
        )
    }

    override fun onCreate() {
        super.onCreate()
        CloudSyncScheduler.schedule(this)
        if (cloudApiClient.isConfigured) CloudSyncScheduler.refresh(this)
    }
}
