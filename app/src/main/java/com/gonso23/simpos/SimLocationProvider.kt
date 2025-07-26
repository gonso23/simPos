package com.gonso23.simpos

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlin.random.Random

class SimLocationProvider(private val context: Context) {
    private var pPowUse: Int = 0
    private lateinit var locationManager: LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var lastTrackPoint: TrackPoint? = null
    private var lastUpdateTime: Long = 0
    private var lastBearing: Float = 0f

    init {
        pPowUse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ProviderProperties.POWER_USAGE_LOW
        } else {
            Criteria.POWER_LOW
        }

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setupProviders()
    }

    private fun setupProviders() {
        providers.forEach { providerName ->
            try {
                locationManager.addTestProvider(
                    providerName,
                    false, false, false, false, true, true, true,
                    pPowUse,
                    ProviderProperties.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(providerName, true)
            } catch (e: SecurityException) {
                Log.e("SimLocationProvider", "Not allowed to perform MOCK_LOCATION for $providerName: ${e.message}")
            } catch (e: Exception) {
                Log.e("SimLocationProvider", "Failed to add test provider for $providerName: ${e.message}")
            }
        }
    }

    private fun getRandomInRange(from: Float, until: Float): Float {
        return Random.nextFloat() * (until - from) + from
    }

    fun pushLocation(trackPoint: TrackPoint?) {
        val currentTime = System.currentTimeMillis()
        val timeDelta = if (lastUpdateTime > 0) (currentTime - lastUpdateTime) / 1000.0f else 0f

        val pointToUse = trackPoint ?: lastTrackPoint
        if (pointToUse == null) {
            //Log.e("SimLocationProvider", "No point available to push")
            return
        }

        val newLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = pointToUse.latitude
            longitude = pointToUse.longitude
            altitude = pointToUse.elevation ?: 0.0
            time = currentTime
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            speed = if (trackPoint == null) 0f else {
                if (lastTrackPoint != null && timeDelta > 0) {
                    val distance = calculateDistance(lastTrackPoint!!, pointToUse)
                    distance / timeDelta
                } else {
                    0f
                }
            }

            bearing = if (trackPoint == null) {
                // Füge einen zufälligen Wert zwischen -5 und 5 Grad zum letzten Bearing hinzu
                val randomChange = getRandomInRange(-5f, 5f)
                (lastBearing + randomChange + 360f) % 360f
            } else {
                if (lastTrackPoint != null) {
                    calculateBearing(lastTrackPoint!!, pointToUse)
                } else {
                    0f
                }
            }
            lastBearing = bearing

            accuracy = getRandomInRange(5f, 10f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = getRandomInRange(3f, 5f)
                verticalAccuracyMeters = getRandomInRange(3f, 5f)
                speedAccuracyMetersPerSecond = getRandomInRange(1f, 2f)
            }
        }

        providers.forEach { providerName ->
            try {
                locationManager.setTestProviderLocation(providerName, newLocation)
            } catch (e: Exception) {
                Log.e("SimLocationProvider", "Failed to push location for $providerName: ${e.message}")
            }
        }

        if (trackPoint != null) {
            lastTrackPoint = trackPoint
            lastUpdateTime = currentTime
        }
    }

    private fun calculateDistance(start: TrackPoint, end: TrackPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0]
    }

    private fun calculateBearing(start: TrackPoint, end: TrackPoint): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

        var bearing = Math.toDegrees(Math.atan2(y, x))
        bearing = (bearing + 360) % 360

        return bearing.toFloat()
    }

    fun shutdown() {
        providers.forEach { providerName ->
            try {
                locationManager.removeTestProvider(providerName)
            } catch (e: Exception) {
                Log.e("SimLocationProvider", "Shutdown failed for $providerName: ${e.message}")
            }
        }
    }
}
