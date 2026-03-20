package com.degel.beepfinder.service

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.degel.beepfinder.data.NotificationDatabase
import com.degel.beepfinder.data.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BeepListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: NotificationRepository

    // Deduplication: track (packageName, notificationId) -> timestamp of last log
    private val recentlyLogged = mutableMapOf<String, Long>()
    private val dedupeWindowMs = 3_000L

    override fun onCreate() {
        super.onCreate()
        val db = NotificationDatabase.getInstance(applicationContext)
        repository = NotificationRepository(db.notificationDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. Skip group summary notifications — these are synthetic rollups,
        //    not individual alerts. The real alert already fired a separate callback.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 2. Skip non-audible notifications by checking the channel importance.
        //    IMPORTANCE_DEFAULT (3) and above produce sound; lower values are silent.
        if (!isAudible(sbn)) return

        // 3. Deduplicate updates — apps often repost the same notification ID
        //    (e.g., to add an action button). Only log if this exact
        //    (package, notificationId) hasn't been logged in the last 3 seconds.
        val dedupeKey = "${sbn.packageName}:${sbn.id}"
        val now = System.currentTimeMillis()
        val lastLogged = recentlyLogged[dedupeKey] ?: 0L
        if (now - lastLogged < dedupeWindowMs) return
        recentlyLogged[dedupeKey] = now

        // Prune old deduplication entries to avoid unbounded growth
        recentlyLogged.entries.removeAll { now - it.value > dedupeWindowMs * 10 }

        val appLabel = resolveAppLabel(sbn.packageName)
        scope.launch {
            repository.record(sbn.packageName, appLabel)
        }
    }

    private fun isAudible(sbn: StatusBarNotification): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = sbn.notification.channelId
        if (channelId != null) {
            val channel = nm.getNotificationChannel(channelId)
            if (channel != null) {
                return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
            }
        }
        // Fallback for notifications without a channel (pre-Android 8 style):
        // check if the notification itself specifies a sound.
        val n = sbn.notification
        @Suppress("DEPRECATION")
        return n.sound != null || (n.defaults and Notification.DEFAULT_SOUND) != 0
    }

    private fun resolveAppLabel(packageName: String): String =
        try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
