#!/bin/bash

VERSION="$1"

if [ -z "$VERSION" ]; then
    echo "Error: Version argument required"
    echo "Usage: ./install_apk.sh <version>"
    exit 1
fi

# Function to check for active ADB connection
check_adb_connection() {
    # Get list of connected devices (excluding header line)
    local devices=$(adb devices 2>/dev/null | grep -v "List of devices" | grep -v "^$" | grep "device$")

    if [ -z "$devices" ]; then
        return 1
    else
        return 0
    fi
}

# Function to pair and connect to device
pair_device() {
    echo ""
    echo "No ADB device connected."
    echo "To connect wirelessly, enable 'Wireless debugging' on your Android device"
    echo "and use 'Pair device with pairing code' option."
    echo ""

    read -p "Enter pairing host:port (e.g., 192.168.1.100:37123): " PAIR_HOST

    if [ -z "$PAIR_HOST" ]; then
        echo "Error: Pairing host required"
        exit 1
    fi

    read -p "Enter pairing code: " PAIR_CODE

    if [ -z "$PAIR_CODE" ]; then
        echo "Error: Pairing code required"
        exit 1
    fi

    echo "Pairing with device..."
    if ! adb pair "$PAIR_HOST" "$PAIR_CODE"; then
        echo "Error: Pairing failed"
        exit 1
    fi

    # Extract IP address from pairing host for connection
    DEVICE_IP=$(echo "$PAIR_HOST" | cut -d':' -f1)

    echo ""
    read -p "Enter connection port (shown in 'Wireless debugging' settings, e.g., 5555 or 38745): " CONNECT_PORT

    if [ -z "$CONNECT_PORT" ]; then
        echo "Error: Connection port required"
        exit 1
    fi

    echo "Connecting to device..."
    if ! adb connect "$DEVICE_IP:$CONNECT_PORT"; then
        echo "Error: Connection failed"
        exit 1
    fi

    echo "Successfully connected to device!"
}

# Check for ADB connection
echo "Checking for ADB connection..."
if ! check_adb_connection; then
    pair_device

    # Verify connection after pairing
    if ! check_adb_connection; then
        echo "Error: Still no device connected after pairing"
        exit 1
    fi
else
    echo "ADB device connected."
fi

DOWNLOAD_URL="https://github.com/kjenney/clickapp/releases/download/$VERSION/app-debug.apk"

echo "Checking if release exists: $VERSION"

# Check if the URL is valid before proceeding
if ! curl -s --head --fail "$DOWNLOAD_URL" > /dev/null 2>&1; then
    echo "Error: Release not found at $DOWNLOAD_URL"
    echo "Please check the version number and try again."
    exit 1
fi

echo "Release found. Downloading..."
wget -q "$DOWNLOAD_URL"

if [ ! -f "app-debug.apk" ]; then
    echo "Error: Download failed"
    exit 1
fi

echo "Uninstalling old version..."
adb uninstall com.example.clickapp 2>/dev/null || true

echo "Installing new version..."
mv app-debug.apk clickapp.apk
adb install -r clickapp.apk

echo "Cleaning up..."
rm -rf *.apk

echo "Done! Version $VERSION installed successfully."
