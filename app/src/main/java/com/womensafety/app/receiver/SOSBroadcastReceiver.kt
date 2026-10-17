package com.womensafety.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.database.AppDatabase
import kotlinx.coroutines.runBlocking

class SOSBroadcastReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SOSReceiver", "SOS broadcast received")
        
        when (intent.action) {
            "com.womensafety.app.SOS_TRIGGER" -> {
                handleSOSTrigger(context)
            }
        }
    }
    
    private fun handleSOSTrigger(context: Context) {
        try {
            // Get emergency contacts from preferences (simplified for now)
            val emergencyContacts = getEmergencyContacts(context)
            
            if (emergencyContacts.isEmpty()) {
                Toast.makeText(context, "No emergency contacts configured", Toast.LENGTH_LONG).show()
                return
            }
            
            val prefs = context.getSharedPreferences("women_safety_prefs", Context.MODE_PRIVATE)
            val senderphonennumber = prefs.getString("user_phone_number", "") ?: "Unknown"

            val message = """URGENT SAFETY ALERT
This is an emergency message sent via a Who Safe Pendant.
The person using this device is in immediate danger and needs urgent help.
Please try to contact the person at $senderphonennumber immediately or inform the nearest police station.
sos This alert has been sent to you as a trusted emergency contact.
Time is critical. Your quick action could save a life.
- WHO SAFE Emergency Response System"""
            
            emergencyContacts.forEach { contact ->
                sendSMS(context, contact, message)
            }
            
            Toast.makeText(context, "Emergency alerts sent to contacts", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("SOSReceiver", "Error handling SOS trigger", e)
            Toast.makeText(context, "Failed to send emergency alerts", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun sendSMS(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.d("SOSReceiver", "SMS sent")
        } catch (e: Exception) {
            Log.e("SOSReceiver", "Failed to send SMS", e)
        }
    }
    
    private fun getEmergencyContacts(context: Context): List<String> {
        return try {
            val database = AppDatabase.getDatabase(context)
            val repository = ContactRepository(database.contactDao())
            runBlocking {
                repository.getActiveContactPhoneNumbers()
            }
        } catch (e: Exception) {
            Log.e("SOSReceiver", "Error getting contacts from database", e)
            emptyList()
        }
    }
}
