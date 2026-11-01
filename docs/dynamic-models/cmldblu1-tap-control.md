# Composite-load tap-control qualification

The legacy composite currently prepares its transformer tap during initialization,
but does not implement runtime tap movement. This is an open implementation gap
for both modified-Euler and staggered integration. The fixed-tap trajectory gates
do not qualify enabled tap control.

## Independent discriminator

The synthetic static/electronic SMIB uses the existing represented distribution
fixture, equal static/electronic fractions, repeated faults with reactance 0.1 pu,
and 0.01-second control and tap-adjustment delays. Only the tap-control flag
changes between the enabled, disabled and initialization-only controls. Each
profile was cold-generated twice; every captured field repeats exactly. Complete
raw samples, startup history and both sides of each event are retained privately.

| Flag | Step | Initial tap | Observed runtime behavior |
| --- | --- | --- | --- |
| 1 | 0.5 ms | 1.05019795895 | Tap moves during and after faults |
| 0 | 0.5 ms | 1.05019795895 | Tap remains fixed |
| -1 | 0.5 ms | 1.05019795895 | Tap remains fixed |
| 1 | 0.25 ms | 1.05019795895 | Same sequence of tap values, with shifted action times |

The identical initial taps are an observation for this legacy fixture, not a
general interpretation of flag zero. The installed documentation describes the
newer composite dialect's flag, delay and diagnostic fields; it is supporting
context rather than proof of the legacy runtime equations. Exact source hashes,
engine provenance and entered/initialized constants remain in the private captures.

## Unresolved downward transition

At 0.5 ms, the first upward action occurs at approximately 0.07000005 seconds,
followed by two more 0.005 increments. At approximately 0.10150012 seconds,
the tap jumps from 1.06519794464 to the lower limit, 0.910000026226.
At 0.25 ms, the same jump occurs at approximately 0.10074959 seconds.
Halving the step therefore does not eliminate the large downward transition.

This is not yet an accepted tap-control equation. Do not reproduce it with an
unexplained limit assignment or change physical parameters to match the trace.
The following discriminators separate downward control, direction reversal,
pending tap actions and limit handling.

## Localized pending-action/deadband interaction

Additional cold pairs independently change the lower limit, control delay,
tap delay and compensation, then exercise fault-free target changes. The probe
now supports `tap-reference-up`, `tap-reference-down`,
`tap-reference-reversal` and `tap-reference-deadband`. These profiles change both
voltage-band endpoints through the reference API, preserve the band's width,
and record the actual before/after constants in the evidence manifest.

| Discriminator | Observation |
| --- | --- |
| Lower limit 0.95 | Large jump lands at 0.95 instead of 0.91 |
| Control delay 0.02 s, tap delay 0.01 s | First action moves from 0.07 s to 0.08 s; large jump remains |
| Control delay 0.01 s, tap delay 0.02 s | First action moves to 0.08 s; subsequent actions are farther apart; large jump remains |
| Zero compensation impedance | Large jump remains |
| Fault-free upward target change | Normal 0.005 increments, bounded at the upper limit |
| Fault-free downward target change | Normal 0.005 decrements |
| Fault-free upward-to-downward command reversal | First reversed action is a normal 0.005 decrement |
| Fault-free return to deadband with a pending action | Direct jump from 1.06519794464 to 0.910000026226 |

In the last profile, the band becomes approximately [1.030, 1.060] pu at
0.1 seconds. The low-side voltage is about 1.05038 pu. The normalized control
delay counter decreases while the previously scheduled action time remains
approximately 0.10150009 seconds. At that pending time, the tap jumps to its
lower limit. The corresponding fault-clearing capture shows the same sequence.
This isolates the observed transition to a pending action expiring after return
to the deadband; it does not prove a particular source-code defect.

The [WECC specification, transformer section](https://www.wecc.org/sites/default/files/documents/meeting/2024/WECC%20Comp%20Load%20Model%20Specification_final.pdf)
describes delayed discrete steps until voltage returns within the band. It does
not justify a direct lower-bound jump in that situation. This source describes
the newer composite family, so its rules must not be silently substituted for
an exact legacy contract. Keep the legacy deviation explicit when implementing
and qualifying deadband hold/cancellation behavior.

Seven additional cold pairs match every captured field exactly. The reversal
pair has differences of at most `1.35631564269e-19` in diagnostics `VAR69` through
`VAR72`; every other field, including tap actions, states and network boundaries,
matches exactly. Both unmodified captures are retained, and those diagnostic
differences are reported rather than discarded or normalized away.

The additional private archive includes the complete captures, source probes,
per-field cold comparison, transition analysis and integrity checks. SHA-256:
`46df94e9454567a72351a1339d4cef93f5d7d93e4aa39972cfc8fdcb6caff680`.

## Shared-runtime requirements

- Advance control and adjustment timers only at accepted simulation endpoints;
  algebraic network iterations and predictor evaluations must not advance history.
- Keep one owner for tap position, pending direction and timers. Both integration
  methods must invoke the same event/history implementation.
- Rebuild or update the transformer network stamp exactly once per accepted tap
  action. Rejected steps must preserve the previous tap and matrix state.
- Expose tap position and timer diagnostics without allocating per-step maps when
  recording is disabled.
- Test both directions, deadband return, reversal, bounds, disabled control,
  omitted transformer, repeated initialization and failed-solve rollback.
- Compare full coupled SMIB trajectories under both methods after the transition
  contract is resolved; the current reference captures alone do not prove parity.

No production equations or acceptance tolerances changed in this investigation.
The private evidence archive includes the complete cold captures, numeric
analysis, source snapshot and per-file integrity manifest. Archive SHA-256:
`b230582a4bedb754c7486bb405308250d55c712b969601b0c508eaf140aff8be`.
