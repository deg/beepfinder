package com.degel.beepfinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.degel.beepfinder.data.NotificationDatabase
import com.degel.beepfinder.data.NotificationEntity
import com.degel.beepfinder.data.NotificationRepository
import kotlinx.coroutines.flow.Flow

class NotificationViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NotificationRepository(
        NotificationDatabase.getInstance(app).notificationDao()
    )

    val notifications: Flow<List<NotificationEntity>> = repository.getLast24Hours()
}
