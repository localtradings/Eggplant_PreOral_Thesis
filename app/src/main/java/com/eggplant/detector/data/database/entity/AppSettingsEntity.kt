package com.eggplant.detector.data.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val languageTag: String = "en",
    val theme: String = "SYSTEM",
    val unitSystem: String = "SYSTEM",
    @ColumnInfo(defaultValue = "0") val autoSaveEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val detectHealthyLeafEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val detectHealthyPlantEnabled: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
