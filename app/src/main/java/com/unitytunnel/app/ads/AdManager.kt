package com.unitytunnel.app.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.sdk.AppLovinSdk
import com.unitytunnel.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AdManager {
    private const val TAG = "AdManager"

    // AppLovin MAX Ad Unit IDs (Placeholders)
    private const val MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID = "YOUR_CONNECTING_INTERSTITIAL_ID"
    private const val MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID = "YOUR_DISCONNECT_INTERSTITIAL_ID"
    private const val MAX_APP_OPEN_AD_UNIT_ID = "YOUR_APP_OPEN_ID"
    const val MAX_BANNER_AD_UNIT_ID = "YOUR_BANNER_ID"
    const val MAX_REWARDED_AD_UNIT_ID = "YOUR_REWARDED_ID"

    private val _isInitialized = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isInitialized: kotlinx.coroutines.flow.StateFlow<Boolean> = _isInitialized

    private var connectingInterstitialAd: MaxInterstitialAd? = null
    private var disconnectInterstitialAd: MaxInterstitialAd? = null
    private var appOpenAd: MaxAppOpenAd? = null

    private var connectingAdRetryAttempt = 0.0
    private var disconnectAdRetryAttempt = 0.0
    private var appOpenAdRetryAttempt = 0.0

    private var onConnectingAdClosed: (() -> Unit)? = null
    private var onDisconnectAdClosed: (() -> Unit)? = null
    private var onAppOpenAdClosed: (() -> Unit)? = null

    private var cachedPreferencesManager: PreferencesManager? = null

    private var isInitializing = false
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context, onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            Handler(Looper.getMainLooper()).post { onComplete() }
            return
        }
        
        pendingCallbacks.add(onComplete)
        if (isInitializing) return
        isInitializing = true
        
        AppLovinSdk.getInstance(context).mediationProvider = "max"
        AppLovinSdk.initializeSdk(context) { configuration ->
            Log.d(TAG, "AppLovin MAX initialized")
            Handler(Looper.getMainLooper()).post {
                _isInitialized.value = true
                isInitializing = false
                pendingCallbacks.forEach { it.invoke() }
                pendingCallbacks.clear()
            }
        }
    }

    // --- Connecting Interstitial ---
    fun loadConnectingInterstitial(activity: Activity) {
        if (connectingInterstitialAd == null) {
            connectingInterstitialAd = MaxInterstitialAd(MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID, activity)
            connectingInterstitialAd?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    connectingAdRetryAttempt = 0.0
                    Log.d(TAG, "Connecting Ad Loaded")
                }
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Connecting Ad Failed: ${error.message}")
                    connectingAdRetryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(Math.pow(2.0, Math.min(6.0, connectingAdRetryAttempt)).toLong())
                    Handler(Looper.getMainLooper()).postDelayed({ if (!MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { connectingInterstitialAd?.loadAd() } }, delayMillis)
                }
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Connecting Ad Display Failed: ${error.message}")
                    if (!MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { connectingInterstitialAd?.loadAd() }
                    onConnectingAdClosed?.invoke()
                    onConnectingAdClosed = null
                }
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdHidden(ad: MaxAd) {
                    if (!MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { connectingInterstitialAd?.loadAd() }
                    onConnectingAdClosed?.invoke()
                    onConnectingAdClosed = null
                }
            })
        }
        if (!MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { connectingInterstitialAd?.loadAd() }
    }
    fun showConnectingInterstitial(onClosed: () -> Unit) {
        if (connectingInterstitialAd?.isReady == true) {
            onConnectingAdClosed = onClosed
            connectingInterstitialAd?.showAd()
        } else {
            onClosed()
        }
    }

    // --- Disconnect Interstitial ---
    fun loadDisconnectInterstitial(activity: Activity) {
        if (disconnectInterstitialAd == null) {
            disconnectInterstitialAd = MaxInterstitialAd(MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID, activity)
            disconnectInterstitialAd?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    disconnectAdRetryAttempt = 0.0
                }
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    disconnectAdRetryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(Math.pow(2.0, Math.min(6.0, disconnectAdRetryAttempt)).toLong())
                    Handler(Looper.getMainLooper()).postDelayed({ if (!MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { disconnectInterstitialAd?.loadAd() } }, delayMillis)
                }
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    if (!MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { disconnectInterstitialAd?.loadAd() }
                    onDisconnectAdClosed?.invoke()
                    onDisconnectAdClosed = null
                }
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdHidden(ad: MaxAd) {
                    if (!MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { disconnectInterstitialAd?.loadAd() }
                    onDisconnectAdClosed?.invoke()
                    onDisconnectAdClosed = null
                }
            })
        }
        if (!MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID.startsWith("YOUR_")) { disconnectInterstitialAd?.loadAd() }
    }
    fun showDisconnectInterstitial(onClosed: () -> Unit) {
        if (disconnectInterstitialAd?.isReady == true) {
            onDisconnectAdClosed = onClosed
            disconnectInterstitialAd?.showAd()
        } else {
            onClosed()
        }
    }

    // --- App Open Ad ---
    fun loadAppOpenAd(context: Context) {
        if (appOpenAd == null) {
            appOpenAd = MaxAppOpenAd(MAX_APP_OPEN_AD_UNIT_ID, context)
            appOpenAd?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    appOpenAdRetryAttempt = 0.0
                }
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    appOpenAdRetryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(Math.pow(2.0, Math.min(6.0, appOpenAdRetryAttempt)).toLong())
                    Handler(Looper.getMainLooper()).postDelayed({ if (!MAX_APP_OPEN_AD_UNIT_ID.startsWith("YOUR_")) { appOpenAd?.loadAd() } }, delayMillis)
                }
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    if (!MAX_APP_OPEN_AD_UNIT_ID.startsWith("YOUR_")) { appOpenAd?.loadAd() }
                    onAppOpenAdClosed?.invoke()
                    onAppOpenAdClosed = null
                }
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdHidden(ad: MaxAd) {
                    if (!MAX_APP_OPEN_AD_UNIT_ID.startsWith("YOUR_")) { appOpenAd?.loadAd() }
                    cachedPreferencesManager?.let { pm ->
                        CoroutineScope(Dispatchers.IO).launch {
                            pm.saveLastOpenAdTime(System.currentTimeMillis())
                        }
                    }
                    onAppOpenAdClosed?.invoke()
                    onAppOpenAdClosed = null
                }
            })
        }
        if (!MAX_APP_OPEN_AD_UNIT_ID.startsWith("YOUR_")) { appOpenAd?.loadAd() }
    }
    fun showAppOpenAdIfAvailable(preferencesManager: PreferencesManager, onClosed: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Main).launch {
            val lastShown = preferencesManager.lastOpenAdTime.first()
            val now = System.currentTimeMillis()
            val fourHoursMs = 4 * 60 * 60 * 1000L
            if (now - lastShown < fourHoursMs) {
                Log.d(TAG, "App Open Ad skipped: Within 4-hour safety cap")
                onClosed()
                return@launch
            }
            if (appOpenAd?.isReady == true) {
                cachedPreferencesManager = preferencesManager
                onAppOpenAdClosed = onClosed
                appOpenAd?.showAd()
            } else {
                onClosed()
            }
        }
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
