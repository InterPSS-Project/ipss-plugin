# Dynamic model coverage development plan and TODO

## Current checkpoint

TGOV1 PowerWorld follow-up: eight exact cold runs add complete fourteen-channel
upper/lower reference-step artifacts at two steps. Explicit recorded-speed
replay retains limiter-crossing errors; a scalar guard explains why they need
not decrease monotonically for one step-halving pair. A source-backed test-only
speed-scaled stator boundary, reusing core GENROU and unchanged parameters,
reduces the coupled Bus 1 voltage floor from 0.001442 pu to below 5e-7 pu.
Twelve matched-convention channel bounds pass; native-convention counterexamples
remain explicit and production imports are unchanged. Successful PowerWorld
post-initialization parameter readback and complete-family profiles remain open.
Final gates pass 15 focused, 410 plugin-core and 1,621 scoped SMIB/model/solver
checks, with zero failures/errors and only existing skips, plus four offline
probe guards and six language-policy checks. All eight raw projections and
1,815 unchanged Java/POM files are checked against the isolated snapshot.
See the [independent active-limit checklist](benchmarks/tgov1-powerworld-limits-2026-10-19.md).

TGOV1 active-valve follow-up: four positive-time-constant upper/lower reference
steps and inward releases have eight exact cold native runs, with all seven
parameters unchanged after initialization. Full-row two-state/mechanical-output
replay improves under step halving, and four strict-import coupled executions
exercise both valve bounds and release. A read-only reheater-storage accessor
preserves the existing CML coordinates and equations. Final gates pass twenty
focused, 410 plugin-core and 1,616 scoped SMIB/model/solver checks, with zero
failures/errors and only existing skips, plus four offline probe guards and
six language-policy checks. Scoped private hashes retain source/reference/report
evidence; complete-family and coupled cross-tool
acceptance remain open. See the
[active-valve checklist](benchmarks/tgov1-active-valve-limits-2026-10-19.md).

Ten-exciter fresh-reference follow-up: AC7C, AC8C, AC9C, DC1C, DC2C, AC8B,
EXAC1A, ST2C, ST3C and ST5C now have additional always-active dense SMIB
comparisons. Ten pinned numerical fixtures retain every raw event row;
all twenty raw cold exports match their public projections exactly. Native
running inputs are pinned separately from reference-tool dialect files.
All 158 existing channel bounds pass in the 42-check focused gate without
production model or tolerance changes. Final gates on baseline `91cc8b652`
pass 410 plugin-core and 1,609 scoped SMIB/model/solver tests, with zero
failures/errors and only existing skips. Private hashes retain source,
references, exact native trace pairs and final reports for this slice.
AC8B remains common-profile acceptance;
active-branch and complete-model verification gaps remain open. See the
[dense comparison checklist](benchmarks/exciter-fresh-dense-cross-tool-2026-10-19.md).

AC11C fresh-reference follow-up: an always-active dense SMIB comparison now
uses a pinned numerical projection of the recovered cold-run pair, retaining
all 2,003 rows and 23 channels in the fixture. An explicit importer validates
the raw export hash, metadata and every sample without replacing historical
checkpoints or using global test settings. Both historical and fresh coupled
profiles pass the original 19 channel bounds; production model equations and
parameters are unchanged. Final gates pass 18 focused, 406 plugin-core and
1,596 scoped SMIB/model/solver tests, with zero failures/errors and only
existing skips. Private source/reference/report hashes retain this slice.
Instantaneous event-side coupled acceptance,
active auxiliary branches and complete-model verification remain open. See the
[fresh-reference checklist](benchmarks/ac11c-fresh-cross-tool-2026-10-19.md).

Exciter input-provenance follow-up: recover eleven missing historical DYR
inputs by their exact archived hashes and preserve thirty content-addressed
assets. All eleven recovered specifications complete twice cold in PowerWorld;
all fresh pairs match exactly. Ten match the historical exports numerically;
AC11C retains a small, explicitly reported discrepancy. Six original
specification byte sequences remain unavailable. Final validation passes
23 focused, 406 plugin-core and 1,591 scoped SMIB/model/solver checks with
zero failures/errors and only existing skips, at unchanged model tolerances.
Source/reference/report hashes retain this scoped provenance slice. See the
[input recovery checklist](benchmarks/exciter-input-recovery-2026-10-19.md).

PSS6C/PSS7C compensation/canonical follow-up: correct machine impedance-base
conversion, PSS6C's published negative-input integrator cascade and PSS7C's
observation-only pure-lag coordinate mapping. Sixteen cold native runs add
eight matched/half-step profiles at 50 and 200 MVA. Full-row state/output
replay, physical nameplate-base guards and eight strict-import faults pass.
Final validation on baseline `100ffc099` passes 107 focused, 406 plugin-core
and 1,579 scoped SMIB/model/solver checks, with zero failures/errors and only
existing skips. Source/reference/report hashes retain this scoped correction;
complete coupled parity and further selector/limit/lifecycle profiles remain open. See the
[compensation/canonical checklist](benchmarks/pss-compensation-canonical-2026-10-19.md).

PSS6C/PSS7C native schema separation: exact 34/38-value native records are
now distinct from the 35/39-value PowerWorld builder layouts. Two schema
assertions fail before the fix; native compensation-tail ordering and vendor
filter mapping have permanent guards. Four cold native runs reproduce every
raw sample/channel of the existing SMIB fault references. Final gates on
baseline `93cb13b54` pass 100 focused, 406 plugin-core and 1,572 scoped
SMIB/model/solver checks with zero failures/errors and only existing skips.
Source/run/report hashes retain the scoped evidence; equations and existing
trajectory tolerances are unchanged. Broader model verification remains open.
See the [schema separation checklist](benchmarks/pss-canonical-native-schema-2026-10-19.md).

GENQEC bounded field-compensation correction: finite Kw>=0 now replaces
the artificial Kw<1 restriction, and NaN is rejected. The existing Kw*Id
product limiter and shared machine equations remain unchanged. Twelve native
and twelve PowerWorld cold runs cover Kw=0/1/1.5 at two steps, including native
initialization, both product-limit signs and release. Electrical replay and
strict imported fault checks pass. Final validation on baseline `1b981eb9b`
passes 23 focused, 406 plugin-core and 1,569 scoped SMIB/model/solver checks,
with only existing skips and zero failures/errors. Source/run/report hashes
retain the scoped implementation evidence. Complete coupled parity remains open. See the
[compensation checklist](benchmarks/genqec-field-compensation-2026-10-19.md).

GENQEC/GENQEJ bounded salient-pole verification: eight exact T'qo=0/X'q=Xq
phasor/state profiles were generated twice cold across two models, two saturation
choices and two steps. Eight additional PowerWorld standard-model diagnostics
repeat exactly. Electrical replay preserves every raw row and bounds four
electrical states, saturation and P/Q; forced rotor inputs do not establish
mechanical trajectory acceptance. Exact predictor/corrector and eight imported
fault checks pass. The tempting q-state projection is rejected by the native
guard, so the existing machine engine and named-state mapping are retained.
The final gates pass 18 focused, 401 plugin-core and 1,562 SMIB/model/solver
checks with only existing skips and zero failures/errors. The shared machine
engine is retained unchanged, with source/run/report hashes for the scoped
verification commit; complete coupled parity remains open. See the
[salient-branch checklist](benchmarks/genqec-salient-branch-2026-10-19.md).

GENQEC bounded native-schema slice: the documented one-ICON/sixteen-CON
standard record replaces the incorrect flat eighteen-field interchange.
GENQECU/GENQEJU wrappers now normalize every nonzero native selector to scaled
quadratic without changing the typed three-way API or shared machine engine.
Four before-fix guards fail and 51 focused checks now pass. Twenty native cold
runs and eight final PowerWorld runs provide exact-input selector evidence and
repeatable diagnostics. The final gate passes 1,559 SMIB/model/solver tests
(one existing diagnostic skip) and 401 plugin-core tests (six optional skips),
with zero failures/errors. Source/run/report hashes retain the scoped commit
evidence; complete coupled parity remains open. See the
[GENQEC source/evidence checklist](benchmarks/genqec-native-schema-2026-10-18.md).

WT12A1B bounded slice committed as `53a70fea0`: explicit-ID wrapper parsing, native-versus-published
initial-reference selection, persistent-low-voltage retriggering and integration
stage guards now pass sixteen focused integration checks (21 reactor checks
including tokenizer tests). Twenty-four native runs and twelve
PowerWorld runs pass cold-repeatability/input audits. Ten positive-filter
three-state replays have bounded acceptance; two zero-filter profiles and all
independent coupled/PowerWorld trajectories remain diagnostics. Registered SMIB
regression passes 1,551 tests (one diagnostic skip); plugin-core passes 401 (six
optional skips), with no failures/errors. Three deliberate regressions are
rejected. The restored-source gate passes 42 checks; private source/run/report
hashes retain the reproducible evidence for the scoped implementation commit.
Complete-model verification remains open. See the
[WT12A1B checklist](benchmarks/wt12a1b-native-smib-2026-10-18.md).

Updated 2026-10-18; upstream baseline is plugin `dcea9ab30` (1.4.6, core 1.4.3).
Subsequent local implementation and validation slices are recorded below.
This document restores the ongoing coverage checklist after rebasing to master.
The generated [support matrix](dynamic-model-support-matrix.md) records parser
and runtime availability. A `LOADABLE` row does not prove independent equation,
trajectory, or full-system acceptance.

**User scope revision, 2026-10-17: focus on SMIB verification.** Do not require
or launch full Texas2k dynamic simulations for this work. Keep Texas2k's input
model inventory as a coverage requirement, but verify those models using
small, independently reproducible machine/infinite-bus or controller testbeds.
Existing system results and failures remain historical evidence, not release
gates for this revised objective.

**Latest scoped acceptance status: incomplete; bounded runtime presence is not full verification.**
The approved native renewable audit finds **27 distinct families, all with bounded
versioned loadable runtime paths**. Exact spellings, selectors, hosts and independent
verification still have material gaps. REPCC now has exact standard import and a bounded REGCA1/REECA1
runtime, enabled-MSS import and bounded fast-band switching comparisons. Slow-band
clock precision and other initialization/host modes remain open. HPLNTD now has a
typed data/aggregation, bounded plant/downstream kernels and a shared opt-in
staggered integration boundary, and a bounded typed multi-unit runtime coordinator.
Exact HPLNTDU/REAXDU wrappers now attach through the main importer with bus-owned
group preflight, deferred electrical-controller ordering, explicit removal/error
accounting and named-state observation. Source-list HPLNTD is not an inferred alias.
The coupled fault diagnostic now identifies a corrected adapter event-frequency
reference ordering defect; instantaneous native current/frequency callback parity
and complete coupled fault acceptance remain open.
Disabled active/reactive plant paths now hold initial commands and leave unused
storage at zero, while PF control retains its required active measurement.
Sixteen two-step selector profiles add recorded-input and bounded coupled SMIB
acceptance. Feasible proportional-only initialization and PF without electrical
feedback now have 24 two-step profiles. Direct-reference probes isolate native
raw-VAR voltage-limit bypass; InterPSS retains its published clamp. Four Q/PF
reference-disturbance profiles add state-replay and bounded coupled acceptance;
native voltage parity and nonzero-deadband initialization remain open.
The HPLNTDU switched-shunt kernel now reuses the ordered ten-device engine with
physical-Mvar thresholds, not REPCC machine-base pu. Fourteen two-step profiles
verify threshold/breaker timers and physical device statuses; two zero-delay
profiles retain origin/timer diagnostics. Exact physical G+jB switching and
accepted-endpoint dispatch now enable the imported path. Eight coupled profiles
have bounded acceptance; eight retain clock/origin diagnostics, and native
discharge-reclose parity remains open. See the
[switching kernel evidence](benchmarks/hplntd-switching-kernel-2026-10-18.md).
The [physical-boundary follow-up](benchmarks/hplntd-switching-boundary-2026-10-18.md)
records exact input networks and clock-only discrepancy discriminators.
Six additional three-cycle fault profiles now isolate native capacitor
discharge/reversal discrepancies: changing Tout from 0 to 0.03/0.15 s leaves
all native channels/statuses identical, while direct sign reversal retains
the native fast timer. InterPSS preserves the published discharge lockout and
sign-specific persistence; these new profiles are counterexamples, not added
native trajectory acceptance. See the
[discharge/reversal evidence](benchmarks/hplntd-discharge-reversal-2026-10-18.md).
The source-list REPCCU spelling is not an inferred alias. REEC_E now has strict native reference standard-name `REECE`
import and deferred REGCA/REGCB attachment. The source-list `REECE1/REECEU1`
names/wrappers remain unsupported; family runtime presence does not prove their
layouts, complete callback parity or coupled trajectory acceptance. The REECE/REGCB
hardware-blocking current boundary is corrected with failing-before and cold-paired
native evidence. A subsequent native discriminator resolves the printed-diagram
active-PI freeze mismatch; other coupled trajectory residuals remain open.
Native QFLAG=2 now also projects its frozen reactive PI state into collapsed
blocking bounds and applies blocking at the filtered-voltage crossing endpoint.
WTGIBFFRA now has
strict standard-native import, REGCA/REGCB + REECD Paux attachment, seven kernel
replay profiles and bounded governed-SMIB comparisons. Native automatic recovery,
callback parity and frequency/voltage residual floors remain open. WTGWGOA/WTGWGOAU
now has strict native import, controller reference routing and bounded SMIB
evidence; callback/handoff and instantaneous network-post parity remain open. WTDTB1/WTDTBU1
now has strict native import and bounded SMIB/reference evidence, with explicit
non-unit precision and instantaneous network-post limitations. The runtime
catalog previously omitted those unsupported families. Missing exact stabilizer
profiles, exhaustive independent model profiles and remaining provenance
gaps also need closure. Generated `LOADABLE` entries do not prove complete
verification. The new `DynamicModelSmibTestSuite` selects model-unit and
small-network independent comparisons without full Texas2k/WECC/IEEE39 runs.
Earlier system gates below apply only to their recorded snapshots; the latest
six-case drift failures are retained in the
[REGFMA1 report](benchmarks/regfma1-native-schema-2026-10-17.md), outside the
revised SMIB acceptance scope.

Scope remains native RAW/DYR with `_gnet.idv` and `_MODREMOVE.idv`
preparation. GE PSLF DYD inputs and rows without a native reference simulator model name are
excluded. Reuse core GENROU and existing controller engines where equations
and state definitions agree.

## Approved renewable source and evidence audit

### HPLNTDU switching kernel slice

- [x] Preserve the native 35-ICON/61-CON layout and ten input-order device slots.
- [x] Reuse ordered REPCC switching logic only for the shared switching rules;
  keep HPLNTDU thresholds/input in physical Mvar, independent of plant MVA.
- [x] Verify fast/slow bands, both directions, removal before insertion,
  disabled switching, invalid thresholds and missing measurement branch.
- [x] Cold-generate sixteen small-testbed profiles twice at 0.5/0.25 ms;
  preserve all raw history/duplicate rows and ten physical status channels.
- [x] Match twelve threshold/breaker timers and all ten statuses in fourteen
  nonzero-delay profiles. Keep zero-delay origin/timer differences explicit.
- [x] Test published capacitor discharge lockout, idempotent accepted endpoints,
  latched requests, reinitialization and named-state immutability independently.
- [x] Pass 38 focused Java checks, all 401 plugin-core tests (six existing
  optional skips) and 1,530 selected SMIB/model/solver tests (one existing
  diagnostic skip), with zero failures/errors and no full Texas2k dynamics.
- [x] Bind HPLNTDU actions to exact individual physical G+jB and Y updates;
  add accepted-endpoint dispatch and main-import nominal-data preflight.
- [x] Reuse the same physical binding for REPCC without changing its units,
  switching rules or accepted trajectories; test persistent Y/cache updates.
- [x] Verify eight coupled profiles across fast-band coarse and disabled/
  invalid/missing-branch cases at both timesteps, scoring all 26 channels and
  ten device statuses. Preserve eight clock/origin diagnostic profiles.
- [x] Replay identical recorded reactive power on native and double solver
  clocks; reproduce every positive-time physical status without timing offsets.
- [x] Pass the physical-boundary slice's 38 focused checks, 401 plugin-core tests
  (six existing optional skips), 1,536 selected SMIB/model/solver tests (one
  existing diagnostic skip) and 40 offline checks, with zero failures/errors.
- [x] Add six capacitor discharge/reclose and direct-sign-reversal native
  discriminators at both timesteps, cold-regenerated twice (twelve runs).
- [x] Distinguish functional native Tout insensitivity from unused timer storage;
  preserve published lockout and sign-specific timing in replay and imported
  three-cycle fault tests, without fitting production to the counterexamples.
- [x] Pass 32 focused Java checks, 401 plugin-core tests (six existing optional
  skips), 1,541 selected SMIB/model/solver tests (one existing diagnostic skip)
  and 46 offline guards. Both isolated rule-removal mutations fail as intended;
  the production engine is restored unchanged before all final gates.
- [ ] Close fine fast-band/slow-band exact timing and source-versus-native
  discharge/reversal parity. The functional reference differences are now
  demonstrated, not accepted by deleting lockout or suppressing timer resets.
- [ ] Resolve zero-delay callback/origin and native unused-timer differences
  before claiming exhaustive state/callback equivalence.

- [x] Freeze the exact REPCGFMC1U seven-ICON/54-CON/15-STATE/20-VAR contract
  and four distinct GFM/GFL output paths. Implement typed data and algebraic
  initialization without an invented REPCA1 alias or dummy runtime.
- [x] Preserve eight cold-paired native foundation datasets: branch flat,
  two-step three-cycle fault, automatic loss estimation, missing-branch fallback,
  alternate plant MVA base, feasible proportional-only voltage control and
  terminal-voltage selection. Retain rejected infeasible/undefined-branch attempts.
  See [plant foundation evidence](benchmarks/repcgfmc-native-contract-2026-10-18.md).
- [x] Validate the plant foundation on baseline `18414693e`: 53 focused checks,
  307 plugin-core tests (six optional skips), 1,359 registered SMIB/model/solver
  checks (one existing diagnostic skip), zero failures/errors. Audit 23 tested
  source/resource files, sixteen accepted native runs and eight byte-identical
  cold pairs. Runtime coverage remains 24/27, not complete model verification.
- [ ] Implement the exact fifteen-state REPCGFMC1U kernel, frequency/voltage
  selectors and bounds, FFR timer/re-arm/ramp logic, measurement resolution and
  base conversion, strict import and REGFMC1U reference routing. Validate all
  states and independently coupled SMIB profiles before raising loadability.
  - [x] Implement the fifteen-state kernel, semantic states, explicit modified-
    Euler stages, bounded integrals, algebraic bypasses and actual-step frequency
    correction; preserve unused storage when both voltage paths are disabled.
  - [x] Add ten cold-paired native manual-reference, active P/Q/voltage-limit and
    FFR hold/ramp/re-arm datasets at two steps. Replay all states from recorded
    physical measurements and external references, without injecting native states.
    See [kernel evidence and remaining work](benchmarks/repcgfmc-kernel-2026-10-18.md).
  - [x] Validate the kernel: 52 focused checks, 318 plugin-core tests (six optional
    skips), 1,364 SMIB/model/solver checks (one existing diagnostic skip), zero
    failures/errors. Audit 26 tested source/resource files, twenty new native
    runs and ten byte-identical cold pairs; retain float/history/timer gaps.
  - [x] Implement strict native import, signed branch/frequency-bus measurement,
    base conversion and REGFMC1U four-output routing. Add nine bounded coupled
    SMIB profiles and ten new cold native runs (five byte-identical pairs).
    See [import evidence](benchmarks/repcgfmc-native-import-2026-10-18.md).
    Runtime coverage is now 25/27; the earlier 24/27 counts are historical.
  - [x] Validate import on plugin `c6cdf42ca` and the exact archived updated
    core dependency: 78 focused checks, 318 plugin-core tests (six optional
    skips), 1,373 SMIB/model/solver checks (one existing diagnostic skip), zero
    failures/errors. Audit 25 owned source/resource files and all new native
    measurement routes; preserve rejected signed-branch diagnostics separately.
  - [ ] Close independent signed receiving-end/transformer trajectories,
    instantaneous callback parity, broader zero-time and reconnect lifecycle
    coverage. Native negative-bus runs used terminal fallback and are rejected
    as receiving-end oracles; their convergence does not prove routing parity.

- [x] Add exact native REGFMC1U wrapper parsing and hybrid network adapter, with
  non-overwriting attachment, machine/system-base Norton conversion, semantic
  monitoring, explicit removal and trip behavior. Coverage is now 24/27 runtime
  paths; HPLNTD, REPCCU and REPCGFMC1 remain missing.
- [x] Compare imported flat, three-cycle-fault and trip SMIB profiles against
  native references at both steps; preserve initial offsets and callback limits.
  Four new cold native trip runs form two byte-identical pairs. See
  [native import evidence](benchmarks/regfmc-native-import-2026-10-18.md).
- [x] Validate the native import slice: 75 focused checks, 299 plugin-core tests
  (six optional skips), 1,348 registered SMIB/model/solver checks (one existing
  diagnostic skip), zero failures/errors. Audit seventeen tested source/resource
  files, 28 native runs and fourteen byte-identical cold pairs; preserve raw
  event history and the declared instantaneous callback limitations.
- [ ] Complete exact REPCGFMC1 plant routing and broader REGFMC1U coupled
  selector/current-limit/reference/reconnect and complete independent acceptance.
- [x] Freeze native reference REGFMC1U allocation (three ICONs, 34 CONs, eleven
  STATEs, 27 VARs), initialization and hybrid GFM/GFL boundary from rendered
  diagrams; implement typed data and algebraic primitives without a dummy runtime.
- [x] Cold-run flat, three-cycle fault and severe current-limited SMIB profiles
  twice at two steps; retain all native channels, history and event sides.
  Compare GFL priority/deadband and the shared current limiter against every raw row.
  See [REGFMC1U foundation](benchmarks/regfmc-native-contract-2026-10-18.md).
- [x] Validate the REGFMC1U foundation: 46 focused checks, 290 plugin-core tests
  (six optional skips), 1,333 SMIB/model checks (one existing diagnostic skip),
  zero failures/errors. Audit thirteen tested files and six byte-identical native
  cold pairs, with complete log/CSV quality checks.
- [ ] Complete REGFMC1U eleven-state kernel, native parser/network adapter,
  plant routing, selector/lifecycle tests and independently coupled SMIB comparisons.
  The foundation checkpoint retained 23/27 loadable paths and four missing
  runtimes; the later import slice above raises runtime presence to 24/27.
  - [x] Implement the eleven-state kernel, semantic states, explicit modified-Euler
    stages, PI/speed anti-windup, zero-time measurements and distinct GFM/GFL references.
  - [x] Add twelve accepted native cold runs / six datasets for off/bounded and
    Fflag-only selectors, reference reversals and both active PI/speed limits.
  - [x] Replay all eleven states in twelve datasets at two steps; retain explicit
    native event-history, absolute-speed float-floor and current-circle limitations.
    See [kernel evidence and remaining work](benchmarks/regfmc-kernel-2026-10-18.md).
  - [x] Validate the kernel: 79 focused checks, 298 plugin-core tests (six optional
    skips), 1,338 SMIB/model checks (one existing diagnostic skip), zero failures/errors.
    Audit twelve tested files, all 24 native runs and twelve byte-identical cold pairs.
  - [x] Add the exact native wrapper, network adapter and independently coupled
    flat/fault/trip SMIB gates. This raises runtime presence to 24/27, not full acceptance.
- [x] Add strict native REGFMB1U wrapper import, non-overwriting device attachment,
  machine/system-base Norton boundary and deferred REPCA1 routing. Coverage is
  23/27 versioned runtime paths; HPLNTD, REPCCU, REGFMC1 and REPCGFMC1 remain missing.
- [x] Run imported REGFMB1U flat/fault/trip SMIB profiles at two steps and bound
  six physical channels against immutable native output. See
  [import evidence](benchmarks/regfmb-native-import-2026-10-18.md).
- [x] Validate REGFMB1U import: 70 focused checks, 283 plugin-core tests (six
  optional skips), 1,327 SMIB/model tests (one existing diagnostic skip), zero
  failures/errors; audit fourteen tested source/fixture files and prior native evidence.
- [x] REGFMB1 foundation: visually freeze native reference REGFMB1U exact wrapper,
  six ICONs/25 CONs/14 STATEs/17 VARs, distinct from PowerWorld parameter fields.
- [x] Cold-run flat and three-cycle-fault native SMIB profiles twice at two
  steps; retain all channels/event sides and byte-identical cold pairs.
- [x] Implement native typed data and algebraic network-boundary primitives,
  with initialization/base and filtered-bound comparisons against native output.
- [x] Validate the REGFMB1U foundation: 32 focused checks, 276 plugin-core tests
  (six optional skips), 1,313 SMIB/model tests (one existing diagnostic skip),
  zero failures/errors; audit all eight native runs and four byte-identical pairs.
- [x] Implement the typed REGFMB1U fourteen-state kernel and replay all states
  for thirteen native datasets, retaining explicit modified-/staggered-Euler
  differences. Add cold-paired selector/sign/washout/PLL discriminators.
- [x] Validate the kernel: 44 focused checks, 283 plugin-core tests (six optional
  skips), 1,318 SMIB/model tests (one existing diagnostic skip), zero failures/errors;
  audit 22 new native runs and eleven byte-identical cold pairs. Keep family missing.
- [x] Add the strict wrapper/device path, plant attachment and basic trip-state
  freeze; retain bounded two-step native coupled fault checks.
- [ ] Expand native active-limit, zero-time, negative-power and PLL recovery
  profiles, plant disturbances/reconnect and independent PowerWorld acceptance.
  See [kernel evidence and remaining work](benchmarks/regfmb-kernel-2026-10-18.md).
  The earlier foundation checkpoint was 22/27; native import above raises it to 23/27. See
  [REGFMB1 native foundation](benchmarks/regfmb-native-contract-2026-10-18.md).
- [x] Native REECE import slice: exact eight-ICON/79-CON standard schema,
  deferred REGCA/REGCB attachment and malformed/duplicate/GNET/base guards.
- [x] Correct REGCB physical-current blocking without clearing retained states;
  retain failing-before evidence and two cold-paired native boundary profiles.
- [x] Validate this slice: 95 focused checks, 266 plugin-core tests (six optional
  skips), 1,306 SMIB/model tests (one existing diagnostic skip), no failures/errors.
- [x] Resolve the native REECE active PI voltage-dip freeze discrepancy using
  cold-paired recorded-input replay; preserve native-versus-diagram distinction.
  See [active PI evidence](benchmarks/reece-active-pi-dip-2026-10-18.md).
- [x] Validate the native PI correction: 35 focused checks, 267 plugin-core tests
  (six optional skips), 1,308 SMIB/model tests (one existing diagnostic skip),
  zero failures/errors. Confirm the replay class is discovered in the SMIB XML.
- [ ] Resolve remaining REECE coupled reactive-limit/callback mismatch;
  the blocking and active PI fixes are not full trajectory acceptance.
- [x] Correct native local reactive PI bound projection during dip freeze and
  immediate blocking entry, with failing-before and two-step native replays.
  See [reactive blocking evidence](benchmarks/reece-reactive-blocking-2026-10-18.md).
- [x] Validate reactive blocking: 38 focused checks, 269 plugin-core tests
  (six optional skips), 1,309 SMIB/model tests (one existing diagnostic skip),
  zero failures/errors; retain both failing-before attempts and raw native hashes.
  See [slice evidence](benchmarks/reece-native-import-2026-10-18.md).
- [x] Visually review pages 3-4 of the user-supplied May 2026 PDF, retain its
  SHA-256 identity, and transcribe all 45 renewable rows into the approval catalog.
- [x] Separate 29 approved rows, 28 concrete native PTI mappings, and 27 distinct
  native families. Exclude blank-PTI PVD1; do not double-count WT12T1 aliases.
- [x] Expose ten truly missing native implementations without dummy runtime
  classes, guessed parameter allocations or aliases copied from another tool.
- [x] Add explicit evidence links for all 17 existing approved native families:
  eight native profile links, one partial native Iolim profile, six bounded
  ANDES dialect comparisons and two focused-only gaps. None is a VERIFIED label.
- [x] Fix SMIB discovery to include the already-registered small Type-3 ANDES
  comparison and renewable Q/V diagnostics. Keep vendor-difference limitations
  and existing tolerances; do not call the finite-only PowerWorld diagnostic parity.
- [x] Complete final validation: 35 focused checks without skips; 1,191 expanded
  SMIB/model tests with one existing diagnostic skip; 135 plugin-core units
  with six optional skips; zero failures/errors. Nine scoped source/generated-
  document files match the tested snapshot byte-for-byte.
- [x] Seal and hash-verify 732 renewable source/evidence files and prepare the
  tested scoped commit; leave unrelated governor/solver work untouched.
- [ ] Complete WTDTBU1 / current-library WTDTB1 verification. Its implementation
  and strict native import now exist; remaining acceptance gaps stay open.
  - [x] Foundation only: source-diagram five-state kernel, common drivetrain
    interface, typed wind-stack selection, algebraic Tp=0 refresh and 13 new
    equation/attachment tests. This is not a loadable or trajectory-accepted model.
  - [x] Audit six native profiles twice cold at 0.5/0.25 ms, all input/output
    hashes, all 14 channels and duplicate event sides. Confirm the exact legacy
    `103 0 0 6 5 3` wrapper matches the standard native record byte-for-byte.
  - [x] Independently run the exact typed PowerWorld WTGT_B baseline twice cold
    at each timestep with unchanged exported parameters and no automatic
    correction. Add permanent five-state power-playback checks, event-side
    retention, frozen fingerprints and negative equation-mutation guards.
    See [PowerWorld profile scope and native discrepancy](benchmarks/wtdtb1-powerworld-playback-2026-10-18.md).
  - [x] Localize a candidate native-only `wg/wt` shaft-feedback term with two
    independently perturbed speed coordinates and exact cold-repeat outputs;
    initially retain it test-only, then promote the explicitly versioned path
    after the branch discriminators and bounded full-solver checks below.
  - [x] Implement the explicitly versioned native feedback, retained one-mass
    turbine coordinate, absolute damping and balanced non-unit aerodynamic input.
    Preserve the separate published-diagram convention and modified Euler.
  - [x] Add strict native/wrapper parser and builder paths, deferred attachment,
    missing-host/duplicate/layout rejection, GNET/model-removal handling and
    immutable named-state visibility through the converter.
  - [x] Reject suspect-initialization non-unit probes; regenerate compatible
    PFLAG=0 profiles. Audit 14 accepted-initialization profiles twice cold,
    including exact CON values, 84,154 raw samples and both event sides.
  - [x] Add permanent native playback and PowerWorld W0=.97 references; run
    two-step-size full-solver flat, three-cycle fault and generator-trip checks.
    Require coupled five-state error reduction when halving the step.
  - [x] Fix the new test's JVM-global diagnostic-output setting without changing
    model equations or trajectory tolerances. Final isolated regression on
    committed plugin `54429b9c7` plus this slice: 160 plugin units (six optional
    skips), 1,217 SMIB/catalog/staggered-solver tests (one diagnostic skip), zero
    failures/errors. Unrelated uncommitted controller/JAX edits are excluded.
  - [ ] Close native non-unit absolute integrated-coordinate precision,
    instantaneous coupled-network event-post export, additional PowerWorld
    branch/base profiles and independently referenced reconnect/restart coverage.
    See [native implementation and exact acceptance scope](benchmarks/wtdtb1-native-import-2026-10-18.md).
  See [WTDTB1 foundation evidence and open discrepancies](benchmarks/wtdtb1-foundation-2026-10-18.md).
- [ ] Complete WTPTBU1 / current-library WTPTB1 verification; native import and
  runtime integration now exist, with partial independent acceptance.
  - [x] Freeze native fourteen-CON/three-STATE/one-VAR source and visually
    inspect its diagram; keep the different PowerWorld parameter layout separate.
  - [x] Add a typed source kernel with state-based PI anti-windup, independent
    PI output limits, dynamic speed-reference input and directional final-angle
    cross anti-windup; preserve modified Euler and immutable observations.
  - [x] Pass ten focused equation/initialization/stage/limit tests; audit sixteen
    twice-cold native runs with valid initialization and exact CSV/hash agreement.
    Add bounded three-state playback at both baseline steps and active upper angle.
  - [x] Complete merged regression on committed plugin `4e5405ce8` plus this
    foundation: 171 plugin units (six optional skips), 1,220 registered
    SMIB/catalog/solver tests (one existing diagnostic skip), zero failures/errors.
  - [x] Add strict standard/legacy import, deferred complete-host attachment,
    duplicate/layout rejection, named states and GNET/model-removal accounting.
    Native `104 0 0 14 3 1` legacy output repeats the standard record exactly.
  - [x] Wire plant Pref and atomically captured torque speed reference without
    changing WTPTA1 behavior. Support native all-zero WTTQA1 curve as Wref=1.
  - [x] Implement native Tp=0 algebraic bypass and explicit two-step history
    freeze handling; independently check zero-time, moving speed reference,
    lower angle and both PI-state limits at two steps with cold repeats.
  - [x] Add coupled flat, three-cycle fault and trip checks at both steps;
    require pitch/PI/generator-speed errors to reduce under step halving.
  - [x] Create four exact typed PowerWorld WTGPT_B cold-run artifacts at two
    steps, with unchanged audited parameters and automatic correction disabled.
    These establish repeatability, not complete cross-tool trajectory acceptance.
  - [x] Pass final native-import regression: 178 plugin units (six optional
    skips), 1,233 SMIB/catalog/solver tests (one diagnostic skip), zero
    failures/errors. Keep the 25-family Texas2k source inventory unchanged.
  - [ ] Close instantaneous network-post comparison, plant-reference steps,
    additional host/base and reconnect/restart profiles, native opt-in solver
    precision and complete PowerWorld trajectory acceptance.
    See [WTPTB1 foundation scope and native evidence](benchmarks/wtptb1-foundation-2026-10-18.md).
    The [native import/SMIB report](benchmarks/wtptb1-native-import-2026-10-18.md)
    records the production path, exact evidence and remaining limitations.
- [ ] Complete WTGWGOAU verification and implement WTGIBFFRA, auditing installed-release identifiers,
  input/output coupling and weak-grid/inertial-response branches separately.
  - [x] Add WTGWGOA eight-CON/three-STATE/three-VAR kernel foundation;
    distinguish published second-fault restart from observed reference simulator
    continuation. Preserve default published behavior and explicit native factory.
  - [x] Audit 22 cold native runs, including an exact legacy wrapper, and retain
    ten immutable recorded-input/checkpoint profiles. Pass 14 equation/stage
    tests and four partial native playback/integrity tests.
  - [x] Pass merged foundation regression on `87ef8a0eb`: 195 plugin units
    (six optional skips), 1,237 SMIB/catalog/solver tests (one diagnostic skip),
    zero failures/errors. Preserve raw callback mismatches as unaccepted evidence.
  - [x] Complete strict WTGWGOA native import, optional plant and REGCA/REGCB
    host attachment; route its absolute reference into REEC_A/B/C/D and Type-3
    torque/pitch. Test both native schemas, malformed/duplicate/removal behavior.
  - [x] Run coupled REECA/REGCA SMIB flat, three-cycle fault, no-plant and trip
    checks at two steps; freeze additional native V1/V2/V3/P/Q checkpoints.
  - [x] Pass native-import regression on `29f5cacd0`: 199 plugin units (six
    optional skips), 1,248 SMIB/catalog/solver tests (one diagnostic skip), zero
    failures/errors. Retain separate channel bounds and unaccepted event rows.
  - [ ] Close callback/handoff and instantaneous network-post pointwise parity,
    independently referenced alternate-host/base/Type-3/reconnect profiles,
    and complete PowerWorld benchmark artifacts. Coverage is now 20/27 loadable;
    neither loadability nor matching-mode playback establishes full verification.
    See [foundation evidence and remaining acceptance](benchmarks/wtgwgoa-foundation-2026-10-18.md).
    See [native import and coupled SMIB scope](benchmarks/wtgwgoa-native-import-2026-10-18.md).
  - [x] Freeze WTGIBFFRA's documented one-ICON/45-CON/four-STATE/17-VAR
    contract and inspect its diagrams. Runtime six-ICON allocation is not six
    DYR inputs. Trace its distinct Paux port rather than reusing weak-grid Pref.
  - [x] Identify and correct REECD's Paux power/current units and algebraic
    input refresh. Generate eight final cold native Paux references and retain
    three additional cold WTGIBFFRA recovery-discriminator pairs.
  - [x] Pass the merged Paux prerequisite regression on `c8890ae1f`: 205 plugin
    units (six optional skips), 1,251 SMIB/catalog/solver tests (one diagnostic
    skip), zero failures/errors. Both reference-update sides are compared;
    inactive native PI/bypass storage differences remain explicitly unaccepted.
  - [x] Implement WTGIBFFRA typed data and published-equation kernel: exact
    grouped 45-CON data, six floor-selected power bands, captured incident power,
    latched rise/peak/fall/recovery, published minimum recovery, cooldown/rearm,
    immutable named states and modified-Euler predictor rollback.
  - [x] Prevent accumulated roundoff from adding a whole step at constant-rate
    ramp/timer endpoints. Retain modified Euler; compensate additions and use
    only ULP-scale endpoint equality, not a timestep-sized boundary tolerance.
  - [x] Freeze seven cold-paired native kernel replay profiles, all input rows,
    four STATEs and seventeen VARs at checkpoints including callback windows.
    Expose cumulative callback lag and raw reset errors rather than claiming
    pointwise conformance. Keep native automatic recovery and ignored-data
    uninitialized VARs separate from accepted partial evidence.
  - [x] Pass foundation regression on `972fed2ba`: 227 plugin units (six
    optional skips), 1,254 SMIB/catalog/solver tests (one diagnostic skip), zero
    failures/errors. Preserve concurrent ST1/parser work outside this slice.
  - [x] Complete strict standard native one-ICON/45-CON import, deferred
    REGCA/REGCB + REECD Paux attachment, duplicate/malformed/missing-host rejection,
    remote/local/missing/inactive frequency-bus selection and GNET/model removal.
    Keep unproven legacy allocations and cross-tool aliases unsupported.
  - [x] Preserve independent external and inertial Paux inputs; sum both after
    Pord and before the voltage divider. Test stage rollback, algebraic corrector
    refresh, weak-grid Pref coexistence, machine-base conversion and reinit.
  - [x] Fix the classical GENCLS plugin wrapper's omitted governor initialization,
    stage stepping and Pm publication, matching the existing DynamicMachine
    stage ordering. Do not change GENROU or the external core dependency.
  - [x] Cold-repeat two native governed-SMIB frequency-response profiles with
    explicit matched 50 ms bus-frequency measurement; retain all event sides,
    actual parameter audits and native STATE/VAR/network checkpoints.
    Add two-step flat, frequency-step, three-cycle fault and trip/reinit tests.
  - [x] Pass merged native-import regression on `9579cf207`: 55 focused checks,
    234 plugin units (six optional skips) and 1,274 SMIB/catalog/solver tests
    (one diagnostic skip), zero failures/errors. Update the stale EXST1 lower-
    limit assertion to the prior committed source convention; leave concurrent
    ST1/staggered-solver work outside this tested slice.
  - [ ] Close native automatic-recovery/callback discrepancies, frequency and
    voltage residual floors, alternate-host/base native profiles, online
    reconnect and independent PowerWorld artifacts. Loadability is now 21/27,
    not complete verification.
    See [kernel foundation and explicit limitations](benchmarks/wtgibffra-foundation-2026-10-18.md).
    See [native import and coupled SMIB verification](benchmarks/wtgibffra-native-import-2026-10-18.md).
    See [Paux correction and WTGIBFFRA investigation](benchmarks/reecd-paux-native-2026-10-18.md).
- [ ] Complete REEC_E across documented native releases, including unresolved
  REECE1/REECEU1 record layouts; do not silently substitute REECD.
  - [x] Inspect native reference REECE eight-ICON/79-CON/eight-STATE/20-VAR schema
    and diagrams; independently consult WECC's July 2025 memo and PowerWorld.
    Cold-prove the installed standard name REECE. Plain REECE1/REECEU1 records
    with this layout are rejected by this installed release; do not guess a
    legacy wrapper or silently equate identifiers from different versions.
  - [x] Add immutable typed data and local QFLAG=2 / PEFLAG=1 PI components,
    explicit modified-Euler stages, conditional anti-windup, state-only voltage-
    dip freezing, algebraic refresh and fault/nonfault priority selection.
  - [x] Cold-repeat two native local-PI profiles; retain prepared Pref (VAR5),
    filtered Pe, Q and limit inputs and both reference-event sides. The local
    block must not confuse the external Pref step with the ramp output.
  - [x] Pass 16 focused checks, 248 plugin-core tests (six optional skips),
    and 1,276 SMIB/catalog/staggered-solver tests (one diagnostic skip), with
    zero failures/errors. Audit the tested source snapshot against `639e50c90`;
    keep this component foundation distinct from full-model acceptance.
  - [x] Implement the typed eight-state controller: measurement (Pe filter also
    active for PEFLAG=1), reference ramp before the power PI, QFLAG 0/1/2,
    current limits/FRT priority, blocking, holds, compensation and bypasses.
    Correct Vext-before-Vbias limiting and independently bounded integral-state
    anti-windup using native branch/saturation discriminators; reuse the corrected
    PI rule. Retain both reference-event sides and the native callback discrepancy.
  - [x] Add ten new cold-paired branch/timestep replay datasets and typed
    two-step-size equilibrium, three-cycle fault and trip/reinitialization tests.
    These are not complete independent coupled trajectory acceptance.
  - [x] Pass 47 focused tests, 266 plugin units (six optional skips), and 1,284
    SMIB/catalog/staggered-solver tests (one diagnostic skip), zero failures/errors.
    Audit 22 scoped sources/resources and 2,021 baseline Java/POM files against
    `890e887f0`, including its committed static-load SMIB regression and excluding
    the unrelated untracked JAX comparison.
  - [x] Add exact standard-name native reference REECE (eight ICONs/79 CONs) catalog,
    parser and deferred active REGCA/REGCB attachment, before deferred REPCA.
    Reject duplicate/conflicting controls, inactive/missing/wrong hosts, malformed
    values/cardinality and guessed release aliases/wrappers. Preserve GNET/removal
    behavior and verify machine-base conversion on both converter types.
  - [ ] Finish release-specific REECE1/REECEU1 import layouts, reference callback
    parity, independent coupled native/PowerWorld trajectories and remaining
    active-boundary, lifecycle, speed/base and time-constant-policy profiles.
    Native reference import raises family runtime presence to 22/27, not full alias
    coverage or complete model acceptance.
    See [REECE local-PI foundation](benchmarks/reece-local-pi-foundation-2026-10-18.md).
    The [typed runtime and limiter correction](benchmarks/reece-runtime-2026-10-18.md)
    supersedes the foundation's inactive-limit-only anti-windup assumption.
    See [standard-native import and exact release boundary](benchmarks/reece-native-import-2026-10-18.md).
- [ ] Implement REPCCU and HPLNTD with the required single-/multi-aggregate
  measurement/dispatch interfaces and explicit native-version schemas.
  - [x] Freeze native HPLNTDU bus-wrapper and REAXDU per-unit contracts, including
    35/61/13/45 and 9/8/2/7 allocations, plant/device bases and weighted dispatch.
    Implement immutable typed data and multi-unit aggregation with fixed initial
    references, trip reweighting, explicit ignore/no-command and pause views.
  - [x] Cold-repeat seven four-bus/two-unit profiles (fourteen runs), retain all
    raw channels/history/event sides, compare six algebraic interface profiles
    and preserve the explicit-base pause counterexample with its matched
    no-intermediate-pause control. This does not raise runtime presence.
    See [hybrid aggregation foundation](benchmarks/hplntd-aggregation-foundation-2026-10-18.md).
  - [x] Validate the foundation on Java baseline `40eb7b164`: 41 focused,
    356 plugin-core (six optional skips), and 1,429 SMIB/model/solver tests
    (one diagnostic skip), zero failures/errors. Audit fourteen native runs,
    all raw interface rows and the explicit-base pause counterexample;
    keep HPLNTD runtime presence missing rather than count data as a model.
  - [x] Implement REAXDU's two-state command-lag kernel with explicit modified-Euler
    stages, immutable named states and output-only limits. Replay all four unit
    states in eight independent profiles; add four cold-paired limit/minimum-time
    datasets. Ordinary-time residuals reduce approximately fourfold at half step.
    This is recorded-input kernel evidence, not attached runtime acceptance.
    Final gates: 26 focused, 362 core-unit (six optional skips), and 1,436
    SMIB/model/solver tests (one existing diagnostic skip), zero failures/errors.
    See [downstream lag evidence and gaps](benchmarks/reaxd-lag-kernel-2026-10-18.md).
  - [ ] Implement HPLNTDU's thirteen-state kernel and complete REAXDU's interface,
    exact production import/attachment and stage-coherent multi-unit coupling;
    verify all remaining selectors, limits, lifecycle and actual SMIB responses.
    - [x] Add a dedicated thirteen-state kernel, nine equation/boundary tests and
      ten matched-method replay profiles. Six new cold-paired profiles expose
      measured frequency, reference steps, voltage freeze and fault recovery.
      An independent scalar test proves reference-slew STORE behavior; the accepted
      host clock resolves the fine-step recovery mismatch. Default modified Euler
      is unchanged. Tight per-channel bounds and the previous failing diagnostics
      are retained; runtime presence does not increase.
      Final gates on baseline `08c49944f`: 51 focused, 371 core-unit (six optional
      skips), 1,455 SMIB/model/solver (one diagnostic skip), zero failures/errors.
      See [bounded plant-kernel evidence](benchmarks/hplntd-kernel-2026-10-18.md).
    - [x] Add exact production wrapper-decoder APIs, an immutable two-pass ownership
      plan and a read-only plant-base/Mvar measurement boundary. Verify all 17
      reviewed input contracts, duplicate/ignored links, source order, branch
      directions, live plant bases, transformer ports and the missing-branch rule.
      This is preflight/measurement support, not main-importer or runtime attachment.
      Gates: 65 focused, 379 core-unit (six optional skips), and 1,469 selected
      SMIB/model/solver tests (one diagnostic skip), zero failures/errors.
      See [binding/measurement evidence and runtime TODOs](benchmarks/hplntd-binding-boundary-2026-10-18.md).
    - [x] Add typed shared runtime composition for bounded REGCA1/REECA1 hosts,
      once-per-stage plant/lag advancement, deterministic command publication,
      ownership guards, trip/rejoin reweighting and whole-group reinitialization.
      Eight small-testbed tests include two-step-size command comparisons across
      all seventeen states and eight network/output channels, plus a three-cycle
      fault. Cross-method reference-slew residuals remain explicit; main import,
      alternate host modes and independent fault acceptance remain open.
      Gates: 52 focused hybrid, 379 core-unit (six optional skips), and 1,480
      selected SMIB/model/solver tests (one diagnostic skip), zero failures/errors.
      See [typed runtime evidence and remaining gaps](benchmarks/hplntd-typed-runtime-2026-10-18.md).
    - [x] Connect exact HPLNTDU/REAXDU wrappers through the main importer and
      builder, preserving bus ownership, order independence, per-generator
      preprocessing and atomic command-port preflight. Add named-state monitoring,
      ten import tests and imported native command comparisons without changing
      reference data or tolerances. Runtime presence reaches 27/27 bounded family
      paths, not full source-name/selector/host or independent fault acceptance.
      Final gates: 113 focused, 379 core-unit (six optional skips), and 1,490
      selected SMIB/model/solver tests (one diagnostic skip), zero failures/errors.
      See [import evidence and remaining work](benchmarks/hplntd-import-2026-10-18.md).
    - [x] Add a portable cold-repeat fault probe and complete two-timestep
      state/network diagnostics, retaining every raw history/event sample.
      Correct the adapter's frequency rebase at the converged event endpoint
      with fail-before phase-reference, preserved-output and disabled-event tests.
      Native instantaneous-current and frequency-callback parity remain explicitly
      unaccepted; no current delay or reference retuning is used to fit them.
      Final gates: 47 focused, 379 core-unit (six optional skips), and 1,493
      selected SMIB/model/solver tests (one diagnostic skip), zero failures/errors.
      Two legacy bounded ANDES wind envelopes are revised only after source-
      controlled frequency-on/off A/B proof; exact/native limits are unchanged.
      See [fault diagnostics, corrected defect and remaining gaps](benchmarks/hplntd-fault-event-frequency-2026-10-18.md).
    - [x] Add exact token-only disabled-control/zero-gain reference probes and
      offline artifact negative controls. Keep all states, outputs and duplicate
      samples; require cold equality at two steps and unchanged frozen fault
      hashes after common-runner refactoring. This is a source-discriminator
      slice, not new runtime support. See [observations and implementation
      implications](benchmarks/hplntd-control-discriminators-2026-10-18.md).
      Fourteen profiles/28 cold runs are byte-identical by pair; all seven coarse
      exploratory outputs and both frozen fault CSVs are unchanged. Gates on
      baseline `7866eb36e`: ten probe/audit and six wording checks, 379 core-unit
      (six optional skips), and 1,493 selected SMIB/model/solver tests (one
      diagnostic skip), zero failures/errors. Production equations are unchanged.
    - [x] Implement disabled-path held commands and unused storage in
      initialization, kernel and coordinator, including PF's shared active
      measurement, both integration stages, algebraic bypasses and group reseeding.
      Add sixteen two-step native state/command replay and imported coupled SMIB
      profiles; preserve explicit cross-method/filter residuals and raw unused-VAR
      nondeterminism. See [disabled-control implementation](benchmarks/hplntd-disabled-controls-2026-10-18.md).
      Final gates: 36 focused, 382 core-unit (six optional skips), 1,499 selected
      SMIB/model/solver tests (one diagnostic skip), and 22 offline checks,
      zero failures/errors. Thirty-two raw runs support sixteen cold pairs;
      fourteen are byte-identical and two differ only in explicitly audited
      unused reactive VAR storage. No existing reference limits are changed.
    - [x] Implement feasible enabled proportional-only equilibrium, signed
      inverse deadbands, all four active feedback/feedforward combinations and
      PF measurement without electrical feedback. Retain zero integral storage,
      strict feasibility checks and group reseeding. Add 24 two-step state/command
      replay and bounded imported SMIB profiles with 48 raw runs, byte-identical
      within each cold pair.
      See [equations, evidence and counterexamples](benchmarks/hplntd-proportional-equilibrium-2026-10-18.md).
      Final gates on `bdd6d02ba`: 47 focused, 393 core-unit (six optional skips),
      1,514 selected SMIB/model/solver tests (one diagnostic skip), and 26 offline
      checks, zero failures/errors. Four raw diagnostic runs remain excluded
      from native acceptance; existing trajectory bounds are unchanged.
    - [x] Localize the initialized voltage-reference discrepancy with sixteen
      two-step direct-reference profiles and 32 raw runs, identical within each
      of the sixteen cold pairs.
      Twelve voltage diagnostics prove native raw-VAR bypass, not initialized
      limit expansion. Preserve the published clamp with a failing mutation
      control. Add four Q-upper/PF-lower state-replay and bounded coupled
      trajectory profiles; preserve the runner's earlier default hashes.
      See [direct-reference evidence](benchmarks/hplntd-reference-limits-2026-10-18.md).
      Final gates: 41 focused, 393 core-unit (six optional skips), 1,519 selected
      SMIB/model/solver tests (one diagnostic skip), and 32 offline checks,
      zero failures/errors. Four fresh default runs preserve earlier CSV hashes;
      production model equations and existing trajectory bounds are unchanged.
    - [ ] Resolve published-diagram versus native voltage-boundary compatibility
      without silently removing limits, and the rejected native nonzero-deadband/
      zero-Ki initialization. Independently benchmark voltage references, remaining
      active limits, branchless/alternate-host, switching and lifecycle profiles.
  - [x] Freeze the installed standard-native REPCC contract: 35 mixed-type ICONs,
    57 CONs, thirteen states, 36 VARs. Add typed data and algebraic equilibrium
    initialization, plus eight cold-paired native foundation profiles. Preserve
    the source-list REPCCU mapping without inventing a standard-name alias.
    See [REPCC foundation](benchmarks/repcc-foundation-2026-10-18.md).
  - [x] Validate the bounded foundation: 50 focused checks, 325 plugin-core
    tests (six optional skips), 1,377 SMIB/model/solver checks (one existing
    diagnostic skip), zero failures/errors. Audit 22 owned files, 2,074 baseline
    Java/POM files and sixteen accepted native runs with eight identical cold
    pairs. Preserve rejected PF/alias probes; do not raise runtime coverage.
  - [ ] Implement the REPCC dynamic kernel, freeze/recovery and slew semantics,
    shunt switching/timers, exact import and downstream reference routing; add
    two-step coupled SMIB disturbance/lifecycle evidence. Foundation data and
    initialization alone do not raise coverage; the bounded native runtime below
    advances runtime presence to 26/27 without claiming full conformance.
    - [x] Add the thirteen-state kernel, explicit modified-Euler stages,
      conditional PI non-windup, reference slew, command-rate limits and
      filtered-voltage freeze/recovery. Preserve native observation coordinates.
    - [x] Record fourteen two-step native discriminator datasets (28 cold runs)
      and compare every raw sample of all thirteen states and three observable
      outputs. Ten pairs are byte-identical; four differ only in a bounded
      inactive-timer residue, with both raw files retained.
      See [REPCC kernel evidence](benchmarks/repcc-kernel-2026-10-18.md).
    - [x] Validate on plugin `8342c05fe` with an isolated archived core jar:
      56 focused checks, 334 plugin-core tests (six optional skips), 1,379
      SMIB/model/solver checks (one existing diagnostic skip), zero failures or
      errors. Audit 33 owned files, 2,079 baseline Java/POM files, all 28 native
      runs and fourteen complete raw-row replay traces.
    - [x] Add the ordered ten-slot shunt state machine, strict two-band timers,
      overlapping breaker delays, latched commands, safe unused slots and
      source-correct capacitor discharge lockout. Match eight two-step native
      event/timer datasets; preserve two additional discharge-discrepancy
      datasets without accepting premature native reclosure. Twenty cold runs
      retained. PowerWorld remains non-activating, not lockout acceptance.
      See [REPCC shunt evidence](benchmarks/repcc-shunts-2026-10-18.md).
    - [x] Validate the shunt slice on `b5c27de3f`: 84 focused, 344 plugin-core
      checks (six optional skips), 1,386 SMIB/model/solver checks (one existing
      diagnostic skip), zero failures/errors with the isolated accepted engine.
      Runtime coverage remains 25/27; no shunt network attachment is claimed.
    - [x] Add the typed physical network boundary: signed/end-resolved branch
      current and P/Q, independent remote/frequency fallbacks, exact MVA-base
      conversion, full-G+jB shunt actions and persistent matrix changes.
      Fix the discovered cached-admittance rebuild bug in core `38e269ecf`;
      all 2,973 engine tests pass. Six direct-query datasets have twelve cold
      runs (six byte-identical pairs), separate from deferred controller plot VARs.
      See [REPCC physical boundary](benchmarks/repcc-network-boundary-2026-10-18.md).
      This typed API does not yet supply automatic runtime attachment/import.
    - [x] Validate the physical boundary with 42 focused checks, 344 plugin-core
      tests (six optional skips) and 1,394 registered SMIB/model/solver checks
      (one existing diagnostic skip), all with zero failures/errors. Verify all
      physical statuses/Y increments for eight recorded-voltage profiles and
      preserve/recheck the engine fix and both old/new isolated dependencies.
    - [x] Add exact 35-ICON/57-CON standard import, deferred dedicated
      REGCA1/REECA1 attachment, absolute-to-incremental command routing,
      modified-Euler stage guards, immutable named values and inactive-owner
      holds. Eight typed equilibrium modes remain stationary. Unsupported
      automatic MSS/disabled-loop initialization is rejected, not substituted.
      Source REPCCU remains unsupported rather than guessed as an alias.
    - [x] Cold-repeat flat and three-cycle fault SMIB profiles at 0.5/0.25 ms:
      eight native runs, all raw history/event-side rows and all thirteen plant
      states compared with separate tightened channel bounds. Preserve the
      load-flow tolerance discriminator and remaining numerical floors.
      See [REPCC native runtime](benchmarks/repcc-native-runtime-2026-10-18.md).
    - [x] Validate on committed plugin baseline `c8f866351`: 102 focused checks,
      344 plugin-core tests (six optional skips) and 1,404 registered
      SMIB/model/solver checks (one existing diagnostic skip), zero failures or
      errors. Audit all eight native runs and every history/event-side row;
      preserve seven earlier sealed archives and the frozen companion core.
    - [x] Separate enabled-MSS continuous initialization from physical device
      setup. Compare all thirteen initial states/references against ten frozen
      native profiles; activate typed coupled switching at both steps and
      check endpoint clocks, persistent Y changes and idempotence.
      See [enabled initialization](benchmarks/repcc-enabled-initialization-2026-10-18.md).
      This is not enabled-MSS import or independent coupled transient acceptance.
    - [x] Validate enabled initialization on `a6b69e592`: 29 focused,
      344 plugin-core (six optional skips), and 1,406 SMIB/model/solver tests
      (one diagnostic skip), zero failures/errors. Preserve the failing-before
      discriminator, unchanged reference hashes and isolated core dependency.
    - [x] Retain original per-device G+jB through fixed-shunt import, including
      initially open devices, and wire enabled-MSS standard REPCC attachment.
      Reject missing/changed provenance and ambiguous identities before taking
      the plant port. Activate both opening and insertion with nonzero G at
      two steps and verify persistent matrix increments.
      See [enabled-switching import](benchmarks/repcc-switching-import-2026-10-18.md).
      Transient device metadata is not a persistent core serialization feature.
    - [x] Validate enabled import on `41922fc74`: 77 focused, 347 plugin-core
      (six optional skips), and 1,412 SMIB/model/solver tests (one diagnostic
      skip), zero failures/errors. Retain the strict-import failure-before-fix,
      tested sources, generated inventory and unchanged independent references.
    - [x] Correct the missing accepted-origin sample in the solver adapter.
      Verify time-zero timer states, zero-delay physical actions without
      compensation absorption, hybrid-current identity and inactive owners.
      Compare all raw rows of four fast-band coupled profiles at two steps;
      retain four unaccepted slow-band profiles with a clock-only discriminator.
      See [origin and coupled switching](benchmarks/repcc-switching-origin-2026-10-18.md).
    - [x] Validate origin/coupled switching on `674583ce0`: 54 focused,
      347 plugin-core (six optional skips), and 1,417 SMIB/model/solver tests
      (one diagnostic skip), zero failures/errors. Preserve failing-before
      diagnostics, all sixteen paired/status traces and exact binary32-clock
      audit. Keep slow-band trajectory and complete REPCC acceptance open.
    - [ ] Complete slow-band clock/trajectory acceptance, independent zero-delay
      and generic-solver startup coverage, independent
      discharge-lockout evidence, remaining initialization
      and selector/limit profiles, independent coupled SMIB acceptance and the
      declared integration-history/precision/timer gaps.
- [ ] Implement REGFMB1, REGFMC1 and REPCGFMC1 as exact grid-forming converter
  and plant models. Do not alias them to REGFMA1 or REPCA1.
  REGFMB1U now has a loadable adapter and bounded native SMIB evidence; its full
  acceptance remains open. REGFMC1U now has an eleven-state kernel, exact native
  import/network adapter and bounded coupled SMIB evidence. REPCGFMC1U now has
  dedicated four-output plant routing and bounded coupled SMIB evidence; full
  acceptance remains open.
- [ ] Complete REECD beyond its new native Paux active-path profiles, close the
  independent WT12A1U_B trajectory gap and establish native conformance
  for the six currently bounded ANDES links.
- [ ] Expand the evidence inventory to retired/legacy and vendor-package models
  required by supplied cases, keeping those separate from approved-list counts.
- [ ] Audit the remaining non-renewable PDF sections against actual source rows;
  the remaining renewable gaps are not a claim that all other sections are complete.

See [all source rows, actual implementation gaps and profile scopes](renewable-model-evidence.md).
Installed reference simulator documentation contains entries for the missing families,
sometimes under updated standard names. Source-list names are not sufficient
evidence to infer their current native/USRMDL allocations or aliases.

## Exact typed PSS1A playback follow-up

- [x] Generate exact `Stabilizer_PSS1A` PowerWorld references at 0.5 and 0.25 ms,
  twice cold per step, without automatic parameter correction or gain fitting.
- [x] Audit actual model identity and every declared parameter before and after
  initialization; retain the rejected native fourteen-value DYR import and
  stalled export diagnostics as failures, not accepted references.
- [x] Compare all regular endpoints, isolate the controller by exact recorded
  speed playback, and derive the second-order filter observation coordinate.
- [x] Add permanent two-step playback checks for six published states and Vstab,
  fixed fingerprints, negative mutation guards and pure repeated observations.
- [x] Finish the final registered regression: 1,160 SMIB/model tests with zero
  failures/errors and one existing diagnostic skip; 130 plugin-core unit tests
  with zero failures/errors and six optional skips. Scoped tested sources match
  byte-for-byte. Preserve the empty-selection diagnostic as non-evidence.
- [x] Reproduce the original full CSV through a frozen-runner/spec cold replay;
  preserve the exact typed-model audit and raw artifacts in the private archive.
- [x] Seal and hash-verify 2,656 private evidence files; prepare the tested
  scoped commit without staging concurrent renewable integration changes.
- [ ] Resolve full-network SMIB differences and exact event-post observations;
  do not replace this requirement with the passing controller replay.
- [x] Independently exercise both output-limit directions and upper/lower
  voltage cutouts with exact PowerWorld speed/voltage play-in at both steps,
  twelve cold runs, unchanged exported parameters and active-branch guards.
- [x] Correct the test observation to compare the unclipped published LL2
  coordinate; retain production equations and all previous baseline values.
- [x] Add cutoff-boundary, zero-disable, continued-state-evolution and immutable
  observation checks. Distinguish local exact-boundary guards from oracle traces.
- [x] Pass the final active-branch snapshot: 1,169 SMIB/model tests with one
  diagnostic skip, 133 plugin-core units with six optional skips, zero
  failures/errors. Include committed renewable integration `94321b10c`.
- [x] Retain the shared-checkout gate's four concurrent GENSAL test failures;
  do not relax them or mislabel that gate green. The isolated snapshot excludes
  uncommitted GENSAL edits, not the committed renewable implementation.
- [x] Seal and hash-verify 1,639 active-branch evidence files and prepare the
  tested scoped commit, preserving all unrelated GENSAL work.
- [ ] Close remaining input-selector/lifecycle profiles and native PSS1A
  dialect verification before complete model acceptance.

See [exact typed PSS1A playback evidence](benchmarks/pss1a-exact-powerworld-playback-2026-10-17.md).
The [active-limit/cutout follow-up](benchmarks/pss1a-active-limits-2026-10-17.md)
retains the rejected lower-only validation probe and the shared-checkout
GENSAL failures separately from the accepted PSS1A profiles.

## What current source proves

- [x] Catalog and runtime paths for the Texas2k model families. The current
  catalog's `texas2kModels()` contains 25 curated family descriptors. The
  rediscovered source ZIP's six original DYR files have now been rescanned:
  16,311 records across 17 distinct native types before GNET/MODREMOVE. Their
  per-case totals (2,223, 2,223, 2,965, 2,965, 2,965, 2,970) reproduce the
  reviewed inventory. Attachment accounting is verified separately below;
  neither inventory nor attachment proves independent dynamic acceptance.
- [x] May 2026 approved native reference simulator exciter implementation coverage: 64/64
  source-list rows, as asserted by `DynamicModelCatalogTest`.
- [x] All 24 approved governor rows with native reference simulator mappings have runtime
  implementations. This does not imply every profile has independent parity.
- [x] Approved stabilizer implementation mappings are present, including
  PSS2A/B/C and PSS3B/C through PSS7C. Source-list aliases must not be counted
  as separate verified equations.
- [x] GENQEC and GENQEJ implementations, exact native allocation tests, named
  states, and registered native trajectory comparisons are committed. The
  earlier GENQEC PowerWorld q-axis discrepancy is explicitly discriminated by
  `GenqecIndependentSmibConformanceTest`: the older reference's Edp derivative
  uses Xd'' where the published equation uses Xq''. The published runtime
  equation is retained.
- [x] Native simulator generator, exciter, governor, limiter, renewable, and
  protection checkpoints are embedded in the public tests. Full simulator
  scripts, raw trajectories, and manifests reside in the private verification
  archive according to the current repository policy.
- [x] IEEE39 matched-settings staggered-Euler comparison: all 246 scored flat
  and fault channels pass at 0.25 ms. See
  [the benchmark report](benchmarks/ieee39-benchmark-2026-10-15.md).
- [x] Default modified-Euler and zero-time controller audits have separate
  reports with their remaining differences. See
  [default integration evidence](benchmarks/ieee39-modified-euler-2026-10-15.md)
  and [zero-time audit](benchmarks/dynamic-zero-time-audit-2026-10-16.md).

## Current verification slice: native checkpoint integrity

The old `EmbeddedNativeTrajectoryValues.manifest()` calculated a hash from the
same embedded CSV that its callers hashed. The equality was tautological and
could not detect accidental edits to reference values.

- [x] Freeze independent expected SHA-256 strings for all 63 native checkpoint
  datasets at `dcea9ab30`.
- [x] Keep the existing manifest-hash callers compatible while returning the
  stored fingerprint instead of hashing the tested bytes again.
- [x] Check all 63 datasets and reject a one-byte mutation of each.
- [x] Reject unknown dataset names rather than inventing an expected digest.
- [x] Focused integrity, GENQECU, and GENQEJ tests pass.
- [x] Complete the registered reactor regression and record its exact results.
- [x] Prepare the scoped commit after all available gates finish; record skipped
  private-data gates explicitly.

Normalization is explicit: UTF-8 lines joined with LF and no final newline.
These are fingerprints of embedded checkpoints, not full raw simulator
manifests. They preserve the current baseline and do not independently prove
its simulator provenance or accuracy. The private source artifacts remain
necessary for cold regeneration and provenance review.

Validation command: `mvn -q test -Pextended-tests --fail-at-end`.
The completed 2026-10-17 run exits zero: 2,643 tests, 2,535 passed,
108 skipped, zero failures/errors. Counts include the separate untracked
WECC240/JAX test present in the shared checkout; that file is outside this
slice. Only reports written after this run started were counted.

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ipss.plugin.core | 128 | 0 | 0 | 4 |
| ipss.plugin.3phase | 468 | 0 | 0 | 28 |
| ipss.test.plugin.core | 2,047 | 0 | 0 | 76 |

`ReferenceArtifactManifestIntegrityTest` passes all four tests. The registered
suite includes this class. GENQECU and GENQEJ focused comparisons also pass.
The topology manifest test passes; its private simulation test skips. All
three six-case smoke tests skip, and the strict private gate is disabled.
Full output is retained locally at
`target/dynamic-reference-integrity-regression.log`.

## Texas2k system acceptance

Availability correction, 2026-10-17: the extracted OneDrive directory is empty,
but the original `Texas2k_series24_cases_with_dynamics.zip` exists in its parent.
The earlier check was too narrow. Its raw SHA-256 is
`a66623bbf3b3faf910f494910f08cca9620066f8964c990aa04ea50b7106ca4e`.
All 27 original RAW/DYR/IDV files (six cases) are now staged under
`target/texas2k-source-20261017`; each extracted file's raw hash matches its ZIP
entry and is recorded in the local `input-sha256.json`. Other formats were not
mixed into the native input set. The source archive and external folder are
unchanged.

The 27 verified originals are now also under the default ignored private input
location. Eleven ordinary Case 1 test paths now use the provided unversioned
version-34 RAW instead of requiring an absent derived `_v36.RAW`. No original
is renamed or relabeled; the separate staged diagnostic keeps its custom-input
contract. Fresh loading, six-case smokes and strict-flat results are recorded in
[the original-source report](benchmarks/texas2k-original-source-2026-10-17.md).

**Version boundary:** the stationary-flat results below apply to the tested
plugin `3453ae674` convention, including the newer-core integration snapshot.
Subsequent `511618835` retains a native PFLAG speed multiplier and independently
demonstrates a nonzero startup derivative in supplied wind profiles. The
stationarity assertion is therefore not yet a valid current-main acceptance
contract. Do not suppress that derivative or fit references to recover a flat
trace. Reconcile initialization and the no-event gate with native evidence;
the [supplied-reference investigation](benchmarks/texas2k-native-reference-comparison-2026-10-17.md)
records the newer failures and open full-window comparison.

- [x] Restore the six original RAW/DYR cases and both preparation IDVs into
  the private input location; verify hashes before simulation.
- [x] Locate the original source ZIP, stage only native inputs separately, and
  verify all 27 extracted files against their archive entries.
- [x] Rescan the six recovered DYR files and reproduce the reviewed 16,311
  records, 17 types and per-case counts before preparation.
- [x] Reaudit every applicable DYR record after GNET/MODREMOVE: attached,
  intentionally removed, rejected, unsupported, or missing target.
- [x] Rerun all six short flat, common three-cycle-fault, and generator-trip
  smokes on the current production solver.
- [x] Enable and run all six strict one-second flat gates at 1/240 s with
  voltage drift <= 1e-6 pu and speed drift <= 1e-7 pu.
  All six pass on the corrected core. The newer merged-core check observes
  maximum voltage drift 1.38e-14 pu and zero reported speed drift; the preceding
  guarded baseline observed 3.13e-12 pu and 5.67e-14 pu. Historical 0/6 strict-flat results
  no longer describe this current source/code combination.
- [ ] Rank the first and largest bus-voltage, generator-speed, and controller
  state differences. Revisit the historically leading REECA1/REPCA1 devices
  only after confirming they still lead on the current equations.
- [x] Run the reviewed 15-event topology fault matrix. Record voltages near
  the fault, generator speed/angle, P/Q, EFD/PM, and controller states.
- [ ] Resolve severe GFM/weak-grid post-fault responses before calling the
  faults physically accepted. After enforcing convergence with sufficient
  iteration headroom, the preceding guarded core gave Case 6 Bus1016 1.681472 pu
  and Case 5 Bus1011 1.030318 pu at 0.5 s. Integrating newer core shunt/generation
  corrections gives 1.512620 pu and 1.064186 pu respectively; neither is an
  independent physical acceptance result. The preceding Iolim-only run gave
  1.681707 and 1.121556 pu respectively but ignored exhausted network stages.
  Rank first divergent states against an independent reference and repeat at
  a reduced timestep; do not fit parameters or bless loose smoke bounds.
  Historical h/2 and h/4 Iolim runs also ignored exhausted stages; they do not
  certify the newly guarded trajectories. Fresh h/2 matrix runs complete both
  events at 500 iterations. The h/4 matrix rejects Case 5 at 0.0958333 s;
  voltage-only probes complete identically at 800/1,600 iterations (up to 614
  recorded corrector iterations), but h/4 machine/state acceptance remains open.
  Case 6 still ends at 1.684953 pu at h/4. See the [Iolim timestep investigation](benchmarks/regca-iolim-direction-2026-10-17.md)
  and [convergence follow-up](benchmarks/network-convergence-propagation-2026-10-17.md).
- [ ] Obtain matched native reference simulator or PowerWorld trajectories for complete
  stacks. Add ANDES comparisons where its topology and parameterization match.
- [ ] Compare every channel at aligned event sides and matched timesteps;
  halve the step to distinguish integration and equation discrepancies.
- [ ] Require all six system cases to meet independent trajectory limits
  before marking the Texas2k milestone complete.

## Remaining model work and release gates

**Core reliability slice:** at baseline core `e886019ce`, all three
modified-Euler calls in `AbstractDStabSolver.nextStep()` ignore the boolean
returned by `DStabSolverImpl.networkSolutionStep()`. Iteration-budget exhaustion
could fail to propagate to the outer step result. That baseline's green execution
gates did not certify network convergence. The correction now stops all three
stages and both simulation entry points, invalidates partial state and requires
reinitialization. Immutable last-solve diagnostics expose time, stage, iteration
count, leading bus and componentwise voltage change (not a KCL residual).

- [x] Implement failure propagation and failing-before regressions; complete
  all 2,918 core tests. Keep core's general limit 20 and tolerance 1e-4 unchanged.
- [x] Diagnose actual rejected stages on the tiny REGCA1 and Texas2k cases;
  establish identical complete exports at independently sufficient budgets.
  Give the reference simulator adapter a configurable 500-iteration ceiling, without changing
  model parameters, tolerance or timestep.
- [x] Complete the coupled full plugin regression for core `12c231df5`.
  All 2,730 registered plugin tests finish: 2,641 passed, 89 skipped, no
  failures/errors; all 2,918 core tests pass without skips. Archive exact binary,
  source, settings, fresh XML and trajectories before restoring private opt-ins.
  Its first run exposed three formerly hidden failures in synthetic tests.
  The REECB1 fixture now has an explicit voltage-forming grid. The two deep
  aggregate faults still fail at 500 iterations; exact passive-network reduction
  proves no symmetric root at their rejected states under the current boundary.
  Their old recovery literals are withdrawn, with explicit analytical rejection
  guards instead. The 23 focused fixture/convergence tests pass. These guards
  do not establish native low-voltage behavior or synthetic recovery acceptance.
  The six-case smokes, strict flats and 15 topology faults pass with enforcement.
  All fifteen complete fault exports repeat byte-for-byte across both guarded
  full runs. See the [coupled slice report](benchmarks/network-convergence-propagation-2026-10-17.md)
  for changed historical baselines, module totals and explicit acceptance limits.
- [ ] Verify native converter low-voltage/angle handling on the symmetric
  five-/thirty-plant deep-fault profiles; their recovered trajectories are not
  accepted. Do not loosen convergence tolerance or silently advance failed stages.
  The five-plant native counterpart has now been run twice with exact exported
  dynamic parameters and initial voltages within 5.7104e-8 pu: initialization
  passes, but both runs report 211 network non-convergences (0.0525-0.1775 s).
  The identical exported recoveries are rejected by the quality gate, not treated
  as a reference to fit. This corroborates the synthetic domain problem; exact
  low-voltage handling and the thirty-plant counterpart remain open. See the
  [native failure evidence](benchmarks/network-iteration-lifecycle-2026-10-17.md).

Current REGCA1 evidence correction: the preceding native zero-Q baseline has
95 network non-convergence diagnostics despite successful export. It is retained
as diagnostic history, not accepted transient evidence. A fresh unchanged-model
baseline with a 200-iteration network budget is twice-repeatable, has no
recognized failures and is byte-identical at a 400-iteration budget. The public
final-recovery test now uses those clean values at unchanged bounds. The
[network-quality report](benchmarks/regca-network-run-quality-2026-10-17.md)
records the permanent log/CSV quality gate and remaining full-transient errors.

- [x] Reject recognized native convergence/initialization failures independently
  of API return codes, export status and cold repeatability.
- [x] Cold-regenerate and quality-check the zero-Q baseline at 200 and 400
  network iterations; preserve the original failed-network logs and CSV.
- [ ] Audit original log quality for all historical native reference profiles;
  embedded hashes and clean new runs do not certify old artifacts.
- [ ] Resolve native REGCA1 Khv/Accel/Iolim versus WECC network-flowchart
  semantics before replacing the finite-gain runtime boundary. Keep PowerWorld's
  distinct convention separate; do not mutate dynamic states during reads.
- [x] Implement the shared opt-in algebraic network-iteration lifecycle needed
  for a correct converter boundary: immutable stage/time context, one explicit
  update per network iteration, identity deduplication, activation rediscovery,
  and failure-safe completion. Ten new core lifecycle tests plus eleven prior
  convergence tests pass; the complete core suite passes 2,928 tests with no skips.
- [x] Complete the coupled lifecycle regression and prepare scoped commits.
  Core commit `95051f797` on `codex/network-iteration-lifecycle-20261017` is based
  directly on tested `12c231df5`; all 2,928 core tests pass. The isolated plugin
  run passes 2,643 tests with 89 skips and no failures/errors, including six-case
  smokes, six strict flats and fifteen complete byte-identical fault exports.
  All 688 archived artifact hashes are rechecked. Private opt-ins are restored.
  The 34 focused checks include exact unchanged imported flat/fault endpoints
  through a no-op participant. No production converter opts in yet;
  this does not close its HV/low-voltage modeling gaps. See the
  [lifecycle report](benchmarks/network-iteration-lifecycle-2026-10-17.md).
- [x] Integrate lifecycle commit `95051f797` with core `124bca40f` and rerun
  combined core/plugin/system gates. Merge `830b47d0d` preserves both parents;
  all 2,939 core tests pass. Its
  first full plugin run finds a real SVSMO1T2 benchmark load-law mismatch.
  The native zero-percentage CONL profile retains constant MVA, but the test used
  constant impedance. The explicit load-law correction and perturbation guard
  pass focused checks with tightened diagnostic bounds. The full rerun exits zero:
  2,641 passes, 91 skips, no failures/errors. Six-case smokes, six strict flats
  and fifteen faults execute; all fifteen fault traces repeat exactly after the
  benchmark correction. All 1,549 archived artifact hashes are rechecked;
  enabled settings are preserved and private opt-ins restored.
  See the [combined integration report](benchmarks/network-iteration-integration-2026-10-17.md).
- [ ] Integrate newer core `9893d5bae` and plugin `511618835`/`be8bd790c`,
  reconcile the native wind startup/no-event contract and rerun latest-source
  combined acceptance. Those concurrent commits were not in the clean tested
  baseline above; do not reuse its green result for the newer source tree.
- [ ] Resolve SVSMO1T2 native point-control initialization and remaining
  transient discrepancies. Two cold native runs reproduce the original full
  reference exactly and pass run-quality checks, but the corrected comparison
  still has 0.152376-pu voltage and 0.165174-pu susceptance-state errors. Tightened
  diagnostic non-regression bounds do not make this model trajectory-verified.
- [ ] Establish a model-specific joint convergence/admissibility criterion
  before enabling the accumulating HV boundary. The lifecycle preserves the
  old voltage-update rule and does not yet expose participant convergence vetoes;
  near a turning point, a small update is not proof of a solved boundary.
- [x] Correct the independently localized Iolim sign/coordinate mismatch:
  native Iolim=-0.25 clips **positive generated current at +0.25 pu** during the
  low-voltage fault; the current implementation instead clips negative current.
  The generated-current ceiling is now -Iolim, applied at the algebraic output
  without clamping the lag state. Two clean cold runs at each of two steps and
  the opposite-direction capacitive discriminator support the correction.
  See the [Iolim model-fix report](benchmarks/regca-iolim-direction-2026-10-17.md)
  for the failing-before evidence, 44 focused checks and full system regression.

Network-quality slice validation: 15 focused tests pass without skips. Full
registered regression passes with 2,722 tests: 2,633 passed, 89 skipped, zero
failures/errors. All six strict flat cases, six-case flat/fault/trip smokes and
fifteen topology faults pass their existing gates. All fifteen complete fault
traces are byte-identical to the preceding committed baseline. Enabled settings,
fresh reports and raw traces are archived; source opt-ins are restored to false.
This closes the new quality utility and clean final-recovery checkpoint work,
not the open Iolim fix, HV boundary or independent system acceptance.

Subsequent Iolim model slice: the full registered regression passes with 2,727
tests, 2,638 passed, 89 skipped and zero failures/errors, including all six strict
flat cases and fifteen topology faults. Six fault traces change; Case 5 weak-grid
final voltage increases to 1.121556 pu and Case 6 GFM remains severe at 1.681707 pu.
The earlier 1.081986 value is the preceding selector/quality-slice result, not
the latest Iolim result. No existing trajectory bound or physical parameter is
changed. The Iolim direction bug is closed; iterative HV management, joint limiter
profiles and independent physical system acceptance remain open.

Historical Iolim-only system-step evidence is preserved: two
additional eight-test runs at h/2 and h/4 pass their execution checks, but Case 6
still ends at 1.682565/1.684869 pu and Case 5 common-time differences grow rather
than converge. All three monitored speeds are compared at matching physical
times and sample kinds. Do not reuse the older pre-selector timestep study as
proof of current-system accuracy, or mark this open acceptance item complete.

1. Build a current per-model evidence inventory connecting each native model
   to exact schema tests, equation/limit tests, named-state coverage, reference
   simulator/version, repeatable artifact, and trajectory comparison. Recount
   the old PowerWorld artifact gaps from the current archive; the September
   count of 47 is not a current completeness claim.
   The [generator/governor inventory](generator-governor-evidence.md) now links
   all three approved generators and all 24 approved native governor mappings
   to focused tests, the exact public DYR fixture, embedded checkpoints, and
   registered comparison classes. It identifies 22 governor PowerWorld
   comparisons and two native reference simulator comparisons (IEEEG1D and TGOV3D).
   GENROU has ANDES evidence; GENQEC and GENQEJ have native reference simulator evidence.
   Reference presence does not establish every profile or full-system parity.
   The [exciter inventory](exciter-evidence.md) now links all 59 distinct native
   models behind the 64 approved exciter rows. Its local provenance audit
   identifies 17 historical PowerWorld input/specification gaps; AC1C and six
   additional exciter profiles now have current twice-cold numerical-repeatability
   evidence, without rewriting the historical manifests. The new
   [stabilizer inventory](stabilizer-evidence.md) links all fourteen implemented
   mappings behind fifteen approved source rows, explicitly distinguishing
   PSS1A equivalent play-in from exact-model references. Source PTI cells are
   preserved independently of implementation names. The renewable inventory,
   exhaustive profiles, simulator-version audits and remaining cold-regeneration
   provenance work are still open.
2. Close missing independent profiles for generators and controllers first.
   Exercise active limiters, zero-time branches, saturation selectors, base
   conversion, remote measurements, and lifecycle events separately where the
   common SMIB fault leaves them inactive.
3. Preserve native DYR cardinality. Keep any vendor dialect conversion in
   explicit separate benchmark inputs and identify both hashes.
4. For each model slice, consult the exact-version native library and the
   user-requested PowerWorld/WECC diagrams, implement, compare the first
   divergent derivative/state, and cold-regenerate twice. Do not fit parameters
   or loosen a bound to conceal a structural difference.
5. Run focused tests, applicable registered core/unit regressions and the
   complete `DynamicModelSmibTestSuite`. For each model, verify schema,
   initialization/equilibrium, the independent three-cycle fault or input-step
   response, active limits/selectors, timestep refinement and lifecycle paths.
   Do not launch full Texas2k dynamic gates for this revised scope. Record
   unavailable independent reference profiles explicitly as unexecuted.
6. Update this checklist and the generated support matrix as appropriate.
   Commit a scoped slice and report its hash, evidence, and remaining gaps.

Completion of the revised objective requires the full model evidence inventory
and each model's independent SMIB/profile gates to be closed. Full Texas2k
system acceptance is separate and not required here. A green SMIB suite does
not establish exhaustive coverage when missing profiles or artifacts remain.

## Generator/governor evidence slice

- [x] Add an explicit 27-model inventory checked against the May 2026 approved
  native mappings; keep the generated Markdown synchronized with it.
- [x] Require every linked model fixture to contain the corresponding native
  model and every linked checkpoint store to be readable.
- [x] Require both focused and trajectory test classes in CorePluginTestSuite.
  This exposed missing GASTD, IEEEG1D, IEESGOD, and TGOV1D focused-test
  registrations. Their existing tests are now public and registered.
- [x] Focused inventory and all four restored governor classes pass (10 tests,
  zero failures/errors/skips).
- [x] Complete the full reactor regression including CorePluginTestSuite.
- [x] Record results and prepare the scoped commit after available gates pass.

The inventory records links to reviewed tests rather than issuing VERIFIED
labels. Existing trajectory tolerances and model equations are unchanged by
this slice. Missing private Texas2k inputs still prevent its case acceptance
gates from executing.

Completed validation on 2026-10-17:

```text
mvn -q test -Pextended-tests --fail-at-end
    -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite
    -Dsurefire.failIfNoSpecifiedTests=false
```

The command exits zero. Surefire reports written during this run contain 2,686
tests: 2,573 passed, 113 skipped, zero failures/errors. Module totals are 128
core-plugin tests (4 skipped), 468 three-phase tests (28 skipped), and 2,090
integration tests (81 skipped). The explicit suite adds coverage beyond default
test-name discovery. These counts include the separate, unchanged WECC240/JAX
test present in the shared checkout. The four restored focused classes pass
all eight tests through the suite. Both evidence-inventory tests pass.

Full output is retained locally at
`target/generator-governor-evidence-regression.log`. Texas2k private simulations
remain skipped or disabled because the source dataset is unavailable. No new
simulator trajectory was created in this slice; existing checkpoint values and
comparison tolerances are preserved.

## Native EXDC2 dialect slice

The exciter audit found that the approved PSLF `EXDC2A` row was wrongly
represented as a distinct native DYR model. The provided May 2026 table
maps that row to EXDC2. Native EXDC2 uses Switch where PowerWorld EXDC2A uses
Tf2; a same-width input record cannot safely substitute one for the other.
See the [source-contract report](benchmarks/exdc2-native-dialect-2026-10-17.md).

- [x] Verify the table columns visually and check native reference simulator schemas.
- [x] Correct the source-list mapping to native EXDC2 and remove the
  non-native EXDC2A reader path, with an explanatory import report.
- [x] Preserve the explicit EXDC2A builder API and its independent equations.
- [x] Regenerate the support matrix; approved native exciter rows remain 64/64.
- [x] Pass focused schema, rejection, builder, equilibrium, native simulator,
  and ANDES regressions without changing trajectory tolerances.
- [x] Complete the full registered reactor regression plus CorePluginTestSuite.
- [x] Record results and prepare this slice for a scoped commit.

Validation on 2026-10-17: 56 focused tests pass without skips. The full
registered reactor command in the source-contract report exits zero with
2,687 tests: 2,574 passed, 113 skipped, zero failures/errors. It excludes only
the unrelated untracked WECC240/JAX test. Native trajectory checkpoints and
tolerances are unchanged. Texas2k private simulations remain unexecuted.

This slice closes a concrete native-format classification error discovered
during the exciter audit. The subsequent evidence-link slice below completes
the exciter mapping inventory, not its exhaustive profile or provenance work.

## Native exciter evidence slice

- [x] Link all 59 distinct native exciter models (64 approved source-list rows)
  to fixtures, focused tests, checkpoints and registered trajectory tests.
- [x] Check fixture native names and record lengths; keep the generated
  [exciter evidence document](exciter-evidence.md) synchronized.
- [x] Restore six missing focused-class suite registrations: SCRX, DC3A,
  ESAC4A, EXAC4, EXST2 and ST5B. Preserve their existing assertions.
- [x] Pass 28 focused tests with zero failures/errors/skips.
- [x] Audit the selected private PowerWorld manifests read-only using their
  documented newline normalization: 46/46 output hashes match, but only 29/46
  have all declared inputs present and hash-matching in this checkout.
- [x] Cold-run the input-complete AC1C PowerWorld profile twice into separate
  new directories. Both outputs have identical canonical hashes and agree
  with every old timestamp/channel value; only empty trailing CSV fields
  differ across executable versions. Keep the original reference unchanged.
- [x] Finish full registered reactor validation including CorePluginTestSuite.
- [x] Record results and prepare the scoped commit.
- [ ] Recover exact inputs/specifications for the 17 provenance gaps, then
  cold-regenerate and compare without rewriting old manifests or fitting curves.
  - [x] Recover eleven missing DYR contracts and all their matching input roles;
    preserve thirty assets and cold-run all eleven specifications twice.
    Fresh pairs match exactly; ten historical profiles match numerically.
  - [ ] Recover six historical specification byte sequences; their separate
    current-specification cold evidence is retained. Diagnose the small AC11C
    old/new discrepancy without rewriting historical results.
  - [x] Add independent dense fresh coupled comparisons for all eleven recovered
    profiles, with pinned public numeric projections, explicit full-row private
    export audits and unchanged historical bounds. AC8B remains a declared
    equation-equivalent common profile; event-side and active-branch gaps remain.

See the [audit and explicit provenance gaps](benchmarks/exciter-evidence-audit-2026-10-17.md).
This inventory does not close branch/profile coverage or system acceptance.

Full validation on 2026-10-17 exits zero with 2,688 tests: 2,575 passed,
113 skipped, zero failures/errors. The command and module counts are in the
audit report. This includes all 25 restored focused tests, the three inventory
tests and the existing AC1C trajectory comparison; it excludes the unrelated
untracked WECC240/JAX test. No reference checkpoint or tolerance changes.

## Stabilizer evidence and source-column slice

- [x] Link all fifteen approved source rows/fourteen implemented mappings to
  focused tests, declared DYR fixtures, comparisons and readable checkpoints.
- [x] Verify registration and actual selection by the permanent SMIB gate;
  keep generated Markdown synchronized with the reviewed Java inventory.
- [x] Correct the source catalog against the supplied May 2026 PDF: PSS1A's
  PTI cell is IEEEST, and PSS3C/PSS4C/PSS5C PTI cells are blank. Preserve all
  implementations separately; do not invent aliases or remove later-release
  benchmark evidence. Twelve nonblank source PTI cells contain nine names.
- [x] Retain explicit scope for thirteen model-specific comparisons and the
  PSS1A equivalent-only play-in. Neither category is an exhaustive VERIFIED label.
- [x] Observe the source-column regression fail before the fix; complete the
  1,158-test SMIB gate (one existing diagnostic skip) and 130 plugin units
  (six optional skips), with zero failures/errors and no full-system dynamics.
- [x] Seal and hash-verify 694 source, fixture, checkpoint and regression
  evidence files; record the source-column correction and inventory in one slice.
- [ ] Add exact PSS1A commercial SMIB evidence and audit PSS1A/PSSSB input
  dialects independently of the WECC cross-vendor source mapping.
- [ ] Audit raw artifact versions/cold repeatability and remaining active
  limiter, selector and lifecycle profiles for the stabilizer links.
  - [x] Separate exact native PSS6C/PSS7C widths from PowerWorld-only filter
    extensions. Retain builder mappings and cold-repeat both native SMIB
    histories, including all states and raw event-side rows. Final 100 focused,
    406 plugin-core and 1,572 SMIB/model/solver checks pass with existing skips
    only. Complete selector/limit/lifecycle and coupled event parity remain open.
  - [x] Correct PSS6C negative-input cascaded canonical equations, both
    compensated impedance-base conversions and PSS7C pure-lag observation.
    Sixteen new cold runs add eight two-step state/output replay profiles;
    source-backed unit and strict imported fault checks pass. Final 107
    focused, 406 plugin-core and 1,579 scoped SMIB/model/solver checks pass
    with existing skips only; complete model/coupled parity remains open.
- [ ] Complete the renewable evidence inventory and remaining model profiles.

See the [source discrepancy, evidence scope and validation report](benchmarks/stabilizer-evidence-audit-2026-10-17.md).

## PowerWorld repeatability slice

- [x] Add a permanent strict SINGLE-CSV comparison utility and command-line
  entry point; preserve metadata, all channels and event-side duplicate samples.
- [x] Register eight positive/negative tests in CorePluginTestSuite; the focused
  run plus four existing checkpoint-integrity tests passes without skips.
- [x] Check the six changed specifications against declared native inputs,
  events, solver settings, channel selection and disabled auto-correction.
- [x] Cold-run AC7B, ESAC4A, ESAC6A, ESAC8B, EXAC4 and REXSYS twice each into
  new directories. All twelve runs complete, with identical paired CSV hashes.
- [x] Compare every new sample/channel against the old export with the permanent
  utility: all six models match numerically, including both event-side rows.
- [x] Preserve exact current specifications and runner beside staged native
  RAW/DYR inputs, runnable AUX, raw results, logs and manifests in the local
  private run bundle; independently check its output/input hashes.
- [x] Complete full registered reactor validation and record the results.
- [x] Prepare the scoped commit after available gates pass.

See the [run identities and reproducibility report](benchmarks/powerworld-exciter-repeatability-2026-10-17.md).
This establishes current repeatability for six additional SMIB profiles, not
recovery of their old specification bytes or exhaustive model acceptance.
Eleven other PowerWorld datasets still lack declared DYR inputs, and the
earlier seventeen historical provenance discrepancies are preserved as such.

Full validation on 2026-10-17 exits zero: 2,696 tests, 2,583 passed,
113 skipped, zero failures/errors. All eight new verifier tests and the six
existing InterPSS trajectory comparisons pass. The report records the exact
command, module totals, cold-run hashes and source-input staging correction.
The newly recovered Texas2k dataset was kept outside the default test root
during this run; it does not turn skipped private gates into passed tests.

## Original-source loading and shared CML initialization slice

- [x] Copy and hash-check all 27 native source inputs into the ignored default
  private case location, preserving the external originals.
- [x] Make eleven ordinary Case 1 test paths select the actual provided
  version-34 RAW. Keep the separate custom staged-diagnostic path unchanged.
- [x] Fix shared CML output refresh on partially initialized annotation graphs;
  add three lifecycle regressions. Core commit: `e886019ce`.
- [x] Pass 13 focused core tests and all 2,910 core tests with zero skips/errors.
- [x] Pass fresh native inventory, GNET/MODREMOVE attachment accounting, all
  six short flat/fault/trip smokes, and all six strict one-second flat gates.
- [x] Execute and hash-check all 15 topology fault traces with 65-135 channels.
- [x] Preserve traces, strict rankings, exact inputs, completed logs and the
  seven current PowerWorld cold-run bundles outside build outputs in the
  ignored private verification archive.
- [x] Run the full registered plugin regression with private data and enabled
  fault matrix: 2,606 passed, 90 skipped, zero failures/errors. Restore opt-ins.
- [ ] Diagnose severe weak-grid/GFM fault responses; broad smoke success is
  not physical acceptance (up to 1.699 pu fault-bus voltage at 0.5 s).
- [ ] Resolve the full-case PowerWorld load stop and obtain exact independent
  fault trajectories. Its current probe times out before RAW completion.

See the [source, lifecycle and acceptance report](benchmarks/texas2k-original-source-2026-10-17.md).
Strict flat initialization is now verified on current original inputs, but the
Texas2k physical fault/cross-tool milestone and the full approved-model goal
are not complete. No gain, physical parameter or comparison bound was fitted.

## REGFMA1 boundary and timestep diagnostic slice

- [x] Discriminate and fix active Imax conversion from inverter to network
  base at 50, 100 and 200 MVA, including actual Norton injection and repeated
  algebraic reads without state accumulation.
- [x] Discriminate and fix plant Q/V offsets on the selected side of Mq;
  retain the published equations rather than an old incorrect test expectation.
- [x] Preserve the existing independent trajectory and all its tolerances.
- [x] Generate three additional active-current-limit PowerWorld profiles
  twice cold; all six complete and paired full CSV values match exactly.
- [x] Preserve and hash-check raw artifacts, runnable inputs, runner and
  specifications outside build directories in the ignored private archive.
- [x] Add class-scoped timestep/end/site/tag settings with baseline and tagged
  artifact protections, exact event-grid checks and six unit tests.
- [x] Rerun Case 5 weak-grid and Case 6 GFM faults at h/2 and h/4. Preserve
  event-side rows and report the lack of demonstrated convergence honestly.
- [x] Finish the full registered regression with strict six-case flat and
  fifteen-event fault gates enabled: 2,704 tests, 2,615 passed, 89 skipped,
  zero failures/errors. All fifteen baseline trajectory hashes are unchanged.
  Archive enabled settings and fresh reports; restore private opt-ins to false.
- [x] Implement exact reference simulator REGFMA1 two-ICON/eighteen-CON (twenty-value)
  parsing and RAW coupling impedance, separately from Texas2k's nineteen-value
  legacy exchange. Implementation and focused native comparisons now pass;
  native comparisons and the selected SMIB/model-unit regression pass. This
  does not close the remaining independent profiles listed below.
- [ ] Add complete active-limiter cross-tool trajectory comparisons for the
  new rating profiles; boundary agreement alone is insufficient.
- [x] Correct the inherited fault writer's one-step offset between monitor
  timestamps and custom-state snapshots. The `endpoint-v2` recorder below keeps
  old exports immutable; exact independent event-post acceptance is still open.
- [x] Cold-run native negative-initial-Q REGCA1 fault recovery with Iqrmin=0
  twice. Both produce identical exports and retain high post-fault current.
- [x] Run diagnostic matched-network comparisons of that native profile at two
  steps with corrected observation alignment. Do not classify these as accepted:
  voltage/Q boundary differences and unmatched event-post observations remain.
- [ ] Resolve severe fault responses and full-system independent execution.

See the [source contracts, failures-before-fix and benchmark evidence](benchmarks/regfma1-boundaries-2026-10-17.md).
The two boundary fixes do not directly explain the severe source-case faults:
its GFM ratings equal the network base and no plant offset is attached.

### Native REGFMA1 schema and initialization follow-up

- [x] Implement the exact native twenty-value layout and RAW ZSOURCE interface,
  keeping native parameter validation separate from PowerWorld corrections.
- [x] Retain and explicitly identify the nineteen-value Texas2k exchange; allow
  callers to pin a known layout rather than infer a truncated record.
- [x] Fix initialization before core impedance-base setup; discriminate actual
  Norton power at 50, 100 and 200 MVA, with a failing-before-fix test.
- [x] Expose pure immutable native nine-STATE/eight-VAR observations without
  changing solver coordinates or existing named-state keys.
- [x] Cold-repeat nine native datasets; preserve twenty completed native runs
  and the failed API-allocation diagnostic. Compare all 24 regular-endpoint
  channels and halve both simulators' steps without changing physical inputs.
- [x] Fix the native internal-voltage VAR coordinate using an active-Imax
  discriminator; retain pre-limit Edroop and verify current limiting/release
  at matched and half steps. No physical limiter equation was changed.
- [x] Replay a frozen native probe in a fresh isolated input tree and reproduce
  the complete RAW, DYR and CSV hashes, with the permanent quality auditor.
- [x] Pass 41 final focused tests and 2,945 core tests with zero failures/errors/skips.
- [x] Archive and hash-verify 2,673 source, reference, audit and focused-build
  evidence files in the ignored private native-evidence archive.
- [x] Finish and preserve the initial full regression: 2,747 tests, three
  failures, zero errors, 91 skips. Correct its inventory assertion in focused
  tests. Do not erase its strict-flat/smoke failures or mark it green.
- [x] Follow the revised user scope: stop the subsequent full-system run and
  restore the private full-system opt-ins to their disabled baseline.
- [x] Finish the complete selected SMIB/model-unit regression with the final
  native observation fix, inventory guard and new active-limit references.
  1,150 tests, zero failures/errors, one existing opt-in diagnostic skipped.
  Plugin-core units: 129 tests, zero failures/errors, six optional skips.
- [x] Seal final evidence: native follow-up 91 files, core validation 1,629,
  initial full regression 1,346, final SMIB/unit validation 666; all hashes pass.
- [x] Commit the tested core integration as `a036d9a78` in the separate lifecycle
  checkout; the original concurrently used core master is not reset or edited.
- [x] Verify all four native P/Q limit paths in direct-voltage mode, at matched
  and half timesteps with twice-cold native references and active-branch guards.
- [ ] Finish native plant-input, lifecycle and exact
  instantaneous event-post comparisons before complete REGFMA1 acceptance.
- [x] Verify active-Imax operation with terminal-voltage PI enabled, including
  native current limiting/release and voltage-integral trajectories at two steps.
- [x] Correct native voltage PI anti-windup: bound s8 independently of the
  algebraic proportional-plus-integral output. Preserve typed/PowerWorld behavior
  until its active-voltage-limit branch has its own independent evidence.
- [x] Preserve two failing-before-fix guards and both-step structural mismatch;
  verify upper integral saturation and inward release against native reference simulator.
- [x] Expand to fifteen fixed-digest native datasets with immutable source-DYR
  CON vectors. Pass 54 focused checks and the 1,155-test SMIB gate (one existing
  diagnostic skip), plus 129 plugin units (six optional skips); no full Texas2k
  dynamics. Seal/hash-verify 844 new evidence files and two exact isolated replays.
- [x] Record implementation in commit `26660c135`; recheck its four model/test
  files against the completed isolated verification snapshot, and retain the
  full validation report rather than relying on its smaller focused subset.
- [ ] Add independent lower voltage-integral saturation, terminal-voltage PI/PQ
  limiter interaction, native zero-time/limit-initialization and typed/PowerWorld
  active-voltage-limit profiles.
- [x] Record final SMIB regression evidence and create the scoped native-schema,
  initialization-base, observation and SMIB-gate implementation slice.

Supplied-case no-event drift under the newer native wind semantics is deferred
to separate full-system work under the user's revised scope. Old passing
stationary-flat claims apply to their recorded snapshots, not the latest one.

See [native REGFMA1 source, base bug and trajectory evidence](benchmarks/regfma1-native-schema-2026-10-17.md).
The [native limiter follow-up](benchmarks/regfma1-native-limiters-2026-10-17.md)
records the new source-backed PI correction and remaining SMIB profile gaps.

## Synchronous fault-observation slice

- [x] Add immutable callback-time state snapshots and explicit physical versus
  raw solver timestamps; remove the inherited timestamp join.
- [x] Preserve event-pre samples, label integrated endpoints correctly and use
  a distinct `endpoint-v2` output name without rewriting old artifacts.
- [x] Register six timing/lifecycle/state tests, including exact voltage/speed
  equality in a public full-solver three-phase fault.
- [x] Export the exact native negative-Q network and compare InterPSS at
  matched and half steps; preserve unmatched native event-post/prehistory rows.
- [x] Cold-repeat native half-step and boundary discriminators. Establish a
  real exactly-zero initial-Q case rather than misclassifying a tiny negative.
- [x] Complete the full registered reactor with all six strict flat gates and
  fifteen faults: 2,710 tests, 2,621 passed, 89 skipped, zero failures/errors.
  Verify all 40,590 original network/machine monitor values are unchanged;
  archive enabled settings and reports, then restore private opt-ins.
- [x] Fix the confirmed REGCA1 exactly-zero selector mismatch: native applies
  neither recovery bound at zero; current code incorrectly selects the lower.
  Preserve strict sign behavior for genuinely nonzero Q, without epsilon fitting.
- [ ] Resolve high-voltage boundary/current-measurement discrepancies before
  accepting the new native negative-Q profile or the complete Texas2k faults.
- [ ] Provide exact instantaneous event-post observations if needed for complete
  independent comparisons; do not relabel an integrated endpoint as event-post.

See the [timing contract, native probes and open discrepancies](benchmarks/texas2k-endpoint-alignment-2026-10-17.md).
The instrumentation slice changes no production model or integration equation.

## REGCA1 exactly-zero-Q model slice

- [x] Replace the nonpositive lower-rate branch with a strict negative-Q
  selector. Neither direction is rate-limited at exactly zero initial Q.
- [x] Verify signed zero and ±1e-15 pu without epsilon normalization; retain
  the native active-zero-rate behavior for genuinely nonzero Q.
- [x] Observe two focused test failures and a full-solver failure before the
  fix. Pass all 34 converter/recovery/timing/aggregate/ANDES focused tests.
- [x] Add the exact native lossless fixture and five final-recovery checkpoints;
  score them separately from the still-unaccepted complete transient profile.
- [x] Cold-run the same profile twice in PowerWorld, with no overrides or
  auto-correction; all 2,003 samples and eight channels repeat exactly.
- [x] Confirm that all four model records and numeric parameters round-trip
  through PowerWorld. Preserve a separate stalled export-audit attempt honestly.
- [x] Finish the full registered regression with six strict flat cases and all
  fifteen topology faults enabled: 2,712 tests, 2,623 passed, 89 skipped,
  zero failures/errors. Archive results and enabled settings, restore opt-ins.
- [x] Compare all fifteen matched-input before/after traces. Case 5 Bus1011
  final voltage improves from 1.5193 to 1.0820 pu; Case 6 Bus1016 still reaches
  1.6821 pu. These changes do not establish independent system acceptance.
- [ ] Correct the independent high-voltage network-boundary discrepancy now
  localized by the same reference simulator and PowerWorld profile. Current early errors are
  reported explicitly, not accepted by widening bounds or fitting Khv.
- [ ] Close complete-profile, instantaneous event-side and Texas2k acceptance.

See the [selector proof, new PowerWorld artifact and remaining boundary gap](benchmarks/regca-zero-q-selector-2026-10-17.md).
