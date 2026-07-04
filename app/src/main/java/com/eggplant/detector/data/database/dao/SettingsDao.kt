package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.eggplant.detector.data.database.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)
}
