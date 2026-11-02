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
companion owns a power-output binding and prepared initial-power normalization, with zero additional
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
changes. Secondary initial-power normalization, active deadband output, physical valve
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

## Bypass observations and active actuator limits

With both speed lead-lag times zero, the published compensation slot is the
live gain-scaled deadband output, `K * deadband(speed - 1)`. It is not the
dormant integrator coordinate. The observation now exposes this algebraic
value; integration storage, zero derivative, controller equations and accepted
history are unchanged. A recorded-input comparison establishes the identity
within binary32 output rounding, and the previous executable fails the new
regression. Reading the observation does not commit hysteresis or advance time.

Independent cold pairs exercise speed compensation bypass, first-turbine and
complete turbine bypass, and both actuator rate and position limits at 0.5 ms
and quarter-cycle intervals. Entered constants remain unchanged at reference
initialization. Bypassed turbine observations equal their algebraic inputs,
both position bounds are reached, and the limited valve trajectories reproduce
the reference's binary32 state accumulation on constant-rate intervals.
That precision check characterizes the reference; production states remain
double precision.

All twenty coupled before/after traces preserve every physical output, stored
state and history value. Only the speed-bypass observation changes. Its maximum
error falls from approximately 0.00934 pu to 0.000250 pu with modified Euler and
0.0000318 pu with staggered Euler at quarter cycle. At 0.5 ms the corresponding
errors are 0.0000275 and 0.000000441 pu. Coupled residuals include differences in
the actual speed input; the recorded-input observation test isolates the mapping.

The extended trajectory matrix is **not fully accepted**. Both methods meet the
0.01-degree rotor-angle RMSE limit, but strict voltage and P/Q range-normalized
RMSE gates remain failing. Quarter-cycle modified Euler has power NRMSE of
0.00113–0.00123 in the bypass faults. The fine-step rate-limit profile has worst
voltage NRMSE about 0.247 with only 0.00000707 pu peak absolute voltage error:
its reference voltage excursions are very small, and accumulated state
rounding is observable. These diagnostics retain all samples and event-side
duplicates, full reference ranges, individual state/output errors and the
original thresholds. Neither small absolute error nor precision effects waive
the outstanding normalized gates.

The focused group has 31 tests: 29 pass and two extended coupled-trajectory
methods fail their retained boundary assertions, with no errors or skips.
Existing WSIEG1 tests remain passing. Numerical allocation checks include both
integration methods and both active and bypassed compensation paths, with zero
bytes allocated in all four measured loops. The complete core-plugin module
executes 490 tests with no failures and four existing skips. The model
remains `LOADABLE`; extended precision/coupling, short positive times,
initialization/base boundaries, reconnection and longer-duration qualification
remain open.

## Preparation of short positive lags

The shared runtime now prepares `T1` and `T4`–`T7` as algebraic bypasses when
their entered values are at most twice the outer network interval. The
independent boundary controls include half, one and two intervals, values just
above the boundary, and four intervals. Short speed lags with a much longer
lead term also collapse to the bypass. Every measured channel matches the
corresponding explicit-bypass reference exactly for the collapsed profiles;
the above-boundary controls retain distinguishable lag responses.

Preparation leaves entered constants unchanged. The valve's rate-limited
integrator retains `T3`, including small positive values: it is not an
ordinary lag and does not inherit this bypass rule. Both production solvers
provide the outer interval through `IntegrationStepAware` before initialization.
The prepared times live in scalar/reusable array storage. Local evaluations
cannot reprepare them; a changed outer interval takes effect on reinitialization.
Direct typed use without an outer interval retains its entered positive lags.

The common parameter report identifies prepared bypasses, warns about short
servo times and rejects nonpositive primary turbine allocation before either
solver initializes. Inactive primary governors remain checked; passive
secondary shafts are explicitly reported as sharing the primary state owner.
Validation neither changes constants nor initializes controller state.

This corrects an actual stability defect. Before preparation, the staggered
half-interval lag profiles failed at the fine step, and quarter-cycle turbine
states grew catastrophically. After preparation, every fast-time profile
completes under both methods. The short-lag and explicit-bypass runs have
identical complete physical/state/history trajectories in the actual solver
path; the former executable fails that regression.

| Fast-time matrix | Worst voltage range NRMSE | Worst P range NRMSE | Worst rotor-angle RMSE, degrees |
| --- | --- | --- | --- |
| Modified Euler | 0.0006685 | 0.001230 | 0.002567 |
| Staggered Euler | 0.0000661 | 0.0003633 | 0.000939 |

Staggered boundary gates pass throughout this matrix. Modified Euler still
fails the existing 0.001 power gate in quarter-cycle profiles; voltage, Q and
rotor-angle gates pass. Fast valve-servo state discrepancies remain separate
from this boundary acceptance: worst valve-coordinate differences are about
0.00134 pu for modified Euler and 0.00103 pu for staggered Euler. These are
retained diagnostics, not a claim of complete internal-state qualification.

All reference profiles reproduce in independent cold pairs. Every startup row,
event-side duplicate and named state/output is retained, with explicit reference
clock checks, per-channel errors and times of maximum error. The twenty earlier
bypass/limiter trajectories remain identical, and the existing ordinary-method
retained-state regression still passes. Full model acceptance, short-servo
state parity, initialization/base boundaries, reconnection and longer-duration
qualification remain open.

Final validation runs 50 focused WSIEG1/adjacent-validator tests: 47 pass and
three coupled-accuracy methods retain their failures, with no errors or skips.
The complete core-plugin module runs 490 tests with no failures and four
existing skips. The registered core integration suite runs 4,029 tests with
10 failures, no errors and 13 skips, including the retained fast-time
modified-Euler power gate and other outstanding reference/system checks.
It is not a passing system-acceptance result. Private evidence retains the
exact runtime overlay, prior executable, complete comparisons and test reports.

## Nonlinear valve initialization and startup qualification

Initialization now preserves entered valve bounds. Only the documented `Iblock`
zero-bound rule replaces a bound, using the initial valve position obtained by
inverting the nonlinear valve-power curve. Mechanical power is not a substitute
for that position. The previous implementation silently expanded ordinary bounds
to contain the solved operating point; an executable negative control confirms
that it fails the new bound-preservation regression.

The valve is projected at initialization while the turbine's dynamic states
retain the solved power. This exposes the actual turbine initialization
derivatives when the initial valve cannot supply that power. Bypassed turbine
slots instead take their algebraic inputs immediately. Consequently, an initial
mechanical-power jump can coexist with zero controller derivatives; a zero-rate
check alone does not establish machine/network equilibrium. The immutable
`getInitialValveLimitMismatch()` diagnostic reports initial valve reference
minus projected position, refreshes on reinitialization and is unavailable after
failed initialization. Existing numerical state names remain unchanged.

Independent cold pairs exercise nonlinear upper/lower clipping, each `Iblock`
selection, conflicting effective limits and full turbine bypass at fine and
quarter-cycle intervals. Both production initializers reproduce the published
initial states, effective bounds, valve histories and mechanical output within
8e-8 pu, without changing entered constants. Reference runs that report suspect
initialization remain explicitly quality-rejected evidence; their coordinates
are checked, but they are not used as successful trajectory references.

The non-flat reference starts integrate two intervals before time zero. The new
initialization trajectory test explicitly selects the same start time and records
both negative-time network endpoints. It preserves every later sample and event
duplicate, with an explicit binary32 reference-clock check. This is a test
configuration, not a change to either production solver's startup default.
The earlier comparison holding startup rows at their initial values is retained
in the private evidence alongside the matched-startup result.

| Clipped, fully bypassed turbine profiles | Worst rotor-angle RMSE without startup, degrees | With two integrated startup intervals, degrees |
| --- | --- | --- |
| Modified Euler, fine interval | 0.03973 | 0.01326 |
| Modified Euler, quarter cycle | 0.3307 | 0.1118 |
| Staggered Euler, fine interval | 0.02646 | 0.0000552 |
| Staggered Euler, quarter cycle | 0.2192 | 0.003062 |

Staggered boundary gates pass for these bypassed-turbine profiles after matching
startup. Modified Euler still fails retained boundary gates; startup alignment
does not establish complete trajectory parity. The ordinary, consistent
`Iblock` profiles pass both methods. A strictly constant reference channel uses
a separate 1e-6 pu maximum-absolute-error gate and reports range NRMSE as
undefined. Every positive reference range, however small, retains the original
0.001 range-NRMSE limit; rotor-angle RMSE remains below 0.01 degrees.

At this checkpoint, conflicting effective limits remained **unqualified**. The observed initial
projection gives the upper bound priority, but a later reference command
reversal causes alternating lower/upper valve values. The earlier runtime's
projection holds the upper bound and does not reproduce that inconsistent-limit
trajectory. The cold-capture test explicitly detects this alternation, and the
full coupled gate failed under both methods. Initialization evidence
must not be generalized into a claim that this runtime branch is correct.

All 84 previously retained bypass/limiter and fast-time coupled traces remain
exactly identical across every sample, state, history and physical channel.
The model remains partially qualified; reversed effective-limit handling,
modified-Euler transient accuracy, base/reconnection boundaries and extended
duration qualification remain open.

Final initialization validation executes 90 focused model/import tests: 85 pass
and five coupled-accuracy test invocations fail, with no errors or skips. The
new component checks and both independent initializer checks pass. The complete
core-plugin module executes 486 tests with no failures and four skips. The
broader registered-suite result recorded in the preceding section predates this
slice; it is not reclassified as passing. Private evidence retains the original
and matched-startup comparisons, cold captures, exact runtime overlay, negative
control, invariance checks and test reports.

## Ordered limit projection and local reference events

The reversed-limit runtime branch is now independently checked. When `Iblock`
produces overlapping limit conditions, the upper branch has priority in the
valve derivative check. A decreasing command can leave that branch. An unchanged
trial holds its valve position; an advancing trial checks the upper bound and
then the lower bound. This differs from simultaneous minimum/maximum clipping.
Both methods now reproduce every recorded valve, backlash-input and
backlash-output sample within 8e-8 pu at both tested intervals. Consistent
effective bounds retain their previous projection and non-windup behavior,
including the zero derivative at equal bounds. Entered parameters are unchanged.

The initial limiter-only experiment exposed a separate event-history error.
At a later command, the valve derivative changes but the turbine derivatives
do not. A global discontinuity notification nonetheless averaged the turbine
rates with older integration history. The independent first-turbine recurrence
discriminates that averaging from the observed retained-history update. Changing
the limiter equations alone therefore did not complete this comparison.

`Wsieg1Governor.applyReferenceEvent(value)` now represents an accepted discrete
command through the common model-local event contract. It saves the current
left-side derivative vector in a reusable buffer and advances a revision.
The shared solver observes that revision once and averages each new derivative
with its actual left endpoint. Unchanged turbine derivatives remain unchanged;
other devices keep their histories. Several writes before the next advance
retain the first left endpoint. Observations and trial evaluations do not consume
the event; accepted completion clears it, and reinitialization resets it.
Calls during an unfinished predictor or through a replaced owner are rejected.

Continuous upstream control still uses `setRefPoint(value)` at its normal
predictor/corrector stages. That setter does not manufacture discrete events.
This distinction preserves upstream-controller compatibility and avoids
restarting numerical history on every continuously changing setpoint. The
synthetic reference-command driver now uses the explicit event method under
both integration schemes and no longer sends a global notification.

| Reversed-limit command profiles | Worst rotor-angle RMSE, degrees | Worst voltage/P/Q range NRMSE |
| --- | --- | --- |
| Modified Euler | 0.0003662 | 0.0002200 |
| Staggered Euler | 0.0000143 | 0.00011292 |

Both methods pass the unchanged 0.01-degree rotor-angle and 0.001 range-NRMSE
boundary gates for these profiles. This also removes unrelated machine-history
rebasing from the clipped, fully bypassed-turbine starts: their worst staggered
rotor-angle RMSE across the tested intervals falls from 0.003062 to 0.00003273
degrees. Modified-Euler bypass-start accuracy remains unresolved. Full internal
turbine-state qualification is separate from boundary acceptance; in particular,
the reversed-limit modified-Euler mechanical-power peak difference remains
about 0.001615 pu at quarter cycle.

The hot numerical path retains primitive arrays and shared state storage.
Warmed allocation checks, now including discrete commands, report zero bytes
allocated in all four active/bypassed and ordinary/shared update loops. The
focused model/import group runs 97 tests: 93 pass, four coupled-accuracy
invocations fail, with no errors or skips. The core-plugin module runs 486 tests
with no failures and four skips. Remaining precision, modified-Euler transient,
base/reconnection and extended-duration tasks stay open; this is not full model
or system acceptance.

An isolated previous-runtime control fails the new limiter-order check. A
separate compatibility run uses the previous command driver with the new
production kernel: all 84 retained bypass/limit and fast-time traces remain
exactly identical, including every state, history, physical channel and event
duplicate. The improved reference-command comparisons use the new explicit
local-event API; the compatibility result does not substitute for their
independent boundary checks.

The complete registered core suite executes 4,047 tests with 11 failures, no
errors and 13 skips. Comparing the exact failing test identities with the
retained earlier suite preserves all ten earlier failures and adds only the
newly registered modified-Euler non-flat initialization trajectory gate. The
suite remains failing. Private evidence retains both executable controls,
the complete comparisons, cold-reference hashes, runtime/dependency provenance
and the focused/module/registered reports.

## Solved shaft-power normalization

The secondary turbine output must match its own initialized mechanical power.
A machine-rating ratio alone is insufficient when the solved shaft loading
does not match the turbine fractions. The shared owner now prepares:

```text
turbine_initial = primary_initial_mechanical_power / sum(primary_fractions)
secondary_scale = secondary_initial_mechanical_power
                  / (turbine_initial * sum(secondary_fractions))
secondary_output = secondary_scale * sum(secondary_fraction[i] * turbine_state[i])
```

Each initial mechanical power is already on its machine's own base. Fresh
independent captures vary both machine ratings, shaft loading, turbine fractions
and network base. An independently loaded secondary requires a scale of three
where the former rating-only implementation gives two. An isolated previous
executable reproduces two; the new executable produces three and the correct
initial secondary power. A zero-loaded secondary can have zero scale.

Machines initialize sequentially, so each governor captures its own freshly
initialized mechanical power. The scale becomes available only after both
shafts have supplied their operating points, in either traversal order.
Reinitialization invalidates the previous scale. Repeated preparation retains
the latest fresh value, and shared integration rejects an incomplete pair.
The scale is not recomputed from moving turbine states. Undefined zero-primary
allocation and nonfinite normalization fail initialization rather than exposing
the previous run's scale. Independent zero-over-zero, initially inactive
companion and reconnection behavior still require qualification.

Both integration methods retain the same state arrays and passive companion.
Warmed paired-shaft update/output checks measure zero allocated bytes under
both methods. The earlier ordinary flat/fault regression trajectories remain
bit-identical, including dual-shaft states and outputs.

The independent network-base control doubles system base while preserving
physical branches and the fault impedance in ohms. Electrical P/Q channels
therefore halve; mechanical powers, controller coordinates, voltages, angles
and timestamps remain unchanged within export precision. The synthetic fault
driver now converts the same physical fault to the actual system base.

Complete comparisons include both rotors, all bus voltages and both machines'
electrical and mechanical powers. Flat reference runs still stop and restart
recording at diagnostic boundaries. The comparison retains those duplicate
samples against the same simulated state, requires their measurements to be
identical and never advances or shifts the integration clock to absorb them.
For flat runs, the report retains the actual reference range and NRMSE, while
acceptance uses a 1e-6 pu maximum absolute error to avoid dividing by incidental
flat-run rounding noise. Fault gates retain 0.001 range NRMSE and 0.01-degree
rotor RMSE without range floors or channel offsets.

| New shaft/base controls | Worst fault voltage/P/Q NRMSE | Worst rotor RMSE, degrees |
| --- | --- | --- |
| Modified Euler | 0.00178160 | 0.00217975 |
| Staggered Euler | 0.00053308 | 0.00184393 |

Staggered fault boundaries pass at both tested intervals. Modified Euler passes
the fine-step fault controls but fails several quarter-cycle active-power gates.
All flat voltage errors remain below 1.10e-7 pu. Flat reactive-power gates still
fail: the largest doubled-base difference is 2.425e-5 pu, already present at
initialization. An isolated tighter-load-flow control removes that discrepancy;
it leaves a separate 2.321e-6 pu reactive-power difference in the higher-rated
secondary profile. Neither diagnostic changes production defaults or the
registered acceptance limits. Initial-power and state agreement does not imply
full trajectory qualification.

The focused group runs 108 tests with six failed accuracy invocations, no
errors and no skips. The five shaft-initialization/lifecycle/allocation tests
pass, as do independent cold-repeat, initial-coordinate and physical-base
controls. The core-plugin module runs 486 tests with no failures and four
skips. These results extend the model's coverage; unresolved precision,
modified-Euler transient, internal-state and lifecycle gates remain explicit.

The complete registered suite executes 4,058 tests with 13 failures, no errors
and 13 skips. Its failing test identities preserve all eleven prior failures
and add only the two new shaft/base trajectory invocations described above.
The private validation capsule retains the cold captures, complete trajectories,
previous/current executable discriminator, tighter-flow control, allocation
checks and focused/module/registered reports. The suite is still failing;
these new controls do not establish full model acceptance.
