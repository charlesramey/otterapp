package com.example.otterenrichment

import android.content.Context
import android.content.Intent
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
        // Back button - go back to experienced user menu
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, ExperiencedUserActivity::class.java)
            startActivity(intent)
            finish()
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
        // Placeholder for power level checking
        binding.progressBarPower.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Simulate checking (will be replaced when ESP32 provides endpoint)
                kotlinx.coroutines.delay(1000)

                // Placeholder values
                binding.tvBatteryLevel.text = "Battery: N/A"
                binding.tvVoltage.text = "Voltage: N/A"
                binding.tvPowerStatus.text = "⚠️ Power monitoring not yet supported by device"
                binding.tvPowerStatus.setTextColor(ContextCompat.getColor(this@MaintenanceModeActivity, android.R.color.holo_orange_dark))

                showToast("Power level endpoint not available on device")
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

        val isConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        binding.tvDeviceStatus.text = if (isConnected) {
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            "✓ Connected to Device"
        } else {
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            "✗ Not Connected"
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
                    val html = response.body() ?: ""
                    parseFilesFromHtml(html)
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

    private fun parseFilesFromHtml(html: String) {
        val files = mutableListOf<ScallopFile>()

        // Parse SD card usage
        val usageRegex = """SD Card Usage:</b>\s*([\d.]+)%""".toRegex()
        val usageMatch = usageRegex.find(html)
        val usage = usageMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        binding.tvSdUsage.text = "SD Card: ${String.format("%.1f", usage)}% used"

        // Parse file list
        val fileRegex = """onclick="requestDownload\('([^']+)'\)">([^<]+)\s*\((\d+)\s*bytes\)""".toRegex()
        fileRegex.findAll(html).forEach { match ->
            val fileName = match.groupValues[1]
            val fileSize = match.groupValues[3].toLongOrNull() ?: 0
            files.add(ScallopFile(fileName, fileSize))
        }

        currentFiles = files
        fileAdapter.submitList(files)

        if (files.isEmpty()) {
            showToast("No files found on SD card")
        } else {
            showToast("Found ${files.size} files")
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
        val selectedFiles = currentFiles.filter { it.isSelected }

        if (selectedFiles.isEmpty()) {
            showToast("No files selected")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Files")
            .setMessage("Delete ${selectedFiles.size} file(s)?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        binding.progressBarDownload.visibility = View.VISIBLE
                        val fileNames = selectedFiles.map { it.name }
                        val requestBody = mapOf("files" to fileNames)

                        val response = withContext(Dispatchers.IO) {
                            ScallopApiService.api.deleteFiles(requestBody)
                        }

                        if (response.isSuccessful) {
                            showToast("Files deleted")
                            toggleDeleteMode()
                            listFiles()
                        } else {
                            showToast("Failed to delete files")
                        }
                    } catch (e: Exception) {
                        showToast("Error: ${e.message}")
                    } finally {
                        binding.progressBarDownload.visibility = View.GONE
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
