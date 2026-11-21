# Network backend and corrected-model tolerance controls

The corrected Case 3 runtime still exceeds the prefault rotor-angle limit when
the dynamic network tolerance is tightened from 1e-4 to 1e-6. A separate Case 1
control shows that removing repeated checks of unchanged LU factors preserves
every recorded value through fault clearing. Neither result changes the
acceptance contract or establishes six-case acceptance.

## Case 3: a paired control with the current corrected models

Both runs retain the same original inputs, I/Z loads, computed operating point,
0.5 ms step, single-precision network arithmetic, two history steps, converter
correction and model runtime. Cold snapshot values, the first network matrix,
first current vector and first solved voltage are exactly equal. The dynamic
stopping tolerance is the only numerical change; initialization retains 1e-8.

The completed comparison covers 203 rows through 0.1 seconds, 6,578 recorded
physical channels, and all 240 supplied physical channel occurrences across
the six original Case 3 events. Each normalized error uses that event's full
ten-second reference range. These are short-prefix metrics, not complete
prefault averages or postfault acceptance results.

| Quantity over 0 to 0.1 seconds | 1e-4 | 1e-6 |
| --- | ---: | ---: |
| Worst observed bus-voltage NRMAE, bus 3054 | 0.00954101 | 0.00967642 |
| Peak voltage error at bus 3054 (pu) | 0.00656739 | 0.00733706 |
| Worst terminal-voltage NRMAE, unit 1073/1 | 0.0128778 | 0.0131581 |
| Worst rotor-angle maximum error, unit 1073/1 (degrees) | 0.0436978 | 0.0370069 |
| Worst generator-speed proxy NRMAE, unit 6082/1 | 0.000673897 | 0.000538756 |
| Network iterations over the same 202 calls | 1,561 | 6,907 |

Tightening reduces the largest rotor error by 15.3% but requires 4.42 times as
many network iterations and slightly increases the voltage discrepancy. Both
rotor prefixes already exceed the 0.02-degree prefault maximum. This closes the
paired-control gap in the [network residual diagnostic](case3-network-residual-diagnostic-2026-10-19.md):
its older tighter run predated the converter correction. Tolerance alone does
not resolve the corrected model/network discrepancy. This evidence does not
select either setting as a universally acceptable tolerance.

## Case 1: unchanged LU arithmetic with less repeated checking

The installed high-level LAPACKE interface has its matrix NaN check enabled.
The `_work` interface omits that repeated check, as documented by
[LAPACKE](https://www.netlib.org/lapack/lapacke.html). The diagnostic wrapper
instead verifies the matrix and LU factors once and verifies each new current
vector and solved voltage on every call. Factorization reuse, LU storage,
pivots and solve arithmetic are retained; no global library option changes.

Three actual 2,000-bus network matrices, both complex precisions and 40 seeded
current vectors per combination produce 240 byte-identical solutions. LU
factors and pivots remain unchanged. The retained first Case 1 current vector
also reproduces its original solution bytes. For the Case 1 single-precision
matrix, the median time for 40 solves changes from 0.1905 to 0.07068 seconds.
This is an interface microbenchmark under concurrent execution, not a full
simulation or production-runtime claim.

The paired autonomous Case 1 trajectories are byte-identical across all 50
recorded channels and 2,805 rows through 1.4 seconds, including both sides of
fault application and clearing. Forty supplied physical channels are matched.
A separate standard sparse network run retains the same models, operating
point and 1e-6 stopping tolerance, but uses its own arithmetic.

| Largest observed absolute error | Dense single | Standard sparse |
| --- | ---: | ---: |
| Prefault bus voltage (pu) | 0.0000159618 | 0.0000145898 |
| Prefault rotor angle (degrees) | 0.00584708 | 0.00575997 |
| Fault/postfault bus voltage through 1.4 s (pu) | 0.0000133489 | 0.0000141158 |
| Fault/postfault rotor angle through 1.4 s (degrees) | 0.0147044 | 0.0147664 |

The three ten-second controls were still running when this prefix evidence was
sealed. Their final acceptance and full-trajectory equivalence remain separate
checks. Other Case 1 event channels are outside this prefix comparison.

## Reproducible evidence and scope

Private evidence set `2026-10-19-network-backend-and-tolerance-controls` retains
the completed Case 3 run, immutable Case 1 prefixes, exact producers, frozen
runtime and input hashes, all comparison metrics, and the LU controls. It also
retains a failed pre-initialization attempt caused by disk exhaustion and the
verified recovery report; that attempt produced no trajectory. Duplicate
matrix files were hard-linked only after byte-hash verification, preserving
their paths and contents. New matrix evidence uses lossless compression.

No model equations, source parameters or acceptance limits are changed by
these controls. The six-case, 36-event goal, direct bus-frequency comparison
and production runtime target remain open. Generator speed is only a proxy.
