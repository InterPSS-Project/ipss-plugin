# Optimization Adjustment (`optadj`) Architecture

## Purpose

The `optadj` module computes coordinated **generator dispatch adjustments** that reduce branch or section overloads under DC load flow (DCLF) assumptions.

At a high level, it:

1. Runs on top of an existing `ContingencyAnalysisAlgorithm` (or ACLF network for section optimization).
2. Builds linear constraints from bus-to-branch power-flow sensitivities.
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
│   ├── util/        # Sensitivity matrix helpers
│   └── bean/        # PowerSystemSection model
├── optimizer/       # GenStateOptimizer LP engine and constraint beans
├── cluster/         # Generator clustering for model reduction
└── result/          # SSA over-limit result container (JSON-serializable)
```

---

## Main Design

The implementation separates network sensitivity, optimization model construction, and scenario-specific orchestration.

| Layer | Package | Responsibility |
|---|---|---|
| **Scenario orchestration** | `algo.lf`, `algo.sec` | Build control sets and constraints for base case, N-1, or user-defined sections |
| **Sensitivity utilities** | `algo.util` | Compute bus-to-branch sensitivity matrices (dense or sparse) |
| **LP engine** | `optimizer` | Collect constraints, preprocess (merge/cluster), build and solve the LP |
| **Model reduction** | `cluster` | Cluster generators with identical sensitivity profiles |
| **SSA screening input** | `result` | Optional over-limit branch lists to shrink the control set |

---

## Key Components

### Load-flow optimizers (`algo.lf`)

#### `AclfNetLoadFlowOptimizer`

Core implementation for **base-case** branch overload mitigation.

- Entry point: `optimize(ContingencyAnalysisAlgorithm dclfAlgo, SsaResultContainer result, double threshold)`
- `threshold` is a loading limit in percent (e.g. `100.0` for 100% of rating).
- Returns `Map<String, GenAdjustResult>` keyed by generator name.
- `GenAdjustResult` is a record: `(genName, genP, dP, genLimit)` where `dP` is the dispatch change in MW.
- When `result` is `null`, all active generators are control candidates.
- When `result` is non-null, control generators are filtered to those with `|sensitivity| > SEN_THRESHOLD` (0.02) on branches listed in the SSA container.

Flow:

1. Compute bus-to-branch sensitivity matrix via `AclfNetSensHelper`.
2. Build control generator index map.
3. Add section constraints for every active branch with meaningful sensitivity.
4. Add generator P-min / P-max device constraints.
5. Solve via `GenStateOptimizer`.
6. Apply non-trivial adjustments (`|dP| > 1 MW`) to `DclfAlgoGen`.

#### `AclfNetContigencyOptimizer`

Extends `AclfNetLoadFlowOptimizer` for **N-1 contingency** overload mitigation.

- Calls `super.buildSectionConstrain(...)` for base-case constraints, then adds post-contingency constraints for every active branch outage.
- Uses `dclfAlgo.lineOutageDFactors(...)` for LODF.
- Post-contingency flow: `postFlow = baseFlow + LODF * outagedFlow`
- Post-contingency sensitivity: `GSF(mon) + LODF * GSF(outaged)`
- Contingency constraints use `RatingMvaB`; base-case constraints use `RatingMvaA`.

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

Sparse sensitivity matrix for section optimization.

- `calSenSortNumber()` — full network
- `calSenSortNumber(Set<String> busSet, Set<String> branchSet)` — scoped to section buses/branches
- Returns `DMatrixSparseCSC` indexed by bus/branch sort numbers

### SSA result container (`result`)

#### `SsaResultContainer`

JSON-serializable container for steady-state analysis (SSA) over-limit results. Used to pre-filter the control generator set.

- `baseOverLimitInfo` — base-case overloaded branches
- `caOverLimitInfo` — contingency overloaded branches (includes outage branch ID)
- Loading thresholds: `baseLoadingThreshold`, `caLoadingThreshold`

#### `SsaBranchOverLimitInfo`

Per-branch over-limit record with branch ID, limit MW, flow MW, and optional outage branch ID for contingency cases.

### Generator clustering (`cluster`)

#### `GeneratorClustering`

Clusters generators whose sensitivity profiles differ by less than a threshold. Generators are clustered only when their objective weights are identical. Reduces LP variable count while distributing solved cluster adjustments back to individual generators proportionally by capacity range.

---

## End-to-End Execution Flows

### Base-case load-flow optimization

Typical flow in `AclfNetLoadFlowOptimizer.optimize(...)`:

1. Compute sensitivity matrix (`AclfNetSensHelper.calSen()`).
2. Build control generator set (all active, or SSA-filtered).
3. Assign compact indices to control generators.
4. Build section constraints from branch flows, ratings, and sensitivities.
5. Build generator P-min / P-max constraints.
6. Solve LP via `GenStateOptimizer.optimize()`.
7. Apply solved adjustments to `DclfAlgoGen` (`setAdjust(dP / baseMva)`).
8. Return `Map<String, GenAdjustResult>`.

After optimization, re-run DCLF to validate loading:

```java
dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
```

### N-1 contingency optimization

Same as base case, plus `AclfNetContigencyOptimizer` adds post-contingency section constraints for every active branch outage using LODF.

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

### Samples (`src/sample/java/org/interpss/optadj/ieee39/`)

| Class | Scenario |
|---|---|
| `IEEE39_OptBasecase_Sample` | Base-case overload mitigation with `AclfNetLoadFlowOptimizer` |
| `IEEE39_OptN1Scan_Sample` | N-1 scan + `AclfNetContigencyOptimizer` |
| `IEEE39_OptBasecase_SsaResult_Sample` | Base-case with SSA-filtered control set |
| `IEEE39_OptN1Scan_SsaResult_Sample` | N-1 with SSA-filtered control set |
| `IEEE39_OptSection_Sample` | Iterative section-limit optimization |
| `IEEE39_Sample_Data` | Shared IEEE-39 test case factory (600 MVA uniform ratings) |

### Regression tests (`ipss.test.plugin.core/.../optadj/`)

| Test class | Validates |
|---|---|
| `IEEE39_OptBasecase_Test` | Base-case optimizer reduces overloaded branches (~362 MW redispatch) |
| `IEEE39_OptN1Scan_Test` | N-1 optimizer reduces contingency violations (51 → ~6–8) |
| `IEEE39_OptBasecase_SsaResult_Test` | SSA-screened base-case path |
| `IEEE39_OptN1Scan_SsaResult_Test` | SSA-screened N-1 path |
| `IEEE39_OptSection_Test` | Section optimizer on IEEE-39 |
| `IEEE14_SensHelper_Test` | Sensitivity helper correctness on IEEE-14 |

Run a single test:

```bash
mvn -pl ipss.test.plugin.core test -Dtest=IEEE39_OptBasecase_Test
```

---

## Extension Points

1. **New optimization scenario**
   - Extend `AclfNetLoadFlowOptimizer` (override `buildSectionConstrain`, `buildControlGenSet`, or `buildGenConstrain`).
   - Or add a new orchestrator alongside `SectionOptimizer`.

2. **Alternative LP engine**
   - Replace or wrap `GenStateOptimizer` while keeping the constraint bean API (`GenConstrainData`, `SectionConstrainData`).

3. **Custom screening rules**
   - Tune `SEN_THRESHOLD` (0.02 in optimizers, 0.02/0.1 in `GenStateOptimizer`).
   - Pre-filter branches/outages before constraint generation.
   - Populate `SsaResultContainer` from an external SSA pipeline.

4. **Load control**
   - Not yet implemented. Would require new decision variables, sensitivity columns, and result application to `DclfAlgoLoad`.

5. **Post-processing policies**
   - Add ramp-rate, area-balance, or market rules as extra `GenConstrainData` / `SectionConstrainData` rows.

---

## Operational Notes

- Sensitivity helpers and several loops use `parallelStream()`. Avoid mutable shared state in stream callbacks.
- Constraints are assembled in MW; adjustments are converted to per-unit (`dP / baseMva`) when writing to DCLF objects.
- `SEN_THRESHOLD` (0.02) and `GEN_DISPATCH_THRESHOLD` (1.0 MW) control solvability and result reporting.
- `GenStateOptimizer` prints section-merge statistics to stdout during preprocessing.
- Re-run DCLF after applying adjustments to validate that overloads are actually reduced.
- On IEEE-39 with 600 MVA uniform ratings at 100% loading limit, base-case optimization typically moves ~362 MW net-zero redispatch across 6–8 generators.

---

## Current Limitations

- **Generator-only control** — no load adjustment variables or constraints.
- **No runtime configuration layer** — the former `OptAdjConfigureInfo` / per-unit override API has been removed; bounds come directly from `AclfGen.getPGenLimit()`.
- **No centralized feasibility diagnostics** — `isAllControl()` and section slack inspection are the primary infeasibility signals.
- **Contingency optimizer scans all branch outages** — no outage pre-screening beyond the optional SSA control-set filter.

These are good candidates for the next iteration of architecture hardening.
