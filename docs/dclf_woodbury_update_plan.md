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
3. Add chunked monitor panels for OpenEI/full EI scale.
4. Replace scalar panel construction with batched endpoint sensitivity solves.
5. Add explicit multi-outage Woodbury helpers based on the existing
   `[E - PTDF]` implementation.

## Phase 2 Validation

`DclfTransferPanelCacheTest` now checks both layers:

- cached LODF panel values match InterPSS `lineOutageDFactor()` on IEEE 14,
- `CachedDclfContingencyAnalyzer` returns the same monitored post-flow records
  as `ParallelDclfContingencyAnalyzer` for the same outage and monitor sets.
