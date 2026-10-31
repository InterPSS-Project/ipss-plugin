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

Before changing numerical storage, the maintained executable and complete flat
and fault state/boundary traces were frozen. After the change all retained
modified-Euler traces remain byte-for-byte identical. Existing import, equation,
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
differences remain diagnostic rather than accepted whole-model parity. These
fine-step, half-second runs do not replace production-step, long-duration or
extended limiter/event qualification. The model remains incomplete in the work
list.
