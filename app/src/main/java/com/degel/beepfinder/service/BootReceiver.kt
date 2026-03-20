package com.degel.beepfinder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // NotificationListenerService is started by the system automatically
        // when the notification listener permission is granted. No explicit
        // start is needed here — Android rebinds it after boot.
        // This receiver exists as a placeholder for any future boot-time
        // initialization (e.g., clearing stale state, scheduling jobs).
    }
}
