package com.womensafety.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result data holder for location lookups.
 */
data class LocationResult(
    val location: Location?,
    val isFresh: Boolean,
    val ageSeconds: Long = 0L
) {
    fun toMapsUrl(): String? {
        val loc = location ?: return null
        return "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
    }

    fun toDisplayString(timestampFormat: String = "HH:mm dd/MM"): String {
        val loc = location ?: return ""
        val timeStr = SimpleDateFormat(timestampFormat, Locale.getDefault()).format(Date(loc.time))
        val acc = loc.accuracy.toInt()
        val mapsUrl = toMapsUrl() ?: return ""
        return if (isFresh) {
            "$mapsUrl (accuracy ${acc}m, $timeStr)"
        } else {
            val ageDesc = when {
                ageSeconds < 60 -> "${ageSeconds}s old"
                ageSeconds < 3600 -> "${ageSeconds / 60}m old"
                else -> "${ageSeconds / 3600}h old"
            }
            "$mapsUrl (last known $ageDesc, accuracy ${acc}m, $timeStr)"
        }
    }
}

/**
 * Resilient, offline-capable LocationProvider implementing Phase 4 specification:
 * 1. FusedLocationProviderClient (PRIORITY_HIGH_ACCURACY, 10-15s timeout)
 * 2. LocationManager GPS_PROVIDER
 * 3. LocationManager NETWORK_PROVIDER
 * 4. Last known location (with calculated age)
 */
class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Attempts to get the best available location within [timeoutMs].
     * Never throws exceptions; returns LocationResult with null location if completely unavailable.
     */
    @SuppressLint("MissingPermission")
    suspend fun getBestLocation(timeoutMs: Long = 12_000L): LocationResult = withContext(Dispatchers.IO) {
        // Step 1: Try FusedLocationProviderClient fresh fix with timeout
        try {
            val freshFix = withTimeoutOrNull(timeoutMs) {
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
            if (freshFix != null) {
                Log.d(TAG, "Obtained fresh location from FusedClient")
                return@withContext LocationResult(freshFix, isFresh = true, ageSeconds = 0L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocationProviderClient lookup failed: ${e.message}")
        }

        // Step 2: Try LocationManager GPS provider
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLoc != null && isRecent(gpsLoc, maxAgeMs = 60_000L)) {
                    val ageSec = calculateAgeSeconds(gpsLoc)
                    Log.d(TAG, "Using recent LocationManager GPS fix ($ageSec s old)")
                    return@withContext LocationResult(gpsLoc, isFresh = false, ageSeconds = ageSec)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager GPS lookup failed: ${e.message}")
        }

        // Step 3: Try LocationManager NETWORK provider
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (netLoc != null && isRecent(netLoc, maxAgeMs = 120_000L)) {
                    val ageSec = calculateAgeSeconds(netLoc)
                    Log.d(TAG, "Using recent LocationManager Network fix ($ageSec s old)")
                    return@withContext LocationResult(netLoc, isFresh = false, ageSeconds = ageSec)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager Network lookup failed: ${e.message}")
        }

        // Step 4: Fallback to FusedClient or any last known location
        try {
            val lastFused = fusedClient.lastLocation.await()
            if (lastFused != null) {
                val ageSec = calculateAgeSeconds(lastFused)
                Log.d(TAG, "Using last known FusedClient location ($ageSec s old)")
                return@withContext LocationResult(lastFused, isFresh = false, ageSeconds = ageSec)
            }
        } catch (e: Exception) {
            Log.w(TAG, "FusedClient lastLocation lookup failed: ${e.message}")
        }

        // Any last known from providers
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) {
                    val ageSec = calculateAgeSeconds(loc)
                    Log.d(TAG, "Using last known from $provider ($ageSec s old)")
                    return@withContext LocationResult(loc, isFresh = false, ageSeconds = ageSec)
                }
            } catch (_: Exception) {}
        }

        Log.w(TAG, "No location fix available at all")
        LocationResult(null, isFresh = false)
    }

    private fun isRecent(loc: Location, maxAgeMs: Long): Boolean {
        val age = SystemClock.elapsedRealtime() - (loc.elapsedRealtimeNanos / 1_000_000L)
        return age in 0..maxAgeMs
    }

    private fun calculateAgeSeconds(loc: Location): Long {
        val ageMs = SystemClock.elapsedRealtime() - (loc.elapsedRealtimeNanos / 1_000_000L)
        return (ageMs / 1000L).coerceAtLeast(0L)
    }

    companion object {
        private const val TAG = "LocationHelper"
    }
}
