package com.womensafety.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.womensafety.app.auth.OtpProvider
import com.womensafety.app.auth.OtpResult
import com.womensafety.app.auth.StubOtpProvider
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.SecurePreferences
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.data.models.EmergencyContact
import com.womensafety.app.util.PhoneUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── OTP State ─────────────────────────────────────────────────────────────────

enum class OtpStep { ENTER_PHONE, ENTER_OTP, DONE }

data class LoginState(
    val step: OtpStep = OtpStep.ENTER_PHONE,
    val phoneNumber: String = "",        // E.164 format
    val verificationId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resendCountdownSeconds: Int = 0  // 0 = can resend
)

// ── Settings State ────────────────────────────────────────────────────────────

data class SettingsState(
    val userPhoneNumber: String = "",
    val manualSosDelay: Int = 5,
    val iAmSafeEnabled: Boolean = true
)

class SimpleMainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ContactRepository(database.contactDao())
    private val otpProvider: OtpProvider = StubOtpProvider()

    // ── Login ─────────────────────────────────────────────────────────────────

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private var resendTimerJob: Job? = null

    /** True if the user has already completed login (phone verified + flag stored). */
    val isLoggedIn: Boolean
        get() = SecurePreferences.isLoggedIn(getApplication())

    // ── Contacts / Guardians ───────────────────────────────────────────────────

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    // ── Settings ───────────────────────────────────────────────────────────────

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    // ── SOS Status ─────────────────────────────────────────────────────────────

    sealed class SosStatus {
        object Idle : SosStatus()
        data class Countdown(val secondsLeft: Int) : SosStatus()
        data class Sending(val sentCount: Int, val totalCount: Int) : SosStatus()
        data class Done(val sentCount: Int, val totalCount: Int) : SosStatus()
        data class Failed(val failedCount: Int) : SosStatus()
    }

    private val _sosStatus = MutableStateFlow<SosStatus>(SosStatus.Idle)
    val sosStatus: StateFlow<SosStatus> = _sosStatus.asStateFlow()

    init {
        loadContacts()
        loadSettings()
    }

    // ── Login methods ─────────────────────────────────────────────────────────

    fun sendOtp(rawPhoneNumber: String) {
        val e164 = PhoneUtil.toE164(rawPhoneNumber)
        if (e164 == null) {
            _loginState.value = _loginState.value.copy(
                errorMessage = "Please enter a valid phone number with country code."
            )
            return
        }
        _loginState.value = LoginState(
            step = OtpStep.ENTER_PHONE,
            phoneNumber = e164,
            isLoading = true
        )
        viewModelScope.launch {
            when (val result = otpProvider.sendOtp(e164)) {
                is OtpResult.Success -> {
                    // Store verificationId in SecurePreferences for resilience
                    SecurePreferences.setOtpVerificationId(getApplication(), result.verificationId)
                    _loginState.value = LoginState(
                        step = OtpStep.ENTER_OTP,
                        phoneNumber = e164,
                        verificationId = result.verificationId,
                        resendCountdownSeconds = 30
                    )
                    startResendTimer()
                }
                is OtpResult.Error -> {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun verifyOtp(otp: String) {
        val state = _loginState.value
        _loginState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = otpProvider.verifyOtp(state.verificationId, otp)) {
                is OtpResult.Success -> {
                    SecurePreferences.setUserPhoneNumber(getApplication(), state.phoneNumber)
                    SecurePreferences.setLoggedIn(getApplication(), true)
                    _loginState.value = LoginState(step = OtpStep.DONE)
                    _settingsState.value = _settingsState.value.copy(
                        userPhoneNumber = state.phoneNumber
                    )
                }
                is OtpResult.Error -> {
                    _loginState.value = state.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun resendOtp() {
        if (_loginState.value.resendCountdownSeconds > 0) return
        sendOtp(_loginState.value.phoneNumber)
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            var remaining = 30
            while (remaining > 0) {
                _loginState.value = _loginState.value.copy(resendCountdownSeconds = remaining)
                delay(1000)
                remaining--
            }
            _loginState.value = _loginState.value.copy(resendCountdownSeconds = 0)
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Clear all guardians
            repository.deleteAllContacts()
            // Clear session
            SecurePreferences.clearSession(getApplication())
            // Reset state
            _loginState.value = LoginState()
            _contacts.value = emptyList()
        }
    }

    // ── Guardian / contact methods ────────────────────────────────────────────

    private fun loadContacts() {
        viewModelScope.launch {
            repository.getAllActiveContacts().collect { contactList ->
                _contacts.value = contactList
            }
        }
    }

    /**
     * Add a guardian. Returns an error string if validation fails, null on success.
     */
    suspend fun addGuardian(name: String, rawPhone: String): String? {
        val userPhone = SecurePreferences.getUserPhoneNumber(getApplication())
        val e164 = PhoneUtil.toE164(rawPhone)
            ?: return "Invalid phone number. Please include country code."

        if (PhoneUtil.isSameNumber(e164, userPhone))
            return "You cannot add your own number as a guardian."

        if (_contacts.value.size >= 5)
            return "You can add at most 5 guardians."

        val duplicate = _contacts.value.any { PhoneUtil.isSameNumber(e164, it.phoneNumber) }
        if (duplicate)
            return "This number is already added as a guardian."

        val contact = EmergencyContact(
            name = name.trim(),
            phoneNumber = e164,
            relationship = "Guardian"
        )
        repository.insertContact(contact)
        return null
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch { repository.deleteContact(contact) }
    }

    fun updateContact(contact: EmergencyContact) {
        viewModelScope.launch { repository.updateContact(contact) }
    }

    // ── SOS methods ────────────────────────────────────────────────────────────

    /**
     * Trigger SOS with optional delay. Shows countdown, then dispatches SMS.
     * BUG-05 FIX: SosStatus only moves to Done/Sent AFTER radio confirms delivery.
     */
    fun triggerSOS() {
        val currentContacts = _contacts.value
        if (currentContacts.isEmpty()) return

        viewModelScope.launch {
            val delaySeconds = _settingsState.value.manualSosDelay
            if (delaySeconds > 0) {
                var remaining = delaySeconds
                while (remaining > 0) {
                    _sosStatus.value = SosStatus.Countdown(remaining)
                    delay(1000)
                    remaining--
                }
            }

            _sosStatus.value = SosStatus.Sending(0, currentContacts.size)
            val context = getApplication<Application>()
            val locationHelper = com.womensafety.app.util.LocationHelper(context)
            val locationResult = locationHelper.getBestLocation(timeoutMs = 4000L)
            val locationStr = locationResult.toDisplayString()

            val sosManager = com.womensafety.app.SOSManager(context)
            sosManager.sendSosAlerts(
                contacts = currentContacts,
                userLocation = locationStr,
                senderPhoneNumber = _settingsState.value.userPhoneNumber,
                onProgress = { sent, total ->
                    _sosStatus.value = SosStatus.Sending(sent, total)
                },
                onComplete = { sent, total ->
                    _sosStatus.value = if (sent == 0) SosStatus.Failed(total)
                                       else SosStatus.Done(sent, total)
                    if (sent > 0) {
                        com.womensafety.app.service.ProtectionService.startSosFollowUp(context)
                    }
                }
            )
        }
    }

    fun cancelSOS() {
        _sosStatus.value = SosStatus.Idle
        com.womensafety.app.service.ProtectionService.stopSosFollowUp(getApplication())
    }

    // ── Settings ───────────────────────────────────────────────────────────────

    private fun loadSettings() {
        val phone = SecurePreferences.getUserPhoneNumber(getApplication())
        val delay = SecurePreferences.getManualSosDelay(getApplication())
        val iAmSafe = SecurePreferences.isIAmSafeEnabled(getApplication())
        _settingsState.value = SettingsState(
            userPhoneNumber = phone,
            manualSosDelay = delay,
            iAmSafeEnabled = iAmSafe
        )
    }

    fun updateManualSosDelay(delay: Int) {
        SecurePreferences.setManualSosDelay(getApplication(), delay)
        _settingsState.value = _settingsState.value.copy(manualSosDelay = delay)
    }

    fun updateIAmSafeEnabled(enabled: Boolean) {
        SecurePreferences.setIAmSafeEnabled(getApplication(), enabled)
        _settingsState.value = _settingsState.value.copy(iAmSafeEnabled = enabled)
    }
}

class SimpleMainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SimpleMainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SimpleMainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
