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

/** Represents an app that has appeared in the notification history. */
data class KnownApp(val packageName: String, val appLabel: String)

/**
 * NotificationViewModel — the bridge between the data layer and the UI.
 *
 * VIEWMODEL
 * =========
 * ViewModel is an Architecture Component that survives configuration changes
 * (screen rotations). When the user rotates the device, the Activity and all
 * its Composables are destroyed and recreated, but the ViewModel lives on.
 * This means the UI doesn't reload data unnecessarily.
 *
 * We use AndroidViewModel (a subclass that receives the Application) rather
 * than plain ViewModel because we need application context to access the
 * database and SharedPreferences. Never store an Activity context in a
 * ViewModel — it would outlive the Activity and cause a memory leak.
 *
 * HOW COMPOSE READS FROM VIEWMODEL
 * =================================
 * Two mechanisms are used here:
 *
 *   1. Flow → collectAsStateWithLifecycle (in the Composable)
 *      For the main data streams (listItems, knownApps). The Flow emits
 *      whenever Room's data changes. Compose recomposes the relevant
 *      sections of the UI automatically.
 *
 *   2. Compose State (mutableStateOf, mutableIntStateOf)
 *      For settings values that the ViewModel mutates in response to user
 *      actions (ignoredPackages, historyHours). Compose reads these values
 *      directly as properties; when they change, the reading composable
 *      recomposes.
 *
 * FLOW OPERATORS USED
 * ===================
 *   flatMapLatest: When the source flow (historyHoursFlow) emits a new value,
 *     cancel any in-progress inner flow and start a new one. This lets us
 *     switch to a different DB query when the history window changes without
 *     leaking the old query's subscription.
 *
 *   map: Transform each emitted list. Used twice — once for grouping/filtering,
 *     once for deriving the known-apps list.
 *
 *   distinctBy: Remove duplicates from a list by a key. Used to show each
 *     app only once in the Settings ignore list.
 */
@OptIn(ExperimentalCoroutinesApi::class)  // flatMapLatest is stable but requires opt-in
class NotificationViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)
    private val repository = NotificationRepository(
        NotificationDatabase.getInstance(app).notificationDao()
    )

    // ── Settings state ────────────────────────────────────────────────────────
    //
    // These are Compose State values, not LiveData or Flow. Compose reads them
    // directly via property access. The `by` delegate makes the syntax clean:
    // `ignoredPackages` reads the state; `ignoredPackages = ...` writes it.
    //
    // mutableIntStateOf is an optimized variant of mutableStateOf<Int> that
    // avoids boxing the Int into an object on every read.

    var ignoredPackages by mutableStateOf(settings.ignoredPackages)
        private set  // Only the ViewModel can write; the UI calls updateIgnored()

    var historyHours by mutableIntStateOf(settings.historyHours)
        private set

    // ── Reactive data pipeline ────────────────────────────────────────────────

    /**
     * A MutableStateFlow that acts as a trigger for switching DB queries.
     * When historyHours changes, we push the new value here, which causes
     * flatMapLatest to cancel the old DB query and start a new one.
     *
     * We use a separate StateFlow rather than observing `historyHours` directly
     * because Flow operators (flatMapLatest) can't observe Compose State.
     */
    private val historyHoursFlow = MutableStateFlow(settings.historyHours)

    /**
     * Raw list items from the DB, before the ignore filter is applied.
     * This is a cold Flow — it only runs when collected (i.e., when the UI is visible).
     *
     * flatMapLatest: each time historyHoursFlow emits, we call repository.getRecent()
     * with the new window. The previous inner flow is automatically cancelled.
     *
     * .map { it.toListItems() }: transforms raw DB rows into grouped display items.
     */
    private val rawItems: Flow<List<ListItem>> = historyHoursFlow
        .flatMapLatest { hours -> repository.getRecent(hours) }
        .map { it.toListItems() }

    /**
     * The final list displayed in the UI: rawItems with ignored apps filtered out.
     * Service events (start/stop markers) are never filtered regardless of settings.
     *
     * This flow is collected in NotificationListScreen via collectAsStateWithLifecycle,
     * which automatically pauses collection when the app is in the background
     * (lifecycle-aware) and resumes when it comes to the foreground.
     */
    val listItems: Flow<List<ListItem>> = rawItems.map { items ->
        items.filter { item ->
            when (item) {
                is ListItem.Event -> true  // Service events are always shown
                is ListItem.Group -> item.group.packageName !in ignoredPackages
            }
        }
    }

    /**
     * The list of distinct apps seen in the current history window, sorted
     * alphabetically. Used by SettingsScreen to populate the ignore-list toggles.
     *
     * Derived from rawItems (before ignore filter) so that ignored apps still
     * appear in Settings and can be un-ignored.
     */
    val knownApps: Flow<List<KnownApp>> = rawItems.map { items ->
        items
            .filterIsInstance<ListItem.Group>()
            .map { KnownApp(it.group.packageName, it.group.appLabel) }
            .distinctBy { it.packageName }
            .sortedBy { it.appLabel }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    /** Toggles the ignore status of [packageName] and persists the change. */
    fun toggleIgnored(packageName: String) {
        settings.toggleIgnored(packageName)
        // Update the Compose State so the UI recomposes immediately.
        ignoredPackages = settings.ignoredPackages
    }

    /**
     * Changes the history retention window to [hours] and persists it.
     *
     * updateHistoryHours (not setHistoryHours) avoids a JVM name clash:
     * `var historyHours` generates a setter named setHistoryHours(Int) at the
     * bytecode level, which would conflict with a method of the same signature.
     */
    fun updateHistoryHours(hours: Int) {
        settings.historyHours = hours
        historyHours = hours
        historyHoursFlow.value = hours  // Triggers flatMapLatest to switch queries
        // Prune records older than the new window immediately.
        viewModelScope.launch { repository.pruneOlderThan(hours) }
    }
}
