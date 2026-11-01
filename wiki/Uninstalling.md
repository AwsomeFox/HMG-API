# Uninstalling

Uninstalling puts every file and setting HMG-API or its mods changed back to the
stock bytes, and removes the mods' apps.

- **USB stick:** add an empty file named `UNINSTALL` to the `HMGAPI` folder of the
  install stick and use the Engineering Mode route (`UNINSTALL-PURGE` also deletes
  HMG-API's data: mods, settings, backups).
- **NEMESIS:** uninstall "HMG-API" in NEMESIS Manager.
- **adb:** `./hmgapi-adb.sh uninstall` (`--purge` also deletes HMG-API's data).

Restart the head unit afterwards.
