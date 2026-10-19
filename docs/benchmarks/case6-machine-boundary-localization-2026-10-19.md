# Case 6 machine-boundary localization

The 23 observed synchronous machines and their complete controllers closely
reproduce the supplied prefault trajectories when driven by the reconstructed
reference terminal voltages. The worst directly reported rotor-angle error is
**0.00376159 degrees** through one second. The coupled network trajectory is
still incorrect; this conditional result does not establish system acceptance.

This continues the [prefault localization](case6-prefault-localization-2026-10-19.md)
using the original Case 6 RAW/DYR, locally computed converted operating point,
and existing supplied measurements. The production plugin snapshot is
`dd18cb30976b6c4003e3eafdc36cad83bf36230d`; the frozen core runtime records
`08fb541731501bcf92c11cbc292310e10a4ecfcb`. No model parameters, initial states,
acceptance limits, or production equations are changed in this slice.

## Conditional machine and controller results

The first replay prescribes voltage, rotor angle, speed and field voltage while
integrating four electrical states. A second replay releases rotor angle and
speed, retaining only voltage, field voltage and mechanical power as inputs.
The complete-stack replay then releases the exciter, stabilizer and governor:
**only terminal voltage magnitude and phase remain prescribed**.

All three use normal machine initialization from the locally computed operating
point and original model parameters. No reference internal state is injected.
Cold derivative history is zero, with two 0.5 ms startup steps; all 2,003
reference rows from -0.001 s through the left side of 1 s are retained.
The complete stack uses the production staggered integration, controller state
bindings, custom governor participants and ten local ST1 steps.

The supplied internal states and terminal P/Q allow reconstruction of terminal
voltage phase through the stator relation. Its magnitude closure is bounded by
2.46822e-7 pu. This phase is inferred, not an independently supplied bus-angle
channel. Prescribing it isolates a physical boundary; it does not validate the
network that must produce that boundary in the coupled simulation.

Complete-stack errors against the **direct physical reference channels**, over
0–1 s:

| Quantity | Largest absolute error | Machine | Peak time (s) |
|---|---:|---|---:|
| Rotor angle | 0.00376159 degrees | 5301/1 | 0.9995 |
| Speed deviation | 7.41100e-7 pu | 6147/1 | 0.8640 |
| Terminal active power | 0.0521097 MW | 6147/1 | 0.7400 |
| Terminal reactive power | 0.0301895 Mvar | 5301/1 | 0.7385 |
| Field voltage | 0.00169715 pu | 5301/1 | 0.7615 |
| Mechanical power | 0.00334239 MW | 6147/1 | 0.4690 |

All 23 conditional rotor channels satisfy the unchanged 0.02-degree prefault
bound. Comparison with the separately stored native angle state gives
0.00379451 degrees; the state and physical angle exports have different rounding.
The physical-channel number above is used for the reported comparison.

A local-boundary control through 0.3 s reproduces the coupled local machine
outputs within 0.000439240 degrees, 0.0173223 MW and 0.00963347 Mvar. It is
close but not bit-identical to the coupled computation. The six-state replay
with supplied field and mechanical power has a maximum angle-state error of
0.00432297 degrees; the complete controller stack therefore does not expose
a large missing controller response in these 23 machines.

The complete-stack input files retain unused mechanical-power and field-voltage
columns from the preceding replay. A fresh repeat replaces both columns by
zero at every machine and sample. All 46,069 output rows, including every
state and physical output, remain byte-identical after decompression. The
uncompressed output SHA-256 is
`7b3b12b9b9dc2b5f05149d5bc0c267fcaa889519eb0236386c4b9fc5aef8a3d6`.
This explicitly checks that those columns do not drive the complete-stack run.

The first complete-stack harness stopped because its bus-frequency observers
had not been initialized. The corrected harness initializes those observers
before stepping; both versions and the failed log are retained. This was a
diagnostic setup failure, not a production model correction.

## Independent physical network balance

The saved coupled prefix contains 603 rows, all 2,000 complex bus voltages and
408 physical generator P/Q pairs. An independent calculation stamps the RAW
branches, transformers and fixed shunts, adds solved switched shunts once, and
uses the confirmed constant-current P / constant-impedance Q load profile.
It retains the existing negative-load conversion of unmodeled units 5045/1
and 7099/2; their reference preparation remains unresolved.

The largest bus power-balance residual is **0.270299 MVA** at bus 2029,
0.2765 s. At the cold initial point the maximum is 0.112883 MVA at bus 5317.
The independently stamped passive off-diagonals agree with the recorded
dynamic matrix within **4.55e-13 pu**, confirming the shared bus order and
passive connections for this check.

One fixed-source correction using that recorded Norton/load matrix gives a
maximum voltage-magnitude change of **8.98640e-5 pu** and a maximum complex
voltage change of 9.59257e-5 pu. Its linear equation residual is below 6.42e-17.
This is much smaller than the coupled reference mismatch of 0.0669426 pu.
It rules out a large unbalanced algebraic endpoint as the immediate explanation;
it does not bound accumulated effects of nonlinear iteration or prove exact
reference network equivalence.

## Evidence and remaining work

The private evidence set
`2026-10-19-case6-machine-and-network-boundaries` retains the frozen runtime,
inputs, replay producers, raw outputs, all per-channel metrics, failed harness,
zero-column repeat, independent network calculation and content hashes. It
depends on the preceding sealed prefault evidence for the original references
and coupled traces. No new reference-simulator run was used.

These results narrow the remaining investigation toward the sources that
produce network voltages: the converter fleet, unobserved synchronous units,
load/conversion behavior and their network coupling. They do not establish
which of those is incorrect. The measured machine/controller subset does not
explain the large coupled discrepancy when its voltage boundary is correct.

All six cases and all 36 disturbances remain required. Voltage/frequency NRMAE
limits remain 0.001 prefault and 0.005 from fault application, normalized by the
full ten-second reference range. Rotor limits remain 0.02 degrees maximum
prefault and 0.2 degrees ten-second RMSE. Direct bus-frequency agreement,
complete coupled accuracy and the 20-second runtime target remain unproved.
