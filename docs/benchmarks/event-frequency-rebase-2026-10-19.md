# Event-frequency rebasing after nonlinear convergence

The staggered solver now marks its zero-time initial network solve as
`INITIAL`. This enables the existing frequency-angle synchronization after
nonlinear convergence and before continuous states advance. Previously the
call was marked `DIRECT`, so frequency retained the angle from the event
processor's preliminary linear solve. The resulting phase discrepancy entered
the frequency filter and affected frequency-sensitive plant control.

The previous stage is restored even when the network solve throws. Ordinary
endpoint reuse, the final network solve, filter state, physical parameters,
integration recurrence and acceptance limits are unchanged. This correction
does not alter the frequency filter's gain or time constant.

## Retained coupled evidence

Eight archived small-network profiles retain all 12,040 rows, including negative
history and duplicate fault sides. They cover electrical Q/QV control and plant
control with frequency response enabled or disabled, at 0.5 and 0.25 ms. Runs
start from ordinary local power flow and initialization. No reference state,
angle offset or time shift is imposed.

The tested production source differs from the earlier isolated trajectory
candidate only by two explanatory comment lines. For the frequency-enabled
plant profiles, the maximum active-reference lag-state error changes as follows:

| Step | Before (pu) | After (pu) |
|---|---:|---:|
| 0.5 ms | 0.00266628 | 0.00000797212 |
| 0.25 ms | 0.00267553 | 0.00000832975 |

Peak active-power error is below 9.65e-5 pu across all eight profiles. Bus 1's
maximum frequency-deviation error decreases from about 0.00194/0.00197 pu to
0.0001418/0.0001422 pu at the two steps. Remaining frequency, voltage and reactive
power residuals prevent a claim of complete coupled trajectory acceptance.

Twenty-eight focused tests pass. Three guards fail against the preceding solver:
initial/final stage ordering, restoration after an exception, and the physical
frequency-angle boundary at both fault application and clearing. The physical
guard detects a 0.0347277-radian mismatch before the correction.

The paired frozen registered run finds 2,331 tests: 2,311 pass, nine skip,
six abort and five fail. The failure identities match the preceding registered
baseline, including the Case 1 flat-drift failure. The solver tests are selected
explicitly alongside the registered suite. An earlier run against current
workspace resources has two fewer optional aborts and is retained separately;
the frozen-resource run is the paired comparison. The system gate is not green.

## Completed Case 2 converter control

The preceding converter-voltage and event-memory corrections have now completed
the full ten-second, 1 ms bus-6307 fault control. Compared with the retained
read-only baseline, only 16 of 48,124,050 physical values differ: fault-bus angle
by at most 4.58e-6 degrees and fault-bus voltage by 4.12e-14 pu. All other physical
channel values match exactly. Common cold values match; the snapshot schemas
and some history observations differ. This extends the earlier 1.2-second
physical comparison to all 10,005 rows and 4,810 channels.

Fresh supplied-reference scoring retains these required results:

| Quantity and period | Passing channels | Worst error | Limit |
|---|---:|---:|---:|
| Voltage prefault NRMAE | 2,000/2,000 | 0.000463305 | 0.001 |
| Voltage fault/postfault NRMAE | 1,995/2,000 | 0.0137413 | 0.005 |
| Speed proxy prefault NRMAE | 103/103 | 0.0000638304 | 0.001 |
| Speed proxy fault/postfault NRMAE | 103/103 | 0.000818356 | 0.005 |
| Rotor prefault maximum, degrees | 102/103 | 0.0285314 | 0.02 |
| Rotor ten-second RMSE, degrees | 102/103 | 0.203219 | 0.2 |

Range-normalized RMS errors are retained as diagnostics; they are excluded from
the required mean-error pass flag. Generator speed remains a frequency proxy.
These converter corrections do not resolve Case 2's remaining errors.

## Wind-startup discrimination

A fresh autonomous run uses an existing two-bus reference with nonunit shaft
speed, the original wind parameters, power-control selector and enabled
electrical speed multiplication. All 2,003 rows are compared by ordinal,
including both startup-history rows. Maximum voltage error is 5.74e-6 pu,
electrical power-order error is 2.15e-6 pu and torque-integral error is
1.20e-7 pu. The speed-reference state retains a 1.48e-5 pu discrepancy, which
does not drive the active power-control branch in this profile.

For Case 3, all 290 local electrical controllers' first power-order derivatives
match the declared selector equation within 3.56e-15 pu/s. This includes 93
active wind controllers and the largest initial rate of -5.54413 pu/s. The
audit uses the ordinary cold states and entered limits. It is equation
consistency, not direct Case 3 internal-state parity. The independent retained
wind trajectory supports preserving these nonzero startup rates; forcing them
to zero would not be a justified initialization repair.

Case 3's large coupled error remains unresolved. The separate full Case 2
solver-stage control is still running and is excluded from the completed
evidence seal. Its first attempt failed because the diagnostic worker could
not find its numerical library at the chosen directory depth; the failed
attempt is retained and the corrected layout uses identical worker code.

Private evidence set `2026-10-19-event-frequency-rebase` retains the tested
sources, failing-before guards, both registered runs, completed Case 2 converter
control, wind-startup comparison and Case 3 rate audit. It links the earlier
coupled-profile evidence by content hash.

All six cases, 36 disturbances, direct bus-frequency validation and the
20-second runtime target remain open. No acceptance limit is relaxed.
