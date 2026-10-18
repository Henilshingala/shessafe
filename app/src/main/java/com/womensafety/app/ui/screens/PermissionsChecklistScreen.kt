package com.womensafety.app.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val isCritical: Boolean,
    val isGranted: Boolean,
    val actionType: ActionType
)

enum class ActionType {
    RUNTIME_SMS,
    RUNTIME_PHONE,
    RUNTIME_LOCATION_FOREGROUND,
    RUNTIME_LOCATION_BACKGROUND,
    RUNTIME_NOTIFICATIONS,
    INTENT_FULL_SCREEN,
    INTENT_BATTERY,
    INTENT_DND,
    APP_SETTINGS
}

@Composable
fun PermissionsChecklistScreen(
    onAllCriticalGranted: () -> Unit,
    onOpenOemSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // State triggers recomposition on resume
    var refreshKey by remember { mutableStateOf(0) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission Launchers
    val requestMultipleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    val requestSingleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    // Dialog state for background location explanation
    var showBgLocationDialog by remember { mutableStateOf(false) }

    // Evaluated checklist items
    val items = remember(refreshKey) {
        getChecklistItems(context)
    }

    val allCriticalGranted = items.filter { it.isCritical }.all { it.isGranted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Protection Checklist",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Nirbhaya needs these permissions to send emergency SOS alerts, locate you offline, and alert you even when silenced.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        items.forEach { item ->
            PermissionItemCard(
                item = item,
                onRequest = {
                    when (item.actionType) {
                        ActionType.RUNTIME_SMS -> {
                            requestMultipleLauncher.launch(
                                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
                            )
                        }
                        ActionType.RUNTIME_PHONE -> {
                            requestMultipleLauncher.launch(
                                arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
                            )
                        }
                        ActionType.RUNTIME_LOCATION_FOREGROUND -> {
                            requestMultipleLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                        ActionType.RUNTIME_LOCATION_BACKGROUND -> {
                            showBgLocationDialog = true
                        }
                        ActionType.RUNTIME_NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= 33) {
                                requestSingleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        ActionType.INTENT_FULL_SCREEN -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                        }
                        ActionType.INTENT_BATTERY -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    openAppSettings(context)
                                }
                            }
                        }
                        ActionType.INTENT_DND -> {
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                openAppSettings(context)
                            }
                        }
                        ActionType.APP_SETTINGS -> {
                            openAppSettings(context)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (allCriticalGranted) {
                    onAllCriticalGranted()
                } else {
                    // Launch first missing critical permission
                    items.firstOrNull { it.isCritical && !it.isGranted }?.let { missing ->
                        when (missing.actionType) {
                            ActionType.RUNTIME_SMS -> requestMultipleLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS))
                            ActionType.RUNTIME_LOCATION_FOREGROUND -> requestMultipleLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            ActionType.RUNTIME_LOCATION_BACKGROUND -> showBgLocationDialog = true
                            ActionType.RUNTIME_PHONE -> requestMultipleLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG))
                            else -> openAppSettings(context)
                        }
                    }
                }
            },
            enabled = allCriticalGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (allCriticalGranted) "Continue to App" else "Grant Required Permissions")
        }

        OutlinedButton(
            onClick = onOpenOemSetup,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Device Autostart / Battery Setup")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showBgLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBgLocationDialog = false },
            title = { Text("Allow Location 'All the time'") },
            text = {
                Text(
                    "To send your emergency location during an SOS even when your screen is locked or the app is closed, " +
                    "Android requires selecting 'Allow all the time' in Settings.\n\n" +
                    "Tap Continue to open the location permissions page."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBgLocationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            requestSingleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBgLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionItemCard(
    item: PermissionItem,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isGranted) MaterialTheme.colorScheme.surface
                             else if (item.isCritical) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Icon
            if (item.isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    if (item.isCritical) Icons.Default.Error else Icons.Default.Info,
                    contentDescription = "Not Granted",
                    tint = if (item.isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.isCritical) {
                        Text(
                            text = "REQUIRED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.isGranted) {
                FilledTonalButton(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Enable", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun getChecklistItems(context: Context): List<PermissionItem> {
    val items = mutableListOf<PermissionItem>()

    // 1. SMS (Send & Receive)
    val hasSendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    items.add(
        PermissionItem(
            id = "sms",
            title = "SMS (Send & Receive)",
            description = "Dispatches emergency SOS alerts and detects alerts from contacts without internet.",
            isCritical = true,
            isGranted = hasSendSms && hasReceiveSms,
            actionType = ActionType.RUNTIME_SMS
        )
    )

    // 2. Foreground Location
    val hasFineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    items.add(
        PermissionItem(
            id = "loc_fg",
            title = "GPS Location",
            description = "Attaches your live coordinates and Google Maps link to emergency messages.",
            isCritical = true,
            isGranted = hasFineLoc || hasCoarseLoc,
            actionType = ActionType.RUNTIME_LOCATION_FOREGROUND
        )
    )

    // 3. Background Location (Android 10+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val hasBgLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        items.add(
            PermissionItem(
                id = "loc_bg",
                title = "Background Location",
                description = "Enables follow-up SOS coordinates when moving with the phone locked.",
                isCritical = true,
                isGranted = hasBgLoc,
                actionType = ActionType.RUNTIME_LOCATION_BACKGROUND
            )
        )
    }

    // 4. Notifications (Android 13+)
    if (Build.VERSION.SDK_INT >= 33) {
        val hasPostNotif = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        items.add(
            PermissionItem(
                id = "notifications",
                title = "Notifications",
                description = "Keeps the 24/7 Protection service visible and triggers high-priority alerts.",
                isCritical = true,
                isGranted = hasPostNotif,
                actionType = ActionType.RUNTIME_NOTIFICATIONS
            )
        )
    }

    // 5. Battery Optimization Exemption
    val isBatteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else true
    items.add(
        PermissionItem(
            id = "battery",
            title = "Battery Unrestricted",
            description = "Prevents OS from killing the safety listeners when phone is in standby.",
            isCritical = true,
            isGranted = isBatteryExempt,
            actionType = ActionType.INTENT_BATTERY
        )
    )

    // 6. Do Not Disturb (DND) Access
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val hasDndAccess = nm?.isNotificationPolicyAccessGranted ?: true
    items.add(
        PermissionItem(
            id = "dnd",
            title = "Do Not Disturb Override",
            description = "Allows emergency alarms to sound audibly even if your phone is in silent mode.",
            isCritical = true,
            isGranted = hasDndAccess,
            actionType = ActionType.INTENT_DND
        )
    )

    // 7. Phone State & Call Log (for Phase 5 "I am safe" call detection)
    val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    items.add(
        PermissionItem(
            id = "phone_state",
            title = "Call Detection",
            description = "Automatically sends an 'I am safe' SMS when your guardian calls.",
            isCritical = false,
            isGranted = hasPhoneState && hasCallLog,
            actionType = ActionType.RUNTIME_PHONE
        )
    )

    // 8. Full Screen Intent (Android 14+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val hasFullScreen = nm?.canUseFullScreenIntent() ?: true
        items.add(
            PermissionItem(
                id = "full_screen",
                title = "Full-Screen Alarm",
                description = "Wakes up and shows the emergency alert over your lock screen.",
                isCritical = false,
                isGranted = hasFullScreen,
                actionType = ActionType.INTENT_FULL_SCREEN
            )
        )
    }

    return items
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
