package com.eggplant.detector.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Forward-only cloud-state migration. Existing request notes intentionally
 * remain intact; the smaller 200-character limit applies to new writes.
 */
val MIGRATION_4_TO_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite cannot drop NOT NULL from a column in place. Preserve both
        // request and child-photo rows while recreating their FK relationship.
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `disease_request_photos_v5_backup`
                (`requestId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                 `localPhotoPath` TEXT NOT NULL, `remotePath` TEXT,
                 `uploadState` TEXT NOT NULL, `sha256` TEXT NOT NULL,
                 `sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`requestId`, `position`))""",
        )
        db.execSQL(
            """INSERT OR REPLACE INTO `disease_request_photos_v5_backup`
                (`requestId`, `position`, `localPhotoPath`, `remotePath`, `uploadState`, `sha256`, `sizeBytes`)
                SELECT `requestId`, `position`, `localPhotoPath`, `remotePath`, `uploadState`, `sha256`, `sizeBytes`
                FROM `disease_request_photos`""",
        )
        db.execSQL("DROP TABLE `disease_request_photos`")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `disease_requests_v5`
                (`id` TEXT NOT NULL, `clientRequestId` TEXT NOT NULL,
                 `requestedName` TEXT, `notes` TEXT, `modelVersion` TEXT NOT NULL,
                 `rightsConsent` INTEGER NOT NULL, `trainingConsent` INTEGER NOT NULL,
                 `state` TEXT NOT NULL, `uploadProgress` REAL NOT NULL,
                 `adminNote` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL,
                 PRIMARY KEY(`id`))""",
        )
        db.execSQL(
            """INSERT INTO `disease_requests_v5`
                (`id`, `clientRequestId`, `requestedName`, `notes`, `modelVersion`,
                 `rightsConsent`, `trainingConsent`, `state`, `uploadProgress`, `adminNote`, `createdAt`, `updatedAt`)
                SELECT `id`, `clientRequestId`, NULLIF(TRIM(`requestedName`), ''), `notes`, `modelVersion`,
                 `rightsConsent`, `trainingConsent`, `state`, `uploadProgress`, `adminNote`, `createdAt`, `updatedAt`
                FROM `disease_requests`""",
        )
        db.execSQL("DROP TABLE `disease_requests`")
        db.execSQL("ALTER TABLE `disease_requests_v5` RENAME TO `disease_requests`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_disease_requests_clientRequestId` ON `disease_requests` (`clientRequestId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_requests_createdAt` ON `disease_requests` (`createdAt`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `disease_request_photos`
                (`requestId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                 `localPhotoPath` TEXT NOT NULL, `remotePath` TEXT,
                 `uploadState` TEXT NOT NULL, `sha256` TEXT NOT NULL,
                 `sizeBytes` INTEGER NOT NULL, `captureSource` TEXT NOT NULL DEFAULT 'capture',
                 PRIMARY KEY(`requestId`, `position`),
                 FOREIGN KEY(`requestId`) REFERENCES `disease_requests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_request_photos_requestId` ON `disease_request_photos` (`requestId`)")
        db.execSQL(
            """INSERT INTO `disease_request_photos`
                (`requestId`, `position`, `localPhotoPath`, `remotePath`, `uploadState`, `sha256`, `sizeBytes`, `captureSource`)
                SELECT `requestId`, `position`, `localPhotoPath`, `remotePath`, `uploadState`, `sha256`, `sizeBytes`, 'capture'
                FROM `disease_request_photos_v5_backup`""",
        )
        db.execSQL("DROP TABLE `disease_request_photos_v5_backup`")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `global_feed_state`
                (`feedKey` TEXT NOT NULL, `nextCursor` TEXT, `hasMore` INTEGER NOT NULL,
                 `syncState` TEXT NOT NULL, `lastErrorCode` TEXT, `lastUpdatedAt` TEXT,
                 PRIMARY KEY(`feedKey`))""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `cloud_deletion_state`
                (`operationKey` TEXT NOT NULL, `state` TEXT NOT NULL,
                 `affectedContributionIdsJson` TEXT NOT NULL, `lastErrorCode` TEXT,
                 `updatedAt` TEXT NOT NULL, PRIMARY KEY(`operationKey`))""",
        )
    }
}
