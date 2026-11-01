#!/bin/sh
# Make HMG-API's install downloads in out/dist/:
#   hmgapi-<v>-usb.zip      a USB stick: DSP OTA (nothing installed yet), NEMESIS
#                           (NEMESIS.data type=install) and the app (HMGAPI/updates)
#   hmgapi-<v>-nemesis.zip  a NEMESIS package
#   hmgapi-<v>-adb.zip      hmgapi-adb.sh and the payload
# Every one carries the same payload (out/payload) and ends in install/common/install.sh.
# HMGAPI/updates gets the signed release (tools/mkrelease.sh, if the release key is
# here) and every signed mod package in out/packages (tools/mkmodpkg.sh).
set -e
cd "$(dirname "$0")/.."
./build.sh payload >/dev/null
V=$(cat out/payload/VERSION)
D=$PWD/out/dist
mkdir -p "$D"
rm -f "$D/hmgapi-$V-usb.zip" "$D/hmgapi-$V-nemesis.zip" "$D/hmgapi-$V-adb.zip"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT

# zipdir <dir> <zip>: fixed order and times, so the same input gives the same zip
zipdir() { (cd "$1" && find . -type f | TZ=UTC xargs touch -d 2026-01-01T00:00:00 && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$2" -@); }
readme() { sed "s/@VERSION@/$V/" "$1" > "$2"; }

# NEMESIS package
N=$W/nemesis
mkdir -p "$N"
sed "s/@VERSION@/$V/" install/nemesis/NEMESIS.data.in > "$N/NEMESIS.data"
cp -r install/nemesis/scripts "$N/scripts"
cp -r out/payload "$N/payload"
chmod 755 "$N"/scripts/*.sh
zipdir "$N" "$D/hmgapi-$V-nemesis.zip"

# adb
A=$W/adb/hmgapi-$V-adb
mkdir -p "$A"
cp install/adb/hmgapi-adb.sh "$A/" && chmod 755 "$A/hmgapi-adb.sh"
cp -r out/payload "$A/payload"
readme install/adb/README.txt "$A/README.txt"
zipdir "$W/adb" "$D/hmgapi-$V-adb.zip"

# USB stick
U=$W/usb
mkdir -p "$U/HMGAPI/install" "$U/HMGAPI/updates"
python3 - install/usb/troubleShooter "$U/ota_package.tar" <<'PY'
import io, sys, tarfile
data = open(sys.argv[1], "rb").read()
# what the DSP agent runs reliably: one regular file, troubleShooter, 0755, root (no ./map/new_one)
with tarfile.open(sys.argv[2], "w", format=tarfile.GNU_FORMAT) as t:
    i = tarfile.TarInfo("troubleShooter"); i.size = len(data); i.mode = 0o755; i.uid = i.gid = 0; i.mtime = 0
    t.addfile(i, io.BytesIO(data))
PY
cp -r out/payload "$U/HMGAPI/install/payload"
cp "$D/hmgapi-$V-nemesis.zip" "$U/HMGAPI/install/"
printf '[nemesis]\nversion=1.0\ntype=install\npath=HMGAPI/install/hmgapi-%s-nemesis.zip\n' "$V" > "$U/NEMESIS.data"
RK=${HMGAPI_RELEASE_KEY:-$HOME/hmg-private/keys/leafia-release.key}
if [ -f "$RK" ]; then
    cp "$(tools/mkrelease.sh "$RK" | tail -1)" "$U/HMGAPI/updates/"
else
    echo "no release key here: HMGAPI/updates has no HMG-API release"
fi
for z in out/packages/*.zip; do [ -f "$z" ] && cp "$z" "$U/HMGAPI/updates/"; done
readme install/usb/README.txt "$U/README.txt"
zipdir "$U" "$D/hmgapi-$V-usb.zip"

for z in usb nemesis adb; do echo "$D/hmgapi-$V-$z.zip $(du -k "$D/hmgapi-$V-$z.zip" | cut -f1) KB"; done
