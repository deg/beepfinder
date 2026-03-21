package com.degel.beepfinder.data

import android.content.Context

/**
 * AppSettings — persistent key-value settings via SharedPreferences.
 *
 * SHAREDPREFERENCES
 * =================
 * SharedPreferences is Android's built-in key-value store for simple settings.
 * It persists a Map<String, Any> to an XML file in the app's private directory.
 *
 * Reads are synchronous (fine for settings, since the data is small and
 * always in memory after first access). Writes use .apply() which is
 * asynchronous (commits to memory immediately, writes to disk in the
 * background) vs .commit() which is synchronous.
 *
 * For more complex persistent state (large datasets, reactive updates,
 * type-safe schemas), the modern replacement is Jetpack DataStore. We use
 * SharedPreferences here because our settings are two small values and we
 * don't need a reactive stream for them — the ViewModel reads them once
 * and holds the values as Compose State.
 *
 * WHY NOT IN THE VIEWMODEL?
 * Settings persistence concerns belong in the data layer, not the UI layer.
 * The ViewModel reads from AppSettings and exposes the values as State —
 * it doesn't know about SharedPreferences itself.
 */
class AppSettings(context: Context) {

    /**
     * We use applicationContext to avoid leaking the Activity or Service
     * that called us. SharedPreferences files outlive any single component.
     */
    private val prefs = context.applicationContext
        .getSharedPreferences("beepfinder_settings", Context.MODE_PRIVATE)

    /**
     * The set of package names (e.g. "com.whatsapp") the user has chosen
     * to hide from the notification list.
     *
     * Stored as a StringSet in SharedPreferences. Note: SharedPreferences
     * StringSets are mutable and shared by reference internally — always
     * copy the result before modifying (see toggleIgnored below).
     */
    var ignoredPackages: Set<String>
        get() = prefs.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_IGNORED, value).apply()

    /**
     * How many hours of history to keep. Default is 24.
     * Valid values are the keys from HISTORY_OPTIONS.
     */
    var historyHours: Int
        get() = prefs.getInt(KEY_HISTORY_HOURS, 24)
        set(value) = prefs.edit().putInt(KEY_HISTORY_HOURS, value).apply()

    /**
     * Adds [packageName] to the ignored set if absent, removes it if present.
     *
     * SharedPreferences returns a reference to its internal set — mutating it
     * directly would corrupt the cache. .toMutableSet() creates a safe copy.
     */
    fun toggleIgnored(packageName: String) {
        val current = ignoredPackages.toMutableSet()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        ignoredPackages = current
    }

    companion object {
        private const val KEY_IGNORED = "ignored_packages"
        private const val KEY_HISTORY_HOURS = "history_hours"

        /**
         * The options shown in the Settings screen retention chip selector.
         * Pairs of (hours, display label). Kept here so the ViewModel and the
         * UI screen always reference the same set of valid values.
         */
        val HISTORY_OPTIONS = listOf(
            24 to "24 hours",
            72 to "3 days",
            168 to "7 days",
        )
    }
}
