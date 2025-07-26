package com.gonso23.simpos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException


class SimGPXActivity : AppCompatActivity() {

    private lateinit var fileNameTextView: TextView
    private lateinit var trackSpinner: Spinner
    private lateinit var modeSpinner: Spinner
    private lateinit var maxDistanceEditText: EditText
    private lateinit var errorVarianceEditText: EditText
    private lateinit var numTPointsTextView: TextView
    private lateinit var currentPointEditText: EditText

    private var simTrack: SimTrack? = null

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val gpxParser = GPXParser(it, contentResolver)
                simTrack = gpxParser.parseFile()?.let { tracks ->
                    SimTrack(tracks).apply {
                        fileName = getFileName(uri) ?: "Unknown file"
                        selectedTrackIndex = 0
                        maxDistance = this@SimGPXActivity.simTrack?.maxDistance ?: 0.0
                        errorVariance = this@SimGPXActivity.simTrack?.errorVariance ?: 0.0
                        selectedMode = this@SimGPXActivity.simTrack?.selectedMode ?: SimTrack.Mode.CURRENT_TRACK
                    }
                }
                updateFileName()
                populateTrackSpinner()
                updateNumTPoints()

            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is XmlPullParserException -> "XML-Error"
                    is IOException -> when {
                        e.message?.contains("No such file") == true -> "No such file"
                        e.message?.contains("Permission denied") == true -> "Permission denied"
                        else -> "IOException"
                    }
                    else -> "Error"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sim_gpx)

        fileNameTextView = findViewById(R.id.fileNameTextView)
        trackSpinner = findViewById(R.id.trackSpinner)
        modeSpinner = findViewById(R.id.modeSpinner)
        maxDistanceEditText = findViewById(R.id.maxDistanceEditText)
        errorVarianceEditText = findViewById(R.id.errorVarianceEditText)
        numTPointsTextView = findViewById(R.id.numTPoints)
        currentPointEditText = findViewById(R.id.eT_currentPoint)


        simTrack = intent.getParcelableExtra<SimTrack>("SIM_TRACK") ?: SimTrack(mutableListOf()).apply {
            fileName = "No file selected"
            selectedTrackIndex = 0
            maxDistance = 0.0
            errorVariance = 0.0
            selectedMode = SimTrack.Mode.CURRENT_TRACK
        }

        // Initialize UI from SimTrack
        updateFileName()
        updateNumTPoints()
        maxDistanceEditText.setText(simTrack?.maxDistance?.toString() ?: "0.0")
        errorVarianceEditText.setText(simTrack?.errorVariance?.toString() ?: "0.0")

        populateTrackSpinner()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.openFileButton).setOnClickListener {
            if (checkStoragePermission()) {
                openFileLauncher.launch(arrayOf("*/*"))
            } else {
                requestStoragePermission()
            }
        }

        val modes = SimTrack.Mode.values().map { it.name }
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeSpinner.adapter = modeAdapter
        simTrack?.selectedMode?.let { mode ->
            val position = SimTrack.Mode.values().indexOf(mode)
            modeSpinner.setSelection(position)
        }

        maxDistanceEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateMaxDistance()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        errorVarianceEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateErrorVariance()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        trackSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                simTrack?.apply {
                    if(selectTrack(position)) {
                        updateNumTPoints()
                    }
                }

            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                simTrack?.selectedMode = SimTrack.Mode.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        //current Point index
        currentPointEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val newIndex = s.toString().toIntOrNull() ?: 0
                simTrack?.updatePIndex(newIndex)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

    }

    fun onBackButtonClick(view: View) {
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun updateNumTPoints() {
        val currentTrack = simTrack?.tracks?.getOrNull(simTrack?.selectedTrackIndex ?: 0)
        val pointCount = currentTrack?.points?.size ?: 0
        numTPointsTextView.text = "$pointCount"

        // Update currentPointIndex
        val currentIndex = simTrack?.currentPointIndex ?: 0
        currentPointEditText.setText(currentIndex.toString())
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:" + packageName)
            startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openFileLauncher.launch(arrayOf("*/*.gpx"))
        }
    }

    private fun populateTrackSpinner() {
        val trackNames = simTrack?.getTrackNames() ?: emptyList()
        val displayList = if (trackNames.isEmpty()) {
            listOf("No Track loaded")
        } else {
            trackNames
        }
        val trackAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayList)
        trackSpinner.adapter = trackAdapter

        if (trackNames.isNotEmpty()) {
            trackSpinner.setSelection(simTrack?.selectedTrackIndex ?: 0)
        } else {
            trackSpinner.setSelection(0) //Zeigt "No Track loaded" an
        }
    }

    private fun updateMaxDistance() {
        val maxDistance = maxDistanceEditText.text.toString().toDoubleOrNull() ?: 0.0
        simTrack?.maxDistance = maxDistance
    }

    private fun updateErrorVariance() {
        val errorVariance = errorVarianceEditText.text.toString().toDoubleOrNull() ?: 0.0
        simTrack?.errorVariance = errorVariance
    }

    private fun updateFileName() {
        fileNameTextView.text = simTrack?.fileName ?: "No file selected"
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    override fun finish() {
        val resultIntent = Intent().apply {
            putExtra("RESULT_TRACK", simTrack)
        }
        setResult(RESULT_OK, resultIntent)
        super.finish()
    }
}
