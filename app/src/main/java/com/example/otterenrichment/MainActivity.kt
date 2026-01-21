package com.example.otterenrichment

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.otterenrichment.databinding.ActivityMainBinding
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
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fileAdapter: FileAdapter
    private var currentFiles = listOf<ScallopFile>()
    private var isSelectionMode = false
    private var countDownTimer: CountDownTimer? = null
    private var deviceStatus = DeviceStatus()

    // Scanning variables
    private lateinit var wifiManager: WifiManager
    private var isScanning = false
    private var previousNetworks = setOf<String>()
    private var connectedToDeviceNetwork = false
    private val SCAN_INTERVAL_MS = 1000L
    private val recognizedDeviceNames = listOf("scallop", "otter", "enrichment")

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    handleScanResults()
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    // We can reuse updateConnectionStatus or add logic here
                    updateConnectionStatus()
                }
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        setupUI()
        checkPermissions()
        updateConnectionStatus()
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
        // Setup RecyclerView
        fileAdapter = FileAdapter(
            onFileClick = { file -> downloadFile(file) },
            onFileSelect = { file, isSelected ->
                currentFiles.find { it.name == file.name }?.isSelected = isSelected
            },
            isSelectionMode = false
        )

        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
        }

        // Control Panel Buttons
        binding.btnStartCollection.setOnClickListener {
            val duration = when (binding.spinnerDuration.selectedItemPosition) {
                0 -> 60000L // 1 minute
                1 -> 600000L // 10 minutes
                2 -> 900000L // 15 minutes
                3 -> 1200000L // 20 minutes
                else -> 60000L
            }
            startDataCollection(duration)
        }

        binding.btnSleep.setOnClickListener {
            sendSleepCommand()
        }

        binding.btnFirmwareUpdate.setOnClickListener {
            openFirmwareUpdate()
        }

        // File Management Buttons
        binding.btnListFiles.setOnClickListener {
            listFiles()
        }

        binding.btnDeleteMode.setOnClickListener {
            toggleDeleteMode()
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedFiles()
        }

        binding.btnRefresh.setOnClickListener {
            refreshStatus()
        }

        // Initially hide countdown and delete button
        binding.layoutCountdown.visibility = View.GONE
        binding.btnDeleteSelected.visibility = View.GONE
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    private fun checkWifiConnection() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.replace("\"", "")

        val isRecognized = recognizedDeviceNames.any { ssid.lowercase().startsWith(it) }

        if (!isRecognized && ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("WiFi Connection")
                .setMessage("Not connected to Scallop device WiFi.")
                .setPositiveButton("Connect") { _, _ ->
                    startDeviceDetection()
                }
                .setNegativeButton("Continue Anyway", null)
                .show()
        } else if (isRecognized) {
            updateTimeOnDevice()
        }
    }

    private fun updateConnectionStatus() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isConnected) {
             val wifiInfo = wifiManager.connectionInfo
             val ssid = wifiInfo.ssid.replace("\"", "")
             val isDevice = recognizedDeviceNames.any { ssid.lowercase().startsWith(it) }

             if (isDevice && !connectedToDeviceNetwork) {
                 connectedToDeviceNetwork = true
                 if (isScanning) {
                     onConnectedToDevice(ssid)
                 }
             } else if (!isDevice) {
                 connectedToDeviceNetwork = false
             }
        } else {
             connectedToDeviceNetwork = false
        }

        binding.tvConnectionStatus.text = if (isConnected && connectedToDeviceNetwork) {
            binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            "Connected to Device"
        } else if (isConnected) {
             binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
             "Connected to WiFi (Not Device)"
        } else {
            binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            "Not Connected"
        }

        deviceStatus = deviceStatus.copy(isConnected = isConnected)
    }

    private fun updateTimeOnDevice() {
        lifecycleScope.launch {
            try {
                val currentTime = System.currentTimeMillis() / 1000
                val response = withContext(Dispatchers.IO) {
                    ScallopApiService.api.updateTime(currentTime)
                }

                if (response.isSuccessful) {
                    showToast("Time synchronized with device")
                }
            } catch (e: Exception) {
                // Silent fail - time sync is not critical
            }
        }
    }

    private fun refreshStatus() {
        updateConnectionStatus()
        showToast("Status refreshed")
    }

    private fun startDataCollection(durationMs: Long) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val response = withContext(Dispatchers.IO) {
                    ScallopApiService.api.startCollection(durationMs)
                }

                if (response.isSuccessful) {
                    showToast("Data collection started")
                    startCountdown(durationMs)
                    disableControls(true)
                } else {
                    showToast("Failed to start collection")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
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
                binding.tvCountdown.text = "Collection complete! Please reconnect to WiFi."
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

        val newDevices = scanResults.filter { result ->
            val ssid = result.SSID.lowercase()
            val isRecognized = recognizedDeviceNames.any { ssid.startsWith(it) }
            isRecognized
        }

        if (newDevices.isNotEmpty()) {
             val actuallyNew = newDevices.filter { !previousNetworks.contains(it.SSID) }

             if (actuallyNew.isNotEmpty()) {
                 onDeviceDetected(actuallyNew)
             } else if (newDevices.isNotEmpty() && !connectedToDeviceNetwork) {
                 onDeviceDetected(newDevices)
             }
        }

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
                updateTimeOnDevice()
                updateConnectionStatus()
                showToast("Ready")
            }
            .setNegativeButton("Stay Here") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun disableControls(disable: Boolean) {
        binding.btnStartCollection.isEnabled = !disable
        binding.btnSleep.isEnabled = !disable
        binding.spinnerDuration.isEnabled = !disable
    }

    private fun sendSleepCommand() {
        AlertDialog.Builder(this)
            .setTitle("Enter Sleep Mode")
            .setMessage("Device will enter sleep mode. Are you sure?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    try {
                        binding.progressBar.visibility = View.VISIBLE
                        val response = withContext(Dispatchers.IO) {
                            ScallopApiService.api.sleep()
                        }

                        if (response.isSuccessful) {
                            showToast("Device entering sleep mode")
                        } else {
                            showToast("Failed to send sleep command")
                        }
                    } catch (e: Exception) {
                        showToast("Error: ${e.message}")
                    } finally {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFirmwareUpdate() {
        AlertDialog.Builder(this)
            .setTitle("Firmware Update")
            .setMessage("This will open the OTA update page in your browser.\\n\\nURL: http://192.168.4.1/update")
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("http://192.168.4.1/update")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun listFiles() {
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
                } else {
                    showToast("Failed to list files")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun toggleDeleteMode() {
        isSelectionMode = !isSelectionMode

        fileAdapter = FileAdapter(
            onFileClick = { file -> if (!isSelectionMode) downloadFile(file) },
            onFileSelect = { file, isSelected ->
                currentFiles.find { it.name == file.name }?.isSelected = isSelected
            },
            isSelectionMode = isSelectionMode
        )

        binding.recyclerViewFiles.adapter = fileAdapter
        fileAdapter.submitList(currentFiles.toList())

        binding.btnDeleteSelected.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        binding.btnDeleteMode.text = if (isSelectionMode) "Cancel" else "Delete Files"
    }

    private fun deleteSelectedFiles() {
        showToast("Delete functionality is not supported by the device.")
    }

    private fun downloadFile(file: ScallopFile) {
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
                    } else {
                        showToast("Failed to save file")
                    }
                } else {
                    showToast("Download failed")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_tutorial -> {
                val intent = Intent(this, TutorialActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
