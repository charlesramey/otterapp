package com.example.otterenrichment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.otterenrichment.databinding.ActivityStartRecordingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StartRecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartRecordingBinding
    private lateinit var fileAdapter: FileAdapter
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private var currentFiles = listOf<ScallopFile>()
    private var isSelectionMode = false
    private var countDownTimer: CountDownTimer? = null

    // Connected device networks
    private var connectedDevices = listOf<String>()
    private var selectedDevice: String? = null

    // Recognized device name prefixes
    private val recognizedDeviceNames = listOf("scallop", "otter", "enrichment")

    // Last connection tracking
    private var lastConnectionTime: Long = System.currentTimeMillis()
    private var lastBatteryLevel: Float = 0f

    // Scanning variables
    private var isScanning = false
    private var previousNetworks = setOf<String>()
    private var connectedToDeviceNetwork = false
    private val SCAN_INTERVAL_MS = 1000L

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        updateConnectedDevices()
        setupUI()
        checkWifiConnection()
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
            finish()
        }

        // Device selection spinner - populate with connected devices
        updateDeviceSpinner()

        // Duration spinner
        val durations = arrayOf("1 minute", "10 minutes", "15 minutes", "20 minutes")
        val durationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, durations)
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDuration.adapter = durationAdapter

        // Start Collection button
        binding.btnStartCollection.setOnClickListener {
            startDataCollection()
        }

        // Device Options buttons
        binding.btnCheckBattery.setOnClickListener {
            checkBattery()
        }

        binding.btnDownloadData.setOnClickListener {
            binding.layoutDataDownload.visibility = View.VISIBLE
            listFiles()
        }

        binding.btnListFiles.setOnClickListener {
            listFiles()
        }

        // Power Off button
        binding.btnPowerOff.setOnClickListener {
            powerOffDevice()
        }

        // Setup RecyclerView for files
        fileAdapter = FileAdapter(
            onFileClick = { file -> downloadFile(file) },
            onFileSelect = { file, isSelected ->
                currentFiles.find { it.name == file.name }?.isSelected = isSelected
            },
            isSelectionMode = false
        )

        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(this@StartRecordingActivity)
            adapter = fileAdapter
        }

        // Initially hide sections
        binding.layoutCountdown.visibility = View.GONE
        binding.layoutDataDownload.visibility = View.GONE
    }

    private fun updateConnectedDevices() {
        // Get current WiFi SSID
        val wifiInfo = wifiManager.connectionInfo
        val currentSsid = wifiInfo.ssid.replace("\"", "")

        // Check if connected to a recognized device network
        val isDeviceNetwork = recognizedDeviceNames.any { currentSsid.lowercase().startsWith(it) }

        connectedDevices = if (isDeviceNetwork && currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>") {
            listOf(currentSsid)
        } else {
            emptyList()
        }

        selectedDevice = connectedDevices.firstOrNull()
    }

    private fun updateDeviceSpinner() {
        if (connectedDevices.isEmpty()) {
            // No devices connected - show placeholder
            val placeholder = listOf("No devices connected")
            val deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, placeholder)
            deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDevice.adapter = deviceAdapter
            binding.spinnerDevice.isEnabled = false

            // Disable controls
            binding.btnStartCollection.isEnabled = false
            binding.btnCheckBattery.isEnabled = false
            binding.btnDownloadData.isEnabled = false
            binding.btnListFiles.isEnabled = false
            binding.btnDeleteFiles.isEnabled = false
            binding.btnFirmwareUpdate.isEnabled = false
            binding.btnPowerOff.isEnabled = false
        } else {
            // Devices connected - populate spinner
            val deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectedDevices)
            deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDevice.adapter = deviceAdapter
            binding.spinnerDevice.isEnabled = true

            // Enable controls
            binding.btnStartCollection.isEnabled = true
            binding.btnCheckBattery.isEnabled = true
            binding.btnDownloadData.isEnabled = true
            binding.btnListFiles.isEnabled = true
            binding.btnDeleteFiles.isEnabled = true
            binding.btnFirmwareUpdate.isEnabled = true
            binding.btnPowerOff.isEnabled = true
        }
    }

    private fun checkWifiConnection() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isConnected) {
            binding.tvConnectionStatus.text = "✓ Connected"
            binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this@StartRecordingActivity, android.R.color.holo_green_dark))

            lastConnectionTime = System.currentTimeMillis()

            // Check if we just connected to a device network
            val wifiInfo = wifiManager.connectionInfo
            val currentSsid = wifiInfo.ssid.replace("\"", "")
            val isDeviceNetwork = recognizedDeviceNames.any { currentSsid.lowercase().startsWith(it) }

            if (isDeviceNetwork && !connectedToDeviceNetwork) {
                connectedToDeviceNetwork = true
                onConnectedToDevice(currentSsid)
            } else if (!isDeviceNetwork) {
                connectedToDeviceNetwork = false
            }

            // Try to get battery level (placeholder)
            lifecycleScope.launch {
                try {
                    lastBatteryLevel = 85f // Placeholder
                } catch (e: Exception) { }
            }
        } else {
            connectedToDeviceNetwork = false
            showWifiError()
        }
    }

    private fun showWifiError() {
        binding.tvConnectionStatus.text = "✗ WiFi Unavailable"
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        val lastConnectionStr = dateFormat.format(Date(lastConnectionTime))

        AlertDialog.Builder(this)
            .setTitle("⚠️ WiFi Connection Lost")
            .setMessage(
                "WiFi is currently unavailable.\n\n" +
                "Last Connection:\n$lastConnectionStr\n\n" +
                "Battery Level at Last Connection:\n${String.format("%.1f", lastBatteryLevel)}%\n\n" +
                "Please reconnect to the device's WiFi network to continue."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startDataCollection() {
        if (!isWifiConnected()) {
            showWifiError()
            return
        }

        if (connectedDevices.isEmpty()) {
            showToast("No devices connected. Please connect to a device network first.")
            return
        }

        selectedDevice = binding.spinnerDevice.selectedItem?.toString()
        val duration = when (binding.spinnerDuration.selectedItemPosition) {
            0 -> 60000L // 1 minute
            1 -> 600000L // 10 minutes
            2 -> 900000L // 15 minutes
            3 -> 1200000L // 20 minutes
            else -> 60000L
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val response = withContext(Dispatchers.IO) {
                    ScallopApiService.api.startCollection(duration)
                }

                if (response.isSuccessful) {
                    showToast("Data collection started on $selectedDevice")
                    startCountdown(duration)
                    disableControls(true)
                } else {
                    showToast("Failed to start collection")
                }
            } catch (e: Exception) {
                if (!isWifiConnected()) {
                    showWifiError()
                } else {
                    showToast("Error: ${e.message}")
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun startCountdown(durationMs: Long) {
        binding.layoutCountdown.visibility = View.VISIBLE

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                binding.tvCountdown.text = String.format("Collecting data: %02d:%02d remaining", minutes, secs)
            }

            override fun onFinish() {
                binding.tvCountdown.text = "Collection complete!"
                binding.layoutCountdown.visibility = View.GONE
                disableControls(false)
                showToast("Data collection complete")
                showReconnectDialog()
            }
        }.start()
    }

    private fun showReconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Data Collection Complete")
            .setMessage("Data collection has finished. Please reconnect to the device to download data.")
            .setPositiveButton("Reconnect") { _, _ ->
                startDeviceDetection()
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startDeviceDetection() {
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please enable WiFi first", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        previousNetworks = getCurrentNetworkSSIDs()
        showToast("Scanning for devices...")

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
        wifiManager.startScan()
    }

    private fun getCurrentNetworkSSIDs(): Set<String> {
        val scanResults = wifiManager.scanResults
        return scanResults.map { it.SSID }.toSet()
    }

    private fun handleScanResults() {
        if (!isScanning) return

        val scanResults = wifiManager.scanResults

        // Find new networks that match recognized device names
        val newDevices = scanResults.filter { result ->
            val ssid = result.SSID.lowercase()
            val isRecognized = recognizedDeviceNames.any { ssid.startsWith(it) }
            isRecognized
        }

        if (newDevices.isNotEmpty()) {
             // Logic to avoid spamming or to ensure we catch it
             val actuallyNew = newDevices.filter { !previousNetworks.contains(it.SSID) }

             if (actuallyNew.isNotEmpty()) {
                 onDeviceDetected(actuallyNew)
             } else if (newDevices.isNotEmpty() && !connectedToDeviceNetwork) {
                 // If we are forcing a scan, prompt for any found device
                 onDeviceDetected(newDevices)
             }
        }

        // Update previous networks
        previousNetworks = scanResults.map { it.SSID }.toSet()
    }

    private fun onDeviceDetected(devices: List<ScanResult>) {
        if (connectedToDeviceNetwork) return

        stopScanning()

        val device = devices.first()
        val deviceName = device.SSID

        AlertDialog.Builder(this)
            .setTitle("Device Detected!")
            .setMessage("A new device '$deviceName' has been detected.\n\nWould you like to connect to it?")
            .setPositiveButton("Connect") { _, _ ->
                onNetworkSelected(device.toWifiNetwork())
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Cancelled
            }
            .setCancelable(false)
            .show()
    }

    private fun onNetworkSelected(network: WifiNetwork) {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                        startActivity(panelIntent)
                    } else {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun stopScanning() {
        isScanning = false
    }

    private fun onConnectedToDevice(ssid: String) {
        stopScanning()

        AlertDialog.Builder(this)
            .setTitle("✅ Connection Successful!")
            .setMessage("You are now connected to $ssid.\n\nReady to proceed to the next step?")
            .setPositiveButton("Continue") { _, _ ->
                updateConnectedDevices()
                updateDeviceSpinner()
                showToast("Ready to download data")
            }
            .setNegativeButton("Stay Here") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun disableControls(disable: Boolean) {
        binding.btnStartCollection.isEnabled = !disable
        binding.spinnerDevice.isEnabled = !disable
        binding.spinnerDuration.isEnabled = !disable
    }

    private fun checkBattery() {
        if (!isWifiConnected()) {
            showWifiError()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ScallopApiService.api.getStatus() }

                if (response.isSuccessful) {
                    val status = response.body()
                    val voltage = status?.voltage ?: 0f
                    // Estimate battery level based on voltage (e.g. 3.3V to 4.2V for LiPo)
                    // This is a rough estimation
                    val minVoltage = 3.3f
                    val maxVoltage = 4.2f
                    val batteryLevel = ((voltage - minVoltage) / (maxVoltage - minVoltage) * 100).coerceIn(0f, 100f)

                    lastBatteryLevel = batteryLevel
                    lastConnectionTime = System.currentTimeMillis()

                    AlertDialog.Builder(this@StartRecordingActivity)
                        .setTitle("Battery Status")
                        .setMessage("Device: $selectedDevice\n\nBattery Level: ${String.format("%.1f", batteryLevel)}%\n\nVoltage: ${voltage}V")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                     showToast("Failed to get status")
                }
            } catch (e: Exception) {
                if (!isWifiConnected()) {
                    showWifiError()
                } else {
                    showToast("Error: ${e.message}")
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun listFiles() {
        if (!isWifiConnected()) {
            showWifiError()
            return
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val response = withContext(Dispatchers.IO) {
                    ScallopApiService.api.listFiles()
                }

                if (response.isSuccessful) {
                    val responseBodyString = response.body()?.string() ?: "{}"
                    val jsonObject = JSONObject(responseBodyString)

                    val filesArray = jsonObject.optJSONArray("files")
                    val files = mutableListOf<ScallopFile>()

                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val fileObj = filesArray.optJSONObject(i)
                            if (fileObj != null) {
                                val name = fileObj.optString("name", "unknown")
                                val size = fileObj.optLong("size", 0)
                                files.add(ScallopFile(name, size))
                            }
                        }
                    }

                    // Sort files: Newest date first
                    val sortedFiles = files.sortedByDescending { file ->
                        // Attempt to parse date from filename: scallop_yyyy-MM-dd_HH-mm-ss.csv
                        // If parsing fails, use 0 so they appear at the end (or just string sort)
                        try {
                            if (file.name.startsWith("scallop_")) {
                                val datePart = file.name.substringAfter("scallop_").substringBefore(".")
                                val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                                format.parse(datePart)?.time ?: 0L
                            } else {
                                0L
                            }
                        } catch (e: Exception) {
                            0L
                        }
                    }

                    currentFiles = sortedFiles
                    fileAdapter.submitList(sortedFiles)

                    if (sortedFiles.isEmpty()) {
                        showToast("No files found on SD card")
                    } else {
                        showToast("Found ${sortedFiles.size} files")
                    }

                    val usage = jsonObject.optDouble("sd_usage_percent", 0.0).toFloat()
                    binding.tvSdUsage.text = "SD Card: ${String.format("%.1f", usage)}% used"
                    lastConnectionTime = System.currentTimeMillis()
                } else {
                    showToast("Failed to list files")
                }
            } catch (e: Exception) {
                if (!isWifiConnected()) {
                    showWifiError()
                } else {
                    showToast("Error: ${e.message}")
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun downloadFile(file: ScallopFile) {
        if (!isWifiConnected()) {
            showWifiError()
            return
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                showToast("Downloading ${file.name}...")

                val response = withContext(Dispatchers.IO) {
                    ScallopApiService.api.requestDownload(file.name)
                }

                if (response.isSuccessful && response.body() != null) {
                    val saved = saveFile(response.body()!!, file.name)
                    if (saved) {
                        showToast("Downloaded to Downloads/${file.name}")
                        lastConnectionTime = System.currentTimeMillis()
                    } else {
                        showToast("Failed to save file")
                    }
                } else {
                    showToast("Download failed")
                }
            } catch (e: Exception) {
                if (!isWifiConnected()) {
                    showWifiError()
                } else {
                    showToast("Error: ${e.message}")
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveFile(body: ResponseBody, fileName: String): Boolean {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun powerOffDevice() {
        if (!isWifiConnected()) {
            showWifiError()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Power Off Device")
            .setMessage("Send $selectedDevice to sleep mode?\n\nThe device will power off and stop collecting data.")
            .setPositiveButton("Power Off") { _, _ ->
                lifecycleScope.launch {
                    try {
                        binding.progressBar.visibility = View.VISIBLE
                        val response = withContext(Dispatchers.IO) {
                            ScallopApiService.api.sleep()
                        }

                        if (response.isSuccessful) {
                            showToast("$selectedDevice is powering off")
                            lastConnectionTime = System.currentTimeMillis()
                        } else {
                            showToast("Failed to power off device")
                        }
                    } catch (e: Exception) {
                        if (!isWifiConnected()) {
                            showWifiError()
                        } else {
                            showToast("Error: ${e.message}")
                        }
                    } finally {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh device list when returning to this activity
        updateConnectedDevices()
        updateDeviceSpinner()
        checkWifiConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        stopScanning()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
