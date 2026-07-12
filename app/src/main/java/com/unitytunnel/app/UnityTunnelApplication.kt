package com.unitytunnel.app

import android.app.Application
import com.unitytunnel.app.ads.AdManager

class UnityTunnelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AdMob and other Ad networks at the application level
        AdManager.initialize(this)
    }
}
