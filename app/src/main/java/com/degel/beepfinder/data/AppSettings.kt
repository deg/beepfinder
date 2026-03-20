package com.degel.beepfinder.data

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("beepfinder_settings", Context.MODE_PRIVATE)

    var ignoredPackages: Set<String>
        get() = prefs.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_IGNORED, value).apply()

    var historyHours: Int
        get() = prefs.getInt(KEY_HISTORY_HOURS, 24)
        set(value) = prefs.edit().putInt(KEY_HISTORY_HOURS, value).apply()

    fun toggleIgnored(packageName: String) {
        val current = ignoredPackages.toMutableSet()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        ignoredPackages = current
    }

    companion object {
        private const val KEY_IGNORED = "ignored_packages"
        private const val KEY_HISTORY_HOURS = "history_hours"

        val HISTORY_OPTIONS = listOf(24 to "24 hours", 72 to "3 days", 168 to "7 days")
    }
}
