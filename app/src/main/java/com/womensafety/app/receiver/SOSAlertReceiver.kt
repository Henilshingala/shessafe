package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.womensafety.app.SOSManager
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.util.PhoneUtil

/**
 * Receives incoming SMS messages and detects [NSOS] emergency messages.
 *
 * BUG-01 FIX: No longer uses the fragile OTP heuristic or word-matching.
 *   Emergency messages are identified by the [NSOS] prefix only.
 * SEC FIX: Does NOT log SMS body content.
 * IoT REMOVED: No IoT SIM checking.
 */
class SOSAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val originatingAddress = messages.firstOrNull()?.originatingAddress ?: return
            val messageBody = messages.joinToString("") { it?.messageBody ?: "" }

            // Only process messages with the [NSOS] prefix
            if (!messageBody.startsWith(SOSManager.SOS_PREFIX)) return

            // Ignore messages from self (e.g. SOS we sent to our own number)
            val selfNumber = SecurePreferences.getUserPhoneNumber(context)
            if (selfNumber.isNotBlank() && PhoneUtil.isSameNumber(originatingAddress, selfNumber)) {
                Log.d(TAG, "Ignoring self-sent SOS")
                return
            }

            Log.d(TAG, "[NSOS] emergency message received — triggering alert")
            SOSAlertService.start(context, durationMs = 30_000L)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming SMS", e)
        }
    }

    companion object {
        private const val TAG = "SOSAlertReceiver"
    }
}
