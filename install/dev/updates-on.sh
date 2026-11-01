#!/system/bin/sh
# HMG-API hook test: give over-the-air updates back to stock (restore.sh runs this):
# re-enable the OTA client if an earlier build disabled it, and put the owner's
# automatic-install setting back. Needs Android up. Run as root:
#   adb shell "hmgapid sh 'sh /data/local/tmp/hmgapi/dev/updates-on.sh'"
if [ "$(getprop persist.sys.hmgapi.otaoff)" = 1 ]; then
    pm enable com.hkmc.system.app.OTAClient && setprop persist.sys.hmgapi.otaoff ""
fi
p=$(getprop persist.sys.hmgapi.autoupd)
if [ -n "$p" ]; then
    settings put system gen_auto_update "$p" && setprop persist.sys.hmgapi.autoupd ""
fi
echo "gen_auto_update=$(settings get system gen_auto_update)"
