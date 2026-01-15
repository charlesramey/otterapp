package com.example.otterenrichment

/**
 * Represents a single tutorial step with requirements and detection logic
 */
data class TutorialStep(
    val stepNumber: Int,
    val title: String,
    val videoUrl: String,  // YouTube or placeholder URL
    val instructions: String,  // Text instructions shown alongside video
    val requirement: StepRequirement,  // What needs to be completed
    val helpText: String  // Additional guidance
)

/**
 * Different types of requirements for tutorial steps
 */
sealed class StepRequirement {
    object None : StepRequirement()  // No automatic detection, manual next only
    object WifiEnabled : StepRequirement()  // WiFi must be turned on
    data class NetworkFound(val ssid: String) : StepRequirement()  // Specific network found
    data class NetworkConnected(val ssid: String) : StepRequirement()  // Connected to network
    object DeviceReachable : StepRequirement()  // HTTP ping to device succeeds
}

/**
 * Status of requirement completion
 */
sealed class RequirementStatus {
    object NotStarted : RequirementStatus()
    object InProgress : RequirementStatus()
    object Completed : RequirementStatus()
    data class Failed(val reason: String) : RequirementStatus()
}

/**
 * Predefined tutorial steps for hardware setup
 */
object TutorialSteps {
    fun getAllSteps(): List<TutorialStep> {
        return listOf(
            // Step 1: Hardware Overview
            TutorialStep(
                stepNumber = 1,
                title = "Hardware Overview",
                videoUrl = "https://www.youtube.com/watch?v=PLACEHOLDER_HARDWARE",
                instructions = """
                    Welcome! This tutorial will guide you through setting up your Otter Enrichment Data Logger.

                    📦 What's in the box:
                    • ESP32 microcontroller
                    • Motion sensors (accelerometer + gyroscope)
                    • SD card for data storage
                    • Rechargeable battery
                    • Magnetic Hall effect switch

                    Watch the video to see each component, then tap 'Next' when ready.
                """.trimIndent(),
                requirement = StepRequirement.None,
                helpText = "Take your time to familiarize yourself with the hardware components."
            ),

            // Step 2: Physical Assembly
            TutorialStep(
                stepNumber = 2,
                title = "Physical Assembly",
                videoUrl = "https://www.youtube.com/watch?v=PLACEHOLDER_ASSEMBLY",
                instructions = """
                    Let's assemble the device step-by-step:

                    1️⃣ Insert the SD card into the SD slot (push until it clicks)
                    2️⃣ Connect the motion sensor to the I2C pins
                    3️⃣ Attach the battery to the power connector
                    4️⃣ Ensure the Hall effect switch is positioned correctly

                    ⚠️ Important: Do NOT power on yet!

                    Watch the video for detailed assembly instructions.
                """.trimIndent(),
                requirement = StepRequirement.None,
                helpText = "Follow the video carefully. If you're unsure, rewind and watch again."
            ),

            // Step 3: Enable WiFi
            TutorialStep(
                stepNumber = 3,
                title = "Enable WiFi on Phone",
                videoUrl = "https://www.youtube.com/watch?v=PLACEHOLDER_WIFI",
                instructions = """
                    Before powering the device, let's prepare your phone:

                    📱 Enable WiFi on your phone:
                    • Open your phone's Settings
                    • Navigate to WiFi settings
                    • Turn WiFi ON

                    ✅ This app will automatically detect when WiFi is enabled!

                    You can also tap 'Next' manually when ready.
                """.trimIndent(),
                requirement = StepRequirement.WifiEnabled,
                helpText = "WiFi needs to be on to find the device network in the next step."
            ),

            // Step 4: Find Device Network
            TutorialStep(
                stepNumber = 4,
                title = "Wake Device & Find Network",
                videoUrl = "https://www.youtube.com/watch?v=PLACEHOLDER_WAKE",
                instructions = """
                    Time to wake up the device!

                    🔌 Wake the device:
                    1. Hold a magnet near the Hall switch for 2 seconds
                    2. The LED should blink, indicating the device is awake
                    3. The device creates a WiFi network called "scallop"

                    📡 Scanning for networks...

                    This app will automatically scan for the "scallop" network.
                    When found, you'll be prompted to continue!
                """.trimIndent(),
                requirement = StepRequirement.NetworkFound("scallop"),
                helpText = "If the network isn't found, try waking the device again with the magnet."
            ),

            // Step 5: Connect to Device
            TutorialStep(
                stepNumber = 5,
                title = "Connect to Device WiFi",
                videoUrl = "https://www.youtube.com/watch?v=PLACEHOLDER_CONNECT",
                instructions = """
                    Great! The "scallop" network was found.

                    🔐 Connect to the network:
                    1. Tap on "scallop" in the network list below
                    2. Enter the password: password
                    3. Wait for connection to establish

                    ✅ This app will detect when you're connected!

                    Once connected, the main control panel will open automatically.
                """.trimIndent(),
                requirement = StepRequirement.NetworkConnected("scallop"),
                helpText = "Default password is 'password'. If connection fails, forget the network and try again."
            )
        )
    }
}
