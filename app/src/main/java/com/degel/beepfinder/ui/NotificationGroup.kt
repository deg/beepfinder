package com.degel.beepfinder.ui

import com.degel.beepfinder.data.EntryType
import com.degel.beepfinder.data.NotificationEntity

/** A grouped run of NOTIFICATION entries from the same app. */
data class NotificationGroup(
    val appLabel: String,
    val packageName: String,
    val count: Int,
    val latestTimestamp: Long,
    val earliestTimestamp: Long,
)

/** A single service lifecycle event to display inline in the list. */
data class ServiceEvent(
    val type: EntryType,
    val timestamp: Long,
)

/** A list item — either a grouped notification run or a service event marker. */
sealed interface ListItem {
    data class Group(val group: NotificationGroup) : ListItem
    data class Event(val event: ServiceEvent) : ListItem
}

/**
 * Converts a flat list of DB entries (newest-first) into display items:
 * - Consecutive NOTIFICATION entries from the same app are merged into a Group.
 * - SERVICE_CONNECTED / SERVICE_DISCONNECTED entries become inline Event markers.
 */
fun List<NotificationEntity>.toListItems(): List<ListItem> {
    val items = mutableListOf<ListItem>()
    for (entity in this) {
        when (entity.entryType()) {
            EntryType.NOTIFICATION -> {
                val last = items.lastOrNull()
                if (last is ListItem.Group && last.group.packageName == entity.packageName) {
                    items[items.lastIndex] = ListItem.Group(
                        last.group.copy(
                            count = last.group.count + 1,
                            earliestTimestamp = entity.timestamp,
                        )
                    )
                } else {
                    items.add(
                        ListItem.Group(
                            NotificationGroup(
                                appLabel = entity.appLabel,
                                packageName = entity.packageName,
                                count = 1,
                                latestTimestamp = entity.timestamp,
                                earliestTimestamp = entity.timestamp,
                            )
                        )
                    )
                }
            }
            EntryType.SERVICE_CONNECTED,
            EntryType.SERVICE_DISCONNECTED -> {
                items.add(ListItem.Event(ServiceEvent(entity.entryType(), entity.timestamp)))
            }
        }
    }
    return items
}
