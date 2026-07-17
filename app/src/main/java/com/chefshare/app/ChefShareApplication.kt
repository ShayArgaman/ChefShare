package com.chefshare.app

import android.app.Application

/**
 * Custom Application class for ChefShare.
 *
 * Firebase auto-initializes from google-services.json via its bundled ContentProvider,
 * so no manual init is needed here. This class is a clean, central place to add app-wide
 * configuration in later milestones (logging, Glide defaults, etc.). Kept minimal for now.
 */
class ChefShareApplication : Application() {

    // Called once when the process starts, before any Activity is created.
    override fun onCreate() {
        super.onCreate()
        // App-wide setup will be added in later steps as needed.
    }
}
