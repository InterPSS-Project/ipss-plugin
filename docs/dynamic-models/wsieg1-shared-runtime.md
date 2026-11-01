# WSIEG1 shared integration and shaft ownership

`Wsieg1Governor` directly implements `StaggeredEulerParticipant` and
`IntegrationStateAccess`. Modified Euler and staggered Euler use the same Java
equations, six numerical coordinates, prepared valve-curve arrays and committed
deadband/backlash histories. The public model parameters and record schema are
unchanged. The [model diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WSIEG1.htm)
distinguishes the governor, valve and four turbine sections; exact independent
schema, executed coefficients and source-document provenance are retained in
the private evidence capsule.

The buffer order is speed lag, valve position and turbine sections one through
four. Turbine bypasses have zero derivatives and synchronize their algebraic
values when a trial state is applied. Numerical evaluation, trial projection and
observation cannot accept backlash or speed-deadband memory. Only an accepted
positive-duration corrector or shared completion commits those histories.
Zero-duration ordinary refresh now exposes rates for state checking while
preserving states and committed histories.

The primary governor owns every turbine coordinate. Its secondary-shaft
companion owns a power-output binding and base conversion, with zero additional
differential states. Both machine updates can therefore observe shaft power
without integrating the turbine twice. A second companion attachment is
rejected. Shared access rejects invalid buffers/steps, nonfinite trials before
mutation, unfinished ordinary predictors and changed machine ownership.
Failed initialization clears derivative-diagnostic status. The solver retains
its audited controller allowlist rather than accepting arbitrary controllers.

## States and diagnostics

Stored coordinates, the last evaluated rates and their evaluation coordinates
are exposed through detached immutable snapshots. Existing named-state keys
remain available, including the accepted valve-output memory and speed-deadband
mode. The separate integration-history map also exposes the accepted valve-input
reference. No snapshot map is created in the numerical update loop.

The independently published first governor state is a compensation coordinate:

```text
published_speed_state = (1 - T2 / T1) * stored_speed_lag
governor_feedback = published_speed_state + K * T2 / T1 * deadband_output
```

`readPublishedState()` performs that observation transform without changing the
stored realization. Bypassed turbine slots expose their algebraic input rather
than a fabricated frozen differential state. The independent ordinary-interval
recurrence discriminates this coordinate from the unscaled lag. Actual entered
parameters remain double precision; recurrence reconstruction uses the reference
run's executed coefficients.

The initialized reference is exposed separately from subsequent live reference
changes. Secondary base conversion, active deadband output, physical valve
output and accepted backlash-input history have explicit read-only accessors.
The reference's allocated `VAR2` remains unused in these captures; its complete
raw values are retained separately from the semantic speed-deadband mode. The
test does not invent a physical history to populate that slot.

## Independent shaft-trip behavior

Fresh synthetic runs include a reference increase before either shaft trips.
This creates moving turbine states and distinguishes genuine continued
integration from a stationary flat trajectory.

After the primary trip, the reference holds the secondary mechanical-power
output and the governor's published physical outputs. Some allocated state
slots nevertheless continue with stale rates. Those raw slots remain in the
lossless diagnostics, with unconditional errors and an explicit inactive-owner
classification. They are not integrated as physical turbine motion in the
production model. Active-owner state gates and held physical-output gates are
separate obligations. Following a secondary trip, the primary governor remains
active and continues its normal turbine response.

Recordings retain all bus voltages/angles, both shafts' electrical and mechanical
channels where present, every stored/published coordinate and all reference
diagnostics. Inactive machine output channels follow the declared zero-output
reference convention, with explicit activity flags; this does not reset the
machine's stored integration state.

## Qualification and numerical limits

The retained matrix covers linear and nonlinear valve curves, single and split
shafts, unused secondary fractions, faults and both excited shaft trips at
0.5 ms and quarter-cycle intervals. Every independent profile has an identical
cold pair. Before initialization, diagnostic VAR storage is explicitly zeroed:
without this, unused slots exposed tiny allocator-dependent values. The original
captures are preserved, and every calculated state and electrical channel is
bit-identical before and after that diagnostic preparation.

Component tests check pure RHS evaluation, externally integrated modified Euler,
bypasses, rate/position limits, rollback, ownership/predictor guards, published
coordinates, immutable snapshots and passive companion behavior. Recorded active
valve inputs independently qualify the actual backlash output and accepted input
reference. Every previously retained ordinary-method state and boundary value
remains bit-identical; the new observation columns are additional diagnostics.

The warmed numerical loops allocate zero bytes in both methods, including
published-state reads. This demonstrates reusable model storage, not a measured
whole-simulation speedup.

The final governor and its private passive companion are registered as audited
machine-input consumers. Their equations read machine speed and owned turbine
states, with no passive-bus frequency dependency. Required-measurement selection
can therefore omit an unrequested passive bus, and a co-located audited exciter
can retain its prepared execution. The positive test checks actual ST1
preparation, not just a configuration flag. With both integration methods and
both shaft arrangements, required-only and all-bus measurement runs preserve
every recorded physical/state/history value exactly. Controller observers and
explicit passive-bus frequency requests still retain all required measurements.

Coupled comparisons retain every startup record and event-side duplicate, with
separate physical and reference timestamps and an explicitly checked binary32
reference clock. They use distinct per-coordinate and per-boundary absolute
envelopes for each method and interval, alongside voltage range-normalized RMSE
and rotor-angle RMSE. No angle offset, time shift, parameter fit or sample
reduction is applied. Modified Euler and staggered Euler retain different
finite-step trajectories, especially immediately after a reference jump.

For this half-second matrix, every measured bus-voltage channel meets the 0.001
range-normalized RMSE gate, and both recorded rotor channels meet the
0.01-degree RMSE gate. Worst errors across the retained profiles are:

| Method | Interval | Voltage range NRMSE | Rotor-angle RMSE, degrees |
| --- | --- | --- | --- |
| Modified Euler | 0.5 ms | 0.000623 | 0.0000529 |
| Modified Euler | Quarter cycle | 0.000831 | 0.001590 |
| Staggered Euler | 0.5 ms | 0.000623 | 0.0000505 |
| Staggered Euler | Quarter cycle | 0.000246 | 0.000684 |

State and electrical-boundary absolute gates are separate and method-specific.
For example, a rate-limited reference change produces a different first-step
valve displacement under the two integration formulas. That finite-step
difference is retained rather than removed with a parameter or timing change.

Extended active-limit/hysteresis profiles, independent bypass/time-constant
correction variants, initialization/base edge cases, reconnection,
longer-duration qualification and full model acceptance remain in
the implementation tracker. This slice does not promote the model to `VERIFIED`.

Final validation passes all 204 focused model/solver checks without skips. The
complete core-plugin module executes 457 tests with no failures and four existing
skips. The registered core suite executes 2,810 tests with the same four failing
methods as the retained baseline, no errors and 13 skips; it is not reported as
fully passing. Private capsules retain the exact source, runtime overlays,
dependency hashes, complete comparisons, negative control and validation reports.
