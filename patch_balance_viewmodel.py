import re

with open('app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt', 'r') as f:
    content = f.read()

imports_to_add = """
import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
"""

# Insert imports after existing imports
if 'import kotlinx.coroutines.launch' in content:
    content = content.replace('import kotlinx.coroutines.launch', 'import kotlinx.coroutines.launch' + imports_to_add)
else:
    print("Could not find import insertion point")


properties_to_add = """
    private var countdownJob: Job? = null
    private var connectionStartTime: Long = 0L

    private var rewardedAd: RewardedAd? = null
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917" // Test ad unit ID
    private var adLoadRetryAttempt = 0
"""

content = re.sub(r'    private var countdownJob: Job\? = null\n    private var connectionStartTime: Long = 0L', properties_to_add, content)


init_to_modify = """
            if (_connectOnLaunch.value && _balanceSeconds.value > 0) {
                connectVpn()
            }
        }
        preloadRewardedAd()
    }
"""

content = re.sub(r'            if \(_connectOnLaunch\.value && _balanceSeconds\.value > 0\) \{\n                connectVpn\(\)\n            \}\n        \}\n    \}', init_to_modify, content)

methods_to_add = """
    private fun preloadRewardedAd() {
        val request = AdRequest.Builder().build()
        RewardedAd.load(getApplication(), adUnitId, request, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                adLoadRetryAttempt = 0
                Log.d(TAG, "Rewarded ad loaded successfully.")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
                Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                
                // Retry with backoff — 30s, then 60s, then 2min, capped.
                adLoadRetryAttempt++
                val retryDelayMillis = Math.min(Math.pow(2.0, adLoadRetryAttempt.toDouble()).toLong() * 30000, 120000)
                
                viewModelScope.launch {
                    delay(retryDelayMillis)
                    preloadRewardedAd()
                }
            }
        })
    }

    fun showRewardedAd(activity: Activity, onResult: (success: Boolean) -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onResult(false) // UI should show "ad not ready, try again shortly" — never a raw crash/blank state
            preloadRewardedAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewardedAd() // immediately start loading the NEXT ad
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                preloadRewardedAd()
                onResult(false)
            }
        }

        ad.show(activity) { rewardItem ->
            // DO NOT grant the hour here directly. Client-side callback is for immediate
            // UI feedback only. The actual balance increment must come from the server
            // after SSV confirms the reward — see SSV integration doc for the correct
            // ECDSA verification flow (not the HMAC version from the earlier draft, which
            // does not match real AdMob SSV and was flagged as incorrect).
            onResult(true)
            notifyServerAdWatched() // triggers SSV round-trip; server is source of truth for balance
        }
    }

    private fun notifyServerAdWatched() {
        Log.d(TAG, "notifyServerAdWatched: triggering SSV round-trip.")
        // For Phase 5 simulation, we call grantRewardedTime() here. 
        // In Phase 6, this will make a network request to the backend.
        grantRewardedTime()
    }

    /**
     * Watching a rewarded video ad grants +2 hours (7200 seconds) additively
"""

content = content.replace('    /**\n     * Watching a rewarded video ad grants +2 hours (7200 seconds) additively', methods_to_add)

with open('app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt', 'w') as f:
    f.write(content)
