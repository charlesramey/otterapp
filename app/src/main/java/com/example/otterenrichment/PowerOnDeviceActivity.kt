package com.example.otterenrichment

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.otterenrichment.databinding.ActivityPowerOnDeviceBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PowerOnDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPowerOnDeviceBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiNetworkAdapter: WifiNetworkAdapter
    private var isScanning = false
    private var previousNetworks = setOf<String>()
    private var connectedToDeviceNetwork = false

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    handleScanResults()
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    checkWifiConnection()
                }
            }
        }
    }

    // Recognized device name prefixes
    private val recognizedDeviceNames = listOf("scallop", "otter", "enrichment")

    companion object {
        private const val PERMISSION_REQUEST_CODE = 3001
        private const val SCAN_TIMEOUT_MS = 10000L // 10 seconds
        private const val SCAN_INTERVAL_MS = 1000L // 1 Hz (1 second)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPowerOnDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        setupUI()
        checkPermissions()
        registerWifiReceiver()
    }

    private fun registerWifiReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(wifiScanReceiver, filter)
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            stopScanning()
            finish()
        }

        // Setup RecyclerView
        wifiNetworkAdapter = WifiNetworkAdapter { network ->
            onNetworkSelected(network)
        }

        binding.recyclerWifiNetworks.apply {
            layoutManager = LinearLayoutManager(this@PowerOnDeviceActivity)
            adapter = wifiNetworkAdapter
        }

        // Start scanning button
        binding.btnStartScanning.setOnClickListener {
            startDeviceDetection()
        }

        // Stop scanning button
        binding.btnStopScanning.setOnClickListener {
            stopScanning()
        }

        // Initially show start button, hide stop button
        binding.btnStopScanning.visibility = android.view.View.GONE
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startDeviceDetection() {
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please enable WiFi first", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        previousNetworks = getCurrentNetworkSSIDs()

        binding.btnStartScanning.visibility = android.view.View.GONE
        binding.btnStopScanning.visibility = android.view.View.VISIBLE
        binding.tvInstructions.text = "Scanning for WiFi networks... Tap a network to connect"
        binding.tvStatus.text = "Scanning WiFi networks..."
        binding.progressBar.visibility = android.view.View.VISIBLE

        // Start continuous WiFi scanning at 1 Hz
        startContinuousScanning()
    }

    private fun startContinuousScanning() {
        lifecycleScope.launch {
            while (isScanning) {
                performWifiScan()
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun performWifiScan() {
        val scanStarted = wifiManager.startScan()
        if (!scanStarted) {
            binding.tvStatus.text = "Scan failed, retrying..."
        }
    }

    private fun getCurrentNetworkSSIDs(): Set<String> {
        val scanResults = wifiManager.scanResults
        return scanResults.map { it.SSID }.toSet()
    }

    private fun handleScanResults() {
        val scanResults = wifiManager.scanResults
        val currentNetworks = scanResults.map { it.SSID }.toSet()

        // Find new networks that match recognized device names
        val newDevices = scanResults.filter { result ->
            val ssid = result.SSID.lowercase()
            val isNew = !previousNetworks.contains(result.SSID)
            val isRecognized = recognizedDeviceNames.any { ssid.startsWith(it) }
            isNew && isRecognized
        }

        if (newDevices.isNotEmpty()) {
            // New device detected!
            onDeviceDetected(newDevices)
        }

        // Update the network list - show ALL networks (same as TutorialActivity)
        val networks = scanResults
            .map { it.toWifiNetwork() }
            .sortedByDescending { it.signal }

        wifiNetworkAdapter.submitList(networks)

        if (isScanning) {
            binding.tvStatus.text = "Scanning WiFi... Found ${networks.size} network(s)"
        }
    }

    private fun onDeviceDetected(devices: List<android.net.wifi.ScanResult>) {
        // Don't show popup if already connected to a device network
        if (connectedToDeviceNetwork) {
            return
        }

        stopScanning()

        val device = devices.first()
        val deviceName = device.SSID

        binding.tvInstructions.text = "✓ New device detected!"
        binding.tvStatus.text = "Device: $deviceName"
        binding.progressBar.visibility = android.view.View.GONE

        // Show prompt to connect
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Device Detected!")
            .setMessage("A new device '$deviceName' has been detected.\n\nWould you like to connect to it?")
            .setPositiveButton("Connect") { _, _ ->
                onNetworkSelected(device.toWifiNetwork())
            }
            .setNegativeButton("Cancel") { _, _ ->
                resetUI()
            }
            .setCancelable(false)
            .show()
    }

    private fun stopScanning() {
        isScanning = false

        binding.btnStartScanning.visibility = android.view.View.VISIBLE
        binding.btnStopScanning.visibility = android.view.View.GONE
        binding.progressBar.visibility = android.view.View.GONE
    }

    private fun resetUI() {
        binding.tvInstructions.text = "Tap 'Start Scanning' to scan for WiFi networks from devices"
        binding.tvStatus.text = "Ready to scan WiFi networks"
        binding.btnStartScanning.visibility = android.view.View.VISIBLE
        binding.btnStopScanning.visibility = android.view.View.GONE
    }

    private fun onNetworkSelected(network: WifiNetwork) {
        // Android 10+ removed the ability for apps to programmatically switch WiFi
        // We must use the system WiFi settings panel
        val message = if (network.isSecured) {
            "Please connect to this network in WiFi settings.\n\n" +
            "Network: ${network.ssid}\n" +
            "Security: ${if (network.capabilities.contains("WPA3")) "WPA3" else if (network.capabilities.contains("WPA2")) "WPA2" else "WPA"}\n\n" +
            if (recognizedDeviceNames.any { network.ssid.lowercase().startsWith(it) }) {
                "💡 Tip: Default password for device networks is usually 'password'"
            } else {
                "Enter your WiFi password when prompted"
            }
        } else {
            "Please connect to this open network in WiFi settings.\n\nNetwork: ${network.ssid}"
        }

        AlertDialog.Builder(this)
            .setTitle("Connect to WiFi")
            .setMessage(message)
            .setPositiveButton("Open WiFi Settings") { _, _ ->
                try {
                    // Try to open WiFi settings with the network pre-selected (Android 10+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                        startActivity(panelIntent)
                    } else {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                } catch (e: Exception) {
                    // Fallback to regular WiFi settings
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkWifiConnection() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifiConnected) {
            connectedToDeviceNetwork = false
            return
        }

        // Get current WiFi SSID
        val wifiInfo = wifiManager.connectionInfo
        val currentSsid = wifiInfo.ssid.replace("\"", "")

        // Check if connected to a recognized device network
        val isDeviceNetwork = recognizedDeviceNames.any { currentSsid.lowercase().startsWith(it) }

        if (isDeviceNetwork && !connectedToDeviceNetwork) {
            // Just connected to a device network
            connectedToDeviceNetwork = true
            onConnectedToDevice(currentSsid)
        } else if (!isDeviceNetwork && connectedToDeviceNetwork) {
            // Disconnected from device network
            connectedToDeviceNetwork = false
        }
    }

    private fun onConnectedToDevice(ssid: String) {
        // Stop scanning
        stopScanning()

        // Update UI
        binding.tvInstructions.text = "✅ Connected to device!"
        binding.tvStatus.text = "Connected to $ssid"
        binding.progressBar.visibility = android.view.View.GONE

        // Show success dialog and proceed
        AlertDialog.Builder(this)
            .setTitle("✅ Connection Successful!")
            .setMessage("You are now connected to $ssid.\n\nReady to proceed to the next step?")
            .setPositiveButton("Continue") { _, _ ->
                // TODO: Navigate to next activity (MainActivity or device control)
                Toast.makeText(this, "Proceeding to device control...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("device_ssid", ssid)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Stay Here") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Check connection status when returning from WiFi settings
        checkWifiConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
