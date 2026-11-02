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
