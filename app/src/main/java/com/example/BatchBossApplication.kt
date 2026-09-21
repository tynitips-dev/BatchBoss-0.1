package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * BatchBossApplication
 *
 * Provides application-level lifecycle management and safe initialization
 * for Google Firebase services (Auth, Firestore, AppCheck).
 * Gracefully defaults to offline/local Room database if google-services.json
 * is not yet supplied or configured.
 */
class BatchBossApplication : Application() {

    companion object {
        lateinit var instance: BatchBossApplication
            private set

        var isFirebaseConfigured: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            // When google-services.json is present, FirebaseApp is automatically initialized.
            val apps = FirebaseApp.getApps(this)
            if (apps.isNotEmpty()) {
                isFirebaseConfigured = true
                Log.i("BatchBossApp", "Firebase initialized successfully with app: ${apps.first().name}")
            } else {
                Log.i("BatchBossApp", "No FirebaseApp found. App is running safely in local Room database mode.")
            }
        } catch (e: Throwable) {
            Log.w("BatchBossApp", "Firebase check notice: ${e.message}. App running safely in offline mode.")
            isFirebaseConfigured = false
        }
    }
}
