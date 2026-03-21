package com.degel.beepfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * NotificationEntity — the database row.
 *
 * ROOM ORM BASICS
 * ===============
 * Room is Android's official ORM layer over SQLite. It requires three pieces:
 *
 *   1. @Entity  — a data class that maps to a table (this file)
 *   2. @Dao     — an interface that defines queries (NotificationDao.kt)
 *   3. @Database — a RoomDatabase subclass that wires them together
 *                  (NotificationDatabase.kt)
 *
 * Room generates all the SQL and boilerplate at compile time via annotation
 * processing (specifically KSP — Kotlin Symbol Processing, listed as a
 * Gradle plugin). If you annotate something incorrectly, you get a compile
 * error, not a runtime crash — that's the main benefit over raw SQLite.
 *
 * SCHEMA
 * ======
 * We use a single "notifications" table for all entry types, discriminated
 * by the `type` column. This avoids joins and keeps queries simple.
 *
 * Service lifecycle events (start/stop) are stored alongside notification
 * entries so they appear in the same time-ordered stream in the UI. They
 * have empty packageName and appLabel fields.
 */

/**
 * Discriminates between rows in the notifications table.
 *
 * Stored as a String (the enum's .name) rather than an Int ordinal, so that
 * the DB remains human-readable in a SQLite browser and is resilient to
 * reordering of the enum cases.
 */
enum class EntryType {
    /** A notification from an installed app. */
    NOTIFICATION,
    /** The NotificationListenerService connected (app started or service restarted). */
    SERVICE_CONNECTED,
    /** The NotificationListenerService disconnected (killed, permission revoked, etc.). */
    SERVICE_DISCONNECTED,
}

/**
 * @Entity(tableName = "notifications") tells Room to create a table named
 * "notifications" for this class. Each property becomes a column.
 *
 * WHY A DATA CLASS?
 * Kotlin data classes automatically generate equals(), hashCode(), toString(),
 * and copy(). Room works well with them because it needs to instantiate them
 * from cursor rows, and data classes are purely structural (no behavior).
 *
 * DEFAULT VALUES
 * Room supports default values for columns. When inserting, you only need to
 * provide the values that differ from the defaults (e.g., you never set `id`).
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    /** Auto-incremented primary key. Room generates the value on INSERT. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** System.currentTimeMillis() at the moment the entry was recorded. */
    val timestamp: Long,

    /** Package name of the source app, e.g. "com.whatsapp". Empty for service events. */
    val packageName: String,

    /**
     * Human-readable app label, e.g. "WhatsApp". Resolved from PackageManager
     * at capture time and stored so the label is stable even if the app is later
     * uninstalled or renamed. Empty for service events.
     */
    val appLabel: String,

    /**
     * Discriminator column. Stored as the enum's String name (e.g. "NOTIFICATION")
     * rather than its ordinal Int for readability and schema stability.
     *
     * DEFAULT 'NOTIFICATION' in the SQL migration means existing rows (before
     * this column was added) are treated as notifications automatically.
     */
    val type: String = EntryType.NOTIFICATION.name,

    /**
     * Human-readable label of the notification sound, or null if the channel
     * uses the device default or the sound couldn't be identified.
     *
     * Nullable columns in Room are mapped to nullable Kotlin types.
     * The SQL type is TEXT (nullable by default in SQLite — SQLite has no
     * NOT NULL by default; Room adds that for non-nullable Kotlin types).
     */
    val soundLabel: String? = null,
) {
    /**
     * Convenience accessor that parses the stored String back to the enum.
     * We store the String name (not the enum directly) because Room doesn't
     * have a built-in enum converter — you'd need a @TypeConverter for that,
     * which adds complexity for little gain here.
     */
    fun entryType(): EntryType = EntryType.valueOf(type)
}
