import os

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    line_num = i + 1
    
    # 1. Replace all Amber with Gold
    if "0xFFFF8A3D" in line:
        line = line.replace("0xFFFF8A3D", "0xFFE1A730")
    
    # 2. Replace Teal with Gold where it shouldn't be Teal
    # Teal is 0xFF2DD4BF.
    # Keep Teal ONLY at:
    # 384: val circleColor = if (connectionState == VpnState.CONNECTED) Color(0xFF2DD4BF) else Color(0xFFE1A730)
    # 538: border = if (connectionState == VpnState.CONNECTED) BorderStroke(2.dp, Color(0xFF2DD4BF)) else null
    # 548: color = if (connectionState == VpnState.CONNECTED) Color(0xFF2DD4BF) else Color(0xFF14171C)
    
    if "0xFF2DD4BF" in line:
        if line_num not in [384, 538, 548]:
            line = line.replace("0xFF2DD4BF", "0xFFE1A730")
            
    new_lines.append(line)

with open(path, "w") as f:
    f.writelines(new_lines)
