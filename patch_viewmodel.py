import re
with open('app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt', 'r') as f:
    content = f.read()
content = re.sub(r'            // Auto-connect on launch if enabled and balance > 0\n\s*if \(_connectOnLaunch\.value && _balanceSeconds\.value > 0\) \{\n\s*connectVpn\(\)\n\s*\}\n', '', content)
with open('app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt', 'w') as f:
    f.write(content)
