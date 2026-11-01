#!/system/bin/sh
# HMG-API hook test: if a star key is set to a mod's action (hmgapi.star.*), set it
# back to the stock "None" (hooks-off.sh and restore.sh run this). Needs Android
# up for the head-unit key (service call to com.hkmc.micom.mode). Run as root:
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/keys-release.sh'
case "$(getprop persist.sys.key.swrccustom)" in
hmgapi.star.*)
    setprop persist.sys.key.swrccustom com.mobis.intent.action.SWRC_CUSTOM_SHORTCUT
    echo "wheel star key: set to None" ;;
esac
case "$(getprop persist.sys.key.custom)" in
hmgapi.star.*)
    # IModeManager.setCustomKey = transaction 37; also writes the /oem_data backup
    if service call com.hkmc.micom.mode 37 s16 com.mobis.intent.action.CUSTOM_SHORTCUT >/dev/null 2>&1; then
        echo "head unit star key: set to None"
    else
        echo "WARN: head unit star key not reset (Android down?); run this again once it is up"
    fi ;;
esac
true
