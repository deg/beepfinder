package com.degel.beepfinder.data

import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationDao) {

    fun getLast24Hours(): Flow<List<NotificationEntity>> {
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return dao.getLast24Hours(since)
    }

    suspend fun record(packageName: String, appLabel: String) {
        val now = System.currentTimeMillis()
        dao.insert(
            NotificationEntity(
                timestamp = now,
                packageName = packageName,
                appLabel = appLabel,
                type = EntryType.NOTIFICATION.name,
            )
        )
        dao.deleteOlderThan(now - 24 * 60 * 60 * 1000L)
    }

    suspend fun recordServiceEvent(type: EntryType) {
        require(type == EntryType.SERVICE_CONNECTED || type == EntryType.SERVICE_DISCONNECTED)
        dao.insert(
            NotificationEntity(
                timestamp = System.currentTimeMillis(),
                packageName = "",
                appLabel = "",
                type = type.name,
            )
        )
    }
}
