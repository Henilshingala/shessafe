package com.womensafety.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.womensafety.app.data.model.EmergencyContact
import com.womensafety.app.data.model.SOSState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SOSService : Service() {
    private val binder = SOSBinder()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _sosState = MutableStateFlow<SOSState>(SOSState.Idle)
    val sosState: StateFlow<SOSState> = _sosState

    private var cancelJob: Job? = null
    private var repeatJob: Job? = null

    inner class SOSBinder : Binder() {
        fun getService(): SOSService = this@SOSService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun triggerSOS(
        contacts: List<EmergencyContact>,
        cancelDelaySeconds: Int,
        repeatAlertCount: Int
    ) {
        scope.launch {
            // Start cancel window
            _sosState.value = SOSState.PendingCancel(cancelDelaySeconds)

            cancelJob = launch {
                for (i in cancelDelaySeconds downTo 1) {
                    _sosState.value = SOSState.PendingCancel(i)
                    delay(1000)
                }

                // Send first alert
                sendEmergencySMS(contacts)
                _sosState.value = SOSState.FirstAlert(System.currentTimeMillis())

                // Handle repeat alerts
                if (repeatAlertCount > 0) {
                    handleRepeatAlerts(contacts, repeatAlertCount)
                } else {
                    _sosState.value = SOSState.Resolved(System.currentTimeMillis())
                }
            }
        }
    }

    fun cancelAlert() {
        cancelJob?.cancel()
        _sosState.value = SOSState.Idle
    }

    fun markSafeNow() {
        cancelJob?.cancel()
        repeatJob?.cancel()
        _sosState.value = SOSState.Resolved(System.currentTimeMillis())
    }

    private suspend fun handleRepeatAlerts(
        contacts: List<EmergencyContact>,
        repeatAlertCount: Int
    ) {
        repeatJob = scope.launch {
            for (i in 1..repeatAlertCount) {
                delay(120000) // 2 minutes

                sendEmergencySMS(contacts)
                _sosState.value = SOSState.RepeatingAlerts(
                    alertsSent = i + 1,
                    totalAlerts = repeatAlertCount + 1,
                    nextAlertIn = if (i < repeatAlertCount) 120 else 0,
                    sentAt = System.currentTimeMillis()
                )
            }

            _sosState.value = SOSState.Resolved(System.currentTimeMillis())
        }
    }

    private fun sendEmergencySMS(contacts: List<EmergencyContact>) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "EMERGENCY ALERT: I may be in danger. Please check on me immediately."

            for (contact in contacts) {
                smsManager.sendTextMessage(
                    contact.phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.launch {
            cancelJob?.cancel()
            repeatJob?.cancel()
        }
    }
}
