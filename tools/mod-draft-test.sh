#!/bin/sh
# Test of tools/mod-draft.py on a made-up package.
set -e
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
P=$T/pkg
mkdir -p $P/app $P/svc $P/scripts $P/bin
cat > $P/NEMESIS.data <<'D'
[nemesis]
version=@VERSION@
id=my.cam
name=My Cam
postinstall=scripts/post.sh

[app.0]
kind=apk
path=app/Cam.apk
package=dev.example.cam
component=dev.example.cam/.MainActivity

[app.1]
kind=sh
path=bin/open.sh

[service.0]
name=Recorder
kind=sh
path=svc/rec.sh
uid=system
mode=restart

[service.1]
name=once
path=svc/once.sh
mode=once

[service.2]
name=ui
kind=instance
path=dev.example.cam/.Svc
D
echo apk > $P/app/Cam.apk
printf '#!/system/bin/sh\nD=/data/nemesis/apps/my.cam\nservice call micom.usm 213 i32 1\n# service call ignored in comments\nnemesis remount rw\n' > $P/svc/rec.sh
printf 'echo once\n' > $P/svc/once.sh
printf 'pm install -r x.apk\n' > $P/scripts/post.sh
printf 'am start x\n' > $P/bin/open.sh
out=$(python3 -I tools/mod-draft.py $P --out $T/mod)
j=$T/mod/mod.json
q() { python3 -c "import json,sys; d=json.load(open('$j')); print($1)"; }
check "id made HMG-API-safe" my-cam "$(q 'd["id"]')"
check "placeholder version" 0.1.0 "$(q 'd["version"]')"
check "apk app" "dev.example.cam app/Cam.apk" "$(q 'd["apps"][0]["package"], d["apps"][0]["apk"]' | tr -d "(),'")"
check "component becomes a tile" dev.example.cam.MainActivity "$(q 'd["provides"]["launcher"][0]["class"]')"
check "sh service, system, restarted" "recorder svc/rec.sh system True boot" "$(q 'd["services"][0]["name"], d["services"][0]["script"], d["services"][0]["user"], d["services"][0]["restart"], d["services"][0]["start"]' | tr -d "(),'")"
check "mode=once: not restarted" False "$(q 'd["services"][1]["restart"]')"
check "instance service left out" 2 "$(q 'len(d["services"])')"
has "instance explained" "service ui: kind=instance" "$out"
has "app script explained" "app script bin/open.sh" "$out"
has "install script listed" "postinstall=scripts/post.sh" "$out"
has "NEMESIS package path" "svc/rec.sh:2: D=/data/nemesis/apps/my.cam" "$out"
has "raw binder call" "svc/rec.sh:3: service call micom.usm 213" "$out"
has "remount" "svc/rec.sh:5: nemesis remount rw" "$out"
has "pm in a script" "scripts/post.sh:1: pm install" "$out"
case "$out" in *"ignored in comments"*) echo "FAIL  comments skipped"; fail=1 ;; *) echo "ok    comments skipped" ;; esac
check "files copied, NEMESIS.data and install scripts left out" "DRAFT-NOTES.txt app bin mod.json svc" "$(ls $T/mod | LC_ALL=C sort | tr '\n' ' ' | sed 's/ $//')"
check "author's scripts unchanged" "$(md5sum < $P/svc/rec.sh)" "$(md5sum < $T/mod/svc/rec.sh)"
exit $fail
