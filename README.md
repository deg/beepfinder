# BeepFinder — Annotated Android Tutorial

BeepFinder logs the source of every audible notification on your phone, so you
can answer "what just beeped?" It was built as a personal utility and
deliberately kept small, making it a good end-to-end example of a modern
Android app.

This README is written for an experienced developer who is **new to Android**.
It explains the Android-specific concepts in context rather than abstractly.

---

## What the app does

1. A background **service** listens for every notification posted by any app.
2. Each audible notification is recorded to a **local SQLite database**.
3. The main screen shows a live-updating **list** of recent notifications,
   grouped by app, newest first.
4. A settings screen lets you ignore specific apps and choose how much history
   to keep.

---

## Project structure

```
app/
  src/main/
    AndroidManifest.xml          ← App registration: activities, services,
    │                              permissions. The equivalent of a web app's
    │                              server config + routing table.
    │
    java/com/degel/beepfinder/
    │
    ├── MainActivity.kt          ← The single screen entry point (Activity).
    │                              Owns top-level state (permission, battery).
    │
    ├── data/                    ← Persistence layer (Room ORM over SQLite).
    │   ├── NotificationEntity   ← The DB row (annotated data class).
    │   ├── NotificationDao      ← The query interface (annotated interface).
    │   ├── NotificationDatabase ← The DB singleton (Room builder).
    │   ├── NotificationRepository ← The only data-access point for the UI.
    │   └── AppSettings          ← Simple key-value settings (SharedPreferences).
    │
    ├── service/
    │   ├── BeepListenerService  ← The core: NotificationListenerService subclass.
    │   └── BootReceiver         ← BroadcastReceiver stub for device boot.
    │
    └── ui/
        ├── Theme.kt             ← Material You color scheme (dark/light).
        ├── NotificationViewModel← Bridge between data layer and UI (ViewModel).
        ├── NotificationGroup    ← Data class + grouping logic for list display.
        ├── NotificationListScreen ← Main list (Jetpack Compose).
        ├── SettingsScreen       ← Settings (Jetpack Compose).
        └── AppIcon.kt           ← Async app icon loader (Compose + coroutine).

gradle/
  libs.versions.toml             ← Central version catalog (all dep versions here).
app/build.gradle.kts             ← Module-level build config.
build.gradle.kts                 ← Root build config (plugin declarations only).
gradle.properties                ← JVM args, AndroidX flag.
AndroidManifest.xml              ← See above.
```

---

## Key Android concepts used

### 1. The Android Manifest (`AndroidManifest.xml`)

Every Android app has a manifest that declares its components and permissions
to the OS. Think of it as a contract between your app and Android.

- **`<activity>`** — A screen. Android launches your app by starting an Activity,
  not a `main()` function.
- **`<service>`** — A component that runs without a UI. Our
  `NotificationListenerService` is declared here.
- **`<receiver>`** — A component that receives system-wide broadcast events
  (e.g., `BOOT_COMPLETED`).
- **`<uses-permission>`** — Declares permissions your app needs. Some are granted
  automatically; others require explicit user approval at runtime.

### 2. Activity lifecycle

An `Activity` (screen) has a lifecycle managed by Android:
`onCreate → onStart → onResume ↔ onPause → onStop → onDestroy`

We use `onResume` to re-check permissions each time the user returns to the app
(e.g., after visiting the system Settings screen). This is the standard pattern
for reacting to out-of-app changes.

### 3. `NotificationListenerService`

A special `Service` subclass that Android binds to when you grant "Notification
Access" permission. The OS calls `onNotificationPosted()` for every notification
posted by any app, giving you the package name, channel, and metadata.

Key points:
- The OS starts and stops it automatically — you don't call `startService()`.
- It runs in your app's process, so it shares memory with the UI.
- We call `startForeground()` inside it to raise its process priority and
  prevent Android from killing it under memory pressure.
- The permission is "sensitive" — Play Store requires justification.

### 4. Foreground services

Android aggressively kills background processes to save battery. A **foreground
service** opts out of this by posting a persistent visible notification, which
tells Android "this process is doing something the user is aware of."

Our service calls `startForeground()` in `onListenerConnected()` and updates
the notification text on every beep — this doubles as the quick-glance feature.

### 5. Room (ORM over SQLite)

Room is Android's official ORM. It wraps SQLite with type safety and
compile-time query validation. Three pieces:

- **`@Entity`** — A data class annotated to map to a DB table.
- **`@Dao`** (Data Access Object) — An interface with annotated methods
  (`@Insert`, `@Query`). Room generates the implementation at compile time.
- **`@Database`** — A `RoomDatabase` subclass that ties entities and DAOs
  together. Always used as a singleton.

Room integrates with Kotlin `Flow` natively: a `@Query` that returns
`Flow<List<T>>` automatically re-emits whenever the underlying table changes.
This is the reactive data pipeline that keeps the UI live.

### 6. Jetpack Compose

Compose is Android's modern declarative UI toolkit (similar to React or
SwiftUI). Key ideas:

- UI is defined as `@Composable` functions, not XML layouts.
- State drives recomposition: when a `State<T>` or `StateFlow` changes,
  Compose re-runs only the composables that read it.
- `remember` caches a value for the lifetime of a composable's presence in the
  tree. `rememberUpdatedState` / `LaunchedEffect` / `produceState` handle
  side effects and async work.
- `LazyColumn` is the Compose equivalent of `RecyclerView` — it only renders
  visible items.

### 7. ViewModel

`ViewModel` survives screen rotations (it outlives the Activity). It holds and
transforms the data the UI needs, without knowing about the UI itself.

Our `NotificationViewModel`:
- Holds `AppSettings` state (ignored apps, history hours) as Compose `State`.
- Chains Room's `Flow` through `flatMapLatest` (switch to new query when
  history window changes) and `map` (apply ignore filter, group entries).
- Exposes the result as a `Flow<List<ListItem>>` that the UI collects.

### 8. Kotlin Flow and coroutines

Flow is Kotlin's reactive stream type (similar to RxJava Observable or a
JS async generator). Key operators used here:

- `flatMapLatest` — when the source emits, cancel the previous inner flow and
  start a new one. Used to switch DB queries when the history window changes.
- `map` — transform each emitted value. Used for filtering and grouping.
- `collectAsStateWithLifecycle` — Compose extension that collects a Flow as
  Compose `State`, automatically pausing collection when the app is in the
  background (lifecycle-aware).
- `produceState` — launches a coroutine inside a composable and stores its
  result as `State`. Used in `AppIcon` to load icons off the main thread.

### 9. SharedPreferences

The Android key-value store for simple settings (analogous to `localStorage`
in a browser, or a small `.ini` file). Synchronous reads, async writes via
`edit().apply()`. For more complex persistent state, DataStore is the modern
successor.

### 10. Adaptive icons

Android 8+ supports **adaptive icons**: a foreground layer (the artwork) on a
background layer (a solid color or pattern). The launcher clips them into
whatever shape it prefers (circle, squircle, teardrop, etc.).

Our icon files:
- `res/drawable/ic_launcher_foreground.xml` — the magnifying glass + bell.
- `res/drawable/ic_launcher_background.xml` — solid blue rectangle.
- `res/mipmap-anydpi-v26/ic_launcher.xml` — the `<adaptive-icon>` wrapper.

The `mipmap-anydpi-v26/` directory name means "any DPI, API 26+". API 26 is
when adaptive icons were introduced.

### 11. The Gradle build system

Android uses Gradle with the **Android Gradle Plugin (AGP)**. Key files:

- `gradle/libs.versions.toml` — the **version catalog**: all library versions
  in one place, referenced by alias throughout the project.
- `settings.gradle.kts` — declares which modules exist and where to find
  dependencies.
- `build.gradle.kts` (root) — applies plugins across all modules.
- `app/build.gradle.kts` — the actual app module config: SDK versions,
  dependencies, build types.

`compileSdk` is the SDK version the code compiles against (determines which
APIs are available). `targetSdk` tells Android how to treat your app at runtime
(newer = stricter behavior). `minSdk` is the minimum Android version you support.

---

## Data flow

```
  Device notification arrives
         │
         ▼
  BeepListenerService.onNotificationPosted()
    • Skip if FLAG_GROUP_SUMMARY (synthetic rollup)
    • Skip if channel importance < DEFAULT (silent)
    • Skip if same (pkg, id) within 3 seconds (dedup)
    • Resolve app label from PackageManager
    • Resolve sound name from NotificationChannel
    • Update foreground notification text
         │
         ▼ (Dispatchers.IO coroutine)
  NotificationRepository.record()
         │
         ▼
  Room: INSERT INTO notifications ...
         │  (Room emits to all active Flow collectors)
         ▼
  NotificationViewModel.listItems (Flow)
    • flatMapLatest on historyHours → new DB query
    • map: toListItems() — group consecutive same-app entries,
      interleave service start/stop markers
    • map: filter ignored packages
         │
         ▼ (collectAsStateWithLifecycle in Compose)
  NotificationListScreen recomposes
    • LazyColumn renders updated list
```

---

## Building and running

```bash
# First time only:
brew install --cask android-studio
brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Build debug APK:
./gradlew assembleDebug

# Install on connected device (USB debugging must be enabled):
./gradlew installDebug
```

On first launch, the app shows a permission prompt. Go to
**Settings → Apps → Special app access → Notification access**
and enable BeepFinder. Also tap "Fix" on the battery optimization banner.

---

## What to read and in what order

If you want to trace the full lifecycle from boot to screen:

1. `AndroidManifest.xml` — understand what's registered
2. `data/NotificationEntity.kt` — the DB schema
3. `data/NotificationDao.kt` — the query interface
4. `data/NotificationDatabase.kt` — Room setup and migrations
5. `data/NotificationRepository.kt` — the data access facade
6. `service/BeepListenerService.kt` — where data enters the system
7. `ui/NotificationGroup.kt` — how raw DB rows become display items
8. `ui/NotificationViewModel.kt` — the reactive pipeline
9. `ui/Theme.kt` — Material You theming
10. `MainActivity.kt` — the Activity and permission flow
11. `ui/NotificationListScreen.kt` — the Compose UI
12. `ui/SettingsScreen.kt` — a second Compose screen
13. `ui/AppIcon.kt` — async work inside a composable
14. `gradle/libs.versions.toml` + `app/build.gradle.kts` — the build system
