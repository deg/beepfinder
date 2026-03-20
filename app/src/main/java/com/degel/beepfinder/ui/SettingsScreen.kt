package com.degel.beepfinder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.degel.beepfinder.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: NotificationViewModel = viewModel(),
) {
    val knownApps by vm.knownApps.collectAsStateWithLifecycle(initialValue = emptyList())
    val ignoredPackages = vm.ignoredPackages
    val historyHours = vm.historyHours

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // History retention section
            item {
                SectionHeader("History Retention")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.HISTORY_OPTIONS.forEach { (hours, label) ->
                        FilterChip(
                            selected = historyHours == hours,
                            onClick = { vm.updateHistoryHours(hours) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Ignored apps section
            item {
                SectionHeader("Ignored Apps")
            }
            if (knownApps.isEmpty()) {
                item {
                    Text(
                        "No apps seen yet — notifications will appear here once BeepFinder records some.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(knownApps) { app ->
                    IgnoreAppRow(
                        app = app,
                        ignored = app.packageName in ignoredPackages,
                        onToggle = { vm.toggleIgnored(app.packageName) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun IgnoreAppRow(app: KnownApp, ignored: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.appLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = ignored,
            onCheckedChange = { onToggle() },
        )
    }
}
