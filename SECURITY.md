# Security

Report security problems privately to the maintainer (VysseCodes) rather than in a public issue.

Notes on what HMG-API changes on a head unit:

- hmgapid runs as root. It only offers a fixed set of operations, chosen by the caller's uid: root and shell (adb) get everything, the HMG-API app gets HMG-API's own operations, everyone else only `ping`/`version`.
- On these head units adb does not check keys: anyone with a USB cable has a shell, and so can ask hmgapid for root.
- HMG-API's files under `/data/hmgapi` are root-only.
