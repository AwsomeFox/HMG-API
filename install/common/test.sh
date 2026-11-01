#!/bin/sh
# Off-car test of install/common/lib.sh (the boot hook) against a fake root.
# UNIT_SH: the shell to run it with (an ARM busybox ash is closest to the unit's mksh).
set -e
cd "$(dirname "$0")"
SH=${UNIT_SH:-sh}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
L() { env ROOT="$T" $SH -c ". $PWD/lib.sh; $*"; }
mkdir -p "$T/system/etc"
printf '#!/system/bin/sh\n# NEMESIS\nnemesis watchdog\nexit 0\n' > "$T/system/etc/install-recovery.sh"; chmod 755 "$T/system/etc/install-recovery.sh"
printf 'echo qb stock\n# NEMESIS_QB_HOOK\nnemesis watchdog\n' > "$T/system/etc/qb_runtime.sh"
IR=$(md5sum < "$T/system/etc/install-recovery.sh"); QB=$(md5sum < "$T/system/etc/qb_runtime.sh")

L hook_add /system/etc/install-recovery.sh top
check "block after the first line" "# HMGAPI_HOOK begin (HMG-API: starts hmgapid. HMG-API's uninstall removes this block.)" "$(sed -n 2p "$T/system/etc/install-recovery.sh")"
check "still ends with exit 0" "exit 0" "$(tail -1 "$T/system/etc/install-recovery.sh")"
check "NEMESIS lines kept" "1" "$(grep -c 'nemesis watchdog' "$T/system/etc/install-recovery.sh")"
check "stays executable" "yes" "$([ -x "$T/system/etc/install-recovery.sh" ] && echo yes)"
L hook_add /system/etc/install-recovery.sh top
check "adding twice keeps one block" "1" "$(grep -c 'HMGAPI_HOOK begin' "$T/system/etc/install-recovery.sh")"
L hook_add /system/etc/qb_runtime.sh end
check "qb block at the end" "# HMGAPI_HOOK end" "$(tail -1 "$T/system/etc/qb_runtime.sh")"
check "qb not made executable" "no" "$([ -x "$T/system/etc/qb_runtime.sh" ] && echo yes || echo no)"
sh -n "$T/system/etc/install-recovery.sh" && echo "ok    hooked script parses" || { echo "FAIL  hooked script syntax"; fail=1; }

L hook_remove /system/etc/install-recovery.sh
L hook_remove /system/etc/qb_runtime.sh
check "install-recovery.sh back byte for byte" "$IR" "$(md5sum < "$T/system/etc/install-recovery.sh")"
check "qb_runtime.sh back byte for byte" "$QB" "$(md5sum < "$T/system/etc/qb_runtime.sh")"

rm "$T/system/etc/install-recovery.sh"
L hook_add /system/etc/install-recovery.sh top
check "created when missing" "yes" "$([ -x "$T/system/etc/install-recovery.sh" ] && grep -q HMGAPI_CREATED "$T/system/etc/install-recovery.sh" && echo yes)"
L hook_remove /system/etc/install-recovery.sh
check "created file removed again" "no" "$([ -e "$T/system/etc/install-recovery.sh" ] && echo yes || echo no)"
exit $fail
