import os

path = "app/src/main/java/com/unitytunnel/app/ads/RewardedAdService.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "rewardedAd?.loadAd()",
    "if (!AdManager.MAX_REWARDED_AD_UNIT_ID.startsWith(\"YOUR_\")) { rewardedAd?.loadAd() }"
)

with open(path, "w") as f:
    f.write(content)
