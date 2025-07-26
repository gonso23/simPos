package com.gonso23.simpos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.EditText
import android.widget.Switch
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import android.os.Looper
import android.preference.PreferenceManager
import com.google.android.gms.location.*
import android.view.View
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.config.Configuration

class LocationReceiver(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private lateinit var etLat: EditText
    private lateinit var etLong: EditText
    private lateinit var etAlt: EditText
    private lateinit var map: MapView
    private lateinit var sMap: Switch

    private var locationCallback: LocationCallback? = null
    private var isMapActive = false

    init {
        initializeViews()
        setupMapSwitch()
    }

    private fun initializeViews() {
        if (context is Activity) {
            etLat = context.findViewById(R.id.eTm_Dev_lat)
            etLong = context.findViewById(R.id.eTm_Dev_long)
            etAlt = context.findViewById(R.id.eTm_Dev_alt)
            map = context.findViewById(R.id.map)
            sMap = context.findViewById(R.id.sMap)
        } else {
            throw IllegalStateException("Context must be an Activity")
        }
    }

    private var isInitialized = false

    private fun initializeMap() {
        if (!isInitialized) {
            Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
            map.controller.setZoom(18.0)

            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), map)
            locationOverlay.enableMyLocation()
            map.overlays.add(locationOverlay)

            isInitialized = true
        }
    }

    private fun setupMapSwitch() {
        sMap.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isInternetPermissionGranted()) {
                    requestInternetPermission()
                } else {
                    initializeMap()
                    toggleMap(true)
                }
            } else {
                toggleMap(false)
            }
        }
    }

    private fun isInternetPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestInternetPermission() {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.INTERNET),
            INTERNET_PERMISSION_REQUEST_CODE
        )
    }

    fun startLocationUpdates(lifecycleScope: LifecycleCoroutineScope) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lifecycleScope.launch {
                        updateLocationEditText(location)
                        if (isMapActive) {
                            updateMapPosition(location)
                        }
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun updateLocationEditText(location: Location) {
        etLat.setText(String.format("%.6f", location.latitude))
        etLong.setText(String.format("%.6f", location.longitude))
        etAlt.setText(String.format("%.1f", location.altitude))
    }

    private fun updateMapPosition(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        map.controller.animateTo(geoPoint)
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    fun toggleMap(isActive: Boolean) {
        map.visibility = if (isActive) View.VISIBLE else View.GONE
        if (isActive) {
            onMapResume()
        } else {
            onMapPause()
        }
    }

    fun onMapResume() {
        if (isMapActive == false) {
            map.onResume()
            isMapActive = true
        }
    }

    fun onMapPause() {
        if (isMapActive) {
            map.onPause()
            isMapActive = false
        }
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val INTERNET_PERMISSION_REQUEST_CODE = 2
    }
}
