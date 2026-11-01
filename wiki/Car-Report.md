# Car Report

A Car Report is how a car, region or software version gets added to HMG-API, and how
anything missing on your car gets fixed. It takes about a minute.

1. Plug a USB stick into the car (to get the file).
2. HMG-API app → **Car Report** → **Make report**.
3. Scan the QR code with your phone. It opens a GitHub issue with the summary filled
   in.
4. Attach the report file from the stick (folder `HMG-API reports`) and submit.

No stick? The report is also saved on the head unit:
`adb pull /data/hmgapi/public/reports/`.

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
