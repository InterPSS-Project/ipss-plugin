# Converter gain and voltage-memory decomposition

The remaining 0.01514 pu fault-right power discrepancy in the attached REECC1 /
REPCA1 control comes from REGCA low-voltage active-current gain, not a current
orientation error or an active-current limiter. This conclusion uses all 1,205
independent samples, including startup and both event sides, with pinned input
and output hashes.

For this synthetic terminal bus, branch current is reconstructed from the full
phasors as `I = (V1 - V2) / (R + jX)`. The line has no charging and the terminal
has no other injection. Rotating current by the negative terminal angle gives
its active/reactive components in the terminal-voltage frame. The recorded
converter angle differs from that terminal angle by at most 3.40e-8 rad.
Reconstructed branch current differs from the state-based source by at most
0.0010071 pu; this retained finite reference-network residual is much smaller
than the disputed current difference. It is not asserted to be an exact KCL
solution.

Using the recorded regulator state `Ip`, saved voltage `Vs`, terminal magnitude
`V` and declared acceleration `a`, the equations

```text
Vd = (1 - a) * Vs + a * V
G = clip((Vd - Lvpnt0) / (Lvpnt1 - Lvpnt0), 0, 1)
P = V * Ip * G
```

match recorded P within 4.97e-8 pu. Substituting instantaneous terminal voltage
for `Vd` leaves 0.0150796 pu error. Recorded Q agrees with `V * Iq` within
1.48e-8 pu. The separate filtered-voltage low-voltage power limiter remains
inactive: its minimum current margin is 0.65946 pu. Regulator states and active
commands do not jump at the event. The current-gain curve, source orientation,
and physical current limits are retained unchanged.

At fault application, the exact local-versus-reference power decomposition is:

| Contribution | P difference (pu) |
| --- | ---: |
| Current gain | -0.0151172813 |
| Terminal-voltage magnitude | -0.0000205283 |
| Regulator state | -0.0000000143 |
| Recorded-channel rounding | -0.0000000039 |
| Total | -0.0151378278 |

The local bus angle also changes when its injection changes; that is a network
response, not evidence of a wrong current-frame transformation.

## Saved-voltage recurrence and solver lifecycle

Across every reference sample, including duplicate event timestamps,
`Vs[n] = (1-a)*Vs[n-1] + a*V[n-1]` holds within 7.16e-8 pu. Using the preceding
filtered voltage instead leaves 0.19448 pu error. Updating from the current
sample's terminal voltage also fails at events. Unrecorded internal network
iterates cannot be independently reconstructed; the evidence establishes the
accepted-sample recurrence, not every internal reference-solver operation.

The existing optional Java memory path commits on every network solve. Its
lifecycle therefore differs between integration methods:

| Explicit diagnostic configuration | Maximum saved-voltage error | Maximum physical P error |
| --- | ---: | ---: |
| Default method, optional memory | 0.0792285 | 0.00384761 |
| Default method, plus event-left capture | 0.0396864 | 0.00355790 |
| Staggered, optional memory | 0.00798856 | 0.000717216 |
| Staggered, plus existing endpoint reuse | 0.0000938730 | 0.0000585452 |

The default solver does not currently invoke the converter's early event-left
capture; its first saved value is 0.93077 rather than 1.01. The diagnostic-only
capture corrects that boundary but cannot remove extra predictor/corrector
memory advances. Staggered integration captures the event correctly, but its
redundant INITIAL solves advance the memory again. Explicit endpoint reuse
removes those extra advances in this fixture; it is not a replacement for a
shared accepted-memory contract.

A common design needs separate committed voltage memory and trial network
outputs. Only an accepted physical step or accepted event should commit the
memory recurrence; current/derivative evaluations and repeated network solves
must not. This design remains pending. No production default changes in this
slice, and the event-input correction remains a separate committed change.

## Numerical work and scope

For this 0.3 s diagnostic, default integration performs 3,600 network calls;
staggered performs 2,400, or 1,202 with explicit endpoint reuse. Optional memory
conservatively disables network acceleration and uncompensated-current solve
eligibility; all runs record zero acceleration mixes. That fallback is distinct
from memory correctness. These counts are not whole-system runtime benchmarks.

The immutable evidence set is
`verification-artifacts/reecc1-gain-memory-decomposition-20261104`. It contains
full phasor/current/state traces, diagnostic source and work counters. Fourteen
tests complete with no failures or skips, including the controlled network
diagnostic, the full-sample reference gain/recurrence guard, existing event
lifecycle tests and unchanged attached-plant replay gates. Broader coupled
trajectory and default-policy qualification remain open.
