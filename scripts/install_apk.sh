VERSION="$1"
wget "https://github.com/kjenney/clickapp/releases/download/$VERSION/app-debug.apk"
mv app-debug.apk clickapp.apk
adb install -r clickapp.apk
rm -rf *.apk