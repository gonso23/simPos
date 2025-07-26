package com.gonso23.simpos

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.location.LocationManager
import android.util.Log
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private lateinit var myTcount: TextView
    private var simTrack: SimTrack? = null

    private lateinit var locationReceiver: LocationReceiver

    private  var simLocationP: SimLocationProvider? = null

    private lateinit var positionTimer: PositionTimer
    private var isBound = false
    private lateinit var toggleButton: Button
    private lateinit var eTm_lat: EditText
    private lateinit var eTm_long: EditText
    private lateinit var eTm_alt: EditText
    private var simActive = false

    private val timerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            positionTimer = (service as PositionTimer.LocalBinder).getService()
            positionTimer.setMainActivity(this@MainActivity)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private val simGPXActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultTrack = result.data?.getParcelableExtra<SimTrack>("RESULT_TRACK")
            simTrack = resultTrack?.deepCopy()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        myTcount = findViewById(R.id.myTcount)
        myTcount.text = "------"


        eTm_lat = findViewById<EditText>(R.id.eTm_lat)
        eTm_long = findViewById<EditText>(R.id.eTm_long)
        eTm_alt = findViewById<EditText>(R.id.eTm_alt)
        eTm_lat.setText("-")
        eTm_long.setText("-")
        eTm_alt.setText("-")

        locationReceiver = LocationReceiver(this)
        locationReceiver.startLocationUpdates(lifecycleScope)

        toggleButton = findViewById(R.id.toggleButton)
        toggleButton.setOnClickListener { toggleSimulation() }
        
        val iB_setting = findViewById<Button>(R.id.iB_setting)
        val rB_GPX = findViewById<RadioButton>(R.id.rB_GPX)

        iB_setting.setOnClickListener {
            if (true) {//rB_GPX.isChecked would be an option
                stopSimulation()
                val intent = Intent(this, SimGPXActivity::class.java).apply {
                    putExtra("SIM_TRACK", simTrack ?: SimTrack(mutableListOf()).apply {
                    })
                }
                simGPXActivityLauncher.launch(intent)
            }
        }

        val rB_Device: RadioButton = findViewById(R.id.rB_Device)

        rB_Device.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                simLocationP?.shutdown()
                simLocationP = null

                Log.d("RadioButton", "rB_Device wurde ausgewählt")
                positionTimer.stopTimer()
            } else {
                try {
                    simLocationP = simLocationP ?: SimLocationProvider(applicationContext)
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "SecurityException: ${e.message}")
                    positionTimer.stopTimer()
                    Toast.makeText(
                        this,
                        "Location simulation permission is missing. Please enable Developer Options and allow mock locations.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                Log.d("RadioButton", "rB_Device wurde abgewählt")
                positionTimer.startTimer()
            }
        }

        val serviceIntent = Intent(this, PositionTimer::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, timerServiceConnection, Context.BIND_AUTO_CREATE)

    }


    private fun stopSimulation() {
        if (toggleButton.text == "Stop") {
            toggleButton.text = "Start"
            simActive = false
        }
    }

    private fun toggleSimulation() {
        if (toggleButton.text == "Start") {
            toggleButton.text = "Stop"
            simActive = true
        }else{
            toggleButton.text = "Start"
            simActive = false
        }
    }

    fun nextStep(counter: Int) {
        myTcount.text = "$counter"

        if (!simActive) {
            simLocationP?.pushLocation(null)
            return
        }


        val pTemp = simTrack?.nextPoint()
        
        if (pTemp == null) { // Track ended -> stop Simulation and restart
            simActive = false
            simTrack?.restartTrack()
        }

        if (pTemp != null) {
            eTm_lat.setText("%.6f".format(pTemp.latitude))
            eTm_long.setText("%.6f".format(pTemp.longitude))
            eTm_alt.setText(pTemp.elevation?.let { "%.1f".format(it) } ?: "-")
        } else {
            eTm_lat.setText("-")
            eTm_long.setText("-")
            eTm_alt.setText("-")
        }

        if (pTemp != null) {
            try {
                simLocationP?.pushLocation(pTemp)
            } catch (e: Exception) {
                Log.e("LocationPush", "Error: ${e.stackTraceToString()}")
            }
        }

    }

    fun isTimerActive() = isBound && positionTimer.isRunning()

    override fun onResume() {
        super.onResume()
        locationReceiver.onMapResume()
    }

    override fun onPause() {
        super.onPause()
        locationReceiver.onMapPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationReceiver.stopLocationUpdates()
        if (isBound) {
            unbindService(timerServiceConnection)
            isBound = false
        }
    }
}

