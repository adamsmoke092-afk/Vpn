import os

path = "app/src/main/AndroidManifest.xml"
with open(path, "r") as f:
    content = f.read()

target = """        <!-- AppLovin MAX SDK Key -->
        <meta-data
            android:name="applovin.sdk.key"
            android:value="YOUR_APPLOVIN_SDK_KEY_HERE" />"""

replacement = """        <!-- AppLovin MAX SDK Key -->
        <meta-data
            android:name="applovin.sdk.key"
            android:value="${APPLOVIN_SDK_KEY}" />"""

content = content.replace(target, replacement)

with open(path, "w") as f:
    f.write(content)
