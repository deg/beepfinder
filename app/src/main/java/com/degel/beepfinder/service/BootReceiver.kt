package com.degel.beepfinder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver — handles the BOOT_COMPLETED system broadcast.
 *
 * BROADCASTRECEIVER
 * =================
 * A BroadcastReceiver is an Android component that handles system-wide or
 * app-wide one-shot events. The OS delivers a broadcast by creating an instance
 * of the receiver, calling onReceive(), and then discarding the instance.
 *
 * onReceive() runs on the main thread and must complete quickly (under ~10
 * seconds, though you should aim for milliseconds). If you need to do
 * significant work, launch a coroutine, start a Service, or use WorkManager.
 *
 * BOOT_COMPLETED
 * ==============
 * Android sends ACTION_BOOT_COMPLETED after the device finishes booting. Apps
 * that need to restart background services after a reboot use this broadcast.
 *
 * WHY IS THIS A STUB?
 * ===================
 * NotificationListenerService is special: the OS automatically restarts it
 * after a reboot if the user has granted Notification Access permission. We
 * don't need to start it manually in onReceive().
 *
 * The receiver is registered in the manifest (which is required to receive
 * BOOT_COMPLETED) and exists as a documented extension point for future
 * boot-time initialization. If we later add a WorkManager job or need to
 * clear stale state after boot, this is where it would go.
 *
 * NOTE: Without the RECEIVE_BOOT_COMPLETED permission in the manifest,
 * Android will simply not deliver this broadcast to us.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No action needed: NotificationListenerService is automatically
        // rebound by Android after boot when notification access is granted.
    }
}
