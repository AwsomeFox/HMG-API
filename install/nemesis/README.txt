HMG-API @VERSION@ - install with NEMESIS
=======================================

1. Unzip this file onto the top level of a USB stick (FAT32 or exFAT), so the
   stick has NEMESIS.data and the packages folder at its root. Only one NEMESIS
   package on the stick at a time.
2. Plug the stick into the car's USB port once Android is up, and confirm the
   install of "HMG-API".
3. Restart the head unit when it says so, and take the stick out.

Updating the same way keeps HMG-API's mods. Uninstall from NEMESIS Manager, or:
    nemesis uninstall hmgapi
From a shell with NEMESIS's root (the stick in the first USB port):
    nemesis install /storage/usb0/packages/hmgapi.zip
