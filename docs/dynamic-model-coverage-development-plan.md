# Dynamic model coverage development plan and TODO

## Current checkpoint

Updated 2026-10-17 against plugin `dcea9ab30` (1.4.6, core 1.4.3).
This document restores the ongoing coverage checklist after rebasing to master.
The generated [support matrix](dynamic-model-support-matrix.md) records parser
and runtime availability. A `LOADABLE` row does not prove independent equation,
trajectory, or full-system acceptance.

Scope remains native PSS/E RAW/DYR with `_gnet.idv` and `_MODREMOVE.idv`
preparation. GE PSLF DYD inputs and rows without a native PSS/E model name are
excluded. Reuse core GENROU and existing controller engines where equations
and state definitions agree.

## What current source proves

- [x] Catalog and runtime paths for the Texas2k model families. The current
  catalog's `texas2kModels()` contains 25 descriptors; this is a curated family
  inventory, not a fresh count of models in the six missing case files.
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

The configured `testData/private/texas2k` dataset is absent. The previously
provided OneDrive case directory exists but currently contains no entries.
Therefore the September six-case results cannot be promoted to acceptance of
the October code. Historical short-smoke and 15-event execution results are
useful diagnostics only until the original inputs and hashes are restored.

- [ ] Restore the six original RAW/DYR cases and both preparation IDVs into
  the private input location; verify hashes before simulation.
- [ ] Reaudit every applicable DYR record after GNET/MODREMOVE: attached,
  intentionally removed, rejected, unsupported, or missing target.
- [ ] Rerun all six short flat, common three-cycle-fault, and generator-trip
  smokes on the current production solver.
- [ ] Enable and run all six strict one-second flat gates at 1/240 s with
  voltage drift <= 1e-6 pu and speed drift <= 1e-7 pu.
- [ ] Rank the first and largest bus-voltage, generator-speed, and controller
  state differences. Revisit the historically leading REECA1/REPCA1 devices
  only after confirming they still lead on the current equations.
- [ ] Run the reviewed 15-event topology fault matrix. Record voltages near
  the fault, generator speed/angle, P/Q, EFD/PM, and controller states.
- [ ] Obtain matched native PSS/E or PowerWorld trajectories for complete
  stacks. Add ANDES comparisons where its topology and parameterization match.
- [ ] Compare every channel at aligned event sides and matched timesteps;
  halve the step to distinguish integration and equation discrepancies.
- [ ] Require all six system cases to meet independent trajectory limits
  before marking the Texas2k milestone complete.

## Remaining model work and release gates

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
   identifies 17 PowerWorld input/specification gaps; one AC1C profile has
   current twice-cold numerical-repeatability evidence. Stabilizer and renewable
   inventories, exhaustive profiles, simulator-version audits and the remaining
   cold-regeneration provenance work are still open.
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
5. Run focused tests, registered core tests, available six-case smokes, strict
   flat gate, 15-fault matrix, and complete registered reactor tests. Record
   unavailable private-data gates explicitly as unexecuted.
6. Update this checklist and the generated support matrix as appropriate.
   Commit a scoped slice and report its hash, evidence, and remaining gaps.

Completion requires both the model evidence inventory and all Texas2k release
gates to be closed. A green public regression with skipped private cases does
not establish that milestone.

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
