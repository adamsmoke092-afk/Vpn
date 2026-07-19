package com.unitytunnel.app

import android.app.Application
import com.unitytunnel.app.ads.AdManager
import com.google.android.gms.ads.MobileAds

class UnityTunnelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AdMob and other Ad networks at the application level
        MobileAds.initialize(this) {}
        AdManager.initialize(this)
    }
}
