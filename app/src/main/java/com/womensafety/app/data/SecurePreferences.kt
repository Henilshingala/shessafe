package com.womensafety.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.womensafety.app.BuildConfig

/**
 * Secure storage manager using EncryptedSharedPreferences (AES-256 GCM via Android Keystore).
 *
 * SEC-01 / SEC-02 FIX: Keystore passwords removed from source code. See build.gradle.kts.
 * SEC-03 FIX: allowBackup="false" set in manifest — backup is disabled.
 *
 * Keys removed in Phase 1 redesign (obsolete):
 *  - KEY_IOT_SIM    (IoT feature removed)
 *  - KEY_USER_ROLE  (single-role app now)
 */
object SecurePreferences {

    private const val PREFS_FILE_NAME = "women_safety_secure_prefs"

    @Volatile private var encryptedPrefs: SharedPreferences? = null

    fun getInstance(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPreferences(context.applicationContext).also {
                encryptedPrefs = it
            }
        }
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.e("SecurePreferences", "Encrypted prefs failed, falling back", e)
            }
            // Fallback: plain SharedPreferences (should not happen on supported devices)
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    // ── Login / session ──────────────────────────────────────────────────────

    fun setLoggedIn(context: Context, loggedIn: Boolean) =
        getInstance(context).edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply()

    fun isLoggedIn(context: Context): Boolean =
        getInstance(context).getBoolean(KEY_IS_LOGGED_IN, false)

    fun setUserPhoneNumber(context: Context, phoneNumber: String) =
        getInstance(context).edit().putString(KEY_USER_PHONE, phoneNumber).apply()

    fun getUserPhoneNumber(context: Context): String =
        getInstance(context).getString(KEY_USER_PHONE, "") ?: ""

    fun setOtpVerificationId(context: Context, verificationId: String) =
        getInstance(context).edit().putString(KEY_OTP_VERIFICATION_ID, verificationId).apply()

    fun getOtpVerificationId(context: Context): String =
        getInstance(context).getString(KEY_OTP_VERIFICATION_ID, "") ?: ""

    // ── SOS settings ─────────────────────────────────────────────────────────

    fun setManualSosDelay(context: Context, delaySeconds: Int) =
        getInstance(context).edit().putInt(KEY_MANUAL_SOS_DELAY, delaySeconds).apply()

    fun getManualSosDelay(context: Context): Int =
        getInstance(context).getInt(KEY_MANUAL_SOS_DELAY, 5)

    // ── Feature toggles ───────────────────────────────────────────────────────

    /**
     * Phase 5: "I am safe" auto-SMS toggle.
     */
    fun setIAmSafeEnabled(context: Context, enabled: Boolean) =
        getInstance(context).edit().putBoolean(KEY_I_AM_SAFE_ENABLED, enabled).apply()

    fun isIAmSafeEnabled(context: Context): Boolean =
        getInstance(context).getBoolean(KEY_I_AM_SAFE_ENABLED, true)

    // ── OEM setup ─────────────────────────────────────────────────────────────

    fun setOemSetupShown(context: Context, shown: Boolean) =
        getInstance(context).edit().putBoolean(KEY_OEM_SETUP_SHOWN, shown).apply()

    fun isOemSetupShown(context: Context): Boolean =
        getInstance(context).getBoolean(KEY_OEM_SETUP_SHOWN, false)

    // Backwards-compatible aliases for XiaomiAutostartHelper
    fun setXiaomiSetupComplete(context: Context, complete: Boolean) = setOemSetupShown(context, complete)
    fun isXiaomiSetupComplete(context: Context): Boolean = isOemSetupShown(context)

    // ── Permissions checklist ────────────────────────────────────────────────

    fun setPermissionsChecklistComplete(context: Context, complete: Boolean) =
        getInstance(context).edit().putBoolean(KEY_PERMISSIONS_CHECKLIST_DONE, complete).apply()

    fun isPermissionsChecklistComplete(context: Context): Boolean =
        getInstance(context).getBoolean(KEY_PERMISSIONS_CHECKLIST_DONE, false)

    // ── Session clear (Logout) ────────────────────────────────────────────────

    /**
     * Clears session data (login flag, phone, OTP token).
     * Guardian list is stored in Room DB and cleared separately.
     */
    fun clearSession(context: Context) {
        getInstance(context).edit()
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_USER_PHONE)
            .remove(KEY_OTP_VERIFICATION_ID)
            .apply()
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    private const val KEY_IS_LOGGED_IN            = "is_logged_in"
    private const val KEY_USER_PHONE              = "user_phone_number"
    private const val KEY_OTP_VERIFICATION_ID     = "otp_verification_id"
    private const val KEY_MANUAL_SOS_DELAY        = "manual_sos_delay"
    private const val KEY_I_AM_SAFE_ENABLED       = "i_am_safe_enabled"
    private const val KEY_OEM_SETUP_SHOWN         = "oem_setup_shown"
    private const val KEY_PERMISSIONS_CHECKLIST_DONE = "permissions_checklist_done"
    // KEY_IOT_SIM  — REMOVED (Phase 1: IoT feature removed)
    // KEY_USER_ROLE — REMOVED (Phase 1: single-role app)
    // KEY_XIAOMI_SETUP_COMPLETE — migrated to KEY_OEM_SETUP_SHOWN
}
