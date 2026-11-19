# Case 1 full-event angle localization

The standard sparse network solver completes the original bus-7249 fault and
passes all supplied voltage-channel limits, but fails every full-window rotor
angle limit. The dominant angle discrepancy is shared motion caused by the
accumulated speed difference. It is not explained by an angle-reference offset.

## Complete original event

The run retains the original inputs, computed cold point, I-P/Z-Q loads, two
history samples, corrected model runtime, 0.5 ms integration step and 1e-6
dynamic network tolerance. It uses the ordinary sparse network solver. All
20,005 rows, including both sides of application and clearing, are retained.
Forty supplied physical channels are matched; other reference auxiliary
channels are explicitly listed as unrecorded in the comparison manifest.

| Required quantity | Passing channels | Worst error | Limit |
| --- | ---: | ---: | ---: |
| Prefault bus-voltage NRMAE | 5 / 5 | 0.0000154562 | 0.001 |
| Fault/postfault bus-voltage NRMAE | 5 / 5 | 0.000400016 | 0.015 |
| Prefault terminal-voltage NRMAE | 5 / 5 | 0.0000248978 | 0.001 |
| Fault/postfault terminal-voltage NRMAE | 5 / 5 | 0.000878609 | 0.015 |
| Prefault rotor maximum error (degrees) | 5 / 5 | 0.00575997 | 0.02 |
| Ten-second rotor RMSE (degrees) | 0 / 5 | 2.187948 | 0.2 |

Voltage normalization uses each channel's complete ten-second reference range.
The fault/postfault interval starts at fault application. The worst full-run
rotor channel is unit 7189/1, reaching -7.002386 degrees of signed error at ten
seconds. All five machines finish between -6.6211 and -7.0024 degrees.

## Distinguishing shared motion from an observation offset

The supplied angle channels agree with the independently exported internal
rotor-angle states within 0.000424 degrees. Their speed channels equal the
exported internal speed-deviation states exactly. Thus the approximately
seven-degree mismatch is not a hidden subtraction of a reference-machine
angle in these supplied outputs.

Integrating the observed speed discrepancy using the 60 Hz rotor-angle
equation accounts for the original angle-error trajectory to within 0.001362
degrees across all five machines. This check uses trapezoidal integration,
the measured initial error, and zero duration for duplicate event rows; it
does not adjust any simulated or reference state. The largest speed difference
is 0.000143229 pu, equivalent to 0.008594 Hz for this diagnostic. Generator
speed still does not establish direct bus-frequency acceptance.

Pairwise angle differences relative to the predetermined first observed unit,
7146/1, have RMSEs from 0.01657 to 0.09877 degrees for the other four machines.
Their smaller errors support a dominant shared-motion discrepancy. Original
absolute-angle errors remain the acceptance quantities; no offset is fitted
or removed from scoring.

The two dense solver controls also accumulate about 0.74 degrees of rotor
error by 4.8 seconds. Their difference from the sparse trajectory is at most
0.01783 degrees over that prefix. All 50 recorded channels and all 9,605 rows
of the two dense prefixes are byte-identical. Their ten-second runs remain
separate ongoing checks; the discrepancy cannot be attributed solely to the
standard sparse backend.

## Evidence and next boundary

Private evidence set `2026-10-19-full-event-and-controller-observations`
retains the complete sparse run, source/runtime hashes, all channel metrics,
immutable dense prefixes, supplied internal-state checks, speed-integration
data and PNG/PDF figures. The concurrent diagnostic runtime of 1,344.7 seconds
uses a different timestep and recording workload from the production target;
it does not establish that target.

The next boundary is the shared speed/power imbalance. The five observed
machines alone do not identify the responsible network or generator input.
Other Case 1 events, all six original cases, direct bus frequency and the
production runtime target remain open. No acceptance threshold is changed.
