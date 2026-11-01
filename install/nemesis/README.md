# HMG-API as a NEMESIS package

`tools/mkinstallers.sh` builds `hmgapi-<version>-nemesis.zip`: this folder's
`NEMESIS.data` and `scripts/`, plus HMG-API's payload in `payload/`. Install it from
NEMESIS Manager, `nemesis install [--update] <zip>`, or a USB stick with a
`NEMESIS.data` of `type=install` (the HMG-API USB stick has one).

- install / update: `postinstall` runs `payload/install.sh`, which works out
  whether it's a first install or an update.
- uninstall: `preuninstall` runs `/data/hmgapi/uninstall.sh` (every mod change and
  HMG-API's own files back to stock; `/data/hmgapi` is kept).
- NEMESIS updates a package by uninstalling the old one first. `preuninstall`
  sees the new package staged and does nothing, so an update keeps HMG-API's mods.
- With Android down (NEMESIS installs without asking then), the HMG-API app goes
  in by itself once Android is up.

The package declares no `[app.N]`: the app is HMG-API's to install and remove, so a
NEMESIS update doesn't uninstall it.
