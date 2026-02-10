package com.example.otterenrichment

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.otterenrichment.databinding.ActivityMaintenanceModeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class MaintenanceModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMaintenanceModeBinding
    private lateinit var fileAdapter: FileAdapter
    private var currentFiles = listOf<ScallopFile>()
    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMaintenanceModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        updateConnectionStatus()
        checkPowerLevels()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Setup RecyclerView for files
        fileAdapter = FileAdapter(
            onFileClick = { file -> if (!isSelectionMode) downloadFile(file) },
            onFileSelect = { file, isSelected ->
                currentFiles.find { it.name == file.name }?.isSelected = isSelected
            },
            isSelectionMode = false
        )

        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(this@MaintenanceModeActivity)
            adapter = fileAdapter
        }

        // Power Level Section
        binding.btnRefreshPower.setOnClickListener {
            checkPowerLevels()
        }

        // Testing Devices Section
        binding.btnTestConnection.setOnClickListener {
            testDeviceConnection()
        }

        binding.btnRefreshStatus.setOnClickListener {
            updateConnectionStatus()
        }

        // Advanced Options Section
        binding.btnFirmwareUpdate.setOnClickListener {
            openFirmwareUpdate()
        }

        binding.btnSleepMode.setOnClickListener {
            sendSleepCommand()
        }

        // Data Download Section
        binding.btnListFiles.setOnClickListener {
            listFiles()
        }

        binding.btnDeleteMode.setOnClickListener {
            toggleDeleteMode()
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedFiles()
        }

        // Initially hide delete button
        binding.btnDeleteSelected.visibility = View.GONE
    }

    private fun checkPowerLevels() {
        binding.progressBarPower.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ScallopApiService.api.getStatus() }

                if (response.isSuccessful) {
                    val status = response.body()
                    val voltage = status?.voltage ?: 0f
                    // Estimate battery level
                    val minVoltage = 3.3f
                    val maxVoltage = 4.2f
                    val batteryLevel = ((voltage - minVoltage) / (maxVoltage - minVoltage) * 100).coerceIn(0f, 100f)

                    binding.tvBatteryLevel.text = "Battery: ${String.format("%.1f", batteryLevel)}%"
                    binding.tvVoltage.text = "Voltage: ${voltage}V"
                    binding.tvPowerStatus.text = "✓ Power levels normal"
                    binding.tvPowerStatus.setTextColor(ContextCompat.getColor(this@MaintenanceModeActivity, android.R.color.holo_green_dark))
                } else {
                    binding.tvBatteryLevel.text = "Battery: Unknown"
                    binding.tvVoltage.text = "Voltage: Unknown"
                    binding.tvPowerStatus.text = "✗ Failed to read power levels"
                    binding.tvPowerStatus.setTextColor(ContextCompat.getColor(this@MaintenanceModeActivity, android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                showToast("Error checking power: ${e.message}")
            } finally {
                binding.progressBarPower.visibility = View.GONE
            }
        }
    }

    private fun updateConnectionStatus() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager

        val isConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isConnected) {
            val wifiInfo = wifiManager.connectionInfo
            val currentSsid = wifiInfo.ssid.replace("\"", "")
            ScallopApiService.setBaseUrlFromSsid(currentSsid)

            binding.tvDeviceStatus.text = "✓ Connected to $currentSsid"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            binding.tvDeviceStatus.text = "✗ Not Connected"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun testDeviceConnection() {
        binding.progressBarTest.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Test connection by trying to sync time
                val currentTime = System.currentTimeMillis() / 1000
                val response = withContext(Dispatchers.IO) {
                    ScallopApiService.api.updateTime(currentTime)
                }

                if (response.isSuccessful) {
                    binding.tvTestResult.text = "✓ Device is responding correctly"
                    binding.tvTestResult.setTextColor(ContextCompat.getColor(this@MaintenanceModeActivity, android.R.color.holo_green_dark))
                    showToast("Device test successful!")
                } else {
                    binding.tvTestResult.text = "✗ Device responded with error"
                    binding.tvTestResult.setTextColor(ContextCompat.getColor(this@MaintenanceModeActivity, android.R.color.holo_red_dark))
                    showToast("Device test failed")
                }
            } catch (e: Exception) {
                binding.tvTestResult.text = "✗ Device unreachable: ${e.message}"
                binding.tvTestResult.setTextColor(ContextCompat.getColor(this@MaintenanceModeActivity, android.R.color.holo_red_dark))
                showToast("Connection test failed")
            } finally {
                binding.progressBarTest.visibility = View.GONE
            }
        }
    }

    private fun listFiles() {
        lifecycleScope.launch {
            try {
                binding.progressBarDownload.visibility = View.VISIBLE
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
                binding.progressBarDownload.visibility = View.GONE
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
        val filesToDelete = currentFiles.filter { it.isSelected }.map { it.name }
        if (filesToDelete.isEmpty()) {
            showToast("No files selected")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Files")
            .setMessage("Are you sure you want to delete ${filesToDelete.size} files?")
            .setPositiveButton("Delete") { _, _ ->
                performDeleteFiles(filesToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDeleteFiles(files: List<String>) {
        lifecycleScope.launch {
            try {
                binding.progressBarDownload.visibility = View.VISIBLE
                var successCount = 0
                var failCount = 0

                for (fileName in files) {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            ScallopApiService.api.deleteFile(fileName)
                        }
                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }

                if (failCount == 0) {
                    showToast("Successfully deleted $successCount files")
                } else {
                    showToast("Deleted $successCount files, failed to delete $failCount files")
                }

                // Refresh file list
                listFiles()
                // Reset selection mode
                isSelectionMode = false
                toggleDeleteMode()
            } catch (e: Exception) {
                showToast("Error during deletion: ${e.message}")
            } finally {
                binding.progressBarDownload.visibility = View.GONE
            }
        }
    }

    private fun openFirmwareUpdate() {
        val updateUrl = ScallopApiService.getBaseUrl() + "update"
        AlertDialog.Builder(this)
            .setTitle("Firmware Update")
            .setMessage("This will open the OTA update page in your browser.\n\nURL: $updateUrl")
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse(updateUrl)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSleepCommand() {
        AlertDialog.Builder(this)
            .setTitle("Enter Sleep Mode")
            .setMessage("Device will enter sleep mode. Are you sure?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    try {
                        binding.progressBarPower.visibility = View.VISIBLE
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
                        binding.progressBarPower.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadFile(file: ScallopFile) {
        lifecycleScope.launch {
            try {
                binding.progressBarDownload.visibility = View.VISIBLE
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
                binding.progressBarDownload.visibility = View.GONE
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
}
