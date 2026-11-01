#!/system/bin/sh
# NEMESIS preuninstall (full root). NEMESIS runs it for an uninstall and also
# before an update (its update is uninstall + install, with the new package
# already staged in /data/local/tmp/nemesis/stage/hmgapi). An update keeps
# HMG-API installed: the new postinstall updates it in place, mods and all.
umask 077
if [ -d "${ROOT:-}/data/local/tmp/nemesis/stage/hmgapi" ]; then
    echo "update: HMG-API stays installed, the new package updates it"
    exit 0
fi
[ -f "${ROOT:-}/data/hmgapi/uninstall.sh" ] || { echo "HMG-API isn't installed"; exit 0; }
exec sh "${ROOT:-}/data/hmgapi/uninstall.sh"
