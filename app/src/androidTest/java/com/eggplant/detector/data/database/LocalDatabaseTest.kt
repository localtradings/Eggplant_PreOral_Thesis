package com.eggplant.detector.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eggplant.detector.data.catalog.DiseaseCatalogSeed
import com.eggplant.detector.data.database.entity.AppSettingsEntity
import com.eggplant.detector.data.database.EggplantDatabase
import com.eggplant.detector.data.database.migration.MIGRATION_1_TO_2
import com.eggplant.detector.data.database.migration.MIGRATION_2_TO_3
import com.eggplant.detector.data.database.migration.MIGRATION_3_TO_4
import com.eggplant.detector.data.database.migration.MIGRATION_4_TO_5
import com.eggplant.detector.data.database.entity.NotificationStateEntity
import com.eggplant.detector.data.database.entity.ScanDetectionEntity
import com.eggplant.detector.data.database.entity.LegacyScanRecordEntity
import com.eggplant.detector.data.database.entity.ScanSessionEntity
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
            LegacyScanRecordEntity(
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
            AppSettingsEntity(
                languageTag = "fil",
                theme = "DARK",
                unitSystem = "IMPERIAL",
                detectHealthyLeafEnabled = true,
                detectHealthyPlantEnabled = false,
            ),
        )
        database.notificationDao().upsert(NotificationStateEntity("welcome", isRead = true))

        assertEquals("scan-1", database.scanDao().observeAll().first().single().id)
        assertEquals("fil", database.settingsDao().observe().first()?.languageTag)
        assertTrue(database.settingsDao().observe().first()?.detectHealthyLeafEnabled == true)
        assertTrue(database.settingsDao().observe().first()?.detectHealthyPlantEnabled == false)
        assertTrue(database.notificationDao().observeAll().first().single().isRead)
    }

    @Test
    fun catalogAndGroupedScanRelationsPersist() = runBlocking {
        val seed = DiseaseCatalogSeed.create()
        database.catalogDao().upsertCatalog(seed.diseases, seed.localizations, seed.signs, seed.treatments, seed.references)
        database.scanSessionDao().insertSessionWithDetections(
            ScanSessionEntity(
                id = "session-1",
                source = "LIVE",
                startedAt = "2026-07-04T10:00:00",
                savedAt = "2026-07-04T10:00:02",
                imagePath = "/private/session-1.jpg",
                modelVersion = "eggplant-yolo26m-v3-clean-768-20260707",
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

        migrationHelper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_TO_2).use { migrated ->
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

    @Test
    fun migrationTwoToThreePreservesSettingsAndDisablesHealthyClassesByDefault() {
        val databaseName = "migration-2-3"
        migrationHelper.createDatabase(databaseName, 2).apply {
            execSQL(
                "INSERT INTO app_settings " +
                    "(id, languageTag, theme, unitSystem, autoSaveEnabled) " +
                    "VALUES (1, 'fil', 'DARK', 'SYSTEM', 1)",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_TO_3).use { migrated ->
            migrated.query(
                "SELECT languageTag, theme, autoSaveEnabled, " +
                    "detectHealthyLeafEnabled, detectHealthyPlantEnabled " +
                    "FROM app_settings WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("fil", cursor.getString(0))
                assertEquals("DARK", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }
        }
    }

    @Test
    fun migrationThreeToFourPreservesPopulatedHistoryAndAddsCloudDefaults() {
        val databaseName = "migration-3-4"
        migrationHelper.createDatabase(databaseName, 3).apply {
            execSQL("INSERT INTO app_settings (id, languageTag, theme, unitSystem, autoSaveEnabled, detectHealthyLeafEnabled, detectHealthyPlantEnabled) VALUES (1, 'fil', 'DARK', 'SYSTEM', 1, 1, 0)")
            execSQL("INSERT INTO diseases (id, modelClassIndex, modelLabel, category, artworkKey) VALUES ('leaf-spot', 5, 'Leaf-Spot', 'LEAF_DISEASE', 'leaf-spot')")
            execSQL("INSERT INTO disease_localizations (diseaseId, languageTag, name, description, symptomPreview, prevention) VALUES ('leaf-spot', 'en', 'Leaf Spot', 'spots', 'spots', 'airflow')")
            execSQL("INSERT INTO scan_sessions (id, source, startedAt, savedAt, imagePath, modelVersion, saveMode) VALUES ('scan-3', 'CAPTURE', '2026-07-10T10:00:00', '2026-07-10T10:00:01', NULL, 'model', 'MANUAL')")
            execSQL("INSERT INTO scan_detections (id, sessionId, diseaseId, modelClassIndex, modelLabel, confidence, `left`, `top`, `right`, `bottom`, detectedAt) VALUES ('box-3', 'scan-3', 'leaf-spot', 5, 'Leaf-Spot', .8, .1, .1, .5, .5, '2026-07-10T10:00:01')")
            close()
        }
        migrationHelper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_3_TO_4).use { migrated ->
            migrated.query("SELECT languageTag, autoSaveEnabled, globalSharingEnabled, motionPreference, contentSyncVersion FROM app_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("fil", cursor.getString(0)); assertEquals(1, cursor.getInt(1)); assertEquals(0, cursor.getInt(2)); assertEquals("SYSTEM", cursor.getString(3)); assertEquals(0, cursor.getInt(4))
            }
            migrated.query("SELECT COUNT(*) FROM scan_detections WHERE sessionId = 'scan-3'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('sync_outbox','global_scan_cache','disease_requests','disease_request_photos')").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(4, cursor.getInt(0)) }
        }
    }

    @Test
    fun migrationTwoToFourPreservesPopulatedSettings() {
        val databaseName = "migration-2-4"
        migrationHelper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO app_settings (id, languageTag, theme, unitSystem, autoSaveEnabled) VALUES (1, 'en', 'LIGHT', 'SYSTEM', 1)")
            close()
        }
        migrationHelper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_2_TO_3, MIGRATION_3_TO_4).use { migrated ->
            migrated.query("SELECT theme, autoSaveEnabled, motionPreference FROM app_settings WHERE id = 1").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("LIGHT", cursor.getString(0)); assertEquals(1, cursor.getInt(1)); assertEquals("SYSTEM", cursor.getString(2)) }
        }
    }

    @Test
    fun migrationOneToFourPreservesLegacyScan() {
        val databaseName = "migration-1-4"
        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL("INSERT INTO app_settings (id, languageTag, theme, unitSystem) VALUES (1, 'en', 'SYSTEM', 'SYSTEM')")
            execSQL("INSERT INTO scan_records (id, diseaseId, category, confidence, scannedAt, source, modelVersion) VALUES ('legacy-4', 'leaf-spot', 'LEAF_DISEASE', 91, '2026-07-10T09:00:00', 'camera', 'legacy-model')")
            close()
        }
        migrationHelper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_1_TO_2, MIGRATION_2_TO_3, MIGRATION_3_TO_4).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM scan_sessions WHERE id = 'legacy-4'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
            migrated.query("SELECT confidence FROM scan_detections WHERE sessionId = 'legacy-4'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(.91f, cursor.getFloat(0), .001f) }
        }
    }

    @Test
    fun migrationFourToFiveAllowsMissingRequestNameAndPreservesLegacyNotes() {
        val databaseName = "migration-4-5"
        migrationHelper.createDatabase(databaseName, 4).apply {
            execSQL(
                "INSERT INTO disease_requests " +
                    "(id, clientRequestId, requestedName, notes, modelVersion, rightsConsent, trainingConsent, state, uploadProgress, adminNote, createdAt, updatedAt) " +
                    "VALUES ('request-1', 'request-1', '', '" + "x".repeat(800) + "', 'model', 1, 0, 'QUEUED', 0, NULL, '2026-07-11T00:00:00', '2026-07-11T00:00:00')",
            )
            execSQL(
                "INSERT INTO disease_request_photos " +
                    "(requestId, position, localPhotoPath, remotePath, uploadState, sha256, sizeBytes) " +
                    "VALUES ('request-1', 0, '/private/request.jpg', NULL, 'QUEUED', 'pending', 1)",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 5, true, MIGRATION_4_TO_5).use { migrated ->
            migrated.query("SELECT requestedName, length(notes) FROM disease_requests WHERE id = 'request-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(800, cursor.getInt(1))
            }
            migrated.query("SELECT captureSource FROM disease_request_photos WHERE requestId = 'request-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("capture", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('global_feed_state','cloud_deletion_state')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }
}
