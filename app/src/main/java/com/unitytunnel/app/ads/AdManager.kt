package com.unitytunnel.app.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.unitytunnel.app.data.PreferencesManager
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AdManager {
    private const val TAG = "AdManager"
    private const val ADMOB_INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val ADMOB_APP_OPEN_TEST_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val ADMOB_BANNER_TEST_ID = "ca-app-pub-3940256099942544/9214589222"

    private var isInitialized = false
    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var disconnectInterstitialAd: InterstitialAd? = null
    private var appOpenLoadTime: Long = 0

    fun initialize(context: Context, onComplete: () -> Unit = {}) {
        if (isInitialized) {
            onComplete()
            return
        }
        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "AdMob initialized: $status")
            }
            isInitialized = true
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Ad initialization failed", e)
            onComplete()
        }
    }

    fun loadInterstitialAd(context: Context, onLoaded: () -> Unit = {}) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, ADMOB_INTERSTITIAL_TEST_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.d(TAG, "Connecting Interstitial loaded")
                onLoaded()
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "Connecting Interstitial failed to load: ${loadAdError.message}")
                interstitialAd = null
            }
        })
    }

    fun showInterstitialAd(activity: Activity, onClosed: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onClosed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onClosed()
                }
            }
            ad.show(activity)
        } else {
            onClosed()
        }
    }

    fun loadAppOpenAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(context, ADMOB_APP_OPEN_TEST_ID, adRequest, object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
                appOpenLoadTime = System.currentTimeMillis()
                Log.d(TAG, "App Open Ad loaded")
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                appOpenAd = null
            }
        })
    }

    fun showAppOpenAdIfAvailable(activity: Activity, preferencesManager: PreferencesManager, onClosed: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Main).launch {
            val lastShown = preferencesManager.lastOpenAdTime.first()
            val now = System.currentTimeMillis()
            val fourHoursMs = 4 * 60 * 60 * 1000L
            if (now - lastShown < fourHoursMs) {
                Log.d(TAG, "App Open Ad skipped: Within 4-hour safety cap")
                onClosed()
                return@launch
            }

            val ad = appOpenAd
            val isAdValid = ad != null && (now - appOpenLoadTime < 4 * 60 * 60 * 1000L) // Valid for 4 hours
            if (isAdValid && ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        appOpenAd = null
                        onClosed()
                        CoroutineScope(Dispatchers.IO).launch {
                            preferencesManager.saveLastOpenAdTime(System.currentTimeMillis())
                        }
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        appOpenAd = null
                        onClosed()
                    }
                }
                ad.show(activity)
            } else {
                onClosed()
                loadAppOpenAd(activity)
            }
        }
    }

    fun loadDisconnectAd(context: Context, onLoaded: () -> Unit = {}) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, ADMOB_INTERSTITIAL_TEST_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                disconnectInterstitialAd = ad
                onLoaded()
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                disconnectInterstitialAd = null
            }
        })
    }

    fun showDisconnectAd(activity: Activity, onClosed: () -> Unit) {
        val ad = disconnectInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    disconnectInterstitialAd = null
                    onClosed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    disconnectInterstitialAd = null
                    onClosed()
                }
            }
            ad.show(activity)
        } else {
            onClosed()
        }
    }

    fun getBannerAdUnitId(): String = ADMOB_BANNER_TEST_ID

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
