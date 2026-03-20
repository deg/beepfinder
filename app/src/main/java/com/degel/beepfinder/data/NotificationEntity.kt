package com.degel.beepfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,         // System.currentTimeMillis()
    val packageName: String,
    val appLabel: String,
)
