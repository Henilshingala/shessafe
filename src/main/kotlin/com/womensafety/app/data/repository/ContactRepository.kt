package com.womensafety.app.data.repository

import com.womensafety.app.data.dao.EmergencyContactDao
import com.womensafety.app.data.model.EmergencyContact
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: EmergencyContactDao) {
    fun getAllContacts(): Flow<List<EmergencyContact>> = contactDao.getAllContacts()

    fun getContactCount(): Flow<Int> = contactDao.getContactCount()

    suspend fun addContact(name: String, phoneNumber: String): Long {
        return contactDao.insert(
            EmergencyContact(
                name = name,
                phoneNumber = phoneNumber
            )
        )
    }

    suspend fun updateContact(id: Int, name: String, phoneNumber: String) {
        contactDao.update(
            EmergencyContact(
                id = id,
                name = name,
                phoneNumber = phoneNumber
            )
        )
    }

    suspend fun deleteContact(contact: EmergencyContact) {
        contactDao.delete(contact)
    }

    suspend fun getContactById(id: Int): EmergencyContact? {
        return contactDao.getContactById(id)
    }
}
