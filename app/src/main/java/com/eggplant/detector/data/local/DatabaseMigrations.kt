package com.eggplant.detector.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `autoSaveEnabled` INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `diseases` (
                `id` TEXT NOT NULL,
                `modelClassIndex` INTEGER NOT NULL,
                `modelLabel` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `artworkKey` TEXT NOT NULL,
                PRIMARY KEY(`id`))""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_diseases_modelClassIndex` ON `diseases` (`modelClassIndex`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_diseases_modelLabel` ON `diseases` (`modelLabel`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `disease_localizations` (
                `diseaseId` TEXT NOT NULL,
                `languageTag` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `symptomPreview` TEXT NOT NULL,
                `prevention` TEXT NOT NULL,
                PRIMARY KEY(`diseaseId`, `languageTag`),
                FOREIGN KEY(`diseaseId`) REFERENCES `diseases`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_localizations_diseaseId` ON `disease_localizations` (`diseaseId`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `disease_signs` (
                `diseaseId` TEXT NOT NULL,
                `languageTag` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                PRIMARY KEY(`diseaseId`, `languageTag`, `position`),
                FOREIGN KEY(`diseaseId`) REFERENCES `diseases`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_signs_diseaseId` ON `disease_signs` (`diseaseId`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `treatments` (
                `diseaseId` TEXT NOT NULL,
                `languageTag` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `treatmentType` TEXT NOT NULL,
                `procedures` TEXT NOT NULL,
                PRIMARY KEY(`diseaseId`, `languageTag`),
                FOREIGN KEY(`diseaseId`) REFERENCES `diseases`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatments_diseaseId` ON `treatments` (`diseaseId`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `scan_sessions` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `startedAt` TEXT NOT NULL,
                `savedAt` TEXT NOT NULL,
                `imagePath` TEXT,
                `modelVersion` TEXT NOT NULL,
                `saveMode` TEXT NOT NULL,
                PRIMARY KEY(`id`))""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_sessions_savedAt` ON `scan_sessions` (`savedAt`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `scan_detections` (
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `diseaseId` TEXT NOT NULL,
                `modelClassIndex` INTEGER NOT NULL,
                `modelLabel` TEXT NOT NULL,
                `confidence` REAL NOT NULL,
                `left` REAL NOT NULL,
                `top` REAL NOT NULL,
                `right` REAL NOT NULL,
                `bottom` REAL NOT NULL,
                `detectedAt` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`sessionId`) REFERENCES `scan_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`diseaseId`) REFERENCES `diseases`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_detections_sessionId` ON `scan_detections` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_detections_diseaseId` ON `scan_detections` (`diseaseId`)")

        seedTechnicalDiseaseRows(db)
        db.execSQL(
            """INSERT OR IGNORE INTO `scan_sessions`
                (`id`, `source`, `startedAt`, `savedAt`, `imagePath`, `modelVersion`, `saveMode`)
                SELECT `id`, UPPER(`source`), `scannedAt`, `scannedAt`, NULL, `modelVersion`, 'MANUAL'
                FROM `scan_records`""".trimIndent(),
        )
        db.execSQL(
            """INSERT OR IGNORE INTO `scan_detections`
                (`id`, `sessionId`, `diseaseId`, `modelClassIndex`, `modelLabel`, `confidence`,
                 `left`, `top`, `right`, `bottom`, `detectedAt`)
                SELECT `scan_records`.`id` || ':0', `scan_records`.`id`, `scan_records`.`diseaseId`,
                       `diseases`.`modelClassIndex`, `diseases`.`modelLabel`,
                       `scan_records`.`confidence` / 100.0, 0.0, 0.0, 1.0, 1.0, `scan_records`.`scannedAt`
                FROM `scan_records`
                INNER JOIN `diseases` ON `diseases`.`id` = `scan_records`.`diseaseId`""".trimIndent(),
        )
    }
}

private fun seedTechnicalDiseaseRows(database: SupportSQLiteDatabase) {
    val rows: List<Array<Any>> = listOf(
        arrayOf("fruit-rot", 0, "Fruit_Rot", "FRUIT_DISEASE"),
        arrayOf("fruit-borer", 1, "Fruit_borer", "FRUIT_DISEASE"),
        arrayOf("insect-pest", 4, "Insect-Pest", "LEAF_DISEASE"),
        arrayOf("leaf-spot", 5, "Leaf-Spot", "LEAF_DISEASE"),
        arrayOf("melon-thrips", 6, "Melon_Thrips", "LEAF_DISEASE"),
        arrayOf("mosaic-virus", 7, "Mosaic", "LEAF_DISEASE"),
        arrayOf("white-molds", 8, "White-Mold", "LEAF_DISEASE"),
        arrayOf("wilt", 9, "Wilt", "LEAF_DISEASE"),
    )
    rows.forEach { row ->
        database.execSQL(
            "INSERT OR IGNORE INTO `diseases` (`id`, `modelClassIndex`, `modelLabel`, `category`, `artworkKey`) VALUES (?, ?, ?, ?, ?)",
            arrayOf(row[0], row[1], row[2], row[3], row[0]),
        )
    }
}
