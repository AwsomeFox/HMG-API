# Mapping the car's data: MICOM and the community CAN work

The head unit never sees the CAN buses directly. Its MICOM (a microcontroller on
the bus) passes selected signals to Android as MICOM frames: a category (CID), a
function code and a data block. The car's framework names them after Hyundai's own
CAN messages and signals (`EV_GW_EV_P_02`, `VCU_BATT_COND_IND_STA`,
`DATC_OUT_TEMP_SNSR` …), and its services turn them into the getters HMG-API's
readings use.

The community has mapped the same data from the other side, on the bus:

- [Ioniq5_CAN](https://github.com/tylerharvey/Ioniq5_CAN) (MIT, Tyler Harvey and Roy
  Dragseth): preconditioning messages, logs, parsing tools
- [egmpdbc](https://github.com/dragz/egmpdbc) (Roy Dragseth): E-GMP DBC
- [ioniq5-can](https://github.com/Sterlingarcher2525/ioniq5-can): DBC with drive,
  brake, steering, TPMS, climate and OBD PIDs
- [wicant-i-precondition](https://github.com/L1Z3/wicant-i-precondition) (GPL-3.0,
  Liz): the preconditioning firmware
- [explorationsincarhacking](https://github.com/dragz/explorationsincarhacking)
  (GPL-3.0), [canair](https://github.com/philipkocanda/canair) and
  [ioniq-can](https://github.com/philipkocanda/ioniq-can) (MIT),
  [vess](https://github.com/ereuter/vess)

HMG-API uses none of their code; this page links their findings to the head unit's
side. Nothing here sends anything on the bus: HMG-API only listens to the MICOM, and
commands go through the car's own services (`vehicle-commands.md`).

## What is already linked

| Community (bus) | Head unit | HMG-API |
|---|---|---|
| `0x4ED` Navigation_Precond_Status (head unit to car: distance to charger, precondition flag) | HEV `setBattCondRouteDis`, `setBattCondRemainTime`, `setBattCondEvStation`: the navigation's own path to that frame | command `conditioning` (same values and order as Launchpad) |
| `0x0C7` Toggle_Battery_Preconditioning (injected on M-CAN by the kit) | not sent by HMG-API (own CAN frame) | `conditioning` reaches the same result through the head unit's own frame |
| `0x2AD` BMS_Precond_Status | HEV `EV_BATTERYCONDITIONING`; `VCU_BATT_COND_IND_STA` in the navigation battery packet | reading `hv.conditioning` |
| `0x496` / `0x475` Driver / Passenger_Seat_Heating | USM `getFrontSeatHeat_DrvState` / `AstState` | readings `seat_heat.driver`, `seat_heat.passenger`; command `seat_heat` |
| `0x418` Steering_Wheel_Heating | VR `set_StrgWhlHeated_Cmd_AVN` (command side) | command `wheel_heat`; state: USM `SWRC_SET` resp (`0x9127`) byte 3 / BODY `0xc40d` byte 0, 1 on, 0 off, sent only on a change (confirmed on a US EV6, 2026-10-31) |
| `0x448` Steering_Wheel_Buttons | key events (`KEYCODE_SHORTCUT_SWRC_CUSTOM`, `MUTE`, `VOLUME_*` …) | `"input"` bindings |
| `0x036` GEAR_STATUS | system service `getGearPStatus` | reading `gear.park` |
| `0x1F1` PARK_BRAKE | system service `getParkingBrakeStatus` | reading `parking_brake` |
| `0x435` VEH_SPEED | telematics CAN service `getCarSpeed` | reading `speed` |
| `0x0EA` STEERING | `getSasAngleVal` | reading `steering.angle` |
| `0x065` Brake_Pedal, `0x2B5` Throttle_Pos | `getCylPrsrVal`, `getAccelPdlVal` | readings `brake.pressure`, `pedal.accel` |
| `0x3A0` TPMS_BROADCAST (psi per tyre) | only status bytes as getters (`get*TirePressureStatus`) | to find (record it) |
| `0x227` DRIVE_MODE | BCM drive mode listener (EGMP uses it) | to find a getter or frame |
| `0x465` CLIMATE_CTRL, outside temperature | DATC services; `DATC_OUT_TEMP_SNSR` in the navigation battery packet | to find (record it) |

## Recording on the head unit

Owner and developers only (root or the HMG-API app):

```
hmgapid micom-watch all          # or categories in hex: micom-watch 88 c2
hmgapid micom-watch              # what it watches
hmgapid micom-log > micom.log    # the last 64 KB (the file: /data/hmgapi/logs/hmgapi/micom.log)
hmgapid micom-watch off
```

HMG-API's vehicle service registers with the car's own raw data service
(`MICOMDATA_SERVICE`) and writes `<ms since boot> <cid> <function> <data hex>` per
frame. It never watches the telematics category (`c5`: modem, account, phone
numbers), and in every frame a run of 8 or more printable characters (a VIN, a
serial, a name) is written as `__`, so a recording can be shared. Off the car:

```
tools/micom-catalogue.py <deodexed automotive.jar> --json ev6.json   # the car's names (keep it private)
tools/micom-decode.py micom.log ev6.json --changes                   # which bytes moved, when
```

Record one thing at a time (seat heat on, a door, the charge cable, conditioning)
with the time noted, as the Ioniq5_CAN notes advise for bus logs: a test and a
control. A byte that follows the action is the signal; the community DBC gives its
bus id and scale. A signal found this way becomes a reading once it's in the hook
map for every car.
