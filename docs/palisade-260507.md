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
The previous off-car mapper run matched all 285 overlapping entries in the supplied
map. The reference lacks six unrelated app files, so this is a partial stock check.
The map is marked `mapped`: the historical 343-point report is not a vehicle
verification of the newly added entries.

`framework.navigation_cluster.update_hu_clu_pe03` and
`framework.navigation_cluster.update_hu_clu_pe12` expose the existing stock
`byte[]` update methods for observation. Their payload semantics remain unassigned.
They do not send new messages or add a CAN writer. The EV6 USA 260507 and Canada
260206 maps, and the Ioniq 5 Canada 260206 map, include these methods based on
[VysseCodes' stock-file confirmation in the review](https://github.com/Open-HMG/HMG-API/pull/199#pullrequestreview-5914436383).
Those stock trees are not available locally; this is maintainer evidence, not a
new local stock-map or vehicle verification. The existing EV6 verification record
is unchanged. Other firmware maps explicitly list these optional points as
unresolved; they need stock mapping or newer reports.

The observation points are read by the separate local `palisade-stock-nav-recorder`
investigation to compare stock navigation messages with captured cluster/HUD
behavior. Their payload semantics remain unassigned. The recorder is not part of
this upstream PR.

[Wireless baseline PR #49](https://github.com/Open-HMG/hmgapi-wireless-cp-aa/pull/49)
does not depend on either observation point. The local wireless port was tested on
HMG-API 1.0.2. Current upstream integration is checked off-car only, pending review
and the stated vehicle and cross-model validation.

The map is stored in the current base-plus-differences format. Its expanded
353 entries are unchanged by that conversion. Confirmed PE03/PE12 entries for
the EV6 and Canadian Ioniq 5 are in `car.json` under `names`; no per-app files
are added to those compact maps. Other cars retain their upstream map entries,
file hashes and verification metadata, with only the unresolved optional points
added. The map rebuilds proposed in #266 remain a pending integration step.
