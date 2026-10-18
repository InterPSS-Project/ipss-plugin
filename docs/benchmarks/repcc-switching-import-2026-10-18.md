# REPCC enabled-switching import and device admittance

## Implementation

The standard REPCC import path now supports enabled switching for the existing
REGCA1/REECA1 host. The fixed-shunt builder retains each original system-base
G+jB on that device, including initially open devices. Existing load-flow
representation is unchanged: B belongs to the named compensator and active G
is added to the bus shunt only once.

`FixedShuntAdmittance` uses device-owned transient EMF adapter metadata. It
does not create a static registry, retain an entire network globally, or
overwrite an unrelated extension. The imported object owns the metadata for
its lifetime. It is deliberately not a new serialized core feature; copied
or deserialized devices without this metadata require reimport or explicit
typed nominal data. The importer does not infer G from aggregate bus shunts.

Before occupying the plant port, the dynamic builder resolves each enabled
configured device, rejects duplicate/missing identities, and requires retained
single-step nominal admittance with unchanged B. The physical boundary still
validates the resolved configuration during initialization. Missing switching
measurement branches or invalid switching-threshold ordering retain the
documented disabled-switching path rather than fabricate a measurement.

The 35-ICON/57-CON source order, continuous equations, limiter gains, shunt
priorities, discharge lockout and existing trajectory bounds are unchanged.
The authority and immutable reference provenance remain those recorded in the
[runtime](repcc-native-runtime-2026-10-18.md) and
[initialization](repcc-enabled-initialization-2026-10-18.md) reports.

## Regression evidence

The new strict import test fails before the change with REPCC rejected.
It constructs a RAW fixed-shunt section from the frozen ten-device profile,
assigning distinct nonzero conductances to discriminate individual devices
from aggregate bus G. The full import and coupled SMIB run activate both
opening initially closed devices and closing initially open devices at
0.5 ms and 0.25 ms. The test checks exact cumulative conductance and complex
Y changes, including a subsequent matrix rebuild.

Separate tests reject missing retained input, changed B, duplicate identities
and missing devices without occupying the controller port. Metadata unit tests
cover independent equal-ID devices, status changes, unrelated extensions,
copy behavior, missing/ambiguous data, multiple steps and nonfinite input.
Existing standard-schema, physical boundary, thirteen-state initialization,
flat/fault trajectory and RAW-version regressions remain in the focused gate.

These nonzero-G coupled tests are local regression discriminators, not new
independent simulator trajectory acceptance. Existing raw independent
references are reused unchanged; no native trace or physical parameter was
adjusted to make comparisons pass.

## Remaining gaps

- Exact startup/zero-time sampling and independently compared coupled shunt
  switching trajectories at both timesteps.
- The documented native discharge-lockout discrepancy and activating
  PowerWorld comparison.
- Complete initialization, selector/limit, lifecycle and host/alias coverage.
- A persistent core nominal-G serialization feature if network serialization
  must preserve automatic shunt switching without reimport.
- HPLNTD and the remaining model-plan acceptance requirements.

Runtime-family presence remains 26/27, not complete verification. No full
Texas2k dynamics are run.

## Final gates

On plugin baseline `41922fc74`, the focused gate passes 77 tests, all
plugin-core units pass 347 tests (six optional skips), and the selected
SMIB/model/solver gate passes 1,412 tests (one existing diagnostic skip).
There are zero failures/errors. A separate six-test before-fix gate retains
the expected strict-import rejection.

The tests use the isolated frozen companion core `38e269ecf`, SHA-256
`fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.
The shared dependency installation and earlier reference archives are not
replaced. The new private evidence archive is
`2026-10-18-repcc-switching-import`; it retains tested sources, reports,
logs and a hash inventory, with existing independent evidence rechecked.
Generated evidence documentation is compared to the compiled inventory.
