package com.unitytunnel.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.unitytunnel.app.viewmodel.BalanceViewModel

class RewardedAdService(
    private val context: Context,
    private val balanceViewModel: BalanceViewModel
) {
    private var rewardedAd: RewardedAd? = null
    private val TAG = "RewardedAdService"
    private val ADMOB_REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917"

    fun loadAd(onLoaded: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, ADMOB_REWARDED_TEST_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Log.d(TAG, "Rewarded ad loaded successfully")
                onLoaded()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                rewardedAd = null
                onFailure(loadAdError.message)
            }
        })
    }

    fun showAdForTopUp(activity: Activity, onClosed: () -> Unit = {}, onFailure: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad closed")
                    rewardedAd = null
                    onClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    onFailure()
                }
            }
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                balanceViewModel.grantRewardedTime()
                Toast.makeText(context, "Top Up Granted: +2 Hours Account Credit!", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e(TAG, "Rewarded ad not loaded when trying to show")
            onFailure()
        }
    }

    fun showAdForDoubleUp(activity: Activity, onClosed: () -> Unit = {}, onFailure: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad closed")
                    rewardedAd = null
                    onClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    onFailure()
                }
            }
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                balanceViewModel.grantDoubleUpReward()
                Toast.makeText(context, "Double Up Reward Granted! +1 Hour Bonus!", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e(TAG, "Rewarded ad not loaded when trying to show")
            onFailure()
        }
    }
}
