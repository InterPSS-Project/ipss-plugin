# URGS3T shared runtime and independent qualification

URGS3T has an exact standard-record import path and a shared Java equation
implementation. Its catalog status is `LOADABLE`; complete trajectory acceptance
remains open. The GAST implementation is not used as a substitute.

## Numerical contract

The record contains 33 constants, in the order exposed by
`Urgs3tGovernor.CONSTANT_NAMES`. Import rejects a short or extended record and
invalid finite-time, rating, droop, limit and curve settings. Unsupported bypass
combinations are rejected rather than silently replaced with arbitrary times.
The entered constants remain unchanged.

The runtime stores five continuous coordinates: valve, engine compensation,
exhaust temperature, valve average and governor lead-lag compensation. Modified
Euler and the shared staggered solver evaluate the same right-hand side and
project the same valve bounds. Numerical stages use reusable primitive arrays;
state dictionaries are materialized only when requested. Tests measure warmed
per-step allocation in both paths.

Deadband memory and turbine-output publication advance at accepted endpoints.
Trial evaluations and zero-duration refreshes do not commit either history.
An explicit segmented-run resume may publish the already accepted turbine
output through `publishAcceptedRunBoundary`, without advancing either history.
Discrete reference commands use `applyReferenceEvent`, which preserves the
first accepted left derivative across multiple command writes. Continuous
upstream reference updates use `setRefPoint`. Reinitialization resets the
reference revision, output history and state diagnostics.

The opening-rate limit is applied before division by `T1`; it does not impose
a symmetric closing-rate limit. The load-limit branch changes the opening
bound when the valve exceeds its filtered average by `Linc`.

Initialization retains the entered `Vmin` and `Vmax` and projects only the
initial valve into that interval. The reference and other coordinates still
represent the solved operating point. If the point is infeasible, engine and
valve-average derivatives expose the mismatch instead of silently expanding the
physical bounds to manufacture equilibrium. When engine feedthrough is active,
the initial turbine publication uses the curve output after valve projection.
Reinitialization recomputes these coordinates and clears accepted command history.

## Explicit source and diagram conventions

The independent executable and its published diagram differ. These are
represented by immutable `RuntimeConvention` values, not by fitted parameters:

| Behavior | `PUBLISHED_DIAGRAM` | `SOURCE_RECORD` |
|---|---|---|
| Exhaust coordinate | First-order lag driven by engine output | Holds its initialized value |
| Turbine output | Live algebraic output | Previous accepted turbine output during stepping; current accepted output at an explicit run resume |
| Damping | On machine base after turbine rating conversion | Inside the turbine rating conversion, using current speed |
| Power deadband | Ordinary backlash/play function | Direction relative to accepted output selects input minus/plus the entered width |

Direct Java construction selects the diagram convention. The standard-record
builder explicitly selects the source convention. This source behavior is
qualified against the exact executable identified in the private evidence;
it is not a claim about every implementation or release of the model.

The thermal discriminator changes `T3` from 0.05 through 0.5 to 5 seconds with
the temperature limiter active. All recorded trajectories are byte-identical.
The valve-increment residual is below `1e-7` with held exhaust feedback; a
reconstructed dynamic lag produces an error above `2e-5`. Thus the held state
cannot be explained solely as a stale observation channel.

Mechanical-power timing is checked independently through the machine swing
increments. Using recorded mechanical power reconstructs the increment within
`1e-10`; substituting the live turbine output fails that discriminator. A
non-unit rating separately distinguishes damping before and after conversion.
No sample shifts are applied to the trajectory acceptance comparisons.

The isolated power-deadband controls vary the entered width over three values,
at two integration intervals, with faults and command reversals. The source
branch selects `input - Db2` above the accepted output, `input + Db2` below it,
and the accepted output at equality. Its reconstructed allocated output differs
by less than `3e-8`; ordinary backlash fails the independent discriminator.
This is an executable-specific behavior, not a replacement definition for
backlash in other models. Both branches remain pure during trial evaluation.

## Reference evidence and remaining work

The synthetic two-bus captures retain all controller states, allocated variables,
machine power/speed/angle/excitation, both bus voltages and angles, raw outputs,
exact inputs, runnable probes and hashes. Cold repeats must be identical.
Comparisons preserve the two held startup samples and every event-side duplicate.
The serialized reference clock is reconstructed from its documented single-
precision accumulation; physical samples are neither interpolated nor discarded.

The suite exercises equilibrium, nonlinear valve curves, rating/idle power,
governor and engine lead-lags, opening-rate limits, active temperature limits,
isolated and combined deadbands, command reversals and fault clearing. It
explicitly verifies that each run uses the requested integration path.

Qualification is incomplete. Resolve remaining source power-deadband and
command-response state/boundary errors without changing acceptance
limits. Extend bypass and time-preparation rules, active-bound release, reconnection,
longer duration and production-step coverage. Full-system tests without this
model establish non-regression only. Raw allocated variables and published
power are retained for diagnosis; passing selected state and boundary gates
does not certify every observation channel.

The command discrepancies must not be conflated. Modified Euler's largest
valve error occurs immediately after a reference reversal. In the sustained
opening-rate segment, reconstructing each reference state increment with
single-precision rounding matches the serialized state within `1e-12`; the
double-precision increment differs by more than `1e-8` per step. Production
continues to use double-precision integration. Small terminal-voltage responses
also fail the unchanged range-normalized gate despite micro-pu absolute errors.
These observations localize remaining work; they do not waive those gates.

## Command and downstream boundary localization

Independent cold controls tighten only the reference network tolerance from its
default to `1e-6` and `1e-8`. Every recorded sample remains byte-identical.
Java runs at `1e-6`, `1e-8` and `1e-10` likewise retain identical complete
trajectories under each integration method. These particular command errors
cannot be corrected by tightening network tolerance alone.

A separate no-command pause at 0.2 seconds distinguishes run-resume publication
from a reference command. On resumption, mechanical power changes to the current
accepted turbine output while all recorded governor and machine states remain
unchanged. The first subsequent speed increment uses the average of the fresh
left and right mechanical derivatives, retaining the older multistep history.
This reconstruction agrees within `1.2e-11`; an unblended increment differs by
more than `7e-9`. A command-only output refresh would miss the no-command control.
The explicit production resume contract below implements this observation
without making ordinary getters or zero-time integration stages commit history.

The diagnostic replay retains both mechanical-power values at duplicate event
timestamps. Modified Euler consumes the left interval's post-event input in its
predictor and the right endpoint's pre-event input in its corrector. Assertions
check the actual mechanical increment, not just the recorded power value. The
staggered power replay checks its existing unblended two-step mechanical update;
it does not claim to reproduce the independently observed resume treatment.

| Diagnostic | Worst bus-1 voltage error | Implication |
|---|---:|---|
| Exact recorded mechanical power, machine states integrated | `1.96e-6` pu | The small-response voltage discrepancy persists downstream of the governor |
| Exact recorded mechanical power and machine states at network stages | `2.46e-7` pu | Most of that discrepancy comes from the machine trajectory |

Both diagnostics cover the active opening-rate and temperature-limit profiles
under both methods. The full-state replay prescribes machine states and
excitation at each network stage and checks that the network solve preserves
those coordinates. It is a localization experiment, not acceptance of machine
integration or the governor. The classical source's excitation observation is
its fixed internal emf; it has no dynamic exciter state. All channel samples,
startup rows and event sides are retained.

The unchanged coupled comparisons still enforce their original limits. In
particular, small voltage ranges amplify these absolute errors in NRMSE; the
replay results do not relax or replace those gates. The separate Heun equation
test confirms the modified-Euler command update, while independent coarse/fine
controls distinguish the reference's staggered command-event weighting.

The new private archives are `network-captures.zip` (SHA-256
`1a4a503a0aa3c9b2d423570019e574d97eae891d480d506aa8cec7c021b13196`)
and `machine-command-captures.zip` (SHA-256
`a2c4fbc8b9f7d38bcc4a562c70d3ca875c52430d3ca63df319c39743ef07fdec`).
Their tests verify hashes, complete manifests and cold-repeat identity.

## Explicit accepted run boundaries

For a segmented source-record run, the host records the pre-resume sample,
notifies the staggered solver if selected, refreshes the governor's accepted
output, and then applies any requested command:

```java
if (algorithm.getSolver() instanceof StaggeredEulerSolver solver) {
    solver.notifyAcceptedEndpointDiscontinuity();
}
governor.publishAcceptedRunBoundary();
machine.setPm(governor.getOutput(machine));
// Apply a reference command here when the segment boundary includes one.
```

This is an explicit run boundary, not every `solveDEqnStep` call. The governor
rejects publication during an unfinished modified-Euler predictor or shared
state trial. Repeated publication is idempotent. It leaves continuous states,
deadband memory, accepted turbine power and command revisions unchanged. The
diagram convention already publishes live algebraic output and needs no change.

`notifyAcceptedEndpointDiscontinuity` captures the first settled machine
derivatives before inputs change. The subsequent event average uses those fresh
left rates while retaining the older two-step history. Repeated notifications
preserve the first snapshot, and the next accepted step consumes it once.
The existing `notifyDiscontinuity` policy remains available and unchanged.
The notification uses the solver's prepared arrays; it does not allocate new
state storage or change the ordinary integration path.

Modified Euler retains its predictor/corrector formula. It uses the explicit
output refresh but does not adopt the staggered event weighting. Both methods
are exercised through command boundaries and an independent pause with no
command. Complete comparison samples retain the pre- and post-resume values.

At a 0.5 ms network interval, maximum mechanical-power errors change as follows:

| Profile | Method | Before explicit resume | After explicit resume |
|---|---|---:|---:|
| Temperature limit | Staggered | `2.4801e-4` pu | `6.6676e-7` pu |
| Opening-rate limit | Staggered | `2.5664e-4` pu | `4.5495e-6` pu |
| Temperature limit | Modified Euler | `2.4683e-4` pu | `2.5151e-6` pu |
| Opening-rate limit | Modified Euler | `2.8830e-4` pu | `4.4805e-5` pu |

The voltage and command-state acceptance failures remain. This change corrects
the independently observed publication and event lifecycle; it does not claim
complete trajectory qualification or alter existing acceptance thresholds.

## Prepared coefficients and active-pole screening

The constructor prepares the immutable engine ratio, engine pole `B*T2`, and
enabled governor lead-lag coefficients once. Both integrators reuse these
scalars and the same primitive state/derivative arrays. Arithmetic grouping is
preserved; all seventy existing short trajectory CSVs remain byte-identical to
the explicit-resume checkpoint, with identical numerical comparison summaries.
This removes repeated coefficient calculations without claiming a measured
whole-simulation speedup. The numerical-stage allocation checks still apply.

Construction rejects overflow, underflow to a zero engine time, and other
nonrepresentable prepared coefficients. Parameter screening checks `T1`,
`B*T2`, and `Tltr`; it checks `T5` only when the governor lead-lag is enabled and
`T3` only under the diagram convention. The source-record exhaust coordinate
is held. Screening neither changes entered constants nor selects a different
integration interval or local-step count. The tests check both conventions,
inactive poles, and unchanged states/history/data.

## Ten-second production-step qualification

`Urgs3tProductionStepTest` registers independent ten-second SMIB comparisons
under both methods. The standard profile includes flat, fault and reference
commands at quarter-cycle and half-quarter-cycle network intervals. Additional
lead-lag and fast-engine profiles cover flat/fault runs at the quarter-cycle
interval. Each reference was generated twice from a cold start; all twenty
captures are complete, finite and byte-identical to their paired repeat.
Entered and initialized constants agree. Both machines' recorded states and
all event-side samples are retained, although the acceptance gates do not yet
qualify every additional observation channel.

The reference's serialized clock requires 2,401 intervals for the quarter-cycle
request and 4,800 for the half-quarter-cycle request. Its last times are
10.0040569305 and 10.0003261566 seconds respectively. The comparison follows
the complete recorded grid, preserving the extra quarter-cycle endpoint rather
than discarding it. `intervals.json` records requested duration, interval count,
physical integration duration and serialized reference endpoint separately.

Fifteen of twenty method/profile comparisons pass the unchanged gates. All flat
controls and the lead-lag/fast-engine fault controls pass under both methods.
The remaining standard-profile failures are:

| Profile | Method | Interval | Unmet gates |
|---|---|---|---|
| Commands | Modified Euler | Quarter cycle | Voltage NRMSE up to `0.00201419`, P `0.00220585`, Q `0.00183239`; valve maximum error `0.00373930` |
| Commands | Modified Euler | Half quarter cycle | Voltage NRMSE `0.00100254`, P `0.00105326`; valve maximum error `0.00195266` |
| Commands | Staggered | Quarter cycle | Valve-average maximum error `6.86163e-6` |
| Commands | Staggered | Half quarter cycle | Valve-average maximum error `1.37736e-5` |
| Fault | Staggered | Half quarter cycle | Valve-average maximum error `1.22902e-5` |

Every rotor-angle comparison passes the `0.01` degree RMSE gate; the largest is
`0.00734883` degrees for quarter-cycle modified-Euler commands. Voltage and
power use the existing `0.001` range-normalized gate. The existing state limits
remain method-specific: `5e-6` for staggered and `2e-4*h/0.0005` for modified
Euler. A passing modified-Euler state check therefore does not prove that its
state error is smaller than the staggered result.

The slow valve-average discrepancy needs further investigation. In the standard
half-quarter fault reference, 2,534 unchanged adjacent state samples have a
nonzero increment predicted from the recorded valve input and the published
first-order lag. Recorded values lie within `5e-13` of binary32 values; the
maximum local two-step recurrence residual is `7.09e-8`. This is consistent
with finite-precision accumulation, but does not prove the full accumulated
error is solely quantization. A separate double-precision two-step replay driven
by the reference's own recorded valve input produces maximum valve-average
errors of `1.13231e-5` for the half-quarter fault, `1.28832e-5` for half-quarter
commands, and `6.50942e-6` for quarter-cycle commands. These reproduce most of
the coupled staggered discrepancy without using Java machine or valve inputs,
localizing further work to the lag's numerical update and stored history.
No rounding was added to production states and
no accuracy gate was relaxed. Modified-Euler command errors and the remaining
internal-state discrepancies remain open qualification work.

The immutable private `production-step-captures.zip` is pinned to SHA-256
`d685c38c70655969f38851008c47e43c5f214266443cda808ecdd8dc86e8a047`.

## Initial valve limits and active-bound release

Independent upper/lower-limit controls disproved the former initial-bound
expansion. The reference leaves entered constants unchanged, projects the valve,
and reports suspect initial conditions with nonzero engine and valve-average
derivatives. The same behavior is reproduced with engine/governor lead-lags and
with a non-unit turbine rating plus idle fuel. Both integration methods match
all initial controller states, variables and mechanical power within `8e-8`.
These intentionally infeasible references remain `rejected-quality`; matching
their diagnostic initialization does not qualify their complete trajectories.

For example, the synthetic upper-limit control requires approximately `0.50001`
initial valve but enters `Vmax=0.4`. Its stored valve is `0.4`; its engine and
valve-average coordinates retain the operating-point values, producing nonzero
initial derivatives. With engine ratio `A/B=0.25`, the initial mechanical output
falls to approximately `0.475008` pu after projection. The old bound expansion
incorrectly kept the initial valve and output at the unconstrained values.

The tests exercise both runtime conventions, both integration interfaces,
opposing commands, unchanged physical bounds and reinitialization into a
feasible operating point. Independent cold pairs also cover feasible narrow
position limits. Flat coupled runs pass under both methods. Complete command
trajectories remain registered failures, with unchanged thresholds:

| Method | Worst voltage NRMSE | Q NRMSE | Maximum valve error |
|---|---:|---:|---:|
| Modified Euler | `0.00315317` | `0.00290654` | `6.70313e-4` |
| Staggered | `0.00250384` | `0.00267062` | `3.96237e-4` |

The staggered command test additionally fails the engine and valve-average
state gates. Its largest valve difference occurs on the first accepted interval
after the upper-limit release command. This requires a separate limiter-history
and integration-stage investigation; the initialization correction does not
claim to resolve it. A direct stored-derivative query was rejected by the
reference API, whose documented reader exposes time, interval, states,
constants and variables. The failed probe is retained privately, and the
unsupported option is absent from the maintained probe.

The immutable private `valve-limit-captures.zip` retains all paired accepted and
rejected diagnostic runs, with SHA-256
`9ce0fbb8bcd04e0611b800fd7477ff85ac1365393b0f04ccdc16b478a7992cd0`.
Host-machine disconnection and reconnection remain separate open work.

## Validation checkpoint

The initial runtime checkpoint's registered suite executes 4,159 tests, with 36 failures, no errors
and 13 skips. The 16 pre-existing failure identities are unchanged; the other
20 are the explicitly registered URGS3T command-response comparisons. The core
plugin module passes 486 tests with four skips and no failures or errors.

Within URGS3T, nine runtime/ownership/history/allocation tests and three
independent equation discriminators pass. Fifty of seventy coupled comparisons
pass the declared state and voltage/power/angle gates, including every flat and
fault comparison. Command failures remain visible in the registered suite.
This checkpoint is not `VERIFIED` or system acceptance.

The boundary-localization focused run executes 100 tests with the same 20
command-response failures and no errors or skips. All 70 coupled trajectory
files and comparison summaries are byte-identical to the initial runtime
checkpoint. The additional boundary tests and the independent Heun test pass;
production equations, numerical precision and acceptance thresholds are
unchanged.

The subsequent registered run executes 4,177 tests with the same 36 failure
identities, no errors and 13 skips. All 18 added tests pass. The private
`boundary-validation.zip` retains that run's reports, complete coupled and
diagnostic traces, test sources/classes and reference-archive hashes over the
initial `validation.zip` runtime checkpoint. Retention verifies byte identity
of the original coupled results and the frozen production classes.

With explicit run-resume publication, the registered suite executes 4,180 tests
with the same 36 failure identities, no errors and 13 skips. The separate
accepted-endpoint solver contract tests pass, and the core plugin module passes
486 tests with four skips and no failures or errors. All seventy coupled
acceptance outcomes are unchanged: fifty pass, and twenty command-response
comparisons remain open. The adjacent `resume-validation.zip` retains the
changed production classes, complete trajectories and test reports over the
boundary-localization checkpoint. The original immutable references are reused.

With prepared coefficients, active-pole screening and registered ten-second
comparisons, the suite executes 4,202 tests with 41 failures, no errors and
13 skips. All 36 preceding failure identities are preserved; the five additional
failures are precisely the long-run gates listed above. The core plugin module
passes 486 tests with four skips and no failures or errors. All seventy original
trajectory CSVs remain byte-identical and their numerical summaries unchanged.
The adjacent private `production-step-validation.zip` retains complete short
and long trajectories, recorded-input diagnostics, reports, source and compiled
classes over `resume-validation.zip`. Full model qualification remains open.

After the physical initial-valve correction, the registered suite executes
4,210 tests with 43 failures, no errors and 13 skips. All 41 preceding failure
identities are preserved; the two added failures are the feasible active-limit
command comparisons described above. Initial-state comparisons, physical-bound
retention, reinitialization and flat controls pass under both methods. The core
plugin module passes 486 tests with four skips and no failures or errors.
All ninety previously retained short/long trajectory CSVs are byte-identical
and their numerical summaries unchanged. The private `valve-validation.zip`
retains the new state/boundary evidence, previous trajectory identity checks,
failed derivative-access probe, regression reports and changed runtime classes
over `production-step-validation.zip`.

Private immutable capture archives retain the complete cold-run evidence; the
current fixture is `urgs3t-initial-20261102/captures-v3.zip`, pinned by the test to
SHA-256 `6045fffa8c31aad9668cfb8f6ebad78dd50be034523d2ccaef5395ad4c47f289`.
The adjacent validation archive retains complete final trajectories, test
reports, source and changed runtime classes over the preceding validated
runtime archive. Earlier unsuccessful comparisons remain documented in the
bounded diagnostic logs.
