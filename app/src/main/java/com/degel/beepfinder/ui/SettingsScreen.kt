package com.degel.beepfinder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.degel.beepfinder.data.AppSettings

/**
 * SettingsScreen — lets the user configure history retention and ignored apps.
 *
 * SHARED VIEWMODEL ACROSS SCREENS
 * =================================
 * Both SettingsScreen and NotificationListScreen call `viewModel()` with the
 * same type (NotificationViewModel). Because both composables are hosted in
 * the same Activity (and therefore the same ViewModelStore), they receive the
 * exact same ViewModel instance. Any state change in one screen is immediately
 * visible in the other.
 *
 * This is the "single source of truth" principle applied to Android architecture:
 * settings changes (ignore list, history window) made here are reflected in the
 * list screen without any explicit communication between the two composables.
 *
 * TWO STATE SOURCES IN ONE SCREEN
 * ================================
 * This screen reads state from two different mechanisms:
 *
 *   1. Flow → collectAsStateWithLifecycle (knownApps)
 *      knownApps is derived from the DB via the ViewModel's Flow pipeline.
 *      It updates automatically when new notifications arrive (new apps appear
 *      in the list) or the history window changes (apps older than the new
 *      window disappear).
 *
 *   2. Direct property reads (ignoredPackages, historyHours)
 *      These are Compose mutableStateOf values exposed directly from the
 *      ViewModel. Compose reads them like any other state — when the ViewModel
 *      writes to them (e.g., after toggleIgnored()), Compose recomposes the
 *      affected parts of this screen automatically.
 *
 * LAZYLISTING MIXED CONTENT TYPES
 * =================================
 * LazyColumn's DSL supports multiple item types in a single list:
 *
 *   item { ... }        — a single item (used for section headers and the
 *                         history chip row, which are not repeated)
 *   items(knownApps)    — one item per element of a list (the app rows)
 *
 * This is more flexible than RecyclerView's multi-type adapter pattern:
 * no view type integers, no adapter classes — just inline composable calls
 * in the correct position.
 *
 * The LazyColumn replaces the outer Column+ScrollView combination you'd use
 * in a View-based UI. The entire screen (headers + chips + list) scrolls as
 * one continuous list.
 *
 * NAVIGATION ICON
 * ===============
 * TopAppBar's navigationIcon slot holds the leading icon — conventionally a
 * back arrow for screens that aren't the root. We use the AutoMirrored variant
 * of ArrowBack, which automatically flips the icon in right-to-left locales
 * (languages like Arabic that read right-to-left use a forward-pointing arrow
 * as the "back" affordance).
 *
 * @OptIn(ExperimentalMaterial3Api::class) is needed because TopAppBar's API
 * is still in the experimental Material 3 tier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: NotificationViewModel = viewModel(),
) {
    // Collect the live list of apps seen in the current history window.
    val knownApps by vm.knownApps.collectAsStateWithLifecycle(initialValue = emptyList())
    // Read ViewModel's Compose State properties directly — no Flow collection needed.
    val ignoredPackages = vm.ignoredPackages
    val historyHours = vm.historyHours

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ── History Retention ─────────────────────────────────────────────

            item {
                SectionHeader("History Retention")
            }
            item {
                /**
                 * FILTERCHIP ROW
                 * ==============
                 * FilterChip is a Material 3 component for single/multi-select
                 * filter options. Here they act as a segmented selector for the
                 * history window.
                 *
                 * AppSettings.HISTORY_OPTIONS is a list of (hours, label) pairs
                 * defined in AppSettings.kt. We iterate over it with forEach —
                 * this is possible inside an item {} because we know the count
                 * is small and fixed (no lazy optimization needed).
                 *
                 * selected = historyHours == hours: The chip reads its selection
                 * state from the ViewModel. When the user taps a chip, the
                 * ViewModel updates historyHours, which triggers recomposition,
                 * which re-evaluates `selected` for all chips. This is the
                 * "state drives UI" pattern — the chip never stores its own
                 * selected state.
                 *
                 * Arrangement.spacedBy(8.dp): adds 8dp of space between each
                 * chip, rather than requiring manual Spacer() calls.
                 */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.HISTORY_OPTIONS.forEach { (hours, label) ->
                        FilterChip(
                            selected = historyHours == hours,
                            onClick = { vm.updateHistoryHours(hours) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ── Ignored Apps ──────────────────────────────────────────────────

            item {
                SectionHeader("Ignored Apps")
            }
            if (knownApps.isEmpty()) {
                // Empty state: a single informational text item.
                item {
                    Text(
                        "No apps seen yet — notifications will appear here once BeepFinder records some.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // One row per known app. items() generates a separate composable
                // for each element — only visible rows are composed (lazy).
                items(knownApps) { app ->
                    IgnoreAppRow(
                        app = app,
                        ignored = app.packageName in ignoredPackages,
                        onToggle = { vm.toggleIgnored(app.packageName) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * SectionHeader — a styled section title used to group related settings.
 *
 * PRIVATE UTILITY COMPOSABLES
 * ============================
 * SectionHeader and IgnoreAppRow are private because they are implementation
 * details of SettingsScreen. Keeping them private:
 *   - Prevents them from being accidentally reused in inconsistent contexts.
 *   - Allows the file to be refactored without worrying about external callers.
 *   - Signals to readers that these are internal helpers, not public components.
 *
 * titleSmall (14sp, medium weight) is the Material 3 type scale role for
 * section labels — slightly bolder than body text but smaller than a heading.
 * colorScheme.primary gives it the app's accent color, distinguishing it
 * visually from regular body text.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * IgnoreAppRow — a single row with an app icon, app name, and an ignore toggle.
 *
 * SWITCH COMPONENT
 * ================
 * Switch is Material 3's toggle component. Key points:
 *   - `checked`: the current state, driven entirely by `ignored` (a parameter
 *     derived from the ViewModel). The Switch has no internal state.
 *   - `onCheckedChange`: called when the user taps the switch, with the new
 *     desired boolean. We ignore the parameter and call onToggle() instead,
 *     because the ViewModel's toggleIgnored() is the authoritative mutation.
 *     The Switch's new state is determined by the ViewModel, not by the lambda's
 *     `it` parameter — this preserves unidirectional data flow.
 *
 * UNIDIRECTIONAL DATA FLOW (UDF) RECAP
 * ======================================
 * The pattern used throughout BeepFinder:
 *   - State flows DOWN: ViewModel → Composable (via parameters/collectAsState)
 *   - Events flow UP: Composable → ViewModel (via lambda callbacks)
 *
 * This row exemplifies it:
 *   Down: `ignored` parameter tells the Switch whether to appear on or off.
 *   Up:   `onToggle()` lambda tells the ViewModel the user changed the toggle.
 *   The ViewModel updates its state, which causes Compose to recompose this
 *   row with the new `ignored` value. The Switch never owns its own state.
 *
 * WEIGHT MODIFIER
 * ===============
 * `modifier = Modifier.weight(1f)` on the Text gives it all remaining
 * horizontal space after the fixed-size AppIcon (40dp) and Switch are
 * measured. This ensures long app names wrap gracefully rather than pushing
 * the Switch off-screen.
 */
@Composable
private fun IgnoreAppRow(app: KnownApp, ignored: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.appLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = ignored,
            onCheckedChange = { onToggle() },
        )
    }
}
