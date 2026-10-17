package com.womensafety.app.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.womensafety.app.R
import com.womensafety.app.SOSManager
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Service for IoT SOS forwarding.
 * 
 * This service runs independently of the app UI and guarantees
 * SOS message forwarding even when:
 * - App is closed
 * - App is killed
 * - Screen is off
 * - Device is locked
 * - Do Not Disturb is on
 * 
 * Architecture:
 * 1. BroadcastReceiver detects IoT SMS → starts this service
 * 2. Service immediately calls startForeground() (within 5 sec window)
 * 3. Service forwards SOS to all emergency contacts
 * 4. Service stops itself after completion
 * 
 * No UI, no ViewModel, no Activity dependencies.
 */
class IoTSOSForwardingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "IoTSOSForwarding"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "iot_sos_forwarding"
        private const val FOREGROUND_SERVICE_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

        /**
         * Start the IoT SOS forwarding service
         */
        fun start(context: Context) {
            val intent = Intent(context, IoTSOSForwardingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "IoT SOS Forwarding Service started")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")

        // CRITICAL: Call startForeground() immediately (within 5 seconds)
        val notification = createOngoingNotification()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "✅ Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Acquire wake lock to keep CPU awake
        acquireWakeLock()

        // Forward SOS in background
        forwardSOSToContacts()

        // Service will stop itself after forwarding completes
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Create ongoing, silent notification for foreground service
     * - No sound, no vibration, no full-screen intent
     * - PUBLIC visibility (shows on lock screen)
     * - ONGOING (can't be dismissed)
     */
    private fun createOngoingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency SOS Forwarding")
            .setContentText("Forwarding emergency alert to contacts...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System icon
            .setPriority(NotificationCompat.PRIORITY_LOW) // Silent
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true) // Can't be dismissed
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Shows on lock screen
            .setSilent(true) // No sound or vibration
            .setAutoCancel(false)
            .build()
    }

    /**
     * Create notification channel (required for Android O+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IoT SOS Forwarding",
                NotificationManager.IMPORTANCE_LOW // Silent
            ).apply {
                description = "Background service for forwarding emergency alerts from IoT devices"
                setSound(null, null) // No sound
                enableVibration(false) // No vibration
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Acquire wake lock to keep CPU awake during forwarding
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WomenSafety:IoTSOSForwarding"
            ).apply {
                acquire(60_000L) // 60 seconds max
            }
            Log.d(TAG, "✅ Wake lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Forward SOS to all emergency contacts
     * This runs independently of the app UI
     */
    private fun forwardSOSToContacts() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting SOS forwarding...")

                // Get sender phone number
                val senderPhoneNumber = com.womensafety.app.data.SecurePreferences
                    .getUserPhoneNumber(this@IoTSOSForwardingService).trim()

                // Get emergency contacts from database
                val database = AppDatabase.getDatabase(this@IoTSOSForwardingService)
                val repository = ContactRepository(database.contactDao())
                val contacts = repository.getAllActiveContactsSync()

                if (contacts.isEmpty()) {
                    Log.w(TAG, "⚠️ No emergency contacts configured")
                    stopServiceSafely()
                    return@launch
                }

                Log.d(TAG, "Found ${contacts.size} emergency contacts")

                // Forward SOS using SOSManager
                val sosManager = SOSManager(this@IoTSOSForwardingService)
                val success = sosManager.sendSOSSAlerts(
                    contacts = contacts,
                    senderPhoneNumber = senderPhoneNumber
                )

                if (success) {
                    Log.d(TAG, "✅ SOS forwarded successfully to ${contacts.size} contacts")
                } else {
                    Log.e(TAG, "❌ Failed to forward SOS")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error forwarding SOS", e)
            } finally {
                // Always stop service after completion
                stopServiceSafely()
            }
        }
    }

    /**
     * Safely stop the service and release resources
     */
    private fun stopServiceSafely() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock", e)
        }

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Service stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopServiceSafely()
    }
}
