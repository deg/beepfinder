package com.degel.beepfinder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * NotificationDao — the query interface.
 *
 * DAO PATTERN IN ROOM
 * ===================
 * A DAO (Data Access Object) is an interface that declares what database
 * operations you need. Room generates a concrete implementation at compile
 * time via KSP annotation processing. You never write SQL boilerplate or
 * cursor management code.
 *
 * Annotate methods with:
 *   @Insert        — Room generates the INSERT statement.
 *   @Update        — Room generates an UPDATE by primary key.
 *   @Delete        — Room generates a DELETE by primary key.
 *   @Query("...")  — You provide the SQL; Room validates it at compile time
 *                    and generates the cursor-to-object mapping.
 *
 * If your @Query references a column that doesn't exist or returns a type
 * that doesn't match your entity, you get a compile error — not a crash.
 *
 * ROOM + KOTLIN FLOW
 * ==================
 * When a @Query returns Flow<T>, Room wraps the query in a reactive observer.
 * Any time the relevant table is modified (INSERT, UPDATE, DELETE), Room
 * automatically re-runs the query and emits the new results to all active
 * collectors. This is what makes the UI update live without polling.
 *
 * The alternative (LiveData) works similarly but is tied to the Android
 * lifecycle framework. Flow is more general and composes better with
 * Kotlin coroutines.
 *
 * SUSPEND FUNCTIONS
 * =================
 * Methods annotated with @Insert and @Query that don't return Flow are
 * declared as `suspend fun`, meaning they must be called from a coroutine.
 * Room runs them on a background thread automatically (you don't specify
 * Dispatchers yourself for these). Non-suspend returning-Flow methods are
 * called from any thread; the Flow itself handles threading.
 */
@Dao
interface NotificationDao {

    /**
     * Inserts a single row. Room generates:
     *   INSERT INTO notifications (timestamp, packageName, ...) VALUES (?, ?, ...)
     *
     * The @PrimaryKey(autoGenerate = true) on the entity means the `id` field
     * is ignored on insert and assigned by SQLite's AUTOINCREMENT.
     */
    @Insert
    suspend fun insert(notification: NotificationEntity)

    /**
     * Returns all rows newer than [since] (a Unix timestamp in milliseconds),
     * ordered newest first.
     *
     * WHY NOT "getLast24Hours()"?
     * The method was renamed from getLast24Hours() to getRecent() to match
     * the repository-level name and make it clear the window is configurable.
     * The caller (NotificationRepository) computes the `since` timestamp from
     * the configured history window.
     *
     * Returning Flow<List<...>> makes this a "live query": Room re-emits
     * whenever the table changes. The list is complete (not paged) because
     * our history window is at most 7 days and the table is kept pruned.
     */
    @Query("SELECT * FROM notifications WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getRecent(since: Long): Flow<List<NotificationEntity>>

    /**
     * Deletes all rows older than [before] (a Unix timestamp in milliseconds).
     * Called after each insert and when the user changes the history window.
     *
     * SQLite has no built-in TTL/expiry mechanism, so we prune manually.
     * This keeps the DB small regardless of how long the app runs.
     */
    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
