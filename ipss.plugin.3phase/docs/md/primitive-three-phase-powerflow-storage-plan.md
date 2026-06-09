# Primitive Three-Phase Power-Flow Storage Plan

Date: 2026-06-07

## Goal

Keep the existing object-oriented three-phase network model as the authoritative
model, but introduce a primitive execution layer for the fixed-point three-phase
power-flow hot loop. The target is the same design lesson used by OpenDSS:
objects own model data and topology, while repeated numerical work operates on
cached node indexes and contiguous primitive arrays.

The intended hot-loop storage layout is:

```text
double[] voltage;        // interleaved complex node voltages
double[] previousVoltage;
double[] rhs;            // interleaved complex current injections
double[] solvedVoltage;

bus sort n:
  Va.re = voltage[6*n]
  Va.im = voltage[6*n + 1]
  Vb.re = voltage[6*n + 2]
  Vb.im = voltage[6*n + 3]
  Vc.re = voltage[6*n + 4]
  Vc.im = voltage[6*n + 5]
```

The main performance objective is to remove `Complex` and `Complex3x1`
allocation from the repeated fixed-point loop, especially around current
injection, RHS composition, solved-voltage update, and mismatch checks.

## Design Principles

- The existing bus, load, generator, branch, control, and parser objects remain
  the public model.
- Primitive arrays are an internal execution view, rebuilt or refreshed only
  when model topology or static device parameters change.
- QSTS repeated-state runs should update mutable schedule values in descriptors,
  not rebuild object lists or sparse structures.
- Bus voltage, sequence voltage, branch current, and result objects are synced
  lazily according to post-solve output options.
- Unsupported dynamic/custom devices keep the existing object fallback path until
  they get explicit primitive descriptors.

## Phase 1: Primitive PF State

Add a `Primitive3PhasePowerFlowState` owned by the fixed-point solver/cache.

The state should contain:

```java
double[] voltage;
double[] previousVoltage;
double[] rhs;
double[] solvedVoltage;
int[] activeNonSwingSorts;
int[] swingSorts;
```

Responsibilities:

- Initialize primitive voltage arrays from existing bus `Complex3x1` voltages.
- Provide stable phase-offset helpers for bus sort numbers.
- Clear and compose RHS values without allocating complex objects.
- Sync primitive voltages back to bus objects only at convergence or output
  boundaries.

Success criteria:

- Existing fixed-point PF tests pass.
- Ckt24 voltage results match the current implementation.
- Profiling exposes primitive voltage update and mismatch timing separately.

## Phase 2: Primitive Voltage Update and Mismatch

Move solved-voltage update and mismatch checks from `Complex3x1` objects to
primitive arrays.

Current shape:

```java
Complex3x1 solved = yMatrix.getPrimitiveSolved3x1(sort);
bus.set3PhaseVotlages(solved);
```

Target shape:

```java
double oldRe = voltage[offset];
double newRe = solvedVoltage[offset];
mismatch = Math.max(mismatch, Math.abs(newRe - oldRe));
voltage[offset] = newRe;
```

Only after convergence should the solver call a sync method to update bus
objects for callers that need object-visible voltages.

Success criteria:

- Same convergence iteration count as the current solver.
- Same max-voltage mismatch within existing tolerances.
- `voltage_update_ms` decreases in the fixed-point PF profile.

## Phase 3: Solver Primitive Result Array API

Extend the primitive solver contract so the PF loop can consume solved voltages
as an interleaved primitive array.

Candidate API:

```java
interface PrimitiveComplex3x3ArrayEquation {
    void clearPrimitiveRhs();
    void setPrimitiveRhs3x1(int row,
            double aReal, double aImaginary,
            double bReal, double bImaginary,
            double cReal, double cImaginary);
    void solvePrimitiveRhs(boolean buildSymbolTable);
    double[] primitiveSolvedInterleaved();
}
```

Implementation direction:

- KLUSolveX should expose its existing `resultInterleaved` solve buffer.
- CSJ can copy `DZcsa` results once into a reusable `double[]` buffer.
- The existing `Complex3x1` result API remains for compatibility.

Success criteria:

- KLUSolveX and CSJ both pass focused fixed-point PF tests.
- `result_unpack_ms`, `equation_result_copy_ms`, and `voltage_update_ms` are
  reduced or eliminated in the profile.

## Phase 4: Primitive Current-Injection Descriptors

Build cached current-injection descriptors before the repeated solve loop.
Instead of iterating every non-swing bus and returning `Complex3x1` objects,
iterate only active injection devices and add directly to the RHS array.

Candidate descriptor shape:

```java
final class PrimitiveLoadInjection {
    int busSort;
    int phaseMask;
    LoadConnectionType connection;
    AclfLoadCode code;
    double pa, qa;
    double pb, qb;
    double pc, qc;
    double nominalKv;
    boolean openDssVoltageModel;

    void addCurrent(double[] voltage, double[] rhs);
}
```

The target hot loop:

```java
Arrays.fill(rhs, 0.0);

for (PrimitiveLoadInjection load : loadDescriptors) {
    load.addCurrent(voltage, rhs);
}

for (PrimitiveGenInjection gen : genDescriptors) {
    gen.addCurrent(voltage, rhs);
}

for (PrimitiveBoundaryInjection boundary : boundaryDescriptors) {
    boundary.addTo(rhs);
}
```

This should avoid:

- Scanning every active non-swing bus when many buses have no injection.
- Re-reading bus load/generator lists each iteration.
- Calling generic `IPhaseLoad.getEquivCurrInj(...)` in supported static cases.
- Allocating `Complex` and `Complex3x1` temporaries.
- Reading bus voltage objects during current injection.

Success criteria:

- Descriptor current matches existing `calc3PhEquivCurInj()` for sampled buses.
- Ckt24 and existing OpenDSS parser/PF comparison tests remain within tolerance.
- `current_injection_ms` and current-injection per-bus/device timing decrease.

## Phase 5: Generator and Fallback Descriptors

Add primitive descriptors for static generators and keep explicit fallback
handling for unsupported dynamic/custom devices.

Direction:

- Static generator descriptors compute `conj(S / V)` using primitive math.
- Dynamic/custom devices can continue through object methods until a primitive
  contract is added.
- The fallback path should be isolated and profiled so it is visible when a
  feeder cannot take the fully primitive route.

Success criteria:

- Static feeder QSTS runs use the primitive descriptor path.
- Dynamic/custom device cases retain current behavior.
- Profiles report descriptor counts and fallback counts.

## Phase 6: Dirty-State Handling

Add explicit invalidation flags:

```java
boolean topologyDirty;
boolean loadDescriptorDirty;
boolean generatorDescriptorDirty;
boolean injectionValueDirty;
boolean matrixDirty;
```

For QSTS:

- Load profile changes should update descriptor P/Q values.
- Generator/storage schedule changes should update descriptor P/Q values.
- Topology, bus phase, or connection changes should rebuild descriptors.
- Voltage-only changes should not rebuild descriptors.

Success criteria:

- QSTS repeated-state runs do not rebuild descriptor arrays.
- Profile output distinguishes descriptor rebuild time from per-step injection
  time.
- Regulator/capacitor tests still rebuild or update only the required state.

## Phase 7: Lazy Output Sync

Keep output conversion separate from the hot loop.

Sync methods:

```java
syncBusVoltages();
syncPositiveSequenceVoltages();
syncBranchCurrents();
syncQstsResults();
```

These should run only when requested by post-solve output options.

Success criteria:

- Voltage-only QSTS does not sync branch currents or sequence values.
- Existing full-output modes still produce the expected bus and branch results.
- Output-sync profile buckets remain near zero in voltage-only benchmarks.

## To-Do List

- [x] Add `Primitive3PhasePowerFlowState` with voltage and previous-voltage
      arrays for the compatible fixed-point primitive path.
- [ ] Extend `Primitive3PhasePowerFlowState` with RHS, solved-voltage,
      active-sort, and swing-sort arrays.
- [x] Initialize primitive voltage arrays from the current bus voltage objects.
- [x] Add a controlled sync path from primitive voltage arrays back to bus
      objects.
- [x] Move fixed-point voltage update and mismatch checks to primitive arrays
      for `PrimitiveComplex3x3ArrayEquation` solvers when the device mix is
      static-load compatible.
- [ ] Add fixed-point profile buckets for primitive voltage update, mismatch,
      and sync.
- [x] Add a primitive solved-result array API for KLUSolveX.
- [x] Add a primitive solved-result array bridge for CSJ.
- [x] Keep the existing `Complex3x1` primitive solver API as a compatibility
      fallback.
- [ ] Build primitive load-injection descriptors for `Static3PLoad`.
- [x] Add a primitive static-load current overload using primitive voltage
      storage for the compatible path.
- [ ] Add sampled validation comparing descriptor currents with existing
      `calc3PhEquivCurInj()` results.
- [x] Change the fixed-point hot loop to iterate active current-injection buses
      instead of all non-swing buses when tap compensation is not active.
- [ ] Add primitive generator-injection descriptors for static generators.
- [ ] Isolate and profile fallback current injection for unsupported devices.
- [ ] Add descriptor dirty flags for topology, load, generator, and mutable
      injection values.
- [ ] Update QSTS state application to refresh descriptor values without
      rebuilding descriptors when only P/Q schedules change.
- [ ] Keep regulator and swing-boundary current contributions as primitive
      descriptor-style RHS additions.
- [ ] Add lazy sync methods for bus voltages, positive-sequence voltages, branch
      currents, and QSTS result sampling.
- [ ] Verify Ckt24 240-step QSTS with KLUSolveX.
- [x] Verify Ckt24 240-step QSTS with CSJ.
- [ ] Verify existing OpenDSS parser/PF comparison tests.
- [x] Document before/after profile results in the optimization history.

## Recommended First Slice

Start with primitive voltage state and solved-result array access before changing
load/generator descriptors. This keeps the behavior surface small and should
directly reduce `voltage_update_ms` and result-copy overhead. After that is
stable, move `Static3PLoad` injection into descriptors, which is the likely
largest remaining PF-loop win.

## Implementation Notes and Measurements

### 2026-06-07 Primitive Array and Voltage-State Slice

Implemented:

- `PrimitiveComplex3x3ArrayEquation` exposes solved values as
  `[a.re, a.im, b.re, b.im, c.re, c.im]` per bus sort.
- KLUSolveX exposes its existing interleaved result buffer.
- CSJ exposes the interleaved `DZcsa.x` solved array directly.
- Fixed-point PF can keep compatible static-load cases in primitive voltage
  arrays during iterations and sync bus voltage objects after convergence.
- `Static3PLoad` has a primitive voltage overload for current injection.
- The PF loop skips no-injection non-swing buses when regulator tap
  compensation is not active.

Validation:

- `mvn -pl ipss.core_EMF -DskipTests clean install`
- `mvn -pl ipss.plugin.3phase -Dtest=QstsLargeFeederPerformanceBenchmark -Dqsts.perf.case=ckt24 -Dqsts.perf.warmupSteps=0 -Dqsts.perf.steps=1 -Dqsts.perf.repeats=1 -Dipss.sparse.solver=csj -Dipss.fixedpoint.profile=true -Dsurefire.failIfNoSpecifiedTests=false clean test`
- `mvn -pl ipss.plugin.3phase -Dtest=QstsLargeFeederPerformanceBenchmark -Dqsts.perf.case=ckt24 -Dqsts.perf.warmupSteps=24 -Dqsts.perf.steps=240 -Dqsts.perf.repeats=3 -Dipss.sparse.solver=csj -Dsurefire.failIfNoSpecifiedTests=false test`

Profile result after primitive voltage state, static-load primitive overload,
and no-injection bus skip:

```text
attempts=2, converged_attempts=2, iterations=14
save_voltages_ms=0.149
current_injection_ms=30.516
current_injection_breakdown_ms regulator=0.021, calc=10.025, rhs=15.416, buses=52108
current_calc_deep_ms voltage=0.000, load_current=4.267, loads=52094
solve_ms=26.700
voltage_update_ms=8.012
per_iteration_ms save_voltages=0.010637
per_iteration_ms current_injection=2.179711
per_iteration_ms solve=1.907170
per_iteration_ms voltage_update=0.572298
current_injection_per_iteration_ms calc=0.716097, rhs=1.101164
current_calc_deep_per_iteration_ms load_current=0.304761
```

Compared with the prior profiled CSJ path, this reduces the main PF-loop object
work:

- `save_voltages`: about `0.25 ms/iter` to `0.01 ms/iter`.
- `current_injection`: about `2.5 ms/iter` to `2.18 ms/iter`.
- `voltage_update`: about `0.79 ms/iter` to `0.57 ms/iter`.
- `load_current`: about `0.38 ms/iter` to `0.30 ms/iter`.

The CSJ 240-step end-to-end benchmark remains noisy and is not yet a clear
aggregate win:

```text
Before this slice, best recent CSJ run:
INTERPSS_QSTS_PERF_AGG feeder=Ckt24 runs=3 avgMsPerStep=5.609116 medianMsPerStep=5.470859 minMsPerStep=5.412824

After this slice, repeated CSJ runs:
INTERPSS_QSTS_PERF_AGG feeder=Ckt24 runs=3 avgMsPerStep=5.855288 medianMsPerStep=5.712991 minMsPerStep=5.649939
INTERPSS_QSTS_PERF_AGG feeder=Ckt24 runs=3 avgMsPerStep=6.226260 medianMsPerStep=6.173749 minMsPerStep=6.003376
```

KLUSolveX could not be benchmarked in this shell because the native library was
not loadable without `ipss.klusolvex.library.path` or
`ipss.klusolvex.library.name`.

Next highest-value work:

- Convert RHS composition/write to a true primitive RHS backing array and flush
  by row or array into the solver.
- Replace the current bus-loop static-load fast path with cached load
  descriptors so the loop iterates loads directly.
- Add sampled current validation before expanding primitive descriptors beyond
  `Static3PLoad`.
