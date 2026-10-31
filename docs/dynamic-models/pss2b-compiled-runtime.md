# PSS2B compiled runtime and qualification

The standard PSS2B graph now has typed Java wiring in the default modified-Euler
solver. It reuses the existing numerical block kernels and their state storage.
This removes expression traversal from stage evaluation without introducing a
second set of controller equations.

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
default modified-Euler solver. It asserts automatic typed-stage coverage,
voltage range-normalized RMSE below 0.001 at both buses, rotor-angle RMSE below
0.01 degree and prefault maximum angle error below 0.002 degree. Constant-voltage
reference channels use a separate 1e-12 absolute RMSE check because their range
is zero. Limiter profiles must reach both output bounds.

The synthetic reference probes use constant-P/constant-Q loads, verified from
their retained runnable scripts, and a 0.04-second bus-frequency filter from
the reference solution settings. These settings differ from the engine defaults
and are applied to the comparison fixture only. They do not change other study
load conversions or the engine's default frequency filter.

Across the half-second SMIB comparisons at 0.5/0.25 ms steps, the worst voltage
NRMSE is 7.38e-4 at the generator bus and 7.05e-4 at the other bus. Worst absolute
voltage errors are 0.000192/0.000066 pu; the largest rotor-angle error is 0.003381
degree. These are boundary-acceptance results, not production-quarter-cycle or
long-duration qualification. All controller states, measurement histories,
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
