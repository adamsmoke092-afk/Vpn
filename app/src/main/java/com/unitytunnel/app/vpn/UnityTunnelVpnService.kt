package com.unitytunnel.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.unitytunnel.app.MainActivity
import com.unitytunnel.app.R
import com.unitytunnel.app.model.ServerEndpoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UnityTunnelVpnService : VpnService() {

    companion object {
        private const val TAG = "UnityTunnelVpnService"
        private const val CHANNEL_ID = "unity_tunnel_service_channel"
        private const val NOTIFICATION_ID = 45912

        const val ACTION_CONNECT = "com.unitytunnel.app.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.unitytunnel.app.ACTION_DISCONNECT"

        const val EXTRA_SERVER_HOST = "extra_server_host"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_LOW_DATA = "extra_low_data"
        const val EXTRA_AUTO_PROTOCOL = "extra_auto_protocol"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayProcess: Process? = null
    private var tun2socksProcess: Process? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_CONNECT -> {
                    val serverHost = intent.getStringExtra(EXTRA_SERVER_HOST) ?: "127.0.0.1"
                    val serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                    val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Default Server"
                    val lowData = intent.getBooleanExtra(EXTRA_LOW_DATA, false)
                    val autoProtocol = intent.getBooleanExtra(EXTRA_AUTO_PROTOCOL, true)

                    startVpn(serverHost, serverPort, serverName, lowData, autoProtocol)
                }
                ACTION_DISCONNECT -> {
                    stopVpn()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(host: String, port: Int, serverName: String, lowData: Boolean, autoProtocol: Boolean) {
        Log.d(TAG, "Starting VPN for $serverName ($host:$port)")
        
        // Start as Foreground Service
        val notification = createNotification("Securing connection with $serverName...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // declared systemExempted in manifest as active VPN
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            // 1. Establish the TUN interface
            val builder = Builder()
            builder.addAddress("10.0.0.1", 24)
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
            builder.addRoute("0.0.0.0", 0) // Route all internet traffic through the tunnel
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(lowData)
            }
            
            vpnInterface = builder.setSession("UnityTunnelSession")
                .setConfigureIntent(getConfigureIntent())
                .establish()

            Log.d(TAG, "TUN Interface established: $vpnInterface")

            // 2. Start Xray-core subprocess with generated JSON config
            val sampleServer = ServerEndpoint("active-id", serverName, "SA", "🇿🇦", "VLESS", "ws", host, port, 20)
            val configJson = XrayConfigGenerator.generateConfig(sampleServer, lowData, autoProtocol)
            
            // Save config to a file for Xray execution
            val configFile = File(cacheDir, "xray_config.json")
            FileOutputStream(configFile).use { out ->
                out.write(configJson.toByteArray())
            }

            // Execute Xray-core child subprocess as per §4.1 Architecture Decision
            launchXraySubprocess(configFile.absolutePath)

            // 3. Launch tun2socks to bridge TUN FD to Xray SOCKS5 proxy on port 10808
            launchTun2socksSubprocess()

            // 4. Update notification to connected state
            updateNotification("Connected to $serverName", "Tap to open tunnel settings.")

            // 5. Register Network Callbacks to handle Wi-Fi/Cellular handoff
            registerNetworkCallback()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN: ${e.message}", e)
            stopVpn()
        }
    }

    private fun launchXraySubprocess(configPath: String) {
        try {
            val xrayBin = File(filesDir, "xray").absolutePath
            // If the executable binary is present, spawn process; otherwise log mock execution
            if (File(xrayBin).exists()) {
                xrayProcess = Runtime.getRuntime().exec(arrayOf(xrayBin, "-config", configPath))
                Log.d(TAG, "Xray-core subprocess spawned successfully.")
            } else {
                Log.d(TAG, "xray binary not found at $xrayBin, running in standard development mock loop mode.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to execute Xray subprocess: ${e.message}")
        }
    }

    private fun launchTun2socksSubprocess() {
        try {
            val tun2socksBin = File(filesDir, "tun2socks").absolutePath
            val fd = vpnInterface?.fd
            if (File(tun2socksBin).exists() && fd != null) {
                // Bridge tun interface file descriptor to the local SOCKS5 proxy (127.0.0.1:10808)
                tun2socksProcess = Runtime.getRuntime().exec(
                    arrayOf(tun2socksBin, "-tunFd", fd.toString(), "-proxyType", "socks5", "-proxyAddr", "127.0.0.1:10808")
                )
                Log.d(TAG, "tun2socks subprocess bridged TUN fd ($fd) successfully.")
            } else {
                Log.d(TAG, "tun2socks binary not found, running in standard development mock loop bridge mode.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to execute tun2socks subprocess: ${e.message}")
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Active underlying network connection established: $network")
                // Implement protocol fallback / seamless reconnection here if underlying IP changed
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Underlying network connection lost: $network")
                // Handle signal loss per §4.6
            }
        }

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
            networkCallback = null
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service and releasing resources")
        
        unregisterNetworkCallback()

        // Terminate subprocesses gracefully
        try {
            xrayProcess?.destroy()
            xrayProcess = null
            tun2socksProcess?.destroy()
            tun2socksProcess = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying child processes: ${e.message}")
        }

        // Close TUN interface
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        }

        stopForeground(true)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = getConfigureIntent()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Unity Tunnel VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingIntent = getConfigureIntent()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getConfigureIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Unity Tunnel Service"
            val descriptionText = "Monitors current VPN session connection status."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
