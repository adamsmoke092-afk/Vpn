package com.unitytunnel.app.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

object RewardedAdService {
    private const val TAG = "RewardedAdService"
    const val MAX_REWARDED_AD_UNIT_ID = "YOUR_REWARDED_AD_UNIT_ID" // Replace with actual ad unit ID

    private var rewardedAd: MaxRewardedAd? = null
    private var retryAttempt = 0.0
    private var isAdReady = false
    
    private var onAdSuccess: (() -> Unit)? = null
    private var onAdFail: (() -> Unit)? = null

    fun initialize(activity: Activity) {
        if (rewardedAd == null) {
            rewardedAd = MaxRewardedAd.getInstance(MAX_REWARDED_AD_UNIT_ID, activity)
            rewardedAd?.setListener(object : MaxRewardedAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    retryAttempt = 0.0
                    isAdReady = true
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Rewarded ad failed to load: \${error.message}")
                    isAdReady = false
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        min(60.0, 2.0.pow(min(6.0, retryAttempt))).toLong()
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!MAX_REWARDED_AD_UNIT_ID.startsWith("YOUR_")) {
                            rewardedAd?.loadAd()
                        }
                    }, delayMillis)
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Rewarded ad failed to display: \${error.message}")
                    if (!MAX_REWARDED_AD_UNIT_ID.startsWith("YOUR_")) {
                        rewardedAd?.loadAd()
                    }
                    onAdFail?.invoke()
                    clearCallbacks()
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "Rewarded ad displayed.")
                }

                override fun onAdClicked(ad: MaxAd) {}

                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "Rewarded ad hidden, loading next ad.")
                    isAdReady = false
                    if (!MAX_REWARDED_AD_UNIT_ID.startsWith("YOUR_")) {
                        rewardedAd?.loadAd()
                    }
                    // If user closed without reward, we might want to trigger fail or just ignore. 
                    // To be safe, we usually trigger fail if success wasn't called.
                    onAdFail?.invoke()
                    clearCallbacks()
                }

                override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                    Log.d(TAG, "User earned reward: \${reward.amount} \${reward.label}")
                    // Call the success callback, then clear so onAdHidden doesn't fail it.
                    onAdSuccess?.invoke()
                    clearCallbacks()
                }
            })
        }
        
        if (!MAX_REWARDED_AD_UNIT_ID.startsWith("YOUR_")) {
            rewardedAd?.loadAd()
        }
    }

    fun showRewardedAd(onSuccess: () -> Unit, onFail: () -> Unit) {
        if (rewardedAd?.isReady == true) {
            onAdSuccess = onSuccess
            onAdFail = onFail
            rewardedAd?.showAd()
        } else {
            Log.e(TAG, "Rewarded ad is not ready yet.")
            onFail()
        }
    }
    
    private fun clearCallbacks() {
        onAdSuccess = null
        onAdFail = null
    }
}
