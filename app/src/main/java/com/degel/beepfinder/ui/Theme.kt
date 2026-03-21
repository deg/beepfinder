package com.degel.beepfinder.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * BeepFinderTheme — Material You theming for the app.
 *
 * MATERIAL 3 AND MATERIAL YOU
 * ============================
 * Material 3 is Google's design system for Android (successor to Material 2).
 * MaterialTheme provides the color scheme, typography, and shape tokens that
 * all Material 3 components (Button, Card, TopAppBar, etc.) read from.
 *
 * "Material You" is Android 12's personalization system: the OS extracts a
 * color palette from the user's wallpaper and makes it available as "dynamic
 * colors". Apps that use dynamicLightColorScheme/dynamicDarkColorScheme
 * automatically match the user's chosen aesthetic.
 *
 * DARK MODE
 * =========
 * isSystemInDarkTheme() reads the system's current dark/light mode setting.
 * Compose automatically recomposes when this changes (e.g., when the user
 * enables dark mode while the app is open).
 *
 * FALLBACK FOR OLDER ANDROID
 * ==========================
 * Dynamic colors require Android 12 (API 31). On older versions, we fall back
 * to a static Material 3 color scheme (darkColorScheme() / lightColorScheme()),
 * which uses the Material 3 defaults (blue/purple tones).
 *
 * HOW THEMING PROPAGATES
 * ======================
 * BeepFinderTheme wraps the entire app at the top of the composition tree
 * (in MainActivity's setContent block). All Composables below it can read
 * MaterialTheme.colorScheme, MaterialTheme.typography, etc. via
 * CompositionLocal — a Compose mechanism for implicitly passing values
 * down the tree without explicit parameters (analogous to React Context).
 */
@Composable
fun BeepFinderTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when {
        // Dynamic color: Android 12+ only (Build.VERSION_CODES.S = API 31).
        // The OS extracts a seed color from the wallpaper and generates a
        // full Material 3 color palette from it.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Static fallback: standard Material 3 dark palette.
        darkTheme -> darkColorScheme()
        // Static fallback: standard Material 3 light palette.
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
