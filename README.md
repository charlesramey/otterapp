# Otter Enrichment - Data Logger Control App

**Complete Android app for controlling the Otter Enrichment Data Logger hardware device.**

This app combines an interactive tutorial system with WiFi scanning and ESP32 device control features into a single, unified experience.

---

## 🎯 Overview

The Otter Enrichment app provides:
- **Interactive Tutorial** - Step-by-step hardware setup with video guides
- **Smart WiFi Detection** - Automatic detection of WiFi state, network availability, and connection
- **Device Control** - Start/stop data collection, manage files, configure firmware
- **File Management** - Download and delete data files from the device's SD card

---

## ✨ Key Features

### 🎓 Interactive Tutorial System

**Shows EVERY time the app launches** to guide users through hardware setup:

1. **Hardware Overview** - Introduction to all components
2. **Physical Assembly** - Step-by-step assembly instructions
3. **Enable WiFi** - Automatically detects when phone WiFi is enabled
4. **Find Device Network** - Scans for and detects the "scallop" network
5. **Connect to Device** - Guides connection and auto-detects when connected

**Smart Features:**
- ✅ Manual "Next" button always available
- ✅ Automatic detection when requirements are met
- ✅ Prompts user to advance when step complete
- ✅ Integrated WiFi scanner for network selection
- ✅ Video placeholders ready for real videos
- ✅ Re-watch tutorial anytime from main menu

### 📡 WiFi Scanner Integration

Built directly into the tutorial flow:
- Real-time WiFi network scanning
- Signal strength indication
- Security status display
- Tap to connect functionality
- Automatic "scallop" network detection

### 🎛️ Device Control Panel

Once connected to the device:
- **Data Collection**: Start timed data collection (1-20 minutes)
- **Sleep Mode**: Put device to sleep to save battery
- **Firmware Update**: OTA firmware updates via browser
- **File Management**: List, download, and delete data files
- **SD Card Status**: Monitor storage usage

---

## 🏗️ Architecture

### Activities

**TutorialActivity** (Launcher)
- Shows on every app launch
- 5-step tutorial wizard
- WiFi state monitoring
- Network scanning and detection 
- Requirement checking and auto-progression

**MainActivity**
- Device control panel
- HTTP communication with ESP32
- File management
- Data collection controls

### Key Components

**TutorialStep.kt**
- Data models for tutorial steps
- Requirements system (`StepRequirement`)
- Status tracking (`RequirementStatus`)
- 5 predefined tutorial steps

**WifiNetworkAdapter.kt**
- RecyclerView adapter for WiFi networks
- Signal strength display
- Network selection handling

**ScallopApiService.kt**
- Retrofit API for ESP32 communication
- Endpoints: `/command`, `/list-files`, `/request-download`, `/delete-files`
- Base URL: `http://192.168.4.1/`

**FileAdapter.kt**
- RecyclerView adapter for SD card files
- Selection mode for batch delete
- File size formatting

---

## 📋 Requirements System

Each tutorial step can have different requirement types:

### Requirement Types

```kotlin
StepRequirement.None                        // Manual next only
StepRequirement.WifiEnabled                 // WiFi must be on
StepRequirement.NetworkFound("scallop")     // Network detected
StepRequirement.NetworkConnected("scallop") // Connected to network
StepRequirement.DeviceReachable             // HTTP ping succeeds
```

### How It Works

1. **Background Monitoring**: App continuously monitors WiFi state (every 2 seconds)
2. **Requirement Checking**: Evaluates current step's requirement
3. **Status Updates**: Shows real-time status (⏳ Waiting, 🔄 Checking, ✅ Completed, ❌ Failed)
4. **Auto-Prompt**: When requirement met, dialog asks if user wants to continue
5. **Manual Override**: User can always tap "Next" manually

---

## 🎥 Video Tutorial System

### Current: Placeholder Mode

Videos are placeholders that show toast messages:

```kotlin
videoUrl = "https://www.youtube.com/watch?v=PLACEHOLDER_HARDWARE"
// Tapping shows: "Video placeholder - Real video: Hardware Overview"
```

### Adding Real Videos

**Option 1: YouTube (Recommended)**

1. Upload videos to YouTube
2. Get video IDs from URLs
3. Update `TutorialStep.kt`:

```kotlin
TutorialStep(
    stepNumber = 1,
    title = "Hardware Overview",
    videoUrl = "https://www.youtube.com/watch?v=ABC123XYZ",  // ← Real ID
    // ...
)
```

**Option 2: Self-Hosted**

```kotlin
videoUrl = "https://yourserver.com/videos/hardware_overview.mp4"
```

**Option 3: Bundle in App** (⚠️ Increases app size)

```kotlin
videoUrl = "android.resource://$packageName/${R.raw.step1_video}"
```

---

## 🔧 Customization

### Modify Tutorial Steps

Edit `TutorialStep.kt` → `TutorialSteps.getAllSteps()`:

```kotlin
fun getAllSteps(): List<TutorialStep> {
    return listOf(
        TutorialStep(
            stepNumber = 1,
            title = "Your Custom Title",
            videoUrl = "https://youtube.com/watch?v=YOUR_VIDEO",
            instructions = """
                Your multi-line instructions here...
            """.trimIndent(),
            requirement = StepRequirement.None,
            helpText = "Additional guidance text"
        ),
        // Add more steps...
    )
}
```

### Change Detection Interval

In `TutorialActivity.kt`, change the monitoring frequency:

```kotlin
lifecycleScope.launch {
    while (true) {
        checkCurrentRequirement()
        delay(2000) // ← Change this (milliseconds)
    }
}
```

### Disable Auto-Detection

To remove automatic progression, comment out the prompt in `TutorialActivity.kt`:

```kotlin
if (currentRequirementStatus is RequirementStatus.Completed) {
    // showRequirementCompletedPrompt()  // ← Disabled
}
```

---

## 📱 Device Communication

### ESP32 Endpoints

The app communicates with the ESP32 device at `192.168.4.1`:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/command` | POST | Send commands (collect, sleep) |
| `/update-time` | POST | Sync device time |
| `/list-files` | GET | Get SD card file list |
| `/request-download` | GET | Download specific file |
| `/delete-files` | POST | Delete multiple files |

### Example API Call

```kotlin
lifecycleScope.launch {
    val response = withContext(Dispatchers.IO) {
        ScallopApiService.api.sendCommand("collect,60000")
    }

    if (response.isSuccessful) {
        // Success
    }
}
```

---

## 🔐 Permissions

The app requires:

- `INTERNET` - API communication
- `ACCESS_WIFI_STATE` - Monitor WiFi status
- `CHANGE_WIFI_STATE` - Enable/disable WiFi
- `ACCESS_FINE_LOCATION` - WiFi scanning (Android requirement)
- `ACCESS_COARSE_LOCATION` - WiFi scanning
- `WRITE_EXTERNAL_STORAGE` - File downloads (Android ≤ 9)

All permissions are requested at runtime.

---

## 🚀 Setup Instructions

### 1. Open in Android Studio

```bash
cd ~/Desktop/OtterEnrichment
# Open in Android Studio
```

### 2. Sync Gradle

Wait for Gradle sync to complete and dependencies to download.

### 3. Add Real Videos (Optional)

Update `TutorialStep.kt` with real YouTube URLs or video links.

### 4. Build and Run

- Connect Android device or start emulator
- Click "Run" (green play button)
- App will launch with tutorial

---

## 📝 File Structure

```
OtterEnrichment/
├── app/
│   ├── build.gradle.kts              # App dependencies
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # App configuration
│   │   ├── java/com/example/otterenrichment/
│   │   │   ├── TutorialActivity.kt   # Tutorial wizard
│   │   │   ├── MainActivity.kt       # Device control
│   │   │   ├── TutorialStep.kt       # Tutorial data models
│   │   │   ├── WifiNetworkAdapter.kt # WiFi list adapter
│   │   │   ├── FileAdapter.kt        # File list adapter
│   │   │   └── ScallopApiService.kt  # API interface
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_tutorial.xml  # Tutorial UI
│   │       │   └── activity_main.xml      # Main UI
│   │       ├── values/
│   │       │   └── strings.xml            # String resources
│   │       └── menu/
│   │           └── main_menu.xml          # Main menu
├── build.gradle.kts              # Project build config
├── settings.gradle.kts           # Project settings
└── README.md                     # This file
```

---

## 🎨 User Experience

### First-Time User Flow

```
1. Opens app
2. Tutorial starts automatically
3. Watches Step 1 video (Hardware Overview)
4. Reads instructions
5. Taps "Next"
6. Watches Step 2 video (Assembly)
7. Taps "Next"
8. Step 3: App detects when WiFi enabled
   → Shows ✅ "Requirement met! Continue?"
9. Step 4: App scans and finds "scallop" network
   → Shows ✅ "Requirement met! Continue?"
10. Step 5: User taps "scallop" in list
    → Enters password
    → App detects connection
    → Shows ✅ "Requirement met! Continue?"
11. Tutorial complete → Main app opens
```

### Experienced User Flow

```
1. Opens app
2. Tutorial starts
3. Taps "Skip" button
4. Main app opens immediately
```

### Re-Watch Tutorial

```
1. In main app
2. Taps menu (⋮) → "View Tutorial"
3. Tutorial opens from Step 1
4. Can navigate through all steps
5. Taps "Skip" → Returns to main app
```

---

## 🧪 Testing Checklist

### Tutorial
- [ ] App launches to tutorial (not main activity)
- [ ] All 5 steps display correctly
- [ ] Progress bar updates
- [ ] Video play button works
- [ ] Instructions are readable
- [ ] Manual "Next" button works on all steps
- [ ] "Previous" button disabled on Step 1
- [ ] "Next" changes to "Finish" on Step 5

### WiFi Detection
- [ ] Step 3 detects WiFi enabled state
- [ ] Requirement status updates in real-time
- [ ] Prompt appears when WiFi enabled
- [ ] WiFi scanner appears on Step 4
- [ ] Scan button finds networks
- [ ] "scallop" network detected automatically
- [ ] Network list shows signal strength

### WiFi Connection
- [ ] Tap network opens connection dialog
- [ ] Password field pre-filled with "password"
- [ ] Connection attempt starts
- [ ] Step 5 detects successful connection
- [ ] Prompt appears when connected

### Main App
- [ ] Tutorial completion opens main activity
- [ ] Connection status displays correctly
- [ ] Data collection buttons work
- [ ] File list loads from device
- [ ] File download works
- [ ] File delete works
- [ ] Menu → "View Tutorial" reopens tutorial

### Edge Cases
- [ ] Back button on tutorial shows confirmation
- [ ] Skip button confirmation dialog
- [ ] WiFi disabled mid-tutorial (status updates)
- [ ] Network disconnect mid-tutorial
- [ ] Device rotation (portrait/landscape)
- [ ] Low memory handling

---

## 🐛 Troubleshooting

### "Network not found"
- Ensure device is powered on and magnet activated
- Try manual WiFi scan from Step 4
- Check if LED is blinking on device

### "Connection failed"
- Verify password is "password" (lowercase)
- Forget network in phone settings and retry
- Ensure no other device connected to "scallop"

### "Download failed"
- Check WiFi connection status
- Ensure file exists on SD card
- Verify storage permissions granted

### Tutorial auto-advancement not working
- Check location permissions granted (required for WiFi scanning)
- Ensure WiFi is enabled on phone
- Try manual "Next" button if needed

---

## 🔄 Differences from Original Apps

This is a **completely new project** that combines features from:

1. **WiFiScanner** - WiFi scanning, network detection
2. **ScallopControl** - ESP32 communication, file management, data collection
3. **Tutorial System** - Video guides, smart detection, auto-progression

**Key Improvements:**
- WiFi Scanner integrated into tutorial (not separate feature)
- Automatic requirement detection
- Better user flow (tutorial → device control)
- Single unified app experience

---

## 📚 Related Documentation

- [TUTORIAL_FEATURE.md](/Users/apramey/Desktop/ScallopControl/TUTORIAL_FEATURE.md) - Original tutorial documentation
- [ESP32 Web Interface](/Users/apramey/Desktop/OtterSoftwareV2-main) - Original hardware code

---

## 🎉 Next Steps

1. **Add Real Videos**: Upload tutorial videos to YouTube and update URLs
2. **Test on Device**: Run on physical Android phone for real WiFi testing
3. **Customize Steps**: Modify tutorial content to match your hardware
4. **Deploy**: Build release APK for distribution

---

**Created**: October 20, 2025
**Version**: 1.0
**Target**: Android 8.0+ (API 26+)
