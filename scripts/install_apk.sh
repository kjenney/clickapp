#!/bin/bash

VERSION="$1"
PACKAGE_NAME="com.example.clickapp"

# If no version specified, fetch the latest release from GitHub
if [ -z "$VERSION" ]; then
    echo -e "No version specified. Fetching latest release..."
    VERSION=$(curl -s "https://api.github.com/repos/kjenney/clickapp/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

    if [ -z "$VERSION" ]; then
        echo -e "\tError: Could not fetch latest release version"
        echo -e "\tUsage: ./install_apk.sh [version]"
        exit 1
    fi

    echo -e "\tLatest version: $VERSION"
fi

# Function to check for active ADB connection
check_adb_connection() {
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
    echo -e "\tNo ADB device connected."
    echo -e "\tTo connect wirelessly, enable 'Wireless debugging' on your Android device"
    echo -e "\tand use 'Pair device with pairing code' option."
    echo ""

    read -p $'\tEnter pairing host:port (e.g., 192.168.1.100:37123): ' PAIR_HOST

    if [ -z "$PAIR_HOST" ]; then
        echo -e "\tError: Pairing host required"
        exit 1
    fi

    read -p $'\tEnter pairing code: ' PAIR_CODE

    if [ -z "$PAIR_CODE" ]; then
        echo -e "\tError: Pairing code required"
        exit 1
    fi

    echo -e "\tPairing with device..."
    if ! adb pair "$PAIR_HOST" "$PAIR_CODE"; then
        echo -e "\tError: Pairing failed"
        exit 1
    fi

    # Extract IP address from pairing host for connection
    DEVICE_IP=$(echo "$PAIR_HOST" | cut -d':' -f1)

    echo ""
    read -p $'\tEnter connection port (shown in Wireless debugging settings, e.g., 5555 or 38745): ' CONNECT_PORT

    if [ -z "$CONNECT_PORT" ]; then
        echo -e "\tError: Connection port required"
        exit 1
    fi

    echo -e "\tConnecting to device..."
    if ! adb connect "$DEVICE_IP:$CONNECT_PORT"; then
        echo -e "\tError: Connection failed"
        exit 1
    fi

    echo -e "\tSuccessfully connected to device!"
}

# Check for ADB connection
echo -e "Checking for ADB connection..."
if ! check_adb_connection; then
    pair_device

    # Verify connection after pairing
    if ! check_adb_connection; then
        echo -e "\tError: Still no device connected after pairing"
        exit 1
    fi
else
    echo -e "\tADB device connected."
fi

DOWNLOAD_URL="https://github.com/kjenney/clickapp/releases/download/$VERSION/app-debug.apk"

echo -e "Checking if release exists: $VERSION"

# Check if the URL is valid before proceeding
if ! curl -s --head --fail "$DOWNLOAD_URL" > /dev/null 2>&1; then
    echo -e "\tError: Release not found at $DOWNLOAD_URL"
    echo -e "\tPlease check the version number and try again."
    exit 1
fi

echo -e "\tRelease found. Downloading..."
wget -q "$DOWNLOAD_URL"

if [ ! -f "app-debug.apk" ]; then
    echo -e "\tError: Download failed"
    exit 1
fi

echo -e "Installing version $VERSION..."
mv app-debug.apk clickapp.apk
adb install -r -d -g clickapp.apk > /dev/null 2>&1

echo -e "Cleaning up..."
rm -rf *.apk

echo -e "\nDone! Version $VERSION installed successfully."
