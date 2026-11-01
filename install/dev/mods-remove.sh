#!/system/bin/sh
# HMG-API hook test: remove one installed mod. Its launcher entries leave the saved
# menu at the launcher's next start. Takes effect at the next restart.
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/mods-remove.sh <id>'
id=$1
case "$id" in ""|*/*|.*) echo "usage: mods-remove.sh <mod id>"; exit 2 ;; esac
D=${ROOT:-}/data/hmgapi/mods/$id
[ -d "$D" ] || { echo "no mod $id"; exit 1; }
rm -r "$D" && echo "removed mod $id; restart the head unit"
