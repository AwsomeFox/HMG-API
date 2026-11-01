# HMG-API as a NEMESIS package

`tools/mkinstallers.sh` builds the package, `hmgapi.zip`: this folder's
`NEMESIS.data` (from `NEMESIS.data.in`) and `scripts/`, plus HMG-API's payload in
`payload/`. NEMESIS's USB handler only reads a `NEMESIS.data` at a stick's root, with
`type=install` and a relative `path=` (`native/usb.c`), so the download
`hmgapi-<version>-nemesis.zip` is laid out like NEMESIS's own (`Packages/*.zip`): that
`NEMESIS.data`, `packages/hmgapi.zip` and `README.txt` (for owners), unzipped onto a
stick. The USB download carries the same package as `HMGAPI/install/hmgapi.zip`. From
a shell: `nemesis install [--update] <hmgapi.zip>`. `install/usb/test.sh` checks both
layouts against those rules.

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
