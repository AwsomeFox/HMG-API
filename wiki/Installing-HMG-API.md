# Installing HMG-API

Takes about 10 minutes. Park the car and keep it on (the head unit restarts once).

**Which download?** Get it from the [Releases](https://github.com/VysseCodes/HMG-API/releases) page:

| You have | Use | Steps |
|---|---|---|
| Nothing installed, no computer | `hmgapi-<version>-usb.zip` | [USB stick](#usb-stick) |
| NEMESIS | `hmgapi-<version>-nemesis.zip` | [NEMESIS](#nemesis) |
| A computer with adb | `hmgapi-<version>-adb.zip` | [adb](#adb) |

All three install the same thing, and [uninstalling](Uninstalling) puts every file
back exactly as it was.

## USB stick

1. Unzip `hmgapi-<version>-usb.zip` onto an empty USB stick (FAT32), at the top level.
2. Plug the stick into the car's USB port.
3. Open **Engineering Mode → USB Copy → DSP → Update OTA**.
   The screen shows "HMG-API … installing", then "HMG-API installed, restarting".
   Error 684 on the OTA screen afterwards is expected: nothing is undone.
4. Take the stick out once the head unit has restarted.

What happened is written to `HMGAPI/install.log` on the stick.

## NEMESIS

1. Copy `hmgapi-<version>-nemesis.zip` to a USB stick.
2. Plug it in and confirm the install of "HMG-API" in NEMESIS Manager.
3. Take the stick out when it's done.

Updating through NEMESIS later keeps your mods.

## adb

```
unzip hmgapi-<version>-adb.zip
```
```
cd hmgapi-<version>-adb
```
```
./hmgapi-adb.sh install
```

Then restart the head unit. The first install needs root from NEMESIS.

## After installing

Open **HMG-API** from the app menu (or put it on a ★ key). The bar at the top says
whether HMG-API's hooks are on for your car:

- **Hooks on**: everything works. Add mods ([Installing and removing mods](Installing-and-removing-mods)).
- **Hooks off for this car**: HMG-API still works (mods' files, settings, apps and
  services), but mods that change the car's own apps wait for your car's hook map.
  Make a [Car Report](Car-Report) and send it: that's what's needed to add your car.

A USB stick left in the port can start wired Android Auto and keep wireless from
connecting, so take it out after installing.
