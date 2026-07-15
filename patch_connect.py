import os

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace("AdManager.showInterstitialAd(activity) {", "AdManager.showConnectingInterstitial {")
content = content.replace("AdManager.showDisconnectAd(activity) {", "AdManager.showDisconnectInterstitial {")
content = content.replace("AdManager.showAppOpenAdIfAvailable(activity, preferencesManager)", "AdManager.showAppOpenAdIfAvailable(preferencesManager)")
content = content.replace("AdManager.loadInterstitialAd(this)", "AdManager.loadConnectingInterstitial(this)")
content = content.replace("AdManager.loadDisconnectAd(this)", "AdManager.loadDisconnectInterstitial(this)")
content = content.replace("AdManager.loadAppOpenAd(this)", "AdManager.loadAppOpenAd(this)")

with open(path, "w") as f:
    f.write(content)
