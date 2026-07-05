package com.eggplant.detector.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_state")
data class NotificationStateEntity(
    @PrimaryKey val notificationKey: String,
    val isRead: Boolean,
)
