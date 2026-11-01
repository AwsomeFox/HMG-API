HMG-API @VERSION@ - install over adb
===================================

Needs adb (Android platform-tools) on a computer, and the head unit on adb
(USB or Wi-Fi). The first install needs root from NEMESIS; after that, HMG-API
gives its own.

  ./hmgapi-adb.sh install              install or update
  ./hmgapi-adb.sh uninstall [--purge]  everything back to stock (--purge: also HMG-API's data)
  ./hmgapi-adb.sh status               what's installed

Restart the head unit afterwards.
