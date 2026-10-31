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
frequency-deadband mode. Only the ordinary corrector or
`completeStaggeredStep()` commits those histories. Rejecting a nonfinite trial
occurs before any coordinate is written. Shared access also rejects an
unfinished modified-Euler predictor, a failed initialization or replacement of
the initialized machine/data owner. Coherent reinitialization resets the guard,
history and diagnostic state.

Read-only snapshots expose current coordinates, the last evaluated rates and
their evaluation coordinates/stage, plus the committed gate output and deadband
mode. The deadband mode is encoded as low=0, neutral=1, high=2. Snapshot maps are
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
pass under both methods. Rate/position-limit and backlash-active extensions
remain open; these profiles do not activate every limiter branch.

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
it. Active backlash/history publication still needs independent qualification.
The complete model is not promoted to `VERIFIED` by this slice.
