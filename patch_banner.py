import os

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

target = """                val isWifi = AdManager.isWifiConnected(context)
                if (isWifi) {"""

replacement = """                val isWifi = AdManager.isWifiConnected(context)
                val isAdManagerInitialized by AdManager.isInitialized.collectAsState()
                if (isWifi && isAdManagerInitialized) {"""

content = content.replace(target, replacement)

with open(path, "w") as f:
    f.write(content)
