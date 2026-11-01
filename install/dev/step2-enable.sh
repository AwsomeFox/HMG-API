#!/system/bin/sh
# HMG-API hook test, step 2: install libhmgapi_hook.so and turn the preload on.
# Needs the step-1 wrapper (this build) in place. Run as root:
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/step2-enable.sh'
# Off again without touching /system: dev/hooks-off.sh. Everything back: dev/restore.sh.
# Takes effect at the next restart. ROOT is for off-car tests only.
# The NEMESIS daemon runs commands with umask 0: keep /data/hmgapi root-only.
umask 077
ROOT=${ROOT:-}
HERE=${HERE:-/data/local/tmp/hmgapi}
B=$ROOT/system/bin/app_process
LIB=$ROOT/system/lib/libhmgapi_hook.so
NEW=$ROOT/system/lib/libhmgapi_hook.so.hmgapi-new
HOOK=$ROOT/data/hmgapi/hook
LOG=$HOOK/install.log

# Root from ADB via the SUID nemesis only has CAP_SETUID/SETGID (adbd's bounding
# set), so remount, chown and /data writes fail. Needs the daemon's full root:
# a non-SUID copy of nemesis (/data/local/tmp/nemesis-d) goes through it.
capcheck() {
    [ "${CAPCHECK:-1}" = 1 ] || return 0
    local k v
    while read k v; do [ "$k" = "CapEff:" ] && break; done < /proc/self/status
    v=${v#????????}
    [ $((0x$v & 0x200003)) = $((0x200003)) ] && return 0
    echo "FAIL: not full root (CapEff ${v}). Run it through the NEMESIS daemon:"
    echo "  adb shell cp /system/xbin/nemesis /data/local/tmp/nemesis-d"
    echo "  adb shell /data/local/tmp/nemesis-d root -c 'sh $0'"
    exit 1
}
capcheck
mkdir -p "$HOOK" 2>/dev/null
log() { echo "$1"; echo "$1" >> "$LOG" 2>/dev/null; }
sum() { set -- $(md5 "$1" 2>/dev/null); echo "$1"; }
fail() { log "FAIL: $1"; nemesis remount ro >/dev/null 2>&1; exit 1; }

log "hmgapi-hook step 2 enable $(date)"
[ -f "$HERE/out/libhmgapi_hook.so" ] || fail "library not found in $HERE/out"
[ "$(sum "$B")" = "$(sum "$HERE/out/app_process")" ] || fail "app_process is not this build's wrapper; run dev/step1-install.sh first"
WANT=$(sum "$HERE/out/libhmgapi_hook.so")
if [ "$(sum "$LIB")" != "$WANT" ]; then
    nemesis remount rw >/dev/null 2>&1 || fail "remount rw"
    cp "$HERE/out/libhmgapi_hook.so" "$NEW" || fail "copy library"
    chmod 644 "$NEW"; chown root:root "$NEW" 2>/dev/null
    [ "$(sum "$NEW")" = "$WANT" ] || { rm -f "$NEW"; fail "library md5 mismatch after copy"; }
    mv "$NEW" "$LIB" || { rm -f "$NEW"; fail "rename library"; }
    sync
    nemesis remount ro >/dev/null 2>&1 || log "WARN: remount ro failed (left rw until restart)"
    log "installed $LIB ($WANT)"
else
    log "library already installed ($WANT)"
fi
echo 0 > "$HOOK/starts" || fail "write $HOOK/starts"
rm -f "$HOOK/disable"
: > "$HOOK/enable" || fail "write $HOOK/enable"
log "hooks on at the next restart; check: dmesg | grep hmgapi-hook, logcat -s HMG-API"
