package com.eggplant.detector.data.local

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
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
