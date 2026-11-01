#!/system/bin/sh
# NEMESIS Manager's Launch for HMG-API ([app.0] in NEMESIS.data): opens the HMG-API
# app, installing it first if it's missing (HMG-API's /data/hmgapi/open.sh). No
# package= or component= on purpose: NEMESIS would pm uninstall that app on an update.
# Manager shows what this prints as a toast (pkg.c cmd_pkg_launch).
if [ ! -f /data/hmgapi/open.sh ]; then
    echo "HMG-API isn't installed on this unit: see /data/hmgapi/install.log, or install it again."
    exit 1
fi
exec sh /data/hmgapi/open.sh
