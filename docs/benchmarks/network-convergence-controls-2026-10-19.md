# Network convergence controls after the salient-state correction

Further tightening the Case 1 network solution does not resolve its remaining
prefault reference errors. The corrected salient-machine runtime is held fixed
while the dynamic stopping tolerance and, in one control, linear-solve precision
are changed. No controller parameter, initial state, supplied measurement,
integration step or acceptance limit is fitted or replaced.

## Case 1: complete prefault controls

Each completed run covers the original 0.5 ms step, two startup-history rows and
the full prefault second: 2,003 rows and 6,190 physical channels. All 240 physical
channels observed across the six original Case 1 events are compared. Voltage
and speed use mean absolute error divided by each originating event's full
ten-second reference range. The limits remain 0.001 for these prefault metrics
and 0.02 degrees for maximum rotor-angle error.

| Network arithmetic / tolerance | Worst voltage NRMAE | Worst rotor maximum error, degrees | Worst speed NRMAE | Network iterations |
| --- | ---: | ---: | ---: | ---: |
| Single / 1e-6 | 0.00182700 | 0.0175464 | 0.00323529 | 18,920 |
| Single / 5e-7 | 0.00183559 | 0.0175877 | 0.00324971 | 23,386 |
| Double / 1e-8 | 0.00182214 | 0.0173917 | 0.00322353 | 41,697 |

Every row has the same passing counts: 24 of 30 voltage channels, all 30 rotor
channels and 28 of 30 generator-speed channels. The worst voltage channel is
bus 1037; the worst rotor and speed channels are bus 1078, unit 1. Generator
speed remains a frequency proxy, not direct bus-frequency validation.

The single-precision 5e-7 setting requires 23.6% more iterations without improving
the worst required errors. Double precision at 1e-8 requires 120.4% more iterations
and changes the worst voltage NRMAE by less than 0.3%. Neither change is adopted
as a remedy. These results support retaining the 1e-6 Case 1 diagnostic control;
they do not establish a common stopping rule for every case.

The attempted single-precision 1e-8 control fails during startup after 500
iterations. Its last complex-voltage change is 1.88486e-7 pu, above the requested
threshold. The rejected run is retained and is not scored as a completed
trajectory. Both completed alternatives have exactly equal cold snapshot values,
original inputs, converted operating points, assembled first matrices and first
current-injection vectors. For that identical first equation, the maximum
current residual is 7.86643e-4 pu in single precision and 1.24571e-12 pu in double
precision; their voltage solutions differ by at most 3.88791e-6 pu. This proves
the arithmetic distinction without attributing the later reference error to it.

## Case 2: complete non-regression control

The ten-second, 1 ms Case 2 bus-6307 fault rerun changes only the corrected core
salient-machine class. Case 2 contains 103 round-rotor machines and 96 converters,
with no salient machine. All 10,005 rows and 4,810 physical channels are
byte-for-byte equal to the preceding control, including startup and duplicate
event sides. Parsed cold snapshot values are also exactly equal.

Fresh supplied-reference scoring therefore retains the previous results:
2,000 of 2,000 prefault voltage channels and 1,995 of 2,000 fault/postfault voltage
channels pass. Worst postfault voltage NRMAE is 0.0137413 at bus 1011. All 103
speed proxies pass both periods. Rotor channels pass 102 of 103 in each angle
gate: worst prefault maximum error is 0.0285314 degrees at bus 1051/1, and worst
full-window RMSE is 0.203219 degrees at bus 1050/1. The limits remain 0.02 and
0.2 degrees respectively. This is non-regression evidence, not error resolution.

## Evidence and remaining work

Private evidence set `2026-10-19-network-convergence-controls` retains the exact
inputs, frozen sources and runtime hashes, physical outputs, failed control,
initial-equation isolation checks and supplied-reference comparisons. Shared
objects are referenced by content hash from earlier evidence archives. Exact
source-tool provenance remains private. Diagnostic timings do not establish
the production runtime target.

The Case 1 residual requires further model/network-input localization. Case 3
prefault extension is separate ongoing work and is outside this evidence seal.
All six cases, all 36 original disturbances, direct bus-frequency validation and
the 20-second runtime target remain open. No acceptance limit is relaxed.
