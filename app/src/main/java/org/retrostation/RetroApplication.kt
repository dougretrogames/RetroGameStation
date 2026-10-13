package org.retrostation

import android.app.Application
import android.util.Log
import com.retro.gamestation.storage.ScopedStorageManager

class RetroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("RetroApplication", "Retro Game Station initialized successfully.")
        try {
            ScopedStorageManager.getInstance(this).extractAssetsIfNeeded(this)
        } catch (e: Exception) {
            Log.e("RetroApplication", "Error extracting assets on application startup: ${e.message}")
        }
    }
}
