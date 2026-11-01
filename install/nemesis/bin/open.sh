#!/system/bin/sh
# NEMESIS Manager's Launch for HMG-API ([app.0] in NEMESIS.data): opens the HMG-API
# app, installing it first if it's missing (HMG-API's /data/hmgapi/open.sh). No
# package= or component= on purpose: NEMESIS would pm uninstall that app on an update.
if [ ! -f /data/hmgapi/open.sh ]; then
    t="HMG-API isn't installed on this unit: see /data/hmgapi/install.log, or install it again."
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$t" --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
    echo "HMG-API isn't installed"
    exit 1
fi
exec sh /data/hmgapi/open.sh
