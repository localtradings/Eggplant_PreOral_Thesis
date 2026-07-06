package com.eggplant.detector.app

import android.app.Application
import androidx.room.Room
import com.eggplant.detector.data.repository.EggplantRepository
import com.eggplant.detector.data.files.ScanSnapshotStore
import com.eggplant.detector.data.database.EggplantDatabase
import com.eggplant.detector.data.database.migration.MIGRATION_1_TO_2
import com.eggplant.detector.data.database.migration.MIGRATION_2_TO_3

class EggplantApplication : Application() {
    val repository: EggplantRepository by lazy {
        val database = Room.databaseBuilder(
            applicationContext,
            EggplantDatabase::class.java,
            "eggplant_detector.db",
        ).addMigrations(MIGRATION_1_TO_2, MIGRATION_2_TO_3).build()
        EggplantRepository(database, ScanSnapshotStore(applicationContext))
    }
}
