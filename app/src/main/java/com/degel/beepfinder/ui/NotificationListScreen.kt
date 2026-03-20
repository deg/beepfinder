package com.degel.beepfinder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.degel.beepfinder.data.EntryType
import com.degel.beepfinder.service.BeepListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    vm: NotificationViewModel = viewModel(),
    isBatteryExempt: Boolean = true,
    onRequestBatteryExemption: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val listItems by vm.listItems.collectAsStateWithLifecycle(initialValue = emptyList())
    var batteryBannerDismissed by remember { mutableStateOf(false) }
    val serviceConnected = BeepListenerService.isConnected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BeepFinder") },
                actions = {
                    ServiceStatusDot(serviceConnected)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isBatteryExempt && !batteryBannerDismissed) {
                BatteryWarningBanner(
                    onFix = onRequestBatteryExemption,
                    onDismiss = { batteryBannerDismissed = true },
                )
            }
            if (listItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No audible notifications in the last 24 hours")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(listItems) { item ->
                        when (item) {
                            is ListItem.Group -> {
                                NotificationGroupRow(item.group)
                                HorizontalDivider()
                            }
                            is ListItem.Event -> {
                                ServiceEventRow(item.event)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusDot(connected: Boolean) {
    val color = if (connected) Color(0xFF4CAF50) else Color(0xFFF44336)
    val label = if (connected) "Listening" else "Stopped"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(end = 0.dp),
        ) {
            // Colored dot
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun BatteryWarningBanner(onFix: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Battery optimization is on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "BeepFinder may stop recording when the screen is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onFix) {
                Text("Fix", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun NotificationGroupRow(group: NotificationGroup) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    val timeLabel = remember(group.latestTimestamp, group.earliestTimestamp, group.count) {
        if (group.count == 1) {
            dateFmt.format(Date(group.latestTimestamp))
        } else {
            "${timeFmt.format(Date(group.earliestTimestamp))} – ${timeFmt.format(Date(group.latestTimestamp))}"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = group.packageName)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.appLabel,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.count > 1) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = "×${group.count}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ServiceEventRow(event: ServiceEvent) {
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(event.timestamp) { timeFmt.format(Date(event.timestamp)) }
    val (icon, label) = when (event.type) {
        EntryType.SERVICE_CONNECTED -> "▶" to "Monitoring started"
        EntryType.SERVICE_DISCONNECTED -> "⏸" to "Monitoring stopped"
        else -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label — $timeStr",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
