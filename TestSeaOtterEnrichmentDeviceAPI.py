import requests
import json
import time
import os
from datetime import datetime

# --- Configuration ---
# Assuming your ESP32 is running the SoftAP and is accessible via this hostname/IP.
BASE_URL = "http://scallop.local"
# BASE_URL = "http://192.168.4.1"
DOWNLOAD_DIR = "downloaded_data"
MAX_RECONNECT_ATTEMPTS = 10
RECONNECT_DELAY_S = 2

def print_response(title, response):
    """Helper function to print API response details."""
    print(f"\n--- {title} ---")
    print(f"URL: {response.url}")
    print(f"Status Code: {response.status_code}")

    try:
        # Attempt to print JSON content
        print("Response JSON:")
        print(json.dumps(response.json(), indent=4))
    except requests.exceptions.JSONDecodeError:
        # Print text content if it's not JSON
        print("Response Content (Not JSON):")
        # Limit output length for clean logging
        content = response.text.strip()
        print(content[:200] + "..." if len(content) > 200 else content)

def wait_for_reconnection():
    """Polls the status endpoint until the ESP32 is back online."""
    print("\n[RECONNECT] Wi-Fi was disabled during data collection. Waiting for SoftAP to return...")
    for attempt in range(1, MAX_RECONNECT_ATTEMPTS + 1):
        try:
            print(f"[RECONNECT] Attempt {attempt}/{MAX_RECONNECT_ATTEMPTS}: Polling {BASE_URL}/api/status")
            response = requests.get(f"{BASE_URL}/api/status", timeout=2)
            if response.status_code == 200:
                print("[RECONNECT] Device is back online! Proceeding with tests.")
                return True
        except requests.exceptions.RequestException:
            pass # Ignore connection error, retry after delay

        time.sleep(RECONNECT_DELAY_S)

    print("\n[RECONNECT FAILED] Could not reconnect to the device. Aborting remaining tests.")
    return False

def test_api():
    """Runs tests against all major API endpoints."""
    print(f"Starting API Test against {BASE_URL}\n")

    # 1. Test Status/Voltage Endpoint (GET)
    try:
        response = requests.get(f"{BASE_URL}/api/status", timeout=5)
        print_response("1. Get Status and Voltage", response)

        if response.status_code != 200:
            print("Status check failed. Aborting tests.")
            return

        status_data = response.json()
        current_voltage = status_data.get('voltage', 'N/A')
        print(f"-> Current Voltage: {current_voltage}V")

    except requests.exceptions.RequestException as e:
        print(f"\n--- ERROR ---: Failed to connect to device. Check Wi-Fi and BASE_URL. Error: {e}")
        return

    # --- Time Setting ---

    # 2. Test Set Time Endpoint (POST)
    current_epoch = int(time.time())
    time_payload = {'time': current_epoch}

    try:
        response = requests.post(f"{BASE_URL}/api/time", data=time_payload, timeout=5)
        print_response("2. Set Device Time", response)
    except requests.exceptions.RequestException as e:
        print(f"Error setting time: {e}")

    # --- Data Collection ---

    # 3. Test Start Data Collection Endpoint (POST)
    # 10 seconds (10000 ms) for a brief test
    TEST_DURATION_MS = 10000
    collect_payload = {'duration_ms': TEST_DURATION_MS}
    print(f"\n-> Starting data collection for {TEST_DURATION_MS / 1000} seconds...")

    try:
        response = requests.post(f"{BASE_URL}/api/collect", data=collect_payload, timeout=5)
        print_response("3. Start Data Collection", response)

        if response.status_code == 200:
            # Wait for collection to finish, accounting for the device's sleep
            print(f"\n-> Waiting for {TEST_DURATION_MS / 1000} seconds...")
            time.sleep(TEST_DURATION_MS / 1000 + 1)
            print("-> Collection window finished. Wi-Fi should be re-initializing now.")

    except requests.exceptions.RequestException as e:
        print(f"Error starting collection: {e}")

    # --- Reconnect Logic (NEW) ---
    if not wait_for_reconnection():
        return

    # --- File Management ---

    # 4. Test List Files Endpoint (GET)
    try:
        response = requests.get(f"{BASE_URL}/api/files", timeout=10)
        print_response("4. List Files", response)

        file_list = response.json().get('files', [])
        if not file_list:
            print("-> No files found to download. Skipping download test.")
            return

        # 5. Test Download File Endpoint (GET)
        # Select the file created recently (or just the last one)
        target_file = file_list[-1]
        filename = target_file['name']
        print(f"\n-> Attempting to download file: {filename}")

        response = requests.get(f"{BASE_URL}/api/download?file={filename}", stream=True, timeout=30)

        if response.status_code == 200:
            os.makedirs(DOWNLOAD_DIR, exist_ok=True)
            download_path = os.path.join(DOWNLOAD_DIR, filename)

            with open(download_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)

            print(f"5. Download File: Success!")
            print(f"-> File saved to: {download_path} ({os.path.getsize(download_path)} bytes)")
        else:
            print_response(f"5. Download File Failed for {filename}", response)

    except requests.exceptions.RequestException as e:
        print(f"Error during file operation: {e}")

    # --- Shutdown ---

    # 6. Test Sleep Endpoint (POST)
    print("\n\n*** FINAL TEST: Initiating Sleep/Shutdown ***")
    try:
        # Note: This command is expected to cause a connection error as the device shuts down immediately after sending the response.
        response = requests.post(f"{BASE_URL}/api/sleep", timeout=5)
        print_response("6. System Sleep", response)
        print("\n-> The device should now be entering Deep Sleep mode.")
    except requests.exceptions.RequestException as e:
        print(f"Expected connection error during sleep command (device shut down): {e}")

if __name__ == "__main__":
    test_api()