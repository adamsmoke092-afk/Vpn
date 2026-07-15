import os
import re

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace("rewardedAdService.showAdForDoubleUp(activity, onClosed = {", "rewardedAdService.showAdForDoubleUp(onClosed = {")
content = content.replace("rewardedAdService.showAdForTopUp(activity, onClosed = {}, onFailure = {", "rewardedAdService.showAdForTopUp(onClosed = {}, onFailure = {")

with open(path, "w") as f:
    f.write(content)
