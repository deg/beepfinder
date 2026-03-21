package com.degel.beepfinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppIcon.kt — asynchronously loads and displays an app's launcher icon.
 *
 * THE CHALLENGE: ASYNC WORK IN COMPOSABLES
 * ==========================================
 * Composables must run on the main (UI) thread and must complete quickly.
 * Loading an app icon via PackageManager involves I/O (reading the APK's
 * resources), which can block the main thread if done synchronously.
 *
 * Compose provides produceState() for exactly this use case: it lets you
 * launch a coroutine from inside a composable, populate a State value
 * asynchronously, and have Compose recompose when the value arrives.
 *
 * produceState<T>(initialValue, key) { ... }
 * ==========================================
 * - initialValue: the value to use while the async work is in progress.
 *   Here null means "nothing to show yet" — the Box renders empty initially.
 *
 * - key1 = packageName: the key(s) that trigger re-execution of the block.
 *   If packageName changes (a different app's icon is requested), Compose
 *   cancels the current coroutine and starts a new one with the new package.
 *   Without a key, the block would run only once and never update.
 *
 * - The block body is a coroutine (CoroutineScope). `value = ...` sets the
 *   produced state and triggers recomposition. If the coroutine is cancelled
 *   (because the composable left the composition or the key changed), the
 *   assignment is simply not reached — no cleanup needed.
 *
 * withContext(Dispatchers.IO) { ... }
 * ====================================
 * Switches execution to the IO thread pool for the PackageManager call and
 * the bitmap conversion. Returns to the calling coroutine's dispatcher after
 * the block. This is the standard Kotlin coroutines pattern for performing
 * blocking work without blocking the calling thread.
 *
 * PACKAGING THE RESULT: THE `by` DELEGATE
 * ========================================
 * `val bitmap by produceState<Bitmap?>(...)` uses Kotlin's property delegation.
 * produceState returns a State<Bitmap?>; the `by` delegate unwraps it so we
 * can write `bitmap` instead of `bitmap.value` in the composable body. Compose
 * also tracks the read, so it recomposes when the value changes.
 *
 * CONDITIONAL RENDERING
 * =====================
 * `bitmap?.let { ... }` only renders the Image composable when bitmap is non-null.
 * When null (still loading or failed), the Box renders as an empty 40dp square,
 * keeping the row layout stable so it doesn't shift when the icon loads.
 *
 * asImageBitmap()
 * ===============
 * Compose's Image composable requires an ImageBitmap (Compose's bitmap type),
 * not Android's android.graphics.Bitmap. The asImageBitmap() extension function
 * converts between the two. It's a zero-copy wrapper — no pixel data is copied.
 *
 * contentDescription = null
 * =========================
 * The icon is purely decorative — the app name in the adjacent Text provides
 * the accessible label. Passing null suppresses the "missing content description"
 * accessibility warning for Images that intentionally have no description.
 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmapOrNull()
            } catch (e: Exception) {
                // App may have been uninstalled since the notification was recorded.
                // Return null — the row will render without an icon.
                null
            }
        }
    }

    Box(modifier = modifier.size(40.dp)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * Converts a Drawable to a Bitmap, or returns null if conversion fails.
 *
 * DRAWABLE vs BITMAP
 * ==================
 * Android's PackageManager returns app icons as Drawable objects.
 * Drawable is an abstraction over many concrete types:
 *   - BitmapDrawable: wraps a Bitmap (most common for app icons)
 *   - VectorDrawable / AdaptiveIconDrawable: vector or layered drawables
 *     that require explicit rasterization to a Bitmap
 *
 * FAST PATH: BitmapDrawable
 * =========================
 * If the Drawable is already a BitmapDrawable with a non-null underlying
 * Bitmap, we return it directly without any pixel copying. This is the
 * common case for legacy app icons and saves a Canvas allocation.
 *
 * SLOW PATH: Rasterization via Canvas
 * =====================================
 * For VectorDrawable, AdaptiveIconDrawable, and other non-bitmap types,
 * we must rasterize to a Bitmap manually:
 *   1. Create an empty Bitmap with ARGB_8888 config (4 bytes per pixel,
 *      with full alpha channel support).
 *   2. Wrap it in an android.graphics.Canvas (note: this is Android's
 *      Canvas, not Compose's Canvas — the import is android.graphics.Canvas).
 *   3. Set the drawable's bounds to fill the canvas.
 *   4. Call draw(canvas) to render the Drawable onto the Canvas, which
 *      writes pixels into the underlying Bitmap.
 *
 * intrinsicWidth/intrinsicHeight
 * ================================
 * Drawable.intrinsicWidth returns the drawable's preferred size, or -1 if
 * it has no intrinsic size (e.g., a solid color). We fall back to 48px
 * (a reasonable icon size in px at medium density) if the intrinsic size
 * is unavailable.
 *
 * This is a private extension function (private fun Drawable.toBitmapOrNull)
 * scoped to this file. Extension functions are a Kotlin feature that lets
 * you add methods to existing classes without subclassing them.
 */
private fun Drawable.toBitmapOrNull(): Bitmap? = try {
    if (this is BitmapDrawable && this.bitmap != null) {
        // Fast path: already a Bitmap.
        this.bitmap
    } else {
        // Slow path: rasterize to a new Bitmap.
        val w = if (intrinsicWidth > 0) intrinsicWidth else 48
        val h = if (intrinsicHeight > 0) intrinsicHeight else 48
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    }
} catch (e: Exception) {
    null
}
