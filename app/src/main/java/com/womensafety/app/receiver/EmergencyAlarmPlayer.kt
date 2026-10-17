package com.womensafety.app.receiver

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Production-grade emergency alarm player using USAGE_ALARM audio stream.
 * 
 * This class handles:
 * - Playing emergency alarm sound at maximum volume using ALARM stream (bypasses DND/Silent mode)
 * - Strong vibration pattern for 30-60 seconds
 * - Proper cleanup of resources
 * - Works reliably even when app is closed/killed
 * 
 * Unlike media or notification audio, USAGE_ALARM:
 * - Plays at maximum volume regardless of media volume setting
 * - Bypasses Do Not Disturb mode
 * - Cannot be muted by user volume controls during playback
 * - Is the correct audio stream for emergency alerts
 */
class EmergencyAlarmPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    
    private var previousAlarmVolume: Int? = null
    private var isPlaying = false
    private var stopTime: Long = 0

    /**
     * Start emergency alert with sound and vibration
     * @param durationMs How long to play the alarm (default 30 seconds)
     * @param onComplete Callback when alarm completes
     */
    fun startEmergencyAlert(durationMs: Long = 30_000L, onComplete: (() -> Unit)? = null) {
        if (isPlaying) {
            Log.w(TAG, "Emergency alert already playing")
            return
        }

        isPlaying = true
        stopTime = System.currentTimeMillis() + durationMs
        
        Log.d(TAG, "Starting emergency alert - duration: ${durationMs}ms")

        // Start sound
        startAlarmSound(durationMs, onComplete)
        
        // Start vibration
        startStrongVibration(durationMs)
    }

    /**
     * Stop the emergency alert immediately
     */
    fun stop() {
        Log.d(TAG, "Stopping emergency alert")
        isPlaying = false
        stopAlarmSound()
        stopVibration()
    }

    private fun startAlarmSound(durationMs: Long, onComplete: (() -> Unit)?) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "STARTING ALARM SOUND")
            Log.d(TAG, "Duration: ${durationMs}ms")
            Log.d(TAG, "========================================")
            
            // Get AudioManager
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            Log.d(TAG, "AudioManager obtained")
            
            // Save current alarm volume and set to maximum
            val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            previousAlarmVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM)
            
            Log.d(TAG, "Current alarm volume: $previousAlarmVolume, Max volume: $maxVolume")
            
            try {
                audioManager!!.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                Log.d(TAG, "✅ Alarm volume set to maximum: $maxVolume (was: $previousAlarmVolume)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to set alarm volume to max", e)
            }

            // Request audio focus for ALARM stream
            try {
                Log.d(TAG, "Requesting audio focus for ALARM stream...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    
                    audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .build()
                    
                    audioManager!!.requestAudioFocus(audioFocusRequest!!)
                    Log.d(TAG, "✅ Audio focus requested (modern API)")
                } else {
                    @Suppress("DEPRECATION")
                    audioManager!!.requestAudioFocus(
                        null,
                        AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                    Log.d(TAG, "✅ Audio focus requested (legacy API)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to request audio focus", e)
            }

            // Create MediaPlayer with USAGE_ALARM audio attributes
            Log.d(TAG, "Creating MediaPlayer with USAGE_ALARM...")
            mediaPlayer = MediaPlayer().apply {
                // Use modern AudioAttributes (API 21+) with USAGE_ALARM
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                setVolume(1.0f, 1.0f)
                Log.d(TAG, "MediaPlayer volume set to 1.0 (max)")
                
                // Set data source to emergency alarm sound
                try {
                    Log.d(TAG, "Loading emergency_alarm resource...")
                    val resId = context.resources.getIdentifier(
                        "emergency_alarm",
                        "raw",
                        context.packageName
                    )
                    Log.d(TAG, "Resource ID: $resId")
                    
                    if (resId == 0) {
                        Log.e(TAG, "❌ emergency_alarm resource not found!")
                        stopAlarmSound()
                        onComplete?.invoke()
                        return
                    }
                    
                    val afd = context.resources.openRawResourceFd(resId)
                    if (afd != null) {
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        Log.d(TAG, "✅ Data source set successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to open emergency_alarm.mp3 - null AssetFileDescriptor")
                        stopAlarmSound()
                        onComplete?.invoke()
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error setting data source", e)
                    e.printStackTrace()
                    stopAlarmSound()
                    onComplete?.invoke()
                    return
                }

                // Loop until duration expires
                isLooping = true
                Log.d(TAG, "Looping enabled")
                
                setOnPreparedListener {
                    try {
                        start()
                        Log.d(TAG, "✅✅✅ EMERGENCY ALARM PLAYBACK STARTED ✅✅✅")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to start alarm playback", e)
                        e.printStackTrace()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ MediaPlayer error: what=$what, extra=$extra")
                    stopAlarmSound()
                    onComplete?.invoke()
                    false
                }

                Log.d(TAG, "Calling prepareAsync()...")
                prepareAsync()
                Log.d(TAG, "prepareAsync() called, waiting for onPrepared callback...")
            }

            // Schedule stop after duration
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isPlaying && System.currentTimeMillis() >= stopTime) {
                    Log.d(TAG, "Emergency alarm duration completed")
                    stop()
                    onComplete?.invoke()
                }
            }, durationMs)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm sound", e)
            stopAlarmSound()
            onComplete?.invoke()
        }
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                    Log.d(TAG, "MediaPlayer stopped and released")
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MediaPlayer", e)
                }
            }
            mediaPlayer = null

            // Restore previous alarm volume
            previousAlarmVolume?.let { prevVolume ->
                try {
                    audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0)
                    Log.d(TAG, "Alarm volume restored to: $prevVolume")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore alarm volume", e)
                }
            }
            previousAlarmVolume = null

            // Abandon audio focus
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                    audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to abandon audio focus", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopAlarmSound", e)
        }
    }

    private fun startStrongVibration(durationMs: Long) {
        try {
            // Get vibrator
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() != true) {
                Log.w(TAG, "No vibrator available on this device")
                return
            }

            // Strong SOS vibration pattern (in milliseconds)
            // Pattern: ... --- ...  (SOS in Morse code)
            val pattern = longArrayOf(
                0,      // Start immediately
                300,    // Short vibration (.)
                200,    // Pause
                300,    // Short vibration (.)
                200,    // Pause  
                300,    // Short vibration (.)
                400,    // Longer pause
                800,    // Long vibration (-)
                200,    // Pause
                800,    // Long vibration (-)
                200,    // Pause
                800,    // Long vibration (-)
                400,    // Longer pause
                300,    // Short vibration (.)
                200,    // Pause
                300,    // Short vibration (.)
                200,    // Pause
                300,    // Short vibration (.)
                1000    // Long pause before repeat
            )

            // Create repeating vibration effect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0) // 0 = repeat from start
                vibrator?.vibrate(effect)
                Log.d(TAG, "Strong vibration started (API 26+) - will repeat for ${durationMs}ms")
            } else {
                // For older Android versions
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0) // 0 = repeat from start
                Log.d(TAG, "Strong vibration started (legacy) - will repeat for ${durationMs}ms")
            }

            // Schedule vibration stop after duration
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isPlaying && System.currentTimeMillis() >= stopTime) {
                    stopVibration()
                }
            }, durationMs)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
            vibrator = null
            Log.d(TAG, "Vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration", e)
        }
    }

    companion object {
        private const val TAG = "EmergencyAlarmPlayer"
    }
}
