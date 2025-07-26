package com.gonso23.simpos

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log

class PositionTimer : Service() {

    private val handler = Handler()
    private var isRunning = false
    private var counter = 0

    private var mainActivity: MainActivity? = null // Referenz zur MainActivity

    private val runnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                counter++
                Log.d("PositionTimer", "Counter: $counter")
                mainActivity?.nextStep(counter)
                handler.postDelayed(this, 1000)
            }
        }
    }

    // Binder-Klasse
    inner class LocalBinder : Binder() {
        fun getService(): PositionTimer = this@PositionTimer
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //isRunning = true
        //handler.post(runnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(runnable)
    }

    fun setMainActivity(activity: MainActivity) {
        this.mainActivity = activity
    }

    fun isRunning(): Boolean = isRunning

    // Methode zum Starten des Timers
    fun startTimer() {
        if (!isRunning) {
            isRunning = true
            handler.post(runnable)
        }
    }

    // Methode zum Stoppen des Timers
    fun stopTimer() {
        isRunning = false
        handler.removeCallbacks(runnable)
    }
}
