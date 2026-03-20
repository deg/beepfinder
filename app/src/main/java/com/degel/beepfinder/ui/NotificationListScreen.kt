package com.degel.beepfinder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(vm: NotificationViewModel = viewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BeepFinder") })
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No audible notifications in the last 24 hours")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(groups) { group ->
                    NotificationGroupRow(group)
                    HorizontalDivider()
                }
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
            // Show time range when multiple alerts were grouped
            "${timeFmt.format(Date(group.earliestTimestamp))} – ${timeFmt.format(Date(group.latestTimestamp))}"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
