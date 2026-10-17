package com.womensafety.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.SimpleOTPManager
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.data.models.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SimpleOTPState(
    val isActive: Boolean = false,
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class SimpleSettingsState(
    val sosDelay: Int = 5,
    val enableSound: Boolean = true,
    val enableVibration: Boolean = true,
    val userPhoneNumber: String = "",
    val iotSimNumber: String = "",
    val manualSosDelay: Int = 10,
    val userRole: String = "" // "sender" or "receiver"
)

class SimpleMainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ContactRepository(database.contactDao())
    private val otpManager = SimpleOTPManager(application)
    private val sosManager = SOSManager(application)
    private val prefs = application.getSharedPreferences("women_safety_prefs", Context.MODE_PRIVATE)
    
    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()
    
    private val _otpState = MutableStateFlow(SimpleOTPState())
    val otpState: StateFlow<SimpleOTPState> = _otpState.asStateFlow()
    
    private val _settingsState = MutableStateFlow(SimpleSettingsState())
    val settingsState: StateFlow<SimpleSettingsState> = _settingsState.asStateFlow()
    
    private var pendingContact: PendingContact? = null
    
    init {
        loadContacts()
        loadSettings()
    }
    
    // Validate that phone number is not already registered with a different role
    fun validatePhoneNumberForRole(phoneNumber: String, role: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.filter { it.isDigit() }.takeLast(10)
        
        // Get all registered phone numbers from SharedPreferences
        val registeredSenders = prefs.getStringSet("registered_senders", setOf()) ?: setOf()
        val registeredReceivers = prefs.getStringSet("registered_receivers", setOf()) ?: setOf()
        
        return when {
            role == "sender" && registeredReceivers.contains(cleanNumber) -> {
                Pair(false, "This phone number is already registered as a receiver. Cannot use same number for sender.")
            }
            role == "receiver" && registeredSenders.contains(cleanNumber) -> {
                Pair(false, "This phone number is already registered as a sender. Cannot use same number for receiver.")
            }
            else -> Pair(true, "")
        }
    }
    
    // Register phone number with role
    fun registerPhoneNumberWithRole(phoneNumber: String, role: String) {
        val cleanNumber = phoneNumber.filter { it.isDigit() }.takeLast(10)
        
        if (role == "sender") {
            val senders = prefs.getStringSet("registered_senders", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            senders.add(cleanNumber)
            prefs.edit().putStringSet("registered_senders", senders).apply()
        } else if (role == "receiver") {
            val receivers = prefs.getStringSet("registered_receivers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            receivers.add(cleanNumber)
            prefs.edit().putStringSet("registered_receivers", receivers).apply()
        }
    }
    
    private fun loadSettings() {
        val delay = prefs.getInt("sos_delay", 5)
        val sound = prefs.getBoolean("enable_sound", true)
        val vibration = prefs.getBoolean("enable_vibration", true)
        // Load sensitive data from SecurePreferences
        val phone = com.womensafety.app.data.SecurePreferences.getUserPhoneNumber(getApplication())
        val iotSimNumber = com.womensafety.app.data.SecurePreferences.getIotSimNumber(getApplication())
        val manualSosDelay = com.womensafety.app.data.SecurePreferences.getManualSosDelay(getApplication())
        val role = com.womensafety.app.data.SecurePreferences.getUserRole(getApplication())
        
        _settingsState.value = SimpleSettingsState(
            sosDelay = delay,
            enableSound = sound,
            enableVibration = vibration,
            userPhoneNumber = phone,
            iotSimNumber = iotSimNumber,
            manualSosDelay = manualSosDelay,
            userRole = role
        )
    }
    
    private fun loadContacts() {
        viewModelScope.launch {
            repository.getAllActiveContacts().collect { contactList ->
                _contacts.value = contactList
            }
        }
    }
    
    fun startOTPVerification(phoneNumber: String, name: String, relationship: String) {
        pendingContact = PendingContact(name, phoneNumber, relationship)
        
        _otpState.value = SimpleOTPState(
            isActive = true,
            phoneNumber = phoneNumber,
            isLoading = true
        )
        
        viewModelScope.launch {
            val otp = otpManager.generateAndSendOTP(phoneNumber)
            
            if (otp.isNotEmpty()) {
                _otpState.value = _otpState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
                Log.d("SimpleViewModel", "OTP sent successfully")
            } else {
                _otpState.value = _otpState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to send OTP. Please check your number and try again."
                )
            }
        }
    }
    
    fun verifyOTP(enteredOTP: String) {
        val currentState = _otpState.value
        
        _otpState.value = currentState.copy(isLoading = true)
        
        viewModelScope.launch {
            if (otpManager.verifyOTP(enteredOTP, currentState.phoneNumber)) {
                Log.d("SimpleViewModel", "OTP verified successfully")
                
                // Add the contact
                pendingContact?.let { contact ->
                    addContact(contact.name, contact.phoneNumber, contact.relationship)
                }
                
                cancelOTPVerification()
            } else {
                Log.d("SimpleViewModel", "OTP verification failed")
                _otpState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = "Invalid OTP. Please try again."
                )
            }
        }
    }
    
    fun resendOTP() {
        val phoneNumber = _otpState.value.phoneNumber
        val name = pendingContact?.name ?: ""
        val relationship = pendingContact?.relationship ?: ""
        
        Log.d("SimpleViewModel", "Resending OTP")
        startOTPVerification(phoneNumber, name, relationship)
    }
    
    fun cancelOTPVerification() {
        _otpState.value = SimpleOTPState()
        pendingContact = null
    }
    
    private suspend fun addContact(name: String, phone: String, relationship: String) {
        val contact = EmergencyContact(
            name = name,
            phoneNumber = phone,
            relationship = relationship
        )
        repository.insertContact(contact)
    }
    
    fun updateContact(contact: EmergencyContact) {
        viewModelScope.launch {
            repository.updateContact(contact)
        }
    }
    
    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }
    
    fun triggerSOS(): Boolean {
        val currentContacts = _contacts.value
        return if (currentContacts.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val settings = _settingsState.value
                    if (settings.manualSosDelay > 0) {
                        kotlinx.coroutines.delay(settings.manualSosDelay * 1000L)
                    }
                    
                    val success = sosManager.sendSOSSAlerts(
                        contacts = currentContacts, 
                        senderPhoneNumber = settings.userPhoneNumber
                    )
                    Log.d("SimpleViewModel", "SOS triggered with ${settings.manualSosDelay}s delay: $success")
                } catch (e: Exception) {
                    Log.e("SimpleViewModel", "Error triggering SOS", e)
                }
            }
            true
        } else {
            Log.d("SimpleViewModel", "No contacts available for SOS")
            false
        }
    }

    // Sender device must remain silent; keep this as a no-op for clarity
    private fun startLocalEmergencyAlert() { }
    
    // Settings functions
    fun updateSosDelay(delay: Int) {
        prefs.edit().putInt("sos_delay", delay).apply()
        _settingsState.value = _settingsState.value.copy(sosDelay = delay)
        Log.d("SimpleViewModel", "SOS delay updated to: $delay seconds")
    }
    
    fun updateSoundSetting(enabled: Boolean) {
        prefs.edit().putBoolean("enable_sound", enabled).apply()
        _settingsState.value = _settingsState.value.copy(enableSound = enabled)
        Log.d("SimpleViewModel", "Sound setting updated: $enabled")
    }
    
    fun updateVibrationSetting(enabled: Boolean) {
        prefs.edit().putBoolean("enable_vibration", enabled).apply()
        _settingsState.value = _settingsState.value.copy(enableVibration = enabled)
        Log.d("SimpleViewModel", "Vibration setting updated: $enabled")
    }
    
    fun updateUserPhoneNumber(number: String) {
        com.womensafety.app.data.SecurePreferences.setUserPhoneNumber(getApplication(), number)
        _settingsState.value = _settingsState.value.copy(userPhoneNumber = number)
        Log.d("SimpleViewModel", "User phone number updated (encrypted)")
    }

    fun updateIotSimNumber(number: String) {
        com.womensafety.app.data.SecurePreferences.setIotSimNumber(getApplication(), number)
        _settingsState.value = _settingsState.value.copy(iotSimNumber = number)
        Log.d("SimpleViewModel", "IoT SIM number updated (encrypted): '$number'")
    }

    fun updateManualSosDelay(delay: Int) {
        com.womensafety.app.data.SecurePreferences.setManualSosDelay(getApplication(), delay)
        _settingsState.value = _settingsState.value.copy(manualSosDelay = delay)
        Log.d("SimpleViewModel", "Manual SOS delay updated to: $delay seconds (encrypted)")
    }

    fun setUserRole(role: String) {
        com.womensafety.app.data.SecurePreferences.setUserRole(getApplication(), role)
        _settingsState.value = _settingsState.value.copy(userRole = role)
        Log.d("SimpleViewModel", "User role set to: $role (encrypted)")
    }

    fun isOnboardingComplete(): Boolean {
        val role = _settingsState.value.userRole
        val phone = _settingsState.value.userPhoneNumber
        
        if (role.isEmpty()) return false
        if (phone.isEmpty()) return false
        
        return if (role == "sender") {
            _settingsState.value.iotSimNumber.isNotEmpty()
        } else {
            true
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Save settings on clear just in case, though we save on update now
    }
    
    data class PendingContact(
        val name: String,
        val phoneNumber: String,
        val relationship: String
    )
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
