package com.womensafety.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

enum class OemBrand {
    XIAOMI,
    VIVO,
    OPPO_REALME_ONEPLUS,
    SAMSUNG,
    HUAWEI,
    OTHER
}

data class OemInstruction(
    val title: String,
    val description: String,
    val steps: List<String>
)

object OemHelper {

    private const val TAG = "OemHelper"

    fun detectOem(): OemBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                OemBrand.XIAOMI
            manufacturer.contains("vivo") || brand.contains("iqoo") ->
                OemBrand.VIVO
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
                OemBrand.OPPO_REALME_ONEPLUS
            manufacturer.contains("samsung") ->
                OemBrand.SAMSUNG
            manufacturer.contains("huawei") || brand.contains("honor") ->
                OemBrand.HUAWEI
            else ->
                OemBrand.OTHER
        }
    }

    fun getInstructions(brand: OemBrand): OemInstruction {
        return when (brand) {
            OemBrand.XIAOMI -> OemInstruction(
                title = "Xiaomi / Redmi / Poco Setup",
                description = "MIUI aggressively restricts background apps. Enable these to ensure 24/7 protection:",
                steps = listOf(
                    "1. Enable Autostart toggle",
                    "2. Battery Saver → choose 'No restrictions'",
                    "3. Other Permissions → allow 'Show on Lock screen' and 'Display pop-up windows while running in the background'",
                    "4. Lock app in Recents (swipe down on app card and tap lock icon)"
                )
            )
            OemBrand.VIVO -> OemInstruction(
                title = "Vivo / iQOO Setup",
                description = "FuntouchOS/OriginOS terminates background safety services unless exempted:",
                steps = listOf(
                    "1. Battery → High background power consumption → Enable for Nirbhaya",
                    "2. Autostart management → Enable Nirbhaya",
                    "3. Lock in Recents screen"
                )
            )
            OemBrand.OPPO_REALME_ONEPLUS -> OemInstruction(
                title = "Oppo / Realme / OnePlus Setup",
                description = "ColorOS/OxygenOS requires explicit background activity permission:",
                steps = listOf(
                    "1. App Battery Management → Allow background activity",
                    "2. Enable 'Auto-launch' / 'Allow auto-start'",
                    "3. Disable 'Deep sleep' or 'Sleep standby optimization' for this app"
                )
            )
            OemBrand.SAMSUNG -> OemInstruction(
                title = "Samsung One UI Setup",
                description = "Ensure Nirbhaya is never put to sleep during emergencies:",
                steps = listOf(
                    "1. Battery → Background usage limits → Never sleeping apps → Add Nirbhaya",
                    "2. App info → Battery → select 'Unrestricted'",
                    "3. Device care → Auto optimization → ensure app is not sleeping"
                )
            )
            OemBrand.HUAWEI -> OemInstruction(
                title = "Huawei / Honor Setup",
                description = "EMUI/MagicOS manages background launch strictly:",
                steps = listOf(
                    "1. Battery → App launch → Nirbhaya → switch to 'Manage manually'",
                    "2. Enable all three toggles: Auto-launch, Secondary launch, and Run in background"
                )
            )
            OemBrand.OTHER -> OemInstruction(
                title = "Background Protection Setup",
                description = "Keep Nirbhaya active in the background:",
                steps = listOf(
                    "1. Battery → App battery usage → set to 'Unrestricted'",
                    "2. Exclude from battery saver",
                    "3. Allow notifications and background activity"
                )
            )
        }
    }

    /**
     * Try opening the vendor-specific autostart or battery screen.
     * Falls back to app details screen on any failure.
     */
    fun openOemSettings(context: Context): Boolean {
        val brand = detectOem()
        val packageName = context.packageName

        val intents = when (brand) {
            OemBrand.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            )
            OemBrand.VIVO -> listOf(
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
            )
            OemBrand.OPPO_REALME_ONEPLUS -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.AppListActivity"))
            )
            OemBrand.SAMSUNG -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"))
            )
            OemBrand.HUAWEI -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            OemBrand.OTHER -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched OEM intent: $intent")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed OEM intent: ${e.message}")
            }
        }

        // Fallback: Open standard app settings details screen
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback to app details also failed", e)
            false
        }
    }
}
