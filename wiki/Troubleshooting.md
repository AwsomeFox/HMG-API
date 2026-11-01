# Troubleshooting

## The top bar says "Hooks off for this car"

HMG-API still works; only the parts of mods that change the car's own apps wait.
- **No hook map for this car or software:** make a [Car Report](Car-Report), or try
  Settings → Hook map → "Map this car from its files".
- **The map isn't checked on this car yet:** Settings → Hook map → "Check the map on
  this car", then restart.

## A mod is red

It doesn't run on your car; the Mods tab says why. Usually a hook point your car's
map doesn't have yet: make a [Car Report](Car-Report).

## A mod's car command does nothing

The first time a mod uses a car command, HMG-API asks you on screen. If you said
"Don't allow", change it in Settings → Vehicle → "Forget my answers".

## The head unit restarted over and over

After three failed starts in a row, HMG-API turns its hooks off by itself so the
head unit starts normally, and the app says the hooks aren't running. Turn off or
remove the mod you added last, then reset the counter and restart:

```
adb shell hmgapid sh 'echo 0 > /data/hmgapi/hook/starts'
```

## A mod isn't doing anything

- Is it switched on, and did you **Apply changes**?
- Look at the plan for a `problem` line about it.
- Its log: the Mods tab, or the Logs tab.

## Android Auto won't connect wirelessly with a USB stick in

A stick in the port can make the head unit start wired Android Auto and refuse
wireless until it times out. Take the stick out; if it stays stuck, restart the head
unit.

## Sending a report

Make a [Car Report](Car-Report) and open an issue ([Getting help](Getting-help)).
