#!/system/bin/sh
# HMG-API hook test: turn the preload off at the next restart, without touching
# /system. Run as root:
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/hooks-off.sh'
# The NEMESIS daemon runs commands with umask 0: keep /data/hmgapi root-only.
umask 077
ROOT=${ROOT:-}
HOOK=$ROOT/data/hmgapi/hook
mkdir -p "$HOOK" 2>/dev/null
: > "$HOOK/disable" && echo "hooks off at the next restart ($HOOK/disable)" || { echo "FAIL: can't write $HOOK/disable"; exit 1; }
if [ -z "$ROOT" ]; then sh "${0%/*}/keys-release.sh"; fi
