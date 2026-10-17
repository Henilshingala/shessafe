package com.womensafety.app.data

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import kotlin.random.Random

class OTPManager(private val context: Context) {
    
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
    
    fun generateOTP(): String {
        val otp = Random.nextInt(100000, 999999).toString()
        Log.d("OTPManager", "Generated OTP")
        return otp
    }
    
    fun sendOTP(phoneNumber: String, otp: String): Boolean {
        return try {
            val message = "Your Women Safety App OTP is: $otp. Do not share this code with anyone."
            
            Log.d("OTPManager", "Attempting to send OTP SMS")
            
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("OTPManager", "OTP SMS sent successfully")
            true
        } catch (e: SecurityException) {
            Log.e("OTPManager", "SMS permission denied", e)
            false
        } catch (e: Exception) {
            Log.e("OTPManager", "Failed to send OTP SMS", e)
            false
        }
    }
    
    fun validateOTP(enteredOTP: String, actualOTP: String): Boolean {
        val cleanedEnteredOTP = enteredOTP.trim().filter { it.isDigit() }
        val cleanedActualOTP = actualOTP.trim().filter { it.isDigit() }

        return cleanedEnteredOTP == cleanedActualOTP && cleanedEnteredOTP.length == 6
    }
    
    data class OTPSession(
        val phoneNumber: String,
        val otp: String,
        val timestamp: Long = System.currentTimeMillis(),
        var attempts: Int = 0
    )
    
    companion object {
        private const val OTP_EXPIRY_TIME = 5 * 60 * 1000L // 5 minutes
        private const val MAX_ATTEMPTS = 3
        
        fun isOTPValid(session: OTPSession): Boolean {
            val currentTime = System.currentTimeMillis()
            val isExpired = (currentTime - session.timestamp) > OTP_EXPIRY_TIME
            val hasAttemptsLeft = session.attempts < MAX_ATTEMPTS
            return !isExpired && hasAttemptsLeft
        }
    }
}
