package com.degel.beepfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntryType {
    NOTIFICATION,
    SERVICE_CONNECTED,
    SERVICE_DISCONNECTED,
}

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val type: String = EntryType.NOTIFICATION.name,
) {
    fun entryType(): EntryType = EntryType.valueOf(type)
}
