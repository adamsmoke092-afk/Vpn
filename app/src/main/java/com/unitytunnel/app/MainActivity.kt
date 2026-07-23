package com.unitytunnel.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.unitytunnel.app.ads.AdManager
import com.unitytunnel.app.data.PreferencesManager
import com.unitytunnel.app.model.ServerEndpoint
import com.unitytunnel.app.ui.theme.MyApplicationTheme
import com.unitytunnel.app.vpn.UnityTunnelVpnService
import com.unitytunnel.app.viewmodel.BalanceViewModel
import com.unitytunnel.app.viewmodel.VpnState
//import com.google.android.gms.ads.AdRequest
//import com.google.android.gms.ads.AdSize
//import com.google.android.gms.ads.AdView
import kotlinx.coroutines.launch
import java.util.Locale

import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.delay
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.unitytunnel.app.data.UiPreferences

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: BalanceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Preferences and Ad SDKs
        preferencesManager = PreferencesManager(applicationContext)
        viewModel = BalanceViewModel(application, preferencesManager)
        
        AdManager.initialize(this) {
            // Load initial standard placements
            AdManager.loadAppOpenAd(this)
            AdManager.loadConnectingInterstitial(this)
            AdManager.loadDisconnectInterstitial(this)
            com.unitytunnel.app.ads.RewardedAdService.initialize(this)
        }

        setContent {
            val darkMode by viewModel.darkMode.collectAsState()
            MyApplicationTheme(darkTheme = darkMode) {
                MainAppLayout(
                    activity = this,
                    viewModel = viewModel,
                    preferencesManager = preferencesManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // App Open Ad Placement (Triggered when tabbing back in, capped at 4h)
        AdManager.showAppOpenAdIfAvailable(preferencesManager)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(
    activity: Activity,
    viewModel: BalanceViewModel,
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val uiPreferences = remember { UiPreferences(context) }
    val customDns by uiPreferences.customDns.collectAsState(initial = "ISP Default")
    val serversJson by uiPreferences.serversJson.collectAsState(initial = null)
    
    val moshi = remember { Moshi.Builder().build() }
    val listType = Types.newParameterizedType(List::class.java, ServerEndpoint::class.java)
    val adapter = moshi.adapter<List<ServerEndpoint>>(listType)
    
    val serversList = remember(serversJson) {
        if (serversJson.isNullOrEmpty()) {
            ServerEndpoint.DEFAULT_SERVERS
        } else {
            try { adapter.fromJson(serversJson!!) ?: ServerEndpoint.DEFAULT_SERVERS }
            catch (e: Exception) { ServerEndpoint.DEFAULT_SERVERS }
        }
    }
    
    var isSyncing by remember { mutableStateOf(false) }
    val onSyncClick: () -> Unit = {
        if (!isSyncing) {
            isSyncing = true
            coroutineScope.launch {
                kotlinx.coroutines.delay(1500) // Simulate network
                isSyncing = false
                Toast.makeText(context, "Server list updated", Toast.LENGTH_SHORT).show()
                uiPreferences.setServersJson(adapter.toJson(ServerEndpoint.DEFAULT_SERVERS))
            }
        }
    }
    
    var showDnsSheet by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val connectionState by viewModel.connectionState.collectAsState()
    val balanceSeconds by viewModel.balanceSeconds.collectAsState()
    val adsToday by viewModel.adsToday.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val autoProtocol by viewModel.autoProtocol.collectAsState()
    val lowDataMode by viewModel.lowDataMode.collectAsState()
    val showDoubleUpDialog by viewModel.showDoubleUpDialog.collectAsState()


    var activeTab by remember { mutableStateOf(0) }
    var showReportIssueDialog by remember { mutableStateOf(false) }

    // Onboarding & Notification Permission Handling
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
            },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "STAY SECURE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Unity Tunnel uses a background status notification to monitor your secure VPN session, active server, and remaining time balance. This also prevents Android from aggressively stopping your VPN connection in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setOnboardingCompleted(true)
                            hasDismissedOnboarding = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ENABLE NOTIFICATIONS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setOnboardingCompleted(true)
                        hasDismissedOnboarding = true
                    }
                ) {
                    Text("SKIP", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // VPN Request Launcher
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                triggerVpnConnection(context, selectedServer, lowDataMode, autoProtocol)
                viewModel.connectVpn()
            } else {
                Toast.makeText(context, "VPN Permission Denied. Connection aborted.", Toast.LENGTH_SHORT).show()
                viewModel.disconnectVpn()
            }
        }
    )

    val onConnectTap = {
        if (connectionState == VpnState.DISCONNECTED) {
            // Placement 2: "Connecting..." Interstitial ad shown during background handshake
            AdManager.showConnectingInterstitial {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnPrepareLauncher.launch(vpnIntent)
                } else {
                    triggerVpnConnection(context, selectedServer, lowDataMode, autoProtocol)
                    viewModel.connectVpn()
                }
            }
        } else {
            // Placement 5: Disconnect Exit Moment ad shown BEFORE finishing disconnect
            AdManager.showDisconnectInterstitial {
                triggerVpnDisconnect(context)
                viewModel.disconnectVpn()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Unity Tunnel",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VLESS & Trojan Prepaid Client",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Support, contentDescription = "Support", tint = MaterialTheme.colorScheme.primary) },
                        label = { Text("Account & Support", color = MaterialTheme.colorScheme.onBackground) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            Toast.makeText(context, "Support portal coming soon!", Toast.LENGTH_SHORT).show()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.HelpOutline, contentDescription = "FAQ", tint = MaterialTheme.colorScheme.primary) },
                        label = { Text("How it Works", color = MaterialTheme.colorScheme.onBackground) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            Toast.makeText(context, "Each video ad watched tops up 1 hour!", Toast.LENGTH_SHORT).show()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Feedback, contentDescription = "Report Issue", tint = MaterialTheme.colorScheme.primary) },
                        label = { Text("Report Issue", color = MaterialTheme.colorScheme.onBackground) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showReportIssueDialog = true
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    
                    
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch { drawerState.close() }
                                showDnsSheet = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Custom DNS", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Text(customDns, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Edit DNS", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Text(
                        text = "Version 1.0.4 (South Africa Core)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "UNITY TUNNEL",
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        if (activeTab == 0 || activeTab == 2) {
                            IconButton(onClick = onSyncClick) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },

                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = "Top Up") },
                        label = { Text("Top Up") },
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Dns, contentDescription = "Servers") },
                        label = { Text("Servers") },
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main visual screens routing
                when (activeTab) {
                    0 -> HomeScreen(
                        activity = activity,
                        connectionState = connectionState,
                        balanceSeconds = balanceSeconds,
                        selectedServer = selectedServer,
                        onConnectTap = onConnectTap)
                    1 -> TopUpScreen(
                        activity = activity,
                        viewModel = viewModel,
                        balanceSeconds = balanceSeconds,
                        adsToday = adsToday,
                    )
                    2 -> ServersScreen(
                        selectedServer = selectedServer,
                        serversList = serversList,
                        onServerSelect = { server -> viewModel.selectServer(server) }
                    )
                    3 -> SettingsScreen(
                        viewModel = viewModel,
                        adsToday = adsToday,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onReportIssueClick = { showReportIssueDialog = true }
                    )
                }

                // Report Issue Dialog
                if (showReportIssueDialog) {
                    ReportIssueDialog(
                        onDismiss = { showReportIssueDialog = false },
                        onSubmit = { category, subject, description, email, attachDiagnostics ->
                            Toast.makeText(context, "Feedback submitted successfully!", Toast.LENGTH_LONG).show()
                        }
                    )
                }

                // Placement 4: "Double Up" Bonus Offer Dialog (Streak Multiplier)
                if (showDnsSheet) {
                    var customIp by remember { mutableStateOf("") }
                    ModalBottomSheet(
                        onDismissRequest = { showDnsSheet = false },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                            Text("Custom DNS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                            val dnsOptions = listOf("ISP Default", "Google DNS (8.8.8.8)", "Cloudflare DNS (1.1.1.1)", "Quad9 (9.9.9.9)", "OpenDNS (208.67.222.222)", "Custom")
                            dnsOptions.forEach { option ->
                                val isSelected = customDns == option || (option == "Custom" && dnsOptions.none { it == customDns })
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (option != "Custom") {
                                            coroutineScope.launch {
                                                uiPreferences.setCustomDns(option)
                                                showDnsSheet = false
                                                Toast.makeText(context, "DNS set to $option", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                uiPreferences.setCustomDns("Custom")
                                            }
                                        }
                                    }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(option, color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            if (customDns.startsWith("Custom") || customDns.matches(Regex("^[0-9.]+$")) && dnsOptions.none { it == customDns }) {
                                OutlinedTextField(
                                    value = customIp,
                                    onValueChange = { customIp = it },
                                    label = { Text("Enter DNS IP") },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                                Button(
                                    onClick = {
                                        if (customIp.isNotBlank()) {
                                            coroutineScope.launch {
                                                uiPreferences.setCustomDns(customIp)
                                                showDnsSheet = false
                                                Toast.makeText(context, "DNS set to $customIp", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }

                if (showDoubleUpDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDoubleUpOffer() },
                        title = {
                            Text(
                                "⚡ DOUBLE UP STREAK!",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        text = {
                            Text(
                                "Watch one more quick sponsored video and gain an extra +1 hour bonus instantly stacked on top!",
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // Watch second ad
                                    viewModel.showRewardedAd(activity, "DOUBLE_UP") { success, msg ->
                                        if (success) {
                                            viewModel.dismissDoubleUpOffer()
                                        } else {
                                            Toast.makeText(context, msg ?: "Ad not ready or failed to show.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Double Up! (+1 Hr)", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissDoubleUpOffer() }) {
                                Text("No Thanks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }
}

/**
 * 1. Home Screen Tab Layout
 */
@Composable
fun HomeScreen(
    activity: Activity,
    connectionState: VpnState,
    balanceSeconds: Long,
    selectedServer: ServerEndpoint,
    onConnectTap: () -> Unit
) {
    val context = LocalContext.current
    val circleColor = if (connectionState == VpnState.CONNECTED) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val isDevMode = !java.io.File(context.filesDir, "xray").exists()
    
    // Dynamic dial breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val radiusMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radius"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        if (isDevMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DEV MODE — NOT TUNNELING",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        // Dial Balance visualizer
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .padding(16.dp)
        ) {
            val dialBgColor = MaterialTheme.colorScheme.surface
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val sizeVal = size.width * radiusMultiplier
                val offset = (size.width - sizeVal) / 2
                
                // Draw background dial ring
                drawArc(
                    color = dialBgColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Draw active visual ring
                drawArc(
                    color = circleColor,
                    startAngle = -90f,
                    sweepAngle = (balanceSeconds.toFloat() / 44100f * 360f).coerceAtMost(360f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PREPAID AIRTIME",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Countdown timer in JetBrains Mono font
                Text(
                    text = formatTime(balanceSeconds),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (connectionState == VpnState.CONNECTED) "SECURE" else "DISCONNECTED",
                    style = MaterialTheme.typography.bodyMedium,
                    color = circleColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Active connection metadata status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedServer.flagEmoji,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = selectedServer.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        /* Protocol hidden */
                    }
                }
                Text(
                    text = "${selectedServer.pingMs} ms",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedServer.pingMs < 50) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Big Connect / Disconnect trigger Button
        Button(
            onClick = onConnectTap,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connectionState == VpnState.CONNECTED) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .testTag("connect_button"),
            border = if (connectionState == VpnState.CONNECTED) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null
        ) {
            val buttonText = when (connectionState) {
                VpnState.DISCONNECTED -> "TAP TO CONNECT"
                VpnState.CONNECTING -> "ESTABLISHING HANDSHAKE..."
                VpnState.CONNECTED -> "TAP TO DISCONNECT"
                VpnState.DISCONNECTING -> "DISCONNECTING SAFE MOMENT..."
            }
            Text(
                text = buttonText,
                color = if (connectionState == VpnState.CONNECTED) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.background,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Anchored Banner ad slot (Section 3.6 viewability aware refresh)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val isWifi = AdManager.isWifiConnected(context)
                val isAdManagerInitialized by AdManager.isInitialized.collectAsState()
                if (isWifi && isAdManagerInitialized) {
                    // Cellular-aware wifi rich banner container
                    AndroidView(
                        factory = { ctx ->
                            val sdk = com.applovin.sdk.AppLovinSdk.getInstance(activity)
                            com.applovin.mediation.ads.MaxAdView(AdManager.MAX_BANNER_AD_UNIT_ID, sdk, activity).apply {
                                setExtraParameter("allow_pause_auto_refresh_immediately", "true")
                                startAutoRefresh()
                                loadAd()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback to static lightweight adaptive banner for low data cellular
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(Icons.Default.NetworkWifi, contentDescription = "Low Data", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Lightweight Cellular Banner Active (Saving Data)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. Top Up Screen Tab Layout
 */
@Composable
fun TopUpScreen(
    activity: Activity,
    viewModel: BalanceViewModel,
    balanceSeconds: Long,
    adsToday: Int,
) {
    val context = LocalContext.current
    val isCapped = adsToday >= 12

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // Timer feedback container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("CURRENT ACCOUNT BALANCE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(balanceSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Daily ad counter indicators (Dots cap 5/day)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DAILY REWARDED TOP-UPS ($adsToday / 5)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..5) {
                    val active = i <= adsToday
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (active) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Watch Ad trigger button
        Button(
            onClick = {
                if (isCapped) {
                    Toast.makeText(context, "Come back tomorrow for new allowances!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.showRewardedAd(activity, "TOP_UP") { success, msg ->
                    if (!success) {
                        Toast.makeText(context, msg ?: "Sponsored video not ready.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCapped) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp)),
            enabled = !isCapped
        ) {
            Text(
                text = if (isCapped) "COME BACK TOMORROW" else "+1 HOUR FREE TOP-UP",
                color = if (isCapped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.background,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * 3. Servers Screen Tab Layout
 */
@Composable
fun ServersScreen(
    selectedServer: ServerEndpoint,
    serversList: List<ServerEndpoint>,
    onServerSelect: (ServerEndpoint) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "PREPAID ENDPOINTS",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Choose ultra low-latency premium endpoints configured across South African ISPs.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(serversList) { server ->
                val isSelected = server.id == selectedServer.id
                val border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                val cardColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onServerSelect(server) },
                    border = border,
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = server.flagEmoji,
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = server.name,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                /* Protocol hidden */
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${server.pingMs} ms",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (server.pingMs < 50) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = { onServerSelect(server) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. Settings Screen Tab Layout
 */
@Composable
fun SettingsScreen(
    viewModel: BalanceViewModel,
    adsToday: Int,
    onRequestNotificationPermission: () -> Unit,
    onReportIssueClick: () -> Unit
) {
    val autoProtocol by viewModel.autoProtocol.collectAsState()
    val connectOnLaunch by viewModel.connectOnLaunch.collectAsState()
    val lowDataMode by viewModel.lowDataMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "TUNNEL SETTINGS",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Manage auto-fallback and network efficiency profiles.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Setting 1: Auto Protocol Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Select Protocol", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Automatically fallbacks sequence to bypass deep packet inspection filters.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoProtocol,
                    onCheckedChange = { viewModel.setAutoProtocol(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Setting 2: Connect On Launch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Connect on Launch", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Instantly start tunnel on application launch if balance permits.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = connectOnLaunch,
                    onCheckedChange = { viewModel.setConnectOnLaunch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Setting 3: Low Data Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Low Data Mode", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Disables rich-media banners on cellular networks to conserve prepaid bandwidth.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = lowDataMode,
                    onCheckedChange = { viewModel.setLowDataMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Setting: Dark Mode / Light Mode
            val darkMode by viewModel.darkMode.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark Theme", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Toggle between Dark and Light color palettes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

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
                    Text("Notification Permission", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Enable background status monitoring.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Setting 5: Info Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Daily Ad Limit Check", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Text(
                    text = "$adsToday / 5 Ads Watched",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Report Issue Row in Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReportIssueClick() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Report an Issue", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Submit feedback, bug reports, or connection logs.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.Feedback, contentDescription = "Feedback", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Footer with links and version numbers
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Policy",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { /* Link to Privacy */ }
                )
                Text("|", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    text = "Terms of Service",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { /* Link to Terms */ }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Unity Tunnel • Powered by libXray Core v1.8.8",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * UTILS
 */
private fun triggerVpnConnection(
    context: Context,
    server: ServerEndpoint,
    lowData: Boolean,
    autoProtocol: Boolean
) {
    val intent = Intent(context, UnityTunnelVpnService::class.java).apply {
        action = UnityTunnelVpnService.ACTION_CONNECT
        putExtra(UnityTunnelVpnService.EXTRA_SERVER_HOST, server.host)
        putExtra(UnityTunnelVpnService.EXTRA_SERVER_PORT, server.port)
        putExtra(UnityTunnelVpnService.EXTRA_SERVER_NAME, server.name)
        putExtra(UnityTunnelVpnService.EXTRA_LOW_DATA, lowData)
        putExtra(UnityTunnelVpnService.EXTRA_AUTO_PROTOCOL, autoProtocol)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun triggerVpnDisconnect(context: Context) {
    val intent = Intent(context, UnityTunnelVpnService::class.java).apply {
        action = UnityTunnelVpnService.ACTION_DISCONNECT
    }
    context.startService(intent)
}

private fun formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueDialog(
    onDismiss: () -> Unit,
    onSubmit: (category: String, subject: String, description: String, email: String, attachDiagnostics: Boolean) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("Connection") }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var attachDiagnostics by remember { mutableStateOf(true) }
    
    var isSubmitting by remember { mutableStateOf(false) }
    var submitSuccess by remember { mutableStateOf(false) }
    var ticketId by remember { mutableStateOf("") }
    
    var subjectError by remember { mutableStateOf(false) }
    var descriptionError by remember { mutableStateOf(false) }

    val categories = listOf("Connection", "Speeds", "Ads", "Crash", "Other")

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 480.dp)
            .wrapContentHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Feedback,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (submitSuccess) "TICKET CREATED" else "REPORT AN ISSUE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            if (submitSuccess) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Thank you for your feedback!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Ticket ID: $ticketId\nOur South Africa engineering team has received your report along with diagnostic logs to analyze deep packet inspection bypass filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else if (isSubmitting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Uploading diagnostic logs...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Help us bypass censors and improve network performance. Submit diagnostic telemetry below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Category Selection
                    Text(
                        text = "SELECT CATEGORY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Horizontal scroll chips (Custom styled boxes)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { category = cat }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (selected) MaterialTheme.colorScheme.background 
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Subject Field
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { 
                            subject = it
                            if (it.isNotBlank()) subjectError = false
                        },
                        label = { Text("Subject / Title") },
                        isError = subjectError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("report_subject_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )
                    if (subjectError) {
                        Text(
                            text = "Subject cannot be blank",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Description Field
                    OutlinedTextField(
                        value = description,
                        onValueChange = { 
                            description = it
                            if (it.length >= 10) descriptionError = false
                        },
                        label = { Text("Description (min 10 chars)") },
                        isError = descriptionError,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("report_description_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )
                    if (descriptionError) {
                        Text(
                            text = "Please enter at least 10 characters",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Optional Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email (for updates)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("report_email_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    // Diagnostic Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { attachDiagnostics = !attachDiagnostics }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = attachDiagnostics,
                            onCheckedChange = { attachDiagnostics = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("report_diagnostics_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Attach Diagnostics Package",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Includes VPN protocol, connection state, and server ping telemetry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (submitSuccess) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("report_dismiss_button")
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                }
            } else if (!isSubmitting) {
                Button(
                    onClick = {
                        val isSubjErr = subject.isBlank()
                        val isDescErr = description.length < 10
                        subjectError = isSubjErr
                        descriptionError = isDescErr

                        if (!isSubjErr && !isDescErr) {
                            isSubmitting = true
                            // Simulate sending to network core
                            val ticket = "UT-" + (100000..999999).random().toString()
                            ticketId = ticket
                            
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            handler.postDelayed({
                                isSubmitting = false
                                submitSuccess = true
                                onSubmit(category, subject, description, email, attachDiagnostics)
                            }, 1200)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("report_submit_button")
                ) {
                    Text("Submit", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!submitSuccess && !isSubmitting) {
                TextButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.testTag("report_cancel_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}
