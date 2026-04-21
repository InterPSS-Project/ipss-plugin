# Optimization Adjustment (`optadj`) Architecture

## Purpose

The `optadj` module computes coordinated generator/load adjustments that reduce branch overloads under DC load flow assumptions.

At a high level, it:

1. Runs on top of an existing `DclfAnalysisAlgorithm/ContingencyAnalysisAlgorithm` result context.
2. Builds linear constraints from branch-flow sensitivities.
3. Solves a linear optimization problem with slack variables.
4. Writes adjustments back to DCLF algorithm entities (`DclfAlgoGen` / `DclfAlgoLoad`).

---

## Main Design

The implementation is layered to keep network sensitivity, optimization model construction, and scenario-specific logic separate.

- **Algorithm layer** (`org.interpss.plugin.optadj.algo`)
  - Orchestrates end-to-end optimization flow.
  - Builds control sets and constraints for each scenario.
- **Sensitivity utility layer** (`org.interpss.plugin.optadj.algo.util`)
  - Computes `GFS` and `LODF` matrices.
  - Provides index mappings between network sort numbers and compact optimization vectors.
- **Optimization engine layer** (`org.interpss.plugin.optadj.optimizer`)
  - Stores constraints.
  - Builds and solves the LP model using `ojAlgo`.
- **Configuration layer** (`org.interpss.plugin.optadj.config`)
  - Adds user-defined unit limits and disabled controls.

---

## Key Components

### Base orchestration

- `BaseAclfNetOptimizer`
  - Owns `dclfAlgo`, optional optimization-size limits, and a `BaseStateOptimizer`.
  - Validates that both DCLF algorithm and its network are present.

### Scenario optimizers

- `AclfNetGenLoadOptimizer`
  - Core implementation for base-case branch overload mitigation.
  - Uses `AclfNetGFSsHelper` to build section constraints from generator/load sensitivities.
  - Supports optional SSA-driven control-set filtering via `AclfNetSsaResultContainer`.

- `AclfNetContigencyOptimizer`
  - Extends base-case constraints with N-1 style contingency constraints.
  - Uses `AclfNetLODFsHelper` and applies:
    - `postFlow = baseFlow + LODF * outagedFlow`
    - `postContingencySensitivity = GSF(mon) + LODF * GSF(outaged)`

- `AclfNetATCOptimizer`
  - Specialization for transfer capability style runs.
  - Uses explicit control sets (`setControlGenSet`, `setControlLoadSet`).
  - Applies broad signed bounds to generator/load decision variables.

- `AclfNetBusOptimizer`
  - Bus-centric variant (control variable per bus instead of per generator).
  - Adds caching (`DclfAlgoBranch`, base MVA) and filters by sensitivity thresholds.
  - Distributes optimized bus adjustment back to each generator proportionally.

### Optimization model

- `BaseStateOptimizer`
  - Collects device and section constraints.
  - Exposes `optimize()`, result vector (`getPoint()`), and objective value.
  - Accepts runtime control limits through `addConfigure(OptAdjConfigureInfo)`.

- `GenStateOptimizer`
  - Concrete LP builder/solver.
  - Constructs variables for:
    - decision adjustments (`x`)
    - section slacks (`s`)
    - absolute adjustment auxiliaries (`|x|`)
    - absolute net imbalance (`|sum(x)|`)
  - Solves a weighted minimization objective.

### Constraint data objects

- `DeviceConstrainData`
  - Single-variable bound in transformed form (`x <= b` or `x >= b`).

- `SectionConstrainData`
  - Linear branch/section constraint using sensitivity vector.
  - Includes duplicate-coefficient perturbation (`makeUnique`) to improve numerical behavior in repeated coefficient patterns.

### Sensitivity matrix and helpers

- `Sen2DMatrix`
  - Sparse-like indexed 2D sensitivity container with row/column re-indexing support.

- `AclfNetGFSsHelper`
  - Computes GFS matrices for:
    - all active buses/branches
    - selected buses
    - selected buses + selected monitored branches
    - mixed generator/load control sets

- `AclfNetLODFsHelper`
  - Computes LODF matrices for:
    - all active outage/monitor branches
    - selected outage branches
    - selected outage + selected monitor branches

---

## End-to-End Execution Flow

Typical flow in `AclfNetGenLoadOptimizer.optimize(...)`:

1. Initialize optimizer (`GenStateOptimizer`) if absent.
2. Build candidate control sets (`AclfGen`, `AclfLoad`).
3. Compute GFS matrix (`calGenLoadGFS`).
4. Optionally reduce control generators using SSA over-limit branches.
5. Build compact control index maps.
6. Build section constraints from branch flows and sensitivities.
7. Build device constraints from generator/load limits.
8. Solve LP via `BaseStateOptimizer.optimize(...)`.
9. Apply solved adjustments back to DCLF model (`setAdjust(...)`).
10. Expose results via `getResultMap()`.

---

## Mathematical Form (as implemented)

The model is built in MW-domain and approximates overload correction with sensitivity linearization.

- **Section constraints**
  - For each monitored branch:
    - `sum(Sen_i * x_i) - s_k <= limit_k - flow_k` (for LEQ form)
    - `s_k >= 0`
- **Device constraints**
  - For each control variable `x_i`:
    - lower and upper bounds translated from current state and min/max capability.

- **Objective**
  - Minimize:
    - section slack penalty + adjustment magnitude penalty + net-balance penalty
  - In `GenStateOptimizer` this is implemented as:
    - `sum(s_k / senLimit) + 0.5 * sum(|x_i|) + |sum(x_i)|`

This objective prioritizes removing overload violations while discouraging unnecessary redispatch and preserving net balance.

---

## Configuration and Runtime Overrides

- `OptAdjConfigureInfo`
  - Adds unit-level runtime constraints.
  - Supports:
    - disable control for a unit (`pMin = pMax = origin`)
    - only max bound override
    - only min bound override
  - Requires index mapping (`fillIndex`) before applying.

- `BaseStateOptimizer.addConfigure(...)`
  - Converts configured limits into `DeviceConstrainData` constraints.

---

## Extension Points

Recommended extension patterns:

1. **New optimization scenario**
   - Extend `AclfNetGenLoadOptimizer` or `BaseAclfNetOptimizer`.
   - Override:
     - control-set builders
     - section/device constraint builders
     - result application logic if needed

2. **Alternative optimization engine**
   - Implement new `BaseStateOptimizer` subclass.
   - Keep algorithm orchestration intact while swapping LP/QP objective behavior.

3. **Custom screening rules**
   - Tune sensitivity thresholds.
   - Pre-filter monitored branches or outage sets before constraint generation.

4. **Post-processing policies**
   - Add ramp-rate, area-balance, or market rules as extra constraints in optimizer layer.

---

## Operational Notes

- Sensitivity helpers and several loops use `parallelStream()`. Maintain thread-safety when adding mutable shared state.
- Most constraints are assembled in MW then converted back to PU only when writing adjustments to DCLF objects.
- `SEN_THRESHOLD` and related constants heavily influence solvability and control-set size.
- The optimization may terminate with tiny non-zero values; current code uses thresholds before applying adjustments.

---

## Current Limitations / TODOs Seen in Code

- `AclfNetGenLoadOptimizer.buildLoadConstrain()` is currently a stub.
- `AclfNetATCOptimizer` uses broad hardcoded bounds (`+/-1000`) that may need domain-specific tightening.
- There is no centralized feasibility diagnostics surface (for example, conflict explanations per constraint family).

These are good candidates for the next iteration of architecture hardening.

