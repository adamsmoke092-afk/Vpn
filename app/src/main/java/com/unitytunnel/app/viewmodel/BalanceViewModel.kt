package com.unitytunnel.app.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unitytunnel.app.data.PreferencesManager
import com.unitytunnel.app.model.ServerEndpoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.app.Activity

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val DAILY_AD_CAP = 12

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

class BalanceViewModel(
    application: Application,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val TAG = "BalanceViewModel"

    private val _balanceSeconds = MutableStateFlow(900L) // Default 15 minutes
    val balanceSeconds: StateFlow<Long> = _balanceSeconds.asStateFlow()

    private val _adsToday = MutableStateFlow(0)
    val adsToday: StateFlow<Int> = _adsToday.asStateFlow()

    private val _connectionState = MutableStateFlow(VpnState.DISCONNECTED)
    val connectionState: StateFlow<VpnState> = _connectionState.asStateFlow()

    private val _selectedServer = MutableStateFlow(ServerEndpoint.DEFAULT_SERVERS[0])
    val selectedServer: StateFlow<ServerEndpoint> = _selectedServer.asStateFlow()

    // Settings
    private val _autoProtocol = MutableStateFlow(true)
    val autoProtocol: StateFlow<Boolean> = _autoProtocol.asStateFlow()

    private val _connectOnLaunch = MutableStateFlow(false)
    val connectOnLaunch: StateFlow<Boolean> = _connectOnLaunch.asStateFlow()

    private val _lowDataMode = MutableStateFlow(false)
    val lowDataMode: StateFlow<Boolean> = _lowDataMode.asStateFlow()

    private val _darkMode = MutableStateFlow(true)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    // Double Up Banner/Offer state
    private val _showDoubleUpDialog = MutableStateFlow(false)
    val showDoubleUpDialog: StateFlow<Boolean> = _showDoubleUpDialog.asStateFlow()


    private var countdownJob: Job? = null
    private var connectionStartTime: Long = 0L

    private val adUnitId = "ca-app-pub-3940256099942544/5224354917" // Test ad unit ID


    init {
        viewModelScope.launch {
            // Load state from DataStore
            _balanceSeconds.value = preferencesManager.balanceSeconds.first()
            _adsToday.value = preferencesManager.adsToday.first()
            
            // Check daily ad cap reset
            checkDailyAdReset()

            // Load selected server
            val savedServerId = preferencesManager.selectedServerId.first()
            val savedServer = ServerEndpoint.DEFAULT_SERVERS.find { it.id == savedServerId }
            if (savedServer != null) {
                _selectedServer.value = savedServer
            }

            // Load settings
            _autoProtocol.value = preferencesManager.autoProtocol.first()
            _connectOnLaunch.value = preferencesManager.connectOnLaunch.first()
            _lowDataMode.value = preferencesManager.lowDataMode.first()
            _darkMode.value = preferencesManager.darkMode.first()
            _onboardingCompleted.value = preferencesManager.onboardingCompleted.first()

        }
    }


    private suspend fun checkDailyAdReset() {
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)
        val lastReset = preferencesManager.lastAdResetDay.first()
        if (lastReset != todayStr) {
            preferencesManager.saveAdsToday(0)
            preferencesManager.saveLastAdResetDay(todayStr)
            _adsToday.value = 0
            Log.d(TAG, "Daily ad cap reset successfully for $todayStr")
        }
    }

    /**
     * Tapping Connect VPN Action.
     */
    fun connectVpn() {
        if (_balanceSeconds.value <= 0) {
            Log.d(TAG, "Cannot connect: Balance is zero")
            return
        }
        viewModelScope.launch {
            _connectionState.value = VpnState.CONNECTING
            // Simulate handshake latency (2-5s) as described in §3.2
            delay(2000)
            _connectionState.value = VpnState.CONNECTED
            connectionStartTime = System.currentTimeMillis()
            startCountdown()
        }
    }

    /**
     * Tapping Disconnect VPN Action.
     */
    fun disconnectVpn() {
        viewModelScope.launch {
            _connectionState.value = VpnState.DISCONNECTING
            stopCountdown()
            delay(1000)
            _connectionState.value = VpnState.DISCONNECTED
            val sessionDurationSeconds = (System.currentTimeMillis() - connectionStartTime) / 1000
            Log.d(TAG, "Session disconnected. Protected for $sessionDurationSeconds seconds.")
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_connectionState.value == VpnState.CONNECTED) {
                delay(1000)
                if (_balanceSeconds.value > 0) {
                    val newBalance = _balanceSeconds.value - 1
                    _balanceSeconds.value = newBalance
                    preferencesManager.saveBalanceSeconds(newBalance)
                    
                    if (newBalance == 0L) {
                        // Section 2 hard rule: Reset balance back to 15 mins (900 seconds) and auto disconnect
                        Log.d(TAG, "Balance hit zero. Resetting to 15 mins and disconnecting VPN.")
                        _balanceSeconds.value = 900L
                        preferencesManager.saveBalanceSeconds(900L)
                        disconnectVpn()
                        break
                    }
                }
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }


    fun showRewardedAd(activity: Activity, rewardType: String = "TOP_UP", onResult: (success: Boolean, msg: String?) -> Unit) {
        if (_adsToday.value >= DAILY_AD_CAP) {
            onResult(false, "Daily limit reached. Come back tomorrow.")
            return
        }
        com.unitytunnel.app.ads.RewardedAdService.showRewardedAd(
            onSuccess = {
                onResult(true, null)
                notifyServerAdWatched(rewardType) // triggers SSV round-trip; server is source of truth for balance
            },
            onFail = {
                onResult(false, "Sponsored video not ready. Try again shortly.") // UI should show "ad not ready, try again shortly"
            }
        )
    }

    private fun notifyServerAdWatched(rewardType: String) {
        Log.d(TAG, "notifyServerAdWatched: triggering SSV round-trip for $rewardType")
        // For Phase 5 simulation, we call the appropriate direct grant method
        if (rewardType == "DOUBLE_UP") {
            grantDoubleUpReward()
        } else {
            grantRewardedTime()
        }
    }

    /**
     * Watching a rewarded video ad grants +1 hour (3600 seconds) additively

     */
    fun grantRewardedTime() {
        viewModelScope.launch {
            checkDailyAdReset()
            val currentAds = _adsToday.value
            if (currentAds >= DAILY_AD_CAP) {
                Log.e(TAG, "Cannot grant reward: ad cap of $DAILY_AD_CAP/day exceeded")
                return@launch
            }

            // Add +1 hour
            val newBalance = _balanceSeconds.value + 3600L
            _balanceSeconds.value = newBalance
            preferencesManager.saveBalanceSeconds(newBalance)

            // Increment daily ad counter
            val newAdsCount = currentAds + 1
            _adsToday.value = newAdsCount
            preferencesManager.saveAdsToday(newAdsCount)

            Log.d(TAG, "Earned reward: +1 Hour added. Total: $newBalance s, Ads Today: $newAdsCount")

            // Show double-up bonus offer if this was the first ad of the day
            if (newAdsCount == 1) {
                _showDoubleUpDialog.value = true
            }
        }
    }

    /**
     * Accept and complete Double Up Bonus: grants bonus hour.
     */
    fun grantDoubleUpReward() {
        viewModelScope.launch {
            // Grant an extra 1 hour (3600 seconds) as a bonus
            val newBalance = _balanceSeconds.value + 3600L
            _balanceSeconds.value = newBalance
            preferencesManager.saveBalanceSeconds(newBalance)
            _showDoubleUpDialog.value = false
            Log.d(TAG, "Double Up Reward completed! +1 Hour bonus added.")
        }
    }

    fun dismissDoubleUpOffer() {
        _showDoubleUpDialog.value = false
    }

    fun selectServer(server: ServerEndpoint) {
        viewModelScope.launch {
            _selectedServer.value = server
            preferencesManager.saveSelectedServerId(server.id)
            // If connected, reconnect to apply server settings
            if (_connectionState.value == VpnState.CONNECTED) {
                disconnectVpn()
                delay(500)
                connectVpn()
            }
        }
    }

    fun setAutoProtocol(enabled: Boolean) {
        viewModelScope.launch {
            _autoProtocol.value = enabled
            preferencesManager.setAutoProtocol(enabled)
        }
    }

    fun setConnectOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            _connectOnLaunch.value = enabled
            preferencesManager.setConnectOnLaunch(enabled)
        }
    }

    fun setLowDataMode(enabled: Boolean) {
        viewModelScope.launch {
            _lowDataMode.value = enabled
            preferencesManager.setLowDataMode(enabled)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            _darkMode.value = enabled
            preferencesManager.setDarkMode(enabled)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            _onboardingCompleted.value = completed
            preferencesManager.setOnboardingCompleted(completed)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdown()
    }
}
