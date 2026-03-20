package com.degel.beepfinder.ui

import com.degel.beepfinder.data.NotificationEntity

data class NotificationGroup(
    val appLabel: String,
    val packageName: String,
    val count: Int,
    val latestTimestamp: Long,
    val earliestTimestamp: Long,
)

fun List<NotificationEntity>.toGroups(): List<NotificationGroup> {
    val groups = mutableListOf<NotificationGroup>()
    for (entity in this) { // list is newest-first
        val last = groups.lastOrNull()
        if (last != null && last.packageName == entity.packageName) {
            groups[groups.lastIndex] = last.copy(
                count = last.count + 1,
                earliestTimestamp = entity.timestamp, // progressively older
            )
        } else {
            groups.add(
                NotificationGroup(
                    appLabel = entity.appLabel,
                    packageName = entity.packageName,
                    count = 1,
                    latestTimestamp = entity.timestamp,
                    earliestTimestamp = entity.timestamp,
                )
            )
        }
    }
    return groups
}
