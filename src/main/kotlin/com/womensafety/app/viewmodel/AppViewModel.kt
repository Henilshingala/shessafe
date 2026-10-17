package com.womensafety.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.app.data.database.AppDatabase
import com.womensafety.app.data.model.EmergencyContact
import com.womensafety.app.data.model.SOSState
import com.womensafety.app.data.repository.ContactRepository
import com.womensafety.app.data.repository.SettingsRepository
import com.womensafety.app.service.SOSService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val contactRepository = ContactRepository(database.emergencyContactDao())
    private val settingsRepository = SettingsRepository(context)

    // Contacts
    val contacts = contactRepository.getAllContacts()
    val contactCount = contactRepository.getContactCount()

    // Settings
    val cancelDelaySeconds = settingsRepository.cancelDelaySeconds
    val repeatAlertCount = settingsRepository.repeatAlertCount
    val isOnboardingComplete = settingsRepository.onboardingComplete
    val smsPermissionGranted = settingsRepository.smsPermissionGranted

    // SOS State
    private val _sosState = MutableStateFlow<SOSState>(SOSState.Idle)
    val sosState: StateFlow<SOSState> = _sosState.asStateFlow()

    private var sosService: SOSService? = null

    init {
        // Bind to SOS Service
        bindSOSService()
    }

    private fun bindSOSService() {
        val intent = Intent(context, SOSService::class.java)
        context.startService(intent)
    }

    fun checkOnboardingStatus() {
        viewModelScope.launch {
            // Check if onboarding is complete
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingComplete(true)
        }
    }

    fun setSmsPermissionGranted(granted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmsPermissionGranted(granted)
        }
    }

    fun setCancelDelay(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setCancelDelay(seconds)
        }
    }

    fun setRepeatAlertCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.setRepeatAlertCount(count)
        }
    }

    fun addContact(name: String, phoneNumber: String) {
        viewModelScope.launch {
            contactRepository.addContact(name, phoneNumber)
        }
    }

    fun updateContact(id: Int, name: String, phoneNumber: String) {
        viewModelScope.launch {
            contactRepository.updateContact(id, name, phoneNumber)
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
        }
    }

    fun triggerSOS() {
        viewModelScope.launch {
            val allContacts = mutableListOf<EmergencyContact>()
            contactRepository.getAllContacts().collect { contacts ->
                allContacts.addAll(contacts)
            }

            val cancelDelay = settingsRepository.cancelDelaySeconds.collect { delay ->
                val repeatCount = settingsRepository.repeatAlertCount.collect { count ->
                    sosService?.triggerSOS(allContacts, delay, count)
                }
            }
        }
    }

    fun cancelSOS() {
        sosService?.cancelAlert()
        _sosState.value = SOSState.Idle
    }

    fun markSafeNow() {
        sosService?.markSafeNow()
    }
}
