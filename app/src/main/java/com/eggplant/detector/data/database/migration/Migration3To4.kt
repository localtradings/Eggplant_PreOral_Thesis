package com.eggplant.detector.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_TO_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `disease_localizations` ADD COLUMN `causes` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `disease_localizations` ADD COLUMN `guidance` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `disease_localizations` ADD COLUMN `whenToAct` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `disease_localizations` ADD COLUMN `disclaimer` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `globalSharingEnabled` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `motionPreference` TEXT NOT NULL DEFAULT 'SYSTEM'")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `sharingConsentVersion` INTEGER")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `sharingConsentedAt` TEXT")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `contentSyncVersion` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `contentEtag` TEXT")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `lastGlobalSyncAt` TEXT")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `disease_references` (`diseaseId` TEXT NOT NULL, `languageTag` TEXT NOT NULL, `position` INTEGER NOT NULL, `publisher` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`diseaseId`, `languageTag`, `position`), FOREIGN KEY(`diseaseId`) REFERENCES `diseases`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_references_diseaseId` ON `disease_references` (`diseaseId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `sync_outbox` (`id` TEXT NOT NULL, `eventType` TEXT NOT NULL, `version` INTEGER NOT NULL, `idempotencyKey` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `nextAttemptAt` TEXT NOT NULL, `lastErrorCode` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_idempotencyKey` ON `sync_outbox` (`idempotencyKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_state_nextAttemptAt` ON `sync_outbox` (`state`, `nextAttemptAt`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `global_scan_cache` (`id` TEXT NOT NULL, `diseaseId` TEXT NOT NULL, `confidence` REAL NOT NULL, `source` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `cachedPhotoPath` TEXT, `publishedAt` TEXT NOT NULL, `expiresAt` TEXT NOT NULL, `contentJson` TEXT NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_global_scan_cache_publishedAt` ON `global_scan_cache` (`publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_global_scan_cache_diseaseId` ON `global_scan_cache` (`diseaseId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `global_ranking_cache` (`diseaseId` TEXT NOT NULL, `scanCount` INTEGER NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`diseaseId`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `disease_requests` (`id` TEXT NOT NULL, `clientRequestId` TEXT NOT NULL, `requestedName` TEXT NOT NULL, `notes` TEXT, `modelVersion` TEXT NOT NULL, `rightsConsent` INTEGER NOT NULL, `trainingConsent` INTEGER NOT NULL, `state` TEXT NOT NULL, `uploadProgress` REAL NOT NULL, `adminNote` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_disease_requests_clientRequestId` ON `disease_requests` (`clientRequestId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_requests_createdAt` ON `disease_requests` (`createdAt`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `disease_request_photos` (`requestId` TEXT NOT NULL, `position` INTEGER NOT NULL, `localPhotoPath` TEXT NOT NULL, `remotePath` TEXT, `uploadState` TEXT NOT NULL, `sha256` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`requestId`, `position`), FOREIGN KEY(`requestId`) REFERENCES `disease_requests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_request_photos_requestId` ON `disease_request_photos` (`requestId`)")
    }
}
