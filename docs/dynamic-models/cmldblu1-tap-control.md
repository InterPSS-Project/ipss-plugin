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
The next discriminator should separate downward control, direction reversal,
pending tap actions and limit handling. Independently vary the control delay,
tap delay and lower bound, and inspect the exact legacy diagram or implementation
documentation before choosing production semantics.

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
