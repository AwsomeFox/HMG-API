# HMG-API as a NEMESIS package

`tools/mkinstallers.sh` builds the package, `hmgapi.zip`: this folder's
`NEMESIS.data` (from `NEMESIS.data.in`) and `scripts/`, plus HMG-API's payload in
`payload/`. NEMESIS's USB handler only reads a `NEMESIS.data` at a stick's root, with
a `type=` and a relative `path=` (`native/usb.c`). The download
`hmgapi-<version>-nemesis.zip` is a NEMESIS 1.16 Package Store list (`type=list`,
04-Packages.md §5.3, `StoreList.parseList`: `[package.0]`, `[package.1]`, … with no
gaps): `packages/hmgapi.zip` first, then each released mod's NEMESIS package as
`packages/hmgapi.<id>.zip`, and `README.txt` (for owners), unzipped onto a stick. The
USB download carries the same package as `HMGAPI/install/hmgapi.zip`, with
`type=install` (it also has to work without NEMESIS 1.16). From
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

## Mods as NEMESIS packages

`tools/mknemesismod.sh <signed mod zip>` wraps a mod (tools/mkmodpkg.sh's zip, as it
is, in `mod/package.zip`) in a NEMESIS package from the template in `mod/`:
`<id>-<v>-nemesis.zip` (stick) and `<id>-<v>-nemesis-package.zip` (the package). The
release builds one for each mod in `tools/release-mods.txt`. `mod-test.sh` runs it all
off-car with the real engine.

- id `hmgapi.<mod id>` (mod ids are at most 57 characters for this),
  `depends="hmgapi@>=1.0"` plus the mod's incompatibilities (below; NEMESIS then also
  refuses to uninstall HMG-API while a mod package is installed),
  `os` from the mod's `"system"` (a firmware range like `>=260206` becomes `*`),
  `package-version` from the mod's version (`tools/nemesis-version.py`).
- `preinstall`: refuses before NEMESIS copies anything if HMG-API isn't installed
  (`/system/bin/hmgapid` and `/data/hmgapi/uninstall.sh`).
- `postinstall`: `hmgapid engine install mod/package.zip` (plus `--any-car` with
  `HMGAPI/ANY-CAR` on a stick), then `engine apply`. If the apply fails on a first
  install, `engine remove` again; an update stays in HMG-API (the app shows the
  problem). Either failure exits 1; NEMESIS keeps the package registered, so it's
  uninstalled as usual.
- `preuninstall`: skipped on a NEMESIS update (the same stage rule as HMG-API's own).
  Otherwise `engine remove`, then `engine apply`. The mod already gone from HMG-API:
  nothing to do. HMG-API gone (its uninstall already restored `/system`): drop
  `/data/hmgapi/mods/<id>` so a reinstall doesn't bring it back. A failed remove keeps
  the package (exit 1); a failed apply after it only warns (the mod is out of HMG-API
  either way; Apply changes in the app finishes it).
- Launch (`[app.0]`, `bin/open.sh`): opens the HMG-API app, as HMG-API's package does.

**Descriptor (1.1, 04-Packages.md §2; 07-Submit-a-Package.md for the Package Store).**
`version=1.1` is the descriptor format, not HMG-API's version. `package-version` is
HMG-API's version as dotted numbers only (NEMESIS refuses anything else); a beta
comes before its release, so it's the version before it plus `.99.<beta>`
(`1.0.0-beta.4` is `0.99.4`, below `1.0.0`; `tools/mkinstallers.sh`, which stops on a
version form it has no rule for). `os` names every car with a hook map
(`ro.build.product` globs from each `maps/*/*/*/*/car.json`, `tools/nemesis-meta.py
os`), all installed and checked by the maintainer, and `models="any"`, so other cars
show as partial, not incompatible.

**Incompatibilities** (`conflicts.txt`): NEMESIS packages that change files or
settings HMG-API or a mod changes too go in `depends` as `!<id>`
(`tools/nemesis-meta.py depends`). NEMESIS refuses to install either one while the
other is installed (exit 23), so the owner uninstalls one first. HMG-API's own: HMG
Leafia Plus (`wireless.carplay.aa`); the wireless mod's: `wireless.projection`,
`projection.tbt`; the adb mod's: `adb.tcp`. Mods installed from the HMG-API app
instead of NEMESIS aren't covered (the engine's own stock checks are). `short-desc` at most 64 characters, `detailed-desc` at most 256.
`install/usb/test.sh` checks all of it.

**For the Package Store** (portal.xwtk.cloud/nemesis): upload
`hmgapi-<version>-nemesis-package.zip`, the package itself (the same bytes as
`packages/hmgapi.zip` in the USB stick download). An update needs a higher
`package-version`.

The package's one `[app.N]` is `kind=sh` (`bin/open.sh`), with no `package` or
`component`: Manager's **Launch** opens the HMG-API app (installing it first from
`/data/hmgapi/HmgApi.apk` if it's missing; `install/common/app-open.sh`, installed as
`/data/hmgapi/open.sh`), and NEMESIS never runs `pm uninstall` on the app (it would on
an update, for an entry naming the app's package). The app stays HMG-API's to install
and remove. Launch needs no hooks, so it's also the way into the app on a car without
a hook map, where the launcher tile and the star key choice aren't added.
