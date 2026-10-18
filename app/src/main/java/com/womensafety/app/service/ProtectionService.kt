package com.womensafety.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.womensafety.app.MainActivity
import com.womensafety.app.SOSManager
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps SMS/call monitoring alive.
 * Started after login and after device reboot.
 * Also handles Phase 4 periodic SOS follow-up updates (every 2 min for up to 30 min, >50m movement).
 */
class ProtectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var followUpJob: Job? = null
    private var lastSentLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "ProtectionService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SOS_FOLLOWUP -> {
                startFollowUpLoop()
            }
            ACTION_STOP_SOS_FOLLOWUP -> {
                stopFollowUpLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFollowUpLoop()
        serviceScope.cancel()
        Log.d(TAG, "ProtectionService destroyed")
    }

    private fun startFollowUpLoop() {
        followUpJob?.cancel()
        followUpJob = serviceScope.launch {
            Log.d(TAG, "Starting SOS location follow-up loop")
            val startTime = System.currentTimeMillis()
            val locationHelper = LocationHelper(this@ProtectionService)
            val database = AppDatabase.getDatabase(this@ProtectionService)
            val repository = ContactRepository(database.contactDao())
            val sosManager = SOSManager(this@ProtectionService)

            while (isActive && (System.currentTimeMillis() - startTime) < SOSManager.FOLLOWUP_DURATION_MS) {
                delay(SOSManager.FOLLOWUP_INTERVAL_MS)
                if (!isActive) break

                val locResult = locationHelper.getBestLocation(timeoutMs = 10_000L)
                val currentLoc = locResult.location

                if (currentLoc != null) {
                    val prevLoc = lastSentLocation
                    val hasMovedSignificantly = prevLoc == null ||
                            currentLoc.distanceTo(prevLoc) >= SOSManager.FOLLOWUP_MIN_DISTANCE_M

                    if (hasMovedSignificantly) {
                        lastSentLocation = currentLoc
                        val contacts = repository.getAllActiveContactsSync()
                        val senderPhone = SecurePreferences.getUserPhoneNumber(this@ProtectionService)
                        val locStr = locResult.toDisplayString()

                        Log.d(TAG, "Sending SOS location follow-up update")
                        contacts.forEach { contact ->
                            try {
                                val message = sosManager.buildFollowUpSosMessage(senderPhone, locStr)
                                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    getSystemService(android.telephony.SmsManager::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    android.telephony.SmsManager.getDefault()
                                }
                                val parts = smsManager.divideMessage(message)
                                smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send follow-up SMS", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "User has not moved >50m, skipping follow-up SMS")
                    }
                }
            }
            Log.d(TAG, "SOS location follow-up loop completed or cancelled")
        }
    }

    private fun stopFollowUpLoop() {
        followUpJob?.cancel()
        followUpJob = null
        lastSentLocation = null
        Log.d(TAG, "SOS follow-up loop stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Protection Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that Nirbhaya protection is active"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // SOS action from notification
        val sosIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent("com.womensafety.app.SOS_TRIGGER").setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Protection active")
            .setContentText("Nirbhaya is monitoring for emergencies")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_delete, "SEND SOS", sosIntent)
            .build()
    }

    companion object {
        private const val TAG = "ProtectionService"
        private const val CHANNEL_ID = "protection_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SOS_FOLLOWUP = "com.womensafety.app.action.START_SOS_FOLLOWUP"
        const val ACTION_STOP_SOS_FOLLOWUP = "com.womensafety.app.action.STOP_SOS_FOLLOWUP"

        fun start(context: Context) {
            val intent = Intent(context, ProtectionService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ProtectionService", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionService::class.java))
        }

        fun startSosFollowUp(context: Context) {
            val intent = Intent(context, ProtectionService::class.java).apply {
                action = ACTION_START_SOS_FOLLOWUP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SOS follow-up", e)
            }
        }

        fun stopSosFollowUp(context: Context) {
            val intent = Intent(context, ProtectionService::class.java).apply {
                action = ACTION_STOP_SOS_FOLLOWUP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SOS follow-up", e)
            }
        }
    }
}
