## What this changes

## Tested on

- [ ] `tools/test-all.sh` passes
- [ ] Cars (model, region, software version), or "off-car only":

## Checklist

- [ ] New or changed hook points: `tools/test-hooks.py` shows no regressions on the cars we have files for
- [ ] Anything written to `/system` keeps a stock check, a backup and an exact restore
- [ ] No manufacturer apps or libraries, no per-car data (VIN, serials, keys, `/data` contents)
