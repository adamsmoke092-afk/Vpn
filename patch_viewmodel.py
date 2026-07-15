import os

path = "app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt"
with open(path, "r") as f:
    content = f.read()

target = """                    if (newBalance == 0L) {
                        // Section 2 hard rule: Reset balance back to 15 mins (900 seconds) and auto disconnect
                        Log.d(TAG, "Balance hit zero. Resetting to 15 mins and disconnecting VPN.")
                        disconnectVpn()
                        _balanceSeconds.value = 900L
                        preferencesManager.saveBalanceSeconds(900L)
                        break
                    }"""

replacement = """                    if (newBalance == 0L) {
                        // Section 2 hard rule: Reset balance back to 15 mins (900 seconds) and auto disconnect
                        Log.d(TAG, "Balance hit zero. Resetting to 15 mins and disconnecting VPN.")
                        _balanceSeconds.value = 900L
                        preferencesManager.saveBalanceSeconds(900L)
                        disconnectVpn()
                        break
                    }"""

content = content.replace(target, replacement)

with open(path, "w") as f:
    f.write(content)
