package com.degel.beepfinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * NotificationDatabase — the Room database singleton.
 *
 * ROOM DATABASE CLASS
 * ===================
 * A RoomDatabase subclass is the entry point to the database. It:
 *   - Declares which @Entity classes belong to it (the tables).
 *   - Declares the schema version number.
 *   - Provides abstract accessor methods for each @Dao.
 *   - Holds the singleton instance.
 *
 * Room generates a concrete implementation of this abstract class at
 * compile time. The generated class handles opening the SQLite file,
 * running migrations, and managing the connection pool.
 *
 * The database file is stored in the app's private data directory
 * (/data/data/com.degel.beepfinder/databases/beepfinder.db). It is
 * not accessible to other apps without root.
 *
 * SINGLETON PATTERN
 * =================
 * A RoomDatabase is expensive to create (it opens a file, runs migrations,
 * builds prepared statement caches). You should have exactly one instance
 * per database per process. The companion object below implements the
 * classic double-checked locking singleton.
 *
 * @Volatile on INSTANCE ensures that writes by one thread are immediately
 * visible to other threads (prevents CPU cache incoherence).
 *
 * SCHEMA MIGRATIONS
 * =================
 * When you change @Entity fields, you must increment the version number and
 * provide a Migration object that describes the SQL changes. Room will run
 * the appropriate migration(s) in sequence when opening an older database.
 *
 * If you don't provide a migration, Room will throw an exception by default
 * (or destroy and recreate the database if you call .fallbackToDestructiveMigration()).
 * We never use destructive migration because we'd lose the notification history.
 *
 * Migration history:
 *   v1 → v2: Added `type TEXT NOT NULL DEFAULT 'NOTIFICATION'` column.
 *             (Service lifecycle event logging.)
 *   v2 → v3: Added `soundLabel TEXT` column (nullable).
 *             (Notification sound identification.)
 *
 * SQLite ALTER TABLE only supports ADD COLUMN — you can't remove or rename
 * columns without recreating the table (which Room's migration system can do,
 * but we haven't needed it yet).
 */
@Database(
    entities = [NotificationEntity::class],
    version = 3,
    // exportSchema = true would write the schema to a JSON file for version
    // control. Useful in production apps to audit schema changes. We set it
    // false to keep the project simpler.
    exportSchema = false,
)
abstract class NotificationDatabase : RoomDatabase() {

    /** Room generates the implementation of this method. */
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: NotificationDatabase? = null

        /**
         * Migration from schema version 1 to 2.
         *
         * ALTER TABLE ... ADD COLUMN is the only non-destructive schema change
         * SQLite supports. The DEFAULT clause ensures existing rows get a valid
         * value for the new column without a data scan.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN type TEXT NOT NULL DEFAULT 'NOTIFICATION'"
                )
            }
        }

        /**
         * Migration from schema version 2 to 3.
         *
         * Nullable TEXT column — no DEFAULT needed because null is a valid
         * value for all existing rows (they have no known sound).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN soundLabel TEXT")
            }
        }

        /**
         * Returns the singleton database instance, creating it if needed.
         *
         * [context] should be the application context (not an Activity context)
         * to avoid leaking the Activity. Room.databaseBuilder() accepts any
         * Context but internally calls applicationContext on it.
         *
         * addMigrations() registers all known migrations. Room applies them in
         * order based on the version numbers, skipping ones that don't apply
         * (e.g., installing fresh on v3 skips all migrations).
         */
        fun getInstance(context: Context): NotificationDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotificationDatabase::class.java,
                    "beepfinder.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
