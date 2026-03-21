package com.degel.beepfinder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.degel.beepfinder.MainActivity
import com.degel.beepfinder.R
import com.degel.beepfinder.data.EntryType
import com.degel.beepfinder.data.NotificationDatabase
import com.degel.beepfinder.data.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BeepListenerService — the core of BeepFinder.
 *
 * NOTIFICATIONLISTENERSERVICE
 * ===========================
 * NotificationListenerService is a system-provided base class for services
 * that want to receive callbacks for every notification posted by any app.
 *
 * To use it:
 *   1. Subclass NotificationListenerService and override the callbacks.
 *   2. Register it in AndroidManifest.xml with the right intent-filter
 *      and permission (see AndroidManifest.xml for details).
 *   3. The user must explicitly grant "Notification Access" in system Settings
 *      (Settings → Apps → Special app access → Notification access).
 *
 * The OS manages the service lifecycle entirely. You don't call startService()
 * or stopService(). The service starts when permission is granted and
 * (re)starts after device reboots automatically.
 *
 * FOREGROUND SERVICE
 * ==================
 * By default, Android can kill background services under memory pressure or
 * when Doze mode activates. To prevent this, we promote the service to a
 * "foreground service" by calling startForeground() with a visible notification.
 *
 * A foreground service:
 *   - Must display a persistent notification (the user can see it's running).
 *   - Gets a higher process priority — Android won't kill it without cause.
 *   - On Android 14+, must declare a foreground service TYPE in both the
 *     manifest and the startForeground() call (we use DATA_SYNC).
 *
 * We call startForeground() in onListenerConnected() rather than onCreate()
 * because that's when the OS has confirmed the binding is active.
 *
 * COROUTINE SCOPE
 * ===============
 * Services run on the main thread. Any database work (Room) must happen off
 * the main thread. We create a private CoroutineScope tied to a SupervisorJob.
 *
 * SupervisorJob: if one child coroutine fails, others keep running (vs a
 * regular Job which would cancel all siblings on failure).
 *
 * Dispatchers.IO: a thread pool optimized for I/O blocking work (file, DB,
 * network). Room requires being called from a non-main thread; using IO here
 * satisfies that requirement.
 *
 * We cancel the scope in onDestroy() to clean up any in-flight coroutines.
 */
class BeepListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: NotificationRepository
    private lateinit var nm: NotificationManager

    /**
     * Deduplication map: (packageName:notificationId) → last-logged timestamp.
     *
     * Many apps repost the same notification ID to update its content (e.g.,
     * adding an "Mark as read" action after the notification appears). Each
     * repost fires onNotificationPosted(). We only log one entry per
     * (package, ID) pair within a 3-second window.
     *
     * This map lives in memory only — it resets when the service restarts.
     * That's intentional: we don't want to permanently suppress an app.
     */
    private val recentlyLogged = mutableMapOf<String, Long>()
    private val dedupeWindowMs = 3_000L

    override fun onCreate() {
        super.onCreate()
        val db = NotificationDatabase.getInstance(applicationContext)
        repository = NotificationRepository(db.notificationDao())
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createStatusChannel()
    }

    /**
     * Called when the OS has successfully bound to this service and it is
     * ready to receive notifications. This is the right place to call
     * startForeground() — not onCreate(), because the binding isn't confirmed
     * there yet.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        startForeground(
            STATUS_NOTIF_ID,
            buildStatusNotification("Listening for notifications…"),
            // ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC must match the
            // foregroundServiceType declared in AndroidManifest.xml.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        scope.launch { repository.recordServiceEvent(EntryType.SERVICE_CONNECTED) }
    }

    /**
     * Called when the binding is lost — either the user revoked notification
     * access, or the OS killed the binding (rare with foreground service).
     * We log this as a gap marker so the user can see the monitoring stopped.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        scope.launch { repository.recordServiceEvent(EntryType.SERVICE_DISCONNECTED) }
    }

    /**
     * The main entry point. Called by the OS for every notification posted
     * by any app on the device.
     *
     * [sbn] (StatusBarNotification) provides:
     *   - sbn.packageName     — the posting app's package name
     *   - sbn.id              — the notification ID (stable for updates)
     *   - sbn.notification    — the Notification object (flags, channel, sound, etc.)
     *   - sbn.postTime        — when the notification was posted
     *
     * We apply three filters before logging:
     *   1. Skip group summary (synthetic rollup notification)
     *   2. Skip non-audible (channel importance below DEFAULT)
     *   3. Skip duplicate repost within 3 seconds
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // FILTER 1: Group summaries.
        //
        // When an app posts grouped notifications (e.g., 3 new emails), Android
        // generates a "group summary" notification in addition to the individual
        // ones. The summary triggers its own onNotificationPosted() call but
        // represents no additional alert to the user. FLAG_GROUP_SUMMARY
        // identifies it.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // FILTER 2: Audibility check (see isAudible() below).
        if (!isAudible(sbn)) return

        // FILTER 3: Deduplication.
        //
        // Apps frequently update notifications (changing text, adding action
        // buttons) by calling NotificationManager.notify() with the same ID.
        // Each such call fires onNotificationPosted(). We ignore the same
        // (package, notificationId) pair within a 3-second window.
        val dedupeKey = "${sbn.packageName}:${sbn.id}"
        val now = System.currentTimeMillis()
        val lastLogged = recentlyLogged[dedupeKey] ?: 0L
        if (now - lastLogged < dedupeWindowMs) return
        recentlyLogged[dedupeKey] = now

        // Periodically prune the dedup map to prevent unbounded memory growth.
        // We keep entries 10x the window (30s) so rapid-fire updates are still
        // deduplicated even if they span multiple 3-second windows.
        recentlyLogged.entries.removeAll { now - it.value > dedupeWindowMs * 10 }

        val appLabel = resolveAppLabel(sbn.packageName)
        val soundLabel = resolveSoundLabel(sbn)

        // Update the persistent foreground notification to show the latest beep.
        // This doubles as the "quick-glance" feature — the user can see the
        // last beep source in the notification shade without opening the app.
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        nm.notify(STATUS_NOTIF_ID, buildStatusNotification("Last: $appLabel at $timeStr"))

        // Write to the database on the IO dispatcher. The UI will update
        // automatically via Room's Flow integration.
        scope.launch {
            repository.record(sbn.packageName, appLabel, soundLabel)
        }
    }

    /**
     * Returns true if the notification is expected to produce an audible alert.
     *
     * The primary check is the NotificationChannel importance. Android 8+ apps
     * configure sound behavior at the channel level, not per-notification:
     *
     *   IMPORTANCE_NONE (0)    — not shown at all
     *   IMPORTANCE_MIN (1)     — shown minimized, no sound
     *   IMPORTANCE_LOW (2)     — shown, no sound
     *   IMPORTANCE_DEFAULT (3) — makes sound (the "beep" threshold)
     *   IMPORTANCE_HIGH (4)    — makes sound, pops up
     *   IMPORTANCE_MAX (5)     — full-screen intent
     *
     * The fallback (Notification.sound / Notification.defaults) handles the
     * rare case of pre-Android-8 style notifications that lack a channel.
     * These fields are deprecated in modern Android but still work.
     */
    private fun isAudible(sbn: StatusBarNotification): Boolean {
        val channelId = sbn.notification.channelId
        if (channelId != null) {
            val channel = nm.getNotificationChannel(channelId)
            if (channel != null) {
                return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
            }
        }
        // Pre-channel (API < 26) fallback: check the notification's own sound config.
        val n = sbn.notification
        @Suppress("DEPRECATION")
        return n.sound != null || (n.defaults and Notification.DEFAULT_SOUND) != 0
    }

    /**
     * Attempts to resolve a human-readable name for the notification sound.
     * Returns null if the channel uses the device default or the sound can't
     * be identified — we log "unknown" rather than an opaque URI string.
     *
     * Sound URIs come in two common forms:
     *
     *   content://media/internal/audio/media/123
     *     → A MediaStore record. Query MediaStore.Audio.Media.TITLE for the name.
     *
     *   android.resource://com.example.app/raw/notification_sound
     *     → An in-app resource. The last path segment is typically a readable name.
     *
     * A null URI from NotificationChannel.getSound() means the channel inherits
     * the device's default notification sound — we return null for that case.
     */
    private fun resolveSoundLabel(sbn: StatusBarNotification): String? {
        val channelId = sbn.notification.channelId ?: return null
        val channel = nm.getNotificationChannel(channelId) ?: return null
        val soundUri: Uri = channel.sound ?: return null  // null = device default

        try {
            val cursor: Cursor? = contentResolver.query(
                soundUri,
                arrayOf(MediaStore.Audio.Media.TITLE),
                null, null, null,
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val title = it.getString(0)
                    if (!title.isNullOrBlank()) return title
                }
            }
        } catch (_: Exception) {
            // The URI may not be a MediaStore URI — silently fall through.
        }

        // Fall back to the last path segment of the URI.
        // For resource URIs like ".../raw/tri_tone" this gives "tri_tone".
        return soundUri.lastPathSegment
    }

    /**
     * Looks up the human-readable app name ("WhatsApp") from the package
     * name ("com.whatsapp") using PackageManager.
     *
     * Returns the package name as a fallback if the app is not found
     * (e.g., if it was uninstalled between the notification arriving and
     * our lookup — unlikely but possible).
     */
    private fun resolveAppLabel(packageName: String): String =
        try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

    // ── Foreground notification helpers ──────────────────────────────────────

    /**
     * Creates the notification channel for our persistent status notification.
     *
     * Notification channels (introduced in Android 8) let users control
     * notification behavior (sound, importance, etc.) per category.
     * You must create the channel before posting any notification on it.
     *
     * IMPORTANCE_LOW: shown in the shade, no sound, no badge.
     * setShowBadge(false): don't show a numeric badge on the app icon.
     *
     * Creating an already-existing channel is a no-op — safe to call
     * repeatedly (e.g., every time the service is created).
     */
    private fun createStatusChannel() {
        val channel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "BeepFinder Status",       // Shown in system notification settings
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows that BeepFinder is actively monitoring notifications"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Builds the persistent foreground notification.
     *
     * NotificationCompat.Builder is the recommended builder — it handles
     * compatibility differences across API levels transparently.
     *
     * setOngoing(true): the user cannot swipe this notification away.
     * setSilent(true): updating the notification doesn't make a sound.
     *
     * The PendingIntent opens MainActivity when the user taps the notification.
     * PendingIntent.FLAG_IMMUTABLE is required on Android 12+ for intents
     * that you don't need to modify later.
     */
    private fun buildStatusNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("BeepFinder")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_status)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the SupervisorJob, which cancels all child coroutines.
        // Without this, coroutines would continue running after the service
        // is destroyed, potentially causing crashes or resource leaks.
        job.cancel()
    }

    companion object {
        private const val STATUS_CHANNEL_ID = "beepfinder_status"
        const val STATUS_NOTIF_ID = 1

        /**
         * Whether the service is currently connected and receiving notifications.
         *
         * @Volatile ensures changes made in one thread are immediately visible
         * to other threads (prevents CPU cache incoherence for this single
         * field). This is sufficient here because we only ever write it from
         * the main thread (onListenerConnected/Disconnected) and only read it
         * from the UI thread (Compose).
         *
         * A more robust approach (using StateFlow) would let Compose recompose
         * reactively when this changes, but a simple flag is sufficient since
         * we only check it on recomposition triggered by other events.
         */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
