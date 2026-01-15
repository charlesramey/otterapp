package com.example.otterenrichment

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        updateConnectedDevices()
        setupUI()
        checkWifiConnection()
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

        binding.btnDeleteFiles.setOnClickListener {
            toggleDeleteMode()
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedFiles()
        }

        binding.btnFirmwareUpdate.setOnClickListener {
            openFirmwareUpdate()
        }

        // Power Off button
        binding.btnPowerOff.setOnClickListener {
            powerOffDevice()
        }

        // Add Additional Device button
        binding.btnAddDevice.setOnClickListener {
            val intent = Intent(this, PowerOnDeviceActivity::class.java)
            startActivity(intent)
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
        binding.btnDeleteSelected.visibility = View.GONE
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
        lifecycleScope.launch {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (isConnected) {
                binding.tvConnectionStatus.text = "✓ Connected"
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this@StartRecordingActivity, android.R.color.holo_green_dark))

                // Update last connection time
                lastConnectionTime = System.currentTimeMillis()

                // Try to get battery level (placeholder)
                try {
                    // TODO: Implement actual battery check when endpoint available
                    lastBatteryLevel = 85f // Placeholder
                } catch (e: Exception) {
                    // Silent fail
                }
            } else {
                showWifiError()
            }
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
            }
        }.start()
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
                    val filesResponse = response.body()
                    val files = filesResponse?.files?.map {
                        ScallopFile(it.name, 0) // Size not provided by API
                    } ?: emptyList()

                    currentFiles = files
                    fileAdapter.submitList(files)

                    if (files.isEmpty()) {
                        showToast("No files found on SD card")
                    } else {
                        showToast("Found ${files.size} files")
                    }

                    // Note: SD usage is not returned by listFiles anymore
                    binding.tvSdUsage.text = "SD Card: Usage N/A"
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
        binding.btnDeleteFiles.text = if (isSelectionMode) "Cancel" else "Delete Files"
    }

    private fun deleteSelectedFiles() {
        showToast("Delete functionality is not supported by the device.")
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

    private fun openFirmwareUpdate() {
        if (!isWifiConnected()) {
            showWifiError()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Firmware Update")
            .setMessage("This will open the OTA update page in your browser.\n\nURL: http://192.168.4.1/update")
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("http://192.168.4.1/update")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
    }
}
