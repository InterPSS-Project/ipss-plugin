# Optimization Adjustment (`optadj`) Architecture

## Purpose

The `optadj` module computes coordinated **generator dispatch adjustments** that reduce branch or section overloads under DC load flow (DCLF) assumptions.

At a high level, it:

1. Runs on top of an existing `ContingencyAnalysisAlgorithm` (or ACLF network for section optimization).
2. Builds linear constraints from bus-to-branch power-flow sensitivities (dense or sparse).
3. Solves a linear optimization problem with slack variables via `ojAlgo`.
4. Writes adjustments back to DCLF algorithm entities (`DclfAlgoGen.setAdjust(...)`) or ACLF generator setpoints.

Control is **generator-only** today; load adjustment is not implemented.

---

## Package Layout

```
org.interpss.plugin.optadj
├── algo
│   ├── lf/          # DCLF base-case and N-1 optimizers
│   ├── sec/         # Section-limit optimizer
│   ├── util/        # Sensitivity and SSA scan helpers
│   └── bean/        # PowerSystemSection model
├── optimizer/       # GenStateOptimizer LP engine and constraint beans
├── cluster/         # Generator clustering for model reduction
└── result/          # SSA and OptAdj result containers (JSON-serializable)
```

---

## Main Design

The implementation separates network sensitivity, optimization model construction, SSA screening, and scenario-specific orchestration.

| Layer | Package | Responsibility |
|---|---|---|
| **Scenario orchestration** | `algo.lf`, `algo.sec` | Build control sets and constraints for base case, N-1, or user-defined sections |
| **SSA screening** | `algo.util` (`AclfNetSsaHelper`) | Scan base-case and contingency loading; produce `SsaResultContainer` |
| **Sensitivity utilities** | `algo.util` | Compute bus-to-branch sensitivity matrices (dense or sparse) |
| **LP engine** | `optimizer` | Collect constraints, preprocess (merge/cluster), build and solve the LP |
| **Model reduction** | `cluster` | Cluster generators with identical sensitivity profiles |
| **Result containers** | `result` | SSA over-limit lists and optional OptAdj dispatch results |

---

## Key Components

### Load-flow optimizers (`algo.lf`)

#### `BaseAclfNetLoadFlowOptimizer`

Abstract base for **base-case** branch overload mitigation. Holds the shared `optimize(...)` pipeline, constraint builders, and result application.

- Entry point: `optimize(ContingencyAnalysisAlgorithm dclfAlgo, OptAdjResultContainer result, double threshold)`
- `threshold` is a loading limit in percent (e.g. `100.0` for 100% of rating).
- Returns `Map<String, OptAdjResultContainer.GenAdjustResult>` keyed by generator name; also stored on the container via `setOptAdjResults(...)`.
- When `result` is `null`, all active branches are constrained and all active generators are control candidates.
- When `result` is non-null (wraps prior SSA data), only SSA-listed branches are constrained and control generators are filtered to those with `|sensitivity| > SEN_THRESHOLD` (0.02) on relevant branches.

Flow:

1. Create sensitivity matrix via subclass `createSenMatrix(...)` (dense or sparse).
2. Build control generator index map (all active, or SSA-filtered).
3. Add section constraints — full scan (`buildSectionConstrain`) or SSA-scoped (`buildSsaSectionConstrain`).
4. Add generator P-min / P-max device constraints.
5. Solve via `GenStateOptimizer`.
6. Apply non-trivial adjustments (`|adjP| > 1 MW`) to `DclfAlgoGen`.

#### `AclfNetLoadFlowOptimizer`

Concrete base-case optimizer extending `BaseAclfNetLoadFlowOptimizer`.

- Constructor: `AclfNetLoadFlowOptimizer(boolean sparseMatrix)`
- `false` — dense matrix via `AclfNetSensHelper`, indexed by bus/branch number.
- `true` — sparse CSC matrix via `AclfNetSensSparseHelper`, indexed by sort number; when SSA data is present, sensitivity scope is limited to generator parent buses and SSA branch/outage IDs.

#### `AclfNetContigencyOptimizer`

Extends `AclfNetLoadFlowOptimizer` for **N-1 contingency** overload mitigation.

- Constructor: `AclfNetContigencyOptimizer(boolean sparseMatrix)`
- **Full-scan path** (`result == null`): calls `super.buildSectionConstrain(...)` for base-case constraints, then adds post-contingency constraints for every active branch outage.
- **SSA path** (`result != null`): adds base-case constraints from `ssaResult.getBaseOverLimitInfo()` and post-contingency constraints only for `(outage, monitored)` pairs in `ssaResult.getCaOverLimitInfo()`.
- Uses `dclfAlgo.lineOutageDFactors(...)` for LODF.
- Post-contingency flow: `postFlow = baseFlow + LODF * outagedFlow`
- Post-contingency sensitivity: `GSF(mon) + LODF * GSF(outaged)`
- Contingency constraints use `RatingMvaB`; base-case constraints use `RatingMvaA`.

### SSA helper (`algo.util`)

#### `AclfNetSsaHelper`

Runs steady-state analysis (SSA) loading scans on a `ContingencyAnalysisAlgorithm` and populates `SsaResultContainer`.

| Method | Purpose |
|---|---|
| `baseCaseScan(loadingThreshold)` | Scan all branches for base-case overloads above threshold |
| `calBaseCaseLoading(baseOverLimitInfo)` | Re-evaluate loading for a fixed branch list after dispatch change |
| `contingencyScan(contList, loadingThreshold)` | Full N-1 scan; record `(outage, monitored)` pairs above threshold |
| `contingencyScan(contList, monitoredBranchIds, loadingThreshold)` | Contingency scan restricted to a monitored-branch ID set |
| `contingencyScan(contList, caOverLimitInfo)` | Re-evaluate only the `(outage, monitored)` pairs from a prior SSA result |

Contingency scans skip pairs with `|shiftedFlowMW| <= ContingencyShiftThreshold`. Results are thread-safe (`CopyOnWriteArrayList`).

Typical SSA → OptAdj workflow:

```java
SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(contList, 100.0);
OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
new AclfNetContigencyOptimizer(false).optimize(dclfAlgo, optAdjResult, 100.0);

dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo)
    .contingencyScan(contList, ssaResult.getCaOverLimitInfo());
ssaResultAfter.printCaOverLimitInfo(ssaResult.getCaOverLimitInfo());
```

### Section optimizer (`algo.sec`)

#### `SectionOptimizer`

Optimizes against **user-defined transfer sections** rather than individual branches.

- Constructor: `SectionOptimizer(AclfNetwork network, List<PowerSystemSection> sections)`
- Entry point: `optmize()` → `Map<String, Double>` (generator ID → MW adjustment)
- Uses `AclfNetSensSparseHelper` for sparse sensitivity on section-relevant buses/branches.
- Each `PowerSystemSection` aggregates branch flows and sensitivities with per-branch coefficients.
- `updateNet(resultMap)` writes adjustments back to `AclfGen` setpoints (preserving Q/P ratio).
- Optional generator filter via `setGenPre(Predicate<AclfGen>)`.

#### `PowerSystemSection` (`algo.bean`)

Models a named section as a weighted sum of branch flows:

- `sectionPower = sum(branchFlow * coefficient)`
- `generatorSensitivity = sum(branchSensitivity * coefficient)`

Provides a `Builder` for constructing sections from branch IDs and coefficients.

### LP engine (`optimizer`)

#### `GenStateOptimizer`

Concrete LP builder and solver. Replaces the older `BaseStateOptimizer` abstraction.

**Preprocessing pipeline** (inside `optimize()`):

1. Collect generator bounds into `GeneratorParameter` objects from `GenConstrainData`.
2. Merge section constraints with identical sensitivity signatures (`mergeSectionsBySensitivity`, tolerance 0.001).
3. Cluster generators with similar sensitivity profiles (`GeneratorClustering`, threshold 0.001).
4. Average sensitivities within each cluster before building the model.

**Decision variables** (per cluster):

- `p_c`, `n_c` — positive/negative dispatch components (split variable)
- `dSecP_k` — section slack variables
- `sp`, `sn` — net power-balance slack

**Objective** (minimize):

- `sum(weight_c * (p_c + n_c))` — adjustment magnitude penalty
- `sectionFactor * sum(dSecP_k)` — section slack penalty (default `1e2`)
- `interfaceFactor * (sp + sn)` — net-balance penalty (default `1e4`)

**Result access:**

- `getPoint()` — legacy layout `[dGenP..., dSecP...]` with cluster adjustments distributed back to individual generators
- `getCachedDGenP()` / `getCachedDSecP()` — direct cached arrays
- `isAllControl()` — true when all section slacks are near zero (full feasibility)

### Constraint data objects (`optimizer.bean`)

- `BaseConstrainData` — common `(value, relationship, limit)` fields
- `GenConstrainData` — single-generator bound; stores variable `index` and optional `weight`
- `SectionConstrainData` — linear section constraint with `senArray` sensitivity vector; optional `name` for diagnostics

### Sensitivity helpers (`algo.util`)

#### `AclfNetSensHelper`

Dense bus-to-branch sensitivity matrix for load-flow optimizers.

- `calSen()` → `float[noActiveBus][noActiveBranch]`
- Uses `SenAnalysisAlgorithm` with `DclfMethod.INC_LOSS`
- Sensitivity per branch: `-b1ft * (angle_from - angle_to)` from `getSenPAngle`

#### `AclfNetSensSparseHelper`

Sparse sensitivity matrix for section optimization and sparse load-flow optimizers.

- `calSen()` — full network
- `calSenSortNumber()` — full network, sort-number indexed
- `calSenSortNumber(Set<String> busSet, Set<String> branchSet)` — scoped to SSA-relevant buses/branches
- Returns `DMatrixSparseCSC` indexed by bus/branch sort numbers

### Result containers (`result`)

#### `SsaResultContainer`

JSON-serializable container for SSA over-limit results.

- `baseOverLimitInfo` — base-case overloaded branches
- `caOverLimitInfo` — contingency overloaded branches (includes outage branch ID)
- Loading thresholds: `baseLoadingThreshold`, `caLoadingThreshold`
- `printBaseOverLimitInfo()` / `printCaOverLimitInfo()` — diagnostic output
- Overload comparison helpers accept a prior SSA list to print before/after loading side by side

#### `SsaBranchOverLimitInfo`

Per-branch over-limit record:

- Base case: `(overLimitBranchId, limitMW, baseFlowMW)` → `loadingPercent = 100 * |baseFlowMW| / limitMW`
- Contingency: adds `outageBranchId`, `shftedFlowMW` → post-flow loading from `baseFlowMW + shftedFlowMW`

#### `OptAdjResultContainer`

Extends `SsaResultContainer` with optimization output.

- Wraps prior SSA data: `new OptAdjResultContainer(ssaResult)`
- `optAdjThreshold` — loading limit used during optimization
- `optAdjResults` — `Map<String, GenAdjustResult>` populated by `optimize(...)`
- `GenAdjustResult` record: `(genP, adjP, genLimit)` where `genP` is current MW, `adjP` is dispatch change in MW, `genLimit` is the generator P limit

### Generator clustering (`cluster`)

#### `GeneratorClustering`

Clusters generators whose sensitivity profiles differ by less than a threshold. Generators are clustered only when their objective weights are identical. Reduces LP variable count while distributing solved cluster adjustments back to individual generators proportionally by capacity range.

---

## End-to-End Execution Flows

### Base-case load-flow optimization (full scan)

Typical flow in `BaseAclfNetLoadFlowOptimizer.optimize(...)` with `result == null`:

1. Compute sensitivity matrix (dense or sparse per constructor flag).
2. Build control generator set (all active generators).
3. Assign compact indices to control generators.
4. Build section constraints from all active branch flows, ratings, and sensitivities.
5. Build generator P-min / P-max constraints.
6. Solve LP via `GenStateOptimizer.optimize()`.
7. Apply solved adjustments to `DclfAlgoGen` (`setAdjust(adjP * 0.01)`).
8. Return `Map<String, GenAdjustResult>`.

After optimization, re-run DCLF to validate loading:

```java
dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
```

### Base-case optimization with SSA screening

1. `AclfNetSsaHelper.baseCaseScan(threshold)` → `SsaResultContainer`.
2. Wrap in `OptAdjResultContainer`.
3. `new AclfNetLoadFlowOptimizer(sparse).optimize(dclfAlgo, optAdjResult, threshold)`.
4. Re-run DCLF; re-evaluate with `calBaseCaseLoading(...)`.

Only SSA-listed branches become section constraints; sensitivity scope is reduced in sparse mode.

### N-1 contingency optimization (full scan)

Same as base case, plus `AclfNetContigencyOptimizer` adds post-contingency section constraints for every active branch outage using LODF.

### N-1 contingency optimization with SSA screening

1. Build branch-outage contingency list.
2. `AclfNetSsaHelper.contingencyScan(contList, threshold)` → overloaded `(outage, monitored)` pairs.
3. Wrap in `OptAdjResultContainer`; run `AclfNetContigencyOptimizer(sparse).optimize(...)`.
4. Re-run DCLF.
5. Re-scan the same pairs with `contingencyScan(contList, ssaResult.getCaOverLimitInfo())` to measure improvement.

On Texas2K (3216 branch outages, 100% limit, full branch scan): 14 contingency overloads before optimization, 10 still above 100% after (dense and sparse paths).

### Section optimization

Typical flow in `SectionOptimizer.optmize()`:

1. Filter generators (optional `genPre` predicate).
2. Compute sparse sensitivities scoped to section buses/branches.
3. Call `PowerSystemSection.calculate(...)` for each section.
4. Build generator and section constraints.
5. Solve and map results back to generator IDs.
6. Optionally call `updateNet(resultMap)` to update ACLF setpoints.

---

## Mathematical Form (as implemented)

The model operates in the MW domain and linearizes overload correction with DC sensitivity.

**Section constraints** — for each monitored branch or section:

- Upper bound (LEQ): `sum(Sen_i * x_i) + s_k <= limit - flow`
- Lower bound (GEQ): `sum(Sen_i * x_i) - s_k >= limit - flow`
- `s_k >= 0`

Flow direction determines sensitivity sign in load-flow optimizers (`flow > 0 ? sen : -sen`).

**Device constraints** — for each control generator `x_i`:

- `x_i <= P_max - P_current`
- `x_i >= P_min - P_current`

**Objective** — minimize weighted sum of:

- section slack usage (`sectionFactor`, default 100)
- adjustment magnitude (`weight`, default 1 per generator)
- net power imbalance (`interfaceFactor`, default 10,000)

Generator clustering solves on cluster variables, then distributes the solution back to individual generators by capacity range.

---

## Samples and Tests

Samples live under `src/sample/java/org/interpss/optadj/`, organized by test system and matrix mode (`dense/` vs `sparse/`).

### IEEE-39 (`ieee39/`)

| Class | Scenario |
|---|---|
| `dense/IEEE39_OptBasecase_Sample` | Base-case overload mitigation (full scan) |
| `dense/IEEE39_OptN1Scan_Sample` | N-1 scan + `AclfNetContigencyOptimizer` (full scan) |
| `dense/IEEE39_OptBasecase_SsaResult_Sample` | Base-case with SSA-filtered control set |
| `dense/IEEE39_OptN1Scan_SsaResult_Sample` | N-1 with SSA-filtered control set |
| `sparse/IEEE39_OptBasecase_Sparse_Sample` | Base-case, sparse sensitivity matrix |
| `sparse/IEEE39_OptN1Scan_Sparse_Sample` | N-1 full scan, sparse matrix |
| `sparse/IEEE39_OptBasecase_SsaResult_Sparse_Sample` | Base-case SSA path, sparse matrix |
| `sparse/IEEE39_OptN1Scan_SsaResult_Sparse_Sample` | N-1 SSA path, sparse matrix |
| `sparse/IEEE39_OptSection_Sample` | Iterative section-limit optimization |
| `IEEE39_Sample_Data` | Shared IEEE-39 test case factory (600 MVA uniform ratings) |

### Texas2K (`texas2k/`)

| Class | Scenario |
|---|---|
| `dense/Texas2K_OptBasecase_SsaResult_Sample` | Base-case SSA path on Texas2K |
| `dense/Texas2K_OptN1Scan_SsaResult_Sample` | N-1 SSA path, monitored-branch subset |
| `dense/Texas2K_OptN1Scan_SsaResult_Sample1` | N-1 SSA path, all branch outages |
| `sparse/Texas2K_OptBasecase_SsaResult_Sparse_Sample` | Base-case SSA path, sparse matrix |
| `sparse/Texas2K_OptN1Scan_SsaResult_Sparse_Sample` | N-1 SSA path, monitored-branch subset |
| `sparse/Texas2K_OptN1Scan_SsaResult_Sparse_Sample1` | N-1 SSA path, all branch outages |
| `Texas2K_Sample_Info` | Texas2K network loader |
| `Texas2K_SenMatrix_Sample` | Sensitivity matrix demonstration |

### Eastern Interconnection (`ei/`)

| Class | Scenario |
|---|---|
| `EInterCon_OptBasecase_SsaResult_Sample` | Base-case SSA path |
| `EInterCon_OptN1Scan_SsaResult_Sample` | N-1 SSA path |

### Regression tests (`ipss.test.plugin.core/.../optadj/`)

| Test class | Validates |
|---|---|
| `ieee39/dense/IEEE39_OptBasecase_Test` | Base-case optimizer reduces overloaded branches (~362 MW redispatch) |
| `ieee39/dense/IEEE39_OptN1Scan_Test` | N-1 optimizer reduces contingency violations (51 → ~6–8) |
| `ieee39/dense/IEEE39_OptBasecase_SsaResult_Test` | SSA-screened base-case path |
| `ieee39/dense/IEEE39_OptN1Scan_SsaResult_Test` | SSA-screened N-1 path |
| `ieee39/sparse/IEEE39_OptBasecase_Sparse_Test` | Base-case, sparse matrix |
| `ieee39/sparse/IEEE39_OptN1Scan_Sparse_Test` | N-1 full scan, sparse matrix |
| `ieee39/sparse/IEEE39_OptBasecase_SsaResult_Sparse_Test` | Base-case SSA path, sparse matrix |
| `ieee39/sparse/IEEE39_OptN1Scan_SsaResult_Sparse_Test` | N-1 SSA path, sparse matrix |
| `ieee39/sparse/IEEE39_OptSection_Test` | Section optimizer on IEEE-39 |
| `texas2K/dense/Texas2K_OptBasecase_SsaResult_Test` | Texas2K base-case SSA path |
| `texas2K/dense/Texas2K_OptN1Scan_SsaResult_Test` | Texas2K N-1 SSA path (monitored subset) |
| `texas2K/dense/Texas2K_OptN1Scan_SsaResult_Test1` | Texas2K N-1 SSA path (all branch outages) |
| `texas2K/sparse/Texas2K_OptBasecase_SsaResult_Sparse_Test` | Texas2K base-case SSA, sparse matrix |
| `texas2K/sparse/Texas2K_OptN1Scan_SsaResult_Sparse_Test` | Texas2K N-1 SSA, monitored subset |
| `texas2K/sparse/Texas2K_OptN1Scan_SsaResult_Sparse_Test1` | Texas2K N-1 SSA, all branch outages |
| `Texas2K_SenMatrixHelper_Test` | Texas2K sensitivity helper correctness |
| `IEEE14_SensHelper_Test` | Sensitivity helper correctness on IEEE-14 |

Run a single test:

```bash
mvn -pl ipss.test.plugin.core test -Dtest=IEEE39_OptBasecase_Test
mvn -pl ipss.test.plugin.core test -Dtest=Texas2K_OptN1Scan_SsaResult_Test1
```

Run the full optadj regression suite:

```bash
mvn -pl ipss.test.plugin.core test -Dtest=org.interpss.CorePluginTestSuite
```

---

## Extension Points

1. **New optimization scenario**
   - Extend `BaseAclfNetLoadFlowOptimizer` (override `buildSectionConstrain`, `buildSsaSectionConstrain`, `buildControlGenSet`, or `createSenMatrix`).
   - Or add a new orchestrator alongside `SectionOptimizer`.

2. **Alternative LP engine**
   - Replace or wrap `GenStateOptimizer` while keeping the constraint bean API (`GenConstrainData`, `SectionConstrainData`).

3. **Custom screening rules**
   - Tune `SEN_THRESHOLD` (0.02 in optimizers, 0.02/0.1 in `GenStateOptimizer`).
   - Pre-filter branches/outages before constraint generation.
   - Populate `SsaResultContainer` from an external SSA pipeline via `AclfNetSsaHelper` or direct list construction.

4. **Sparse vs dense sensitivity**
   - Pass `true` to `AclfNetLoadFlowOptimizer` / `AclfNetContigencyOptimizer` for large networks; SSA data automatically scopes the sparse matrix to relevant buses and branches.

5. **Load control**
   - Not yet implemented. Would require new decision variables, sensitivity columns, and result application to `DclfAlgoLoad`.

6. **Post-processing policies**
   - Add ramp-rate, area-balance, or market rules as extra `GenConstrainData` / `SectionConstrainData` rows.

---

## Operational Notes

- Sensitivity helpers and several loops use `parallelStream()`. Avoid mutable shared state in stream callbacks.
- Constraints are assembled in MW; adjustments are written to DCLF as `setAdjust(adjP * 0.01)`.
- `SEN_THRESHOLD` (0.02) and `GEN_DISPATCH_THRESHOLD` (1.0 MW) control solvability and result reporting.
- `GenStateOptimizer` prints section-merge statistics to stdout during preprocessing.
- Re-run DCLF after applying adjustments to validate that overloads are actually reduced.
- Use `OptAdjResultContainer` + SSA re-scan to compare before/after loading on the same branch or `(outage, monitored)` pairs.
- On IEEE-39 with 600 MVA uniform ratings at 100% loading limit, base-case optimization typically moves ~362 MW net-zero redispatch across 6–8 generators.

---

## Current Limitations

- **Generator-only control** — no load adjustment variables or constraints.
- **No runtime configuration layer** — the former `OptAdjConfigureInfo` / per-unit override API has been removed; bounds come directly from `AclfGen.getPGenLimit()`.
- **No centralized feasibility diagnostics** — `isAllControl()` and section slack inspection are the primary infeasibility signals.
- **Full-scan N-1 path still enumerates all branch outages** — use the SSA-screened path (`OptAdjResultContainer` + `buildSsaSectionConstrain`) to constrain only identified overload pairs on large networks.

These are good candidates for the next iteration of architecture hardening.
