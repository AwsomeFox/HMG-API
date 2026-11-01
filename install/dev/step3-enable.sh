#!/system/bin/sh
# HMG-API hook test, step 3: install libhmgapi_hook.so and hmgapi-hook.jar (Dalvik
# method hooks) and turn the preload on.
# Needs the step-1 wrapper (this build) in place. Run as root:
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/step3-enable.sh'
# Off again without touching /system: dev/hooks-off.sh. Everything back: dev/restore.sh.
# Takes effect at the next restart. ROOT is for off-car tests only.
# The NEMESIS daemon runs commands with umask 0: keep /data/hmgapi root-only.
umask 077
ROOT=${ROOT:-}
HERE=${HERE:-/data/local/tmp/hmgapi}
B=$ROOT/system/bin/app_process
LIB=$ROOT/system/lib/libhmgapi_hook.so
NEW=$ROOT/system/lib/libhmgapi_hook.so.hmgapi-new
JAR=$ROOT/system/framework/hmgapi-hook.jar
JNEW=$ROOT/system/framework/hmgapi-hook.jar.hmgapi-new
JCACHE=$ROOT/data/dalvik-cache/system@framework@hmgapi-hook.jar@classes.dex
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

log "hmgapi-hook step 3 enable $(date)"
[ -f "$HERE/out/libhmgapi_hook.so" ] || fail "library not found in $HERE/out"
[ -f "$HERE/out/hmgapi-hook.jar" ] || fail "hmgapi-hook.jar not found in $HERE/out"
[ "$(sum "$B")" = "$(sum "$HERE/out/app_process")" ] || fail "app_process is not this build's wrapper; run dev/step1-install.sh first"
# put <src> <dst> <tmp> <mode>: copy with md5 check and rename
put() {
    local want
    want=$(sum "$1")
    [ "$(sum "$2")" = "$want" ] && { log "already installed: $2"; return 0; }
    cp "$1" "$3" || fail "copy $1"
    chmod "$4" "$3"; chown root:root "$3" 2>/dev/null
    [ "$(sum "$3")" = "$want" ] || { rm -f "$3"; fail "md5 mismatch after copy to $3"; }
    mv "$3" "$2" || { rm -f "$3"; fail "rename onto $2"; }
    log "installed $2 ($want)"
}
nemesis remount rw >/dev/null 2>&1 || fail "remount rw"
put "$HERE/out/libhmgapi_hook.so" "$LIB" "$NEW" 644
put "$HERE/out/hmgapi-hook.jar" "$JAR" "$JNEW" 644
rm -f "$JCACHE"
sync
nemesis remount ro >/dev/null 2>&1 || log "WARN: remount ro failed (left rw until restart)"
echo 0 > "$HOOK/starts" || fail "write $HOOK/starts"
rm -f "$HOOK/disable"
: > "$HOOK/enable" || fail "write $HOOK/enable"
log "hooks on at the next restart; check: dmesg | grep hmgapi-hook, logcat -s HMG-API"
