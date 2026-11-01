# Car Report

A Car Report is how a car, region or software version gets added to HMG-API, and how
anything missing on your car gets fixed. It takes about a minute.

1. HMG-API app → **Car Report** → **Make report**.
2. Scan the QR code with your phone. It opens a GitHub issue with the summary filled
   in.
3. Get the report file and attach it to the issue, then submit.

## Getting the file

**USB stick:** plug one in before making the report. The file is in the folder
`HMG-API reports` on the stick.

**adb** (a computer with adb, the head unit's adb on): the file name is on the screen.

```
adb pull /data/hmgapi/public/reports/
```

That copies every report HMG-API made into a `reports` folder on your computer.

The USB port works for one or the other: while adb over USB is on, the port is in
computer mode and doesn't read sticks.

## What's in it

- What the head unit is: model, region, software version, platform.
- Your car's hook map, checked against your car.
- Your car mapped from its own software: the names of the places HMG-API hooks, found
  on the spot. Names only, no code.
- Which mods are installed and whether each one loads on your car.
- HMG-API's recent warnings and errors.

## What's never in it

Your VIN, serial numbers, phone numbers, IMEI, account details, addresses, or anything
from your own data. Log lines are scrubbed before they go in, and you can open the
`.md` file next to the report to read it first.
