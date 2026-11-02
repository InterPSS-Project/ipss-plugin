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

## Ten-second production-step qualification

`Pss2bLongRunConformanceTest` extends the synthetic coupled SMIB matrix to ten
seconds at network intervals of 1/240 and 1/480 second. It exercises speed,
voltage derivative, remote frequency and active voltage/output limits under
both integration methods. Cold reference repeats are byte-identical. Every
sample, startup row, event-side duplicate and final reference overshoot is
retained; actual and reference timestamps remain separate. Alignment follows
integration ordinals, with a timestamp bound smaller than half an interval.
Machine and exciter states are recorded alongside all stabilizer coordinates
to localize upstream discrepancies.

The first extended comparison exposed a host-exciter integration difference.
At fault application, the reference transducer starts from equilibrium and
holds the new terminal voltage during its local update. The staggered
two-history recurrence with ten local steps reproduces its first accepted
state within 0.00000008 pu in every retained long profile. One local step
differs by more than 0.002 pu. This discriminator uses the recorded voltage
and executed transducer time constant; it does not fit a coefficient or shift
a sample. The test explicitly configures ten ST1-family exciter steps for
staggered comparisons. Production defaults remain one step. PSS2B still
advances once per network interval through its shared numerical storage.

For the quarter-cycle derivative profile, matching this host setting reduces
staggered voltage NRMSE from 0.006032 to 0.0000347 and rotor-angle RMSE from
0.150281 to 0.000985 degrees. The retained first-postfault power-path state
gate also passes. Worst boundary errors across the extended profiles are:

| Integration | Network interval | Voltage range NRMSE | Rotor-angle RMSE (degrees) |
|---|---:|---:|---:|
| Modified Euler | 1/240 s | 0.000691 | 0.017025 |
| Modified Euler | 1/480 s | 0.000266 | 0.006506 |
| Staggered, ten exciter steps | 1/240 s | 0.0000347 | 0.000986 |
| Staggered, ten exciter steps | 1/480 s | 0.0000147 | 0.000390 |

All staggered long boundary profiles and all half-interval modified-Euler
profiles pass the unchanged voltage NRMSE <0.001 and rotor-angle RMSE <0.01
degree gates. The quarter-cycle modified-Euler voltage-derivative profile
**fails the rotor-angle gate**. Its registered test deliberately retains that
failure; this is a qualification checkpoint, not an all-green milestone.
The speed, remote-frequency and voltage-limit quarter-cycle profiles pass.

Independent reported states reconstruct every long-run stabilizer output
within the existing 0.000015 pu equation gate. Recorded-boundary modified-Euler
replay also reduces every state/output maximum error when the interval is
halved. These checks separate output equations and step convergence from
coupled trajectory acceptance. Full controller-state and sampled-output
parity remain open: peak output errors in the quarter-cycle coupled profiles
reach 0.126022 pu with modified Euler and 0.002604 pu with staggered integration.
Neither limit was relaxed, and no controller equation, parameter, production
integration setting or optimization default changed for this qualification.

The immutable references are retained at
`testData/private/verification-artifacts/pss2b-long-20261102/captures.zip`
with SHA-256
`1fc652f56497f1d6620a47fda67481c204bab4298210164704083285ec653475`.
Their manifests preserve runnable probes and exact source provenance. The
comparison writes diagnostics before checking acceptance so a failed gate
cannot erase its full trace or error report. Public files contain synthetic
model work only, without source-case inventories.

Initial long-run validation executes 69 tests: 68 pass, and the quarter-cycle
modified-Euler rotor-angle gate above fails. Import, compiled/generic
equivalence, shared-state ownership/allocation, prior coupled boundaries,
host-exciter, measurement and artifact checks pass. This run does not claim
full-repository or system acceptance.

## Localizing the modified-Euler controller-chain error

`Pss2bCoupledPlaybackTest` replaces selected output boundaries with unmodified
reference samples in a synthetic network. The original machine and network
still run through modified Euler; the exciter also runs unless its field
output is prescribed. These are diagnostic interventions, not complete-model
acceptance tests. The ordinary coupled PSS2B gate remains separate and enabled.

The playback clock preserves every integration ordinal and both event-side
samples. It rejects missing ordinals or distinct event-side source outputs
that this fixture cannot represent. Predictor/corrector counts and exact
published source outputs are checked. Negative-time rows explicitly use the
initialized snapshot, and full diagnostic traces retain both clocks.

| Controller-chain experiment | Angle RMSE at 1/240 s (degrees) | Angle RMSE at 1/480 s (degrees) |
|---|---:|---:|
| Ordinary coupled model | 0.017025 | 0.006506 |
| Prescribed stabilizer, endpoint updates | 0.010592 | 0.005162 |
| Prescribed stabilizer, held through both stages | 0.002017 | 0.000914 |
| Prescribed stabilizer and field voltage | 0.000680 | 0.000174 |

The field-voltage intervention passes a separate 0.001-degree downstream
machine/network RMSE guard. It localizes the larger error to the controller
chain. It does not prove an exciter equation defect, and differences between
rows cannot be subtracted as independent error contributions because the
interventions change feedback.

Two isolated implementation candidates were rejected. Using the predictor
stabilizer value during exciter correction increased the quarter-cycle angle
RMSE to 0.019217 degrees. Holding the live stabilizer input throughout both
exciter stages increased it to 0.129624 degrees (0.065769 at the half interval),
despite the improvement with prescribed stabilizer samples. This demonstrates
why playback alone cannot authorize a controller-coupling change. Neither
candidate changed production source, installed dependencies or normal runtime
defaults. Reconcile the complete state and sampled-input lifecycle before
changing that coupling; the strict quarter-cycle gate is still unresolved.

The subsequent focused run includes the registered playback checks: 74 of 75
tests pass, with only the same ordinary quarter-cycle modified-Euler gate
failing. Production equations and runtime settings are unchanged. Preserve the
unmodified coupled traces alongside the intervention evidence when evaluating
the next controller-stage change.

## Input-transducer preparation

Independent synthetic SMIB controls distinguish the standard record's input-lag
preparation from the existing typed extension. At outer intervals of 0.5 ms and
0.25 ms, each input transducer with a time constant equal to twice that interval
produces exactly the same complete reference trace as a zero-time-constant
transducer. A time constant of 2.1 intervals produces a clearly active lag.
Both input paths and independent cold repeats are checked. Earlier exploratory
controls also exercise speed input and values below the boundary.

The standard importer now selects this preparation rule for T6 and T7. The
comparison uses the source coefficient precision; the runtime retains the
entered constant whenever the transducer remains active. Preparation occurs
once at initialization, using the outer interval. It adds no per-stage work,
and both integration methods use the same prepared block/state storage.
Reinitialization with a smaller interval recomputes the effective constants
from the unchanged data. Typed extensions retain their existing half-step
preparation rule. This evidence does not authorize applying the input-lag rule
to washouts, lead-lags or the ramp filter.

`Pss2bInputLagPreparationTest` retains complete event-side trajectories and
checks the imported policy, unchanged raw data, reinitialization, typed
compatibility, cold-repeat identity and both-method SMIB boundary gates. The
largest short-lag rotor-angle RMSE is approximately 0.00255 degrees and the
largest voltage range-normalized RMSE is below 0.000287. Controller-state and
sampled-output errors remain diagnostic: fast transitions still show output
differences above 0.1 pu. Passing the network boundary gates does not establish
complete internal-state qualification or resolve the earlier long-run
modified-Euler failure.

Full independent captures are pinned by SHA-256
`db881e29663911b21a2b0b569f6be3734d831069aa4f3efde05d10694e413eee`
in the private evidence archive. The runnable capture helper accepts an
explicit input-lag override; it continues to reject changed or incorrectly
loaded constants rather than silently adjusting the reference inputs.

## Sampled bypass-path output

The input-lag captures now provide an output-stage discriminator. Reconstructing
the output with current differential states and current bypass-path values fails
when the second transducer is bypassed. Substituting only the preceding bus
voltage is also insufficient because the washout states have advanced. Using
the preceding published bypass-path coordinate with the current differential
states reconstructs every retained profile within the existing 0.000015 pu
output gate. In the affected profiles, the largest reconstruction error is
below 0.000006 pu, compared with an approximately 0.106 pu discrepancy using
the current path value.

At a duplicate event timestamp, bypass coordinates can refresh while the
published stabilizer output remains the preceding accepted output. The
reconstruction checks both observations explicitly. It preserves every row and
timestamp; it does not shift the simulated trajectory. Negative controls require
the current-path reconstruction to fail the output gate in the profiles that
distinguish these policies. The standalone reconstruction also prepares the
model using the captured outer interval before injecting its recorded states.

This is a diagnostic use of recorded internal coordinates, not a replacement
for physical inputs during simulation. Production integration and output
equations remain unchanged. An isolated candidate that cached bypass values
during ordinary evaluations did not improve the coupled errors. In particular,
the staggered solver evaluates derivatives again after state and network updates;
a cache overwritten by those evaluations cannot represent accepted history.
Do not promote that candidate. A runtime change needs an explicit accepted-step
sampling contract, including zero-duration events, trial/rollback behavior and
both integration methods, followed by complete coupled qualification.
