package com.womensafety.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    manualSosDelay: Int = 5,
    onManualSosDelayChange: (Int) -> Unit = {},
    iAmSafeEnabled: Boolean = true,
    onIAmSafeEnabledChange: (Boolean) -> Unit = {},
    onRecheckSetup: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var localDelay by remember { mutableStateOf(manualSosDelay) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("This will clear your session and all guardians. Are you sure?") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Alert Configuration", style = MaterialTheme.typography.headlineSmall,
                 color = MaterialTheme.colorScheme.onBackground,
                 modifier = Modifier.padding(top = 8.dp))

            // SOS Delay card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SOS Alert Delay", style = MaterialTheme.typography.titleMedium)
                        Text("Countdown before sending SOS (0 = instant)",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Slider(
                            value = localDelay.toFloat(),
                            onValueChange = { v ->
                                localDelay = v.toInt()
                                onManualSosDelayChange(v.toInt())
                            },
                            valueRange = 0f..60f,
                            steps = 59,
                            modifier = Modifier.weight(1f)
                        )
                        Card(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("${localDelay}s", style = MaterialTheme.typography.titleMedium,
                                     color = MaterialTheme.colorScheme.onPrimaryContainer,
                                     fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(if (localDelay == 0) "SOS will be sent instantly"
                         else "SOS will be sent after $localDelay second${if (localDelay != 1) "s" else ""}",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // I am safe auto-reply toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Auto \"I am safe\" reply", style = MaterialTheme.typography.titleSmall)
                        Text("Send automatic safe message when a guardian calls",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = iAmSafeEnabled,
                        onCheckedChange = onIAmSafeEnabledChange
                    )
                }
            }

            // How SOS works
            Text("How SOS Works", style = MaterialTheme.typography.headlineSmall,
                 color = MaterialTheme.colorScheme.onBackground,
                 modifier = Modifier.padding(top = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HowItWorksItem("1", "Press SOS Button", "A countdown appears (if delay > 0)")
                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    HowItWorksItem("2", "Cancel if needed", "Tap the countdown to abort")
                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    HowItWorksItem("3", "Guardians notified", "SMS sent to all guardians with location")
                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    HowItWorksItem("4", "Say you're safe", "Tap 'I'm safe' to stop follow-up messages")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Re-check setup button
            OutlinedButton(
                onClick = onRecheckSetup,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-check Setup & Permissions", style = MaterialTheme.typography.labelLarge)
            }

            // Logout button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HowItWorksItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.titleSmall,
                 color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                 color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(description, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}
