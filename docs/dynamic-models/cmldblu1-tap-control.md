# Composite-load tap-control qualification

The composite now has a shared runtime tap controller for modified-Euler and
staggered integration. Tap-state agreement, physical endpoint balance and owned
history rollback are tested. Full coupled reference qualification remains open:
the reference's action-sample publication and deadband behavior differ from the
new runtime. Fixed-tap trajectory gates do not qualify those active-tap cases.

## Runtime implementation

`CompositeTapControl` stores scalar tap position, signed control-delay memory,
clock and pending action time. `CompositeTapControlPort` is a zero-injection
leaf with no differential coordinates. Both integration methods advance the
same discrete history using the previous solved voltage. The controller uses
the initialized subsystem MVA base for line-drop compensation, not the raw
negative loading-factor input. Voltage/current calculations use primitive
arithmetic; observation maps are allocated only when requested.
Registering this zero-coordinate owner does not select the staggered solver's
extended machine-derivative preparation path. A regression test explicitly
enables the optional preparation setting and verifies that the discrete-only
owner leaves that path unused.

The controller is attached only for an enabled flag, a represented transformer
and a positive tap step. Disabled and initialization-only flags keep their
existing fixed-tap behavior. A disconnected transformer cannot advance history.
Active controls reject invalid bounds or negative delays before initialization.
`setTapVoltageBand` changes the runtime target between completed intervals.

A tap action updates the transformer's existing matrix entries and invalidates
factorization. Ordinary steps do not rebuild the matrix. The following network
solve accepts the history; failure restores the old tap, matrix entries and
timer values. Getters publish committed history. Reinitializing the leaf resets
its clock/tap without registering another device; matrix reconstruction retains
the transformer stamp exactly once. The container exposes the discrete owner
through `getIntegrationStateOwners()` and named `LTC` diagnostics.

Return to the deadband cancels a pending action and holds the tap. It does not
reproduce the independently observed lower-bound jump described below.

## Verification scope and remaining accuracy work

The registered SMIB test exercises upward, downward, reversal and deadband
targets under both methods, plus both-method failed-solve rollback. Unit tests
cover delays, bounds, reversal memory, zero intervals, zero delays and snapshots.
At every nonnegative reference sample, ordinary target profiles agree on tap
position within `1.2e-7`; maximum low-side voltage error is below `6e-5` pu.
Independent current and active-power balance checks cover every solved endpoint,
including each tap action, with a `2e-6` absolute residual bound.

Those checks are **not full-trajectory acceptance**. Direct comparisons retain
all nonnegative samples and both sides of every recorded event. Against the
original captures, active-power NRMSE reaches about 0.00919, reactive-power NRMSE
0.01564, and external-voltage NRMSE 0.01413. Worst rotor-angle RMSE is about
0.000271 degrees. The strict 0.001 normalized and 0.0002-degree short-run criteria
remain unmet and are printed separately as `TAP_FULL_TRAJECTORY_QUALIFIED=false`.
No established trajectory acceptance limits have been changed.

At the first upward action, the reference publishes the new tap/internal voltage
while static-load and machine active power retain their preceding values. The
new runtime instead records the re-solved electrical endpoint. For example,
reference static power stays near 0.19528823 pu at that sample while the new
endpoint is about 0.19719486 pu. This localizes an observation/integration-stage
difference; it does not establish that it explains every accumulated angle error.
Reducing reference network tolerance to `1e-6` changes low-side voltages by less
than `2e-6` pu and does not remove this action-sample difference. Both original
and tighter captures are retained. Reconcile the stage contract without dropping
samples, changing physical parameters or hiding the raw comparisons.

## Regression validation

### First-action stage discriminator

The upward target control was independently repeated at 0.25 ms, preserving
the same physical parameters and target-change times as the 0.5 ms control.
Relative to the first tap action, both resolutions show:

| Observed quantity | First response after tap action |
| --- | --- |
| Internal low-side voltage | Same sample |
| External bus voltages, machine active power, static-load active power | One integration interval |
| Machine speed deviation above `1e-9` pu | Two integration intervals |

The external-voltage/power delay therefore changes from 0.5 ms to 0.25 ms.
Reconstructed machine power from the source-bus and adjacent-bus phasors agrees
with its channel within `1.7e-7` pu over both complete captures. The external
network channels share the delay; it is not an isolated stale power channel.
This supports staged coupling as the next investigation, but does not prove
the exact internal call order or explain all accumulated trajectory error.

`tools/audit_composite_tap_stages.py` checks the exact synthetic network fixture,
reports explicit response thresholds, keeps duplicate samples, and reconstructs
power from branch impedance. It makes no sample shifts or acceptance claims.
Both fine-step cold runs and every raw field are retained with their hashes;
the comparison does not replace the original strict trajectory gates. Any
future stage change must distinguish physical transformer stamping, internal
load observation and accepted machine inputs under each integration method.

### Electrical-application experiment

A test-only solver wrapper held the physical transformer at the preceding
accepted tap during the endpoint solve. The hold persisted through zero-time
initial solves, so the following interval—not merely the recorder—observed
the old electrical tap. Command history retained its original timing. No
production equations, physical parameters or comparison timestamps changed.
Upward, downward and reversal controls ran under both methods, retaining
endpoint current/power balance checks and all reference samples.

| Reversal metric | Production | Delayed electrical application |
| --- | --- | --- |
| Modified-Euler angle RMSE (degrees) | 0.000148788 | 0.0000948083 |
| Staggered angle RMSE (degrees) | 0.000270553 | 0.0000285250 |
| Staggered active-power NRMSE | 0.00919222 | 0.000376031 |
| Staggered reactive-power NRMSE | 0.00805806 | 0.00333947 |

This is not a qualified replacement. Modified-Euler downward angle RMSE
worsened from 0.000100342 to 0.000144425 degrees. Reactive-power normalized
error remains above the existing limit, and physical low-side voltage differs
by about 0.00494 pu at command-action samples. The reference updates its
internal voltage with the commanded tap before external electrical application.
The implementation therefore needs a justified distinction between command,
internal algebraic observation and applied network tap; a blanket one-interval
delay cannot satisfy the full contract. The diagnostic wrapper and exact test
reports are retained privately and are not installed as a production option.

### Runtime regression results

The final runtime passed the core module checks (486 tests, four existing skips)
and the selected three-phase checks (15 tests). The registered integration suite
ran 3,560 tests with four existing failures, no errors and 13 skips. The failure
names and assertion messages are checked against the retained parent baseline;
this is a regression comparison, not a claim that the full suite passes.
All ten new both-method tap SMIB checks passed. The six ordinary-target full
trajectory assessments still report unqualified, as described above.

Private evidence retains the final reports, source and runtime hashes, ordered
dependencies, and independently repeated tighter-network reference captures.
Earlier diagnostic CSVs and interrupted/failed investigation logs are labeled
separately from the final validation runs.

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

The initial diagnostic archive includes the complete cold captures, numeric
analysis, source snapshot and per-file integrity manifest. Archive SHA-256:
`b230582a4bedb754c7486bb405308250d55c712b969601b0c508eaf140aff8be`.
