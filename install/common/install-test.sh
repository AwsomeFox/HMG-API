#!/bin/sh
# Off-car test of install.sh / uninstall.sh against a fake head unit: install,
# update, uninstall must leave /system byte-identical; refusals change nothing.
# UNIT_SH: the shell to run them with (busybox ash is closest to the unit's mksh).
# Needs out/payload (./build.sh payload).
set -e
cd "$(dirname "$0")/../.."
SH=${UNIT_SH:-sh}
PL=$PWD/out/payload
[ -f "$PL/MANIFEST" ] || { echo "run ./build.sh payload first"; exit 1; }
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
R=$T/root
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
# stubs for the unit's tools
mkdir -p $T/bin
printf '#!/bin/sh\n[ -f "$1" ] || exit 1\nset -- $(md5sum "$1"); echo "$1 $2"\n' > $T/bin/md5
printf '#!/bin/sh\nexit 0\n' > $T/bin/chown
chmod +x $T/bin/*
run() { set +e; out=$(cd $T && env PATH="$T/bin:$PATH" ROOT=$R CAPCHECK=0 $SH "$@" 2>&1); rc=$?; set -e; }
tree() { (cd $R/system && find . -type f | sort | while read f; do echo "$(md5sum < "$f" | cut -c1-32) $f"; done) }

mkdir -p $R/system/bin $R/system/lib $R/system/framework $R/system/etc $R/data/dalvik-cache
STOCK=$HOME/hmg-private/stock/ev6-sysimg/bin/app_process
if [ -f "$STOCK" ]; then cp $STOCK $R/system/bin/app_process; else head -c 9000 /dev/urandom > $R/system/bin/app_process; fi
chmod 755 $R/system/bin/app_process
printf '#!/system/bin/sh\n# NEMESIS\nnemesis watchdog\nexit 0\n' > $R/system/etc/install-recovery.sh; chmod 755 $R/system/etc/install-recovery.sh
printf 'echo qb stock\n# NEMESIS_QB_HOOK\nnemesis watchdog\n' > $R/system/etc/qb_runtime.sh
echo stock > $R/system/lib/libc.so
BEFORE=$(tree)
# the payload's binaries, with this tree's scripts (so a script edit needs no rebuild)
P=$T/payload; cp -r $PL $P
cp install/common/install.sh install/common/uninstall.sh install/common/lib.sh install/common/stock.txt install/common/app-pending.sh $P/
[ -f "$STOCK" ] || echo "$(md5sum < $R/system/bin/app_process | cut -c1-32) test stock" > $P/stock.txt
(cd $P && md5sum hmgapid lpatch app_process libhmgapi_hook.so hmgapi-hook.jar HmgApi.apk install.sh uninstall.sh lib.sh stock.txt app-pending.sh MAPS.md5 > MANIFEST)

# refusals first: nothing may change
cp $R/system/bin/app_process $T/real; echo x >> $R/system/bin/app_process
run $P/install.sh $P
has "unknown app_process refused" "isn't a known stock build, so nothing was changed" "$out"
check "refusal exit code" 1 $rc
check "nothing installed" no "$([ -e $R/system/bin/hmgapid ] && echo yes || echo no)"
cp $T/real $R/system/bin/app_process
echo junk >> $P/lpatch
run $P/install.sh $P
has "damaged payload refused" "payload file lpatch is damaged" "$out"
cp $PL/lpatch $P/lpatch
check "still stock after refusals" "$BEFORE" "$(tree)"

run $P/install.sh $P
has "install finishes" "installed. Restart the head unit" "$out"
check "app_process is the wrapper" "$(md5sum < $P/app_process)" "$(md5sum < $R/system/bin/app_process)"
check "stock app_process kept beside it" "$(md5sum < $T/real)" "$(md5sum < $R/system/bin/app_process.hmgapi-stock)"
check "stock app_process backed up" "$(md5sum < $T/real)" "$(md5sum < $R/data/hmgapi/stock/system/bin/app_process)"
for f in bin/hmgapid bin/lpatch lib/libhmgapi_hook.so framework/hmgapi-hook.jar; do
    check "installed $f" "$(md5sum < $P/${f##*/})" "$(md5sum < $R/system/$f 2>/dev/null)"
done
check "boot hook in install-recovery.sh" 1 "$(grep -c 'HMGAPI_HOOK begin' $R/system/etc/install-recovery.sh)"
check "boot hook in qb_runtime.sh" 1 "$(grep -c 'HMGAPI_HOOK begin' $R/system/etc/qb_runtime.sh)"
check "hooks on at the next start" "yes" "$([ -f $R/data/hmgapi/hook/enable ] && echo yes)"
check "updates off by default" '{"updates": false}' "$(cat $R/data/hmgapi/settings/hmgapi.json)"
check "/data/hmgapi: traverse only for apps" "drwx--x--x" "$(ls -ld $R/data/hmgapi | cut -c1-10)"
check "Android down: app kept for later, readable by the package manager" "-rw-r--r--" "$(ls -l $R/data/hmgapi/public/HmgApi.apk | cut -c1-10)"
check "... and its installer kept" yes "$([ -f $R/data/hmgapi/app-pending.sh ] && echo yes)"
has "boot hook starts it while the app waits" "app-pending.sh" "$(cat $R/system/etc/install-recovery.sh)"
check "uninstaller kept on the unit" yes "$([ -f $R/data/hmgapi/uninstall.sh ] && echo yes)"
check "hook maps installed" yes "$([ -f $R/data/hmgapi/maps/kia/ev6/usa/260507/car.json ] && [ -s $R/data/hmgapi/maps/points.txt ] && echo yes)"
has "install record" "file $(md5sum < $P/app_process | cut -c1-32) /system/bin/app_process" "$(cat $R/data/hmgapi/installed.txt)"
echo '{"updates": true}' > $R/data/hmgapi/settings/hmgapi.json

run $P/install.sh $P
has "update over itself" "HMG-API's wrapper is in place" "$out"
check "update keeps the stock copy" "$(md5sum < $T/real)" "$(md5sum < $R/system/bin/app_process.hmgapi-stock)"
check "update keeps one boot hook" 1 "$(grep -c 'HMGAPI_HOOK begin' $R/system/etc/install-recovery.sh)"
check "update keeps the owner's settings" '{"updates": true}' "$(cat $R/data/hmgapi/settings/hmgapi.json)"

run $R/data/hmgapi/uninstall.sh
has "uninstall finishes" "HMG-API uninstalled" "$out"
check "/system byte-identical to stock" "$BEFORE" "$(tree)"
check "settings kept without --purge" yes "$([ -f $R/data/hmgapi/settings/hmgapi.json ] && echo yes)"
check "hooks off" no "$([ -f $R/data/hmgapi/hook/enable ] && echo yes || echo no)"
check "waiting app removed" no "$([ -f $R/data/hmgapi/public/HmgApi.apk ] && echo yes || echo no)"

run $P/install.sh $P
run $R/data/hmgapi/uninstall.sh --purge
check "reinstall + purge: /system stock" "$BEFORE" "$(tree)"
check "purge removes /data/hmgapi" no "$([ -e $R/data/hmgapi ] && echo yes || echo no)"
exit $fail
