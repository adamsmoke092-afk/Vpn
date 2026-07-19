import re

with open('app/src/main/java/com/unitytunnel/app/UnityTunnelApplication.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.unitytunnel.app.ads.AdManager', 'import com.unitytunnel.app.ads.AdManager\nimport com.google.android.gms.ads.MobileAds')

content = content.replace('        AdManager.initialize(this)', '        MobileAds.initialize(this) {}\n        AdManager.initialize(this)')

with open('app/src/main/java/com/unitytunnel/app/UnityTunnelApplication.kt', 'w') as f:
    f.write(content)
