package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.womensafety.app.SOSManager
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.receiver.SOSAlertService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SOSAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SOSAlertReceiver", "SMS received: ${intent.action}")
        
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return

                val originatingAddress = messages.firstOrNull()?.originatingAddress
                val messageBody = messages.joinToString(separator = "") { it?.messageBody ?: "" }
                
                // PRIORITY 1: Check if from IoT SIM first (start Foreground Service immediately)
                if (originatingAddress != null && isFromIotSim(context, originatingAddress)) {
                    if (isIotTriggerMessage(messageBody)) {
                        Log.d("SOSAlertReceiver", "========================================")
                        Log.d("SOSAlertReceiver", "IoT SOS DETECTED from: $originatingAddress")
                        Log.d("SOSAlertReceiver", "Starting IoT SOS Forwarding Service...")
                        Log.d("SOSAlertReceiver", "========================================")
                        
                        // START FOREGROUND SERVICE IMMEDIATELY
                        // Service will handle all forwarding logic independently
                        IoTSOSForwardingService.start(context)
                        
                        return  // Exit immediately - service handles everything
                    } else {
                        Log.d("SOSAlertReceiver", "Message from IoT SIM but not 'emergency', ignoring")
                        return
                    }
                }
                
                // PRIORITY 2: Check for SOS keywords (plays alarm on this device)
                if (isSOSMessage(messageBody)) {
                    Log.d("SOSAlertReceiver", "SOS message detected!")
                    if (originatingAddress != null && isFromSelfNumber(context, originatingAddress)) {
                        Log.d("SOSAlertReceiver", "Ignoring self-sent SOS message")
                        return
                    }
                    Log.d("SOSAlertReceiver", "Playing emergency alert on THIS device")
                    triggerEmergencyAlert(context)
                    return
                }
            } catch (e: Exception) {
                Log.e("SOSAlertReceiver", "Error handling SMS", e)
            }
        }
    }

    private fun isFromIotSim(context: Context, sender: String): Boolean {
        val stored = com.womensafety.app.data.SecurePreferences.getIotSimNumber(context).trim()
        Log.d("SOSAlertReceiver", "IoT SIM check - Stored: '$stored', Sender: '$sender'")
        if (stored.isBlank()) {
            Log.w("SOSAlertReceiver", "⚠️ IoT SIM number NOT configured in settings!")
            return false
        }
        val matches = phoneNumbersMatch(stored, sender)
        Log.d("SOSAlertReceiver", "IoT SIM match result: $matches")
        return matches
    }

    private fun isFromSelfNumber(context: Context, sender: String): Boolean {
        val stored = com.womensafety.app.data.SecurePreferences.getUserPhoneNumber(context).trim()
        if (stored.isBlank()) return false
        return phoneNumbersMatch(stored, sender)
    }

    private fun phoneNumbersMatch(a: String, b: String): Boolean {
        val na = a.filter { it.isDigit() }
        val nb = b.filter { it.isDigit() }
        if (na.isBlank() || nb.isBlank()) return false
        if (na.length < 10 || nb.length < 10) return false
        val aSuffix = na.takeLast(10)
        val bSuffix = nb.takeLast(10)
        return aSuffix == bSuffix
    }
    
    private fun isIotTriggerMessage(messageBody: String): Boolean {
        val normalized = messageBody.trim().lowercase()
        val isMatch = normalized == "emergency"
        Log.d("SOSAlertReceiver", "IoT trigger message check: '$messageBody' -> $isMatch")
        return isMatch
    }

    private fun isSOSMessage(messageBody: String): Boolean {
        val normalized = messageBody.lowercase()
        
        Log.d("SOSAlertReceiver", "Checking message: '$messageBody'")
        
        if (isOTPMessage(normalized)) {
            Log.d("SOSAlertReceiver", "OTP message detected, ignoring")
            return false
        }
        
        // Broader SOS detection - include standalone "sos" keyword
        val isSOS = normalized.contains("who safe") ||
                    normalized.contains("urgent safety alert") ||
                    normalized.contains("sos") ||
                    (normalized.contains("emergency") && normalized.contains("alert"))
        
        Log.d("SOSAlertReceiver", "SOS message check result: $isSOS for message: '$messageBody'")
        return isSOS
    }
    
    private fun isOTPMessage(messageBody: String): Boolean {
        val normalized = messageBody.lowercase()
        // Detect OTP messages by common patterns
        return normalized.contains("otp") ||
               normalized.contains("verification code") ||
               normalized.contains("one time password") ||
               normalized.contains("your code is") ||
               normalized.contains("verification") ||
               normalized.contains("confirm") ||
               // OTP messages are usually short and contain digits
               (messageBody.matches(Regex(".*\\d{4,6}.*")) && messageBody.length < 100)
    }
    
    private fun triggerEmergencyAlert(context: Context) {
        Log.d("SOSAlertReceiver", "========================================")
        Log.d("SOSAlertReceiver", "EMERGENCY ALERT TRIGGERED!")
        Log.d("SOSAlertReceiver", "Starting SOSAlertService for foreground buzzer + vibration")
        Log.d("SOSAlertReceiver", "========================================")
        
        try {
            // Start the emergency alert service (handles 30-60 second alarm + vibration)
            SOSAlertService.start(context, durationMs = 30_000L)
            Log.d("SOSAlertReceiver", "SOSAlertService.start() called successfully")
        } catch (e: Exception) {
            Log.e("SOSAlertReceiver", "CRITICAL ERROR: Failed to start SOSAlertService", e)
            e.printStackTrace()
        }
    }

    private fun triggerAutoSOS(context: Context) {
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${context.packageName}:AutoSOS"
            )?.apply { acquire(35_000L) }
        } catch (e: Exception) {
            Log.w("SOSAlertReceiver", "Failed to acquire wake lock", e)
            null
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("SOSAlertReceiver", "Auto-SOS triggered, waiting 3 seconds before sending alerts...")
                Thread.sleep(3000L)
                Log.d("SOSAlertReceiver", "3-second delay completed, now sending SMS alerts...")
                
                val senderPhoneNumber = com.womensafety.app.data.SecurePreferences.getUserPhoneNumber(context).trim()
                val iotSimNumber = com.womensafety.app.data.SecurePreferences.getIotSimNumber(context).trim()

                val database = AppDatabase.getDatabase(context)
                val repository = ContactRepository(database.contactDao())
                val contacts = repository.getAllActiveContactsSync()

                if (contacts.isEmpty()) {
                    Log.w("SOSAlertReceiver", "No contacts available for Auto-SOS")
                    return@launch
                }

                val filteredContacts = contacts.filterNot { contact ->
                    contact.phoneNumber.isBlank() ||
                    (iotSimNumber.isNotBlank() && phoneNumbersMatch(iotSimNumber, contact.phoneNumber)) ||
                    (senderPhoneNumber.isNotBlank() && phoneNumbersMatch(senderPhoneNumber, contact.phoneNumber))
                }

                val contactsToSend = if (filteredContacts.isNotEmpty()) {
                    filteredContacts
                } else {
                    contacts.filterNot { contact ->
                        contact.phoneNumber.isBlank() ||
                        (senderPhoneNumber.isNotBlank() && phoneNumbersMatch(senderPhoneNumber, contact.phoneNumber))
                    }
                }

                if (contactsToSend.isNotEmpty()) {
                    val sosManager = SOSManager(context)
                    sosManager.sendSOSSAlerts(
                        contacts = contactsToSend,
                        senderPhoneNumber = senderPhoneNumber
                    )
                    Log.d("SOSAlertReceiver", "Auto-SOS SMS alerts sent successfully")
                } else {
                    Log.w("SOSAlertReceiver", "No valid contacts to send Auto-SOS")
                }
                
            } catch (e: Exception) {
                Log.e("SOSAlertReceiver", "Failed to send Auto-SOS SMS alerts", e)
            } finally {
                try {
                    wakeLock?.release()
                } catch (e: Exception) {
                    Log.w("SOSAlertReceiver", "Failed to release wake lock", e)
                }
                pendingResult.finish()
            }
        }
    }
    
    companion object {
        fun cleanup() {
            // Static cleanup if needed
        }
    }
}
