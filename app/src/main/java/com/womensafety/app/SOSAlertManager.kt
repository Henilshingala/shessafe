package com.womensafety.app

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*

class SOSAlertManager(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var soundJob: Job? = null
    private var vibrationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val audioManager: AudioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var previousAlarmVolume: Int? = null
    
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    fun playEmergencyAlert(enableSound: Boolean = true, enableVibration: Boolean = true) {
        Log.d("SOSAlertManager", "Playing emergency alert - Sound: $enableSound, Vibration: $enableVibration")
        
        if (enableSound) {
            playBuzzerSound()
        }
        
        if (enableVibration) {
            startStrongVibration()
        }
    }
    
    private fun playBuzzerSound() {
        try {
            stopBuzzerSound()
            startTime = System.currentTimeMillis()
            
            try {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            } catch (e: Exception) {
                Log.w("SOSAlertManager", "Failed to set alarm volume", e)
            }

            try {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            } catch (e: Exception) {
                Log.w("SOSAlertManager", "Failed to request audio focus", e)
            }

            mediaPlayer = MediaPlayer().apply {
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setVolume(1.0f, 1.0f)
                setDataSource(context, android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.emergency_alarm}"))
                setOnPreparedListener {
                    try {
                        start()
                        Log.d("SOSAlertManager", "Emergency alarm started")
                    } catch (e: Exception) {
                        Log.e("SOSAlertManager", "Failed to start playback", e)
                    }
                }
                setOnCompletionListener {
                    if (System.currentTimeMillis() < (startTime + 30000L)) {
                        try {
                            start()
                        } catch (e: Exception) {
                            Log.e("SOSAlertManager", "Failed to restart playback", e)
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("SOSAlertManager", "MediaPlayer error: $what, $extra")
                    false
                }
                prepareAsync()
            }
            
            soundJob = scope.launch {
                delay(30000L)
                stopBuzzerSound()
                Log.d("SOSAlertManager", "Emergency alarm stopped after 30 seconds")
            }
            
        } catch (e: Exception) {
            Log.e("SOSAlertManager", "Error playing emergency alarm", e)
        }
    }
    
    private var startTime = 0L
    
    private fun stopBuzzerSound() {
        try {
            soundJob?.cancel()
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            mediaPlayer = null
            // Restore previous alarm volume
            previousAlarmVolume?.let { prev ->
                try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, prev, 0) } catch (_: Exception) {}
            }
            previousAlarmVolume = null
        } catch (e: Exception) {
            Log.e("SOSAlertManager", "Error stopping alarm", e)
        }
    }
    
    private fun startStrongVibration() {
        vibrationJob?.cancel()
        
        vibrationJob = scope.launch {
            try {
                // Strong SOS vibration pattern - repeating for 30 seconds
                val vibrationPattern = longArrayOf(
                    0,      // Start immediately
                    800,    // Vibrate for 800ms
                    200,    // Pause 200ms
                    800,    // Vibrate for 800ms
                    200,    // Pause 200ms
                    800,    // Vibrate for 800ms
                    500,    // Longer pause
                    400,    // Short vibrate
                    200,    // Pause
                    400,    // Short vibrate
                    1000    // Long pause before repeat
                )
                
                // Repeat pattern for 30 seconds total
                val endTime = System.currentTimeMillis() + 30000L
                
                while (System.currentTimeMillis() < endTime && isActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(vibrationPattern, -1)
                    }
                    
                    delay(6000) // Wait for pattern to complete, then repeat
                }
                
                Log.d("SOSAlertManager", "Strong vibration completed")
            } catch (e: Exception) {
                Log.e("SOSAlertManager", "Error during vibration", e)
            }
        }
    }
    
    fun stopAlert() {
        Log.d("SOSAlertManager", "Stopping emergency alert")
        
        try {
            stopBuzzerSound()
            
            vibrationJob?.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                vibrator?.cancel()
            }
        } catch (e: Exception) {
            Log.e("SOSAlertManager", "Error stopping alert", e)
        }
    }
    
    fun cleanup() {
        try {
            stopAlert()
            scope.cancel()
        } catch (e: Exception) {
            Log.e("SOSAlertManager", "Error during cleanup", e)
        }
    }
}
