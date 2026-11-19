# Case 1 full-event machine voltage replay

All 30 observed machine/event combinations across the six original Case 1
disturbances satisfy the rotor limits when their terminal voltage is
prescribed from the supplied measurements. The worst ten-second rotor RMSE
is **0.0100750 degrees**, and the worst prefault maximum is **0.00413010
degrees**. These are conditional component results, not coupled-network
acceptance.

## Scope and controls

The three faults retain 20,005 rows each; the three generator trips retain
20,004 rows each. Cold history and both sides of each event are preserved.
Duplicate event rows do not advance differential states. Original trip
targets disconnect at their event, and their physical machine channels
become zero while retained internal coordinates remain available.

Only terminal voltage magnitude and phase are prescribed. Magnitude is the
supplied terminal-voltage channel. Phase is reconstructed using the original
machine stator equation, supplied electrical states, rotor angle and terminal
P/Q. Reconstruction checks the magnitude closure and consistency between
colocated machines. This use of supplied internal states constructs the
external voltage boundary; it does not load those states into the replay.

Machines, exciters, stabilizers and governors evolve freely using original
parameters and the retained staggered integrator. Initialization uses the
independently computed local operating point. Across 1,105 checked cold
operating-point and state values, the largest difference from the corresponding
coupled cold snapshots is **2.62013e-14**. Derivative caches and evaluation-stage
metadata are not claimed equal before the first derivative evaluation.

The output observer reads terminal power without changing machine caches.
Current refresh occurs explicitly at the prescribed network boundaries.
The input, runtime, row ordering, event sides and active status are checked.
No angle offset, clock shift or parameter fit is applied.

## Rotor comparisons

| Original event | Worst prefault maximum, degrees | Worst ten-second RMSE, degrees | Unit with worst full-window RMSE |
| --- | ---: | ---: | --- |
| Bus fault 01 | 0.000952598 | 0.00790570 | 7146/1 |
| Bus fault 02 | 0.00413010 | 0.01007501 | 6041/1 |
| Bus fault 03 | 0.000575954 | 0.00187074 | 5444/5 |
| Generator trip 01 | 0.000268770 | 0.00119609 | 1073/1 |
| Generator trip 02 | 0.00130961 | 0.00476634 | 7166/1 |
| Generator trip 03 | 0.000251466 | 0.00109466 | 4076/1 |

Each row covers five supplied machine channels. The current numerical limits
are 0.02 degrees prefault maximum and 0.2 degrees full-window RMSE.

For the first bus fault, the coupled sparse-network run has a worst rotor
RMSE of 2.187948 degrees. Under supplied voltage boundaries, the worst RMSE
among the same five machines is 0.00790570 degrees. At the coupled run's worst
unit, 7189/1, RMSE falls from 2.187948 to 0.00529406 degrees. This comparison
supports investigating the network response and other connected devices.
It does not uniquely identify a faulty network term or prove every machine
model correct.

## Remaining component differences

All physical-channel and electrical-state errors remain in the evidence.
The largest connected electrical-coordinate error is 0.000454313, in the
salient-machine d-axis damper coordinate at 6041/1. Raw full-window errors
also include the disconnected machines: the largest is 0.0783207 in the
retained transient-voltage coordinate at 1078/1. The supplied offline slots
and the local retained states have different post-disconnection behavior.
Those raw differences are not removed from the report.

The field-voltage output has a 0.863980 pu discrepancy at the clearing-side
sample of fault 02, unit 6349/11. The replay reports the preceding field-voltage
value at that zero-time boundary. All samples, including this discrepancy,
remain scored. The rotor result therefore must not be described as complete
agreement of every machine/controller output.

## Reproducibility and remaining work

A separate local-voltage control retains 2,405 rows through 1.2 seconds,
including fault application and clearing. The added observation leaves the
original 50 coupled channels exactly unchanged. Cold and both history
snapshots also remain equal. With recorded local terminal voltage, the
machine replay reproduces rotor angle within 8.40193e-8 degrees, P within
4.64472e-7 MW, Q within 5.36427e-7 Mvar and electrical coordinates within
1.54400e-9. This bounds replay error through the event; it is not a complete
ten-second local replay control.

An additional immutable comparison captures both coupled tolerance controls
through two seconds, with 4,005 rows each and identical cold snapshots.

| Two-second diagnostic | Network tolerance 1e-6 | Network tolerance 1e-8 |
| --- | ---: | ---: |
| Worst prefault rotor maximum, degrees | 0.00575997 | 0.00574799 |
| Worst available-prefix rotor RMSE, degrees | 0.0150673 | 0.0151181 |
| Mean logged network iterations per call | 11.0554 | 22.1880 |
| Logged network iterations, 4,005 calls | 44,277 | 88,863 |

Tightening the tolerance roughly doubles iterations without improving the
two-second rotor error. The later drift still requires the full-event
comparison. The shortened postfault window is not an acceptance result;
voltage normalization continues to use the full ten-second reference range.

Private evidence set `2026-10-19-case1-full-machine-voltage-replay` retains
the six completed replays, original inputs, cold snapshots, reconstructed
boundaries, physical and electrical-state comparisons, plots, exact producers
and runtime hashes. The first-event comparison also retains the original
coupled sparse-network output through its existing evidence dependency.
Private supplement `2026-10-19-case1-local-voltage-and-tolerance-prefixes`
retains the completed local replay, both immutable coupled prefixes,
their comparisons and the unchanged setup sources for the ongoing runs.

Two separate coupled runs are being used to obtain a local-voltage replay
control and test whether reducing the network tolerance from 1e-6 to 1e-8
improves the full event. They add read-only terminal-phase and electrical-state
observations; they are not included as completed results in this evidence set.

The six-case, 36-event objective remains open. These conditional replays do
not establish direct bus-frequency agreement, autonomous full-system accuracy
or the production runtime target. No production equations or acceptance limits
are changed by this diagnostic.
