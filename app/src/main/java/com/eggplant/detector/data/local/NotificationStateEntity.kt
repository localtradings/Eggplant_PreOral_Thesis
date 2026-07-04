package com.eggplant.detector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_state")
data class NotificationStateEntity(
    @PrimaryKey val notificationKey: String,
    val isRead: Boolean,
)
