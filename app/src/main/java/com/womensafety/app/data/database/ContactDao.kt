package com.womensafety.app.data.database

import androidx.room.*
import com.womensafety.app.data.models.EmergencyContact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM emergency_contacts WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveContacts(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllActiveContactsSync(): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    suspend fun getContactById(id: Long): EmergencyContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContact): Long

    @Update
    suspend fun updateContact(contact: EmergencyContact)

    @Delete
    suspend fun deleteContact(contact: EmergencyContact)

    @Query("UPDATE emergency_contacts SET isActive = :isActive WHERE id = :id")
    suspend fun updateContactStatus(id: Long, isActive: Boolean)
}
