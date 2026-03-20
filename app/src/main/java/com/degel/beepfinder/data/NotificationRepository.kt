package com.degel.beepfinder.data

import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationDao) {

    fun getRecent(historyHours: Int): Flow<List<NotificationEntity>> {
        val since = System.currentTimeMillis() - historyHours * 60 * 60 * 1000L
        return dao.getLast24Hours(since)
    }

    suspend fun record(packageName: String, appLabel: String) {
        dao.insert(
            NotificationEntity(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                appLabel = appLabel,
                type = EntryType.NOTIFICATION.name,
            )
        )
    }

    suspend fun pruneOlderThan(historyHours: Int) {
        val cutoff = System.currentTimeMillis() - historyHours * 60 * 60 * 1000L
        dao.deleteOlderThan(cutoff)
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
