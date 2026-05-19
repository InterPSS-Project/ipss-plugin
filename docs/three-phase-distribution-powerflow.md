# Three-Phase Distribution Power Flow

## Architecture

The three-phase distribution power flow module lives in `ipss.plugin.3phase` and solves unbalanced power flow for radial distribution feeders with mixed phase configurations (1-phase, 2-phase, and 3-phase lines and loads).

### Key Classes

| Class | Location | Role |
|-------|----------|------|
| `DistributionPowerFlowAlgorithmImpl` | `powerflow/impl/` | Solver: fixed-point and forward/backward sweep |
| `DStab3PBusImpl` | `basic/dstab/impl/` | Three-phase bus with ABC voltages, loads, shunts |
| `DStab3PBranchImpl` | `basic/dstab/impl/` | Three-phase branch with Zabc/Yabc, handles partial phases |
| `Static3PXformerImpl` | core: `abc/impl/` | 3-phase transformer Y-bus model (Delta/Yg/Y/Delta11) |
| `IEEEFeederLineCode` | `basic/` | Standard IEEE test feeder line impedance matrices |
| `OpenDSSDataParser` | `dataParser/opendss/` | Parses OpenDSS .dss files into network model |
| `CSJSparseEqnComplexMatrix3x3Impl` | core: `sparse/impl/` | Sparse Y-matrix with 3x3 complex blocks per bus pair |

### Network Model

- **Bus**: stores 3-phase ABC voltages as `Complex3x1`, loads as `DStab3PLoad` (3-phase wye-connected constant PQ/I/Z)
- **Branch**: stores `Complex3x3` impedance (Zabc) and derived admittance (Yabc). For partial-phase lines (1-ph or 2-ph), zero rows in Zabc are detected and only the non-zero submatrix is inverted.
- **Transformer**: modeled via `Static3PXformer` using Chen/Dillon and Moorthy references. Supports Delta, Delta11, Wye (grounded and ungrounded) connection types, each producing different Y1/Y2/Y3 admittance matrices from positive- and zero-sequence impedances.

### Y-Matrix Assembly (Fixed-Point Solver)

The fixed-point solver in `DistributionPowerFlowAlgorithmImpl.formYMatrixABCForPowerflow()` assembles a sparse Y-bus with 3x3 Complex blocks:

1. **Diagonal (Yii)**: Sum of `getYffabc()` or `getYttabc()` from all connected branches, plus bus shunt admittance. For non-swing buses, zero diagonal entries are replaced with `1.0` to prevent singularity at partial-phase buses.
2. **Off-diagonal (Yij)**: `getYftabc()` and `getYtfabc()` from branches. For lines, this is `-Ybranch`. For transformers, this delegates to `Static3PXformer` which handles connection-specific phase shifts.
3. **Transformer anti-float**: Small admittance (1e-6) added to diagonal of buses connected through floating transformer windings (Delta, ungrounded Wye).

### Solution Method

1. Form Y-matrix, apply swing bus voltage boundary (zero out swing row/column, move coupling to RHS compensation)
2. LU factorization
3. Iterate: set current injections (I = conj(S)/conj(V)), solve Y*V = I, update voltages
4. Check convergence by max voltage mismatch across all buses and phases

### Forward/Backward Sweep (Fallback)

BFS is an alternative solver that traverses the radial network from leaf buses to root (backward sweep for currents) and root to leaves (forward sweep for voltages). It does not use the Y-matrix directly.

## Partial-Phase Handling

### Problem

IEEE test feeders (13-bus, 34-bus, 37-bus, 123-bus) contain lines with 1 or 2 active phases. For example:

- `zMtx605` (IEEE 13-bus): single-phase C, only CC element non-zero in 3x3 Zabc
- `zMtx603`: two-phase B,C, only BB/BC/CB/CC non-zero
- `zMtx604`: two-phase A,C, only AA/AC/CA/CC non-zero

When these are assembled into a 3x3 ABC Y-matrix, the unused phases produce near-zero diagonal entries, making the matrix ill-conditioned.

### Current Fix (Two Parts)

**Part 1 - Y-Matrix Diagonal** (`DistributionPowerFlowAlgorithmImpl.java:412-418`):

Zero diagonal entries in the 3x3 Yii block are replaced with `1.0` for non-swing buses. This prevents the LU factorization from encountering near-zero pivots:

```java
if(!bus.isSwing()) {
    double yiiMinTolerance = 1.0E-8;
    if(yii.aa.abs() < yiiMinTolerance) yii.aa = new Complex(1.0, 0);
    if(yii.bb.abs() < yiiMinTolerance) yii.bb = new Complex(1.0, 0);
    if(yii.cc.abs() < yiiMinTolerance) yii.cc = new Complex(1.0, 0);
}
```

**Part 2 - Current Injection NaN Guard** (`DStab3PLoadImpl.java:203-213`):

With the diagonal replacement, unused phases solve to V~0. The load current injection `I = conj(S)/conj(V)` would produce 0/0 = NaN for unused phases with zero load. The fix adds per-phase voltage guards:

```java
equivCurInj = new Complex3x1();
if(vabc.a_0.abs() > this.Vminpu) {
    equivCurInj.a_0 = loadPQ.a_0.divide(vabc.a_0).conjugate().multiply(-1.0);
}
// same for phases B and C
```

### Known Limitation

The fixed-point solver produces V=0 for unused phases (since the artificial diagonal has no physical coupling). BFS propagates upstream voltage for unused phases, which is a better approximation. Both methods agree exactly on active phases.

## Long-Term Direction: OpenDSS-Style Primitive Y Matrix

The current 3x3 ABC frame forces all components into a fixed 3-phase structure, causing the zero-padding issues above. OpenDSS uses a different approach:

1. Each component builds a **primitive Y matrix** in its actual phase dimension (e.g., 2x2 for a single-phase line, 6x6 for a 3-phase transformer)
2. Each component declares a **conductor-to-(bus, phase) mapping**
3. The system Y-bus is assembled by **stamping** primitive matrices into a conductor-indexed sparse matrix

Benefits:
- No zero-padded rows; every diagonal has physical admittance
- Exact representation of 1-phase and 2-phase devices
- Smaller system dimension (only active conductors)
- Direct compatibility with OpenDSS test cases for validation

## Test Cases

| Test | Feeder | Source |
|------|--------|--------|
| `IEEE_13BusFeeder_Test` | IEEE 13-bus | Hand-coded in Java |
| `TestIEEETestFeederPowerFlow` | IEEE 123-bus | OpenDSS .dss file parser |

The 123-bus tests validate against IEEE published reference voltages at buses 150r, 21, and 30, with tolerances of 5-6 mS.
