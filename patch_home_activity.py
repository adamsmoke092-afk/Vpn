import os

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace(
"""fun HomeScreen(
    connectionState: VpnState,""",
"""fun HomeScreen(
    activity: Activity,
    connectionState: VpnState,""")

content = content.replace(
"""                    0 -> HomeScreen(
                        connectionState = connectionState,
                        balanceSeconds = balanceSeconds,
                        selectedServer = selectedServer,
                        onConnectTap = onConnectTap
                    )""",
"""                    0 -> HomeScreen(
                        activity = activity,
                        connectionState = connectionState,
                        balanceSeconds = balanceSeconds,
                        selectedServer = selectedServer,
                        onConnectTap = onConnectTap
                    )""")

with open(path, "w") as f:
    f.write(content)
