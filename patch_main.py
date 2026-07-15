import os

path = "app/src/main/java/com/unitytunnel/app/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

# 1. Update onboarding logic
old_onboarding_logic = """    // Onboarding & Notification Permission Handling
    val onboardingPrefs = remember { context.getSharedPreferences("unity_tunnel_onboarding", Context.MODE_PRIVATE) }
    var showOnboarding by remember {
        mutableStateOf(
            !onboardingPrefs.getBoolean("onboarding_completed", false) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            onboardingPrefs.edit().putBoolean("onboarding_completed", true).apply()
            showOnboarding = false
            if (isGranted) {
                Toast.makeText(context, "Status notification enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "VPN status will run quietly in background.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {
                onboardingPrefs.edit().putBoolean("onboarding_completed", true).apply()
                showOnboarding = false
            },"""

new_onboarding_logic = """    // Onboarding & Notification Permission Handling
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    var hasDismissedOnboarding by remember { mutableStateOf(false) }
    
    val showOnboarding = !onboardingCompleted && 
                         !hasDismissedOnboarding &&
                         Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                         ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            viewModel.setOnboardingCompleted(true)
            hasDismissedOnboarding = true
            if (isGranted) {
                Toast.makeText(context, "Status notification enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "VPN status will run quietly in background.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setOnboardingCompleted(true)
                hasDismissedOnboarding = true
            },"""

content = content.replace(old_onboarding_logic, new_onboarding_logic)

content = content.replace(
"""                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onboardingPrefs.edit().putBoolean("onboarding_completed", true).apply()
                            showOnboarding = false
                        }""",
"""                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setOnboardingCompleted(true)
                            hasDismissedOnboarding = true
                        }""")

content = content.replace(
"""                    onClick = {
                        onboardingPrefs.edit().putBoolean("onboarding_completed", true).apply()
                        showOnboarding = false
                    }""",
"""                    onClick = {
                        viewModel.setOnboardingCompleted(true)
                        hasDismissedOnboarding = true
                    }""")


# 2. Add SettingsScreen row and update signature
content = content.replace(
"""fun SettingsScreen(
    viewModel: BalanceViewModel,
    adsToday: Int
) {""",
"""fun SettingsScreen(
    viewModel: BalanceViewModel,
    adsToday: Int,
    onRequestNotificationPermission: () -> Unit
) {""")

settings_row = """            Divider(color = Color(0xFF242933), modifier = Modifier.padding(vertical = 8.dp))

            // Setting 4: Info Display"""
            
new_settings_row = """            Divider(color = Color(0xFF242933), modifier = Modifier.padding(vertical = 8.dp))

            // Setting 4: Notification Permission
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestNotificationPermission() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification Permission", color = Color(0xFFF2F0EB), fontWeight = FontWeight.Bold)
                    Text(
                        "Enable background status monitoring.",
                        color = Color(0xFF8B92A0),
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFE1A730))
            }

            Divider(color = Color(0xFF242933), modifier = Modifier.padding(vertical = 8.dp))

            // Setting 5: Info Display"""

content = content.replace(settings_row, new_settings_row)

content = content.replace(
"""                    3 -> SettingsScreen(
                        viewModel = viewModel,
                        adsToday = adsToday
                    )""",
"""                    3 -> SettingsScreen(
                        viewModel = viewModel,
                        adsToday = adsToday,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )""")

# 3. Remove hasPermission logic in triggerVpnConnection
content = content.replace(
"""    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        // If not granted, the foreground service still starts safely but stays hidden/quiet in the background.
    }
    val intent = Intent(context, UnityTunnelVpnService::class.java).apply {""",
"""    val intent = Intent(context, UnityTunnelVpnService::class.java).apply {""")

with open(path, "w") as f:
    f.write(content)

