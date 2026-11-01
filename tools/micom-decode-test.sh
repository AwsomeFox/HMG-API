#!/bin/sh
# Off-car test of tools/micom-decode.py on a made-up recording and catalogue.
set -e
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
cat > "$T/cat.json" <<'J'
{"HEV": {"cid": 136, "functions": {"EV_GW_EV_P_02_M": 35851}, "groups": {}},
 "DATC": {"cid": 194, "functions": {"DATC_STATUS_M": 33281}, "groups": {}}}
J
cat > "$T/micom.log" <<'L'
# watching 2 categories (88 c2)
1000 88 8c0b 0010ff
1500 c2 8201 00
2000 88 8c0b 0011ff
2500 88 9999 01
3000 88 9999 ____02
L
out=$(python3 tools/micom-decode.py "$T/micom.log" "$T/cat.json")
has "frames named from the catalogue" "HEV            EV_GW_EV_P_02_M" "$out"
has "times from the first frame" "    1.000  HEV" "$out"
has "an unknown function keeps its code" "HEV            0x9999" "$out"
out=$(python3 tools/micom-decode.py "$T/micom.log" "$T/cat.json" --changes)
has "changes: the byte that moved" "EV_GW_EV_P_02_M                          [1] 10->11" "$out"
has "changes: messages counted" "2  HEV EV_GW_EV_P_02_M" "$out"
out=$(python3 tools/micom-decode.py "$T/micom.log" "$T/cat.json" --only DATC)
check "only one category" 1 "$(echo "$out" | grep -c DATC_STATUS_M)"
has "... and nothing else" "DATC" "$(echo "$out" | grep -v HEV)"
has "blanked text bytes shown as __" "0x9999                                   __ __ 02" "$(python3 tools/micom-decode.py "$T/micom.log" "$T/cat.json")"
exit $fail
