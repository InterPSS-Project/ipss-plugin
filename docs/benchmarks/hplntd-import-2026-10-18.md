# Exact bus-owned hybrid import

## Implemented path

The main dynamic reader now imports the exact native wrappers:

```
IBUS 'USRBUS' 'HPLNTDU' 504 0 35 61 13 45 ICON(35) CON(61) /
IBUS 'USRMDL' ID 'REAXDU' 107 0 9 8 2 7 ICON(9) CON(8) /
```

Catalog parameter counts 102 and 23 include the six wrapper allocation fields.
They do not mean 102 or 23 floating-point model constants. Exact decoder typing,
quoted character fields and cardinality remain mandatory. No standard-name or
cross-tool alias is inferred from the source-list spelling `HPLNTD`.

`HplntdImporter` compiles a two-pass immutable link plan after ordinary device
and control attachment. `DStabNetworkBuilder.addHplntd` resolves every host
before the shared coordinator preflights and attaches any command port. Stable
import phases load REGCA1 first and REECA1 second, followed by other records in
their original relative order. This preserves existing wind-host dependencies
and supports completely reversed source ordering for the tested hybrid stack. Existing controls are not replaced
to force a hybrid attachment.

The source contract is unchanged from the
[wrapper and measurement evidence](hplntd-binding-boundary-2026-10-18.md) and
[typed runtime evidence](hplntd-typed-runtime-2026-10-18.md). The catalog links
the WECC approval source, not an unverified PowerWorld model page. The exact
equation/schema authority remains the identified Model Library contract and
independent frozen native data, not the approval list alone.

## Import accounting and removal

The plant belongs to its bus, not a fictitious generator with ID `*`.
Generator preprocessing applies to each downstream unit independently. A plant
with one remaining supported unit is retained; a plant whose every linked unit
was intentionally removed is reported as inapplicable using the corresponding
removal status. Mixed generator/stack removals remain explicitly accounted for.
Removed source records never become runtime data or command ports.

Malformed schemas or duplicate identities reject the hybrid batch before any
hybrid attachment. A missing downstream target rejects its whole group.
Unsupported links retain explicit diagnostics rather than silently becoming
commands. A conflict on a later unit cannot leave an earlier unit partially
attached. Existing strict-import failure semantics apply to all unsatisfied
records; preprocessing skips are deliberately distinguished from missing models.

## Observation and tests

Each converter now exposes thirteen shared plant states under `HPLNTDU.` and
its own two downstream states under `REAXDU.`. Observation before initialization
is safe and never advances the shared group. Unrelated named-state keys remain
unchanged; another unit's lag states are not duplicated into each device map.

Ten importer tests cover full-file strict loading, reversed record/bus order,
two timestep sizes, named states, one/all/mixed removals, malformed/duplicate
batches, strict missing-target rejection, unsupported links and atomic ownership
failure, including a reused or wrongly owned electrical-controller object.
The ownership case fails before the fix; host ownership is now checked before
any command-port writes, rather than waiting for a later port setter to fail.
Imported command responses and three-cycle fault trajectories match
the preceding typed composition at 1e-10 in the compared states/voltages.

The existing two native command comparisons now use **main-imported** models,
retaining every raw row and all twenty-five state/network/output channels with
the preceding channel-specific bounds. No reference data, numerical tolerances,
controller gains, default integration methods or fault impedances are changed.
The two fast reference-slew states retain their documented cross-method residual;
independent coupled fault acceptance is still open.

## Coverage and remaining work

Final gates: **113 focused tests, 379 core-unit tests and 1,490 selected
SMIB/model/solver tests**, zero failures or errors. Six existing optional core
skips and one existing diagnostic skip remain. The final source snapshot also
passes the wording guard and all six guard tests. The first broad run exposed
a wind-host dependency regression in the initial electrical-control deferral;
the corrected stable phases pass all affected wind regressions and the repeated
complete selected gate. The fail-first electrical-ownership test also passes
after moving validation ahead of attachment. Both failing diagnostics are
retained rather than erased.

Source snapshots, logs, XML results and paired traces are sealed under private
artifact `2026-10-18-hplntd-main-import-v2`. The first sealed snapshot remains
immutable diagnostic evidence; the final seal also audits its full inventory.
The seal audits all other baseline Java
and build files, generated-document consistency, the unchanged frozen companion
jar, prior archive integrity and both complete main-imported command traces.

All 27 approved planned renewable families now have bounded catalog/import/runtime
paths. This is **not complete model verification**, nor support for every exact
source spelling, wrapper, selector, initialization or host. HPLNTDU remains a
partial-profile implementation with the same active-PV REGCA1/REECA1, default-link,
enabled-positive-integral-loop and disabled-switching boundary described in the
typed runtime report. Unsupported modes reject explicitly.

Remaining work includes independent coupled fault comparisons, disabled/zero-gain
loops, alternate hosts and valid relative mappings, initially inactive/all-off
restart, automatic shunt switching, and the broader model-by-model verification
gaps retained in the coverage plan. No full Texas2k dynamics are run in this slice.
