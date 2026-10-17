package com.womensafety.app.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.womensafety.app.EmergencyAlertActivity
import com.womensafety.app.R

/**
 * Production-grade foreground service for emergency SOS alerts.
 * 
 * This service is designed to work reliably even when the app is closed or killed:
 * - Uses FOREGROUND_SERVICE_SPECIAL_USE type for Android 14+
 * - Acquires PARTIAL_WAKE_LOCK to keep CPU running
 * - Uses IMPORTANCE_MAX notification channel
 * - Plays alarm sound via EmergencyAlarmPlayer (USAGE_ALARM)
 * - Strong vibration pattern for 30-60 seconds
 * - Full-screen intent for lock screen visibility
 * 
 * Started by SOSAlertReceiver when an SOS SMS is detected.
 */
class SOSAlertService : Service() {

    private var alarmPlayer: EmergencyAlarmPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var durationMs: Long = 30_000L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SOSAlertService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - action: ${intent?.action}")
        
        // Handle STOP action
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop action received, stopping service")
            stopAlertAndService()
            return START_NOT_STICKY
        }

        try {
            // Get duration from intent (default 30 seconds)
            durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 30_000L) ?: 30_000L
            if (durationMs <= 0) durationMs = 30_000L
            
            Log.d(TAG, "Starting emergency alert for ${durationMs}ms")

            // Start foreground IMMEDIATELY (required for Android 14+)
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Acquire wake lock to keep CPU running
            acquireWakeLock()

            // Start alarm player with ALARM audio stream
            startAlarmPlayer()

            // Launch full-screen alert activity
            launchFullScreenAlert()

            return START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopAlertAndService()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SOSAlertService destroyed")
        stopAlarmPlayer()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:SOSAlertWakeLock"
            ).apply {
                // Acquire for duration + 5 seconds buffer
                acquire(durationMs + 5_000L)
                Log.d(TAG, "Wake lock acquired for ${durationMs + 5000}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private fun startAlarmPlayer() {
        try {
            stopAlarmPlayer()
            alarmPlayer = EmergencyAlarmPlayer(this).apply {
                startEmergencyAlert(
                    durationMs = durationMs,
                    onComplete = {
                        Log.d(TAG, "Emergency alert completed")
                        stopAlertAndService()
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm player", e)
        }
    }

    private fun stopAlarmPlayer() {
        try {
            alarmPlayer?.stop()
            alarmPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm player", e)
        }
    }

    private fun launchFullScreenAlert() {
        try {
            val fullScreenIntent = Intent(this, EmergencyAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                action = "com.womensafety.app.ACTION_SHOW_EMERGENCY_ALERT"
            }
            startActivity(fullScreenIntent)
            Log.d(TAG, "Full-screen alert activity launched")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch full-screen alert activity", e)
        }
    }

    private fun stopAlertAndService() {
        try {
            stopAlarmPlayer()
            releaseWakeLock()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }

    private fun buildNotification(): Notification {
        // Stop PendingIntent
        val stopIntent = Intent(this, SOSAlertService::class.java).apply { 
            action = ACTION_STOP 
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Full-screen intent for lock screen
        val fullScreenIntent = Intent(this, EmergencyAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            action = "com.womensafety.app.ACTION_SHOW_EMERGENCY_ALERT"
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 1, fullScreenIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 EMERGENCY SOS ALERT")
            .setContentText("Emergency alert is active - Sound and vibration playing")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("An emergency SOS message has been received. The alarm will play for ${durationMs/1000} seconds. Tap STOP to dismiss."))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(false)
            .addAction(0, "STOP ALERT", stopPending)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setSound(null) // No notification sound - we use AlarmManager
            .setVibrate(null) // No notification vibration - we handle it manually
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Emergency SOS Alerts",
            NotificationManager.IMPORTANCE_MAX // MAX importance for emergency
        ).apply {
            description = "Critical emergency alerts with alarm sound and vibration"
            enableVibration(false) // We handle vibration manually
            setSound(null, null) // We use ALARM stream, not notification sound
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created with IMPORTANCE_MAX")
    }

    companion object {
        private const val TAG = "SOSAlertService"
        const val CHANNEL_ID = "sos_alert_channel"
        const val NOTIFICATION_ID = 42001
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val ACTION_STOP = "com.womensafety.app.action.STOP_SOS_ALERT"
        
        /**
         * Start the emergency alert service from any context (e.g., BroadcastReceiver)
         */
        fun start(context: Context, durationMs: Long = 30_000L) {
            val intent = Intent(context, SOSAlertService::class.java).apply {
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Emergency alert service start requested")
        }
    }
}
