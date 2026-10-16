# IEEE39 benchmarking

## Result

The benchmark reference and InterPSS plugin 1.4.4 / core 1.4.1 were run on
the unchanged IEEE39 RAW/DYR fixture. The benchmark covers all 39 bus voltage
magnitudes and frequencies, all ten machines, and all nine exciters and governors.
**Discrepancies remain; this is not full model verification.** No production
equations or case parameters were changed to improve agreement.

Five-second undisturbed and bus-fault cases were compared at 1 ms and 0.5 ms.
The largest fault-case absolute errors over the complete trajectory are:

| Quantity | Channels | 1 ms | 0.5 ms | Worst channel at 0.5 ms |
|---|---:|---:|---:|---|
| Bus voltage, pu | 39 | 0.00366940 | 0.00368794 | Bus 38 |
| Bus frequency, Hz | 39 | 0.256470 | 0.255643 | Bus 22 |
| Machine electromagnetic states, pu | 36 | 0.00602510 | 0.00605610 | Machine 38, PSIKD |
| Machine speed deviation, pu | 10 | 0.000193253 | 0.000193697 | Machine 34 |
| Machine absolute angle, rad | 10 | 0.0642394 | 0.0637547 | Machine 38 |
| Exciter states, pu | 36 | 0.512823 | 0.513658 | Exciter 38, VR |
| Governor states, model coordinates | 37 | 0.00440741 | 0.00440474 | Governor 36, TURBINE4 |
| Machine outputs, pu | 39 | 0.284543 | 0.284268 | Machine 39, PE on system base |

The bus-39-relative rotor-angle error reaches 0.0235612 rad at 1 ms and
0.0237359 rad at 0.5 ms (approximately 1.36 degrees). The larger absolute-angle
error therefore cannot be dismissed entirely as a common angle-reference shift.
The 0.284268 pu active-power difference is 28.43 MW on the 100 MVA system base.
Halving the integration step does not substantially reduce the discrepancies.

## Conditions and coverage

- Inputs: `ipss.test.plugin.core/testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw`
  and `IEEE39bus.dyr` in that directory; full original controller data.
- 60 Hz; 100 MVA system base; constant-impedance active and reactive loads
  explicitly selected in both simulators. Conclusions apply to this load profile.
- Flat case: no disturbance, 0 through 5 seconds.
- Fault case: three-phase bus 17 fault, applied at 1.00 s, cleared at 1.05 s;
  impedance j0.01 pu; no line trip. The benchmark reference uses j1 ohm at 100 kV and 100 MVA.
- InterPSS: modified Euler; power-flow tolerance 1e-10 pu, maximum 50 iterations.
  Benchmark reference: native FNSL, CONG, three CONL stages, ORDR, FACT, TYSL, DYRE and STRT.
- Machines 30–38: GENROU, six states each. Machine 39: GENCLS, two states.
- Exciters 30–38: IEEET1, four states each.
- Governors: GAST at 30; TGOV1 at 31/34/35; IEESGO at 32/38;
  IEEEG1 at 33/36/37. Every documented state slot is exported.
- 247 channels recorded; 246 scored. Machine 39 has no exciter/governor.
  Its EFD output is excluded: the benchmark reference reports internal voltage for GENCLS,
  whereas InterPSS returns zero. This is not a missing physical exciter state.
- The benchmark export's 150-channel limit requires separate bus (78), machine (96),
  and controller (73) runs. Input hashes, configuration and time grids match.

## State definitions and alignment

Benchmark state indices come from `MDLIND` and the archived benchmark model library.
Physical PDF pages consulted: GENCLS 45; GENROU 57; IEEET1 299–300;
GAST 379; IEEEG1 447–448; IEESGO 455; TGOV1 481.

| Channel family | Comparison coordinate |
|---|---|
| Bus frequency | Hz: InterPSS frequency pu multiplied by 60; benchmark reference frequency deviation converted as 60*(1+deviation) |
| GENROU | EQ1, ED1, PSIKD, PSIKQ, speed minus one, angle in radians |
| GENCLS | Speed minus one and angle in radians |
| IEEET1 | Measured voltage, regulator output VR, field state EFD, feedback storage KF*filtered EFD |
| GAST | Valve, fuel flow, exhaust temperature |
| TGOV1 | Valve and native reheater integrator; the latter is T3 times CML lead-lag storage |
| IEESGO | First filter, servo, three turbine states; bypassed first-filter output includes K1 |
| IEEEG1 | First filter, valve integrator, four turbine states; bypassed first-filter slot uses algebraic output |
| Machine output | PE/QE on system base; PMECH on machine base; field output in native model pu |

IEEET1 feedback storage is TF times the CML washout storage. These are
analytical observation-coordinate conversions, not fitted parameters. In this
fixture IEEEG1 and IEESGO first filters have zero denominator time; their unused
CML storage stays zero while the benchmark reference records algebraic output in the state slot.

All initial offsets are retained. The benchmark reference's floating-point clock is rebased at
each recorded event boundary. Interpolation is restricted to continuous
segments; it never blends values across a fault application or clearing.
The two instantaneous post-event benchmark reference rows are preserved but unscored because
the InterPSS exporter has no corresponding zero-duration algebraic sample.
Power-flow voltages are compared separately before dynamic initialization.

The report uses provisional engineering screens: 0.01 pu voltage and generic
states, 0.02 Hz frequency, 0.0005 pu machine speed, 0.01 rad angle, 0.02 pu
exciter states, and 0.05 pu PE/QE. They are diagnostic screens, not acceptance
tolerances establishing any model's verified status. Fault-case exceedances:
39 frequency, 10 angle, 8 exciter-state, and 7 machine-output channels.

## Diagnosis and remaining work

1. **Exciter saturation below the knee.** In core's
   `com/interpss/dstab/controller/cml/field/func/SeFunction.java`, the quadratic
   branch evaluates `B*(EFD-A)^2/EFD` for EFD > 0.01 without limiting it to
   EFD > A. This fixture's saturation points give A = 2.39841864 and
   B = 0.711860322. At machine 38, InterPSS EFD = 1.71765110 is below A,
   yet VR-EFD = 0.32990771, exactly the quadratic saturation contribution.
   The benchmark reference starts with VR = EFD = 1.71780598. The initial cross-simulator VR
   discrepancy is 0.32975282 pu; machine 37 has the same below-knee issue.
   This localizes a likely missing domain cutoff. A core fix needs an
   authoritative equation check, focused below/at/above-knee tests and a fresh
   full-model comparison; this benchmark does not alter core.
2. **Bus-frequency mismatch remains unresolved.** The worst discrepancy is
   near clearing; even after 1.2 s the 1 ms comparison reaches 0.0660911 Hz.
   It persists at the smaller step. InterPSS uses an angle-derived filtered
   bus-frequency measurement; exact estimator and event semantics need a
   separate audit against the benchmark reference. Small rotor-speed differences do not prove
   equivalent bus-frequency measurements.
3. **Static operating points differ slightly.** Before dynamics, the largest
   voltage difference is 0.000405754 pu. Machine 31 reactive power differs
   by 0.0286067 pu (2.86 Mvar). This must be isolated from dynamic model error;
   neither initial conditions nor trajectories were shifted to remove it.
4. **Closed-loop attribution is incomplete.** Exciter discrepancies feed into
   voltage, power and rotor trajectories. Those downstream differences cannot
   yet be attributed independently to machine or governor equations.

## Evidence and reproduction

Public exporter: `Ieee39PsseBenchmarkTest` in the core test module. Its four
parameterized cases assert initialization, integration and finite outputs;
passing them does **not** assert benchmark reference parity.

```sh
mvn -o -pl ipss.test.plugin.core -am test -Pextended-tests -Dtest=Ieee39PsseBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false
```

The repository excludes simulator-driving Python and native trajectory
references from Git. The local private archive is
`ipss.test.plugin.core/testData/reference/ieee39-psse-benchmark-20261015/`.
It contains raw benchmark reference `.out`, native and normalized CSVs, manifests, logs,
InterPSS exports, comparison scripts and numerical metrics.
The working Python programs are under `ipss.test.plugin.core/src/test/python/`.

Using the archived benchmarking Python environment, run
`psse_ieee39_benchmark.py --scenario SCENARIO --group GROUP --step STEP --output OUTPUT`
for flat/fault and bus/machine/controllers; repeat in fresh processes and a
separate output directory. Use STEP 0.001 and 0.0005. Run
`compare_ieee39_psse.py --reference OUTPUT --actual INTERPSS_OUTPUT --output REPORT`
with NumPy installed. `test_compare_ieee39_psse.py` exercises event segmentation
and preservation of initial offsets. The archived Python files must be restored
to their original repository location before running (their fixture paths are
relative to that location).

All six 1 ms benchmark CSVs reproduced byte-for-byte in the second cold run.
All declared artifact and fixture hashes were rechecked before comparison.
Manifests identify native model-library and documentation hashes and exact
channel/state indices. The archived inventory hashes the final evidence files.

Validation: `mvn -o test -Pextended-tests -B -ntp --fail-at-end` completed
successfully: 2,517 tests, zero failures/errors, 108 skipped (2,409 passed),
including the four benchmark exports. All three comparison-alignment unit
tests passed. These regression results do not override the numerical
discrepancies reported above.

| Input | SHA-256 |
|---|---|
| RAW | `337969751311a922a4d1546a3649ba63a922ceaf56c3d49a883d2dde3abed46a` |
| DYR | `c837ae01536bb3992319e75695e8214e41bcd88afab4e11e16881c251856bf60` |
| Benchmark model-library binary | `c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6` |
| Benchmark model documentation | `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d` |
