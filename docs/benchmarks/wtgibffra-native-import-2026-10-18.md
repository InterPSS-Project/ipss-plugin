# WTGIBFFRA native import and bounded coupled SMIB verification

Status: **LOADABLE, with partial independently referenced evidence**, not complete
native or PowerWorld conformance. Approved native renewable coverage increases
from **20/27 to 21/27**. The six missing families are HPLNTD, REECE1, REGFMB1,
REGFMC1, REPCCU and REPCGFMC1. No full Texas2k dynamic simulations were executed.

## Production changes

- Catalog/parser: exact standard `IBUS 'WTGIBFFRA' ID Fbus CON(1)..CON(45) /`;
  one integer ICON and 45 grouped CONs, 46 input fields total. Runtime six-ICON
  allocation does not add five DYR fields. Unproven legacy wrappers and aliases
  remain rejected. Deferred attachment works before/after its host record.
- Attachment: REGCA1 or REGCB1 with REECD only. Other REEC models do not silently
  receive an invented port. Duplicate and missing/wrong-host records cannot
  claim successful attachment. GNET/model removal preserves strict accounting.
- Frequency: use the requested available bus; zero, missing or inactive remote
  buses fall back to the generator bus. Absolute PU frequency is sampled by
  the kernel at both modified-Euler stages. Converter power is on machine MVA
  base before band selection; a 50 MVA unit test covers the conversion.
- Coupling: external REECD Paux and the inertia increment remain independent.
  Their sum enters `(Pord + externalPaux + inertiaPaux)/Vmeas`, before current
  limits. Neither changes Pref/Pord. WTGWGOA's absolute Pref limiter can coexist.
- Staging: the inertia kernel receives predictor/corrector endpoints. The
  corrected auxiliary input refreshes final current limits without advancing
  REECD states, timers or plant controls a second time. The established REECD
  realization is unchanged when no inertia controller is attached.
- Monitoring: immutable semantic kernel states are exposed through converters
  with the `WTGIBFFRA.` prefix. Existing named-state keys remain unchanged.
- Lifecycle: trip freezes the attached states/timers; explicit reinitialization
  clears the response. This is not online-reconnect acceptance.

The published minimum recovery equation remains the default. The earlier native
36.7 automatic/too-short recovery discrepancy is not adopted or fitted away.
See the [foundation report](wtgibffra-foundation-2026-10-18.md) for authoritative
source identities, six-band equations and the seven native kernel profiles.

## GENCLS governor prerequisite found by the comparison

The core `EConstMachineImpl` initializes/steps only its classical rotor; it omits
the governor lifecycle present in `DynamicMachineImpl`. An imported TGOV1 could
therefore exist without affecting GENCLS mechanical power. The failed governed
SMIB initially remained READY rather than producing underfrequency.

The plugin's existing `IeeeVcEConstMachine` wrapper now initializes an active
governor, advances it at each stage and publishes finite Pm after the mechanical
stage, matching the established DynamicMachine order. Three dedicated tests
cover equilibrium, a reference-step response and inactive-governor behavior.
GENROU and the external core JAR are not changed. Controlled GENCLS support in
the opt-in staggered solver is not newly claimed by this slice.

## Native reference protocol and provenance

reference simulator on the existing public three-bus network, with a 100 MW/20 Mvar load
added at Bus 2. Bus 3 has GENCLS H=3/D=0 and TGOV1
R=.05/T1=.1/Vmax=1.5/Vmin=0/T2=0/T3=.1/Dt=0. Apply a -0.01 PU governor reference
increment at 0.05 s. WTGIBFFRA measures Bus 3 and uses 1.2 s user recovery.
Converter and REECD settings otherwise match the public Paux fixture.

The native global bus-frequency measurement filter is explicitly 0.05 s, matching
InterPSS. Installed API documentation identifies REALAR(4) of
`dynamics_solution_param_2` as this filter. This is a declared benchmark boundary
setting, not a fitted model parameter. Native default-filter diagnostics remain
separate. The installed Model Library GENCLS sheet (printed p33/physical p45)
was rendered/inspected to distinguish its speed STATE K from angle STATE K+1.

Two timesteps, 0.5/0.25 ms, each have two cold runs with identical raw CSV bytes.
Every file digest, 45 WTGIBFFRA CONs, 77 REECD CONs, ICON values, two GENCLS CONs,
seven TGOV1 CONs, finite raw channels and initial-conditions O.K. are audited.
Final native DSMDL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

Committed lossless gzip/Base64 checkpoints retain all native channel tokens,
including four inertia STATEs, seventeen VARs, seven REECD STATEs, generator/
governor STATEs and V/P/Q/frequency boundaries. There are **1,161 checkpoints**
from **15,005 nonnegative-time rows**, including both sides of the reference
event. Nominal tick excludes duplicates; original float time and side are
retained. No interpolation or input/state fitting is performed.

Raw/checkpoint bundle SHA-256:
`9c8aad9c83002b6c07d8cb5a3699424892e1ade453eb3a36e33a6fa137413673`.
See the [encoding and reproduction contract](../../ipss.test.plugin.core/src/test/resources/wtgibffra-kernel/README.md).

Early direct-state-edit probes did not sustain the intended native frequency
disturbance. They are retained as diagnostics, not accepted references. The
governor-input event avoids editing integrator storage. Native REECD VAR6 is
the external auxiliary slot, not the inertia output. The latter is the published
sum of the first three WTGIBFFRA STATEs; the test checks their sum plus VAR6.

## Comparison scope and observed errors

All inertia states/VARs are compared in sustained matching modes, with phase
sequence/timing checked separately. Filter STATE is checked at every retained
checkpoint. Raw reset/flag/timer maxima remain visible; pointwise callback parity
is not accepted. The phase-lag budget accumulates sampled handoffs, and ramp
bounds derive from slope, bounded phase lag and float accumulation.

Active REECD coordinates, commands and network boundaries are checked without
callback exclusions. Native inactive PIV/Vcomp storage is zero while InterPSS
retains different unused coordinates; those two coordinates are explicitly not
parity claims. Native Pord already differs by 1.395345e-6 PU in negative-time
initialization and stays constant; that raw offset is retained, not normalized
or copied into the InterPSS initial state. SG speed is reported diagnostically;
the complete synchronous-machine/governor trajectory is not accepted here.

Retained coarse/fine checkpoint maximum errors:

- Paux: 7.1992e-4 / 2.6998e-4 PU.
- Active-current command: 8.8557e-4 / 3.5087e-4 PU.
- Active power: 8.8944e-4 / 3.5544e-4 PU.
- Bus 1 voltage: 2.7814e-5 / 2.7735e-5 PU.
- Bus 2 voltage: 2.2222e-5 / 2.2853e-5 PU.
- Bus 3 voltage: 2.1359e-6 / 2.1723e-6 PU.
- Reactive power: 4.0492e-6 / 3.5255e-6 PU.
- Filtered frequency: 1.8848e-5 / 2.5981e-5 PU.

Permanent tests require Paux, Ipcmd and P error reduction under timestep halving.
Voltage/frequency floors do not consistently reduce and remain open. No broad
callback-amplitude bound or previously accepted envelope was relaxed.

## Gates and remaining work

Baseline is committed `9579cf207` plus this slice, including the prior committed
EXST1 limit fix. Concurrent uncommitted ST1/staggered-solver and WECC240/JAX work
is excluded from the tested snapshot and preserved unstaged. Core 1.4.3 JAR
SHA-256: `f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.

- Final focused import/controller/governor/SMIB/catalog/inventory/legacy mapping:
  **55 tests, no skips, failures or errors**.
- Full plugin-core: **234 tests, six optional skips, zero failures/errors**.
- Registered SMIB/catalog/solver: **1,274 tests, one existing diagnostic skip,
  zero failures/errors**. Final commands exit zero; totals come from fresh XML.

```text
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtgibffra-import-focused-20261018-m -Dtest=WtgibffraSmibIntegrationTest,PsseGenclsGovernorTest,PsseWtgibffraModelTest,Reecd1InertialResponseTest,EmbeddedTrajectoryCoverageInventoryTest,DynamicModelCatalogTest,PsseLegacyControllerMappingTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtgibffra-import-units-20261018-n --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtgibffra-import-smib-20261018-o -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

The first full SMIB run exposed one stale EXST1 assertion after the preceding
`9579cf207` commit: it omitted Kc*Ifd from the lower field-voltage bound. This
slice updates that assertion to the already committed PTI convention, consistent
with the dedicated EXST1 tests; it does not modify the concurrent exciter code.
Rejected focused gates, native probe protocols and the stale-assertion failure
remain in the archive. A source audit checks 2,000 baseline Java/build files and
22 scoped tested source/resource/fixture files, excluding concurrent work.

Remaining: exact native automatic recovery/callback parity, residual-floor
localization, native alternate converter/base/band and selector profiles,
online reconnect, proved legacy wrapper allocation and repeatable independent
PowerWorld evidence. Loadability is not a VERIFIED label.

Private sealed evidence location:
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtgibffra-native-import`.
It retains the final four cold runs and earlier diagnostics, runnable probes,
staged RAW/DYR, all raw channels/logs/manifests, source diagrams, focused failures,
InterPSS exports, final test XML/logs and source hashes. Simulator-driving Python
is private under repository policy; no ignore rule is bypassed.
