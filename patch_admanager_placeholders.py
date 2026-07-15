import os

path = "app/src/main/java/com/unitytunnel/app/ads/AdManager.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "connectingInterstitialAd?.loadAd()",
    "if (!MAX_CONNECTING_INTERSTITIAL_AD_UNIT_ID.startsWith(\"YOUR_\")) { connectingInterstitialAd?.loadAd() }"
)

content = content.replace(
    "disconnectInterstitialAd?.loadAd()",
    "if (!MAX_DISCONNECT_INTERSTITIAL_AD_UNIT_ID.startsWith(\"YOUR_\")) { disconnectInterstitialAd?.loadAd() }"
)

content = content.replace(
    "appOpenAd?.loadAd()",
    "if (!MAX_APP_OPEN_AD_UNIT_ID.startsWith(\"YOUR_\")) { appOpenAd?.loadAd() }"
)

with open(path, "w") as f:
    f.write(content)
