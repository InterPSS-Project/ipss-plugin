# Case 3 startup integration refinement

Halving the integration step does not remove Case 3's early voltage mismatch.
Two completed autonomous runs compare the first 0.1 seconds with all 240
physical channels observed across the six supplied Case 3 events. The initial
states and physical startup-history duration are identical. The smaller step
reduces the worst observed bus-voltage error by only 1.57 percent and increases
the worst rotor-angle error by 8.54 percent.

| Quantity, maximum absolute error through 0.1 s | 0.5 ms step | 0.25 ms step |
|---|---:|---:|
| Observed bus voltage, pu | 0.00656739 | 0.00646423 |
| Observed rotor angle, degrees | 0.0436978 | 0.0474292 |
| Observed speed deviation, pu | 0.0000777681 | 0.0000791723 |

Bus 3054 has the largest observed voltage error in both runs. Machine 1073/1
has the largest angle and speed errors. These are short-prefix absolute errors,
not substitutes for the full prefault and ten-second acceptance metrics. The
angle error already exceeds the unchanged 0.02-degree prefault limit.

## Paired controls

Both runs use the original Case 3 inputs, ordinary local power flow and dynamic
initialization, I-P/Z-Q load conversion, netting before conversion, the same
single-precision network backend, and the existing complex-voltage stopping
tolerance of 1e-4. The frozen runtime includes the corrected salient-machine
state refresh, converter voltage memory and event-frequency solver stage.
No event occurs in this prefix.

The cold start remains at -1 ms. The original grid takes two history steps;
the refined grid takes four. Both retain observations at -1 ms and -0.5 ms,
then every 0.5 ms from zero to 0.1 seconds: 203 rows per run. All intermediate
refined integration steps execute normally. No recorded reference state is
loaded into the simulation, and no time shift or parameter fitting is used.

The new 0.5 ms control reproduces the preceding converter-corrected Case 3
trajectory exactly across all 203 rows and 6,578 physical channels. Its cold
snapshot is identical. The refined run also has an identical cold snapshot and
network matrix. Every source and frozen runtime dependency is checked against
its retained hash before and after execution. Diagnostic elapsed times of
57.3 and 69.3 seconds include initialization, dense diagnostic solves and
recording; they do not measure the production runtime target.

The small change under refinement points away from integration step size as
the principal cause of this prefix error. It does not identify a faulty
controller or establish a converged continuous-time solution.

## Voltage thresholds and initial-rate inventory

The retained one-second converter-corrected trajectory was checked against the
entered voltage thresholds for all 290 active converter bundles. The earliest
sampled threshold crossing is the low-voltage dip threshold of 0.85 pu at
bus 3051/1 at 0.130 seconds. Thus the measured voltage divergence develops
before these sampled voltage-limit activations. This check uses recorded
terminal endpoints; it does not establish that every inner network iterate
stays inside every threshold, and it does not cover current or power limiters.

The first stored history derivatives are now inventoried for all 623 dynamic
generators: 313 round-rotor machines, 20 salient machines and 290 converters.
The inventory retains 14,650 rate entries, including all 333 exciters,
governors and stabilizers; all 290 electrical and plant controllers; and all
93 torque and pitch controllers. The reported audit maps have no coverage-gap
entries. Counts include observations and aliases and do not imply 14,650
independent differential states.

The largest synchronous machine electrical-state rate is 1.11228e-4 at
7113/1; the largest mechanical speed rate is 5.68569e-7 at 1050/1. The largest
synchronous controller rate is 0.00302396 in the exciter regulator state at
4094/1. These state coordinates have different units and are not a common
physical error measure. Apart from the already investigated active-power-order
rates, the largest stored renewable rate is 3.11e-13. The power-order rates
remain consistent with the entered selector equation and the separately
retained independent wind-startup evidence; they are not forced to zero.
Small initial rates do not prove subsequent trajectory correctness.

## Evidence and remaining work

Private evidence set `2026-10-19-case3-startup-step-refinement` retains both
complete runs, exact inputs and runtime dependencies, reproducible producers,
all-channel prefix comparisons, the first-rate inventory, and comparison plots.
The preceding Case 2 postfault error review and its unchanged-limit sensitivity
table are retained in the same set.

The next investigation remains the coupled network/controller response before
0.1 seconds, rather than a step-size reduction or forced equilibrium. Case 3's
full prefault failure is unresolved. All six cases, 36 disturbances, direct
bus-frequency validation and the runtime target remain open. No acceptance
limit or production model equation changes in this diagnostic slice.
