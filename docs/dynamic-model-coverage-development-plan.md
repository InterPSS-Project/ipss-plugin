# Dynamic model coverage development plan and TODO

## Current checkpoint

Updated 2026-10-17; upstream baseline is plugin `dcea9ab30` (1.4.6, core 1.4.3).
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

**Latest scoped acceptance status: incomplete, including missing implementations.**
The approved native renewable audit finds **27 distinct families: 17 loadable,
ten not implemented**, not merely missing benchmark coverage. The runtime
catalog previously omitted those unsupported families. Missing exact stabilizer
profiles, exhaustive independent model profiles and remaining provenance
gaps also need closure. Generated `LOADABLE` entries do not prove complete
verification. The new `DynamicModelSmibTestSuite` selects model-unit and
small-network independent comparisons without full Texas2k/WECC/IEEE39 runs.
Earlier system gates below apply only to their recorded snapshots; the latest
six-case drift failures are retained in the
[REGFMA1 report](benchmarks/regfma1-native-schema-2026-10-17.md), outside the
revised SMIB acceptance scope.

Scope remains native PSS/E RAW/DYR with `_gnet.idv` and `_MODREMOVE.idv`
preparation. GE PSLF DYD inputs and rows without a native PSS/E model name are
excluded. Reuse core GENROU and existing controller engines where equations
and state definitions agree.

## Approved renewable source and evidence audit

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
- [ ] Implement WTDTBU1 / current-library WTDTB1 after exact wrapper/native
  schema audit; verify the drivetrain-emulation equations against native PSS/E
  and PowerWorld in a small independent case before its scoped commit.
- [ ] Implement WTPTBU1 / current-library WTPTB1 with exact pitch-controller
  attachment, state initialization, active limits and two-step references.
- [ ] Implement WTGWGOAU and WTGIBFFRA, auditing installed-release identifiers,
  input/output coupling and weak-grid/inertial-response branches separately.
- [ ] Implement REECE1/REECEU1 with exact native record allocation, all selector
  paths and controller initialization; do not silently substitute REECD.
- [ ] Implement REPCCU and HPLNTD with the required single-/multi-aggregate
  measurement/dispatch interfaces and explicit native-version schemas.
- [ ] Implement REGFMB1, REGFMC1 and REPCGFMC1 as exact grid-forming converter
  and plant models. Do not alias them to REGFMA1 or REPCA1.
- [ ] Close independent REECD and WT12A1U_B trajectory gaps and native conformance
  for the six currently bounded ANDES links.
- [ ] Expand the evidence inventory to retired/legacy and vendor-package models
  required by supplied cases, keeping those separate from approved-list counts.
- [ ] Audit the remaining non-renewable PDF sections against actual source rows;
  the ten renewable gaps are not a claim that all other sections are complete.

See [all source rows, actual implementation gaps and profile scopes](renewable-model-evidence.md).
Installed PSS/E 36.7 documentation contains entries for the missing families,
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
- [x] May 2026 approved native PSS/E exciter implementation coverage: 64/64
  source-list rows, as asserted by `DynamicModelCatalogTest`.
- [x] All 24 approved governor rows with native PSS/E mappings have runtime
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
- [ ] Obtain matched native PSS/E or PowerWorld trajectories for complete
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
  Give the PSS/E adapter a configurable 500-iteration ceiling, without changing
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
   comparisons and two native PSS/E comparisons (IEEEG1D and TGOV3D).
   GENROU has ANDES evidence; GENQEC and GENQEJ have native PSS/E evidence.
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
represented as a distinct native PSS/E DYR model. The provided May 2026 table
maps that row to EXDC2. Native EXDC2 uses Switch where PowerWorld EXDC2A uses
Tf2; a same-width input record cannot safely substitute one for the other.
See the [source-contract report](benchmarks/exdc2-native-dialect-2026-10-17.md).

- [x] Verify the table columns visually and check native PSS/E 36.7 schemas.
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
- [x] Implement exact PSS/E 36.7 REGFMA1 two-ICON/eighteen-CON (twenty-value)
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
  verify upper integral saturation and inward release against native PSS/E.
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
  localized by the same PSS/E and PowerWorld profile. Current early errors are
  reported explicitly, not accepted by widening bounds or fitting Khv.
- [ ] Close complete-profile, instantaneous event-side and Texas2k acceptance.

See the [selector proof, new PowerWorld artifact and remaining boundary gap](benchmarks/regca-zero-q-selector-2026-10-17.md).
