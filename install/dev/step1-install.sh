#!/system/bin/sh
# HMG-API hook test, step 1: put the zygote wrapper in front of app_process.
# Run as root:  adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/step1-install.sh'
# Undo:         adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/restore.sh'
# The wrapper only runs the stock binary until /data/hmgapi/hook/enable exists.
# Takes effect at the next restart. ROOT is for off-car tests only.
# The NEMESIS daemon runs commands with umask 0: keep /data/hmgapi root-only.
umask 077
ROOT=${ROOT:-}
HERE=${HERE:-/data/local/tmp/hmgapi}
STOCK_MD5=33f798115689d1859e8d2159dc88e7dc   # app_process, EV6 US 260507
B=$ROOT/system/bin/app_process
KEEP=$ROOT/system/bin/app_process.hmgapi-stock
NEW=$ROOT/system/bin/app_process.hmgapi-new
BAK=$ROOT/data/hmgapi/stock/system/bin/app_process
W=$HERE/out/app_process
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

mkdir -p "$ROOT/data/hmgapi/hook" "$ROOT/data/hmgapi/stock/system/bin" 2>/dev/null
chmod 700 "$ROOT/data/hmgapi" "$ROOT/data/hmgapi/hook" "$ROOT/data/hmgapi/stock" "$ROOT/data/hmgapi/stock/system" "$ROOT/data/hmgapi/stock/system/bin" 2>/dev/null
log() { echo "$1"; echo "$1" >> "$LOG" 2>/dev/null; }
sum() { set -- $(md5 "$1" 2>/dev/null); echo "$1"; }
fail() { log "FAIL: $1"; nemesis remount ro >/dev/null 2>&1; exit 1; }

log "hmgapi-hook step 1 install $(date)"
[ -f "$W" ] || fail "wrapper not found at $W (run tools/push-dev.sh first)"
WANT=$(sum "$W")
HAVE=$(sum "$B")
if [ "$HAVE" = "$WANT" ]; then
    log "already installed (app_process is the wrapper)"
    exit 0
fi
if [ "$HAVE" != "$STOCK_MD5" ] && [ -f "$BAK" ] && [ "$(sum "$BAK")" = "$STOCK_MD5" ] && [ "$(sum "$KEEP")" = "$STOCK_MD5" ]; then
    log "updating an earlier hmgapi-hook wrapper ($HAVE)"
    HAVE=$STOCK_MD5
    UPDATE=1
elif [ "$HAVE" != "$STOCK_MD5" ]; then
    if [ "$HMGAPI_FORCE" = 1 ]; then
        log "WARNING: app_process md5 $HAVE is not the known stock $STOCK_MD5; HMGAPI_FORCE=1, going on (restore puts back the first backed-up copy)"
    else
        fail "app_process md5 $HAVE is not the known stock $STOCK_MD5. Nothing changed. Set HMGAPI_FORCE=1 to install anyway, at your own risk."
    fi
fi

# one-time backup of the original
if [ ! -f "$BAK" ]; then
    cp "$B" "$BAK" || fail "backup copy"
    [ "$(sum "$BAK")" = "$HAVE" ] || { rm -f "$BAK"; fail "backup md5 mismatch"; }
    log "backed up app_process to $BAK"
fi

nemesis remount rw >/dev/null 2>&1 || fail "remount rw"
if [ -z "$UPDATE" ] && [ "$(sum "$KEEP")" != "$HAVE" ]; then
    cp "$B" "$KEEP" || fail "copy to $KEEP"
    chmod 755 "$KEEP"; chown root:shell "$KEEP" 2>/dev/null
    [ "$(sum "$KEEP")" = "$HAVE" ] || fail "$KEEP md5 mismatch"
fi
cp "$W" "$NEW" || fail "copy wrapper"
chmod 755 "$NEW"; chown root:shell "$NEW" 2>/dev/null
[ "$(sum "$NEW")" = "$WANT" ] || { rm -f "$NEW"; fail "wrapper md5 mismatch after copy"; }
mv "$NEW" "$B" || { rm -f "$NEW"; fail "rename onto app_process"; }
sync
nemesis remount ro >/dev/null 2>&1 || log "WARN: remount ro failed (left rw until restart)"
[ "$(sum "$B")" = "$WANT" ] || fail "app_process is not the wrapper after install"
log "installed: app_process = wrapper $WANT, stock kept as $KEEP"
log "restart the head unit, then check: dmesg | grep hmgapi-hook"
