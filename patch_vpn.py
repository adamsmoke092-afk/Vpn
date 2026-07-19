import re

with open('app/src/main/java/com/unitytunnel/app/vpn/UnityTunnelVpnService.kt', 'r') as f:
    content = f.read()

old_content = """            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(lowData)
            }"""
new_content = """            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(lowData)
            }
            
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                Log.e(TAG, "Failed to exclude app from VPN: ${e.message}")
            }"""

if old_content in content:
    content = content.replace(old_content, new_content)
    with open('app/src/main/java/com/unitytunnel/app/vpn/UnityTunnelVpnService.kt', 'w') as f:
        f.write(content)
    print("Patched successfully")
else:
    print("Could not find content")
