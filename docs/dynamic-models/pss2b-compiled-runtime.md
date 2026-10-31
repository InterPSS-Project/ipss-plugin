# PSS2B compiled runtime and qualification

The standard PSS2B graph now has typed Java wiring in the default modified-Euler
solver. It reuses the existing numerical block kernels and their state storage.
This removes expression traversal from stage evaluation without introducing a
second set of controller equations. The same existing block storage now also
supplies direct shared-state staggered integration.

## Execution contract

`Pss2bCompiledStage` binds the two input conditioners, ramp filter, lead-lag
cascade and output limiter during preparation. Primitive arrays retain the stage
inputs, states and rates. Initialization order, repeated algebraic preparation,
predictor/corrector boundaries and observation semantics follow generic CML
execution. Input values, reference points and scalar mixing gains remain live.

Automatic preparation accepts only audited standard owners and graphs. Custom
blocks, changed expressions, aliased blocks, debug wrappers and observed
controllers retain compatible execution. Structural edits require runtime-plan
invalidation and recompilation. The configured local or remote signal buses
retain frequency history, including when a live selector changes to frequency.
Changing a signal bus or activating a derivative-input history requires
reinitialization.

## Accepted voltage measurement

The derivative selector uses sampled voltage memory, held throughout both
integration stages. At an accepted endpoint:

```text
d_next = 0.8*d_previous + 0.2*(V_accepted - V_memory)/h
V_memory = V_accepted
```

An algebraic event rebases voltage memory without advancing the derivative.
`AcceptedVoltageMeasurement` supplies the endpoint callback shared with the
existing sampled stabilizer measurement. Both input histories have named-state
entries in addition to the existing CML states. Monitoring returns immutable
snapshots and does not update controller history.

The standard record, legacy typed record and typed extension retain separate
field layouts. Malformed cardinalities, non-finite parameters and improper ramp
orders are rejected. The standard record additionally respects its published
ramp-state allocation. Existing integration-step corrections act on prepared
coefficients and preserve imported data.

## Shared staggered integration

`Ieee1992PSS2BStabilizer` directly implements `StaggeredEulerParticipant`.
Preparation binds the two input paths, each physical ramp-filter section, the
lead-lag cascade and the optional output-filter coordinate. The compound ramp
block's unused outer coordinate is excluded. Bypassed coordinates retain their
existing zero-rate behavior. Modified Euler and staggered Euler read and write
the same block states; the enclosing staggered solver owns its reusable trial,
rate and two-step history arrays.

Pure rate evaluation samples the configured inputs and resolves the acyclic
algebraic graph once in signal order. Existing block kernels provide the
first-order equations. The optional washout and output-filter blocks expose
their existing derivative functions without replacing predictor history.
Trial writes refresh algebraic feedthrough and the static output limiter;
they do not advance sampled voltage memory. Output observation refreshes that
algebraic graph from held inputs and live mixing/setpoints. A measurement commit
cannot leave the preceding output cached for another interval. The regression
exercises actual feedthrough and preserves every block's predictor slopes.
Ordinary modified-Euler calls retain their existing
compiled or compatible execution and observation behavior.

Only the audited standard graph can use this shared path. Changed owners,
observers, expressions or custom blocks require coherent reinitialization;
unknown custom graphs retain their compatible modified-Euler path. Changing a
signal bus or changing a selector into or out of sampled voltage differentiation
also requires reinitialization. Other live input selectors retain both configured
buses' frequency history. Shared access rejects unfinished ordinary or prepared
modified-Euler predictors and active runtime phases. Numerical reads and output
observations do not commit a timer or measurement history.

Both integration methods dispatch `AcceptedVoltageMeasurement` at settled
network boundaries. Algebraic events rebase voltage memory; accepted endpoints
advance its derivative once. The shared contract permits a per-coordinate
event policy while retaining older participants' whole-vector selection.
PSS2B's two input conditioners select their histories independently: a sampled
derivative path retains its two-step history, and it cannot suppress the power
path's event averaging. Independent first-washout recurrences distinguish both
rules at fault application and clearing with two step sizes. The coupled test
also gates the first power-path state after fault application. Broader mixed
state/output qualification remains open. External reference changes use the solver's explicit
`notifyDiscontinuity()` boundary.

`Pss2bSharedStateTest` checks pure rates against the existing block equations,
external predictor/corrector integration against the existing modified-Euler
implementation, immutable measurement observations, duplicate endpoints,
transactional invalid trials and reinitialization guards. Its warmed shared
read/evaluate/apply/output loop measures zero allocated bytes across 100,000
groups. This is a component allocation result, not a full-simulation speedup.

## Evidence and remaining work

`Pss2bCompiledStageTest` checks bitwise generic/typed block states, derivatives,
inputs and outputs through bypasses, filter orders, live mixing gains and
repeated observations. `ModifiedEulerControllerPreparationTest` exercises
automatic typed dispatch through a coupled SMIB fault, including both derivative
input slots and activation changes. `OptionalBusFrequencyIntegrationTest`
compares all-bus and required-bus measurement modes through local/remote input
changes. Import checks reject invalid records before controller attachment.

`tools/pss2b_reference_probe.py` captures synthetic SMIB selector, remote-input,
limit, bypass and event-memory discriminators. Raw samples include startup and
event-side duplicates. Exact source/runtime provenance belongs in the retained
private evidence manifests. These reference captures contain no source-case
inventory information.

The recorded-boundary replay checks the sampled derivative memory and the
algebraic output reconstructed from independently reported states. Across the
standard input selectors, the reconstruction agrees within 1.5e-5 pu. The
secondary transducer's reported state precedes its gain, whereas CML stores the
gained state; this conversion is applied only at observation.

Equation reconstruction uses the exact executed coefficients recorded in the
reference manifest, including its single-precision rounding. This matters when
large lead-lag feedthrough terms cancel. Production import continues to preserve
the entered values; neither gains nor parameters are fitted to trajectories.
The complete captures and their dependencies are retained in a hash-pinned
private archive; tests stage only the input and sample files they need.

Complete transient state parity remains open. The first-washout reference
exports follow a two-history predictor recurrence; the default modified-Euler
path has a different state trajectory at finite step size. Halving the replay
step reduces the discrepancy, but this is not an acceptance test for every
trajectory. Multi-section ramp coordinates use the same per-filter scaling;
their replay also converges when the step is halved.

In fully bypassed profiles, the reference publishes the preceding controller
stage's output while allocated algebraic coordinates refresh at the current
boundary. The tests check this sampled-output recurrence and the fresh algebraic
equation separately, preserving every event-side duplicate. This observation
does not establish exact controller-state or sampled-output lifecycle parity. No whole-model
`VERIFIED` or measured speedup claim is made by this change.

## Coupled boundary acceptance

`Pss2bCoupledBoundaryConformanceTest` runs the complete retained selector,
local/remote, limit, bypass, reference-step and ramp-order matrix through the
default modified-Euler solver and the shared staggered solver. It asserts
automatic typed-stage or shared-participant coverage,
voltage range-normalized RMSE below 0.001 at both buses, rotor-angle RMSE below
0.01 degree and prefault maximum angle error below 0.002 degree. Constant-voltage
reference channels use a separate 1e-12 absolute RMSE check because their range
is zero. Limiter profiles must reach both output bounds.

The synthetic reference probes use constant-P/constant-Q loads, verified from
their retained runnable scripts, and a 0.04-second bus-frequency filter from
the reference solution settings. These settings differ from the engine defaults
and are applied to the comparison fixture only. They do not change other study
load conversions or the engine's default frequency filter.

Across the half-second SMIB comparisons at 0.5/0.25 ms steps, both fixtures
explicitly use the same 0.000001 pu network voltage-correction tolerance;
algorithm defaults are unchanged. The comparison has worst voltage NRMSE below
0.000738 with modified Euler and 0.000979 with staggered Euler. Worst absolute
voltage errors are below 0.000192 and 0.000025 pu, respectively; worst rotor-angle
RMSE is below 0.00167 and 0.000556 degrees. The staggered first-postfault
power-input state differs by less than 0.000000030 pu in its targeted gate.
Maximum staggered output error across the matrix is below 0.001245 pu and remains
an explicit diagnostic rather than complete published-output acceptance.
These are boundary-acceptance results,
not production-quarter-cycle or long-duration qualification. All controller states, measurement histories,
outputs and network boundary channels are retained in bounded lossless traces
with per-channel errors and their times. Controller-state and output errors
remain explicitly diagnostic while their stage conventions are reconciled.

Every raw reference sample is compared. Both interval-side duplicates are
retained, including segments without a physical event. Negative-time startup
samples are compared to the initialized snapshot and carry an explicit
`STARTUP_HELD_INITIAL` tag and actual time zero; the test does not invent a
negative-time simulation history. The new parser, typed-stage, replay and coupled
boundary checks are registered in `CorePluginTestSuite`.

Focused import, compiled-stage, coupled SMIB, measurement, replay and artifact
checks pass. The registered core suite retains its previously recorded failures
in endpoint monitoring, converter conformance and disturbance smokes; it is not
an all-green system-acceptance result.

The [public model description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS2B.htm)
provides an additional schema and parameter-correction reference; it does not
replace exact-version independent captures.
