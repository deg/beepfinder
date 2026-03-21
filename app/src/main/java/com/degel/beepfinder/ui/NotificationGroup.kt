package com.degel.beepfinder.ui

import com.degel.beepfinder.data.EntryType
import com.degel.beepfinder.data.NotificationEntity

/**
 * NotificationGroup.kt — data model and transformation logic for the UI list.
 *
 * This file sits between the raw database rows (NotificationEntity) and the
 * Compose UI. It defines the display model and contains the grouping logic.
 *
 * SEPARATION OF CONCERNS
 * ======================
 * We deliberately keep this transformation logic OUT of:
 *
 *   - The DAO:       SQL should only filter and sort; business logic doesn't
 *                    belong in query annotations.
 *   - The ViewModel: The ViewModel orchestrates flows; pure transformation
 *                    logic is easier to test in a plain function.
 *   - The UI:        Composables should only read state and render it.
 *
 * A plain extension function on List<NotificationEntity> is the right home
 * for this logic — it's testable with no Android dependencies.
 */

/**
 * A group of consecutive notifications from the same app.
 *
 * When an app sends multiple notifications in a row (e.g., a flurry of
 * messages), we collapse them into one row showing the count and time range.
 * This makes the list far more readable after a busy period.
 */
data class NotificationGroup(
    val appLabel: String,
    val packageName: String,
    /** How many individual notifications are merged into this group. */
    val count: Int,
    /** Timestamp of the most recent notification in the group. */
    val latestTimestamp: Long,
    /** Timestamp of the oldest notification in the group. For count=1, equals latestTimestamp. */
    val earliestTimestamp: Long,
    /** Sound label from the most recent notification in the group. May be null. */
    val soundLabel: String? = null,
)

/**
 * A service lifecycle event to display inline in the list.
 *
 * Showing these in the same time-ordered stream as notifications lets the
 * user tell whether a quiet period was genuine (no beeps) or a monitoring
 * gap (service was stopped).
 */
data class ServiceEvent(
    val type: EntryType,
    val timestamp: Long,
)

/**
 * A sealed interface for the two kinds of items in the notification list.
 *
 * SEALED INTERFACES IN KOTLIN
 * ===========================
 * A sealed interface (or sealed class) restricts which classes can implement
 * it — only classes defined in the same package (in Kotlin 1.5+, same module).
 * This makes `when` expressions exhaustive: the compiler knows all possible
 * subtypes and requires you to handle all of them, eliminating the need for
 * an else branch.
 *
 * This is a common Android/Kotlin pattern for discriminated unions — similar
 * to TypeScript union types or algebraic data types in functional languages.
 */
sealed interface ListItem {
    /** A grouped run of same-app notifications. */
    data class Group(val group: NotificationGroup) : ListItem
    /** A service start or stop event. */
    data class Event(val event: ServiceEvent) : ListItem
}

/**
 * Converts a flat list of DB entries (newest-first) into a list of display
 * items, grouping consecutive NOTIFICATION entries from the same app and
 * converting SERVICE_* entries into inline event markers.
 *
 * GROUPING ALGORITHM
 * ==================
 * We iterate newest-first. For each NOTIFICATION entry, if the previous
 * item in the output list is a Group from the same app, we merge by
 * incrementing the count and updating the earliestTimestamp (which moves
 * further into the past as we descend through older entries).
 *
 * If the entry is from a different app, or the last item is an Event (a
 * service marker breaks the run), we start a new Group.
 *
 * This produces the natural reading: "WhatsApp ×5, 11:01–11:03" with a
 * ×N badge, rather than 5 separate identical rows.
 *
 * Note: data class .copy() creates a new instance with specified fields
 * changed and everything else the same. Because data classes are immutable,
 * we replace the list entry rather than mutating in place.
 */
fun List<NotificationEntity>.toListItems(): List<ListItem> {
    val items = mutableListOf<ListItem>()

    for (entity in this) {
        when (entity.entryType()) {
            EntryType.NOTIFICATION -> {
                val last = items.lastOrNull()
                if (last is ListItem.Group && last.group.packageName == entity.packageName) {
                    // Extend the existing group: increment count, push earliestTimestamp back.
                    items[items.lastIndex] = ListItem.Group(
                        last.group.copy(
                            count = last.group.count + 1,
                            earliestTimestamp = entity.timestamp,
                        )
                    )
                } else {
                    // Start a new group for this app.
                    items.add(
                        ListItem.Group(
                            NotificationGroup(
                                appLabel = entity.appLabel,
                                packageName = entity.packageName,
                                count = 1,
                                latestTimestamp = entity.timestamp,
                                earliestTimestamp = entity.timestamp,
                                soundLabel = entity.soundLabel,
                            )
                        )
                    )
                }
            }

            EntryType.SERVICE_CONNECTED,
            EntryType.SERVICE_DISCONNECTED -> {
                // Service events are never grouped — each is an independent marker.
                items.add(ListItem.Event(ServiceEvent(entity.entryType(), entity.timestamp)))
            }
        }
    }

    return items
}
