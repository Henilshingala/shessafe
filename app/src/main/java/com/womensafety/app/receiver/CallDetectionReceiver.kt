package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.womensafety.app.SOSManager
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.util.LocationHelper
import com.womensafety.app.util.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects incoming calls from configured emergency guardians.
 * If incoming caller matches a guardian:
 * - Checks 2-minute cooldown per guardian
 * - Retrieves best current location
 * - Sends an [NSAFE] auto-SMS to ONLY that guardian:
 *   "[NSAFE] Automatic message from <user phone number>: I am safe right now. My location: https://maps.google.com/?q=<lat>,<lng> (<HH:mm dd/MM>)."
 * - Does not answer or reject call.
 */
class CallDetectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallDetectionReceiver"
        private const val COOLDOWN_MS = 2 * 60 * 1000L // 2 minutes cooldown per guardian
        private val lastSentTimestamps = ConcurrentHashMap<String, Long>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        if (!SecurePreferences.isLoggedIn(context) || !SecurePreferences.isIAmSafeEnabled(context)) {
            return
        }

        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (incomingNumber.isNullOrBlank()) {
            Log.d(TAG, "Incoming number not available in broadcast (may need call log permission)")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncomingCall(context, incomingNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming call for safe auto-reply", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleIncomingCall(context: Context, incomingNumber: String) {
        val database = AppDatabase.getDatabase(context)
        val repository = ContactRepository(database.contactDao())
        val contacts = repository.getAllActiveContactsSync()

        val matchingGuardian = contacts.firstOrNull { contact ->
            PhoneUtil.isSameNumber(contact.phoneNumber, incomingNumber)
        } ?: return

        val normalizedNumber = matchingGuardian.phoneNumber
        val now = System.currentTimeMillis()
        val lastSent = lastSentTimestamps[normalizedNumber] ?: 0L

        if (now - lastSent < COOLDOWN_MS) {
            Log.d(TAG, "Cooldown active for guardian $normalizedNumber, skipping safe auto-SMS")
            return
        }

        lastSentTimestamps[normalizedNumber] = now

        val locationHelper = LocationHelper(context)
        val locationResult = locationHelper.getBestLocation(timeoutMs = 6000L)
        val timeStamp = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date())
        val mapsUrl = locationResult.toMapsUrl() ?: ""
        val senderPhone = SecurePreferences.getUserPhoneNumber(context)

        val sosManager = SOSManager(context)
        val message = sosManager.buildSafeMessage(senderPhone, mapsUrl, timeStamp)

        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(normalizedNumber, null, parts, null, null)
        Log.d(TAG, "Safe auto-SMS sent to guardian (no PII in logs)")
    }
}
