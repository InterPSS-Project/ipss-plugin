# Fixed-Point Distribution Power Flow Implementation Plan

## Goal

Implement an OpenDSS-style fixed-point iteration method for three-phase distribution power flow. This method should become the default distribution power-flow method and should solve:

```text
Ypf * Vnew = Iinj_pf(Vold)
```

where:

- `Ypf` is the power-flow network admittance matrix.
- `Vold` is the previous iteration's bus voltage vector.
- `Iinj_pf(Vold)` is the voltage-dependent power-flow current injection vector.
- `Vnew` is the solved voltage vector for the next iteration.

This method is intended for distribution snapshot, daily, and duty-cycle style power-flow simulation.

## Core Design Rule

The power-flow Y-matrix must be separate from the stability simulation Y-matrix.

The existing three-phase stability network matrix can include dynamic model equivalent admittances. The fixed-point power-flow matrix must not include dynamic generator, dynamic load, motor, controller, or other stability-model contributions.

Power-flow Y-matrix contents:

- Include passive linear network components.
- Include line and transformer primitive admittances.
- Include shunts, capacitors, and reactors.
- Include valid static constant-impedance load or generator portions if explicitly modeled as admittance.

Power-flow Y-matrix exclusions:

- Dynamic generator Norton admittances.
- Dynamic load admittances.
- Induction motor equivalent admittances.
- Stability simulation current-source or admittance equivalents.
- Any contribution produced only by dynamic model initialization.

## Existing Classes

### `org.interpss.threePhase.powerflow.DistributionPFMethod`

Current enum:

```java
Forward_Backword_Sweep, Fast_Decoupled, Newton_Raphson
```

Required change:

```java
Fixed_Point, Forward_Backword_Sweep, Fast_Decoupled, Newton_Raphson
```

`Fixed_Point` should become the default method.

### `org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm`

This interface already exposes the required public power-flow controls:

- `powerflow()`
- `getPFMethod()`
- `setTolerance(double tolerance)`
- `getTolerance()`
- `setMaxIteration(int maxIterNum)`
- `getMaxIteration()`
- `setInitBusVoltageEnabled(boolean enableInitBus3PhaseVolts)`
- `isInitBusVoltageEnabled()`

No required interface change is expected for the first implementation, unless a setter for `DistributionPFMethod` is added later.

### `org.interpss.threePhase.powerflow.impl.DistributionPowerFlowAlgorithmImpl`

This is the primary implementation target.

Current responsibilities already useful for fixed-point power flow:

- Bus ordering through `orderDistributionBuses(boolean radialOnly)`.
- Three-phase voltage initialization through `initBusVoltages()`.
- Existing power-flow dispatch through `powerflow()` and `powerflow_singleNet(...)`.
- Existing forward/backward sweep implementation through `FBSPowerflow()`.
- Existing convergence tolerance and maximum iteration fields.
- Existing source generation update through `calcSwingBusGenPower()`.

Required additions:

- Set default `pfMethod` to `DistributionPFMethod.Fixed_Point`.
- Add dispatch from `powerflow_singleNet(...)` to a new fixed-point method.
- Add `fixedPointPowerflow()`.
- Add helper methods for power-flow Y-matrix assembly and current-injection assembly.
- Add post-solve branch-current update if output or swing-power calculation depends on branch currents.

### `org.interpss.threePhase.dynamic.DStabNetwork3Phase`

This interface currently exposes:

- `formYMatrixABC()`
- `getYMatrixABC()`
- `solveNetEqn()`
- custom three-phase current injection table methods.

Recommended new API:

```java
ISparseEqnComplexMatrix3x3 formYMatrixABCForPowerflow();
ISparseEqnComplexMatrix3x3 getYMatrixABCForPowerflow();
```

These methods keep the power-flow matrix separate from the stability simulation matrix.

If the first implementation should be more surgical, equivalent private helpers can be added inside `DistributionPowerFlowAlgorithmImpl` first, then moved to the network class after validation.

### `org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl`

This class currently owns the stability-oriented `formYMatrixABC()` implementation.

Important current behavior:

- It builds a three-phase sparse matrix with `CSJSparseEqnComplexMatrix3x3Impl`.
- It includes branch admittance terms.
- It includes bus self admittance terms.
- It appends dynamic load equivalent admittances.
- It is used by stability simulation through `solveNetEqn()`.

Required change:

Add a power-flow-only matrix builder that excludes dynamic model contributions.

Recommended fields:

```java
protected ISparseEqnComplexMatrix3x3 yMatrixAbcPowerflow = null;
```

Recommended methods:

```java
@Override
public ISparseEqnComplexMatrix3x3 formYMatrixABCForPowerflow() throws IpssNumericException

@Override
public ISparseEqnComplexMatrix3x3 getYMatrixABCForPowerflow()
```

The implementation should assemble:

1. Active bus diagonal admittance.
2. Active branch off-diagonal admittance.
3. Static power-flow shunt and constant-Z terms only.

It should not execute the dynamic-device loop currently present in `formYMatrixABC()`.

### `org.interpss.threePhase.basic.dstab.DStab3PBus`

Current useful methods:

- `get3PhaseVotlages()`
- `set3PhaseVotlages(Complex3x1 vabc)`
- `calc3PhEquivCurInj()`
- `calcLoad3PhEquivCurInj()`
- `getYiiAbc()`
- `get3PhaseTotalLoad()`
- `get3PhaseNetLoadResults()`

Potential required addition:

```java
Complex3x3 getYiiAbcForPowerflow();
```

This avoids reusing a stability-oriented self-admittance method if `getYiiAbc()` contains load or model contributions that are not valid for power flow.

### `org.interpss.threePhase.basic.dstab.impl.DStab3PBusImpl`

This class currently implements `getYiiAbc()`.

The existing method mixes several concerns:

- Connected branch self admittances.
- Bus shunt admittance.
- Conventional sequence load equivalent admittance.
- Optional static load equivalent admittance when `isStaticLoadIncludedInYMatrix()` is true.

Required review:

Determine whether each section is valid for power flow, stability, or both.

Recommended change:

Extract common passive self-admittance assembly:

```java
private Complex3x3 calcPassiveYiiAbc()
```

Then implement:

```java
public Complex3x3 getYiiAbcForPowerflow()
public Complex3x3 getYiiAbc()
```

`getYiiAbcForPowerflow()` should include only passive and static admittance terms appropriate for distribution power flow. `getYiiAbc()` can retain stability simulation behavior.

### `org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl`

Current useful methods:

- `getEquivYabc()`
- `getEquivCurrInj(Complex3x1 vabc)`
- `get3PhaseLoad(Complex3x1 vabc)`

Power-flow usage:

- Constant-Z load portions can be represented in `Ypf`.
- Constant-P and constant-I portions should be represented through `Iinj_pf(Vold)`.
- Existing `getEquivCurrInj(...)` already computes negative load injection based on voltage and load code.

Required review:

Confirm that constant-Z loads are not double counted in both `Ypf` and current injection. If constant-Z is included in `Ypf`, the RHS injection for that same constant-Z load should be excluded or zeroed for the fixed-point method.

## Fixed-Point Algorithm

### High-Level Flow

In `DistributionPowerFlowAlgorithmImpl.powerflow_singleNet(...)`:

1. Set the active network.
2. Order distribution buses.
3. Initialize bus voltages if enabled.
4. Dispatch to `fixedPointPowerflow()` when `pfMethod == DistributionPFMethod.Fixed_Point`.
5. Set the network load-flow convergence flag.

### `fixedPointPowerflow()` Steps

1. Validate network type.

```java
if (!(this.distNet instanceof DStabNetwork3Phase)) {
    throw new UnsupportedOperationException("Fixed-point power flow requires DStabNetwork3Phase");
}
```

2. Build the power-flow Y-matrix once.

```java
DStabNetwork3Phase net3p = (DStabNetwork3Phase) this.distNet;
ISparseEqnComplexMatrix3x3 ypf = net3p.formYMatrixABCForPowerflow();
```

3. Apply source-bus boundary treatment.

Preferred formulation:

```text
Ynn * Vn = In(Vold) - Yns * Vs
```

where:

- `n` means non-swing buses.
- `s` means swing/source buses.
- `Vs` is fixed.

This is cleaner than a large source admittance and prevents the swing voltage from drifting.

4. Iterate until convergence.

Pseudo-code:

```java
for (int iter = 0; iter < maxIteration; iter++) {
    saveOldVoltages();

    clearRightHandSide();
    setPowerflowCurrentInjectionsFromOldVoltage();
    applySwingVoltageBoundaryContribution();

    solveLinearSystem();
    updateNonSwingBusVoltages();
    restoreSwingBusVoltages();

    double maxDeltaV = calcMaxVoltageMismatch();
    if (iter > 0 && maxDeltaV <= tol) {
        updateBranchCurrentsFromSolvedVoltages();
        calcSwingBusGenPower();
        return true;
    }
}

return false;
```

5. Report convergence using the existing tolerance.

Use max phase-voltage change:

```java
bus3p.get3PhaseVotlages().subtract(oldVabc).absMax()
```

## Swing Bus Treatment

The fixed-point method needs explicit voltage boundary handling. A direct full `Ypf * V = I` solve will not correctly preserve a fixed source voltage unless the source is handled.

Preferred approach:

1. Identify active swing buses.
2. Keep their `Vabc` fixed during the fixed-point loop.
3. Build or solve a reduced linear system for non-swing buses.
4. Move source-bus terms to the RHS:

```text
Ireduced = Inonlinear - Yns * Vs
```

Implementation options:

- Build a reduced sparse matrix directly with non-swing bus indexing.
- Build full `Ypf`, then copy non-swing rows and columns into a reduced matrix.

Recommended first implementation:

Build a reduced matrix in the fixed-point power-flow helper. It is explicit, easy to test, and avoids modifying the existing sparse matrix contract.

Fallback approach:

Use a large source admittance only if reduced-matrix assembly proves too invasive. This is easier but less clean numerically.

## Current Injection Rules

The fixed-point RHS should be assembled from power-flow current injections, not dynamic simulation injections.

Use:

```java
DStab3PBus.calc3PhEquivCurInj()
```

Avoid:

```java
DStab3PBus.injCurDynamic3Phase()
```

Power-flow injection contents:

- Static constant-P loads: negative current injection.
- Static constant-I loads: negative voltage-dependent current injection.
- Static constant-Z loads: preferably included in `Ypf`, not RHS.
- Three-phase distributed generators: positive current injection.
- Dynamic devices: excluded unless they have an explicit static power-flow representation.

## Branch Current Update

Forward/backward sweep updates branch current fields during the solve. Fixed-point Y-matrix solve will not do that automatically.

After convergence, add:

```java
updateBranchCurrentsFromSolvedVoltages()
```

For each active `DStab3PBranch`:

```text
I_from = Yff * V_from + Yft * V_to
I_to   = Ytf * V_from + Ytt * V_to
```

Then set:

```java
branch.setCurrentAbcAtFromSide(I_from);
branch.setCurrentAbcAtToSide(I_to);
```

This keeps existing output functions and `calcSwingBusGenPower()` usable.

## Testing Plan

### Unit Tests

Add tests to:

```text
src/test/java/org/interpss/threePhase/system/TestDistributionPowerflowAlgo.java
```

or create:

```text
src/test/java/org/interpss/threePhase/system/TestFixedPointDistributionPowerflow.java
```

Recommended tests:

1. Fixed-point method is default.
2. Simple radial feeder converges.
3. Swing bus voltage remains fixed.
4. Fixed-point voltages match forward/backward sweep within tolerance for a simple radial feeder.
5. Branch currents are populated after convergence.
6. Source generation is updated after convergence.
7. Power-flow Y-matrix excludes dynamic load and dynamic generator model admittances.
8. Constant-Z static load is included only once.

### Matrix Separation Test

Create a test feeder with a dynamic load or motor model attached.

Test expectations:

- Stability `formYMatrixABC()` includes dynamic model admittance.
- Power-flow `formYMatrixABCForPowerflow()` excludes dynamic model admittance.
- Fixed-point power flow can run without dynamic model initialization.

### Regression Tests

Run targeted tests:

```bash
mvn -Dtest=TestDistributionPowerflowAlgo test
```

Run full module tests when targeted tests pass:

```bash
mvn test
```

## Implementation Phases

### Phase 1: Method Selection

Files:

- `src/main/java/org/interpss/threePhase/powerflow/DistributionPFMethod.java`
- `src/main/java/org/interpss/threePhase/powerflow/impl/DistributionPowerFlowAlgorithmImpl.java`

Steps:

1. Add `Fixed_Point` enum value.
2. Set `pfMethod` default to `Fixed_Point`.
3. Add fixed-point dispatch in `powerflow_singleNet(...)`.
4. Keep forward/backward sweep available.

Verification:

- Project compiles.
- Existing FBS tests can still run when method is set to FBS.

### Phase 2: Power-Flow Y-Matrix Separation

Files:

- `src/main/java/org/interpss/threePhase/dynamic/DStabNetwork3Phase.java`
- `src/main/java/org/interpss/threePhase/dynamic/impl/DStabNetwork3phaseImpl.java`
- `src/main/java/org/interpss/threePhase/basic/dstab/DStab3PBus.java`
- `src/main/java/org/interpss/threePhase/basic/dstab/impl/DStab3PBusImpl.java`

Steps:

1. Add power-flow Y-matrix API.
2. Add `yMatrixAbcPowerflow` storage.
3. Extract bus self-admittance logic as needed.
4. Build `Ypf` without dynamic model admittance.
5. Add tests proving `Ypf` and stability `Yabc` differ when dynamic models exist.

Verification:

- Power-flow Y-matrix excludes dynamic model terms.
- Stability simulation matrix behavior is unchanged.

### Phase 3: Fixed-Point Solver

File:

- `src/main/java/org/interpss/threePhase/powerflow/impl/DistributionPowerFlowAlgorithmImpl.java`

Steps:

1. Add `fixedPointPowerflow()`.
2. Save previous bus voltages per iteration.
3. Assemble current injections from static power-flow models.
4. Solve the linear sparse system.
5. Update bus voltages.
6. Check convergence by max phase-voltage delta.
7. Return false after `maxIteration` if not converged.

Verification:

- Simple radial feeder converges.
- Voltage results are stable and finite.
- Tolerance and max-iteration settings are honored.

### Phase 4: Source Boundary Handling

File:

- `src/main/java/org/interpss/threePhase/powerflow/impl/DistributionPowerFlowAlgorithmImpl.java`

Steps:

1. Identify active swing buses.
2. Keep swing `Vabc` fixed.
3. Implement reduced matrix or equivalent RHS correction.
4. Ensure solved voltages are applied only to non-swing buses.

Verification:

- Swing voltage is unchanged before and after solve.
- Non-swing voltages converge.
- No artificial source admittance appears in power-flow results.

### Phase 5: Post-Solve Quantities

File:

- `src/main/java/org/interpss/threePhase/powerflow/impl/DistributionPowerFlowAlgorithmImpl.java`

Steps:

1. Add branch-current update from solved voltages.
2. Reuse `calcSwingBusGenPower()` after branch current update.
3. Confirm existing output functions work.

Verification:

- Branch current fields are non-null and finite.
- Swing bus generation is updated.
- Existing power-flow output functions do not regress.

### Phase 6: Validation Against Existing Solver

Files:

- `src/test/java/org/interpss/threePhase/system/TestDistributionPowerflowAlgo.java`
- Optional new `TestFixedPointDistributionPowerflow.java`

Steps:

1. Run a known feeder with FBS.
2. Run the same feeder with fixed-point.
3. Compare bus voltage magnitudes and angles.
4. Compare source power.
5. Add acceptable tolerances.

Verification:

- Results match FBS for radial test systems within agreed tolerance.
- Fixed-point works for non-radial systems where FBS is not appropriate, once test data exists.

## Open Questions

1. Should `DistributionPowerFlowAlgorithm` expose `setPFMethod(DistributionPFMethod method)`? The implementation currently has `getPFMethod()` only.
2. Should constant-Z static loads be included through `Ypf` by default, or should all loads remain in RHS for the first implementation?
3. Should the first source-boundary implementation use a reduced matrix, or is a large source admittance acceptable for an initial version?
4. Should fixed-point support PV bus voltage control immediately, or should PV generators initially behave as current injections based on specified power?
5. How should voltage-dependent ZIP load fractions be represented in the existing load model classes?

## Success Criteria

The implementation is complete when:

1. `Fixed_Point` is the default distribution power-flow method.
2. Fixed-point power flow converges on at least one simple three-phase distribution feeder.
3. The power-flow Y-matrix is separate from the stability Y-matrix.
4. Dynamic model admittances are excluded from the power-flow Y-matrix.
5. Swing/source bus voltage remains fixed.
6. Branch currents and swing generation are populated after convergence.
7. Tests cover convergence, matrix separation, source handling, and comparison with forward/backward sweep.
