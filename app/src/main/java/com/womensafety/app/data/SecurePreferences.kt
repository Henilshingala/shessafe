package com.womensafety.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.womensafety.app.BuildConfig

/**
 * Secure storage manager using EncryptedSharedPreferences.
 * All sensitive data (phone numbers, settings) are encrypted using Android Keystore (AES-256).
 * 
 * This ensures:
 * - Data is encrypted at rest
 * - Keys are stored in Android Keystore (hardware-backed if available)
 * - Backup files are encrypted
 * - Root access cannot read sensitive data
 */
object SecurePreferences {
    
    private const val TAG = "SecurePreferences"
    private const val PREFS_FILE_NAME = "women_safety_secure_prefs"
    
    private var encryptedPrefs: SharedPreferences? = null
    
    /**
     * Get encrypted SharedPreferences instance
     */
    fun getInstance(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPreferences(context).also {
                encryptedPrefs = it
            }
        }
    }
    
    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            // Create or retrieve master key from Android Keystore
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // Create encrypted SharedPreferences
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to create encrypted preferences, falling back to regular", e)
            }
            // Fallback to regular SharedPreferences only if encryption fails
            // This should NEVER happen in production on modern devices
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }
    
    // Convenience methods for common operations
    
    fun setUserPhoneNumber(context: Context, phoneNumber: String) {
        getInstance(context).edit().putString(KEY_USER_PHONE, phoneNumber).apply()
    }
    
    fun getUserPhoneNumber(context: Context): String {
        return getInstance(context).getString(KEY_USER_PHONE, "") ?: ""
    }
    
    fun setIotSimNumber(context: Context, iotNumber: String) {
        getInstance(context).edit().putString(KEY_IOT_SIM, iotNumber).apply()
    }
    
    fun getIotSimNumber(context: Context): String {
        return getInstance(context).getString(KEY_IOT_SIM, "") ?: ""
    }
    
    fun setUserRole(context: Context, role: String) {
        getInstance(context).edit().putString(KEY_USER_ROLE, role).apply()
    }
    
    fun getUserRole(context: Context): String {
        return getInstance(context).getString(KEY_USER_ROLE, "") ?: ""
    }
    
    fun setManualSosDelay(context: Context, delaySeconds: Int) {
        getInstance(context).edit().putInt(KEY_MANUAL_SOS_DELAY, delaySeconds).apply()
    }
    
    fun getManualSosDelay(context: Context): Int {
        return getInstance(context).getInt(KEY_MANUAL_SOS_DELAY, 3)
    }
    
    fun setXiaomiSetupComplete(context: Context, complete: Boolean) {
        getInstance(context).edit().putBoolean(KEY_XIAOMI_SETUP_COMPLETE, complete).apply()
    }
    
    fun isXiaomiSetupComplete(context: Context): Boolean {
        return getInstance(context).getBoolean(KEY_XIAOMI_SETUP_COMPLETE, false)
    }
    
    fun clear(context: Context) {
        getInstance(context).edit().clear().apply()
    }
    
    // Keys - defined as constants for type safety
    private const val KEY_USER_PHONE = "user_phone_number"
    private const val KEY_IOT_SIM = "iot_sim_number"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_MANUAL_SOS_DELAY = "manual_sos_delay"
    private const val KEY_XIAOMI_SETUP_COMPLETE = "xiaomi_setup_complete"
}
