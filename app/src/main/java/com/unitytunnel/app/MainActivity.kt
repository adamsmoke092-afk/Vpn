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
import com.unitytunnel.app.ads.RewardedAdService

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
        }

        setContent {
            MyApplicationTheme {
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

    val connectionState by viewModel.connectionState.collectAsState()
    val balanceSeconds by viewModel.balanceSeconds.collectAsState()
    val adsToday by viewModel.adsToday.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val autoProtocol by viewModel.autoProtocol.collectAsState()
    val lowDataMode by viewModel.lowDataMode.collectAsState()
    val showDoubleUpDialog by viewModel.showDoubleUpDialog.collectAsState()

    val rewardedAdService = remember { RewardedAdService(activity, viewModel) }

    var activeTab by remember { mutableStateOf(0) }

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
            containerColor = Color(0xFF1C2027),
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFFE1A730),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "STAY SECURE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF2F0EB),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Unity Tunnel uses a background status notification to monitor your secure VPN session, active server, and remaining time balance. This also prevents Android from aggressively stopping your VPN connection in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B92A0),
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
                        containerColor = Color(0xFFE1A730),
                        contentColor = Color(0xFF14171C)
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
                    Text("SKIP", color = Color(0xFF8B92A0), fontWeight = FontWeight.Bold)
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
                drawerContainerColor = Color(0xFF1C2027),
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
                        color = Color(0xFFE1A730),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VLESS & Trojan Prepaid Client",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B92A0)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Support, contentDescription = "Support", tint = Color(0xFFE1A730)) },
                        label = { Text("Account & Support", color = Color(0xFFF2F0EB)) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            Toast.makeText(context, "Support portal coming soon!", Toast.LENGTH_SHORT).show()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.HelpOutline, contentDescription = "FAQ", tint = Color(0xFFE1A730)) },
                        label = { Text("How it Works", color = Color(0xFFF2F0EB)) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            Toast.makeText(context, "Each video ad watched tops up 2 hours!", Toast.LENGTH_SHORT).show()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "Version 1.0.4 (South Africa Core)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8B92A0),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFF14171C),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "UNITY TUNNEL",
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFFF2F0EB)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFFE1A730))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF14171C)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1C2027),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE1A730),
                            selectedTextColor = Color(0xFFE1A730),
                            unselectedIconColor = Color(0xFF8B92A0),
                            unselectedTextColor = Color(0xFF8B92A0),
                            indicatorColor = Color(0xFF242933)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = "Top Up") },
                        label = { Text("Top Up") },
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE1A730),
                            selectedTextColor = Color(0xFFE1A730),
                            unselectedIconColor = Color(0xFF8B92A0),
                            unselectedTextColor = Color(0xFF8B92A0),
                            indicatorColor = Color(0xFF242933)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Dns, contentDescription = "Servers") },
                        label = { Text("Servers") },
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE1A730),
                            selectedTextColor = Color(0xFFE1A730),
                            unselectedIconColor = Color(0xFF8B92A0),
                            unselectedTextColor = Color(0xFF8B92A0),
                            indicatorColor = Color(0xFF242933)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE1A730),
                            selectedTextColor = Color(0xFFE1A730),
                            unselectedIconColor = Color(0xFF8B92A0),
                            unselectedTextColor = Color(0xFF8B92A0),
                            indicatorColor = Color(0xFF242933)
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
                        onConnectTap = onConnectTap
                    )
                    1 -> TopUpScreen(
                        activity = activity,
                        viewModel = viewModel,
                        balanceSeconds = balanceSeconds,
                        adsToday = adsToday,
                        rewardedAdService = rewardedAdService
                    )
                    2 -> ServersScreen(
                        selectedServer = selectedServer,
                        onServerSelect = { server -> viewModel.selectServer(server) }
                    )
                    3 -> SettingsScreen(
                        viewModel = viewModel,
                        adsToday = adsToday,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                // Placement 4: "Double Up" Bonus Offer Dialog (Streak Multiplier)
                if (showDoubleUpDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDoubleUpOffer() },
                        title = {
                            Text(
                                "⚡ DOUBLE UP STREAK!",
                                color = Color(0xFFE1A730),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        text = {
                            Text(
                                "Watch one more quick sponsored video and gain an extra +1 hour bonus instantly stacked on top!",
                                color = Color(0xFFF2F0EB)
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // Watch second ad
                                    rewardedAdService.loadAd(onLoaded = {
                                        rewardedAdService.showAdForDoubleUp(onClosed = {
                                            viewModel.dismissDoubleUpOffer()
                                        })
                                    })
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1A730))
                            ) {
                                Text("Double Up! (+1 Hr)", color = Color(0xFF14171C), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissDoubleUpOffer() }) {
                                Text("No Thanks", color = Color(0xFF8B92A0))
                            }
                        },
                        containerColor = Color(0xFF1C2027)
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
    val circleColor = if (connectionState == VpnState.CONNECTED) Color(0xFF2DD4BF) else Color(0xFFE1A730)
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (isDevMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE53E3E), RoundedCornerShape(8.dp))
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val sizeVal = size.width * radiusMultiplier
                val offset = (size.width - sizeVal) / 2
                
                // Draw background dial ring
                drawArc(
                    color = Color(0xFF1C2027),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Draw active visual ring
                drawArc(
                    color = circleColor,
                    startAngle = -90f,
                    sweepAngle = (balanceSeconds.toFloat() / 7200L * 360f).coerceAtMost(360f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PREPAID AIRTIME",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B92A0),
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
                    color = Color(0xFFF2F0EB)
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2027))
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
                            color = Color(0xFFF2F0EB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Protocol: ${selectedServer.protocol} • Transport: ${selectedServer.transport.uppercase()}",
                            color = Color(0xFF8B92A0),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = "${selectedServer.pingMs} ms",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedServer.pingMs < 50) Color(0xFFE1A730) else Color(0xFF8B92A0)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Connect / Disconnect trigger Button
        Button(
            onClick = onConnectTap,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connectionState == VpnState.CONNECTED) Color(0xFF1C2027) else Color(0xFFE1A730)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .testTag("connect_button"),
            border = if (connectionState == VpnState.CONNECTED) BorderStroke(2.dp, Color(0xFF2DD4BF)) else null
        ) {
            val buttonText = when (connectionState) {
                VpnState.DISCONNECTED -> "TAP TO CONNECT"
                VpnState.CONNECTING -> "ESTABLISHING HANDSHAKE..."
                VpnState.CONNECTED -> "TAP TO DISCONNECT"
                VpnState.DISCONNECTING -> "DISCONNECTING SAFE MOMENT..."
            }
            Text(
                text = buttonText,
                color = if (connectionState == VpnState.CONNECTED) Color(0xFF2DD4BF) else Color(0xFF14171C),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2027)),
            border = BorderStroke(1.dp, Color(0xFF242933))
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
                        Icon(Icons.Default.NetworkWifi, contentDescription = "Low Data", tint = Color(0xFF8B92A0))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Lightweight Cellular Banner Active (Saving Data)",
                            color = Color(0xFF8B92A0),
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
    rewardedAdService: RewardedAdService
) {
    val context = LocalContext.current
    val isCapped = adsToday >= 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.AddAlarm,
                contentDescription = "Clock Topup",
                modifier = Modifier.size(72.dp),
                tint = Color(0xFFE1A730)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TOP UP AIRTIME",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFF2F0EB),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Watch a fast sponsored video to receive +2 hours high-speed connection time. Balance stacks additively.",
                textAlign = TextAlign.Center,
                color = Color(0xFF8B92A0),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // Timer feedback container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2027))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("CURRENT ACCOUNT BALANCE", color = Color(0xFF8B92A0), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(balanceSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFFE1A730),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Daily ad counter indicators (Dots cap 5/day)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DAILY REWARDED TOP-UPS ($adsToday / 5)",
                color = Color(0xFF8B92A0),
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
                            .background(if (active) Color(0xFFE1A730) else Color(0xFF242933))
                            .border(1.dp, if (active) Color.Transparent else Color(0xFF8B92A0), CircleShape)
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
                // Preload ad
                rewardedAdService.loadAd(onLoaded = {
                    rewardedAdService.showAdForTopUp(onClosed = {}, onFailure = {
                        Toast.makeText(context, "Failed to present sponsored video. Try again shortly.", Toast.LENGTH_SHORT).show()
                    })
                }, onFailure = { err ->
                    Toast.makeText(context, "No available sponsored video fill: $err", Toast.LENGTH_SHORT).show()
                })
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCapped) Color(0xFF242933) else Color(0xFFE1A730)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp)),
            enabled = !isCapped
        ) {
            Text(
                text = if (isCapped) "COME BACK TOMORROW" else "+2 HOURS FREE TOP-UP",
                color = if (isCapped) Color(0xFF8B92A0) else Color(0xFF14171C),
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
            color = Color(0xFFF2F0EB)
        )
        Text(
            text = "Choose ultra low-latency premium endpoints configured across South African ISPs.",
            color = Color(0xFF8B92A0),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ServerEndpoint.DEFAULT_SERVERS) { server ->
                val isSelected = server.id == selectedServer.id
                val border = if (isSelected) BorderStroke(1.5.dp, Color(0xFFE1A730)) else BorderStroke(1.dp, Color(0xFF242933))
                val cardColor = if (isSelected) Color(0xFF242933) else Color(0xFF1C2027)

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
                                    color = Color(0xFFF2F0EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${server.protocol} • ${server.transport.uppercase()}",
                                    color = Color(0xFF8B92A0),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${server.pingMs} ms",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (server.pingMs < 50) Color(0xFFE1A730) else Color(0xFF8B92A0)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = { onServerSelect(server) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFE1A730),
                                    unselectedColor = Color(0xFF8B92A0)
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
    onRequestNotificationPermission: () -> Unit
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
                color = Color(0xFFF2F0EB)
            )
            Text(
                text = "Manage auto-fallback and network efficiency profiles.",
                color = Color(0xFF8B92A0),
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
                    Text("Auto-Select Protocol", color = Color(0xFFF2F0EB), fontWeight = FontWeight.Bold)
                    Text(
                        "Automatically fallbacks sequence to bypass deep packet inspection filters.",
                        color = Color(0xFF8B92A0),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoProtocol,
                    onCheckedChange = { viewModel.setAutoProtocol(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF14171C),
                        checkedTrackColor = Color(0xFFE1A730)
                    )
                )
            }

            Divider(color = Color(0xFF242933), modifier = Modifier.padding(vertical = 8.dp))

            // Setting 2: Connect On Launch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Connect on Launch", color = Color(0xFFF2F0EB), fontWeight = FontWeight.Bold)
                    Text(
                        "Instantly start tunnel on application launch if balance permits.",
                        color = Color(0xFF8B92A0),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = connectOnLaunch,
                    onCheckedChange = { viewModel.setConnectOnLaunch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF14171C),
                        checkedTrackColor = Color(0xFFE1A730)
                    )
                )
            }

            Divider(color = Color(0xFF242933), modifier = Modifier.padding(vertical = 8.dp))

            // Setting 3: Low Data Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Low Data Mode", color = Color(0xFFF2F0EB), fontWeight = FontWeight.Bold)
                    Text(
                        "Disables rich-media banners on cellular networks to conserve prepaid bandwidth.",
                        color = Color(0xFF8B92A0),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = lowDataMode,
                    onCheckedChange = { viewModel.setLowDataMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF14171C),
                        checkedTrackColor = Color(0xFFE1A730)
                    )
                )
            }

            Divider(color = Color(0xFF242933), modifier = Modifier.padding(vertical = 8.dp))

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

            // Setting 5: Info Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Daily Ad Limit Check", color = Color(0xFFF2F0EB), fontWeight = FontWeight.Bold)
                Text(
                    text = "$adsToday / 5 Ads Watched",
                    color = Color(0xFFE1A730),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
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
                    color = Color(0xFFE1A730),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { /* Link to Privacy */ }
                )
                Text("|", color = Color(0xFF8B92A0), fontSize = 13.sp)
                Text(
                    text = "Terms of Service",
                    color = Color(0xFFE1A730),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { /* Link to Terms */ }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Unity Tunnel • Powered by libXray Core v1.8.8",
                color = Color(0xFF8B92A0),
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
