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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.degel.beepfinder.ui.NotificationListScreen

class MainActivity : ComponentActivity() {
    private val hasPermission = mutableStateOf(false)
    private val isBatteryExempt = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                BeepFinderApp(hasPermission.value, isBatteryExempt.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermission.value = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        val pm = getSystemService(PowerManager::class.java)
        isBatteryExempt.value = pm.isIgnoringBatteryOptimizations(packageName)
    }
}

@Composable
fun BeepFinderApp(hasPermission: Boolean, isBatteryExempt: Boolean) {
    val context = LocalContext.current
    if (hasPermission) {
        NotificationListScreen(isBatteryExempt = isBatteryExempt) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
    } else {
        PermissionPromptScreen {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}

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
