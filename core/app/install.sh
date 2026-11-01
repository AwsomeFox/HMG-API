#!/system/bin/sh
# Install or update the HMG-API app on the head unit.
#   adb push out/HmgApi.apk /data/local/tmp/HmgApi.apk
#   adb shell "hmgapid sh 'sh /data/local/tmp/hmgapi/install-app.sh'"   (or nemesis root -c)
# While sys.boot_completed is 1 this head unit only installs apps signed by an
# OEM-allowlisted key (anything else: INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES),
# so, like NEMESIS, set it to 0 for the pm call only and put it back.
APK=${1:-/data/local/tmp/HmgApi.apk}
[ -f "$APK" ] || { echo "no $APK"; exit 1; }
b=$(getprop sys.boot_completed)
setprop sys.boot_completed 0
pm install -r "$APK"
setprop sys.boot_completed "$b"
pm path hmgapi.app
