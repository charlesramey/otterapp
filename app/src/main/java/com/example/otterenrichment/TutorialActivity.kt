package com.example.otterenrichment

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.otterenrichment.databinding.ActivityTutorialBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private val tutorialSteps = TutorialSteps.getAllSteps()
    private var currentStepIndex = 0
    private var currentRequirementStatus: RequirementStatus = RequirementStatus.NotStarted

    private val wifiNetworkAdapter = WifiNetworkAdapter { network ->
        onNetworkSelected(network)
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    handleScanResults()
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    checkWifiState()
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            runOnUiThread {
                checkNetworkConnection()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread {
                checkNetworkConnection()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        setupUI()
        checkPermissions()
        showCurrentStep()
        startMonitoring()
    }

    private fun setupUI() {
        // Setup WiFi RecyclerView
        binding.recyclerWifiNetworks.apply {
            layoutManager = LinearLayoutManager(this@TutorialActivity)
            adapter = wifiNetworkAdapter
        }

        // Next button
        binding.btnNext.setOnClickListener {
            goToNextStep()
        }

        // Previous button
        binding.btnPrevious.setOnClickListener {
            goToPreviousStep()
        }

        // Skip tutorial button - go back to launcher
        binding.btnSkipTutorial.setOnClickListener {
            goBackToLauncher()
        }

        // Play video button
        binding.btnPlayVideo.setOnClickListener {
            playVideo()
        }

        // Manual WiFi settings button
        binding.btnWifiSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        // Scan WiFi button
        binding.btnScanWifi.setOnClickListener {
            startWifiScan()
        }
    }

    private fun showCurrentStep() {
        val step = tutorialSteps[currentStepIndex]

        // Update progress
        binding.progressBar.progress = ((currentStepIndex + 1) * 100) / tutorialSteps.size
        binding.tvStepNumber.text = "Step ${step.stepNumber} of ${tutorialSteps.size}"

        // Update content
        binding.tvStepTitle.text = step.title
        binding.tvInstructions.text = step.instructions
        binding.tvHelpText.text = step.helpText

        // Update navigation buttons
        binding.btnPrevious.isEnabled = currentStepIndex > 0
        binding.btnNext.text = if (currentStepIndex == tutorialSteps.size - 1) "Finish" else "Next"

        // Show/hide WiFi scanner based on requirement
        when (step.requirement) {
            is StepRequirement.NetworkFound,
            is StepRequirement.NetworkConnected -> {
                binding.layoutWifiScanner.visibility = View.VISIBLE
                startWifiScan()
            }
            else -> {
                binding.layoutWifiScanner.visibility = View.GONE
            }
        }

        // Reset requirement status
        currentRequirementStatus = RequirementStatus.NotStarted
        updateRequirementStatus()
    }

    private fun updateRequirementStatus() {
        val step = tutorialSteps[currentStepIndex]

        val statusText = when (currentRequirementStatus) {
            is RequirementStatus.NotStarted -> "⏳ Waiting..."
            is RequirementStatus.InProgress -> "🔄 Checking..."
            is RequirementStatus.Completed -> "✅ Requirement met! You can proceed."
            is RequirementStatus.Failed -> "❌ ${(currentRequirementStatus as RequirementStatus.Failed).reason}"
        }

        binding.tvRequirementStatus.text = when (step.requirement) {
            is StepRequirement.None -> ""
            is StepRequirement.WifiEnabled -> "📱 WiFi Status: $statusText"
            is StepRequirement.NetworkFound -> "📡 Network Search: $statusText"
            is StepRequirement.NetworkConnected -> "🔐 Connection Status: $statusText"
            is StepRequirement.DeviceReachable -> "🌐 Device Status: $statusText"
        }

        // Show prompt if requirement completed
        if (currentRequirementStatus is RequirementStatus.Completed) {
            showRequirementCompletedPrompt()
        }
    }

    private fun showRequirementCompletedPrompt() {
        val step = tutorialSteps[currentStepIndex]

        AlertDialog.Builder(this)
            .setTitle("✅ Step Complete!")
            .setMessage("${step.title} requirement has been met.\n\nWould you like to continue to the next step?")
            .setPositiveButton("Continue") { _, _ ->
                goToNextStep()
            }
            .setNegativeButton("Stay Here") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startMonitoring() {
        // Register WiFi broadcast receiver
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        registerReceiver(wifiScanReceiver, filter)

        // Register network callback
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Start periodic requirement checking
        lifecycleScope.launch {
            while (true) {
                checkCurrentRequirement()
                delay(2000) // Check every 2 seconds
            }
        }
    }

    private fun checkCurrentRequirement() {
        val step = tutorialSteps[currentStepIndex]

        // Only check if not already completed
        if (currentRequirementStatus is RequirementStatus.Completed) {
            return
        }

        currentRequirementStatus = RequirementStatus.InProgress

        when (val requirement = step.requirement) {
            is StepRequirement.None -> {
                // No automatic checking
            }
            is StepRequirement.WifiEnabled -> {
                if (wifiManager.isWifiEnabled) {
                    currentRequirementStatus = RequirementStatus.Completed
                } else {
                    currentRequirementStatus = RequirementStatus.Failed("WiFi is disabled. Please enable WiFi.")
                }
            }
            is StepRequirement.NetworkFound -> {
                startWifiScan()
                // Scan results will trigger handleScanResults()
            }
            is StepRequirement.NetworkConnected -> {
                checkNetworkConnection()
            }
            is StepRequirement.DeviceReachable -> {
                // Will be implemented when checking HTTP connectivity
            }
        }

        updateRequirementStatus()
    }

    private fun checkWifiState() {
        val step = tutorialSteps[currentStepIndex]
        if (step.requirement is StepRequirement.WifiEnabled) {
            checkCurrentRequirement()
        }
    }

    private fun startWifiScan() {
        if (!wifiManager.isWifiEnabled) {
            showToast("Please enable WiFi first")
            return
        }

        binding.tvScanStatus.text = "🔄 Scanning for networks..."
        wifiManager.startScan()
    }

    private fun handleScanResults() {
        val results = wifiManager.scanResults
        val networks = results
            .map { it.toWifiNetwork() }
            .sortedByDescending { it.signal }

        wifiNetworkAdapter.submitList(networks)
        binding.tvScanStatus.text = "Found ${networks.size} networks"

        // Check if target network found
        val step = tutorialSteps[currentStepIndex]
        if (step.requirement is StepRequirement.NetworkFound) {
            val targetSsid = step.requirement.ssid
            val found = networks.any { it.ssid == targetSsid }

            currentRequirementStatus = if (found) {
                RequirementStatus.Completed
            } else {
                RequirementStatus.Failed("Network '$targetSsid' not found. Try scanning again.")
            }
            updateRequirementStatus()
        }
    }

    private fun checkNetworkConnection() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifiConnected) {
            return
        }

        val wifiInfo = wifiManager.connectionInfo
        val currentSsid = wifiInfo.ssid.replace("\"", "")

        val step = tutorialSteps[currentStepIndex]
        if (step.requirement is StepRequirement.NetworkConnected) {
            val targetSsid = step.requirement.ssid

            currentRequirementStatus = if (currentSsid == targetSsid) {
                RequirementStatus.Completed
            } else {
                RequirementStatus.Failed("Connected to '$currentSsid', but need '$targetSsid'")
            }
            updateRequirementStatus()
        }
    }

    private fun onNetworkSelected(network: WifiNetwork) {
        val step = tutorialSteps[currentStepIndex]

        // If we're on the connect step and selected the target network
        if (step.requirement is StepRequirement.NetworkConnected &&
            network.ssid == step.requirement.ssid) {

            // For Android 10+ use WifiNetworkSpecifier
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectToNetworkModern(network)
            } else {
                // Older Android - direct the user to settings
                AlertDialog.Builder(this)
                    .setTitle("Connect to ${network.ssid}")
                    .setMessage("Please connect to this network manually in WiFi settings.\n\nPassword: password")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            showToast("Tap '${step.requirement}' network to connect")
        }
    }

    private fun connectToNetworkModern(network: WifiNetwork) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Show password input dialog
            val input = android.widget.EditText(this)
            input.hint = "Enter WiFi password"
            input.setText("password")

            AlertDialog.Builder(this)
                .setTitle("Connect to ${network.ssid}")
                .setMessage("Enter the network password:")
                .setView(input)
                .setPositiveButton("Connect") { _, _ ->
                    val password = input.text.toString()

                    val specifier = WifiNetworkSpecifier.Builder()
                        .setSsid(network.ssid)
                        .setWpa2Passphrase(password)
                        .build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(specifier)
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            runOnUiThread {
                                showToast("Connected successfully!")
                                checkNetworkConnection()
                            }
                        }

                        override fun onUnavailable() {
                            runOnUiThread {
                                showToast("Connection failed. Check password.")
                            }
                        }
                    }

                    connectivityManager.requestNetwork(request, callback)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun playVideo() {
        val step = tutorialSteps[currentStepIndex]
        val videoUrl = step.videoUrl

        if (videoUrl.contains("PLACEHOLDER")) {
            showToast("Video placeholder - Real video: ${step.title}")
        } else {
            // Open video URL
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showToast("No app available to play video")
            }
        }
    }

    private fun goToNextStep() {
        if (currentStepIndex < tutorialSteps.size - 1) {
            currentStepIndex++
            showCurrentStep()
        } else {
            // Tutorial complete - go to main activity
            finishTutorial()
        }
    }

    private fun goToPreviousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--
            showCurrentStep()
        }
    }

    private fun finishTutorial() {
        AlertDialog.Builder(this)
            .setTitle("🎉 Tutorial Complete!")
            .setMessage("You're ready to start using your Otter Enrichment Data Logger!\n\nYou'll be taken back to the main menu.")
            .setPositiveButton("Let's Go!") { _, _ ->
                val intent = Intent(this, LauncherActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun goBackToLauncher() {
        val intent = Intent(this, LauncherActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showSkipConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Skip Tutorial?")
            .setMessage("Are you sure you want to skip the setup tutorial?\n\nYou can access it later from the main menu.")
            .setPositiveButton("Skip") { _, _ ->
                goBackToLauncher()
            }
            .setNegativeButton("Continue Tutorial", null)
            .show()
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wifiScanReceiver)
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
