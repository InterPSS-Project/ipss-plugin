# Converter voltage input and event memory

REGCA1's optional network-memory path now supplies its decelerated voltage to
the low-voltage active-current gain. Previously only the high-voltage reactive
correction used that voltage; active-current injection and observation used
instantaneous terminal magnitude. Both active-current paths now share the same
input. The independent low-voltage power limiter still uses filtered voltage.

The event callback also captures terminal magnitude before the event processor's
preliminary linear network solve. The next iterative solve advances saved
voltage from this left endpoint. A successful solve consumes the capture; a
failed solve restores it for retry. Reinitialization and disabling the optional
memory path discard pending captures. The default instantaneous mode, DYR
parameters, differential states and integration equations are unchanged.

## Independent evidence

Eight existing, hash-checked small-network reference profiles cover electrical
reactive-power and voltage control, plant voltage control and disabled plant
frequency control at 0.5 and 0.25 ms. All 12,040 rows, including negative-time
history and both event sides, are retained. No new reference simulation or
parameter adjustment was used.

With recorded terminal voltage `V`, active regulator state `Ip`, saved voltage
`Vs` and acceleration `a`, the reference active power satisfies

```text
Vd = (1 - a) * Vs + a * V
P = V * Ip * clip((Vd - Lvpnt0) / (Lvpnt1 - Lvpnt0), 0, 1)
```

The maximum algebraic residual is 4.39e-8 pu. Substituting instantaneous terminal
voltage leaves 0.01508 pu; substituting saved voltage alone leaves 0.01070 pu.
This is a recorded-boundary discriminator, not autonomous trajectory acceptance.

Saved voltage advances from the preceding accepted terminal voltage on every
recorded row, including duplicate event timestamps. That recurrence agrees
within 7.45e-8 pu. At the first fault, the old local event path stored 0.93077 pu
instead of the reference's 1.01000 pu because a preliminary solve had already
changed the bus voltage. Capturing the left boundary corrects that ordering.

## Coupled controls and remaining differences

All profiles start from ordinary local power flow and model initialization.
Comparison uses exact step/event ordinals without shifting trajectories or
injecting reference states.

| Profile family | Previous peak P error (pu) | LVG input alone (pu) | LVG input and event capture (pu) |
|---|---:|---:|---:|
| Electrical Q/QV and plant frequency disabled, six profiles | 0.0152043 | 0.0014601 | 0.0000965 |
| Plant voltage control with frequency enabled, two profiles | 0.0152043 | 0.0015789 | 0.0015814 |

The six-profile peak P error decreases by more than 99.3%. The frequency-enabled
plant profiles retain a separate active-reference discrepancy; the worst
published plant-state error is 0.002676 pu. Read-only bus-frequency observations
localize a spurious first-step frequency response at network events. A separate
isolated solver-stage correction reduces that plant-state peak to 8.33e-6 pu
and brings all eight profiles' peak P error below 9.65e-5 pu. It labels the
initial network solve so the existing final-angle rebase runs before frequency
integration. That solver correction remains separate, uncommitted work.

Voltage and reactive-power residuals remain: worst voltage error is 0.0001818 pu,
and worst Q error is 0.0002313 pu. These residuals do not consistently shrink on
step halving. No broad model or system acceptance is claimed.

The complete original Case 3 prefault LVG-only control still fails all 30
observed channels in each required quantity. Worst voltage NRMAE increases from
0.15610 to 0.17538 and worst rotor maximum error increases from 16.98682 to
17.20886 degrees. The independent converter correction does not resolve the
upstream Case 3 drift. See the separate Case 3 localization report.

## Validation and scope

Fifty-six focused tests pass. The new LVG guard fails before the first correction;
the two event guards and retained-reference boundary guard fail against the
LVG-only implementation. Tests cover common current-source/output behavior,
pure observations, accepted and rejected solves, event capture consumption,
reinitialization, disabling and the previous instantaneous default. The native
boundary guard covers all eight retained profiles without advancing differential
states.

The frozen registered suite finds 2,318 tests: 2,298 pass, nine skip, six abort
and five fail. All five failure identities match the preceding salient-refresh
baseline, including its Case 1 flat-drift failure. This is not a green system
gate. The additional retained-reference boundary test passes in the 56-test
focused run after the registered run's source snapshot was created.

The ongoing full Case 2 production run is separate from the completed
small-network controls. Its first attempted overlay was rejected before time
advanced because the old diagnostic harness does not support iteration-aware
production devices; that failed attempt is retained. The compatible production
baseline's complete 1.2-second prefix differs in only 16 of 5,796,050 numeric
values from the retained full run: fault-bus angle differs by at most
4.58e-6 degrees and voltage by 4.12e-14 pu. Snapshot schemas and history-observer
values differ, although all common cold values match. This prefix comparison
does not prove complete ten-second runtime equivalence.

The private evidence set `2026-10-19-regca-voltage-input-and-case3-controls`
retains completed controls, source snapshots, exact fixture hashes, failed-before
tests, the failed harness attempt and the separate solver-stage candidate.
The running Case 2 corrected trajectory is excluded from that completed seal.

The six-case, 36-event objective, direct bus-frequency comparison, strict
trajectory gates and runtime target remain open. Acceptance limits are unchanged.
