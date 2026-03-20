package com.degel.beepfinder.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.degel.beepfinder.data.AppSettings
import com.degel.beepfinder.data.EntryType
import com.degel.beepfinder.data.NotificationDatabase
import com.degel.beepfinder.data.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class KnownApp(val packageName: String, val appLabel: String)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = AppSettings(app)
    private val repository = NotificationRepository(
        NotificationDatabase.getInstance(app).notificationDao()
    )

    var ignoredPackages by mutableStateOf(settings.ignoredPackages)
        private set

    var historyHours by mutableIntStateOf(settings.historyHours)
        private set

    // Raw entries from DB, refreshed when historyHours changes
    private val historyHoursFlow = MutableStateFlow(settings.historyHours)

    private val rawItems: Flow<List<ListItem>> = historyHoursFlow
        .flatMapLatest { hours -> repository.getRecent(hours) }
        .map { it.toListItems() }

    // Filtered list: service events always shown, notifications filtered by ignore list
    val listItems: Flow<List<ListItem>> = rawItems.map { items ->
        items.filter { item ->
            when (item) {
                is ListItem.Event -> true
                is ListItem.Group -> item.group.packageName !in ignoredPackages
            }
        }
    }

    // Distinct notification apps seen in the current history window (for Settings screen)
    val knownApps: Flow<List<KnownApp>> = rawItems.map { items ->
        items
            .filterIsInstance<ListItem.Group>()
            .map { KnownApp(it.group.packageName, it.group.appLabel) }
            .distinctBy { it.packageName }
            .sortedBy { it.appLabel }
    }

    fun toggleIgnored(packageName: String) {
        settings.toggleIgnored(packageName)
        ignoredPackages = settings.ignoredPackages
    }

    fun updateHistoryHours(hours: Int) {
        settings.historyHours = hours
        historyHours = hours
        historyHoursFlow.value = hours
        viewModelScope.launch { repository.pruneOlderThan(hours) }
    }
}
