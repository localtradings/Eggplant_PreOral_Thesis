package com.eggplant.detector

import android.app.Application
import androidx.room.Room
import com.eggplant.detector.data.LocalAppRepository
import com.eggplant.detector.data.SnapshotStore
import com.eggplant.detector.data.local.EggplantDatabase
import com.eggplant.detector.data.local.MIGRATION_1_2

class EggplantApplication : Application() {
    val repository: LocalAppRepository by lazy {
        val database = Room.databaseBuilder(
            applicationContext,
            EggplantDatabase::class.java,
            "eggplant_detector.db",
        ).addMigrations(MIGRATION_1_2).build()
        LocalAppRepository(database, SnapshotStore(applicationContext))
    }
}
