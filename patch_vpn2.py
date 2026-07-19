import re
with open('app/src/main/java/com/unitytunnel/app/vpn/UnityTunnelVpnService.kt', 'r') as f:
    content = f.read()
old_str = """            Log.d(TAG, "TUN Interface established: $vpnInterface")"""
new_str = """            Log.d(TAG, "TUN Interface established: $vpnInterface")
            
            if (vpnInterface == null) {
                Log.e(TAG, "VpnService.establish() returned null. Permission might be missing.")
                stopVpn()
                return
            }"""
if old_str in content:
    content = content.replace(old_str, new_str)
    with open('app/src/main/java/com/unitytunnel/app/vpn/UnityTunnelVpnService.kt', 'w') as f:
        f.write(content)
