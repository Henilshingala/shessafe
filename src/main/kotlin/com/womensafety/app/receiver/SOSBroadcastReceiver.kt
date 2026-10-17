package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.womensafety.app.service.SOSService

class SOSBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.womensafety.app.SOS_TRIGGER") {
            // Trigger SOS from wearable device
            val sosIntent = Intent(context, SOSService::class.java)
            context?.startService(sosIntent)
        }
    }
}
