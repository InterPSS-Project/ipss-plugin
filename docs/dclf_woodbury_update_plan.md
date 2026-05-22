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

The first checked-in slice is intentionally conservative:

- It adds the new package `org.interpss.plugin.contingency.dclf`.
- It builds an immutable full in-memory LODF panel for N-1 branch-open
  contingencies.
- It uses existing InterPSS `pTransferDistFactor()` and
  `lineOutageDFactor()` APIs so sign conventions, transformer handling, radial
  cases, and parallel branch behavior stay aligned with current InterPSS.
- It adds a cached analyzer that applies the panel without mutating shared
  `DclfAlgoBranch.shiftedFlow` state.

## Next Steps

1. Done: add unit tests comparing cached panel LODF values to
   `ContingencyAnalysisAlgorithm.lineOutageDFactor()`.
2. Done: add post-flow comparisons against
   `ParallelDclfContingencyAnalyzer`.
3. Done: add chunked monitor panels for OpenEI/full EI scale.
4. Done: replace scalar panel construction with per-outage LODF vector solves.
5. Done: add explicit multi-outage Woodbury helpers based on the existing
   `[E - PTDF]` implementation.

## Phase 2 Validation

`DclfTransferPanelCacheTest` now checks both layers:

- cached LODF panel values match InterPSS `lineOutageDFactor()` on IEEE 14,
- `CachedDclfContingencyAnalyzer` returns the same monitored post-flow records
  as `ParallelDclfContingencyAnalyzer` for the same outage and monitor sets.

## Phase 3 Woodbury Solver

`DclfWoodburyOutageSolver` adds a small API around InterPSS DCLF Woodbury
calculations:

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
