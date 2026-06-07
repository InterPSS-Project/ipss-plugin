# Three-Phase Voltage Storage and Conversion Optimization Plan

## Goal

Reduce fixed-point distribution power-flow overhead caused by repeated Java
`Complex` object creation, abc-to-sequence conversion, and rectangular-to-polar
voltage conversion, while keeping the existing public bus and sparse-equation
APIs compatible.

This plan is intentionally staged. The first stages optimize internal storage
and lazy conversion only. The larger solver/PF primitive-vector API should not
start until these stages are reviewed and approved.

## Current Hot-Path Behavior

The fixed-point solve currently follows this shape:

1. Build or reuse the Y matrix.
2. For every iteration:
   - Save old bus `Vabc` values for convergence checking.
   - Build RHS current injections from bus/device state.
   - Solve the sparse equation.
   - Read solved `Complex3x1` values from the sparse equation.
   - Store each solved `Vabc` into the three-phase bus.
   - Historically, also recompute positive-sequence voltage and call
     `bus.setVoltage(...)` each iteration.
3. After convergence:
   - Update branch currents and swing generation.
   - Export/report code may compute magnitude/angle.

The expensive pieces are:

- `Complex3x1.abc_to_z12(...)` / `to012()` create multiple `Complex` objects and
  do several complex multiplies/adds.
- `BaseAclfBusImpl.setVoltage(Complex)` computes `abs()` and `getArgument()`.
  `getArgument()` is effectively an `atan2`.
- The sparse solver result path still materializes Java `Complex` objects for
  the equation and bus state.
- Some code asks for sequence voltage or magnitude/angle even when the next PF
  iteration only needs phase-domain `Vabc`.

## Design Principles

- Keep phase-domain `Vabc` as the primary fixed-point PF state.
- Treat positive-sequence voltage and magnitude/angle as derived data.
- Calculate derived data lazily, and only sync it eagerly at well-defined API
  boundaries.
- Preserve existing external APIs:
  - `IBus3Phase.get3PhaseVotlages()`
  - `DStab3PBus.getThreeSeqVoltage()`
  - `BaseAclfBus.getVoltage()`, `getVoltageMag()`, `getVoltageAng()`
- Avoid spreading raw `double[]` indexing throughout device models.
- Keep CSJ and KLUSolveX paths behaviorally equivalent.

## Stage 1: Lazy Sequence Voltage Cache

Status: initial implementation started.

Implemented shape:

- `DStab3PBusImpl.set3PhaseVotlages(vabc)` stores `Vabc` and marks
  `threeSeqVoltage` invalid.
- `DStab3PBusImpl.getThreeSeqVoltage()` computes abc-to-sequence only if the
  cache is invalid.
- Fixed-point PF no longer forces `bus.setVoltage(...)` for every solved bus on
  every iteration.
- Fixed-point PF syncs positive-sequence/base-bus voltage once after convergence
  before branch-current and swing-generation calculations.

Verification:

- Ckt24 DSS comparison passes.
- Expected performance effect: per-iteration solved-voltage update avoids
  sequence conversion and polar conversion.

Remaining checks before broad acceptance:

- Run IEEE13 and IEEE123 fixed-point comparisons.
- Run at least one regulator/capacitor control case where controls execute
  during outer control iterations.
- Confirm no downstream logic expects `BaseAclfBus.getVoltageMag()` to reflect
  the latest fixed-point iteration before convergence.

Potential issue:

- If a control or diagnostic reads `getVoltageMag()` inside the fixed-point
  inner iteration, it may see the previous synced positive-sequence voltage.
  Current control code appears to use `IBus3Phase.get3PhaseVotlages()` for
  local voltage measurement, but this should be verified with focused tests.

## Stage 2: Lazy Positive-Sequence/Base Voltage Sync Boundary

Problem:

`BaseAclfBus` still stores voltage in rectangular and polar fields. Calling
`setVoltage(Complex)` updates all of them and computes `abs()` and angle.

Plan:

1. Add an explicit sync helper in the distribution PF implementation:

   ```java
   private void syncPositiveSequenceBusVoltages()
   ```

2. Call it only at boundaries where base-bus positive-sequence state is needed:
   - after fixed-point convergence;
   - before branch-current and swing-generation result calculations;
   - before public result/export workflows if they can be reached without a
     convergence sync;
   - before any legacy API path that reads `BaseAclfBus.getVoltage*()` for
     solved distribution state.

3. Do not call it inside the inner fixed-point iteration unless a future control
   mode proves it needs base-bus positive-sequence state.

Verification:

- Compare final bus positive-sequence voltage, branch currents, and swing power
  before/after the change.
- Add a regression test that calls `getVoltage()`, `getVoltageMag()`, and
  `getVoltageAng()` after convergence and confirms they match `Vabc.to012().b_1`.

Potential issues:

- Legacy code may read base voltage during convergence.
- The sync point must happen before result export and before any swing power
  calculation that uses base-bus voltage.
- FBS power flow may have different state requirements from fixed-point PF; do
  not change FBS sync behavior until tested separately.

## Stage 3: Primitive Phase-Voltage Cache Inside Bus

Problem:

Even with lazy sequence conversion, `Vabc` is currently a `Complex3x1`, which
stores three Apache `Complex` objects. Every solved bus update still moves data
through object containers.

Plan:

Add primitive phase-voltage fields to `DStab3PBusImpl`:

```java
private double vaRe;
private double vaIm;
private double vbRe;
private double vbIm;
private double vcRe;
private double vcIm;
private boolean phaseVoltagePrimitiveValid;
private boolean phaseVoltageObjectValid;
private boolean threeSeqVoltageValid;
```

Behavior:

- `set3PhaseVotlages(Complex3x1 vabc)` updates primitive fields and stores or
  invalidates the object cache.
- `get3PhaseVotlages()` materializes `Complex3x1` only if the object cache is
  invalid.
- Add package-private or interface-level helpers for hot code:

```java
double phaseVoltageReal(int phase);
double phaseVoltageImag(int phase);
void setPhaseVoltages(
    double vaRe, double vaIm,
    double vbRe, double vbIm,
    double vcRe, double vcIm);
```

Recommended first implementation:

- Keep `get3PhaseVotlages()` returning the existing `Complex3x1` type.
- Avoid changing device models initially.
- Use primitive setters only in the solver result update path.

Verification:

- Unit test bus cache coherence:
  - set object -> read primitive -> read object;
  - set primitive -> read object -> read sequence;
  - set sequence -> read object -> read primitive.
- Run fixed-point PF tests under CSJ and KLUSolveX.

Potential issues:

- Direct mutation risk: existing code can mutate fields on the returned
  `Complex3x1`. If `get3PhaseVotlages()` returns a cached object and callers
  mutate `a_0`, `b_1`, or `c_2`, primitive fields may become stale.
- To avoid breaking existing code, Stage 3 should either:
  - continue returning the cached object and accept this risk temporarily; or
  - return a defensive copy and audit callers that expect object identity.
- Defensive copies reduce mutation risk but add allocation, so this needs a
  measured decision.

## Stage 4: Primitive Solver Result to Bus Update

Problem:

KLUSolveX already returns native interleaved complex doubles. The adapter still
copies those values into Java equation objects before the PF loop updates bus
voltages.

Plan:

1. Add a narrow solver fast path:

   ```java
   interface PrimitiveComplexMatrix3x3Solve {
       void solveInto(double[] rhsInterleaved, double[] resultInterleaved)
           throws IpssNumericException;
   }
   ```

2. Keep the current sparse-equation API as fallback.

3. In fixed-point PF:
   - build RHS into a reusable `double[]`;
   - call `solveInto(...)` when available;
   - update each bus primitive voltage fields directly from the result array;
   - skip equation `getX(...)` and `setBi(new Complex...)` result materialization.

4. Keep the current object path for CSJ and any solver that does not implement
   the primitive interface.

Verification:

- Compare primitive fast path against the current object path for Ckt24,
  IEEE13, IEEE123, and one regulator/capacitor case.
- Verify final public API state after convergence is still synced:
  `get3PhaseVotlages()`, `getThreeSeqVoltage()`, `getVoltageMag()`,
  `getVoltageAng()`.

Potential issues:

- Row/phase indexing errors become easier with `double[]`.
- The matrix equation dimension is bus-level 3x3, while KLUSolveX scalar output
  is flattened; the row mapping must be centralized.
- Existing diagnostics that inspect the sparse equation result vector may not
  see updated values in the primitive path unless explicitly synchronized.

## Stage 5: Primitive Derived Quantities

Problem:

Some controls and result calculations only need magnitude or current/power from
phase voltages. Building `Complex3x1` objects first is unnecessary.

Plan:

Add primitive utility methods around bus voltage state:

```java
double phaseVoltageMagnitude(int phase);
double phaseVoltageMagnitudeSquared(int phase);
double positiveSequenceReal();
double positiveSequenceImag();
double positiveSequenceMagnitude();
double positiveSequenceAngle();
```

Use these only in hot control/result paths after correctness is proven.

Verification:

- Replace one hot user at a time.
- Each replacement needs a before/after numerical parity test.

Potential issues:

- Reimplementing complex formulas manually increases risk.
- Some controls may intentionally need actual phase voltage, while others need
  physical line-neutral magnitude after base-voltage scaling. Keep units explicit.

## Measurement Plan

Add profiling counters around:

- sparse RHS pack;
- native solve;
- result unpack;
- bus `Vabc` update;
- sequence sync;
- base voltage polar sync;
- control measurement;
- branch-current update;
- swing-generation update.

Suggested profile output:

```text
pf_rhs_ms=
pf_solve_ms=
pf_result_to_bus_ms=
pf_sequence_sync_ms=
pf_polar_sync_ms=
pf_control_ms=
pf_branch_current_ms=
pf_swing_power_ms=
```

Reason:

The current KLUSolveX profile measures solver-adapter time. It does not isolate
bus update, sequence conversion, or polar conversion. These counters are needed
before approving Stage 4 or Stage 5.

## Latest Ckt24 Profiling Results

Date: 2026-06-07

Test command:

```bash
mvn -pl ipss.plugin.3phase test \
  -Dtest=OpenDssParserPowerFlowComparisonTest#ckt24VoltageDepthExportDiagnostic \
  -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dipss.sparse.solver=klusolvex \
  -Dipss.klusolvex.library.path=/Users/ipssdev/github/klusolve/build/libklusolvex.dylib \
  -Dipss.klusolvex.profile=true
```

Correctness result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
iterations=8
max |V| error 0.002929 pu at ckt24:n283892.3
max angle error 0.331513 deg at ckt24:g2100bk4500_n283756_sec.2
```

Latest KLUSolveX adapter profile:

```text
matrix_full_builds=1
matrix_nonzeros_seen=43485
matrix_traversal_ms=3.715
native_matrix_ms=44.272
primitive_calls=18177
factor_calls=1
factor_ms=1.490
solve_calls=9
rhs_pack_ms=4.498
rhs_collect_ms=4.287
rhs_native_write_ms=0.211
native_solve_ms=1.179
result_unpack_ms=0.143
complex_result_ms=0.000
equation_result_copy_ms=4.584
equation_result_create_ms=2.023
equation_result_store_ms=2.561
```

Focused core verification:

```bash
mvn -pl ipss.test.core -am clean \
  -Dtest=SparseEqnSolverProviderTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dipss.klusolvex.library.path=/Users/ipssdev/github/klusolve/build/libklusolvex.dylib \
  test
```

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 1
```

Interpretation:

- `rhs_pack_ms` is almost entirely Java-side sparse-equation collection:
  `rhs_collect_ms=4.287` versus `rhs_native_write_ms=0.211`.
- The native solve itself is already small at `native_solve_ms=1.179`.
- Bulk result unpack from native memory is also small at
  `result_unpack_ms=0.143`.
- The remaining result cost is Java object/state materialization:
  `equation_result_create_ms=2.023` for Apache `Complex` creation and
  `equation_result_store_ms=2.561` for storing those objects back into sparse
  equation rows.
- A direct row-store experiment did not improve the Ckt24 profile, so this plan
  should not treat `setBi(...)` dispatch as the primary issue. The larger win is
  avoiding equation-row `Complex` materialization for the fixed-point result
  path.

Optimization conclusion:

The current adapter-side native boundary is no longer the main solve-loop
problem for RHS/result transfer. The next meaningful target is a primitive
RHS/result path that bypasses the generic sparse equation `Complex` row storage
for fixed-point PF, then writes directly into primitive bus voltage state after
the Stage 3 bus cache is approved.

## Approval Gates

Do not move to the larger primitive solver/PF API until these are approved:

1. Stage 1 and Stage 2 pass Ckt24, IEEE13, IEEE123, and a control case.
2. Profiling shows sequence/polar sync no longer dominates the per-iteration
   solved-voltage update.
3. Public API post-convergence voltage state is proven coherent.
4. The mutation policy for `get3PhaseVotlages()` is decided.
5. The row/phase mapping helper for primitive arrays is reviewed.

## Recommended Next Step

Before implementing Stage 3 or Stage 4, add the profiling counters from the
Measurement Plan. That will show whether the next bottleneck is still bus
conversion, sparse equation result materialization, device current injection, or
branch-current/swing-power result calculation.
