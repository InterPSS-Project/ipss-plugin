# WTGWGOA / WTGWGOAU native import and SMIB coupling

Status: **LOADABLE with partial independent verification**, not VERIFIED.
Approved native renewable coverage advances to **20 of 27 loadable families**;
seven implementations remain missing. The earlier
[kernel foundation](wtgwgoa-foundation-2026-10-18.md) remains historical evidence.

## Production path

The reader accepts only the tested PSS/E 36.7 standard eight-CON WTGWGOA record
or the exact legacy USRMDL WTGWGOAU allocation `109 0 0 8 3 3`. Both map to the
same immutable parameter record and explicit native-36.7 recovery convention.
Incorrect field counts, allocation metadata, wrapper types, non-finite/invalid
parameters, guessed flat aliases, duplicates and absent hosts are rejected.
Deferred attachment supports a record before its converter/electrical/plant
controllers. GNET and whole-stack model removal retain intentional-skip status.
Disabled power ordering is accepted and does not invent an active recovery.
The original 25-model Texas2k input inventory is not expanded by this addition.

The optional reference is attached to the electrical controller, not hidden
inside REPCA. REGCA1 and REGCB1 can host it; REEC_A/B/C/D consume its absolute
output. Without REPCA, input is the fixed initialized active power. With REPCA,
input is initial power plus the incremental plant output. Reactive references
remain independent. Type-3 routing sends the same limited input to torque and
pitch before the electrical-controller input selector. No multi-unit plant
controller interface or grid-forming substitute is invented.

Without WTGWGOA, all pre-existing reference paths remain unchanged. REEC_A sends
both modified-Euler stages to the new kernel and uses its explicit two-step
path when selected. REEC_B/C/D retain their existing predictor-only controller
integration contract; this slice does not claim to upgrade those controllers
to a new coupled integration method. Zero-duration two-step refreshes consume
no weak-grid history. Initialization resets the option from the solved power
and voltage. A tripped converter no longer advances its weak-grid states or
timer. Both converter named-state maps expose immutable `WTGWGOA.` entries.

The trigger uses actual local terminal voltage, not a remotely selected REEC
measurement or REGCA's 0.01-pu safe current divisor. Native DYR imports select
the observed 36.7 continuation-on-second-fault convention explicitly. Typed
clients still default to the published restart convention. This difference
and native STATE/VAR coordinates remain documented in the foundation; there
is no new gain, initial-state or curve fitting.

## Focused and coupled evidence

Seven parser tests cover strict schemas, record order, optional plant,
REGCB with its existing typed REECD host, duplicate preservation, disabled ordering, missing targets and
intentional removals. Four additional plugin tests cover all four electrical
reference paths, unchanged absent-option behavior, staged Type-3 torque/pitch
routing and history-neutral repeated two-step refreshes. Existing fourteen
kernel and four native partial-playback/integrity tests remain registered.

The full InterPSS solver runs the public three-bus case at 0.5 and 0.25 ms:

- Flat initialization remains stationary.
- A Bus 2 three-phase fault from 0.05 to 0.10 s (three cycles at 60 Hz) activates
  fault hold, first ramp, timed hold, second ramp and return to bypass.
- The same disturbance without a plant controller uses fixed initial Pref.
- A generator trip during recovery freezes all three STATEs and all three VARs.

These are small-network tests only, not Texas2k simulations. REEC_B/C/D and
REGCB have focused routing/import coverage but not new independently referenced
full-network profiles in this slice. The dynamic Type-3 stack has equation-stage
coupling tests, not a new complete native mechanical-stack comparison.
The existing REGCB importer supports REECD, not arbitrary REEC_A/B/C combinations;
the initial exploratory REGCB/REECB input was correctly rejected and is retained
as a failed diagnostic rather than adding an unverified compatibility alias.

The independent baseline reuses the foundation's exact cold-paired native
traces, without replacing any raw output. Input/checkpoint bundle SHA-256:
`b64d4bf29365f705fa92e9bac53a5b42b7586b0fd20d4b08a9f1a4e52770c235`.
Two additional immutable network bodies contain 245 selected raw checkpoints
each, including both sides of fault application and clearing. Columns preserve
raw row, nominal tick, raw time, post flag, three voltages and device-base P/Q.
Their SHA-256 identities are:

- Coarse: `2221f814cea1cf75a33af10447b43c1641df709a032e55f48a670c38ddb368d5`.
- Fine: `3dd83f179053ea975cc646f4b44cd2dd711e454b63a3c47106326f643d233500`.

Both derive from raw native CSVs whose full bytes match their independent cold
partners and the foundation's published raw identities. No event row is
deleted. The regular InterPSS endpoint exporter supplies the pre-side boundary
at the native immediate-post tick; those two network-post samples remain
unaccepted and their full errors are printed separately.

Coarse/fine complete-input diagnostic maxima include about 3.38e-4 / 1.74e-4 pu
in filtered-voltage STATE and 3.15e-4 / 3.21e-4 pu in live plant Pref. The
callback ramp/reset/timer/handoff gaps remain as disclosed in the foundation.
Regular sampled network errors are much smaller than immediate-post errors.
At the 245 selected network checkpoints, coarse/fine maximum differences are:

- Bus 1 voltage: 2.5141e-5 / 2.0202e-5 pu.
- Bus 2 voltage: 1.4593e-5 / 1.1696e-5 pu.
- Bus 3 voltage: 1.5237e-6 / 1.0841e-6 pu.
- Active power: 1.3740e-3 / 6.9708e-4 pu on device base.
- Reactive power: 1.5739e-6 / 1.3522e-6 pu on device base.

The retained instantaneous-post maxima are about 0.057 pu voltage and
0.01354/0.00268 pu P/Q; these are not included in the accepted sampled envelope.
Raw maximum-error times and every diagnostic channel remain in the test log.
Separate
channel bounds apply to V1/V2/V3/P/Q and to the continuous filter/plant signals.
No general convergence claim is made for voltage or plant residuals.

## Remaining acceptance

- Resolve native callback, reset, timer and bypass-handoff pointwise timing.
- Expose and compare the exact instantaneous network-post boundary.
- Independently verify alternate REEC/REGCB hosts, Type-3 mechanical-stack
  coupling, base changes and reconnect/restart profiles.
- Complete staged integration verification for legacy electrical controllers
  and native storage precision beyond the currently bounded profiles.
- Finish the rejected PowerWorld probe's field/table discovery, parameter
  round-trip and cold regeneration, then compare the published restart dialect.

The PowerWorld diagnostic from the foundation remains rejected, not a new
benchmark success. Native Model Library, DSMDL and core JAR source identities
are unchanged from the foundation.

## Final scoped regression and evidence

The tested snapshot is committed plugin `29f5cacd0` plus this slice. Unrelated
untracked WECC240/JAX and in-progress reference-runner reporting changes are
excluded. All production and scoped test files match the tested snapshot;
baseline source comparison accounts only for CRLF/LF line-ending differences.

- Plugin-core units: **199 tests, zero failures/errors, six optional skips**.
- Registered SMIB/model, catalog and staggered-solver gate: **1,248 tests,
  zero failures/errors, one existing diagnostic skip**.

Both commands exit zero; totals come from fresh Surefire XML, not console
silence or previous reports:

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtgwgoa-native-import-units-20261018-e --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtgwgoa-native-import-smib-20261018-f -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

The ignored private archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtgwgoa-native-import`
retains the exact scoped sources, generator, failed diagnostics, eight InterPSS
exports, final XML/logs and source identities. Its foundation link hashes the
previous archive inventory and checks every retained foundation file, including
all 22 native runs. No new native run or accepted PowerWorld run is claimed.
No existing archive is overwritten. No full Texas2k dynamic simulation is run.
