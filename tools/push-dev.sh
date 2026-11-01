#!/bin/sh
# Copy the build (out/) and the dev install scripts to the head unit's staging
# folder /data/local/tmp/hmgapi (out/ and dev/), for install/dev/*.sh.
set -e
cd "$(dirname "$0")/.."
[ -f out/hmgapid ] || { echo "run ./build.sh first"; exit 1; }
adb shell "rm -r /data/local/tmp/hmgapi 2>/dev/null; mkdir -p /data/local/tmp/hmgapi"
adb push out /data/local/tmp/hmgapi/ | tail -1
adb push install/dev /data/local/tmp/hmgapi/ | tail -1
adb push core/app/install.sh /data/local/tmp/hmgapi/install-app.sh | tail -1
adb shell chmod 755 /data/local/tmp/hmgapi/out/hmgapid /data/local/tmp/hmgapi/out/lpatch
