package com.womensafety.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Production-grade permission manager for emergency safety app.
 * 
 * Handles all runtime permissions required for Android 12-14:
 * - SMS permissions (SEND_SMS, RECEIVE_SMS)
 * - POST_NOTIFICATIONS (Android 13+)
 * - USE_FULL_SCREEN_INTENT (Android 14+)
 * - SCHEDULE_EXACT_ALARM (Android 12+)
 * - Battery optimization exemption
 * - Do Not Disturb access (for reliable alarms)
 */
class PermissionManager(private val activity: ComponentActivity) {
    
    private var onPermissionResult: ((Boolean) -> Unit)? = null
    
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.isNotEmpty() && permissions.values.all { it }
        Log.d(TAG, "Permissions result: $permissions, allGranted: $allGranted")
        onPermissionResult?.invoke(allGranted)
        onPermissionResult = null
    }
    
    private val settingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Settings result: ${result.resultCode}")
        // Check if the permission was granted after coming back from settings
        onPermissionResult?.invoke(hasAllPermissions())
        onPermissionResult = null
    }
    
    /**
     * Request a single SMS permission
     */
    fun requestSMSPermission(onResult: (Boolean) -> Unit) {
        onPermissionResult = onResult
        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
    }

    /**
     * Request all runtime permissions needed for the app
     */
    fun requestAllPermissions(onResult: (Boolean) -> Unit) {
        onPermissionResult = onResult
        permissionLauncher.launch(getAllRequiredPermissions())
    }

    /**
     * Check if all runtime permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return getAllRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                activity, permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Request receiver-specific permissions (for receiving SOS alerts)
     */
    fun requestReceiverPermissions(onResult: (Boolean) -> Unit) {
        onPermissionResult = onResult
        val permissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }
    
    /**
     * Check if SMS permissions are granted
     */
    fun hasSMSPermission(): Boolean {
        val hasSendSMS = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return hasSendSMS
    }
    
    /**
     * Check if receiver permissions are granted
     */
    fun hasReceiverPermissions(): Boolean {
        val hasReceive = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val hasPostNotifications = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasReceive && hasPostNotifications
    }
    
    /**
     * Check if full-screen intent permission is granted (Android 14+)
     */
    fun hasFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.USE_FULL_SCREEN_INTENT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }
    }
    
    /**
     * Request full-screen intent permission (Android 14+)
     */
    fun requestFullScreenIntentPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            onPermissionResult = onResult
            permissionLauncher.launch(arrayOf(Manifest.permission.USE_FULL_SCREEN_INTENT))
        } else {
            onResult(true)
        }
    }
    
    /**
     * Check if battery optimization is disabled for this app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        } else {
            true
        }
    }
    
    /**
     * Request to disable battery optimization (critical for emergency alerts)
     */
    fun requestDisableBatteryOptimization(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                onPermissionResult = onResult
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                settingsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request battery optimization exemption", e)
                onResult(false)
            }
        } else {
            onResult(true)
        }
    }
    
    /**
     * Check if exact alarm permission is granted (Android 12+)
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    /**
     * Request exact alarm permission (Android 12+)
     */
    fun requestExactAlarmPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                onPermissionResult = onResult
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                settingsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request exact alarm permission", e)
                onResult(false)
            }
        } else {
            onResult(true)
        }
    }
    
    /**
     * Request all critical permissions for receiver mode
     * This includes runtime permissions and special permissions
     */
    fun requestAllReceiverPermissions(onResult: (Boolean) -> Unit) {
        requestReceiverPermissions { runtimeGranted ->
            if (!runtimeGranted) {
                onResult(false)
                return@requestReceiverPermissions
            }
            
            // Check and request full-screen intent permission
            if (!hasFullScreenIntentPermission()) {
                requestFullScreenIntentPermission { fullScreenGranted ->
                    if (!fullScreenGranted) {
                        Log.w(TAG, "Full-screen intent permission not granted")
                    }
                    
                    // Check battery optimization
                    if (!isBatteryOptimizationDisabled()) {
                        requestDisableBatteryOptimization { batteryOptGranted ->
                            if (!batteryOptGranted) {
                                Log.w(TAG, "Battery optimization not disabled")
                            }
                            onResult(true) // Continue even if special permissions not granted
                        }
                    } else {
                        onResult(true)
                    }
                }
            } else {
                // Check battery optimization
                if (!isBatteryOptimizationDisabled()) {
                    requestDisableBatteryOptimization { batteryOptGranted ->
                        if (!batteryOptGranted) {
                            Log.w(TAG, "Battery optimization not disabled")
                        }
                        onResult(true) // Continue even if special permissions not granted
                    }
                } else {
                    onResult(true)
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "PermissionManager"
        
        /**
         * Get all runtime permissions required for the app
         */
        fun getAllRequiredPermissions(): Array<String> {
            val permissions = mutableListOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.VIBRATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )

            // Add POST_NOTIFICATIONS for Android 13+
            if (Build.VERSION.SDK_INT >= 33) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // Add USE_FULL_SCREEN_INTENT for Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions.add(Manifest.permission.USE_FULL_SCREEN_INTENT)
            }

            return permissions.toTypedArray()
        }
    }
}
