package com.womensafety.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.womensafety.app.data.ContactRepository
import com.womensafety.app.data.OTPManager
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.data.models.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class SettingsState(
    val sosDelay: Int = 5,
    val enableSound: Boolean = true,
    val enableVibration: Boolean = true
)

data class OTPState(
    val isActive: Boolean = false,
    val phoneNumber: String = "",
    val otp: String = "",
    val timeRemaining: Int = 0,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ContactRepository(database.contactDao())
    private val otpManager = OTPManager(application)
    
    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()
    
    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()
    
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Main)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
    private val _showAddContactDialog = MutableStateFlow(false)
    val showAddContactDialog: StateFlow<Boolean> = _showAddContactDialog.asStateFlow()
    
    private val _otpState = MutableStateFlow(OTPState())
    val otpState: StateFlow<OTPState> = _otpState.asStateFlow()
    
    private var pendingContact: PendingContact? = null
    
    init {
        loadContacts()
    }
    
    private fun loadContacts() {
        viewModelScope.launch {
            repository.getAllActiveContacts().collect { contactList ->
                _contacts.value = contactList
            }
        }
    }
    
    fun navigateToScreen(screen: Screen) {
        _currentScreen.value = screen
    }
    
    fun showAddContactDialog() {
        _showAddContactDialog.value = true
    }
    
    fun hideAddContactDialog() {
        _showAddContactDialog.value = false
    }
    
    fun addContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            val contact = EmergencyContact(
                name = name,
                phoneNumber = phone,
                relationship = relationship
            )
            repository.insertContact(contact)
        }
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
    
    suspend fun hasActiveContacts(): Boolean {
        return repository.hasActiveContacts()
    }
    
    suspend fun getActiveContactPhoneNumbers(): List<String> {
        return repository.getActiveContactPhoneNumbers()
    }
    
    fun updateSettings(newSettings: SettingsState) {
        _settings.value = newSettings
    }
    
    fun updateSosDelay(delay: Int) {
        _settings.value = _settings.value.copy(sosDelay = delay)
    }
    
    fun updateSoundSetting(enabled: Boolean) {
        _settings.value = _settings.value.copy(enableSound = enabled)
    }
    
    fun updateVibrationSetting(enabled: Boolean) {
        _settings.value = _settings.value.copy(enableVibration = enabled)
    }
    
    // OTP Functions
    fun startOTPVerification(phoneNumber: String, name: String, relationship: String) {
        pendingContact = PendingContact(name, phoneNumber, relationship)
        val otp = otpManager.generateOTP()
        
        Log.d("MainViewModel", "Starting OTP verification")
        
        _otpState.value = OTPState(
            isActive = true,
            phoneNumber = phoneNumber,
            otp = otp,
            timeRemaining = 300, // 5 minutes in seconds
            isLoading = true
        )
        
        viewModelScope.launch {
            val success = otpManager.sendOTP(phoneNumber, otp)
            _otpState.value = _otpState.value.copy(
                isLoading = false,
                errorMessage = if (!success) "Failed to send OTP. Please try again." else null
            )
            
            if (success) {
                startCountdown()
            }
        }
    }
    
    private fun startCountdown() {
        viewModelScope.launch {
            while (_otpState.value.timeRemaining > 0) {
                delay(1000)
                _otpState.value = _otpState.value.copy(
                    timeRemaining = _otpState.value.timeRemaining - 1
                )
            }
        }
    }
    
    fun verifyOTP(enteredOTP: String) {
        val currentState = _otpState.value
        
        Log.d("MainViewModel", "Verifying OTP")
        
        // Check if OTP has expired (timeRemaining <= 0)
        if (currentState.timeRemaining <= 0) {
            _otpState.value = currentState.copy(
                errorMessage = "OTP has expired. Please request a new OTP."
            )
            return
        }
        
        _otpState.value = currentState.copy(isLoading = true)
        
        viewModelScope.launch {
            if (otpManager.validateOTP(enteredOTP, currentState.otp)) {
                Log.d("MainViewModel", "OTP validation successful")
                // OTP verified, add contact
                pendingContact?.let { contact ->
                    addContact(contact.name, contact.phoneNumber, contact.relationship)
                }
                cancelOTPVerification()
            } else {
                Log.d("MainViewModel", "OTP validation failed")
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
        
        Log.d("MainViewModel", "Resending OTP")
        
        // Generate new OTP and restart the process
        startOTPVerification(phoneNumber, name, relationship)
    }
    
    fun cancelOTPVerification() {
        _otpState.value = OTPState()
        pendingContact = null
    }
    
    data class PendingContact(
        val name: String,
        val phoneNumber: String,
        val relationship: String
    )
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

sealed class Screen {
    object Main : Screen()
    object Contacts : Screen()
    object Settings : Screen()
    object OTPVerification : Screen()
}
