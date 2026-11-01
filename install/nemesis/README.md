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
- Installed with Android down (a stick with `NEMESIS.emergency`; NEMESIS otherwise
  waits for Android), the HMG-API app goes in by itself once Android is up.

**Descriptor (1.1, 04-Packages.md §2; 07-Submit-a-Package.md for the Package Store).**
`version=1.1` is the descriptor format, not HMG-API's version. `package-version` is
HMG-API's version as dotted numbers only (NEMESIS refuses anything else); a beta
comes before its release, so it's the version before it plus `.99.<beta>`
(`1.0.0-beta.4` is `0.99.4`, below `1.0.0`; `tools/mkinstallers.sh`, which stops on a
version form it has no rule for). `os` names what's tested (`ro.build.product` globs:
the US EV6 260507), and `models="any"`, so other cars show as partial, not
incompatible. `short-desc` at most 64 characters, `detailed-desc` at most 256.
`install/usb/test.sh` checks all of it.

**For the Package Store** (portal.xwtk.cloud/nemesis): upload
`hmgapi-<version>-nemesis-package.zip`, the package itself (the same bytes as
`packages/hmgapi.zip` in the USB stick download). An update needs a higher
`package-version`.

The package declares no `[app.N]`: the app is HMG-API's to install and remove, so a
NEMESIS update doesn't uninstall it.
