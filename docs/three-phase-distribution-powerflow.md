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

Ckt24 repeated-state QSTS performance was checked with OpenDSS static controls enabled
on both sides. The default InterPSS Ckt24 master has no time-varying
profile bindings. This historical workload must be distinguished from the
yearly-profile master `master_ckt24_yearly_interpss.dss`. QSTS caches the matrix,
while enabled controls still evaluate the solved operating point at each step.

Commands used on 2026-06-11:

Test run settings now live in the module JSON fixture. For the historical InterPSS
run below, set `qsts.perf.case` to `ckt24`, `qsts.perf.warmupSteps` to `24`,
`qsts.perf.steps` to `8760`, `qsts.perf.repeats` to `1`, and `qsts.perf.profile`
to `true` in `QstsLargeFeederPerformanceBenchmark`. See
[test configuration](test-configuration.md).

```bash
python3 ipss.plugin.3phase/src/test/python/qsts_large_feeder_perf.py \
  --case ckt24 --warmup-steps 24 --steps 8760 --repeats 1

mvn -pl ipss.plugin.3phase -am test \
  -Dtest=QstsLargeFeederPerformanceBenchmark \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Current controlled results:

| Engine | Controls | Steps | Result |
|--------|----------|-------|--------|
| DSS-Python | static, regulators and capacitors enabled | 8760 | `1.146780 ms/step`, converged, max iterations `9` |
| InterPSS | static, regulators and capacitors enabled | 8760 | `1.021454 ms/step`, converged, max PF iterations `10` |

The InterPSS profile reported `symbolicFactors=1`, `numericFactors=2`,
`fallbackCount=0`, and `pf_iterations_per_step=2.000913`. This result is
about `112.3%` of DSS-Python throughput for the same controlled 8760 run, so it
meets the 80% performance target.

The controlled two-step Ckt24 parity comparison passes for voltages, load
powers, and branch powers. Current common-key maxima are `0.0017546525 pu`,
`0.000756435 deg`, `0.06397092 kW`, `0.240896534 kvar`, `0.28418337 kW`, and
`0.8739569147 kvar`, with zero voltage, load-power, and branch-power tolerance
failures.

IEEE8500 controlled PV-duty one-step row-level parity is now green with static
controls enabled. The current comparison maxima are `0.00104704199 pu`,
`0.0267427413 deg`, `2.87351585 kW`, `2.728537974 kvar`, `0.014988296 kW`,
and `0.00406943705 kvar`, with zero voltage, load-power, branch-power,
generator-power, and capacitor-state tolerance failures.

## Paired 8,760-step benchmark, 2026-10-13

The optimized QSTS execution exceeds the 90% DSS-Python throughput target for
both large feeders. These are medians of three measured runs per engine and
workload, after a separate 24-step warmup. All 24 measured runs converged at
every step. Throughput is DSS-Python elapsed time divided by InterPSS elapsed
time; DSS-Python represents 100%.

| Workload | InterPSS solver | InterPSS total (s) | DSS-Python total (s) | InterPSS throughput |
|---|---|---:|---:|---:|
| IEEE8500 static, hourly | KLUSolveX | 3.439474 | 9.172926 | 266.7% |
| IEEE8500 PV duty, 1-second | KLUSolveX | 4.130892 | 5.207665 | 126.1% |
| Ckt24 static, hourly | KLUSolveX | 3.753073 | 15.590169 | 415.4% |
| Ckt24 yearly profile, hourly | Java KLU | 19.788752 | 24.758709 | 125.1% |

Each run has 8,760 steps. The PV-duty workload covers 2 hours 26 minutes,
not a year. The hourly workloads cover 365 days; the static masters repeat
their static operating point, whereas the Ckt24 yearly master varies its load
profiles. Circuit compilation and full row exports are excluded. Java timing
includes study construction; DSS-Python timing includes the solve loop and
convergence checks. Both engines use tolerance `1e-4`, maximum 1,000 power-flow
iterations, and static regulator/capacitor controls with up to 100 control
iterations. InterPSS uses minimum one iteration, primitive voltage state,
`NONE` sampling and `VOLTAGE_ONLY` output. See the explicit JSON settings in
`test-configuration.md`; the default test configuration remains unchanged.

Measured on Windows with an Intel Core i7-13700KF and Temurin Java 21.0.6+7.
The reference uses DSS-Python 0.15.7, OpenDSSDirect.py 0.9.4 and DSS C-API
0.14.5 (OpenDSS SVN 3723). Raw configurations, repeat timings, accuracy exports
and provenance are retained locally in
`target/qsts-performance-optimization/final-paired-8760/`.

The performance improvements cache prepared control lookups and profile
multipliers, defer unobserved voltage object updates until study exit, preserve
the fixed-point matrix structure across regulator taps, and update only changed
native matrix entries. Control observations still use current voltages at each
step. Final full-versus-deferred voltage tests match to `1e-10` with identical
tap and capacitor states for all four workloads.

Independent 24-step row comparisons use energized bus phases and total
terminal-1 branch real power on a 1 MVA three-phase base:

| Workload | Max Vmag difference (pu) | Avg Vmag difference (pu) | Max branch P difference (pu) | Avg branch P difference (pu) |
|---|---:|---:|---:|---:|
| IEEE8500 static | 8.3987e-4 | 4.1928e-4 | 1.5461e-3 | 8.5099e-6 |
| IEEE8500 PV duty | 1.0682e-3 | 4.2860e-4 | 4.6890e-3 | 1.7941e-4 |
| Ckt24 static | 1.7439e-3 | 9.6788e-4 | 1.6956e-4 | 4.7504e-7 |
| Ckt24 yearly profile | 1.7549e-3 | 9.7762e-4 | 3.1045e-3 | 1.2826e-5 |

All compared regulator taps and capacitor states agree with DSS-Python. There
are no missing reference energized voltage phases; unmatched reference branches
have zero real power. These accuracy checks cover 24 steps, rather than every
annual step. This total terminal-1 metric differs from historical per-phase or
both-terminal branch metrics. The full three-phase suite passed (384 tests,
28 skipped), as did ten core three-phase regressions and the actual native
matrix-update test with 100 updates and pattern changes.

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
