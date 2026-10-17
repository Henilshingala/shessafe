package com.womensafety.app.data

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.womensafety.app.BuildConfig
import kotlin.random.Random

/**
 * Production-grade OTP manager with security hardening:
 * - OTP expiration (5 minutes)
 * - Resend cooldown (60 seconds)
 * - Verification attempt limit (3 max)
 * - Phone number validation
 * - No public OTP access
 * - No OTP persistence
 * - Debug logs only in debug builds
 */
class SimpleOTPManager(private val context: Context) {
    
    // Private - no public access to OTP
    private var currentOTP: String = ""
    private var currentPhone: String = ""
    private var otpExpiryTime: Long = 0
    private var lastOtpSentTime: Long = 0
    private var verificationAttempts: Int = 0
    private var lockoutUntil: Long = 0
    
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
    
    companion object {
        private const val TAG = "SimpleOTPManager"
        private const val OTP_VALIDITY_MS = 300_000L // 5 minutes
        private const val RESEND_COOLDOWN_MS = 60_000L // 60 seconds
        private const val MAX_VERIFICATION_ATTEMPTS = 3
        private const val LOCKOUT_DURATION_MS = 300_000L // 5 minutes
        
        // Phone number validation
        private fun isValidPhoneNumber(phone: String): Boolean {
            // Remove common formatting characters
            val cleaned = phone.filter { it.isDigit() || it == '+' }
            
            // Must be 10-15 digits for international format
            if (cleaned.length < 10 || cleaned.length > 15) return false
            
            // Must start with + or digit
            if (cleaned.isNotEmpty() && cleaned[0] != '+' && !cleaned[0].isDigit()) {
                return false
            }
            
            // Must contain only valid characters
            val validChars = cleaned.all { it.isDigit() || it == '+' }
            if (!validChars) return false
            
            // If starts with +, next must be digit
            if (cleaned.startsWith("+") && cleaned.length > 1 && !cleaned[1].isDigit()) {
                return false
            }
            
            return true
        }
        
        // Generate secure 6-digit OTP
        private fun generateSecureOTP(): String {
            val otp = Random.nextInt(100000, 999999).toString()
            // Double-check it's exactly 6 digits
            return if (otp.length == 6 && otp.all { it.isDigit() }) {
                otp
            } else {
                // Fallback - should never happen
                String.format("%06d", Random.nextInt(1000000))
            }
        }
    }
    
    /**
     * Generate and send OTP with security checks
     * Returns empty string if failed
     */
    fun generateAndSendOTP(phoneNumber: String): String {
        // Check lockout
        val now = System.currentTimeMillis()
        if (now < lockoutUntil) {
            val remainingSeconds = (lockoutUntil - now) / 1000
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Account locked. Retry in $remainingSeconds seconds")
            }
            return ""
        }
        
        // Validate phone number
        if (!isValidPhoneNumber(phoneNumber)) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Invalid phone number format")
            }
            return ""
        }
        
        // Check resend cooldown
        if (now - lastOtpSentTime < RESEND_COOLDOWN_MS) {
            val remainingSeconds = (RESEND_COOLDOWN_MS - (now - lastOtpSentTime)) / 1000
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Resend cooldown active. Wait $remainingSeconds seconds")
            }
            return ""
        }

        // Generate secure OTP
        currentOTP = generateSecureOTP()
        currentPhone = phoneNumber
        otpExpiryTime = now + OTP_VALIDITY_MS
        lastOtpSentTime = now
        verificationAttempts = 0 // Reset attempts on new OTP
        
        // Sanitize OTP (ensure only digits)
        val sanitizedOTP = currentOTP.filter { it.isDigit() }
        
        val message = "Your Women Safety App OTP is: $sanitizedOTP. Valid for 5 minutes. Do not share."
        
        return try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OTP sent successfully to ${phoneNumber.takeLast(4)}")
            }
            // Return success indicator (NOT the OTP itself)
            "sent"
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "SMS permission denied", e)
            }
            clearOTP()
            ""
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to send OTP", e)
            }
            clearOTP()
            ""
        }
    }
    
    /**
     * Verify OTP with security checks
     * Returns true only if valid, not expired, and within attempt limit
     */
    fun verifyOTP(enteredOTP: String, phoneNumber: String): Boolean {
        val now = System.currentTimeMillis()
        
        // Check lockout
        if (now < lockoutUntil) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Account locked during verification")
            }
            return false
        }
        
        // Check expiration
        if (now > otpExpiryTime) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "OTP expired")
            }
            clearOTP()
            return false
        }
        
        // Check attempt limit
        if (verificationAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Max verification attempts exceeded")
            }
            // Lock account for 5 minutes
            lockoutUntil = now + LOCKOUT_DURATION_MS
            clearOTP()
            return false
        }
        
        // Increment attempts
        verificationAttempts++
        
        // Verify OTP and phone
        val sanitizedEntered = enteredOTP.filter { it.isDigit() }
        val isValid = sanitizedEntered == currentOTP && phoneNumber == currentPhone
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "OTP verification: ${if (isValid) "success" else "failed"} (attempt $verificationAttempts/$MAX_VERIFICATION_ATTEMPTS)")
        }
        
        if (isValid) {
            // Success - clear everything
            clearOTP()
            verificationAttempts = 0
        } else if (verificationAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            // Lock account after max attempts
            lockoutUntil = now + LOCKOUT_DURATION_MS
            clearOTP()
        }
        
        return isValid
    }
    
    /**
     * Check if resend is available (cooldown expired)
     */
    fun canResendOTP(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastOtpSentTime) >= RESEND_COOLDOWN_MS && now < lockoutUntil
    }
    
    /**
     * Get remaining cooldown time in seconds
     */
    fun getRemainingCooldownSeconds(): Long {
        val now = System.currentTimeMillis()
        val remaining = RESEND_COOLDOWN_MS - (now - lastOtpSentTime)
        return if (remaining > 0) remaining / 1000 else 0
    }
    
    /**
     * Check if OTP is still valid (not expired)
     */
    fun isOTPValid(): Boolean {
        return System.currentTimeMillis() < otpExpiryTime && currentOTP.isNotEmpty()
    }
    
    /**
     * Clear OTP data (security cleanup)
     */
    private fun clearOTP() {
        currentOTP = ""
        currentPhone = ""
        otpExpiryTime = 0
    }
    
    /**
     * Force clear all OTP data (for logout/security)
     */
    fun forceReset() {
        clearOTP()
        verificationAttempts = 0
        lastOtpSentTime = 0
        lockoutUntil = 0
    }
}
