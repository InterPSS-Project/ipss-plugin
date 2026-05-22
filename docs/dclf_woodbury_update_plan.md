# DCLF Woodbury Update Plan for `ipss.plugin.core`

This plan adds a reusable contingency transfer cache to InterPSS DCLF
contingency analysis. The goal is to keep the existing InterPSS modeling,
parsing, B-matrix, LU factorization, and result semantics, while avoiding
repeated monitor/outage sensitivity work for fixed-topology studies.

## Existing InterPSS Behavior

InterPSS already caches important DCLF work:

| Layer | Class | Behavior |
| --- | --- | --- |
| B-matrix cache | `BMatrixSolver` | Caches `B1` and `B11`, rebuilding only when dirty. |
| LU reuse | `BaseDclfSolver.prepareBMatrix()` | Factorizes only when the matrix is dirty. |
| Base DCLF result | `BaseDclfSolver.calculateDclf()` | Caches bus angles and writes base DCLF flow to every `DclfAlgoBranch`. |
| Optional sensitivity cache | `HashMapCacheDclfSolver` | Caches solved bus sensitivity vectors in a thread-safe map. |
| Multi-outage inverse | `ContingencyAnalysisAlgorithmImpl.calMultiOutageInvE_PTDF()` | Computes the small dense `[E - PTDF]^-1` matrix used by multi-outage LODF. |

The current plugin analyzer, `ParallelDclfContingencyAnalyzer`, creates a
single `ContingencyAnalysisAlgorithm`, runs one base DCLF, then evaluates
contingencies in parallel. For each outage, it computes one full LODF vector and
then filters monitored branches.

## Target Cache

For a fixed topology, outage set, and monitor set, precompute:

```text
PTDF[monitor, outage]
denominator[outage] = 1 - PTDF[outage, outage]
LODF[monitor, outage] = PTDF[monitor, outage] / denominator[outage]
```

After this cache is built, every new load/generation profile only needs:

```text
1. run DCLF with the existing B1 factorization,
2. refresh base monitor and outage flows,
3. apply postFlow = baseMonitor + LODF * baseOutage,
4. stream threshold violations.
```

## First Implementation Slice

The first checked-in slice is intentionally conservative and is now treated as
an internal optimization layer rather than a user-facing API:

- It adds the new package `org.interpss.plugin.contingency.dclf`.
- It builds an immutable full in-memory LODF panel for N-1 branch-open
  contingencies.
- It uses existing InterPSS `pTransferDistFactor()` and
  `lineOutageDFactor()` APIs so sign conventions, transformer handling, radial
  cases, and parallel branch behavior stay aligned with current InterPSS.
- It adds a cached analyzer that applies the panel without mutating shared
  `DclfAlgoBranch.shiftedFlow` state.
- `DclfTransferPanel*`, `PanelBuildOptions`, `DclfContingencyStudySpec`, and
  `CachedDclfContingencyAnalyzer` are package-private implementation classes.
  Normal callers should use the consistent CA APIs and select the solution
  method through `ContingencyAnalysisAlgorithm.setSolutionMethod(...)` or
  `DclfContingencyConfig.setSolutionMethod(...)`.

## Next Steps

1. Done: add unit tests comparing cached panel LODF values to
   `ContingencyAnalysisAlgorithm.lineOutageDFactor()`.
2. Done: add post-flow comparisons against
   `ParallelDclfContingencyAnalyzer`.
3. Done: add chunked monitor panels for OpenEI/full EI scale.
4. Done: replace scalar panel construction with per-outage LODF vector solves.
5. Done: add explicit multi-outage Woodbury helpers based on the existing
   `[E - PTDF]` implementation.
6. Done: add a multi-outage contingency analyzer and JSON-driven
   Texas2k regression that randomly groups existing branch contingencies into
   N-2 and N-3 outage cases.
7. Done: expose solution-method selection through the existing CA API boundary
   instead of exposing transfer-panel-specific classes.

## Phase 2 Validation

`DclfTransferPanelCacheTest` now checks both layers:

- cached LODF panel values match InterPSS `lineOutageDFactor()` on IEEE 14,
- `CachedDclfContingencyAnalyzer` returns the same monitored post-flow records
  as `ParallelDclfContingencyAnalyzer` for the same outage and monitor sets.

## Phase 3 Woodbury Solver

`DclfContingencyWoodburySolver` now lives in `ipss.core_EMF` alongside the
existing InterPSS DCLF contingency algorithm.
`DclfContingencySolutionMethod` names the two core solver modes,
`SparseEqnSolve` and `WoodburyMatrixUpdate`; the Woodbury solver reports
`WoodburyMatrixUpdate`.

- `singleOpenLodf()` and `singleOpenPostFlow()` expose the N-1
  Sherman-Morrison result through existing InterPSS semantics.
- `solveMultiOpen()` computes multi-open outage monitor flows with the existing
  `[E - PTDF]^-1` Woodbury matrix, returning pre-flow, shifted-flow, and
  post-flow in both pu and MW.
- The wrapper restores branch sort numbers and the original outage list after
  computing the Woodbury result, so callers do not inherit the temporary matrix
  indexing used by `calMultiOutageInvE_PTDF()`.

Current tests verify single-open post-flow equivalence and multi-open shifted
flow equivalence against InterPSS `multiOpenOutageAnalysis()` on IEEE 14.

`DclfMultiOutageContingencyAnalyzer` now promotes solution-method selection into
an analysis path for `DclfMultiOutage` contingencies. It calculates the current
DCLF once, sets the selected `DclfContingencySolutionMethod`, and calls
`ContingencyAnalysisAlgorithm.ca()` for each contingency. The analyzer emits the
same `BranchCAResultRec` type used by N-1 analysis; the result record carries
normalized outage-equipment accessors and optional multi-outage LODF factors for
combined-shift sensitivity calculations.

The Texas2k JSON regression reads the existing branch-contingency and monitored
branch JSON files, shuffles the valid single outages with a fixed seed, creates
one N-2 and one N-3 `DclfMultiOutage`, and compares Woodbury monitor results
against InterPSS `multiOpenOutageAnalysis()`.

## Phase 4 Chunked Panels and Batched LODF Construction

The transfer cache is now chunk-native:

- `DclfTransferPanelChunk` stores a contiguous monitor slice and its
  monitor-by-outage LODF block.
- `PanelBuildOptions.monitorChunkSize` controls chunking. The default `0`
  preserves full-panel behavior by creating one chunk.
- `CachedDclfContingencyAnalyzer` iterates chunks directly so large monitor sets
  do not require callers to materialize one monolithic panel.
- `DclfTransferPanelBuilder` now computes one InterPSS `lineOutageDFactors()`
  vector per outage and scatters only requested monitor entries into chunks.
  This keeps InterPSS radial/parallel-branch semantics while avoiding repeated
  scalar `lineOutageDFactor()` calls for every monitor/outage pair.

The IEEE 14 regression test builds both full and chunked panels, compares every
cached LODF value, and verifies that full-panel and chunked analyzers return the
same post-flow records.

## Phase 5 Scaled Regression Tests

The regression suite now includes medium-size InterPSS fixtures:

- IEEE 118: 25 outages, 40 monitored branches, monitor chunk size 8, compared
  against `ParallelDclfContingencyAnalyzer` with 4 workers.
- IEEE 300: 40 outages, 80 monitored branches, monitor chunk size 16, compared
  against `ParallelDclfContingencyAnalyzer` with 4 workers.

This scale-up exposed a radial-outage edge case. When `PTDF(k,k)` is near
`+/-1`, the raw denominator `1 - PTDF(k,k)` can be near zero, but InterPSS still
returns a valid LODF vector for radial outages. `DclfTransferPanelBuilder` now
marks those outages valid to match `lineOutageDFactors()` and the existing
parallel analyzer.

## Phase 6 Large Case Gated Tests

`DclfTransferPanelLargeCaseTest` adds opt-in large regressions. They are gated
by the system property `-Dinterpss.largeDclfTests=true` so normal test runs
compile the class but skip the large RAW imports.

Current opt-in cases:

- Texas2k PSS/E v36: 60 outages, 120 monitored branches, monitor chunk size 24,
  compared against `ParallelDclfContingencyAnalyzer` with 4 workers.
- ACTIVSg25k PSS/E v33: 30 outages, 60 monitored branches, monitor chunk size
  15, compared against `ParallelDclfContingencyAnalyzer` with 4 workers.
- OpenEI full JSON PSS/E v33: `Base_Eastern_Interconnect_515GW.RAW` with
  `OpenEI_filtered_contingencies.json` and `OpenEI_monitored_branches.json`,
  compared against `ParallelDclfContingencyAnalyzer` with 8 workers. This case
  requires both `-Dinterpss.largeDclfTests=true` and
  `-Dinterpss.fullJsonDclfTests=true`.
- OpenEI 24-hour repeated profiles: the same full JSON study set with
  deterministic `+/-5%` contributed-load variation per hour. The cached path
  builds the transfer panel once, then reuses it across 24 profile solves. Both
  cached and original analyzers use 8 workers for the contingency scan. This
  case additionally requires `-Dinterpss.hourlyDclfTests=true`.

Latest OpenEI full JSON result:

- JSON import: 6,294 contingencies and 31,840 monitored branches.
- Resolved study set after filtering reference-bus/out-of-service outage
  branches: 6,288 outages and 31,840 monitors.
- DCLF matrix dimension: 78,484.
- Chunking: 125 monitor chunks with chunk size 256.
- Estimated cached LODF panel size: 1,527 MB.
- Thermal violations above 100% rating: 11,513 records.
- Cached transfer-panel run: 41,225 ms, including panel build and 8-worker
  current profile scan.
- InterPSS parallel baseline: 19,209 ms with 8 workers.
- The cached and parallel result keys and MW values matched exactly within
  `1.0e-7`.

Latest OpenEI 24-hour repeated-profile result with 8-worker cached scans:

- Hourly profiles: 24 deterministic random load profiles, varying 56,504
  contributed load records by `+/-5%`.
- Cached transfer-panel setup: 40,951 ms.
- Cached repeated-profile scans: 4,320 ms with 8 workers.
- Cached total time including setup: 45,271 ms.
- Original parallel baseline total time: 446,071 ms with 8 workers.
- Total violation records across all hours: 280,854.
- End-to-end speedup: 9.85x including one-time setup, and 103.26x for repeated
  hourly scans after setup.
- The cached and original solver results matched exactly within `1.0e-7` for
  every hourly profile.

This repeated-profile test exposed a stale outage-flow issue in the original
parallel analyzer. `DclfOutageBranch.dclfFlow` is captured when contingencies
are created, but repeated profiles change the branch pre-flow. The analyzer now
refreshes each outage branch pre-flow from the current `ContingencyAnalysisAlgorithm`
after `calculateDclf()` and before running contingency monads.

The cached analyzer now snapshots current outage and monitor base flows after
each hourly DCLF solve, then applies the immutable LODF panel by monitor chunk
using a caller-provided worker count. `DclfWoodburyOutageSolver` also exposes a
parallel multi-open monitor solve overload so the Woodbury monitor update can be
split across workers.

The full JSON test needs the heap on the Surefire forked JVM, not only Maven
itself. A 12 GB forked heap was not enough for this case; 24 GB completed.

Run commands:

```text
mvn -q -pl ipss.test.plugin.core -am \
  -Dinterpss.largeDclfTests=true \
  -Dtest=org.interpss.plugin.contingency.dclf.DclfTransferPanelLargeCaseTest#texas2kChunkedPanelMatchesParallelAnalyzer \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -q -pl ipss.test.plugin.core -am \
  -Dinterpss.largeDclfTests=true \
  -Dtest=org.interpss.plugin.contingency.dclf.DclfTransferPanelLargeCaseTest#activsg25kChunkedPanelMatchesParallelAnalyzer \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -q -pl ipss.test.plugin.core -am \
  -DargLine=-Xmx24g \
  -Dinterpss.largeDclfTests=true \
  -Dinterpss.fullJsonDclfTests=true \
  -Dtest=org.interpss.plugin.contingency.dclf.DclfTransferPanelLargeCaseTest#openEiFullJsonChunkedPanelMatchesParallelAnalyzer \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -q -pl ipss.test.plugin.core -am \
  -DargLine=-Xmx24g \
  -Dinterpss.largeDclfTests=true \
  -Dinterpss.fullJsonDclfTests=true \
  -Dinterpss.hourlyDclfTests=true \
  -Dtest=org.interpss.plugin.contingency.dclf.DclfTransferPanelLargeCaseTest#openEiFullJsonTwentyFourHourProfilesReuseTransferPanel \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
