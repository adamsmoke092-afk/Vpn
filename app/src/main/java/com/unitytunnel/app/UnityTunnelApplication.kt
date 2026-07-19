package com.unitytunnel.app

import android.app.Application
import com.unitytunnel.app.ads.AdManager

class UnityTunnelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
    }
}
