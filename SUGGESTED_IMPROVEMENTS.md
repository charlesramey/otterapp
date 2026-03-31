# Usability and UI Improvements

Based on an analysis of the codebase, here are the key recommendations to reduce redundancy and confusion in the app flow.

## 1. Eliminate `MainActivity` (Redundant)

**Observation:** `MainActivity.kt` appears to be a legacy entry point or a "dashboard" prototype that is not part of the standard user flow (`Launcher` -> `Experienced` -> `StartRecording`). However, it contains duplicated logic for Wi-Fi scanning, file management, and data collection found in `StartRecordingActivity` and `MaintenanceModeActivity`.

**Recommendation:**
- **Deprecate or Remove `MainActivity`:** Its presence confuses the architecture.
- **Migrate any unique features:** If `MainActivity` has unique features (like "Device Status" monitoring dashboard), move them to `MaintenanceModeActivity` or a new `DeviceDashboardActivity`.

## 2. Streamline the "New Collection" Flow

**Observation:** The `BeginNewCollectionActivity` introduces an extra step where the user must choose between "Power On Devices" and "Start Recording". This distinction is often unnecessary; a user starting a recording *needs* the device powered on.

**Recommendation:**
- **Merge "Power On" into "Start Recording":**
    - Modify `StartRecordingActivity` to handle the "Device Search" phase first.
    - If no device is connected, automatically trigger the "Power On / Scan" logic (similar to `PowerOnDeviceActivity`) directly within `StartRecordingActivity`.
    - This creates a seamless "Wizard" experience: *Open Recording -> Scan/Connect -> Configure -> Start*.

## 3. Clarify Activity Responsibilities

**Observation:** `StartRecordingActivity` is currently overloaded. It handles recording, file downloading, firmware updates, and power off. This mixes "Session" tasks with "Maintenance" tasks.

**Recommendation:**
- **StartRecordingActivity:** Focus strictly on the **Session**.
    - Connect -> Configure Duration -> Start -> Monitor -> Stop.
    - Remove "Firmware Update" and "Delete Files" buttons.
    - Keep "Download Last Session" as a post-recording step, but link "Manage Files" to the Maintenance activity.
- **MaintenanceModeActivity:** Rename to **DeviceManagerActivity**.
    - This becomes the hub for all non-recording tasks:
        - Firmware Updates.
        - Full File Management (List, Delete, Bulk Download).
        - Battery/Power diagnostics.
        - Wi-Fi Configuration (if applicable).

## 4. Unified Device Connection Header

**Observation:** Users often don't know if they are connected to the right device until they try an action.

**Recommendation:**
- Create a reusable **Status Header Fragment** (or custom View) included in `StartRecordingActivity` and `MaintenanceModeActivity`.
- This header should always display:
    - **Connection Status:** (Connected/Disconnected)
    - **Current SSID:** (e.g., "scallop_01")
    - **Battery Level:** (if available)
- Clicking this header could open a quick "Switch Device" dialog.

## 5. Visual Hierarchy & Feedback

**Observation:** The app relies heavily on standard Toasts and Alerts.

**Recommendation:**
- **Use Snackbars:** For non-blocking notifications (e.g., "Scanning...", "Saved to Downloads").
- **Empty States:** When lists are empty (e.g., File List), show a helpful graphic or text ("No files found on device") instead of a blank screen or a Toast.
- **Progress Indicators:** Ensure specific actions (Downloading, Connecting) have distinct progress bars or blocking dialogs so the user knows exactly what is happening.

## Summary Plan
1.  **Refactor:** Remove `MainActivity`.
2.  **Simplify:** Merge `PowerOnDeviceActivity` logic into the start of `StartRecordingActivity` (or auto-navigate).
3.  **Split:** Move "Firmware" and "File Management" out of Recording and into Maintenance.
4.  **UI:** Implement a consistent Status Header.
