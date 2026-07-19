import re

with open('app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt', 'r') as f:
    content = f.read()

# Modify showRewardedAd to take rewardType
old_show = """    fun showRewardedAd(activity: Activity, onResult: (success: Boolean) -> Unit) {"""
new_show = """    fun showRewardedAd(activity: Activity, rewardType: String = "TOP_UP", onResult: (success: Boolean) -> Unit) {"""
content = content.replace(old_show, new_show)

# Modify notifyServerAdWatched
old_notify = """            notifyServerAdWatched() // triggers SSV round-trip; server is source of truth for balance
        }
    }

    private fun notifyServerAdWatched() {
        Log.d(TAG, "notifyServerAdWatched: triggering SSV round-trip.")
        // For Phase 5 simulation, we call grantRewardedTime() here. 
        // In Phase 6, this will make a network request to the backend.
        grantRewardedTime()
    }"""
new_notify = """            notifyServerAdWatched(rewardType) // triggers SSV round-trip; server is source of truth for balance
        }
    }

    private fun notifyServerAdWatched(rewardType: String) {
        Log.d(TAG, "notifyServerAdWatched: triggering SSV round-trip for $rewardType")
        // For Phase 5 simulation, we call the appropriate direct grant method
        if (rewardType == "DOUBLE_UP") {
            grantDoubleUpReward()
        } else {
            grantRewardedTime()
        }
    }"""
content = content.replace(old_notify, new_notify)

with open('app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt', 'w') as f:
    f.write(content)
