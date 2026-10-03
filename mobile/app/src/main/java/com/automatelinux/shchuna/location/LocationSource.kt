package com.automatelinux.shchuna.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's position, through the platform LocationManager (GPS + network
 * together) rather than Play Services — same choice and reasons as brownSigns:
 * works on any device and in an emulator fed `adb emu geo fix`.
 */
class LocationSource(private val context: Context) {
    private val manager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun providersEnabled(): Boolean =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    /**
     * Position updates. Every 5 s / 15 m: a parking zone can change across one
     * street, so this is finer than brownSigns needs.
     */
    @SuppressLint("MissingPermission")
    fun positions(): Flow<Location> = callbackFlow {
        if (!hasPermission()) { close(); return@callbackFlow }
        val lm = manager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { close(); return@callbackFlow }

        var best: Location? = null
        fun offer(candidate: Location?) {
            if (candidate == null) return
            if (candidate.isBetterThan(best)) { best = candidate; trySend(candidate) }
        }
        providers.forEach { offer(runCatching { lm.getLastKnownLocation(it) }.getOrNull()) }
        val listener = LocationListener { offer(it) }
        providers.forEach { lm.requestLocationUpdates(it, 5_000L, 15f, listener, Looper.getMainLooper()) }
        awaitClose { lm.removeUpdates(listener) }
    }

    /** Newer wins, unless it is markedly vaguer than a fix still under a minute old. */
    private fun Location.isBetterThan(other: Location?): Boolean {
        if (other == null) return true
        val newerBy = time - other.time
        if (newerBy > 60_000L) return true
        if (newerBy < 0) return false
        if (!hasAccuracy()) return !other.hasAccuracy()
        if (!other.hasAccuracy()) return true
        return accuracy <= other.accuracy * 2f
    }
}
