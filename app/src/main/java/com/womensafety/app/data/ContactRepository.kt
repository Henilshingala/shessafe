package com.womensafety.app.data

import com.womensafety.app.data.database.ContactDao
import com.womensafety.app.data.models.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ContactRepository(private val contactDao: ContactDao) {

    fun getAllActiveContacts(): Flow<List<EmergencyContact>> {
        return contactDao.getAllActiveContacts()
    }

    suspend fun getAllActiveContactsSync(): List<EmergencyContact> {
        return withContext(Dispatchers.IO) {
            contactDao.getAllActiveContactsSync()
        }
    }

    suspend fun insertContact(contact: EmergencyContact): Long {
        return withContext(Dispatchers.IO) {
            try {
                contactDao.insertContact(contact)
            } catch (e: Exception) {
                android.util.Log.e("ContactRepository", "Error inserting contact", e)
                -1L
            }
        }
    }

    suspend fun updateContact(contact: EmergencyContact) {
        withContext(Dispatchers.IO) {
            try {
                contactDao.updateContact(contact)
            } catch (e: Exception) {
                android.util.Log.e("ContactRepository", "Error updating contact", e)
            }
        }
    }

    suspend fun deleteContact(contact: EmergencyContact) {
        withContext(Dispatchers.IO) {
            try {
                contactDao.deleteContact(contact)
            } catch (e: Exception) {
                android.util.Log.e("ContactRepository", "Error deleting contact", e)
            }
        }
    }

    suspend fun hasActiveContacts(): Boolean {
        return withContext(Dispatchers.IO) {
            contactDao.getAllActiveContactsSync().isNotEmpty()
        }
    }

    suspend fun getActiveContactPhoneNumbers(): List<String> {
        return withContext(Dispatchers.IO) {
            contactDao.getAllActiveContactsSync().map { it.phoneNumber }
        }
    }
}
