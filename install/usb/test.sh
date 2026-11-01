#!/bin/sh
# Off-car test of the USB stick installer (troubleShooter, as the DSP OTA runs it)
# and of the NEMESIS package's scripts, on a fake head unit: install, update,
# uninstall from the stick; NEMESIS install, update (HMG-API stays), uninstall.
# /system must come back byte-identical. Builds out/dist (tools/mkinstallers.sh) if it isn't there.
set -e
cd "$(dirname "$0")/../.."
SH=${UNIT_SH:-sh}
V=$(cat out/payload/VERSION 2>/dev/null)
NZ=$PWD/out/dist/hmgapi-$V-nemesis.zip
[ -f "$NZ" ] || tools/mkinstallers.sh >/dev/null
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
R=$T/root; S=$T/stick
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
mkdir -p $T/bin
printf '#!/bin/sh\n[ -f "$1" ] || exit 1\nset -- $(md5sum "$1"); echo "$1 $2"\n' > $T/bin/md5
printf '#!/bin/sh\nexit 0\n' > $T/bin/chown
chmod +x $T/bin/*
run() { set +e; out=$(cd ${CWD:-$T} && env PATH="$T/bin:$PATH" ROOT=$R CAPCHECK=0 HMGAPI_USB_DIRS="$T/none $S" $SH "$@" 2>&1); rc=$?; set -e; }
tree() { (cd $R/system && find . -type f | sort | while read f; do echo "$(md5sum < "$f" | cut -c1-32) $f"; done) }
installed() { [ -f $R/system/bin/hmgapid ] && [ -f $R/system/bin/app_process.hmgapi-stock ] && echo yes || echo no; }

mkdir -p $R/system/bin $R/system/lib $R/system/framework $R/system/etc $R/data/dalvik-cache $R/data/local/tmp
STOCK=$HOME/hmg-private/stock/ev6-sysimg/bin/app_process
if [ -f "$STOCK" ]; then cp $STOCK $R/system/bin/app_process; else head -c 9000 /dev/urandom > $R/system/bin/app_process; fi
chmod 755 $R/system/bin/app_process
printf 'echo qb stock\n' > $R/system/etc/qb_runtime.sh
BEFORE=$(tree)
# the stick, as tools/mkinstallers.sh lays it out, with this tree's scripts
mkdir -p $S/HMGAPI/install
unzip -q "$NZ" 'payload/*' -d $T/pk
P=$S/HMGAPI/install/payload; mv $T/pk/payload $P
cp install/common/install.sh install/common/uninstall.sh install/common/lib.sh install/common/stock.txt install/common/app-pending.sh $P/
[ -f "$STOCK" ] || echo "$(md5sum < $R/system/bin/app_process | cut -c1-32) test stock" > $P/stock.txt
(cd $P && md5sum hmgapid lpatch app_process libhmgapi_hook.so hmgapi-hook.jar HmgApi.apk install.sh uninstall.sh lib.sh stock.txt app-pending.sh MAPS > MANIFEST)
TS=$PWD/install/usb/troubleShooter

mv $S/HMGAPI $S/X
run $TS
has "no HMGAPI folder: says so" "no HMGAPI folder on the USB stick. Nothing was changed" "$out"
check "... and fails" 1 $rc
mv $S/X $S/HMGAPI

run $TS
has "stick install: done" "HMG-API installed, restarting" "$out"
check "stick install: exit 0" 0 $rc
check "stick install: HMG-API is in" yes "$(installed)"
has "install log on the stick" "installed. Restart the head unit" "$(cat $S/HMGAPI/install.log)"
check "staging cleaned up" no "$([ -e $R/data/local/tmp/hmgapi-usb ] && echo yes || echo no)"
run $TS
has "stick again: update over itself" "HMG-API's wrapper is in place" "$(cat $S/HMGAPI/install.log)"

: > $S/HMGAPI/UNINSTALL
run $TS
has "UNINSTALL on the stick: uninstalled" "HMG-API uninstalled, restarting" "$out"
check "/system byte-identical to stock" "$BEFORE" "$(tree)"
has "uninstall log on the stick" "HMG-API uninstalled" "$(cat $S/HMGAPI/install.log)"
rm -r $R/data/hmgapi
run $TS
has "UNINSTALL with nothing installed: says so" "HMG-API isn't installed. Nothing was changed" "$out"
rm $S/HMGAPI/UNINSTALL

# NEMESIS: the package unpacked where NEMESIS puts it; postinstall runs there
A=$R/data/nemesis/apps/hmgapi
mkdir -p $A; unzip -q "$NZ" -d $A; rm -r $A/payload; cp -r $P $A/payload
has "package metadata" "postinstall=scripts/postinstall.sh" "$(cat $A/NEMESIS.data)"
CWD=$A run scripts/postinstall.sh
has "NEMESIS install: done" "installed. Restart the head unit" "$out"
check "NEMESIS install: HMG-API is in" yes "$(installed)"
mkdir -p $R/data/local/tmp/nemesis/stage/hmgapi
CWD=$A run scripts/preuninstall.sh
has "NEMESIS update: preuninstall keeps HMG-API" "HMG-API stays installed" "$out"
check "... still installed" yes "$(installed)"
CWD=$A run scripts/postinstall.sh
has "... and the new postinstall updates it" "HMG-API's wrapper is in place" "$out"
rm -r $R/data/local/tmp/nemesis/stage
CWD=$A run scripts/preuninstall.sh
has "NEMESIS uninstall: uninstalled" "HMG-API uninstalled" "$out"
check "/system byte-identical to stock again" "$BEFORE" "$(tree)"
exit $fail
