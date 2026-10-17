package com.womensafety.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat.startActivity

/**
 * Helper class for Xiaomi/MIUI specific background restrictions
 * 
 * Xiaomi devices have aggressive battery optimization that prevents
 * apps from receiving SMS when closed. This helper:
 * - Detects MIUI/Xiaomi devices
 * - Opens Autostart settings
 * - Guides users through required permissions
 */
object XiaomiAutostartHelper {
    
    private const val TAG = "XiaomiAutostart"
    
    /**
     * Check if device is Xiaomi/MIUI
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        val isXiaomi = manufacturer == "xiaomi" || 
                      brand == "xiaomi" || 
                      brand == "redmi" || 
                      brand == "poco"
        
        Log.d(TAG, "Device check - Manufacturer: $manufacturer, Brand: $brand, Is Xiaomi: $isXiaomi")
        return isXiaomi
    }
    
    /**
     * Open MIUI Autostart management page
     * 
     * Tries multiple intents in order of preference:
     * 1. Direct autostart page
     * 2. App-specific settings
     * 3. General settings page (fallback)
     */
    fun openAutostartSettings(context: Context): Boolean {
        val packageName = context.packageName
        
        // Try 1: MIUI Autostart page (most direct)
        val autostartIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (tryStartActivity(context, autostartIntent, "MIUI Autostart page")) {
            return true
        }
        
        // Try 2: Alternative MIUI Autostart page
        val altAutostartIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (tryStartActivity(context, altAutostartIntent, "MIUI Power Settings")) {
            return true
        }
        
        // Try 3: App-specific settings page (fallback)
        val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (tryStartActivity(context, appSettingsIntent, "App Settings")) {
            return true
        }
        
        Log.e(TAG, "Failed to open any settings page")
        return false
    }
    
    /**
     * Open battery optimization settings
     */
    fun openBatterySettings(context: Context): Boolean {
        val packageName = context.packageName
        
        // Try 1: Battery optimization settings
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (tryStartActivity(context, batteryIntent, "Battery Optimization Settings")) {
            return true
        }
        
        // Try 2: App settings as fallback
        val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return tryStartActivity(context, appSettingsIntent, "App Settings")
    }
    
    /**
     * Try to start an activity, catching exceptions
     */
    private fun tryStartActivity(context: Context, intent: Intent, description: String): Boolean {
        return try {
            context.startActivity(intent)
            Log.d(TAG, "✅ Successfully opened: $description")
            true
        } catch (e: Exception) {
            Log.w(TAG, "❌ Failed to open $description: ${e.message}")
            false
        }
    }
    
    /**
     * Check if Xiaomi setup has been completed
     */
    fun isSetupComplete(context: Context): Boolean {
        return com.womensafety.app.data.SecurePreferences.isXiaomiSetupComplete(context)
    }
    
    /**
     * Mark Xiaomi setup as complete
     */
    fun markSetupComplete(context: Context) {
        com.womensafety.app.data.SecurePreferences.setXiaomiSetupComplete(context, true)
        Log.d(TAG, "Xiaomi setup marked as complete")
    }
}
