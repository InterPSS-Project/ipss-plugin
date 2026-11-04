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

### Reactive-power localization

The delayed-application diagnostic was also compared against the retained
`1e-6` reference network-tolerance controls. The tighter reference changes peak
machine reactive power by about `9.6e-6` pu, but it does not explain the residual:
staggered reversal reactive-power NRMSE changes only from 0.00333947 to
0.00325565. No reference or acceptance limit was replaced.

At every sample, independently reconstructed source-branch power agrees with
the implementation's machine getter within `2.4e-8` pu active power and `6e-11`
pu reactive power. Thus the remaining reactive discrepancy is not a stale
machine-power getter in this diagnostic. In the upward control it peaks near
0.2785 seconds at `3.34e-5` pu, while static-load reactive-power error stays
below `3.3e-7` pu and electronic reactive-power error below `1.6e-9` pu.
Investigate the coupled machine electrical states and terminal voltages next;
these measurements do not justify changing composite-load coefficients.

The production tap SMIB checks additionally require complete complex-power
balance, including transformer/feeder losses and both physical shunts, and
agreement between machine power and independently solved source-branch power.
These constraints supplement the existing endpoint KCL and active-power tests.

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

## Integration ownership validation

The tap owner now requires a matching positive-duration modified-Euler predictor
before its corrector. Repeated predictors, orphan or mismatched correctors, and
shared-step completion during an ordinary predictor are rejected before timers
or physical taps change. Target-band changes are accepted only between completed
intervals. Zero-duration event refreshes remain read-only and do not consume a
pending predictor. A failed predictor network solve permits retry without
advancing accepted history; a failed corrector retains the existing tap/matrix
rollback.

The regression first failed against the previous implementation because an orphan
corrector was accepted. The guarded implementation passes the composite
initialization, shared-state, shared-SMIB and tap-control tests (80 tests, no skips).
This is lifecycle validation, not resolution of the full-trajectory discrepancy
described above. No equations, coefficients, integration ordering or acceptance
thresholds were changed. All eight complete recorded traces (upward, downward,
reversal and deadband controls under both methods) are byte-identical to the
preserved baseline executable. The private lifecycle evidence manifest records
the distinct production class hashes, actual test classpaths and trace hashes.

## Tap-action history discriminator

A separate staggered-integration experiment tested whether a tap action needs
an explicit derivative-history discontinuity notification. It covered upward,
downward and reversing commands, each with immediate or one-interval-delayed
physical tap application and with or without notification. All 12 runs retained
every comparison sample and satisfied endpoint current and complex-power balance;
none satisfied the unchanged complete-trajectory criteria. These are diagnostic
runs, not 12 newly qualified model profiles.

For the reversing command with delayed physical application, notification changed
reactive-power range-normalized RMSE from `0.00333947` to `0.00329214`, still above
`0.001`, while rotor-angle RMSE worsened from `0.0000285250` to `0.0000928993`
degrees. Without delayed application, reactive-power normalized RMSE remained
approximately `0.00803`. A blanket history reset is therefore not an established
fix, and neither the reset nor the diagnostic tap delay was added to production.

The retained independent capture already includes six generator states; new
captures were unnecessary. At the first action, the tap changes at approximately
`0.0700 s` while source power and all four electrical states remain unchanged.
Source power first changes at `0.0705 s`, with the electrical states still
unchanged; electrical states first respond at `0.0710 s`. This separates command
publication, electrical application and state response in the observed trace.
It does not establish that a global state freeze or time shift is the correct
implementation. The next investigation must distinguish the network/device
integration stages and their reported outputs, preserving the physical endpoint
balance and both methods' state ownership. Full trajectory and deadband-boundary
qualification remain open.

The private evidence archive preserves the diagnostic Java source, all 12 full
traces, bounded logs (including the initial rejected configuration attempt),
per-channel metrics, reference state samples and per-file hashes. Archive SHA-256:
`51f8553b71eca6406694d3192d0c7748db11108552adefbf003133f4e25a83e7`.

An independent reconstruction uses the recorded source voltage phasor, P/Q,
field voltage and six states with the fixture's guarded unsaturated round-rotor
equations and 100 MVA machine/system bases. Over the first responding interval,
`0.0705–0.0710 s`, the largest electrical-state increment residual is
`3.91e-8` for ordinary two-step Adams–Bashforth history, versus `1.87e-7` for
a fresh Euler step and `2.86e-7` for the fault-event left/right averaging rule.
The first result is consistent with the captured state precision. This local
discriminator supports retaining ordinary derivative history when the internally
scheduled tap becomes electrically visible; it does not justify treating that
internal action like an externally imposed network fault. It also does not
resolve the remaining full-run reactive-power discrepancy. The reconstruction
script and all per-state candidate residuals are retained separately, SHA-256:
`7b0b9177c02abe054a7b457270e02e1a84213fe39c7750cdf39c981ba8083245`.

## Accepted command and electrical application

The runtime now separates the accepted tap command (`LTC.Tap`) from the
transformer ratio actually used by the network (`LTC.AppliedTap`). Each interval
applies the previously accepted command during its network evaluation. The
new command produced by that interval's timer becomes accepted only after a
successful solve. It cannot change the electrical endpoint that produced it.
This is an integration-stage contract, with no added delay parameter and no
change to generator derivative history.

The complete reversing trace reproduces all three independently observed
boundaries under this contract: command publication at `0.0700 s`, source power
response at `0.0705 s`, and the first electrical-state response at `0.0710 s`.
The recorded low-side observation at command publication uses the new command
with the preceding boundary current. Tests retain that prepared observation
separately from the physical solved voltage and check physical current and
complex-power balance at every sample. Neither quantity replaces the other.

Failed intervals restore both the preceding physical ratio and the accepted
command/timer history. A successful retry followed by a full network-matrix
rebuild preserves all four transformer admittance entries to `1e-12`.
Modified Euler and staggered integration both exercise this lifecycle.

The final focused run passes 11 tests; the preceding composite/capacity and
recording run passes 51 tests. These counts establish lifecycle and equation
checks, not full trajectory acceptance. The strict complete-trajectory diagnostic
still reports failure for all six active up/down/reversal profiles, with all
samples retained and the original thresholds unchanged. For the staggered
reversing profile:

| Channel | RMSE | Maximum absolute error | Range-normalized RMSE |
|---|---:|---:|---:|
| Source rotor angle (degrees) | 0.0000285250 | 0.0000571949 | Not used |
| Active power (pu) | 0.00000284894 | 0.00000672942 | 0.000376031 |
| Reactive power (pu) | 0.00000894798 | 0.0000192650 | 0.00333947 |
| Source-bus voltage (pu) | 0.00000137865 | 0.00000278771 | 0.00393902 |
| Load-parent voltage (pu) | 0.000000566885 | 0.00000169620 | 0.000946625 |
| Infinite-bus voltage (pu) | 0.000000107128 | 0.000000320002 | 0.00299553 |

An independent reconstruction from recorded generator states, field voltage,
power and voltage phasors identifies a separate precision effect. Over part of
the response the recorded transient field state stays constant despite computed
per-step changes between `8.65e-9` and `2.88e-8`, below its binary32 half-ULP.
The cumulative missing increments reach `4.94e-6`, close to the double-precision
runtime's `5.03e-6` peak discrepancy in that state. This is evidence consistent
with state-update quantization in the reference, not proof of its internal
implementation. No rounding, fitted coefficient, threshold relaxation or global
history reset is introduced. Complete trajectory qualification remains open.

The private `cmldblu1-tap-command-stage-20261104` evidence contains the source,
independent reconstruction scripts, eight complete traces for both methods,
rollback/rebuild tests, bounded logs and per-file hashes. Archive SHA-256:
`02c7b3ce2087b7d21ac0d9b5193c49890a0c55259cf817ef9867a40812d8fa66`.


## Timer measurement after an accepted command

A separate measurement-coordinate defect is now corrected without moving the
command or electrical application boundaries. The timer previously sampled the
physical low-side voltage even when the accepted command differed from the
applied transformer ratio. The source instead prepares its compensated control
voltage from that accepted command and the existing primary boundary current:

`Vcontrol = abs(Vlow * command/applied - Zcomp * Iprimary/command)`.

This changes neither the solved voltage nor the transformer matrix. When command
and applied ratio coincide, the original arithmetic path is retained exactly.
The sampler uses primitive arithmetic and does not advance any history.

The retained downward and reversal profiles discriminate these coordinates at
0.2484986 and 0.3294975 seconds, respectively. Physical compensated voltage is
about 0.948355 pu, above the 0.945 pu upper bound; the prepared observation is
about 0.943398 pu, inside the 0.915–0.945 pu band. Independent reconstruction
matches the recorded control observation within 1.5e-8 pu. With the unchanged
0.01-second control delay and 0.0005-second interval, the next accepted counter
must decrease by 0.05. Both runtime methods previously held the saturated
counter for an extra interval. All four before-change tests fail by 0.05;
the corrected histories agree through their complete decay to zero.

The tests also cover compensation disabled, identical command/applied ratios,
and repeated pure observations with unchanged bus voltages, histories and all
four transformer admittance entries. Existing failed-step rollback, successful
retry and matrix-rebuild checks remain enabled. These checks qualify timer
measurement and history only. The six active full-trajectory profiles still
fail their unchanged normalized gates; reversing-profile reactive-power NRMSE
remains approximately 0.00333947. No precision rounding, timing adjustment or
trajectory-tolerance change is introduced.

Final focused validation: 62 tests, zero failures/errors/skips. The private
`cmldblu1-tap-measurement-20261104/validation.zip` retains source, before/after
logs, exact test reports and reference provenance. SHA-256:
`19024b74de52ff8027526c8bc14555ac0b9a6cc4e4c189f1b740717f62b4cb8f`.
