package com.eggplant.detector.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.eggplant.detector.data.database.entity.NotificationStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_state")
    fun observeAll(): Flow<List<NotificationStateEntity>>

    @Upsert
    suspend fun upsert(state: NotificationStateEntity)
}
