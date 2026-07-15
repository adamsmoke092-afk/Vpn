import os

path = "app/src/main/java/com/unitytunnel/app/ads/RewardedAdService.kt"

content = """package com.unitytunnel.app.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import com.unitytunnel.app.viewmodel.BalanceViewModel
import java.util.concurrent.TimeUnit

class RewardedAdService(
    private val activity: Activity,
    private val balanceViewModel: BalanceViewModel
) {
    private var rewardedAd: MaxRewardedAd? = null
    private val TAG = "RewardedAdService"
    private var retryAttempt = 0.0

    // Callbacks
    private var onAdLoadedCallback: (() -> Unit)? = null
    private var onAdLoadFailedCallback: ((String) -> Unit)? = null
    private var onAdClosedCallback: (() -> Unit)? = null
    private var onAdDisplayFailedCallback: (() -> Unit)? = null

    // Reward state
    private var currentRewardType: String? = null // "TOP_UP" or "DOUBLE_UP"

    init {
        rewardedAd = MaxRewardedAd.getInstance(AdManager.MAX_REWARDED_AD_UNIT_ID, activity)
        rewardedAd?.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "Rewarded ad loaded successfully")
                retryAttempt = 0.0
                onAdLoadedCallback?.invoke()
                onAdLoadedCallback = null
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                retryAttempt++
                val delayMillis = TimeUnit.SECONDS.toMillis(Math.pow(2.0, Math.min(6.0, retryAttempt)).toLong())
                Handler(Looper.getMainLooper()).postDelayed({ rewardedAd?.loadAd() }, delayMillis)
                
                onAdLoadFailedCallback?.invoke(error.message)
                onAdLoadFailedCallback = null
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                rewardedAd?.loadAd()
                onAdDisplayFailedCallback?.invoke()
                onAdClosedCallback?.invoke()
                clearCallbacks()
            }

            override fun onAdDisplayed(ad: MaxAd) {}
            override fun onAdClicked(ad: MaxAd) {}
            
            override fun onAdHidden(ad: MaxAd) {
                rewardedAd?.loadAd()
                onAdClosedCallback?.invoke()
                clearCallbacks()
            }

            override fun onRewardedVideoStarted(ad: MaxAd) {}
            override fun onRewardedVideoCompleted(ad: MaxAd) {}

            override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                if (currentRewardType == "TOP_UP") {
                    Log.d(TAG, "User earned reward top up")
                    balanceViewModel.grantRewardedTime()
                    Toast.makeText(activity, "Top Up Granted: +2 Hours Account Credit!", Toast.LENGTH_LONG).show()
                } else if (currentRewardType == "DOUBLE_UP") {
                    Log.d(TAG, "User earned reward double up")
                    balanceViewModel.grantDoubleUpReward()
                    Toast.makeText(activity, "Double Up Reward Granted! +1 Hour Bonus!", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun clearCallbacks() {
        onAdClosedCallback = null
        onAdDisplayFailedCallback = null
        currentRewardType = null
    }

    fun loadAd(onLoaded: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        if (rewardedAd?.isReady == true) {
            onLoaded()
        } else {
            onAdLoadedCallback = onLoaded
            onAdLoadFailedCallback = onFailure
            rewardedAd?.loadAd()
        }
    }

    fun showAdForTopUp(onClosed: () -> Unit = {}, onFailure: () -> Unit = {}) {
        if (rewardedAd?.isReady == true) {
            currentRewardType = "TOP_UP"
            onAdClosedCallback = onClosed
            onAdDisplayFailedCallback = onFailure
            rewardedAd?.showAd()
        } else {
            Log.e(TAG, "Rewarded ad not loaded when trying to show top up")
            onFailure()
        }
    }

    fun showAdForDoubleUp(onClosed: () -> Unit = {}, onFailure: () -> Unit = {}) {
        if (rewardedAd?.isReady == true) {
            currentRewardType = "DOUBLE_UP"
            onAdClosedCallback = onClosed
            onAdDisplayFailedCallback = onFailure
            rewardedAd?.showAd()
        } else {
            Log.e(TAG, "Rewarded ad not loaded when trying to show double up")
            onFailure()
        }
    }
}
"""

with open(path, "w") as f:
    f.write(content)
