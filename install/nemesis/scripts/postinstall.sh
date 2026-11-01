#!/system/bin/sh
# NEMESIS postinstall (full root, cwd /data/nemesis/apps/hmgapi): HMG-API's own
# installer on the payload in this package. Install and update alike.
umask 077
# HMGAPI/FORCE on a USB stick: install even if app_process isn't a known stock build (own risk)
for d in /storage/usb0 /storage/usb1 /storage/usb2; do [ -f "$d/HMGAPI/FORCE" ] && export HMGAPI_FORCE=1; done
exec sh ./payload/install.sh "$PWD/payload"
