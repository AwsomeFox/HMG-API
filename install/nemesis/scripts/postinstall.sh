#!/system/bin/sh
# NEMESIS postinstall (full root, cwd /data/nemesis/apps/hmgapi): HMG-API's own
# installer on the payload in this package. Install and update alike.
umask 077
exec sh ./payload/install.sh "$PWD/payload"
