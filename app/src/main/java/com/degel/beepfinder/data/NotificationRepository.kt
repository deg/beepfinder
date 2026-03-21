package com.degel.beepfinder.data

import kotlinx.coroutines.flow.Flow

/**
 * NotificationRepository — the single data-access facade for the UI layer.
 *
 * REPOSITORY PATTERN
 * ==================
 * The repository sits between the DAO (which speaks SQL) and the ViewModel
 * (which speaks business logic). Its jobs are:
 *
 *   1. Hide the data source. The ViewModel doesn't know or care that the
 *      data comes from Room. If we later added a network sync, the ViewModel
 *      wouldn't change.
 *
 *   2. Contain data-adjacent logic. The "compute the cutoff timestamp from
 *      historyHours" calculation lives here rather than in the ViewModel or
 *      the DAO, where it would be out of place.
 *
 *   3. Be a unit-test boundary. You can test the ViewModel with a fake
 *      repository without spinning up a real database.
 *
 * In a larger app you'd typically inject the repository via a DI framework
 * (Hilt is the standard Android choice). Here we construct it directly in
 * the ViewModel for simplicity.
 */
class NotificationRepository(private val dao: NotificationDao) {

    /**
     * Returns a live-updating stream of all entries newer than [historyHours]
     * hours ago, newest first.
     *
     * The returned Flow stays active as long as it has collectors. Room
     * re-emits whenever the notifications table changes. The ViewModel
     * collects this flow and the Compose UI recomposes automatically.
     *
     * WHY PASS historyHours HERE (not in the DAO)?
     * The DAO's @Query parameter must be a raw value (timestamp). Computing
     * "now minus N hours" belongs in the repository, not the DAO, because
     * it's a data/time policy decision, not a SQL concern.
     */
    fun getRecent(historyHours: Int): Flow<List<NotificationEntity>> {
        val since = System.currentTimeMillis() - historyHours * 60 * 60 * 1000L
        return dao.getRecent(since)
    }

    /**
     * Inserts a notification entry. Called from BeepListenerService on the
     * IO dispatcher (the service's coroutine scope uses Dispatchers.IO).
     *
     * [soundLabel] is null when the channel uses the device default sound or
     * when the sound URI couldn't be resolved to a name.
     */
    suspend fun record(packageName: String, appLabel: String, soundLabel: String? = null) {
        dao.insert(
            NotificationEntity(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                appLabel = appLabel,
                type = EntryType.NOTIFICATION.name,
                soundLabel = soundLabel,
            )
        )
    }

    /**
     * Inserts a service lifecycle event (connected or disconnected).
     * These appear as inline markers in the notification list so the user
     * can tell whether a quiet period was real or a monitoring gap.
     *
     * require() is an assertion — it throws IllegalArgumentException if
     * violated. Using NOTIFICATION here would be a programming error, not a
     * recoverable runtime condition, so we assert rather than silently ignore.
     */
    suspend fun recordServiceEvent(type: EntryType) {
        require(type == EntryType.SERVICE_CONNECTED || type == EntryType.SERVICE_DISCONNECTED) {
            "recordServiceEvent() called with wrong type: $type"
        }
        dao.insert(
            NotificationEntity(
                timestamp = System.currentTimeMillis(),
                packageName = "",
                appLabel = "",
                type = type.name,
            )
        )
    }

    /**
     * Deletes all entries older than [historyHours] hours.
     * Called when the user changes the history window in Settings, to
     * immediately reflect the new window rather than waiting for the next insert.
     */
    suspend fun pruneOlderThan(historyHours: Int) {
        val cutoff = System.currentTimeMillis() - historyHours * 60 * 60 * 1000L
        dao.deleteOlderThan(cutoff)
    }
}
