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
