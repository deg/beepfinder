package com.degel.beepfinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.degel.beepfinder.data.NotificationDatabase
import com.degel.beepfinder.data.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NotificationRepository(
        NotificationDatabase.getInstance(app).notificationDao()
    )

    val listItems: Flow<List<ListItem>> =
        repository.getLast24Hours().map { it.toListItems() }
}
