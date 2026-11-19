# Case 3 prefault localization

The corrected salient-machine state refresh does not resolve Case 3's coupled
prefault drift. This report separates three autonomous controls from conditional
machine-boundary replays. All use the supplied original measurements and retain
the 0.5 ms step, cold history and unchanged acceptance limits.

## Complete prefault controls

The six original disturbances supply 240 unique physical observation channels,
including 30 bus voltages, 30 rotor angles and 30 generator-speed channels.
Each control covers the full first second, with 2,003 retained history/time rows
and 6,578 local physical channels. All 30 channels fail in each required quantity.
Generator speed remains a frequency proxy, not a direct bus-frequency comparison.

| Control | Worst voltage NRMAE | Worst rotor maximum error (degrees) | Worst speed NRMAE | Network iterations |
|---|---:|---:|---:|---:|
| Original 1e-4 network tolerance | 0.156105 | 16.986820 | 0.119512 | 41,521 |
| Only tolerance changed to 1e-6 | 0.161854 | 17.471551 | 0.124584 | 94,638 |
| Only converter LVG input corrected, 1e-4 | 0.175381 | 17.208856 | 0.131762 | 41,117 |

The worst channels are bus 3054 voltage, machine 1073/1 angle and machine
6082/1 speed. The tolerance control preserves the complete cold snapshot,
initial matrix, right-hand side and first solution exactly. It changes only
the dynamic stopping threshold; initialization remains at 1e-8. It therefore
rules out a tighter stopping threshold alone as the remedy for this drift.

Baseline observed voltage error starts at 2.20e-6 pu at time zero and grows to
0.00411 pu by 0.05 s and 0.10620 pu by 0.2 s. Ordinary local renewable power
falls by approximately 618 MW by 0.05 s. That movement identifies an upstream
localization target; absent corresponding reference converter channels, it is
not itself a measured converter error.

## Conditional synchronous-machine checks

Thirty machine replays start from exactly the same ordinary cold state as the
coupled baseline. Supplied machine states, P/Q and terminal magnitude reconstruct
the reference terminal phasor; its magnitude closes within 2.69e-7 pu. The
reference phasor is a reconstructed input, not a directly measured bus angle.

Replaying local voltage reproduces the coupled rotor trajectory within
0.000605 degrees, establishing useful replay fidelity. Replaying the reconstructed
reference voltage keeps all 30 rotor errors below 0.02 degrees; the worst is
0.007467 degrees at machine 1072/1. This localizes the large coupled rotor error
upstream of these machines. It does not establish full-network acceptance.

The active torque-path audit finds 93 WTTQA1 records, all using Tflag=1, and no
active drivetrain models. Eleven Tflag=0 records are inactive or netted. The
known alternative torque-curve branch is therefore not an active electrical
cause in this Case 3 control.

## Evidence and open work

Inputs, complete outputs, state origins, original event-specific ten-second
normalization ranges, producers, command lines and source hashes are retained
in the private evidence archive. No reference states initialize an autonomous
run; no angle offset, event shift, physical parameter or acceptance tolerance
is fitted. Diagnostic recording/linear-algebra timings do not establish the
production runtime target.

Case 3 remains outside both prefault and full-ten-second acceptance. Further
work must address the coupled renewable/network path and validate all original
disturbances, while preserving the already matched power-flow operating point.
