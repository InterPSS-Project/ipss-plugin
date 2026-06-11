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

## Anti-Float Necessity Check

Diagnostic switches were added temporarily around the matrix stabilization paths
to test which mechanisms are necessary for the checked-in OpenDSS feeders. The
tests used the CSparseJ solver and the QSTS smoke cases for IEEE123, Ckt7,
Ckt24, and IEEE8500:

```bash
mvn -pl ipss.plugin.3phase \
  -Dtest=OpenDSSQstsFeederSmokeTest#ieee123ControlsOffRepeatedStateDailyWindowConverges+ckt7ControlsOffYearlyWindowConverges+ckt24ControlsOffRepeatedStateDailyWindowConverges+ieee8500ControlsOffShortRepeatedStateDailyWindowConverges \
  -Dipss.sparse.solver=csj \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Results on 2026-06-07:

| Disabled mechanism | Result | Interpretation |
|--------------------|--------|----------------|
| None | All four feeders converged | Baseline is healthy. |
| Zero diagonal fill only | All four feeders converged | The broad non-swing shunt can still regularize missing-phase rows. |
| Transformer anti-float only | All four feeders converged | Not proven necessary by these feeders; keep transformer-specific edge-case coverage before removing. |
| Floating-component anti-float only | All four feeders converged | Redundant for these feeders when pre-PF floating-component deactivation remains active. |
| Broad non-swing bus anti-float only | All four feeders converged | Zero diagonal fill can still regularize missing-phase rows. |
| Zero diagonal fill and broad non-swing bus anti-float | IEEE123, Ckt7, and IEEE8500 failed numeric LU factorization at step 1 | At least one general missing-phase diagonal stabilizer is required while the solver uses fixed 3-phase bus expansion. |
| All matrix-time anti-float mechanisms | IEEE123, Ckt7, and IEEE8500 failed numeric LU factorization at step 1 | Transformer and floating-component anti-float do not solve the fixed-expansion missing-phase singularity. |
| Floating-component deactivation only | All four feeders converged; Ckt24 used matrix-time anti-floating admittance on 7,163 phase nodes and max iterations increased from 2 to 5 | Deactivation is not strictly required when matrix-time floating anti-float remains, but it materially reduces Ckt24 solve work. |
| Floating-component deactivation and matrix-time floating-component anti-float | All four feeders converged | The general diagonal stabilizers are enough for these smoke cases, but this leaves disconnected/floating topology in the solved matrix. |

Current conclusion:

- Keep one general missing-phase diagonal stabilizer until the solver moves to an
  OpenDSS-style active conductor/node matrix.
- Prefer the explicit zero diagonal fill over the broad non-swing shunt because
  it targets only missing/near-zero phase diagonals.
- Keep pre-PF floating-component deactivation enabled by default.
- Treat matrix-time floating-component anti-float as redundant for the tested
  feeders when pre-PF deactivation is enabled. It is now opt-in by setting
  `ipss.distpf.enableFloatingComponentAntiFloat=true`.
- Treat the broad non-swing shunt as a compatibility fallback rather than a
  default stabilizer. It is now opt-in by setting
  `ipss.distpf.enableNonSwingBusAntiFloat=true`.
- Keep transformer anti-float until a focused delta/ungrounded transformer
  regression suite proves it can be removed or moved into a solve-only
  transformer YPrim contribution.

The default fixed-point path is now:

- floating-component deactivation: enabled;
- zero diagonal fill: enabled;
- transformer anti-float: enabled;
- matrix-time floating-component anti-float: disabled unless explicitly enabled;
- broad non-swing bus anti-float: disabled unless explicitly enabled.

## Controlled QSTS Performance Evidence

Ckt24 annual QSTS performance was checked with OpenDSS static controls enabled
on both sides. The checked-in InterPSS Ckt24 QSTS case has no time-varying
profile bindings, so QSTS solves the first controlled static state and reuses
the settled network state for the remaining samples. This is disabled for
delayed/event controls, inverter controls, and any QSTS profile-bound case.

Commands used on 2026-06-11:

```bash
python3 ipss.plugin.3phase/src/test/python/qsts_large_feeder_perf.py \
  --case ckt24 --warmup-steps 24 --steps 8760 --repeats 1

mvn -pl ipss.plugin.3phase -am test \
  -Dtest=QstsLargeFeederPerformanceBenchmark \
  -Dqsts.perf.case=ckt24 \
  -Dqsts.perf.warmupSteps=24 \
  -Dqsts.perf.steps=8760 \
  -Dqsts.perf.repeats=1 \
  -Dipss.qsts.profile=true \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Results:

| Engine | Controls | Steps | Result |
|--------|----------|-------|--------|
| DSS-Python | static, regulators and capacitors enabled | 8760 | `0.820762 ms/step`, converged, max control iterations `9` |
| InterPSS | static, regulators and capacitors enabled | 8760 | `0.142280 ms/step`, converged, max PF iterations `5` |

The InterPSS profile reported `reused_powerflow_steps=8759`,
`symbolicFactors=1`, `numericFactors=2`, `fallbackCount=0`, and
`pf_iterations_per_step=0.000571`.

The Ckt24 one-step controlled voltage comparison also passes at the current
large-feeder tolerance after removing the parser-time fixed regulator tap shim:
`maxMagDelta=0.00299752703`, `maxAngleDelta=0.330423172`,
`magFailures=0`, and `angleFailures=0` at `0.003 pu` and `1.0 deg`.
Both DSS-Python and InterPSS settle `SubXFMR_Regulator` at tap position `2`.
The same controlled export now includes static branch terminal powers. The
branch-power comparator currently runs as a diagnostic and fails at `5 kW` /
`5 kvar` tolerance: `commonKeys=14102`, `dssOnly=124`, `interpssOnly=23`,
`maxPDelta=877.25032075` at `line.other_feeders` terminal 1 phase B, and
`maxQDelta=187.98447851` at `transformer.subxfmr` terminal 1 phase B.

IEEE8500 was checked with static capacitor controls enabled and regulator
controls disabled on both sides because the checked-in IEEE8500 regulator
controls do not settle in DSS-Python/OpenDSS under the current master-file
defaults. DSS-Python cap-control-only converged for 8760 steps at
`0.504579 ms/step`, with max OpenDSS iterations `8`. InterPSS converged the
same controlled 8760-step case at `0.164846 ms/step`, with max PF iterations
`9`, `reused_powerflow_steps=8759`, `symbolicFactors=1`, `numericFactors=1`,
and `fallbackCount=0`.

The IEEE8500 cap-control-only voltage comparison passes at `0.005 pu` and
`1.0 deg`: `commonKeys=8531`, `dssOnly=0`, `interpssOnly=6100`,
`maxMagDelta=0.004350571409` at `0:l3312692:A`,
`maxAngleDelta=0.216126424` at `0:x2841634b:A`, `magFailures=0`, and
`angleFailures=0`. At the stricter Ckt24 tolerance of `0.003 pu`, IEEE8500 has
`1858` magnitude failures, so the remaining IEEE8500 voltage parity gap is
small but still larger than the Ckt24 threshold.

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
