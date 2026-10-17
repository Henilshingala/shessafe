package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives internal SOS_TRIGGER broadcast (sent by notification action).
 * SEC FIX: Reads phone number from SecurePreferences (encrypted), not plain prefs.
 * BUG FIX: Uses goAsync() + coroutine instead of runBlocking to avoid ANR.
 */
class SOSBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.womensafety.app.SOS_TRIGGER") return
        Log.d(TAG, "SOS_TRIGGER broadcast received")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val repository = ContactRepository(database.contactDao())
                val contacts = repository.getAllActiveContactsSync()

                if (contacts.isEmpty()) {
                    Log.w(TAG, "No contacts — cannot send SOS")
                    return@launch
                }

                val senderPhone = SecurePreferences.getUserPhoneNumber(context)
                val sosManager = com.womensafety.app.SOSManager(context)
                sosManager.sendSosAlerts(
                    contacts = contacts,
                    senderPhoneNumber = senderPhone
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending SOS from broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SOSBroadcastReceiver"
    }
}
