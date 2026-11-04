# HYG3 prepared curves and shared integration

The governor evaluates its equations directly in Java. Gate-curve interpolation
now reuses normalized point arrays instead of cloning and rebuilding them during
every forward or inverse evaluation.

`PsseHyg3GovernorData` advances a curve revision when a scalar or array point
setter is called. The governor prepares both interpolation directions together,
keyed by data-object identity and that revision. Repeated evaluations reuse the
prepared arrays. Invalid point updates fail preparation without publishing a
partial descriptor. Built-in curves, implicit endpoints, extrapolation and
source-specific output clipping retain their existing behavior.

Gate scaling continues to use the live power base and convention. The prepared
geometry does not capture numerical gains or integration state.
Replacing the data owner rebuilds the descriptor even if revisions coincide.
Custom governor classes and custom point providers retain the original getter
dispatch because their getters may carry behavior not represented by a revision.
Public array getters continue to return defensive copies.

`Hyg3PreparedCurveTest` covers forward/inverse evaluation, scalar and array
writes, ownership changes, built-in and partial curves, scaling, invalid updates
and custom-provider fallback. Its allocation check warms the actual methods and
then exercises both directions. The measured loop allocates fewer than 1 KiB in
total, including measurement overhead; it does not allocate point arrays for
each evaluation. This is a component allocation result, not a complete-simulation
speedup claim. The test is registered in `CorePluginTestSuite`.

Before changing the runtime, fresh flat and fault SMIB runs were frozen with
source, bytecode and complete state/boundary traces. The independent boundary
checks passed in both control modes and the deadband/partial-curve profiles.
After rebuilding the optimized runtime, all those complete traces match the
frozen traces byte for byte. The existing independent equation, initialization,
limiter and coupled governor checks also pass.
The registered core suite retains the same previously recorded failures in
endpoint monitoring, converter conformance and disturbance smokes.

The prepared curve is derived numerical data. It does not introduce a physical
state or replace governor history. Further work remains on complete native-state
coordinate qualification, extended limit/event trajectories and integration
coverage. Existing stored-state access and the distinct source/typed deadband
conventions remain part of that work. This change does not classify the complete
model as `VERIFIED`.

## Shared numerical storage

`PsseHyg3Governor` directly implements `StaggeredEulerParticipant` and
`IntegrationStateAccess`. Modified Euler and staggered Euler reuse the same
Java equations and nine stored coordinates. Their public buffer order is input
filter, integral, derivative lag, valve, gate, electrical-power filter, water
flow and the two double-derivative lags. Bypassed coordinates retain their zero
rates. This integration order matches the existing named-state metadata and
does not claim that every stored coordinate equals an independently published
controller state.

The model prepares state, predictor-state, rate, algebraic and deadband buffers
once. A corrector retains the original arithmetic order and its own first-stage
rates. No temporary numerical record is created in either update method. The
prepared gate-curve identity/revision and custom-provider fallback remain intact;
all scalar inputs and gains still use their existing live bindings.

Trial evaluation and state projection can refresh algebraic commands, gate
backlash output and turbine power. They never commit the backlash reference or
frequency-deadband mode. Only a positive-duration ordinary corrector or
`completeStaggeredStep()` commits those histories. Rejecting a nonfinite trial
occurs before any coordinate is written. Shared access also rejects an
unfinished modified-Euler predictor, a failed initialization or replacement of
the initialized machine/data owner. Coherent reinitialization resets the guard,
history and diagnostic state.

Read-only snapshots expose current coordinates, the last evaluated rates and
their evaluation coordinates/stage, plus the committed gate output, backlash
input reference and deadband mode. The deadband mode is encoded as low=0, neutral=1, high=2. Snapshot maps are
detached and immutable; they allocate only when requested. Turbine-head
observation uses the currently published gate output without committing history.

## Shared-method qualification

`Hyg3SharedStateTest` checks both control modes and algebraic bypasses, shared
rates against the ordinary equations, externally integrated modified Euler,
limiter projection, backlash and hysteresis acceptance, invalid buffers/trials,
predictor/ownership guards and state diagnostics. Its warmed update loop measures
zero allocated bytes for each control mode with each integration method. This
component result does not establish a whole-simulation speedup.

Before the storage-only refactor, the maintained executable and complete flat
and fault state/boundary traces were frozen. At that checkpoint all retained
modified-Euler traces remained byte-for-byte identical. The subsequent
direct-speed equation correction described below intentionally changes the
double-derivative trajectories. Existing import, equation,
limiter, prepared-curve and independent SMIB tests continue to pass.

`Hyg3SharedSmibTest` exercises the retained synthetic selector, deadband and
partial-curve profiles with both solvers. It explicitly uses matching 0.000001 pu
network voltage-correction tolerances and 0.5 ms steps; algorithm defaults are
unchanged. The fixture keeps its established current-P/impedance-Q load
conversion. It asserts actual shared-participant coverage, stationary no-event
states and the existing independent fault-boundary limits. All raw rows,
negative-time startup samples and both event sides are retained. Startup is
explicitly compared with the initialized snapshot, without inventing a
negative-time integration history.

Across these retained profiles, worst voltage range-normalized RMSE is below
0.000031 with modified Euler and 0.0000019 with staggered Euler. Worst
rotor-angle RMSE is below 0.000130 and 0.000110 degree, respectively. These
boundary results do not qualify the complete internal controller trajectory.

The lossless bounded traces retain all independent channels, all stored states,
physical diagnostics and committed histories. Each channel's maximum error and
its time are recorded. Transient state-coordinate and cached-output lifecycle
differences in that earlier matrix remain diagnostic. The state-coordinate and
quarter-cycle extension below adds independent acceptance; extended limiter/event
and longer-duration qualification remain incomplete in the work list.

## Published compensation coordinates

The integration lags retain their existing storage coordinates. The published
derivative-block states use positive compensation coordinates. For derivative
input `u`, derivative lag `zD` and the two double-derivative lags `z1`, `z2`:

```text
x2 = KD / TF * zD
x7 = K2 / TF * z1
x8 = K2 / TF * z2
D1 = KD / TF * u - x2
D2 = K2 / TF^2 * u - x7 / TF - x8
```

`readPublishedState()` exposes these coordinates in diagram order without
changing numerical storage, physical equations, predictor slopes or committed
histories. The existing typed canonical-state getters retain their distinct
coordinate convention. Scalar gain/time writes remain live, and the read uses
caller-owned arrays. Its warmed allocation check remains zero in both methods.

`tools/hyg3_reference_probe.py` captures synthetic nonunit-gain/time-constant
discriminators with both controller modes, two fine steps and five-second
quarter-cycle runs. Each profile has a verified cold pair with identical
numerical exports. Complete scripts, inputs, logs, outputs and exact source/runtime
provenance remain in a hash-pinned private archive. No source-case inventory is
used by this probe or its reports.

`Hyg3StateConformanceTest` independently reconstructs the compensation-state
two-history recurrences from the captured executed coefficients. Nonunit gains
discriminate which section owns `K2`; a unit-gain profile alone could not prove
that placement. The three maximum recurrence defects are below 2e-10, 8e-10 and
3e-9, respectively. Reconstruction uses the executed coefficient precision;
production import continues to preserve entered parameters.

## Direct filtered-speed feedback

The double-derivative branch's integral input sums the direct filtered-speed
path together with the first and second derivative paths. The runtime previously
omitted the direct term. It now uses:

```text
integral_rate = KI * (Pref - auxiliary - u - D1 - D2 - Rgate * CV - Relec * Pe)
```

The independent integral recurrence agrees within two binary32 state ULPs
when the direct path is included and rejects its omission. A retained negative
control also shows the isolated equation regression failing before the fix:
the expected integral rate is -0.006 while the old implementation returns zero.
The correction applies to the shared Java equations, so both integration
methods receive the same physical fix. The PID branch is unchanged. Captures
hold auxiliary input at zero; the existing typed auxiliary convention remains.

In the retained fine-step coupled matrix, worst integral-state error falls from
about 0.000114 to below 0.000006 pu. This improvement follows the documented
signal path; no gains, initial states, offsets or filtering are fitted.

## Complete-state and quarter-cycle gates

The extended coupled test compares all nine published state coordinates and
boundary channels with every independent sample. It applies separate, explicit
absolute envelopes per state and per method/interval, retained alongside the
measured maxima and their times. All raw integration coordinates are also saved.
The initialized states, fine-step matrix and five-second quarter-cycle matrix
pass under both methods. These profiles do not activate every limiter branch;
the active-limit extension below adds separate coverage.

At quarter-cycle steps over five seconds, worst voltage range-normalized RMSE is
below 0.000216 with modified Euler and 0.000022 with staggered Euler. Worst
rotor-angle RMSE is below 0.000775 and 0.000337 degree, respectively. Both methods
retain the requested 0.001 voltage NRMSE and 0.01-degree angle-RMSE gates. The
electrical-power filter has a larger finite-stage envelope with modified Euler;
its independent maximum remains below 0.000769 pu. The state-specific gates do
not substitute one common large tolerance for the smaller filter/state errors.

Reference and physical timestamps remain separate. The comparison verifies the
reference clock through explicit binary32 accumulation and preserves every
physical grid point and event-side duplicate. Negative-time startup records
retain the declared initialized-snapshot comparison. No time shift or sample
reduction is used.

With backlash bypassed, the reference's allocated input-history diagnostic
`VAR2` stays at its initialized value. The tests assert that behavior and retain
the raw channel separately from the live `BACKLASH_INPUT`. They compare the
active gate output through `VAR3`; they do not report an unused allocated slot
as a physical gate-input error or introduce fake integration history to match
it. The active-backlash extension below qualifies that history separately.
The complete model is not promoted to `VERIFIED` by this slice.

## Active actuator limits and backlash

`Hyg3LimitConformanceTest` adds independent synthetic reference reversals with
opening/closing valve-rate limits, upper/lower gate-position limits, moving
backlash and a backlash width that holds the output fixed. Both control modes
run for one second at 0.5 ms and quarter-cycle intervals. Fresh cold pairs have
identical complete numerical exports. The hash-pinned private archive retains
the exact generator, entered/executed coefficients, raw outputs and runtime
provenance. All state, diagnostic and electrical-boundary samples are retained,
including startup and duplicated event timestamps.

The active gate-limit captures disprove clipping the controller integral or
command to the gate-position bounds: both cross those bounds while the physical
gate remains limited. The runtime now constrains only the physical valve and
gate coordinates at those actuator limits. Removing the unsupported integral
projection also removes its unsupported outward-slope suppression. An archived
negative control fails the corresponding isolated projection test.

Source-native `Db2` is a per-unit gate width. It is no longer divided by the
governor MVA base. `BacklashConvention` makes that choice independent of the
frequency-deadband convention; existing typed clients retain their MW-on-base
interpretation. The reader selects the source convention and the builder copies
it explicitly. The retained negative control distinguishes the two units with
an input movement smaller than the entered width.

The backlash input reference changes only when an accepted step moves the
backlash output. Trial evaluation and zero-duration predictor/corrector refresh
cannot accept this history. Replaying each recorded physical gate coordinate
through the actual runtime agrees with both the output and input-history
diagnostics within 0.00000002 pu. This is a component replay result; the coupled
trajectories have their own gates.

## Input jumps and two-step history

For the PID input filter, the independent reference reversals distinguish the
latest pre-jump slope from the older two-step integration history. With `fLeft`
and `fRight` evaluated at the current event endpoint, and `fPrevious` retained
from the preceding interval, the qualified recurrence is:

```text
fEvent = (fLeft + fRight) / 2
xNext = x + h * (1.5 * fEvent - 0.5 * fPrevious)
```

Using `fPrevious` in both places changes the event integration. The second and
third reversals discriminate the formulas even when the first starts from
equilibrium. Independent recurrence defects are below 0.00000003 pu. A frozen
negative control fails the coupled quarter-cycle input-filter gate with an
error of 0.00000762 pu. Correct event handling reduces the maximum across the
staggered active-limit matrix to below 0.00000023 pu.

HYG3 opts into `StaggeredEulerParticipant.useAcceptedEndpointRateAtInputDiscontinuity()`.
The solver copies its latest accepted RHS into a reusable event buffer before
the notified input refresh can overwrite it. It leaves the two-step history
separate, performs no additional model/network evaluation and adds no per-step
numerical allocation. Reactivated participants use their initialized slope.
Other participants retain their existing policy. Scheduled network-event
boundaries also retain their independently gated policy: extending the new rule
to those boundaries changed existing strict fault-state results and was not
promoted. An external input change must still call `notifyDiscontinuity()`.

## Coupled active-limit acceptance and precision limits

Both methods compare every published state and diagnostic against explicit
per-channel absolute envelopes. Physical actuator bounds, rotor-angle RMSE and
electrical-boundary gates are enforced. The original five-second fault matrix
retains its stricter gates. The two methods remain different integration
schemes: modified Euler has larger finite-step state differences immediately
after a reference jump.

Discrete input-history releases/reversals are checked by count, direction,
accepted pre/post values and a maximum displacement of one physical interval.
Unconditional history errors remain in the lossless diagnostics. A one-step
release-time difference can put the methods on opposite sides of a 0.04 pu
history jump; those rows are explicitly counted rather than hidden by a large
continuous-error tolerance or removed from the data.

Slow valve-rate plateaus expose a separate precision limit. On those intervals,
the reference gate exactly follows binary32 accumulation of `gate + h * valve`
to within 1e-12 pu after decimal export. Its cumulative rounding is about
0.00002 pu at the fine interval. The test verifies this recurrence directly;
production equations and state storage retain double precision.

Across the active-limit matrix, maximum bus-voltage error is below 0.000006 pu
and rotor-angle RMSE below 0.0033 degree. Some small-response voltage channels
still fail the strict 0.001 range-normalized target despite their small absolute
errors: worst voltage NRMSE is about 0.0733 at the fine interval and 0.00401 at
the quarter-cycle interval. Raw reference ranges and both errors remain reported; a constant range
produces an undefined normalized value. These results qualify the declared
absolute/state/event gates, not universal strict range-normalized acceptance.
Longer active-limit runs, typed hysteresis, tuning transitions and complete
model acceptance remain in the tracker.

The focused model/solver matrix passes all 121 checks without skips. The complete
core-plugin module passes 457 checks with four existing skips. The registered
core integration suite executes 2,784 checks with the same four failing methods
as the retained baseline, no errors and 13 skips. Its existing endpoint-monitor,
converter-conformance and disturbance-smoke failures remain unresolved; the
suite is not reported as fully passing. The private evidence capsules retain
the tested source/classes, core library, dependency hashes, complete comparisons,
negative controls and test reports.

## Zero-time filter and servo observations

Independent synthetic captures now exercise zero `Tt`, `Td` and `Tp` separately
in both control modes, at 0.5 ms and quarter-cycle intervals. Cold repeats have
identical numerical exports, and the reference leaves the entered constants
unchanged. The [governor diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_PDF/Generator/Governor/Governor%20HYG3.pdf)
identifies the corresponding power filter, input filter and servo; their
source-specific published coordinates are checked against these captures.

`readPublishedState()` now reports the algebraic filter/servo output when its
time constant is zero. Previously it returned dormant integration storage.
The existing stored-state accessors, physical equations and integration
coordinates retain their meanings. The getter reuses the model's algebraic
buffer and commits neither backlash nor deadband history. Allocation checks
include bypassed models under both methods and measure zero bytes in the
warmed update/observation loops.

The recorded power-filter coordinate agrees with electrical power within
0.00000004 pu; the bypassed servo agrees with its clipped proportional command
within 0.0000000001 pu. In double-derivative mode the bypassed input coordinate
equals the deadband output. The PID algebraic-feedback reconstruction retains
a finite-stage residual up to 0.00000220 pu at the quarter-cycle interval;
that residual is reported separately from the exact direct-path identities.

Worst published-coordinate errors in the 0.5 ms comparisons are:

| Bypassed coordinate | Before (pu) | Modified Euler after (pu) | Staggered after (pu) |
|---|---:|---:|---:|
| Input filter | 0.0004623 | 0.000001372 | 0.000000315 |
| Servo | 0.012267 | 0.000008926 | 0.000008926 |
| Power filter | 0.120337 | 0.000119579 | 0.000002368 |

All complete before/after comparisons preserve every physical output,
differential coordinate, stored history, timestamp and event-side sample
exactly. Only the corrected published coordinates differ. An isolated frozen
pre-change executable fails the new bypass-observation regression.

`Hyg3BypassConformanceTest` retains the complete reference and actual traces,
per-channel errors, state envelopes and the existing 0.001 range-normalized
boundary limits. State envelopes, voltage gates and the 0.01-degree rotor-angle
gate pass under both methods. The new quarter-cycle modified-Euler comparisons
expose an existing active-power NRMSE of 0.001128–0.001131, which exceeds 0.001;
the frozen executable reproduces it. Fine-step modified Euler and both
staggered resolutions pass the power gate. This failure remains enabled, so
the focused run reports 47 of 48 tests passing, with no skips or errors. The
observation correction does not qualify complete model accuracy.

The immutable synthetic capture archive is
`testData/private/verification-artifacts/hyg3-bypass-qualification-20261102/captures.zip`,
SHA-256 `09899d9ed070291f2e27fcbe403ef844fe6b95983c3c2ede7db16664b7ec7711`.
Its private manifests retain exact source provenance and executable identity.
Remaining work includes the quarter-cycle power gate, the PID feedback
observation residual, additional bypass/preparation boundaries, tuning changes
and longer active-limit trajectories.

## Zero derivative-filter preparation

The imported HYG3U1 zero `Tf` setting keeps the derivative branches active with
an effective time constant of four outer integration intervals. It is not a
derivative-disable switch. Independent zero/explicit-time controls establish
this behavior in both control modes at 0.25 ms, 0.5 ms and quarter-cycle steps.
All cold repeats reproduce their complete numerical exports. At the two fine
steps, zero and explicit `4H` exports match exactly; at the quarter-cycle step,
physical outputs match exactly and internal-state differences are below
0.0000001 pu. Explicit `2H` and fixed-time controls distinguish the rule.
The entered constants remain unchanged in every accepted capture.

The reader selects `FOUR_OUTER_INTERVALS` and the builder preserves it.
Initialization prepares the zero-setting replacement from the configured outer
interval. Both integration methods, derivative-compensation observations and
the zero-input-filter feedback calculation use the same effective time.
Local stage intervals do not alter it. Reinitialization prepares a new value;
positive entered `Tf` values remain live and are not floored by this rule.
The typed `BYPASS` convention remains available separately. An imported zero
setting requires a positive configured outer interval before initialization.

For the double-derivative fault control at 0.5 ms, rotor-angle RMSE changes from
0.0305103 to 0.0000234 degree under modified Euler and from 0.0304968 to
0.0000187 degree under staggered integration. Every state, boundary channel,
timestamp and event-side duplicate remains in the comparisons. The separate
state envelopes retain the fast compensation-state truncation differences;
these are not described as exact internal-state parity.

`Hyg3DerivativePreparationConformanceTest` retains the cold controls and complete
zero-setting comparisons under both methods. All voltage and angle gates pass.
The quarter-cycle modified-Euler active-power NRMSE remains 0.001127–0.001130,
above the unchanged 0.001 gate. Fine-step modified Euler and all staggered power
comparisons pass. The new test leaves this failure enabled alongside the
existing bypass-profile power failure. Shared-update tests cover local-step
changes, reinitialization, import/builder propagation, typed bypass and live
positive parameters. Warmed update/observation loops include the prepared-zero
path and allocate zero bytes under both methods.

The independent capture archive is
`testData/private/verification-artifacts/hyg3-derivative-preparation-20261102/captures.zip`,
SHA-256 `0d46fcac218ec495737f9156af95a5d9b104c1db9d8da0d3a9f4f4804fa63ea5`.
Private validation evidence also retains the pre-correction executable control,
complete comparison traces, rejected nonfinite reference probes and test reports.
Zero water time and a fixed small derivative time at a coarse outer interval
produced nonfinite reference results; they are excluded from accepted reference
fixtures and remain separate validation work.

### Localizing the remaining power gate

A diagnostic-only playback supplies the reference's exact mechanical-power
samples to the machine while retaining the ordinary machine/network solver.
The prescribed output matches every positive-time reference sample exactly;
the retained initial machine output differs by 0.000000014 pu.
Across the original bypass profiles, quarter-cycle active-power NRMSE remains
0.00112–0.00114. This localizes the remaining gate downstream of the governor
output; it does not qualify the complete governor or justify changing its
equations to compensate for machine/network integration differences. Production
code contains no playback override and the accuracy threshold is unchanged.

## Fast feedback and pre-initialization validation

HYG3 now participates in the common parameter-validation report. Checks cover
finite scalar data, nonnegative time constants, existing selector/curve/physical
constraints, gate/valve limits and the prepared zero-`Tf` convention. The
report uses the governor's outer interval even when another controller uses
local subdivisions. It never changes entered parameters or initializes states.
Zero water time remains an explicitly reported, unqualified algebraic extension.

The bypassed PID input equation requires the denominator
`1 + Rgate * (K2 + K1 / effectiveTf)`, with the derivative term omitted for the
typed derivative bypass. A zero or nonfinite denominator now prevents both
direct governor initialization and initialization through either solver's
shared validation path. Previously direct initialization could succeed while
publishing nonfinite derivatives. A frozen pre-change runtime fails the new
regression. Finite, nonzero denominators are not replaced or clamped.

For the PID branch with positive `Td` and effective `Tf` and nonnegative
feedback gains, validation also screens the frozen-integral input/derivative
subsystem. Its Jacobian is `[-a, b; d, -d]`, where
`a = (1 + Rgate * (K2 + K1 / Tf)) / Td`,
`b = Rgate * K1 / (Td * Tf)` and `d = 1 / Tf`.
The faster decay time is `2 / (a + d + sqrt((a-d)^2 + 4*b*d))`.
This may be shorter than either entered lag. A value below the configured
step margin produces a warning; this partial subsystem calculation is not a
complete stability certificate for the governor, turbine or network.

Independent combined-bypass and short-positive-time controls retain all states
and terminal channels. Cold repeats reproduce both the finite exports and the
explicit nonfinite rejections. The probe retains raw output and a
`rejected-nonfinite` manifest instead of producing an accepted CSV for those
failures. Combined zero `Td`/`Tf` controls agree with explicit `Tf = 4H` to
within 0.0000003 pu across the finite captures; the fine double-derivative
controls agree exactly. Some fine PID combined-bypass references are nonfinite
and cannot establish trajectory acceptance for that setting.

A particularly important negative control has PID `Tf = H` at a quarter-cycle
interval. Its reference compensation coordinate exceeds 2.5 million despite
finite, limited mechanical power. The staggered run also develops a large
internal oscillation, while modified Euler remains bounded. Another
double-derivative `Tf = H` profile has a compensation-coordinate discrepancy
near 1.8 pu despite small voltage and rotor-angle errors. These profiles remain
unqualified; terminal-output agreement and finiteness do not replace internal
state acceptance. No gains, lags, integration defaults or accuracy thresholds
were changed to conceal these differences.

The focused model/common-validator run executes 78 tests with two existing
quarter-cycle power-gate failures, no errors and no skips. The complete
core-plugin module executes 486 tests with no failures and four existing skips.
Warmed HYG3 state/update/observation loops still allocate zero bytes. The new
guard runs at initialization, and the added validation work stays outside the
integration loop. Complete named samples from the finite fast-feedback runs
are unchanged by the validation/initialization guard.

The cold-reference archive is
`testData/private/verification-artifacts/hyg3-fast-feedback-20261102/captures.zip`,
SHA-256 `feab110e4cea07232d1b04f7182f6ea048c7c5c6b7acc7f94efb325d24822fa7`.
`Hyg3FastFeedbackReferenceTest` checks reference quality and preparation controls;
it does not claim coupled trajectory qualification. Private evidence retains
all diagnostic comparisons and the frozen-runtime negative control. Remaining
work includes fast-state integration accuracy, combined PID bypass qualification,
the existing power gate and longer-duration state/limiter histories.

## Accepted pre-event controller rates

HYG3 now opts into the shared solver's accepted pre-event derivative snapshot.
The final ordinary endpoint evaluation includes its committed gate/deadband
histories, so those cached rates are valid immediately before the network event.
The event average uses this snapshot, while the two-step integration formula
keeps its older derivative history. The existing primitive event buffer is
reused; the change adds no per-step allocation or controller evaluation.
Modified-Euler equations and external reference-event handling are unchanged.

Independent recorded states distinguish the two histories at fault clearing.
For compensation coordinates, reconstructing the event increment with the
accepted left rate agrees within 0.00000000018 pu; using the older integration
rate misses by up to 0.000025762 pu. The maintained coupled regression covers
both controller modes, altered gain/time profiles, both event sides and the
available integration intervals. Its maximum first-increment error is
0.00000000064 pu against a 0.000000001 pu limit. A frozen previous governor
executable fails the same regression. A separate all-coordinate reconstruction
also checks the input filter, integral, valve, gate, power filter and flow;
its maximum residual is 0.000000059 pu.

The correction restores the strict standard-state and zero-derivative-time
staggered comparison groups. For example, the five-second, quarter-cycle PID
standard profile's input-filter error falls from 0.0000008884 to 0.0000004139 pu,
and its derivative-compensation error falls from 0.000001299 to
0.0000007701 pu. All existing trajectory thresholds remain unchanged.

The double-derivative power-filter-bypass profile still has a separate coupled
discrepancy at 0.5 ms. Replaying its recorded speed and electrical power through
the same controller equations gives the following maximum errors:

| Published coordinate | Coupled (pu) | Prescribed-input replay (pu) |
| --- | ---: | ---: |
| Bypassed power | 0.0000034172 | 0.0000000299 |
| First double-derivative compensation | 0.0000001708 | 0.00000000070 |
| Second double-derivative compensation | 0.0000008760 | 0.00000000378 |

This diagnostic preserves all samples, startup observations and both event sides.
It verifies input delivery and read-only evaluation, integrates the shared
states with separate accepted-event and two-step histories, and retains every
published coordinate. It localizes these failures to the coupled speed/power
inputs; it does not qualify the complete machine/network trajectory or replace
the unchanged coupled bypass gate. The remaining controller integral, valve,
gate and flow differences are retained as well. No model coefficients, state
precision or accuracy limits are adjusted to compensate for the coupled error.

The registered HYG3 tests run 62 tests with three existing failing comparisons:
the coupled staggered bypass states and the two modified-Euler coarse-step
power groups. The core-plugin module runs 486 tests with no failures or errors
and four skips. The complete registered suite runs 4,073 tests with sixteen
failures, no errors and thirteen skips, resolving exactly the two HYG3 staggered
state groups without adding any new failing identities. All thirty-six retained
modified-Euler state, bypass and derivative-preparation trajectories remain
identical across every sample and channel. Warmed state/update/observation tests
continue to report zero allocation under both integration methods.

Private validation retains the executed source/classes, full paired samples,
prescribed-input traces, independent all-state recurrence audit and old-model
negative control. These qualify the event-history correction and input
localization; they do not establish complete HYG3 or system acceptance.

## Ten-second fault and reversal extension

`Hyg3LongDurationConformanceTest` extends both controller modes to ten seconds
using independently captured fault and large-backlash reversal profiles. Each
profile has two cold captures; all six pairs have identical channel files and
unchanged entered constants. Standard faults cover quarter-cycle and half that
interval; reversals cover quarter-cycle integration. Both shared integration
methods use the existing state and boundary envelopes without relaxation.

The pinned archive is
`testData/private/verification-artifacts/hyg3-long-duration-agent-20261104/captures.zip`,
SHA-256 `0ca02f1095d2a8505158311eac18a9a3b42f674e17476383bad157e975be989b`.

The comparison horizon now derives from the number of distinct nonnegative
reference integration ticks. At quarter-cycle resolution, the accumulated
reference clock reaches 9.99989032745 seconds before taking its final step to
10.0040569305 seconds. Stopping the comparison at exactly 2,400 steps previously
omitted that final sample. The extended comparison runs 2,401 steps, retains
startup rows and both sides of every event, and records both clocks separately.
It does not shift, interpolate or truncate either trajectory. At half the
interval, the reference completes 4,800 steps at 10.0003261566 seconds.

All standard ten-second fault comparisons pass the existing full-trajectory
voltage, active/reactive power range-normalized RMSE and rotor-angle gates.
The largest active-power NRMSE is 0.000288. However, all four method/mode
standard-state groups exceed their five-second absolute envelopes late in the
run. One PID backlash comparison also exceeds its gate-state envelope, with
maximum error 0.0000156594 pu. These failures remain visible; this extension
does not qualify the model for all longer trajectories.

Prescribing the recorded speed and electrical power still leaves slow-state
differences. The replay integrates the same nine controller states and retains
accepted event rates separately from the older integration history:

| Mode | Interval (s) | Maximum gate error (pu) | Maximum flow error (pu) |
| --- | ---: | ---: | ---: |
| Double derivative | 1/240 | 0.000007518 | 0.000009138 |
| Double derivative | 1/480 | 0.000011300 | 0.000014764 |
| PID | 1/240 | 0.000013577 | 0.000014874 |
| PID | 1/480 | 0.000020806 | 0.000022785 |

Thus these late differences cannot be attributed solely to coupled machine or
network inputs. Halving the interval increases the slow-state discrepancy.
Recorded flow states frequently remain on single-precision plateaus, and a
preliminary recurrence audit finds defects around one single-precision unit
in the last place. That observation is diagnostic, not proof of the complete
cause. Production state precision, equations and test thresholds remain
unchanged. Further work must distinguish stored-state rounding, exact internal
reference initialization and controller history before changing equations.

The isolated HYG3 audit runs 72 tests: 63 pass and nine fail, with no errors or
skips. All 62 existing test identities retain their previous outcomes (three
existing failures); the extension adds six failing comparisons. Field-by-field
comparison of all 72 existing exported trajectories finds no numerical changes.
Raw CSV byte hashes can differ because map-derived column ordering varies
between processes. Private validation retains the full traces, test reports,
executed controller classes and a field-based audit. The new failures remain
qualification work, rather than evidence supporting a production equation fix.
