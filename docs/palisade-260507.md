# Palisade USA 260507 map and navigation observations

This contribution targets a 2023 Hyundai Palisade Limited, Gen5W software
`LX2_PE.USA.S5W_M.V015.012.260507`, product
`LX2PE.USA.0000.V015.012.260507`.

The November 5 owner Car Report verified the original 343 map entries and all
18 file hashes, with hooks enabled and no mismatches. Its original on-car map
folder was `unknown/lx2pe/usa/260507`; the Hyundai model identity is confirmed.
Only names, signatures and model-level checksums are included here.

The current map has 353 points. Eight newer upstream points and the two optional
PE observation points were resolved off-car against the available stock reference.
All 285 overlapping entries resolved by the current mapper match the supplied
map. The reference lacks six unrelated app files, so this is a partial stock check.
The map is marked `mapped`: the historical 343-point report is not a vehicle
verification of the newly added entries.

`framework.navigation_cluster.update_hu_clu_pe03` and
`framework.navigation_cluster.update_hu_clu_pe12` expose the existing stock
`byte[]` update methods for observation. Their payload semantics remain unassigned.
They do not send new messages or add a CAN writer. Other firmware maps explicitly
list these optional points as unresolved; they need stock mapping or newer reports.

Wireless CarPlay and navigation changes belong in the separate
[wireless repository](https://github.com/Open-HMG/hmgapi-wireless-cp-aa).
The local port was tested on HMG-API 1.0.2. Current upstream integration is checked
off-car only, and both contributions remain drafts pending review and the stated
vehicle and cross-model validation.
