package com.eggplant.detector

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eggplant.detector.data.CatalogSeed
import com.eggplant.detector.data.local.AppSettingsEntity
import com.eggplant.detector.data.local.EggplantDatabase
import com.eggplant.detector.data.local.MIGRATION_1_2
import com.eggplant.detector.data.local.NotificationStateEntity
import com.eggplant.detector.data.local.ScanDetectionEntity
import com.eggplant.detector.data.local.ScanRecordEntity
import com.eggplant.detector.data.local.ScanSessionEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EggplantDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private lateinit var database: EggplantDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EggplantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun historySettingsAndNotificationStatePersist() = runBlocking {
        database.scanDao().insert(
            ScanRecordEntity(
                id = "scan-1",
                diseaseId = "leaf-spot",
                category = "LEAF_DISEASE",
                confidence = 87,
                scannedAt = LocalDateTime.of(2026, 7, 3, 10, 0).toString(),
                source = "camera",
                modelVersion = "legacy-model",
            ),
        )
        database.settingsDao().upsert(
            AppSettingsEntity(languageTag = "fil", theme = "DARK", unitSystem = "IMPERIAL"),
        )
        database.notificationDao().upsert(NotificationStateEntity("welcome", isRead = true))

        assertEquals("scan-1", database.scanDao().observeAll().first().single().id)
        assertEquals("fil", database.settingsDao().observe().first()?.languageTag)
        assertTrue(database.notificationDao().observeAll().first().single().isRead)
    }

    @Test
    fun catalogAndGroupedScanRelationsPersist() = runBlocking {
        val seed = CatalogSeed.create()
        database.catalogDao().upsertCatalog(seed.diseases, seed.localizations, seed.signs, seed.treatments)
        database.scanSessionDao().insertSessionWithDetections(
            ScanSessionEntity(
                id = "session-1",
                source = "LIVE",
                startedAt = "2026-07-04T10:00:00",
                savedAt = "2026-07-04T10:00:02",
                imagePath = "/private/session-1.jpg",
                modelVersion = "eggplant-yolo26m-b96-20260704",
                saveMode = "MANUAL",
            ),
            listOf(
                ScanDetectionEntity(
                    id = "detection-1",
                    sessionId = "session-1",
                    diseaseId = "leaf-spot",
                    modelClassIndex = 5,
                    modelLabel = "Leaf-Spot",
                    confidence = 0.87f,
                    left = 0.1f,
                    top = 0.2f,
                    right = 0.6f,
                    bottom = 0.8f,
                    detectedAt = "2026-07-04T10:00:02",
                ),
            ),
        )

        assertEquals(8, database.catalogDao().diseaseCount())
        val session = database.scanSessionDao().observeSessions().first().single()
        assertEquals("session-1", session.session.id)
        assertEquals("leaf-spot", session.detections.single().diseaseId)
    }

    @Test
    fun migrationOneToTwoPreservesLegacyScanAndAddsManualSaveDefault() {
        val databaseName = "migration-1-2"
        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO app_settings (id, languageTag, theme, unitSystem) " +
                    "VALUES (1, 'en', 'SYSTEM', 'SYSTEM')",
            )
            execSQL(
                "INSERT INTO scan_records " +
                    "(id, diseaseId, category, confidence, scannedAt, source, modelVersion) " +
                    "VALUES ('legacy-1', 'leaf-spot', 'LEAF_DISEASE', 87, " +
                    "'2026-07-03T10:00:00', 'camera', 'legacy-model')",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM scan_records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT autoSaveEnabled FROM app_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM scan_sessions WHERE id = 'legacy-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM scan_detections WHERE sessionId = 'legacy-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }
}
