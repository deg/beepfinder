package com.degel.beepfinder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.degel.beepfinder.data.EntryType
import com.degel.beepfinder.service.BeepListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NotificationListScreen — the main screen of BeepFinder.
 *
 * COMPOSABLE FUNCTION PARAMETERS
 * ================================
 * The parameters use default values to simplify both call sites and Compose
 * Preview annotations:
 *
 *   vm: NotificationViewModel = viewModel()
 *     viewModel() is a Compose extension from lifecycle-viewmodel-compose that
 *     retrieves (or creates) the ViewModel scoped to the nearest ViewModelStore
 *     owner (the Activity). Crucially, this means the same ViewModel instance
 *     is returned on every recomposition and across screen rotations. Passing vm
 *     as a parameter (with viewModel() as the default) makes this composable
 *     testable — a test can inject a fake ViewModel instead of using the default.
 *
 *   isBatteryExempt, onRequestBatteryExemption, onOpenSettings
 *     These are "event up" parameters — the parent (BeepFinderApp) owns the
 *     battery state and navigation logic; this screen just signals intent by
 *     calling the provided lambdas. This keeps the screen reusable and isolated.
 *
 * @OptIn(ExperimentalMaterial3Api::class)
 *   TopAppBar is still marked experimental in Material 3 (its API may change
 *   in future library versions). This annotation suppresses the compiler warning.
 *
 * COLLECTING FLOW AS COMPOSE STATE
 * =================================
 * `val listItems by vm.listItems.collectAsStateWithLifecycle(initialValue = emptyList())`
 *
 * This single line does a lot:
 *   - vm.listItems is a Kotlin Flow<List<ListItem>> that Room emits to whenever
 *     the database changes.
 *   - collectAsStateWithLifecycle() collects the Flow and wraps the latest
 *     emitted value in a Compose State<T>.
 *   - "WithLifecycle" means collection is automatically paused when this
 *     composable's lifecycle owner (the Activity) is in the background (STOPPED
 *     state) and resumed when it comes back to the foreground. This prevents
 *     wasted work — Room's Flow is still emitting, but we're not processing it.
 *   - initialValue = emptyList() is shown until the first emission arrives
 *     (Room queries are asynchronous; the first result takes a few milliseconds).
 *   - The `by` delegate unwraps the State<List<ListItem>> to List<ListItem>,
 *     so we can write `listItems` instead of `listItems.value` everywhere.
 *
 * READING VOLATILE SERVICE STATE
 * ================================
 * `val serviceConnected = BeepListenerService.isConnected`
 *
 * This reads the companion object's @Volatile Boolean directly. It is NOT
 * reactive — Compose won't recompose when this changes. This is acceptable
 * because: (a) connected/disconnected events are infrequent, and (b) the list
 * itself updates on reconnect (a SERVICE_CONNECTED row appears), which triggers
 * a recomposition from listItems, at which point we re-read isConnected too.
 * A more reactive approach would use StateFlow; see BeepListenerService for the
 * trade-off discussion.
 *
 * LOCAL UI STATE
 * ==============
 * `var batteryBannerDismissed by remember { mutableStateOf(false) }`
 *
 * This is ephemeral UI state — it should reset when the Activity is destroyed.
 * We deliberately do NOT hoist this into the ViewModel because:
 *   - It controls a UI detail (banner visibility), not app data.
 *   - Dismissing the banner is a single-session action; it should reappear
 *     after the user force-quits and reopens the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    vm: NotificationViewModel = viewModel(),
    isBatteryExempt: Boolean = true,
    onRequestBatteryExemption: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val listItems by vm.listItems.collectAsStateWithLifecycle(initialValue = emptyList())
    var batteryBannerDismissed by remember { mutableStateOf(false) }
    val serviceConnected = BeepListenerService.isConnected

    /**
     * SCAFFOLD
     * ========
     * Scaffold implements the Material 3 visual layout structure. It handles:
     *   - Top bar positioning (below the system status bar)
     *   - Content area insets (the padding lambda parameter)
     *   - FAB, bottom bar, snackbar slots (not used here)
     *
     * The content lambda receives a PaddingValues that accounts for the top
     * bar height and system bar insets. Apply it to the top-level content
     * modifier to avoid content appearing behind the app bar.
     */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BeepFinder") },
                actions = {
                    // The actions slot is in the trailing section of the top bar.
                    // Items are laid out left-to-right in the order added.
                    ServiceStatusDot(serviceConnected)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The battery banner is only shown when the user hasn't dismissed
            // it AND the app isn't already exempt from battery optimizations.
            if (!isBatteryExempt && !batteryBannerDismissed) {
                BatteryWarningBanner(
                    onFix = onRequestBatteryExemption,
                    onDismiss = { batteryBannerDismissed = true },
                )
            }
            if (listItems.isEmpty()) {
                // Empty state: center a message. Box with contentAlignment
                // is the idiomatic way to center a single child in Compose.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No audible notifications in the last 24 hours")
                }
            } else {
                /**
                 * LAZYCOLUMN
                 * ==========
                 * LazyColumn is Compose's equivalent of RecyclerView. It only
                 * composes and lays out the items currently visible on screen,
                 * plus a small buffer. For a long notification history this is
                 * critical — composing 1,000 rows at once would be slow and
                 * wasteful.
                 *
                 * The items() DSL function generates one item composable per
                 * element in the list. Compose identifies each item by its
                 * position by default; if items could be reordered, providing
                 * a key parameter (key = { it.id }) improves animation and
                 * recomposition efficiency.
                 *
                 * contentPadding adds space before the first item and after
                 * the last item, without cutting off items at the edges.
                 *
                 * SEALED INTERFACE DISPATCH
                 * ==========================
                 * The `when (item)` here is an exhaustive match on the sealed
                 * interface ListItem. Because ListItem is sealed, the compiler
                 * knows the only subtypes are Group and Event, so no `else`
                 * branch is needed. Adding a new subtype to ListItem would
                 * immediately cause a compile error here — a useful safety net.
                 */
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(listItems) { item ->
                        when (item) {
                            is ListItem.Group -> {
                                NotificationGroupRow(item.group)
                                HorizontalDivider()
                            }
                            is ListItem.Event -> {
                                ServiceEventRow(item.event)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ServiceStatusDot — a small colored dot + label showing monitoring status.
 *
 * CANVAS IN COMPOSE
 * =================
 * androidx.compose.foundation.Canvas is a low-level drawing composable.
 * Its content lambda receives a DrawScope, which provides drawing primitives
 * (drawCircle, drawRect, drawPath, etc.) in the composable's coordinate space.
 *
 * We use it here to draw a colored circle (10dp) because Material 3 doesn't
 * provide a simple "status dot" component. Canvas gives pixel-level control
 * without dropping down to an Android View.
 *
 * Color values use the 0xFFRRGGBB format where FF is full alpha (opaque).
 * Color(0xFF4CAF50) is Material's standard green-500 (#4CAF50).
 *
 * PRIVATE COMPOSABLE
 * ==================
 * ServiceStatusDot is private to this file because it's an internal detail of
 * NotificationListScreen. Making it private prevents accidental reuse elsewhere
 * and keeps the public API surface minimal. Same applies to BatteryWarningBanner,
 * NotificationGroupRow, and ServiceEventRow below.
 */
@Composable
private fun ServiceStatusDot(connected: Boolean) {
    val color = if (connected) Color(0xFF4CAF50) else Color(0xFFF44336)
    val label = if (connected) "Listening" else "Stopped"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(end = 0.dp),
        ) {
            // Colored dot
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/**
 * BatteryWarningBanner — dismissible warning that battery optimization may
 * cause gaps in monitoring.
 *
 * CARD AND COLOR SCHEME TOKENS
 * =============================
 * Card is a Material 3 container with elevation and rounded corners.
 * CardDefaults.cardColors() lets us override the default container color.
 *
 * MaterialTheme.colorScheme.errorContainer is a semantic color token —
 * it's a muted version of the error color, suitable for a warning container.
 * Using semantic tokens (rather than hardcoded colors) ensures the banner
 * looks correct in both light and dark mode and with Material You dynamic
 * colors.
 *
 * onErrorContainer is the paired text/icon color guaranteed to be readable
 * over errorContainer — the "on" prefix is Material's convention for the
 * foreground color corresponding to a background token.
 *
 * LAYOUT: ROW WITH WEIGHT
 * =======================
 * Row lays out children horizontally. `modifier = Modifier.weight(1f)` on
 * the Column causes it to take all remaining horizontal space after the
 * fixed-size buttons are measured. This is how you push buttons to the
 * trailing edge while allowing text to fill the available width.
 */
@Composable
private fun BatteryWarningBanner(onFix: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Battery optimization is on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "BeepFinder may stop recording when the screen is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onFix) {
                Text("Fix", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/**
 * NotificationGroupRow — displays one grouped run of same-app notifications.
 *
 * REMEMBER FOR EXPENSIVE OBJECTS
 * ================================
 * `val timeFmt = remember { SimpleDateFormat(...) }`
 *
 * SimpleDateFormat is not lightweight — creating one involves locale lookups
 * and pattern parsing. `remember { }` without a key parameter creates the
 * object once for this composable instance and reuses it across recompositions.
 * This is important because LazyColumn recomposes rows frequently as the user
 * scrolls, and creating a new SimpleDateFormat on every recomposition would
 * be wasteful.
 *
 * REMEMBER WITH KEYS
 * ==================
 * `val timeLabel = remember(group.latestTimestamp, ...) { ... }`
 *
 * When remember is given key parameters, it recalculates the value only when
 * those keys change. Here, timeLabel recomputes only when the timestamps or
 * count change — not on every unrelated recomposition. Without keys, remember
 * would keep the stale value forever.
 *
 * CONDITIONAL LAYOUT
 * ==================
 * The count badge is only added to the Row when group.count > 1. In Compose,
 * `if (...)` inside a composable conditionally includes/excludes composables
 * from the tree. When the condition becomes false, the Badge is removed and
 * Compose skips composing it entirely — no visibility toggling needed.
 *
 * MATERIAL 3 BADGE
 * ================
 * Badge is a small chip-like container, typically used for counts (unread
 * messages, etc.). Here we give it a secondaryContainer background and
 * render "×N" text inside it. The secondaryContainer/onSecondaryContainer
 * pair ensures readable contrast in all themes.
 *
 * TYPOGRAPHY SCALE
 * ================
 * Material 3 defines a type scale:
 *   bodyLarge (16sp)  — primary content text
 *   bodySmall (12sp)  — secondary/supporting text
 *   labelSmall (11sp) — very small labels
 * Using these semantic roles (rather than hardcoded sp values) keeps the
 * app consistent and respects the user's system font size preferences.
 */
@Composable
private fun NotificationGroupRow(group: NotificationGroup) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    // Compute the display label only when the relevant data changes.
    val timeLabel = remember(group.latestTimestamp, group.earliestTimestamp, group.count) {
        if (group.count == 1) {
            // Single notification: show full date+time.
            dateFmt.format(Date(group.latestTimestamp))
        } else {
            // Multiple: show the time range (date is implicit — same session).
            "${timeFmt.format(Date(group.earliestTimestamp))} – ${timeFmt.format(Date(group.latestTimestamp))}"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon loaded asynchronously (see AppIcon.kt).
        AppIcon(packageName = group.packageName)
        Spacer(modifier = Modifier.width(12.dp))
        // weight(1f) makes this Column fill remaining space, pushing the badge right.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.appLabel,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Sound label is optional; only shown when present.
            if (!group.soundLabel.isNullOrBlank()) {
                Text(
                    text = "♪ ${group.soundLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Count badge — only rendered when more than one notification in the group.
        if (group.count > 1) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = "×${group.count}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * ServiceEventRow — displays a service start/stop marker inline in the list.
 *
 * DESTRUCTURING DECLARATIONS
 * ==========================
 * `val (icon, label) = when (event.type) { ... }`
 *
 * This uses Kotlin's destructuring declaration with a Pair. The when expression
 * returns a Pair<String, String> (using the `to` infix function), and
 * destructuring binds the two elements to `icon` and `label` in one line.
 * It's more readable than declaring two separate variables.
 *
 * EARLY RETURN FROM A COMPOSABLE
 * ================================
 * `else -> return` exits the composable function early for the NOTIFICATION
 * entry type, which should never appear here (service events are filtered by
 * the ViewModel). Returning early from a composable is valid but must be done
 * before any other composable calls in the function — Compose's slot table
 * requires composable calls to be in a consistent order (the "rules of hooks"
 * apply to Compose just as they do to React hooks).
 *
 * Here it's safe because the entire body is just this `when` followed by a Row.
 *
 * ITALIC AND MUTED STYLING
 * ========================
 * Service events are secondary information — the user wants to know "was
 * monitoring running?", not these as primary content. The italic font style
 * and onSurfaceVariant color (a muted variant of the on-surface color) visually
 * de-emphasize these rows relative to the notification group rows above them.
 */
@Composable
private fun ServiceEventRow(event: ServiceEvent) {
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(event.timestamp) { timeFmt.format(Date(event.timestamp)) }
    val (icon, label) = when (event.type) {
        EntryType.SERVICE_CONNECTED -> "▶" to "Monitoring started"
        EntryType.SERVICE_DISCONNECTED -> "⏸" to "Monitoring stopped"
        else -> return  // NOTIFICATION entries never appear here
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label — $timeStr",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
