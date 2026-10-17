package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.service.ProtectionService

/**
 * Starts the ProtectionService after device reboot, so SMS/call listeners
 * are restored without requiring the user to open the app.
 * Only starts if the user is logged in.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        if (!SecurePreferences.isLoggedIn(context)) {
            Log.d(TAG, "Not logged in — skipping protection service start")
            return
        }

        Log.d(TAG, "Boot completed — starting ProtectionService")
        ProtectionService.start(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
