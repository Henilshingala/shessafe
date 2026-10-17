package com.womensafety.app

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.womensafety.app.data.models.EmergencyContact

class SOSManager(private val context: Context) {
    
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
    
    fun sendSOSSAlerts(
        contacts: List<EmergencyContact>,
        userLocation: String = "",
        senderPhoneNumber: String = ""
    ): Boolean {
        if (contacts.isEmpty()) {
            Log.e("SOSManager", "No contacts to send SOS to")
            return false
        }
        
        var successCount = 0
        
        contacts.forEach { contact ->
            try {
                if (contact.phoneNumber.isBlank()) {
                    Log.w("SOSManager", "Skipping contact with empty phone number: ${contact.name}")
                    return@forEach
                }
                
                val message = buildSOSMessage(contact.name, userLocation, senderPhoneNumber)
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
                successCount++
                Log.d("SOSManager", "SOS sent to ${contact.name}")
            } catch (e: SecurityException) {
                Log.e("SOSManager", "SMS permission denied for ${contact.name}", e)
            } catch (e: Exception) {
                Log.e("SOSManager", "Failed to send SOS to ${contact.name}", e)
            }
        }
        
        Log.d("SOSManager", "SOS alerts sent: $successCount/${contacts.size}")
        return successCount > 0
    }
    
private fun buildSOSMessage(contactName: String, location: String, senderPhoneNumber: String): String {
    val senderInfo = if (senderPhoneNumber.isNotEmpty()) senderPhoneNumber else "Unknown"
    
    val baseMessage = """URGENT SAFETY ALERT
Hello $contactName,
This is an emergency message sent via a Who Safe Pendant.
The person using this device is in immediate danger and needs urgent help.
Please try to contact the person at $senderInfo immediately or inform the nearest police station.
sos This alert has been sent to you as a trusted emergency contact.
Time is critical. Your quick action could save a life.
- WHO SAFE Emergency Response System"""
    
    val locationInfo = if (location.isNotEmpty()) {
        "\n\nCurrent location: $location"
    } else {
        "\n\nLocation information not available."
    }
    
    return "$baseMessage$locationInfo"
    }
}
