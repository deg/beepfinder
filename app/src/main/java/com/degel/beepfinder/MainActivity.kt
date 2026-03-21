package com.degel.beepfinder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.degel.beepfinder.ui.BeepFinderTheme
import com.degel.beepfinder.ui.NotificationListScreen
import com.degel.beepfinder.ui.SettingsScreen

/**
 * MainActivity — the single Activity that hosts the entire Compose UI.
 *
 * ACTIVITY
 * ========
 * An Activity is the Android entry point for user interaction. It represents
 * one "screen" in the traditional sense, though with Compose we often host
 * multiple logical screens inside one Activity via state-driven navigation
 * (see BeepFinderApp below).
 *
 * Modern Android apps typically use a single Activity ("single-activity
 * architecture") and handle all navigation within Compose or the Navigation
 * component. This app follows that pattern.
 *
 * ComponentActivity is the base class recommended for Compose apps. It provides
 * setContent {} (the entry point for Compose) and lifecycle-aware behavior
 * via the Jetpack Lifecycle library.
 *
 * ACTIVITY LIFECYCLE
 * ==================
 * Android manages a well-defined lifecycle for activities:
 *
 *   onCreate()  — Activity is first created. Set up the UI here (call setContent).
 *                 savedInstanceState carries state from the previous run if the
 *                 system killed and recreated the Activity.
 *
 *   onResume()  — Activity is visible and in the foreground. Called after onCreate
 *                 and every time the user returns to the app (e.g., after visiting
 *                 system Settings to grant a permission). This is the right place
 *                 to refresh state that may have changed while away.
 *
 *   onPause()   — Activity is partially obscured or losing focus.
 *   onStop()    — Activity is no longer visible.
 *   onDestroy() — Activity is being destroyed (back pressed, or system reclaim).
 *
 * We override only onCreate and onResume; all other lifecycle transitions are
 * handled automatically by ComponentActivity.
 *
 * PERMISSION STATE AND WHY IT'S ON THE ACTIVITY
 * ==============================================
 * Notification Access is a special permission the user grants via the system
 * Settings UI (not the standard runtime-permission dialog). After the user
 * grants it and returns, we need to recheck it.
 *
 * We use mutableStateOf on the Activity (not in a ViewModel) for two reasons:
 *
 *   1. The check requires a Context — the Activity IS a context, so it can
 *      call NotificationManagerCompat.getEnabledListenerPackages() directly.
 *
 *   2. The check must run in onResume(), which is an Activity lifecycle callback.
 *      A ViewModel doesn't have onResume() — it has no lifecycle callbacks at all.
 *
 * Using Compose's mutableStateOf here is the key insight: when we write
 * `hasPermission.value = true` in onResume(), Compose sees the state change
 * and recomposes BeepFinderApp with the new value. This is the bridge between
 * the imperative Android lifecycle and the declarative Compose UI.
 *
 * Note: we pass `.value` (the current Boolean) into BeepFinderApp as a plain
 * parameter — this means the composable recomposes whenever the Activity's
 * state changes, because Compose tracks reads of mutableStateOf values.
 *
 * EDGE-TO-EDGE
 * ============
 * enableEdgeToEdge() tells Android that our app wants to draw behind the
 * system bars (status bar at top, navigation bar at bottom). Compose's
 * Scaffold handles the insets automatically via WindowInsets, so our content
 * avoids the cutout areas without extra padding code.
 */
class MainActivity : ComponentActivity() {
    // Compose-aware state for permission and battery status.
    // Declared as Activity fields (not inside setContent) so onResume()
    // can write to them and trigger recomposition.
    private val hasPermission = mutableStateOf(false)
    private val isBatteryExempt = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // BeepFinderTheme wraps the entire composition tree, providing
            // Material 3 color tokens to all descendant composables.
            BeepFinderTheme {
                BeepFinderApp(hasPermission.value, isBatteryExempt.value)
            }
        }
    }

    /**
     * Called every time this Activity comes to the foreground.
     *
     * We re-check both permissions here because:
     *   - The user may have just returned from system Settings having granted
     *     Notification Access — we need to detect that and drop the prompt screen.
     *   - The battery exemption could have been revoked externally (rare).
     *
     * Writing to mutableStateOf triggers Compose recomposition automatically.
     */
    override fun onResume() {
        super.onResume()
        // NotificationManagerCompat.getEnabledListenerPackages() returns the set
        // of package names that currently hold Notification Access permission.
        // We check if our package is in that set.
        hasPermission.value = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        // PowerManager.isIgnoringBatteryOptimizations() returns true if the
        // system will not apply Doze-mode restrictions to our app.
        val pm = getSystemService(PowerManager::class.java)
        isBatteryExempt.value = pm.isIgnoringBatteryOptimizations(packageName)
    }
}

/**
 * BeepFinderApp — the root composable that manages top-level navigation.
 *
 * NAVIGATION APPROACH
 * ===================
 * This app uses simple boolean state for navigation rather than the full
 * Jetpack Navigation component. With only two screens (main list and settings),
 * a boolean `showSettings` is simpler and more readable than a NavHost/NavGraph.
 *
 * The navigation model is:
 *   - If no Notification Access permission → show PermissionPromptScreen
 *   - If showSettings is true → show SettingsScreen
 *   - Otherwise → show NotificationListScreen (the main screen)
 *
 * NAVIGATION AS STATE
 * ===================
 * In Compose, navigation is just state. `var showSettings by remember { ... }`
 * is a Boolean that Compose tracks. When it changes, the reading composable
 * recomposes and shows a different screen. No fragment transactions, no
 * back stack manager — just a state flip.
 *
 * The `remember { mutableStateOf(false) }` pattern:
 *   - mutableStateOf(false): creates a Compose State object wrapping false
 *   - remember { ... }: keeps this State object alive across recompositions
 *     (without remember, every recomposition would reset it to false)
 *   - by delegation (Kotlin): lets us write `showSettings` instead of
 *     `showSettings.value` on every read/write
 *
 * LAMBDA CALLBACKS FOR NAVIGATION
 * ================================
 * Composables communicate upward via lambdas ("events up" in Compose
 * unidirectional data flow). NotificationListScreen doesn't know about
 * showSettings — it just calls onOpenSettings() when the user taps the gear
 * icon. BeepFinderApp responds by setting showSettings = true. This keeps
 * child composables reusable and ignorant of their navigation context.
 *
 * LOCATING CONTEXT
 * ================
 * LocalContext.current provides the nearest Android Context (here the Activity)
 * via CompositionLocal — Compose's mechanism for implicitly passing values down
 * the tree. We use it to start Activities (for the deep-link to system Settings).
 *
 * DEEP LINKS TO SYSTEM SETTINGS
 * ==============================
 * Android provides standard Intent actions for jumping to specific system
 * settings screens:
 *
 *   Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
 *     → Opens the "Notification Access" special-permissions screen.
 *
 *   Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 *     → Opens the battery optimization exemption dialog for a specific app.
 *     The app's package must be passed as a URI: "package:com.example.app"
 *
 * Both start a new Activity on top of ours. When the user presses Back,
 * our Activity resumes and onResume() rechecks both permissions.
 */
@Composable
fun BeepFinderApp(hasPermission: Boolean, isBatteryExempt: Boolean) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // Guard: if the user hasn't granted Notification Access, show the prompt.
    // `return` inside a Composable exits the function early, so nothing below
    // this block is composed when the permission is missing.
    if (!hasPermission) {
        PermissionPromptScreen {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        return
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        NotificationListScreen(
            isBatteryExempt = isBatteryExempt,
            onRequestBatteryExemption = {
                // The ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent requires
                // the target app's package as a "package:" URI.
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            },
            onOpenSettings = { showSettings = true },
        )
    }
}

/**
 * PermissionPromptScreen — shown when Notification Access has not been granted.
 *
 * SCAFFOLD
 * ========
 * Scaffold is Material 3's standard layout structure. It manages the
 * relationship between a top app bar, bottom bar, FAB, and the main content
 * area. Even when we don't use a top bar (as here), Scaffold applies the
 * correct edge-to-edge insets via the `padding` parameter it passes to the
 * content lambda.
 *
 * Always apply the Scaffold padding to your top-level content modifier
 * (.padding(padding)) to avoid content appearing behind system bars.
 *
 * WHY A SEPARATE COMPOSABLE
 * ==========================
 * Extracting PermissionPromptScreen into its own function (rather than
 * inlining in BeepFinderApp) keeps BeepFinderApp readable and makes the
 * permission screen independently testable and previewable.
 *
 * THE LAMBDA PARAMETER
 * ====================
 * onOpenSettings: () -> Unit is a lambda parameter — a function with no
 * arguments and no return value. The caller (BeepFinderApp) provides the
 * implementation; this composable just calls it when the button is tapped.
 * This is Compose's "events up" pattern.
 */
@Composable
fun PermissionPromptScreen(onOpenSettings: () -> Unit) {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BeepFinder needs Notification Access",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This lets BeepFinder record which apps trigger notification sounds, so you can see what's beeping.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onOpenSettings) {
                    Text("Open Notification Access Settings")
                }
            }
        }
    }
}
