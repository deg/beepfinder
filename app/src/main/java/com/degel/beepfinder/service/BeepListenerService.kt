package com.degel.beepfinder.service

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

    override fun onCreate() {
        super.onCreate()
        val db = NotificationDatabase.getInstance(applicationContext)
        repository = NotificationRepository(db.notificationDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val appLabel = try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        scope.launch {
            repository.record(packageName, appLabel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
