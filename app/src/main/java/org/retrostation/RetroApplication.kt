package org.retrostation

import android.app.Application
import android.util.Log

class RetroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("RetroApplication", "Retro Game Station initialized successfully.")
    }
}
