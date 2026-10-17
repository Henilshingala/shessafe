package com.womensafety.app.data.model

sealed class SOSState {
    object Idle : SOSState()
    data class PendingCancel(val remainingSeconds: Int) : SOSState()
    data class FirstAlert(val sentAt: Long) : SOSState()
    data class RepeatingAlerts(
        val alertsSent: Int,
        val totalAlerts: Int,
        val nextAlertIn: Int,
        val sentAt: Long
    ) : SOSState()
    data class Resolved(val resolvedAt: Long) : SOSState()
}

data class AppSettings(
    val cancelDelaySeconds: Int = 10, // 10, 20, or 30
    val repeatAlertCount: Int = 0, // 0-5
    val repeatIntervalSeconds: Int = 120, // 2 minutes
    val onboardingComplete: Boolean = false,
    val smsPermissionGranted: Boolean = false
)
