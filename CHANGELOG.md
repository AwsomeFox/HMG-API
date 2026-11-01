# Changelog

## 1.0.0 (unreleased)

HMG-API replaces HMG Leafia Plus 1.x. Leafia was one NEMESIS package for wireless
CarPlay and Android Auto; HMG-API is a mod manager. Any number of mods can be
installed together, none of them overwrites another, and removing one puts back
exactly what it changed.

**Tested on:** a US Kia EV6 260507 (2026-10-31, `docs/car-test.md`): the hook runtime,
mods, the app, buttons, the side panel, Android Auto turn-by-turn and projection events,
the MICOM recording, the Car Report. Not yet: CarPlay on HMG-API, installs without adb,
other cars.
HMG-API runs on any Gen5W navigation head unit. A car's hook map (US Kia EV6 260507
today) turns on the parts that change the car's own apps; without one, or for points a
map lacks, only those parts stay off and the app says why.

### For owners

- **Install** from a USB stick (with or without NEMESIS), as a NEMESIS package, or
  over adb. Uninstalling puts every file and setting back to the stock bytes.
- **The HMG-API app** (its own menu tile and ★ choice): a status bar (hooks on, changes
  not applied, restart needed) with *Apply changes* (a plan of what will change before
  anything does), *Check USB updates* and *Restart unit*; tabs for **Mods** (each mod's
  state in colour, switch, settings, log), **Settings** (vehicle, updates, phone
  projection, hook map, about), **Car Report** and **Logs** (HMG-API's own log, kept
  since the start).
- **Car Report:** checks how HMG-API fits the car and maps it from its own software on
  the spot; saved to a USB stick, with a QR code that opens a filled-in GitHub issue.
  Model-level facts only, logs scrubbed.
- **Asked first:** the first time a mod uses a car command, HMG-API asks you on screen
  (once per mod and command; giving back never asks).
- **Mods are signed by their author.** Updates must come from the same author key
  as the installed mod, so nobody can install over someone else's mod. A mod from
  an author not on HMG-API's trusted author list installs with a warning.
- **Nothing is hidden.** The plan lists, per mod, the apps it installs, the services
  it runs, the car readings and commands it uses, the buttons it binds, its side
  panel pages and its Android Auto settings. When two mods want the same thing (a
  button press, Android Auto's density), the plan says which one has it, and you can
  pick the other.
- **Wireless CarPlay and Android Auto** are now a mod, with the same features as
  Leafia 1.x (turn-by-turn in the cluster and HUD, the CarPlay Bluetooth bridge,
  extra audio formats), and no app or library file replaced: the changes are made in
  memory as the apps load.
- **OTA updates** stay blocked by default while HMG-API is installed (USB and dealer
  updates still work), so an update can't silently remove it.
- **Startup notice:** a switch in the app (off by default) lets a mod dismiss the
  notice shown at start. It says what it skips.

### For mod authors

A mod is a folder with a `mod.json`; everything below is declared there, shown to the
owner in the plan, and undone when the mod is turned off or removed. Reference:
`docs/mod-reference.md`. An existing package can be offered as a mod as well, side by
side with it: `tools/mod-draft.py` drafts its `mod.json`.

- **Hooks:** Java methods in any app, by stable hook point names mapped per car
  (`core/hookpoints`, `maps/`), never by an app's obfuscated names. Native library
  patches in memory (`hooks.native`).
- **Your own APK** (`apps`): installed, turned off and removed with the mod.
- **Services:** scripts or `app_process` jars, run as root, system or your app's own
  user; restarted with backoff, logged, stopped when the mod is turned off.
- **The client library** (`hmgapi-client.jar`): your app and services ask HMG-API
  for their settings, services, logs, USB folders, readings and commands, without
  root.
- **Vehicle readings** (18, by name: gear, speed, steering angle, pedals, 12V and HV
  battery, conditioning, charging, odometer, seat heat, guide lines), read through
  the car's own services and shared between mods.
- **Vehicle commands**, each reviewed and guarded: charge door (park only), seat
  heat, wheel heat, battery conditioning, ambient light (one mod at a time), parking
  guide lines, rear camera turns, startup notice (owner's switch).
- **Buttons** (`input`): 40 head unit, steering wheel and overhead buttons. A held
  button is a mod's shortcut (the car's hold gives way; a short press still reaches
  the car); a short press can be watched or taken; knobs report every click. Star
  keys: the short press stays the owner's pick in the car's menu, the hold a mod's.
- **Side panel pages:** a standard Android app widget from your app, in the
  right-hand split screen.
- **Phone projection:** Android Auto density, sensors (no tap limit, EV sensor
  types), fuel and connector types; events for Android Auto and CarPlay sessions,
  guidance and navigation messages.
- **Not while driving:** your app follows the car's own driving restriction and
  popup.
- **USB folders, tiles with day and night icons, the car's notices.**
- **Shared files and settings:** feature flags, the Wi-Fi client, `media_codecs.xml`
  by codec name, list settings (enabled keyboards), and an install-time probe for
  decisions made on the unit.
- **MICOM watch** (owners and developers): records the frames the head unit gets
  from the car's MICOM, for mapping new readings with the community CAN projects
  (`docs/micom-map.md`). Listening only; identity never reaches the log.

### Compared with Leafia 1.x

- One mod per feature, installable together, instead of one package for all.
- No replaced app or library files: hooks and in-memory patches.
- Settings change without reinstalling; most changes apply without a restart.
- `stepofspec` is never changed.
