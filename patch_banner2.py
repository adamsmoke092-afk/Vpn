import os

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

target = """                            com.applovin.mediation.ads.MaxAdView(AdManager.MAX_BANNER_AD_UNIT_ID, activity).apply {"""

replacement = """                            val sdk = com.applovin.sdk.AppLovinSdk.getInstance(activity)
                            com.applovin.mediation.ads.MaxAdView(AdManager.MAX_BANNER_AD_UNIT_ID, sdk, activity).apply {"""

content = content.replace(target, replacement)

with open(path, "w") as f:
    f.write(content)
