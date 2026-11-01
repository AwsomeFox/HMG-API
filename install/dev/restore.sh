#!/system/bin/sh
# HMG-API hook test: put the original app_process back. Works with Android down
# (over ADB). Run as root:
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/restore.sh'
# Takes effect at the next restart. ROOT is for off-car tests only.
# The NEMESIS daemon runs commands with umask 0: keep /data/hmgapi root-only.
umask 077
ROOT=${ROOT:-}
STOCK_MD5=33f798115689d1859e8d2159dc88e7dc   # app_process, EV6 US 260507
B=$ROOT/system/bin/app_process
KEEP=$ROOT/system/bin/app_process.hmgapi-stock
NEW=$ROOT/system/bin/app_process.hmgapi-new
BAK=$ROOT/data/hmgapi/stock/system/bin/app_process
LOG=$ROOT/data/hmgapi/hook/install.log

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

mkdir -p "$ROOT/data/hmgapi/hook" 2>/dev/null
log() { echo "$1"; echo "$1" >> "$LOG" 2>/dev/null; }
sum() { set -- $(md5 "$1" 2>/dev/null); echo "$1"; }
fail() { log "FAIL: $1"; nemesis remount ro >/dev/null 2>&1; exit 1; }

log "hmgapi-hook restore $(date)"
if [ -z "$ROOT" ] && [ -f "${0%/*}/keys-release.sh" ]; then sh "${0%/*}/keys-release.sh"; fi
if [ -z "$ROOT" ] && [ -f "${0%/*}/updates-on.sh" ]; then sh "${0%/*}/updates-on.sh"; fi
[ -f "$BAK" ] || fail "no backup at $BAK"
ORIG=$(sum "$BAK")
[ -n "$ORIG" ] || fail "can't read $BAK"
if [ "$ORIG" != "$STOCK_MD5" ]; then
    if [ "$HMGAPI_FORCE" = 1 ]; then
        log "WARNING: backup md5 $ORIG is not the known stock $STOCK_MD5; HMGAPI_FORCE=1, restoring it anyway"
    else
        fail "backup md5 $ORIG is not the known stock $STOCK_MD5. Nothing changed. Set HMGAPI_FORCE=1 to restore it anyway, at your own risk."
    fi
fi

nemesis remount rw >/dev/null 2>&1 || fail "remount rw"
if [ "$(sum "$B")" != "$ORIG" ]; then
    cp "$BAK" "$NEW" || fail "copy backup"
    chmod 755 "$NEW"; chown root:shell "$NEW" 2>/dev/null
    [ "$(sum "$NEW")" = "$ORIG" ] || { rm -f "$NEW"; fail "md5 mismatch after copy"; }
    mv "$NEW" "$B" || { rm -f "$NEW"; fail "rename onto app_process"; }
    log "app_process restored ($ORIG)"
else
    log "app_process was already the original"
fi
rm -f "$KEEP"
if [ -f "$ROOT/system/lib/libhmgapi_hook.so" ]; then
    rm -f "$ROOT/system/lib/libhmgapi_hook.so" && log "removed /system/lib/libhmgapi_hook.so"
fi
if [ -f "$ROOT/system/framework/hmgapi-hook.jar" ]; then
    rm -f "$ROOT/system/framework/hmgapi-hook.jar" && log "removed /system/framework/hmgapi-hook.jar"
fi
rm -f "$ROOT/data/dalvik-cache/system@framework@hmgapi-hook.jar@classes.dex"
rm -f "$ROOT/data/hmgapi/hook/enable"
sync
nemesis remount ro >/dev/null 2>&1 || log "WARN: remount ro failed (left rw until restart)"
[ "$(sum "$B")" = "$ORIG" ] || fail "app_process is not the original after restore"
[ ! -e "$ROOT/system/lib/libhmgapi_hook.so" ] || fail "libhmgapi_hook.so still there"
[ ! -e "$ROOT/system/framework/hmgapi-hook.jar" ] || fail "hmgapi-hook.jar still there"
log "done; restart the head unit. The backup stays in $BAK"
