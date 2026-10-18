package com.womensafety.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.womensafety.app.data.models.EmergencyContact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class SOSManager(private val context: Context) {

    companion object {
        const val SOS_PREFIX = "[NSOS]"
        const val SAFE_PREFIX = "[NSAFE]"
        // SOS follow-up constants
        const val FOLLOWUP_INTERVAL_MS = 2 * 60 * 1000L    // 2 minutes
        const val FOLLOWUP_DURATION_MS = 30 * 60 * 1000L   // 30 minutes total
        const val FOLLOWUP_MIN_DISTANCE_M = 50f             // 50 metres
    }

    private val smsManager: SmsManager
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    /**
     * BUG-06 FIX: sendSosAlerts (was sendSOSSAlerts — typo fixed).
     * Uses real sentIntents/deliveryIntents with FLAG_IMMUTABLE.
     * Calls onProgress as each SMS is dispatched and onComplete when all are done.
     */
    fun sendSosAlerts(
        contacts: List<EmergencyContact>,
        userLocation: String = "",
        senderPhoneNumber: String = "",
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean {
        if (contacts.isEmpty()) {
            Log.e("SOSManager", "No contacts to send SOS to")
            return false
        }

        val total = contacts.size
        val sentCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val timeStamp = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date())

        contacts.forEach { contact ->
            try {
                if (contact.phoneNumber.isBlank()) {
                    Log.w("SOSManager", "Skipping contact with empty phone number")
                    failCount.incrementAndGet()
                    return@forEach
                }

                val message = buildSosMessage(contact.name, userLocation, senderPhoneNumber, timeStamp)
                val parts = smsManager.divideMessage(message)

                // Build per-part PendingIntents
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                val deliveryIntents = ArrayList<PendingIntent>(parts.size)

                parts.indices.forEach { partIndex ->
                    val sentAction = "${context.packageName}.SMS_SENT_${contact.id}_$partIndex"
                    val deliveryAction = "${context.packageName}.SMS_DELIVERED_${contact.id}_$partIndex"

                    val sentIntent = PendingIntent.getBroadcast(
                        context, (contact.id.toInt() * 100 + partIndex),
                        Intent(sentAction), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val deliveryIntent = PendingIntent.getBroadcast(
                        context, (contact.id.toInt() * 100 + partIndex + 50),
                        Intent(deliveryAction), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    sentIntents.add(sentIntent)
                    deliveryIntents.add(deliveryIntent)

                    // Register receivers for last part only (to track overall delivery)
                    if (partIndex == parts.size - 1) {
                        val sentReceiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                                val sc = android.app.Activity.RESULT_OK
                                if (resultCode == sc) {
                                    val s = sentCount.incrementAndGet()
                                    Log.d("SOSManager", "SMS sent successfully")
                                    onProgress(s, total)
                                    if (s + failCount.get() >= total) onComplete(s, total)
                                } else {
                                    val f = failCount.incrementAndGet()
                                    Log.w("SOSManager", "SMS send failed, resultCode=$resultCode")
                                    if (sentCount.get() + f >= total) onComplete(sentCount.get(), total)
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_EXPORTED)
                        } else {
                            context.registerReceiver(sentReceiver, IntentFilter(sentAction))
                        }
                    }
                }

                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber, null, parts, sentIntents, deliveryIntents
                )

            } catch (e: SecurityException) {
                Log.e("SOSManager", "SMS permission denied", e)
                failCount.incrementAndGet()
                if (sentCount.get() + failCount.get() >= total) onComplete(sentCount.get(), total)
            } catch (e: Exception) {
                Log.e("SOSManager", "Failed to send SOS SMS", e)
                failCount.incrementAndGet()
                if (sentCount.get() + failCount.get() >= total) onComplete(sentCount.get(), total)
            }
        }

        return true
    }

    fun buildSosMessage(
        contactName: String,
        location: String,
        senderPhoneNumber: String,
        timeStamp: String
    ): String {
        val senderInfo = senderPhoneNumber.ifBlank { "User" }
        val locationPart = if (location.isNotEmpty()) {
            "Live location: $location. "
        } else {
            ""
        }
        return "$SOS_PREFIX EMERGENCY! This is an automatic SOS from $senderInfo. " +
               "She is in danger and needs help. ${locationPart}Call her now."
    }

    /**
     * Follow-up SOS message containing updated location.
     */
    fun buildFollowUpSosMessage(
        senderPhoneNumber: String,
        location: String
    ): String {
        val senderInfo = senderPhoneNumber.ifBlank { "User" }
        return "$SOS_PREFIX UPDATE from $senderInfo: Current location: $location"
    }

    /** Build an [NSAFE] safe confirmation message. */
    fun buildSafeMessage(senderPhoneNumber: String, location: String, timeStamp: String): String {
        val senderInfo = senderPhoneNumber.ifBlank { "Unknown" }
        val locPart = if (location.isNotEmpty()) "My location: $location ($timeStamp)." else "($timeStamp)."
        return "$SAFE_PREFIX Automatic message from $senderInfo: I am safe right now. $locPart"
    }
}
