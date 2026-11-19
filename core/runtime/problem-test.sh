#!/bin/sh
# Off-car test of the problem log (engine problem record | stop, ProblemLog): a fake logcat
# with lines from allowlisted and other tags, a tombstone with raw memory and its process's
# log lines. What it keeps, what it must never keep (other apps' lines, addresses, numbers,
# phone ids, GPS positions, a dump's memory and log), and that stop ends a recording early.
set -e
cd "$(dirname "$0")/../.."
CP=$(mktemp -d)
sh tools/engine-classes.sh "$CP"
T=$(mktemp -d)
trap 'rm -rf "$T" "$CP"' EXIT
R=$T/root
D=$R/data/hmgapi
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
hasnt() { case "$3" in *"$2"*) echo "FAIL  $1: [$2] in [$3]"; fail=1 ;; *) echo "ok    $1" ;; esac; }

mkdir -p "$D/mods/wireless/" "$R/system/bin" "$R/system/lib" "$R/data/tombstones" "$R/storage/usb0"
printf 'ro.build.product=ZZEV.USA.0000.V015.012.260507\nro.board.platform=tcc897x\n' > "$R/props.txt"
printf '{"id": "wireless", "version": "1.0", "hooks": {"native": {"libCarPlayUtils.so": ["recipes/x.recipe"], "libGone.so": []}}}' > "$D/mods/wireless/mod.json"
printf 'stock bytes' > "$R/system/lib/libCarPlayUtils.so"
cat > "$R/system/bin/logcat" <<'EOF'
#!/bin/sh
cat <<'L'
--------- beginning of /dev/log/main
11-19 13:02:01.100  1151  1170 I TccCarPlay: audio stream start fmt=aac rate=44100
11-19 13:02:01.200  1151  1170 E LeafiaAAC: FDK from libstagefright_soft_aacdec.so: ok
11-19 13:02:01.300   900   920 I BluetoothPbapManager: contact Jane Doe +1 (555) 123-4567
11-19 13:02:01.400   900   921 D CarPlay_Session: iPhone 00008030-001A2B3C4D5E802E at 3c:22:fb:12:34:56
11-19 13:02:01.500   700   710 I ActivityManager: START u0 {act=android.intent.action.CALL dat=tel:5551234567}
11-19 13:02:01.600   700   710 I ActivityManager: Process com.daudio.app.dipo (pid 1151) has died
11-19 13:02:01.700   600   610 I Navi: position 37.774929, -122.419416
11-19 13:02:01.800  1151  1170 F libc: Fatal signal 11 (SIGSEGV) at 0x00000000 (code=1), thread 1170 (CarPlayAudio)
11-19 13:02:01.900  1151  1170 I AudioFlinger: route at 47.6062 -122.3321 vin KNAC0000000000000
11-19 13:02:02.000  1364  1866 I BTCMStateMachine: <BTCM> Bluetooth start disconnection to (Andr***, B0:EC:69:5B:E4:E2) by User Actions
L
# then stream until killed, like logcat
while :; do sleep 1; done
EOF
chmod +x "$R/system/bin/logcat"
cat > "$R/data/tombstones/tombstone_00" <<'EOF'
*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
Build fingerprint: 'tcc/tcc897x/tcc897x:4.4.2/KOT49H/1:user/release-keys'
pid: 1151, tid: 1170, name: CarPlayAudio  >>> com.daudio.app.dipo <<<
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 00000000
    r0 00000000  r1 4f2a1000  r2 00000400  r3 00000000
backtrace:
    #00  pc 00004a10  /system/lib/libleafiaaac.so (LeafiaAudioConverterFillComplexBuffer+88)
    #01  pc 00012345  /system/lib/libCarPlayCore.so
stack:
         5f3e2a00  6a616e65  SECRETSTACK
memory near r1:
    4f2a1000 4a616e65 20446f65 Jane Doe SECRETMEM
code around pc:
    00004a00 e5901000 e3510000
memory map around fault addr 00000000:
    (no map below)
    40000000-40010000 r-x /system/lib/libleafiaaac.so
--- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
pid: 1151, tid: 1180, name: Binder_1  >>> com.daudio.app.dipo <<<
backtrace:
    #00  pc 00021a3c  /system/lib/libc.so (__ioctl+8)
stack:
         5f3e2b00  SECRETSTACK2
--------- tail end of log /dev/log/main
11-19 13:01:59.000  1151  1151 I DiPOManager: caller Jane Doe SECRETLOG
EOF

# a short recording that runs to its time limit
set +e
out=$(HMGAPI_ROOT=$R HMGAPI_HOST=1 HMGAPI_TEST_ANY_CAR=1 java -cp "$CP" hmgapi.engine.Engine problem record --crash-dumps --usb --seconds 2 2>&1); rc=$?
set -e
check "record runs" 0 $rc
has "file named for the car" "problem: problem-zzev-usa-260507-" "$out"
has "count of kept lines" "kept: 7 of 11 log lines" "$out"
has "logcat columns left as they are" "11-19 13:02:01.100  1151  1170 I TccCarPlay:" "$(cat "$D"/public/reports/problem-*.txt)"
has "ended by the time limit" "ended by time limit (2 s)" "$out"
has "saved on the USB stick" "usb: /storage/usb0/HMG-API reports/problem-" "$out"
f=$(cat "$D"/public/reports/problem-*.txt)
check "same file on the stick" "$f" "$(cat "$R/storage/usb0/HMG-API reports"/problem-*.txt)"
has "unit" "model=ZZEV region=USA firmware=260507 platform=tcc897x" "$f"
has "CarPlay line kept" "TccCarPlay: audio stream start fmt=aac" "$f"
has "converter line kept" "LeafiaAAC: FDK from" "$f"
has "native crash line kept" "F libc: Fatal signal 11" "$f"
has "ActivityManager: process death kept" "com.daudio.app.dipo (pid 1151) has died" "$f"
hasnt "ActivityManager: intents left out" "act=android.intent.action.CALL" "$f"
hasnt "other apps left out (contacts)" "Jane Doe" "$f"
hasnt "other apps left out (navigation)" "122.419416" "$f"
has "kept tags counted" "  CarPlay_Session: 1" "$f"
has "Bluetooth manager line kept" "BTCMStateMachine: <BTCM> Bluetooth start disconnection to" "$f"
hasnt "Bluetooth manager line: no address" "5B:E4:E2" "$f"
hasnt "no Bluetooth address" "3c:22:fb" "$f"
hasnt "no iPhone UDID" "001A2B3C4D5E802E" "$f"
hasnt "no GPS position in a kept line" "47.6062" "$f"
hasnt "no VIN" "KNAC0000000000000" "$f"
has "mod listed" "wireless 1.0 (on)" "$f"
has "patched library's md5" "libCarPlayUtils.so $(printf 'stock bytes' | md5sum | cut -c1-32)" "$f"
has "absent library" "libGone.so absent" "$f"
has "dump: crashed process" "name: CarPlayAudio  >>> com.daudio.app.dipo <<<" "$f"
has "dump: signal" "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)" "$f"
has "dump: registers" "r0 00000000  r1 4f2a1000" "$f"
has "dump: backtrace" "LeafiaAudioConverterFillComplexBuffer+88" "$f"
has "dump: other threads' backtraces" "__ioctl+8" "$f"
has "dump: map near the fault" "40000000-40010000 r-x /system/lib/libleafiaaac.so" "$f"
hasnt "dump: no stack" "SECRETSTACK" "$f"
hasnt "dump: no memory near registers" "SECRETMEM" "$f"
hasnt "dump: no code bytes" "e5901000" "$f"
hasnt "dump: no log lines" "SECRETLOG" "$f"

# without --crash-dumps: none
rm -f "$D"/public/reports/problem-*
out=$(HMGAPI_ROOT=$R HMGAPI_HOST=1 HMGAPI_TEST_ANY_CAR=1 java -cp "$CP" hmgapi.engine.Engine problem record --seconds 1 2>&1)
f=$(cat "$D"/public/reports/problem-*.txt)
has "dumps off: said so" "crash dumps: not included" "$f"
hasnt "dumps off: no dump" "CarPlayAudio  >>>" "$f"

# stop ends a long recording early
rm -f "$D"/public/reports/problem-*
start=$(date +%s)
HMGAPI_ROOT=$R HMGAPI_HOST=1 HMGAPI_TEST_ANY_CAR=1 java -cp "$CP" hmgapi.engine.Engine problem record --seconds 60 > "$T/long.out" 2>&1 &
bg=$!
sleep 3
HMGAPI_ROOT=$R HMGAPI_HOST=1 HMGAPI_TEST_ANY_CAR=1 java -cp "$CP" hmgapi.engine.Engine problem stop > /dev/null 2>&1
wait $bg
took=$(( $(date +%s) - start ))
has "stop: ended by stop" "ended by stopped" "$(cat "$T/long.out")"
[ $took -lt 30 ] && echo "ok    stop: ended early (${took} s)" || { echo "FAIL  stop: took ${took} s"; fail=1; }
check "stop: its flag cleared" "no" "$([ -e "$D/state/problem.stop" ] && echo yes || echo no)"

set +e
HMGAPI_ROOT=$R HMGAPI_HOST=1 java -cp "$CP" hmgapi.engine.Engine problem > /dev/null 2>&1; rc=$?
set -e
check "no subcommand: usage" 2 $rc

[ $fail = 0 ] && echo "problem: all ok" || { echo "problem: FAILED"; exit 1; }
