# CDC6T implementation contract

The source record starts with a quoted DC-link name, followed by the model name
and 32 constants. It has no generator identifier or bus-number prefix. The
record reader now preserves that name and the constant order, including names
containing commas, escaped quotes and slashes. A quoted numeric name remains a
link name. Adjacent machine records are retained.

Automatic import now accepts this exact named record, validates its unique
ordinary LCC owner and retains the immutable source constants before load flow.
The default dynamic solver prepares and attaches the existing terminal runtime
from the solved operating point before device initialization. Missing, ambiguous,
duplicate, inactive or unsupported equipment is rejected. A disabled model does
not create active terminal ports. Static DC power is not converted into another
dynamic load, and only the rectifier port owns the three integration states.

Private pre-initialization network copies preserve the source constants and
captured static owner/terminal identities. The generic branch-copy builder's
generated name is restored from that explicit captured identity during
preparation; arbitrary renamed or missing owners are not inferred. Running-state
checkpoint copies remain unsupported and fail explicitly.

Strict import acceptance establishes record attachment, not complete model
acceptance. The existing typed runtime, protection policies, equations and
trajectory tolerances are unchanged. Full independent trajectory qualification
remains open, including the retained finite-resistance modified-Euler voltage
gate described below. Imported and explicitly prepared flat/fault runs are
compared sample-by-sample under both integration methods.

The following sections retain successive implementation checkpoints. Statements
about automatic attachment being disabled describe those earlier checkpoints;
the current import path is the one described above.

## Shared-runtime work

- Resolve the exact named two-terminal DC link and its rectifier/inverter
  terminals. Reject missing or ambiguous ownership before initialization.
- Implement the measured inverter voltage/current and rectifier voltage as
  three differential states using shared equations and reusable storage for
  modified-Euler and staggered integration.
- Keep blocking, bypass, mode-switch timers and recovery history separate from
  trial state evaluation. Preserve the auxiliary power-command interface.
- Implement current-demand limits, voltage/current recovery ramps and converter
  electrical coupling with explicit physical units and network-base conversion.
- Initialize from the solved DC operating point. Publish named states,
  derivatives, timer histories and limiter status without advancing history.
- Qualify ordinary operation, blocking/unblocking, bypass/unbypass, mode changes
  and recovery against independent small-network captures, retaining every
  event-side sample. A static DC load-flow result is not dynamic verification.

The exact source-library identity is retained in private evidence. The source
data sheet establishes layout and state roles; detailed controller equations and
event sequencing still require authoritative evidence and independent probes.

## Retained independent boundary evidence

`tools/cdc6t_reference_probe.py` now produces cold flat and rectifier-fault
captures on the existing small LCC network fixture. The staged RAW marks its
disconnected bus out of service; the original RAW is retained alongside it.
The fixture uses fixed classical machine sources to isolate the DC boundary.
This is a preliminary small-network discriminator, not final SMIB qualification.

Both cold pairs match exactly. The reference confirms three differential states,
18 diagnostic variables and three internal control flags. The flat state values
are 500,000 V, 1,000 A and 505,000 V. The all-sample contract verifies the
five-ohm DC voltage drop, rectifier/inverter power signs and 100 MVA power base,
including startup and duplicate segment/event samples. Flat states are constant;
the fault produces nonzero transducer and terminal-power response.

These tests validate the reference contract only. Complete CDC6T import remains
unsupported. Qualify the assembled controller, state/history integration and
network coupling before changing that status.

## Measurement component

`Cdc6tMeasurements` implements the three first-order voltage/current measurement
equations with shared primitive state, input and derivative buffers. It provides
explicit modified-Euler predictor/corrector stages and the common staggered
participant interface. Initialization sets measurements from the solved boundary;
reading diagnostic maps does not advance history. Predictors can be cancelled,
zero-duration local steps do not advance state, and nonfinite updates fail before
changing stored coordinates. Mixed local/staggered trial ownership is rejected.

Analytic exponential tests verify second-order convergence for local modified
Euler and accepted two-step rates. The typed conducting runtime below owns this
component; the measurements do not themselves implement blocking, bypass,
recovery or command limits. The original differential-only boundary admitted positive times. The prepared
short-transducer path described below now covers zero and sufficiently short
times without changing the raw source constants.

Recorded-input replay also remains diagnostic: endpoint-sampled voltage inputs
alone leave roughly 48 V of peak measured-voltage discrepancy around the fault.
Resolve input sampling and event-stage sequencing in the coupled implementation
before claiming state-trajectory parity. Do not tune time constants to this error.

## Normal current order

`Cdc6tCurrentOrder` implements normal-operation command conversion with prepared
mode/setpoint data and no allocation during evaluation. Negative power setpoints
select measured inverter voltage; positive power setpoints select measured
rectifier voltage. Current mode retains its ampere setpoint. Voltage compensation
subtracts the limited-current resistive drop from the scheduled voltage.

Independent cold-paired fault captures now distinguish all three modes. Every
current sample matches the selected measurement equation within `3.4e-5` A;
substituting the other voltage exceeds 10 A error in the power-control profiles.
The component remains upstream of voltage-dependent current limitation and
blocking/recovery logic. The normal current ceiling is now implemented separately;
protection and recovery remain unfinished.

Auxiliary MW conversion and nonzero compensation have equation tests only.
Direct writes to the auxiliary diagnostic VAR were reset by the reference
runtime, so those attempted modulation profiles provide no response evidence.
The probe now rejects a requested modulation unless the recorded input actually
changes. Independent modulation qualification needs the proper auxiliary-signal
model/interface; do not treat a successful API call as proof of an applied input.

## Voltage-dependent current ceiling

`Cdc6tVoltageCurrentLimit` prepares the slopes of an ordered three-point curve
once. Evaluation takes measured inverter volts and returns an ampere ceiling,
with constant extrapolation below/above the first/last breakpoint. The normal
current order passes through a minimum selector. No per-evaluation arrays or
temporary objects are created.

Cold-repeated flat, equal-slope fault and unequal-slope fault captures qualify
the normal-operation equation using every recorded sample. Both sloped segments
are active in the fault controls; the unequal-slope control prevents a single-line
implementation from passing accidentally. Peak current error is below `3.9e-5` A.
Breakpoint, plateau and smaller-order behavior have separate analytic checks.

The ordinary constructor requires strictly increasing voltage coordinates and
nondecreasing nonnegative current coordinates. Unordered points are never sorted.
The source factory separately supports the independently checked redundant final
plateau described below. Other unordered or ambiguous curves remain unqualified.
Recovery and blocking/bypass overrides are supplied by the electrical runtime;
they are not intrinsic properties of the ceiling. Complete CDC6T import and
protected coupled trajectories remain unfinished.

## Converter terminal boundary

`Cdc6tConverterBoundary` prepares transformer/bridge coefficients and evaluates
the non-capacitor-commutated electrical equations into caller-owned primitive
buffers. It takes terminal AC magnitude, positive DC voltage and current; it
returns signed terminal active power, reactive consumption and the firing or
extinction angle. Evaluation does not change taps, controller modes or histories.
Infeasible voltage/current combinations fail before modifying the output buffer.

All samples of the retained control-mode and current-limiter captures verify both
terminal powers and angles. Peak active-power error is below `1.2e-6` pu, reactive
power below `1e-5` pu and angle below `0.0003` degrees. DC resistive loss is checked
separately. These are measured-input electrical checks, not a coupled simulation.

The overlap expression cancels the vanishing power-factor cosine analytically
instead of multiplying zero active power by an unbounded tangent. Analytic tests
cover zero current and continuity of reactive consumption at zero DC voltage.
This does not establish bypass-event behavior. Converter active-set selection,
protection/recovery and network injection ownership remain to be integrated and
qualified before the full model can be attached.

## Conducting-link algebra

`Cdc6tConductingLink` combines the prepared terminal equations with line loss
and minimum firing/extinction angles. For a supplied current order, the inverter
voltage is the minimum of its voltage order, its own angle-limited voltage, and
the rectifier angle-limited voltage minus the resistive line drop. Both terminal
powers, angles and DC voltages are evaluated into reusable storage and published
only after the complete trial succeeds. No taps, modes, timers or accepted
integration history change during evaluation.

Independent captures exercise rectifier-side and inverter-side faults, both
power-order conventions, current control and active voltage-dependent current
limits. Tests derive the current order from recorded measurement states using
the Java command/limiter components; reference terminal current and DC voltage
are not inputs to the calculation. All startup and event-side samples remain.
Observed maximum errors are below 0.207 V DC, 2.64e-6 pu active power,
8.08e-6 pu reactive power and 0.000062 degrees. Both minimum-angle branches
are explicitly exercised, with separate resistive-loss and failed-trial checks.

The focused CDC6T gate passes 29 tests without skips. This establishes
measured-state conducting algebra, not full trajectory acceptance. Current-margin
transfer, protection/recovery, network ownership and coupled integration under
both methods still require implementation and independent qualification.

## Blocking evidence and recovery factors

Cold-paired captures now exercise instantaneous and delayed rectifier blocking.
Both initialize normally. During the fault, blocking raises terminal AC voltage
enough to permit unblocking; renewed conduction lowers it and can block again.
The event implementation must preserve this feedback rather than assuming one
block followed by one recovery. A preliminary capture whose threshold blocked
the initial operating point failed the quality gate and is not acceptance evidence.

`Cdc6tRecoveryRamp` prepares voltage/current restart fractions and separate
per-unit recovery rates. Evaluation takes elapsed time from an externally owned,
accepted restart, clamps each factor to unity and mutates no history. Both
integration methods can evaluate the same equations repeatedly without advancing
the ramp. Voltage-dependent current limitation still applies downstream.

Reference tests use recorded restart events, retain every sample, and distinguish
blocked, normal and recovering intervals. Recovery-factor discrepancies remain
below `5e-7`. Unequal rates and nonmonotonic trial calls have analytic checks only;
independent captures currently exercise equal rates and restart fractions.
These checks do not qualify Java protection timers or event ownership: those,
bypass recovery, mode switching and coupled trajectories remain unfinished.

## Rectifier protection history

`Cdc6tRectifierProtection` separates read-only trial evaluation from accepted
event observations. It implements strict instantaneous/delayed undervoltage
pickup, interruption reset, strict recovery pickup and the minimum blocking
duration. Reusable primitive buffers carry the blocked flag, pickup deadline,
minimum-block deadline, recovery deadline and last restart time. Failed inputs
cannot publish partial history. A new electrical observation at the same time
may reblock after an unblock; a repeated integration trial cannot commit history.

Analytic checks exercise interrupted pickup/recovery, equality boundaries,
zero delays, rejected trials and repeated blocking. Recorded terminal-voltage
replay reproduces the first delayed pickup within one output interval. This
does not qualify complete protection trajectories: instantaneous blocking can
occur at intermediate network voltages absent from exported boundary samples.
The final runtime must check protection against those electrical observations
and resolve any resulting discontinuity before recording the accepted output.

The typed component constructor requires explicit nonnegative delays. Source
preparation now handles the qualified legacy recovery-delay sentinel described
below. Inverter-origin blocking, bypass and transactional ownership have their
separate gates below; concurrent protection causes and complete protected
trajectory acceptance remain open before automatic import can use the full model.

## Inverter-origin blocking

`Cdc6tInverterProtection` uses a latched communication request. Independent
short-fault captures demonstrate that voltage recovery before the communication
deadline does not cancel the pending block. Reusing the resettable rectifier
undervoltage pickup would therefore give incorrect behavior. Once blocked,
unblocking requires sustained voltage above the recovery threshold and expiration
of the minimum blocking interval. Preview and accepted-history ownership follow
the same primitive-buffer contract as rectifier protection.

Cold-paired short and long inverter-fault captures distinguish communication,
minimum blocking duration and recovery delays. Endpoint-voltage replay checks the
first block and first unblock within one recorded interval. Analytic checks cover
latched requests, interrupted recovery, equality boundaries and rejected trials.
All capture rows remain in the tests, but later full event trajectories are not
qualified: intermediate network observations can trigger further requests absent
from exported samples. Concurrent rectifier/inverter causes require coordinated
ownership before these components can drive a runnable DC-link device.

## Bypass electrical boundary and first protection cycle

`Cdc6tConductingLink.evaluateBypassed` represents a closed inverter bypass:
inverter active/reactive power and DC voltage are zero, while the rectifier
supplies the line's resistive loss at the supplied bypass current. Its reactive
consumption and firing angle follow the same prepared bridge equations used
for normal conduction. The reported inverter angle is 90 degrees. Infeasible
rectifier current fails before modifying caller output.

Cold-paired DC-voltage and delayed AC-voltage bypass captures independently
verify this boundary. Bypass maintains scheduled current in these captures;
ordinary power divided by the decaying voltage measurement would be incorrect.
Every recorded bypass sample is checked against the Java electrical kernel.
The measured rectifier reactive-power difference is below `2.5e-5` pu; this is
a separate bypass bound and does not replace the tighter normal-conduction gate.
`Cdc6tBypassProtection` separately provides trial/accepted timer history for
strict DC pickup, sustained AC pickup, minimum bypass duration and sustained
AC recovery. Analytic reset/atomicity checks and first-cycle reference timing
checks qualify these components, not complete coupled operation.

The bypass component now retains an explicit recovering mode after unbypass.
While the voltage recovery factor is below unity, DC-voltage pickup is inhibited;
delayed AC-voltage pickup remains active. Completing the voltage ramp restores
normal DC pickup. The caller supplies the recovery factor from the shared ramp,
without committing history during trial evaluation.

Three cold-paired second-fault discriminators establish this distinction:
a severe fault during recovery does not trigger DC bypass even below a scaled
pickup threshold; sustained AC undervoltage during recovery does trigger bypass;
and a fault after recovery restores DC pickup. Recorded internal mode snapshots
independently identify normal, bypassed and recovering operation. Tests retain
every sample, compare bypass status throughout, and explicitly confine endpoint
replay discrepancies to the recorded transition neighborhoods. This is not
exact event-stage parity or full coupled trajectory qualification. Combine these
rules with concurrent blocking, current-order ownership and internal network
observations before attaching the model.

## Low-voltage mode switching

`Cdc6tModeSwitch` transfers power control to scheduled current when actual
inverter DC voltage falls below the configured threshold. A deadline measured
from the initial transfer enforces the minimum switched duration. Return to
power control requires voltage strictly above the threshold and expiration of
that deadline; it does not require an additional sustained-recovery timer.
Preview uses caller storage, and only accepted observations change history.

Cold-paired long and short faults distinguish those timing rules and show that
switched operation holds the original scheduled current. Recorded-input tests
compare mode and current order across every sample, with differences permitted
only in explicit event neighborhoods. Separate checks cover strict equality,
rejected trials and inhibition in current control or while blocked/bypassed.
These are component checks; concurrent protection transitions, auxiliary input
conversion in switched mode and coupled integration remain unqualified.

## Assembled conducting runtime

`Cdc6tNormalRuntime` combines the shared measurement states, current command,
voltage-dependent ceiling and conducting electrical boundary. Each network
trial refreshes voltage-dependent terminal currents and transducer inputs using
primitive reusable buffers. Rectifier and inverter power signs are converted
to network-base current injections using their own complex terminal voltages.
No measurement state advances during current evaluation.

The runtime delegates modified-Euler predictor/corrector and staggered state/
derivative operations to one measurement owner. State updates invalidate the
electrical boundary; subsequent integration must refresh it. Predictor rollback
restores the same state owner. Tests check equilibrium under both lifecycles,
disturbed-state refinement, rotating-phasor power conservation, fresh voltage
inputs and failed/stale trial behavior.

The original API and paired ports below select ordinary conduction. The explicit
regime overload described later also evaluates protected electrical boundaries
through the same transducers and buffers. It does not own protection events.
Coordinated event behavior and complete automatic attachment remain outstanding.

## Network ownership established before attachment

The current engine's `BaseAclfBusImpl.powerIntoNet` uses
`externalPowerIntoNet` for the static outer-loop contribution, including DC
terminal power. `BaseDStabBusImpl.calNetLoadResults` and `processInitLoad`
convert ordinary load separately; they do not automatically convert that
external power into a dynamic load. The dynamic current path likewise does not
inject the external-power field. A registered non-generator bus-device port
adds its current explicitly.

`Cdc6tNetworkOwnershipTest` checks these engine behaviors with both signs of
external power, retained ordinary load, explicit current-port addition and
deactivation. Therefore attachment must add each physical terminal injection
once; subtracting the static external power from ordinary load would create
an incorrect compensating contribution. This is an engine ownership check,
not proof that the full named-link importer and dynamic solver are attached.

The engine's script-device classification requires its script-specific interface;
it is not a generic numerical-current port. The tested registration uses the
non-generator `DYNAMIC_LOAD` device classification, as existing numerical shunt
devices do, without creating an ordinary static load. Staggered support must
recognize the actual CDC6T port class and its single shared state owner explicitly.

## Typed terminal-port attachment

`Cdc6tNormalTerminalPorts` explicitly attaches a prepared conducting runtime to
two distinct buses in one network. The rectifier port owns the three measurement
states; the inverter port has no differential coordinates. Both obtain currents
from the same runtime and refresh both terminal voltages before evaluation.
Modified-Euler advancement runs only through the owner. The staggered solver
recognizes both ports but registers only the rectifier state participant, with
attachment and activation guards.

Bus references are assigned only during explicit attachment, because the engine
automatically maintains inverse device-list membership. Tests cover initialization
in either terminal order, duplicate attachment, current signs, inverter-only
no-op stepping, owner advancement, shared derivative evaluation, pair deactivation
and loss of either terminal attachment. The adjacent numerical-shunt staggered
binding tests are retained as regression coverage.

This typed path currently supplies ordinary conduction only. Automatic named-link
import remains unsupported, and full network trajectories, coordinated protection
events, reinitialization after stopped integration and independent reference
accuracy are not yet qualified. No full-model support claim follows from the
terminal-port tests.

## Complete-network no-event gate

The paired ports now pass a complete small-network no-event run under both
modified-Euler and staggered solvers, including the ordinary state monitor.
The solved terminal voltages remain within `1e-5` pu throughout the run and the
three DC measurement states remain within `0.01` of their initial SI values.
Observed voltage drift in this gate is approximately `1.45e-15` pu, with unchanged
measurement states. This is equilibrium evidence, not fault-trajectory acceptance.

The full path exposed two integration defects that component replay could not:
the LCC builder assumed load-flow-only bus types, leaving converter terminals
unbound when importing a dynamic network; and terminal recording omitted the
required device identifier. The builder now binds converters through the common
bus interface while preserving converter IDs, and each port exports its recording
identifier. The focused gate passes 77 tests without failures or skips, including
existing LCC load-flow and JSON round-trip tests. Automatic controller import and the
coordinated fault/protection runtime remain unfinished.

## Coupled conducting-fault comparison

The typed ports now pass fourteen independent coupled comparisons: both methods
for the original rectifier fault, three current/power command modes, two active
current-ceiling curves and an inverter fault. Each run uses the retained source
network, a 0.5 ms step and `1e-6` network voltage-correction tolerance. All 1,005
samples are compared in grid order, including negative-time startup and both
event-side duplicates. No angle offsets or event-window exclusions are applied.

The comparison checks both terminal voltage magnitudes/angles, all three
measurement states, converter P/Q, firing/extinction angles and DC voltages.
At every observation, the electrical boundary is refreshed using the solved
terminal voltages without advancing state or protection history. Complete actual
trajectories and per-channel errors use bounded output writers.

Across these profiles, maximum terminal-voltage errors are `1.215e-4` pu for
modified Euler and `3.73e-6` pu for staggered integration. Maximum measured DC
voltage-state errors are 113.82 V and 1.60 V, respectively; current-state errors
are 0.02780 A and 0.00749 A. Maximum terminal active/reactive-power errors are
0.003578/0.002059 pu for modified Euler and 0.000100/0.000069 pu for staggered
integration, on the 100 MVA network base. Tests enforce separate channel-specific
envelopes close to these observed values. The largest modified-Euler differences
occur at the first endpoint after fault clearing; the equations and parameters
are shared, without fitted corrections.

This qualifies only the declared conducting profiles on the small LCC network.
Complete protected SMIB behavior, shorter-step convergence, production intervals,
longer runs, automatic named-link import and coordinated protection remain open.

## Electrical regimes and source-order current ceiling

The shared electrical/transducer runtime now accepts explicit blocked, bypassed,
recovering and power-to-current regimes from an external event owner. Evaluation
does not select a transition or commit a timer. Blocking sets both terminal
currents and DC voltages to zero while measurement derivatives continue to decay;
it also supports a zero terminal voltage without division. Bypass retains the
scheduled current and supplies only rectifier loss/reactive demand. Recovery uses
scheduled current times its recovery factor before applying the current ceiling;
the voltage factor scales the scheduled voltage. Power-to-current mode uses the
scheduled current with the same ceiling. Auxiliary modulation in these additional
regimes is explicitly rejected pending independent qualification.

Six retained blocking/bypass profiles qualify every electrical sample using the
recorded states, regimes and ramp factors, including transition duplicates. The
largest observed terminal-power error is below `2.43e-5` pu, converter-angle error
below `6.24e-5` degrees, and DC voltage error below `0.186` V. These are measured-input
electrical checks, not proof of coupled timer decisions. Separate tests verify
blocked-state decay through both integration methods and reusable numerical buffers.

Recovery also established the source-order interpretation of the redundant final
plateau in the retained current-limit data. The first segment runs from 250 kV/
450 A to 500 kV/2,000 A; a subsequent 425 kV/2,000 A point does not move that knee.
Sorting those points produces the wrong slope and prematurely relaxes the ceiling.
`fromSourcePoints` preserves this qualified redundant-plateau case; the ordinary
constructor still requires increasing voltages, and other unordered source curves
remain rejected. The normal coupled fixtures now use the exact source-order
preparation. Full protection ownership and automatic import remain unfinished.

## Transactional protection coordinator

`Cdc6tProtectionCoordinator` owns independent accepted and working copies of the
rectifier, inverter, bypass and mode-switch histories. `beginTrial` starts at a
finite, nondecreasing physical time; explicit `observe` calls may update working
timers and discover transitions as the electrical solution changes. Getters and
differential/current evaluation never perform those observations. `acceptTrial`
publishes the complete working history; `discardTrial` leaves every accepted
coordinate unchanged, including a pending inverter communication request.
Invalid observations poison the trial until it is discarded. Reinitialization
is explicit and forbidden while a trial is open.

Three cold-repeated independent controls establish the shared-mode rules:

- Inverter-origin blocking supersedes and clears an active inverter bypass.
- Release from that block uses the inverter recovery delay. Changing it from
  0.01 to 0.04 seconds changes release from approximately 0.1105 to 0.1405 seconds.
- Rectifier-origin blocking uses its own recovery conditions; the control
  recovers while inverter voltage remains below its separately raised recovery
  threshold. Recovery from blocking ramps both voltage and current and inhibits
  DC-voltage bypass pickup until recovery completes.

The coordinator retains the originating block cause and delegates its release
to that component. Blocking clears the bypass history; release creates one shared
restart epoch. Its primitive buffers are reused: the warmed normal trial/rollback
gate observes 600 bytes total over 100,000 trials, below its 1 KiB limit.
Both integration APIs can consume its electrical
regime without accepting a protection event. The focused CDC6T and adjacent LCC/
staggered regression gate passes 110 tests without failures or skips.

All rows of the two inverter-origin captures are replayed. Four/two mode
differences remain confined to explicit transition neighborhoods, with first
transition times within one recording interval. The rectifier discriminator
independently checks release while the inverter threshold is unsatisfied;
instantaneous pickup still cannot be inferred completely from exported endpoint
voltages because intermediate network observations can trigger it.

The optional typed terminal attachment now connects this coordinator to network
callbacks under both methods. Initial-boundary solves accept zero-time event
observations. Modified Euler retains a provisional endpoint across its predictor
and corrector; only final convergence accepts that endpoint's history. Discarding
predictor history while keeping predictor voltage had repeatedly restarted the
inverter recovery timer, preventing recovery. A failed corrector now discards the
entire provisional history. Staggered completion supplies the endpoint clock to
its final network solve; unrelated direct probes cannot advance that clock.
Only the rectifier port owns history, and getters do not observe protection.

The focused CDC6T and adjacent LCC/staggered gate passes 128 tests, with no
failures or skips. It includes all three protected fault profiles under both
methods, explicit failed-solve rollback, predictor/corrector ownership,
reinitialization with a pending trial, and read-only monitoring. Sustained
conducting recovery begins within two network intervals of each independent
capture. Retained ramp factors alone cannot identify recovery: a release attempt
may immediately reblock while retaining those factors.

These lifecycle checks do **not** establish protected trajectory acceptance.
The diagnostic export preserves every independent row and maps it to the same
integration time, retaining scheduled event sides and inspection duplicates.
Internal reference transitions without a corresponding recorded side in our run
are reported rather than synthesized or discarded. Across the protected profiles,
maximum differences reach 0.23390 pu terminal voltage and 4.355 kV measurement
state; voltage and power differences also persist during recovery. Complete raw
trajectories and per-reference-row errors are retained for diagnosis. No broad
accuracy tolerance is used to turn these diagnostics into a passing trajectory
gate. The ordinary conducting trajectory gates remain unchanged.

The private `verification-artifacts/cdc6t-protected-network-20261101/validation.zip`
retains the six complete diagnostic exports, validation log and focused reports;
SHA-256: `a1d561f79d1aebbc24ec67d914f7b0500518f2503df6a572e48473e986f4819b`.

Complete protected coupled trajectories, simultaneous fresh blocking
requests, cross-cause requests during blocking, remaining recovery interactions
and automatic import still require qualification. The complete model remains
unsupported by strict automatic import.

## Internally accepted input discontinuities

Internal blocking, bypass and recovery transitions now publish a monotonic
`staggeredInputDiscontinuityVersion` from the state-owning terminal. Predictor
trials and discarded solves cannot publish a revision. The shared staggered
solver consumes each revision once, applying the existing per-coordinate
discontinuity policy to that participant alone. It does not notify unrelated
controllers or change their retained rates. Existing participants default to a
constant revision and keep their previous execution. This adds one primitive
revision per participant and no per-step buffers or allocations.

This closes a concrete coupling omission: after inverter-origin blocking,
the first 0.5 ms staggered current-measurement update used to fall from 1,000 A
to 985 A. The independent capture gives 992.5 A, reproduced by the accepted
internal-event notification. At 0.25 ms the same treatment gives 996.25 A.
Tests verify those updates, provisional-versus-accepted revision ownership,
discarded recovery, monitoring and reinitialization.

For the rectifier-origin profile, the largest staggered voltage-measurement
state error decreases from approximately 1.884 kV to 19.05 V. For the ordinary
inverter-origin profile it decreases from 4.347 kV to 3.110 kV. A separate
cold-repeated 0.25 ms reference reduces the corresponding inverter-voltage state
error from 3.082 kV at 0.5 ms to 1.552 kV. This is consistent with the remaining
finite-step transition timing discrepancy; it does not establish full protected
trajectory accuracy. Modified-Euler equations and integration stages are
unchanged. The original and half-step diagnostic comparisons retain all reference
rows, internal-transition duplicates and original timestamps. The half-step
reference's single-precision clock drifts by up to 9.36 microseconds; comparison
checks the same integration index and a 10-microsecond clock bound without
shifting either trajectory.

The CDC6T, adjacent LCC and all staggered-named test classes pass 168 tests with
no failures or skips. Both cold half-step captures have identical CSV and
snapshot files. Their private archive is
`verification-artifacts/cdc6t-coordination-halfstep-20261101/captures.zip`, SHA-256
`8137239dd9529a6a9e8d34661ba7050a82e4f9b63b5106bf7bf187d1447a8d29`.
The validation log, focused reports and complete original/half-step comparison
exports are retained in
`verification-artifacts/cdc6t-local-events-20261101/validation.zip`, SHA-256
`425c4d688f95ebb4a3c74be7af1301294c2bf502f1cde9145ba7d3e06d43b13a`.

## Minimum conducting current demand

The source `C0` minimum demand now has an explicit prepared runtime parameter.
For normal, recovering and switched conducting operation, the runtime clamps
the requested current from below before applying the voltage-dependent ceiling.
Blocking still gives zero current; bypass keeps its separate scheduled-current
rule. The new constructor rejects negative/nonfinite minima and minima above
the lowest ceiling. The existing typed constructor retains a zero minimum;
source-driven callers must pass the supplied `C0`. The coupled fixtures now do
so explicitly. This scalar clamp uses the same reusable state and boundary
buffers under both integration methods.

Cold-repeated controls hold the restart request at 100 A and independently vary
the minimum to 200 A and 400 A. Both references hold current at the corresponding
minimum until the recovery request rises above it. All recorded rows, including
blocked, bypassed and recovering regimes and duplicated event samples, agree
with the shared electrical equations to below `3.06e-5` A current,
`2.20e-5` pu terminal power, `5.33e-5` degrees converter angle and `0.031` V DC
voltage. The regression limits are tightened separately for each channel.
These are recorded-input electrical checks, not claims about complete protected
trajectory agreement.

Both minimum-current profiles also run through the complete network under
modified Euler and staggered integration, asserting that recovery cannot fall
below the minimum. Their slower current recovery is intentionally still active
at the end of the half-second run. The focused CDC6T, LCC and staggered checks
pass 175 distinct tests, followed by a passing rerun of the three tightened
minimum-current tests. Full protected trajectories remain diagnostic.

Private captures:
`verification-artifacts/cdc6t-minimum-current-20261101/captures.zip`, SHA-256
`9634c30bc479897165d89e1bf97a9fba3c11881614dd7f6d3035eec6258c0639`.
Private validation reports and complete comparison exports:
`verification-artifacts/cdc6t-minimum-current-20261101/validation.zip`, SHA-256
`ff126fa64966bb286e053fc20499a6613012691283da4aac6080004073df329b`.

## Explicit reference-clock observation

The reference probe now records the exposed simulation clock, actual interval,
states and flags before and after fault operations and at requested inspections.
Cold repeats are identical. For the inverter-origin control, the exposed clearing
time is approximately 0.1000001 s, while the recorded recovery deadline is
0.1102501 s. Conducting recovery appears at the 0.1105001 s output sample; the
Java endpoint-clock control releases at 0.1100 s. The protection diagram defines
the delay and voltage conditions but does not define this internal half-step
observation schedule. No empirical clock offset has been added to production.
This timing difference and the remaining protected state trajectories still
need qualification.

Private clock captures:
`verification-artifacts/cdc6t-protection-clock-20261101/captures.zip`, SHA-256
`609104596f3293090e3e35e92302f05b765f701ea4ef1e82cdd8e8bcc6c93369`.

## Legacy rectifier recovery-delay sentinel

`Cdc6tRectifierProtection.fromSource` interprets the documented `TREBLK = -999`
source default as zero additional recovery delay. It still requires voltage
strictly above the rectifier recovery threshold and completion of the minimum
blocking duration. The existing typed constructor remains explicit and rejects
negative delays; the source factory also rejects unqualified negative values
other than this exact sentinel. Preparation occurs once and does not alter the
source record or add work to the event loop.

The discriminator raises the recovery-voltage threshold so that a blocked line
cannot recover while the fault remains. Separate negative-sentinel and explicit
zero-delay controls are each regenerated twice. All four reference CSV files and
state/flag/clock snapshots are identical; the initialized source constant remains
`-999` in the sentinel control. Complete Java traces are also identical between
the two settings within each integration method. Analytic checks independently
exercise the minimum duration, strict voltage equality, repeated blocking and
read-only preview behavior.

The focused CDC6T, adjacent LCC and staggered regression passes 180 tests with
no failures or skips, including the four new coupled sentinel/zero controls.
Their independent full-trajectory differences remain diagnostic; equivalence of
the two delay settings does not establish complete protected trajectory accuracy.

Private reference archive:
`verification-artifacts/cdc6t-recovery-sentinel-20261101/captures.zip`, SHA-256
`1655d2dda780170d4a48bafa5abaabde4c2fb68d991ece298ce9ec52bfce9aef`.
Validation reports and complete comparison exports:
`verification-artifacts/cdc6t-recovery-sentinel-20261101/validation.zip`, SHA-256
`6d8ff11380eee0bfce5d29bb7528042745bf43e3781581badc704e4a18022e56`.

## Immutable source preparation

`Cdc6tRecordMapper` prepares a named record with exactly 32 constants into
`Cdc6tParameters`. It preserves source units, record identity and sentinel values
in an immutable copy, and rejects invalid identity, cardinality and coefficients
with source-location diagnostics. Preparation converts angles and voltage units
once, outside the integration loop. Each prepared link owns its measurement
states, numerical buffers and protection histories; only immutable coefficients
can be shared. The implementation uses the existing shared equations under both
modified Euler and staggered integration.

The protected network fixtures now read their actual archived dynamic records
instead of duplicating the parameter wiring. All 48 complete diagnostic exports
across 16 runs are byte-identical to the preceding manually assembled runs.
Focused tests also check distinct transducer time constants, both integration
interfaces, recovery units, minimum current, dynamic angle bounds and independent
state/history ownership. The model, adjacent LCC, staggered and parser checks
pass 216 tests with no failures, errors or skips.

This is an explicit preparation API for the qualified fixed-tap, ordinary
dynamic-angle path. It accepts static equipment and a solved operating point;
the network preparation described below obtains these from the static owner.
Automatic attachment, complete constant-gamma variants and protected trajectory
acceptance remain open. Strict
automatic import continues to reject the incomplete runtime. Exact preservation
of earlier outputs does not resolve their documented reference discrepancies.

Private validation archive, including complete exports and test reports:
`verification-artifacts/cdc6t-source-preparation-20261101/validation.zip`, SHA-256
`6e30959216b6ffe3ba2090c5c75a6bb6784b60d0da74bd0255dc6fa3e0144d6b`.

## Coupled bypass and temporary current control

The independent DC-triggered bypass, AC-triggered bypass, long mode-switch and
early-clearing mode-switch controls now run through the full network with the
source-prepared coordinator under both integration methods. Each run checks
accepted clock ownership, scheduled current during bypass/current control, zero
inverter power during bypass, voltage-only bypass recovery and eventual return
to normal operation. The early-clearing control clears its fault at 0.06 s;
temporary current control must persist until its minimum duration ends near
0.07 s. Event times come from the retained reference manifests. The recorder
preserves both scheduled event sides, including early clearing.

The long mode-switch control passes complete per-channel trajectory gates:

| Maximum absolute difference | Modified Euler | Staggered |
| --- | ---: | ---: |
| Terminal voltage (pu) | 0.00004345 | 0.000003585 |
| Terminal angle (degrees) | 0.004278 | 0.001520 |
| Voltage measurement state (V) | 109.101 | 1.344 |
| Current measurement state (A) | 0.141063 | 0.001022 |

The gates also cover converter power, converter angles and DC boundary voltage,
with separate measured bounds for each integration method. No reference rows
are removed or shifted. The short mode-switch control remains diagnostic for
complete trajectories: the return transition produces a maximum terminal
voltage difference of approximately 0.0023062 pu at the transition sample.
Bypass recovery remains diagnostic as well, reaching approximately 0.12228 pu
terminal-voltage difference near release. Their lifecycle checks passing does
not establish full trajectory accuracy or justify widening acceptance limits.

The focused CDC6T, adjacent LCC and staggered regression passes 193 tests with
no failures, errors or skips. The complete model remains unqualified, and
automatic attachment remains disabled.

Private complete exports, reports and test source:
`verification-artifacts/cdc6t-bypass-switch-network-20261101/validation.zip`,
SHA-256 `374b74ea0f00ae05a37ea6084c186eea514ada2167ebf44ad83f33f5df484c7f`.

## Preparation from the solved static owner

`Cdc6tNetworkPreparation.prepare` resolves the exact named static LCC link in a
converged dynamic network. Missing or duplicate names fail before attachment.
It obtains the power-control side or current demand, scheduled DC voltage,
compensation resistance, solved terminal voltages/current, converter bridge
counts, reactances, ratios and taps from that owner. Converter primary voltage
base is kept distinct from the connected bus's nominal base. These values are
prepared once into the existing numerical runtime; no network search or static
equipment lookup enters the integration loop.

Preparation rejects inactive/unsolved owners, inconsistent current or DC voltage
drop, terminals outside the owning network, nonphysical coefficients and
unqualified static equipment/control variants. It creates an unattached pair
with independently owned states and histories, leaving the static network and
its existing devices unchanged. Explicit attachment uses the established single
injection per terminal. Later equipment changes require preparation again at a
safe initialization boundary; prepared coefficients do not track mutable taps.

All independent conducting and protected fixtures now use their archived
dynamic constants and actual solved static owners. The 24 protected trajectories
are byte-identical to the previous explicitly wired versions. The 14 conducting
fault trajectories still pass their unchanged per-channel limits, including
both power-control sides and current control. Additional checks verify initial
zero derivatives and equilibrium under both methods, immutable preparation,
independent ownership, command signs, compensation, equivalent primary-base
specifications and invalid-owner rejection. The final focused model, adjacent
LCC, staggered and parser regression passes 231 tests without failures or skips.

Automatic dynamic import remains disabled. Static RAW/JSON import now retains
the mode-transfer voltage and both commutation-capacitor impedances, as described
below. Other equipment/control variants, protected release timing and complete
trajectory qualification remain open.

Private complete exports, reports and preparation source:
`verification-artifacts/cdc6t-static-owner-20261101/validation.zip`, SHA-256
`72f9a21d0e971b0ecb1a8e1aee40c930ce68cc623171455e4ae30946f34f4555`.


## Retained source controls and network copying

`LccSourceControls` belongs to the core LCC data model and is serialized through
`HvdcLine2TLCCState`. Static RAW and JSON imports retain the mode-transfer
threshold and both capacitor impedances; JSON export and complete-network JSON
copy preserve them. The immutable record validates finite values without
changing source settings. Capacitor impedances remain in ohms and never enter
the static converter's reactive-power-offset field.

Default explicit CDC6T preparation uses the imported threshold. Missing or
unrecognized metadata fails instead of silently disabling mode transfer. A
separate explicit-threshold overload remains available for programmatic
networks and controlled experiments. Both paths reject retained capacitor
commutation and negative mode-transfer settings pending qualification. Equal
inverter angle limits select constant-gamma control. This variant was initially
rejected; the conducting implementation and remaining static-preparation limits
are described in [constant-gamma control](#constant-gamma-control).

Moving the metadata into core fixes a regression found by adjacent network-copy
tests: plugin-only extension data was previously lost on copying. Older
snapshots without these fields still restore without them, and unrelated
extension objects are not cleared by an absent source-control field.

Validation passes 15 core copy checks and 317 focused plugin checks, with no
failures, errors or skips. The scope includes versioned RAW layouts, nonzero
JSON export/import/copy round trips, missing metadata and variant rejection,
adjacent LCC power-flow/copy checks, parser checks, and staggered regressions.
All 14 conducting and 24 protected coupled trajectories under both integration
methods are byte-identical to the preceding static-owner checkpoint. This
preserves existing conducting acceptance and existing protected discrepancies;
it does not establish full protection accuracy or automatic attachment support.
No source metadata lookup was added to the per-step numerical runtime.

Private complete trajectories, reports and dependency hash:
`verification-artifacts/cdc6t-source-controls-20261101/validation.zip`, SHA-256
`b27ed7af773bcb16a5c551b8170c39dd7a48ff4ca9ed2ee4537612f2fdb6353b`.


## Half-step protection-release discriminators

Paired cold captures now cover DC bypass, delayed AC bypass and short-duration
power-to-current switching at 0.25 ms, in addition to their existing 0.5 ms
controls. Each pair has identical complete CSV output and state/flag snapshots;
all recorded samples and internal-transition duplicates are retained. The
coupled test executes each profile under modified Euler and staggered integration.

The exposed timer coordinates rule out one universal clock offset. Pickup
uses the preceding midpoint: the short switch deadline is fault time minus
half the interval plus its minimum duration. Bypass recovery is first observable
at the endpoint after clearing, and its deadline starts at the following
midpoint. In the ordinary bypass controls, the reference returns at 0.1105 s
with a 0.5 ms step and at 0.11025 s with a 0.25 ms step. Our accepted-endpoint
policy returns at 0.1100 s in both runs. These assertions describe the retained
reference observation schedule; they do not alter the production clock.

For the DC-bypass profile, the filtered inverter-voltage state errors shrink
approximately in proportion to the interval:

| Method | Interval | Maximum state error (V) | State RMSE (V) | Bus-voltage peak (pu) | Bus-voltage RMSE (pu) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Modified Euler | 0.5 ms | 4317.15 | 1000.03 | 0.122279 | 0.005544 |
| Modified Euler | 0.25 ms | 2172.83 | 496.98 | 0.122278 | 0.003871 |
| Staggered | 0.5 ms | 3089.61 | 730.81 | 0.122279 | 0.005534 |
| Staggered | 0.25 ms | 1553.45 | 363.77 | 0.122278 | 0.003867 |

The bus-voltage figures refer to the rectifier terminal and include every
reference row. The fixed peak occurs at the release discontinuity; halving the
step shrinks its time contribution, not its amplitude. The older nearest-row
short mode-return diagnostic reported about 0.002306 pu because it paired the
pre-action physical regime with the post-action reference side. The accepted
internal-transition observer now supplies both converged sides. Across both
methods and 0.5/0.25 ms intervals, side-matched rectifier and inverter voltage
errors stay below 0.000005 and 0.000006 pu; rectifier active-power error stays
below 0.00003 pu. Both sources retain unchanged continuous states across that
action. `Cdc6tBypassSwitchNetworkTest` gates the ordered side mapping and the
single accepted transition. This resolves the apparent short-return peak,
separately from the bypass's one-interval release difference. No duplicate rows,
event windows, or channels are removed, and no trajectory is shifted.

The focused CDC6T suite passes 143 tests without failures, errors or skips.
These additional checks constrain the diagnosis; complete protected trajectory
acceptance remains unresolved. A production correction must define separate
pickup/recovery observation stages, preserve transactional histories under both
methods and failed solves, and record actual internal transition observations.
The protection diagram alone does not justify adding a fitted half-step delay.

Private paired reference captures:
`verification-artifacts/cdc6t-release-half-20261101/captures.zip`, SHA-256
`c213f1d75cfd32f46652fabe6490e1d08e65ad61e05572670c742d9a575bd078`.
Private complete comparisons, all-channel metrics and focused reports:
`verification-artifacts/cdc6t-release-half-20261101/validation.zip`, SHA-256
`600856a7708a0d1cd7be56c20b9a44d08b8a303a9a6100d184852ed6771a9b16`.


## Prepared zero/short transducers

`Cdc6tMeasurements` now prepares a per-coordinate algebraic mask from the outer
network interval H. Independent paired fault controls establish the strict
rule T < 2H; equality stays differential. The source constants remain unchanged.
Negative and nonfinite constants are rejected. A local derivative call's step
size does not change the prepared mask. The terminal owner implements
`IntegrationStepAware`, so both supported imported-network solvers prepare this
mask before state initialization. Programmatic drivers must supply that same
preinitialization contract when using positive short constants.

Bypassing the lag alone is insufficient: it would otherwise create an immediate
feedback loop through the current-order calculation. Bypassed measurements are
projected from the converged network boundary, while command inputs retain their
sample until the next integration command boundary. Modified Euler samples once
at its predictor; the staggered owner samples once before its endpoint network
solve. Differential measurements continue to use their live integration states.
Current/output getters refresh electrical quantities but cannot project states
or sample new commands. Failed network solves cannot accept the algebraic
projection. Both numerical paths reuse the same arrays and equations; no
reflection, parsing or per-step buffer allocation was added.

The independent matrix includes zero constants, short unequal constants, values
on both sides of one and two network intervals, and an excited equality control.
Every profile is regenerated twice with identical complete CSV and state/flag
snapshots. All algebraic profiles pass strict full-trajectory gates under both
methods, preserving every sample and both scheduled event sides. Maximum bus
voltage difference is below 3.5e-6 pu; voltage-state differences stay below
0.22 V. Staggered integration also passes the strict differential equality and
above-boundary controls. Modified Euler retains its own explicit Heun equation,
checked analytically, and its differential-boundary accuracy remains separate:
the equality control reaches about 0.002174 pu voltage and 5.223 kV state error.
These errors have not been absorbed into wider trajectory-acceptance limits.

Projection, rejected solves, repeated getters, command sampling, equality,
local-step independence and predictor ownership have targeted lifecycle checks.
The focused model, adjacent staggered and parser run passes 247 tests without
failures, errors or skips. All 44 previously retained positive-lag conducting and
protected trajectories are byte-identical to the preceding checkpoint. Full
CDC6T qualification remains open, including protection-release timing, modified-
Euler differential-boundary accuracy, other equipment variants and automatic
attachment.

Private paired reference captures:
`verification-artifacts/cdc6t-short-transducers-20261101/captures.zip`, SHA-256
`70102ccd87680e741dbb251ad9e9b08fb2d6994cc95a6dfc19e5316b0e6eedf1`.
Private complete comparisons, reports and implementation snapshots:
`verification-artifacts/cdc6t-short-transducers-20261101/validation.zip`, SHA-256
`7f2725dc01e9da2096b41c1d1bc9b750d83413dc6a14f5b913f0717f4d27b0f5`.

## Constant-gamma control

Equal static inverter angle limits select a prepared constant-gamma conducting
kernel. The static gamma supersedes the dynamic gamma constant without changing
the source record. This branch excludes the scheduled DC-voltage ceiling and
retains the rectifier's minimum-angle capability: when that capability binds,
the DC voltage drops and inverter gamma can exceed its static target. The
ordinary scheduled-voltage constructor retains its existing behavior.

Both integration methods use the same numerical kernel, measurements and
reusable buffers. Selection and angle conversion occur during preparation;
the network iteration adds no allocations, source lookups or separate state
implementation. Protection ownership and accepted-history policies are unchanged.

Independent paired power-controlled captures with dynamic gamma set below and
above the static target produce identical trajectories. Measured-input replay
using independently solved equipment passes all converter P/Q, firing-angle
and DC-voltage gates, including active rectifier limitation. Maximum DC-voltage
error is 0.234 V and maximum converter-angle error is 0.000037 degrees. These
replays qualify conducting algebra, not the local static power-flow solution.

The coupled fixed-tap, current-controlled controls use matching static/dynamic
rectifier limits to avoid an initial static limiter transfer. Faults at either
terminal run for 0.5 seconds with 0.5 ms network steps under both integration
methods. Every reference row and both scheduled event-side duplicates are
retained. Observed maxima are:

| Quantity | Modified Euler | Staggered |
| --- | ---: | ---: |
| Bus voltage | 0.00000804 pu | 0.00000804 pu |
| Bus angle | 0.000589 degrees | 0.000589 degrees |
| Voltage transducer state | 184.25 V | 3.32 V |
| DC boundary voltage | 5.05 V | 5.05 V |
| Converter P/Q | 0.0000981 pu | 0.0000976 pu |

The larger modified-Euler transducer error occurs at an event-side update;
the test declares separate state envelopes for the two methods, retains the
same timestamps and gates every channel. It does not establish general gamma
protection/recovery or production-step qualification.

The original static power-controlled preparation gap is resolved for the
normal conducting profiles described below. Rectifier-limited current-order
initialization is qualified separately in the startup-transfer section;
power-order and nonzero-margin transfer variants remain open.

Explicit preparation now rejects a constant-gamma power point whose solved
terminal V-times-I differs from its power schedule by more than one part per
million. This prevents silent initialization from the known inconsistent
operating point while allowing consistent solved equipment. Automatic dynamic
attachment remains disabled and complete CDC6T qualification remains open.

Validation passes 259 focused model, parser and adjacent integration tests,
with no failures, errors or skips. All 70 retained trajectory files from the
preceding checkpoint are byte-identical. The new test is registered in the
core plugin suite; the complete repository suite was not rerun for this slice.

Private paired power-controlled captures:
`verification-artifacts/cdc6t-constant-gamma-20261101/captures.zip`, SHA-256
`b5398197ab827c04b1d955482fa99acedc6e5227c56ea5a0395b68e82b2b41cf`.
Private paired current-controlled captures:
`verification-artifacts/cdc6t-constant-gamma-fixed-v2-20261101/captures.zip`, SHA-256
`1fe186a87d766575bfa7bdda495cf85d6f9b88f760a00014cdf0102cc381df68`.
Private complete comparisons, reports, implementation snapshots and static
preparation diagnostics:
`verification-artifacts/cdc6t-constant-gamma-20261101/validation.zip`, SHA-256
`d9242920a91b5583cfe75d31add9a5dcadb22c404cee4138fb709934aa5549de`.

## Constant-gamma power and tap preparation

Explicit equal inverter angle limits now retain the scheduled DC power in both
the static converter calculation and reduced coupled network equations. With
inverter source voltage `E`, commutation resistance `Rc`, and line resistance
`Rd`, solve `P = I * (E + R * I)` using the stable low-current root
`I = 2P / (E + sqrt(E*E + 4*R*P))`. Here `R = -Rc` for inverter metering and
`R = Rd - Rc` for rectifier metering. Invalid or singular operating points are
rejected. Unequal angle limits retain the existing transferred-current policy.

For the single-rectifier/single-inverter configuration, enabled discrete taps
regulate the scheduled compounded voltage. The scheduled voltage and current
determine the target source voltage; the evolving solved DC voltage is not
used as the target. Taps are rounded to the configured grid and bounded by the
equipment limits. Reduced electrical equations continue to own terminal P/Q,
while the outer control boundary permits this explicit gamma tap adjustment.
Fixed-tap controls explicitly disable the DC tap flag in the test setup.

Independent paired captures at scheduled 495, 500 and 505 kV reproduce the
inverter tap settings and initial current/voltage, including power metering at
either terminal. Initial converter checks require tap agreement within 1e-7,
DC voltage within 0.5 V and current within 0.001 A. Core tests check the power
equation, fixed-tap behavior, residuals and analytic bus/order/tap derivatives.
The inconsistent-power preparation guard is retained and tested by corrupting
an otherwise correctly solved schedule.

The expanded coupled matrix covers fixed-current and power-controlled faults,
both terminal disturbances, dynamic gamma above/below the static target,
scheduled-voltage variation and both power-metering ends. Each runs under
modified Euler and staggered integration with the shared runtime and unchanged
state lifecycle. Complete 1,005-row comparisons retain both scheduled event
sides. Every channel has an explicit gate; `maxima.csv` preserves measured
maxima and limits. This preparation fix adds no per-step model work.

Validation passes 121 isolated core tests and 293 plugin/model/adjacent tests
with no failures, errors or skips. All 77 preceding complete trajectory files
are byte-identical. The complete repository suites were not rerun for this
slice. Protected gamma recovery, rectifier-limited initialization, additional
equipment variants, production-step/long-duration acceptance and automatic
attachment remain open; this is not full CDC6T qualification.

Private paired additional reference captures:
`verification-artifacts/cdc6t-gamma-power-preparation-20261101/captures-v2.zip`, SHA-256
`002bef9babdc7f44cd5afc708d16e04d7b6f675d5dfb485feffd31265a61fd3e`.
Private complete comparisons, reports, source snapshots and manifest:
`verification-artifacts/cdc6t-gamma-power-preparation-20261101/validation.zip`, SHA-256
`1567f9becdbac170043599ad1e5b3a3428dfc42100afc1b15b3a233423b8c157`.

## Rectifier-limited startup transfer

The explicit current-order constant-gamma path now accepts a solved rectifier
minimum-angle/inverter-current operating point with zero current margin and
unchanged scheduled current. In this mode equal source inverter angle limits
select gamma control; they do not impose a physical upper angle bound after
current-control transfer. Static reporting and reduced-equation boundary
detection permit the inverter angle to rise while retaining its minimum.
Unequal physical angle limits retain their existing behavior. A core regression
checks the resulting derivatives and the unequal-upper-limit control.

The independent startup discriminator has different static and dynamic
rectifier minima. Its dynamic converter therefore changes the physical initial
injection. Previously the solver absorbed that change into operating-point
current compensation, holding the bus voltages at the static solution and
leaving voltage-transducer states initialized from the old boundary. Merely
correcting the reported static angle did not fix that trajectory discrepancy.

Prepared terminal ports now publish their solved static injection while the
solver captures representation compensation. The existing `InitialEndpointAction`
contract then activates the shared dynamic equations, requests a fresh network
solution and initializes the measurement states at its settled boundary. Only
the rectifier port owns this action. Repeated actions/getters are idempotent;
either terminal initialization order restores the prepared starting condition.
Initial settling does not advance protection clocks, so negative-time startup
remains available. Integrating before this endpoint is settled fails explicitly.
No new hot-loop arrays, expression lookup or duplicate equations were added.

Paired independent captures retain load-flow and converted-network equipment
and AC boundaries; recorded bus-angle units are radians. The static reference
retains a small AC residual: the local inverter angle differs by 0.002472 degrees
after the corrected static solve. The dynamic comparison uses every absolute
sample without fitting or subtracting an initial offset. Across faults at either
terminal, at 0.5 ms steps over 0.5 seconds, the measured maxima are:

| Quantity | Modified Euler | Staggered |
| --- | ---: | ---: |
| Bus voltage | 0.000002085 pu | 0.000002085 pu |
| Bus angle | 0.0009115 degrees | 0.0009116 degrees |
| Voltage measurement state | 183.69 V | 2.214 V |
| DC boundary voltage | 1.023 V | 1.023 V |
| Converter P/Q | 0.0000240 pu | 0.0000236 pu |

The earlier voltage discrepancy was 0.001371 pu. These new profiles have their
own channel limits, including 0.00095 degrees for the bus-angle residual and
separate finite-stage state limits. Existing profile limits are unchanged.
Complete trajectories retain 1,005 samples and both scheduled event sides.

Validation passes 122 isolated core tests and 298 plugin/model/adjacent tests,
without failures, errors or skips. All 89 preceding complete trajectories are
byte-identical. Tests cover source-mode guards, nonfinite initialization,
nonzero-margin rejection, repeated current reads, single-owner initial action,
reinitialization, premature integration rejection and both coupled integrators.
The new static/lifecycle test is registered in the core plugin suite. Complete
repository suites were not rerun for this slice.

Power-order transfer, nonzero current margin, initially active current ceilings,
protected gamma recovery, other equipment configurations and production-step/
long-duration qualification remain open. Automatic attachment remains disabled.

Private paired preparation-boundary captures:
`verification-artifacts/cdc6t-gamma-transfer-20261101/captures.zip`, SHA-256
`be173a4e676a863b17e6575e51ef5580c13e94ea5c3ac85d7cf793c8190142d1`.
The earlier fixed-current reference archive retains both terminal faults.
Private complete comparisons, reports, diagnostics and source snapshots:
`verification-artifacts/cdc6t-gamma-transfer-20261101/validation.zip`, SHA-256
`8f943506398640b59f30c901d9a70f968ab29445658388aa63514f056efbc604`.

## Power-order and current-margin startup transfer

Explicit constant-gamma preparation now accepts rectifier-limited power orders
at either metering end with zero current margin, and current orders with a
consistent nonnegative static margin below one. The core static calculation
and reduced network equations preserve the scheduled power after transfer.
They solve the positive, nonsingular branch of the converter power equation;
analytical voltage, power-order, firing-angle and tap derivatives are checked
against finite differences. Unequal inverter angle limits and nonzero-margin
power orders retain their previous handling.

A separate static bug applied the current margin at the rectifier without
publishing that reduced current to the inverter. A later calculation could
therefore undo it. Both terminals now receive the reduced current together.
Tests exercise zero and nonzero margins with tap adjustment enabled and
disabled, including a second calculation to check that the margin is not
applied twice. Existing imposed-current fixtures explicitly select current
control rather than inheriting an unrelated sample power schedule.

Independent startup captures distinguish static and dynamic control. For the
current-order discriminator, the static margin reduces 1,000 A to 900 A, while
the dynamic controller starts at its original 1,000 A order. Power-controlled
startup also changes the solved current. During the initial network solve,
the single owning port equilibrates all three measurements against each fresh
electrical boundary. Final acceptance retains that current and those voltages;
it no longer reinstates the stale static current. These zero-time operations
do not advance protection history. Current getters remain read-only, and both
integrators use the same state storage and equations. The added work occurs
only during initialization and uses the existing numerical buffers.

Paired independent inverter-fault captures cover current margin and both
power-metering ends at 0.5 ms, plus inverter-metered power at 0.25 ms. Runs last
0.5 seconds, with fault application at 0.05 seconds and clearing at 0.10 seconds.
Every channel and both scheduled event sides are retained. The inverter-power
comparison gives these maximum absolute errors:

| Quantity | Modified Euler, 0.5 ms | Modified Euler, 0.25 ms | Staggered, 0.5 ms | Staggered, 0.25 ms |
| --- | ---: | ---: | ---: | ---: |
| Bus voltage, pu | 0.00005833 | 0.00002984 | 0.000002342 | 0.000002175 |
| Bus angle, degrees | 0.004874 | 0.002841 | 0.000970 | 0.001105 |
| Voltage measurement state, V | 181.063 | 91.023 | 2.313 | 1.397 |
| Current measurement state, A | 0.012281 | 0.004289 | 0.002287 | 0.001921 |
| Converter P/Q, pu | 0.001948 | 0.000985 | 0.00002536 | 0.00002697 |
| DC boundary voltage, V | 7.497 | 3.696 | 1.268 | 1.188 |

The modified-Euler event error decreases with the smaller interval. Staggered
bus-angle error does not decrease monotonically: an initial absolute residual
of approximately 0.000748 degrees remains. Neither method subtracts that
residual. The half-step reference's binary32 clock accumulates 9.36 microseconds
of departure; comparison retains both original times, checks the same interval
index and event side, and bounds the clock departure separately. It does not
shift or interpolate trajectories. New profiles have individual channel gates;
all preceding trajectory gates remain unchanged.

Validation passes 123 isolated core tests and 306 plugin/model/adjacent tests,
without failures, errors or skips. All 93 preceding complete trajectories are
byte-identical. Initial-state checks also reject retention of the old static
current and verify that reading the boundary cannot change state. Complete
repository suites were not rerun for this slice.

Protected recovery after these startup transfers remains unqualified: its
prepared ramp target still originates from the static operating point and
needs an independent discriminator before any change. Nonzero-margin power
transfer, initially active current ceilings, other equipment variants,
production-step/long-duration acceptance and automatic attachment remain open.
This slice does not establish complete CDC6T support.

Private paired captures:
`verification-artifacts/cdc6t-transfer-orders-20261101/captures-v2.zip`, SHA-256
`22d3a04e2db17d59cc8dfac64c2b3e472029a7e7c1b0d29f2d36346ccde5a543`.
Private complete comparisons, reports, source snapshots and integrity manifest:
`verification-artifacts/cdc6t-transfer-orders-20261101/validation.zip`, SHA-256
`c0989f391ea944fda364c0253788d9133720d7756dbf4361b8e6d77c98c49f36`.

## Recovery bases and converged protection decisions

Independent blocking/recovery captures distinguish three different currents:
the static load-flow current, the settled dynamic initial current and the
scheduled recovery base. The recovery base is the source current setpoint in
current control, or absolute scheduled power divided by scheduled voltage in
power control. The metering end does not introduce a DC-loss subtraction.
For example, 505 MW and 500 kV give a 1,010 A recovery base even though the
dynamic initial current is approximately 909.57 A. Both the restart fraction
and the delivered recovery request now use this same prepared base. Normal
power control continues to use its measured voltage.

For constant-gamma current control, a binding rectifier minimum angle reduces
the requested current by the configured current margin. The shared conducting
kernel now returns that delivered current and recalculates both terminal
boundaries. The current transducer receives the delivered current. This is
an algebraic decision: repeated evaluations do not accumulate reductions, and
an inactive rectifier limit leaves the existing equations unchanged. Nonzero
dynamic margins with scheduled-voltage control remain unqualified and are not
enabled by the new kernel overload.

These corrections exposed a separate coupled-event defect. At a restart at
0.070 s, an intermediate network iterate reached 0.899925 pu and latched another
rectifier block. The converged recovery voltage is above the blocking threshold.
The spurious block subsequently caused alternating network iterates and exhausted
the unchanged 50-iteration budget near 0.090 s under both integration methods.
The experimental mixer did not resolve it and remains disabled.

The core now provides an opt-in `ConvergedNetworkAction` contract. A participant
evaluates its working event decision at converged voltage; a changed injection
requests another numerical pass at the same physical time. All passes share
the original iteration budget, one begin/end lifecycle and failure cleanup.
`afterNetworkConvergence` runs once after the actions stabilize. A before-iteration
index is local to its numerical pass. Callback exceptions, action-budget
exhaustion and ordinary numerical nonconvergence all discard working events.
The existing path is retained when no participant implements this contract.
The action list is reused and the first pass reuses its existing diagnostic
record; no controller arrays or histories are copied in the numerical loop.

CDC6T samples protection at the preceding accepted boundary and at converged
network boundaries. Intermediate voltage iterates refresh electrical injections
without latching protection. The owning port keeps predictor/corrector and
accepted-history rules; the other terminal does not own an action. Recovery now
completes under both integrators with the original step, tolerance and iteration
limit. At 0.080 s the rectifier voltage differs from the independent current-order
capture by approximately 0.00000002 pu.

Three paired independent captures cover current control with a nonzero margin
and both power-metering ends. Complete recorded-input replay, including every
transition sample, has maximum converter P/Q error below 0.0000082 pu, angle
error below 0.000064 degrees and DC-voltage error below 0.297 V. Prepared-factor
tests cover restart, reset and rejected trials. Coupled tests verify both
integration methods, delivered-current derivatives, accepted clocks and the
shared network budget. The new test is registered in the core plugin suite.

Validation passes 149 isolated core tests and 319 plugin/model/adjacent tests,
without failures, errors or skips. Of 101 preceding complete trajectories,
71 are byte-identical. All preceding state values, timestamps, accepted clocks
and recorded modes are unchanged. The remaining numerical differences have
maximum bus-voltage change 0.000000330 pu; the largest converter reactive-power
change is 0.00000439 pu. Existing acceptance gates are unchanged. Complete
repository suites were not rerun for this slice.

Full protected-trajectory acceptance remains open. The new complete comparisons
retain a peak bus-voltage error of 0.057843 pu at the internal recovery transition;
the local recorder does not yet expose every reference internal event-side
observation. Maximum voltage-state errors are approximately 4,095 V for modified
Euler and 29.23 V for staggered integration. These are retained diagnostic
results, not waived accuracy gates. Remaining work includes internal transition
recording and timing, severe faults with infeasible conducting intermediates,
nonzero-margin power/scheduled-voltage controls, bypass and temporary-current
bases after startup transfer, production intervals, long-duration runs and
automatic attachment.

Private paired captures:
`verification-artifacts/cdc6t-transfer-recovery-20261101/captures.zip`, SHA-256
`61767d749282ba5c1c6399e4fb55e4f6717ae5188bbcbfcb29fd225ca52f62dd`.
Private complete comparisons, reports, failure diagnostics and source snapshots:
`verification-artifacts/cdc6t-transfer-recovery-20261101/validation.zip`, SHA-256
`a79719a398973dccfef7b1425b88d7d42aa85d4f79ace830f00d25969e8a154e`.

## Source-base bypass and temporary current control

Paired independent discriminators now separate the scheduled current from the
current reached by startup settling. With constant-gamma controls, the latter
is approximately 907.93 A for inverter-metered power and 909.57 A for
rectifier-metered power. The reference bypass and temporary-current regimes
instead use 1,000 A and 1,010 A, respectively: absolute scheduled power divided
by scheduled voltage. Current-controlled bypass uses the source current order.

The shared runtime now uses the same immutable prepared base for these regimes
and recovery. Initialization changes measurement states without overwriting that
base. Temporary current control retains the existing minimum demand, current
ceiling and converter-limit evaluation. Bypass retains its separate electrical
rule and sends the delivered current to the measurement derivative. This removes
one mutable field and adds no per-step allocations, filtering or integration
changes. Both integration methods execute the same equations.

All five new recorded-input profiles failed the current-derivative assertion
before the correction. Afterwards, all active samples pass. Bypass terminal
active-power error is below 0.000000006 pu, firing-angle error below 0.00004
degrees, and DC voltages match the independent captures. The bypass reactive
residual reaches 0.00008556 pu in these transferred profiles; its separate
0.00009 pu envelope does not replace the tighter earlier-profile limits or
establish exact numerical equivalence. Temporary-current electrical replay has
power errors below 0.000002 pu and DC-voltage errors below 0.22 V.

Both power-metering ends have complete temporary-current fault trajectory gates
under both methods, retaining all states, channels and scheduled event sides.
Maximum bus-voltage error is 0.00005091 pu for modified Euler and 0.00000236 pu
for staggered integration. Maximum voltage-state error is approximately 184.77 V
and 2.38 V, respectively; the separate method-specific gates retain this finite-step
difference. Bypass profiles also complete under both methods and check delivered
current, accepted clocks and the unchanged network-iteration budget.

Full bypass/recovery accuracy is still incomplete. Complete diagnostic comparisons
retain a maximum bus-voltage difference of 0.14590 pu around internal transitions;
maximum voltage-state differences are approximately 3,534 V for modified Euler
and 26.34 V for staggered integration. These are not accepted trajectory gates.
Internal-event observation, recovery timing and longer/production-step runs
remain required. Automatic attachment remains disabled pending full qualification.

The adjacent plugin/model matrix passes 334 tests without failures, errors or
skips. All 110 preceding retained trajectories are byte-identical. A subsequent
focused run checks the new per-channel temporary-current trajectory limits.
The preceding core lifecycle validation remains applicable; no core code changed
in this slice. Complete repository suites were not rerun.

An initial temporary-current discriminator put its transfer threshold above the
scheduled initial voltage and failed the reference initialization quality gate.
It is retained as rejected evidence. The accepted discriminator uses a threshold
below that initial voltage, activates during the fault and cold-repeats exactly.
No quality gate was bypassed.

Private paired captures:
`verification-artifacts/cdc6t-transferred-modes-20261102/captures.zip`, SHA-256
`2989ff0be244dad9a96b05cda8c275643414f6e738a7ee6ba1a66e20a9fc397d`.

Private complete comparisons, test reports, source snapshots and rejected-profile evidence:
`verification-artifacts/cdc6t-transferred-modes-20261102/validation.zip`, SHA-256
`9dd702c22258adf8f742baae309483ab19619f541a2211e2ac0fd8c305134ca9`.

## Modified-Euler endpoint discontinuities

The predictor network previously applied a protection transition at its future
endpoint. The differential corrector then used the new regime's input over the
interval ending at that endpoint. This mixed a right-hand discontinuous input
into integration of the preceding interval, even though committing protection
history itself waited for final convergence.

The predictor now prepares continuous recovery factors without observing or
applying discrete protection changes. The differential corrector uses this
left-hand regime; the accepted endpoint network observes protection and applies
the new input. Reaching a recovery-ramp limit similarly preserves recovery
control until explicit endpoint observation. The same transactional coordinator
and reusable numerical buffers remain in use. No equation, time constant,
network tolerance, integration interval or reference timestamp was changed.

An analytic regression blocks the link for a complete modified-Euler interval
ending exactly at release. Each transducer must obey the zero-input Heun update
`x_next = x * (1 - h/T + h*h/(2*T*T))`. Applying endpoint recovery must leave
those continuous states unchanged. Separate lifecycle checks cover deferred
release, failed corrector rollback, ramp completion, and unchanged accepted
clocks. These replace the former test expectation that the predictor already
select recovery before differential correction.

Independent comparisons show a material improvement without establishing full
protected-trajectory acceptance. In the transferred bypass profiles, the worst
modified-Euler inverter-voltage-state error falls from approximately 3,534 V to
1,360.2 V. In transferred block recovery it falls from approximately 4,078 V to
1,364.94 V. In the earlier AC-bypass profile, the remaining error falls from
3,094.06 V to 1,554.83 V when halving the interval from 0.5 ms to 0.25 ms.
Finite-step and internal-event differences remain; they are not fitted away.

The adjacent matrix passes 335 tests. Across 120 retained preceding complete
trajectories, 92 are byte-identical. Only modified-Euler protected trajectories
change; all staggered trajectories remain byte-identical. Recorded modes,
physical timestamps and accepted clocks are unchanged. Existing strict
conducting and temporary-current trajectory gates pass unchanged. A subsequent
focused lifecycle run additionally checks deferred ramp completion. No core
code changed, and complete repository suites were not rerun.

The large reported bus-voltage peaks at internal recovery transitions remain:
the reference contains pre/post observations at the same timestamp while the
local recorder currently exposes only the post-transition observation there.
All raw reference samples remain retained. This observation gap and the remaining
state errors must be resolved before claiming complete protection accuracy.

Private complete comparisons, analytic/lifecycle reports, source snapshots and integrity manifest:
`verification-artifacts/cdc6t-left-limit-20261102/validation.zip`, SHA-256
`9608c608941b91f4f78ed0285117ba4f4c5846442f0ba4fca86657296bbcf055`.

## Transactional two-sided network observations

Protection decisions now run only after the current left-side network boundary
converges. They no longer act on the previous endpoint's voltage before the
first network pass. A changed electrical regime triggers another pass within
the existing shared iteration budget. This provides actual converged boundaries
on both sides of an internal transition at the same physical time.

`Cdc6tNormalTerminalPorts.setTransitionObserver` optionally publishes immutable
`Cdc6tNetworkTransition` observations. Recording is disabled by default. Snapshot
objects are allocated only at transitions when enabled; pending-list and state
buffers are reused. Output getters do not publish or advance history. A failed
network action discards both sides, and a successful accepted-history commit
publishes the completed transition. A recorder exception propagates after that
commit, clears pending observations and does not replay the event on the next
solve. Configure the observer outside a trial.

Differential measurement states do not advance between the two observations.
Algebraic measurement coordinates are evaluated separately on each converged
side through a read-only projection; the observer cannot change committed
storage or command sampling. Tests exercise ordinary and algebraic measurements,
discarded transitions, disabled recording, reinitialization and recorder failure.

The coupled recorder retains both snapshots alongside every ordinary sample.
The recorded physical times are unchanged, and no reference rows or duplicate
event samples are removed. Each new profile runs with recording both disabled
and enabled under both integration methods; all ordinary samples are exactly
equal, including states, outputs and timestamps.

This resolves the missing-side explanation for the large transferred-bypass
voltage peaks. With complete sides, current-controlled bypass has maximum
terminal-voltage error 0.00003026 pu for modified Euler and 0.00002150 pu for
staggered integration. The separate terminal-voltage gates pass across all
reference rows. Full-state qualification remains open: voltage-state errors
remain approximately 1,357 V and 26.34 V respectively.

Power-controlled bypass still reaches 0.013983 pu maximum voltage error at
recovery completion. The reference's accumulated single-precision time is just
below the ramp endpoint on that sample and it changes control on the following
recorded interval; the local double-precision clock reaches the endpoint sooner.
Both original clocks and all observations are retained. No timing offset or
precision emulation was introduced to conceal the difference. These profiles
remain diagnostic rather than fully trajectory-qualified.

Validation passes 349 plugin/model/adjacent tests without failures, errors or
skips. Of 120 preceding complete trajectories, 70 remain byte-identical; the
extra convergence before actions changes voltage by at most 0.000000057 pu
and voltage states by at most 0.0000000041 V. Current states, recorded modes
and timestamps are unchanged. Earlier strict trajectory gates remain unchanged.
Recording itself causes no numerical differences. Complete repository suites
were not rerun and no core source changed in this slice.

Event-side recording still needs extension across the remaining protected
profiles. Severe faults that cannot form a feasible conducting left-side
boundary, wider source settings, production-step/long-duration behavior and
automatic attachment remain unqualified. The model is not yet fully supported.

Private complete comparisons, recording parity/lifecycle reports, source snapshots and integrity manifest:
`verification-artifacts/cdc6t-transition-sides-20261102/validation.zip`, SHA-256
`42febbc4ef22bb5dfc3153353bebbdc20c9ee60eade11c2bf425f641230e8e14`.

## Explicit external-event protection before conducting evaluation

A repeated severe rectifier fault during recovery exposed an ordering failure:
the external event processor had updated the network and solved its frozen
injections, but the first device callback attempted an infeasible conducting
current before checking the blocking threshold. Both integration methods failed
at the second fault before completing a numerical network iteration.

`NetworkIterationAware.Context.externalEventBoundary` now identifies the INITIAL
network solve following an enabled external event. Its compatibility constructor
defaults to false, and predictor, corrector and direct numerical probes do not
set it. This marker describes an external discontinuity; it does not certify
nonlinear convergence or authorize committing device history.

At that explicit boundary, CDC6T observes terminal AC voltages once before its
conducting evaluation. Blocking stays in working history until the protected
network converges. Failure discards the pending action. Ordinary numerical
voltage iterates still cannot trigger this shortcut, and internal recovery
decisions retain the converged-network lifecycle. The frozen-injection voltage
is not published as a converged pre-transition observation. Scheduled-event
recording retains the actual external event sides separately.

Independent repeated-fault captures cover second-fault reactances of 0.02 and
0.0002 pu. Both modified Euler and staggered integration complete the half-second
runs at 0.5 ms intervals within the unchanged 50-iteration budget. The near-zero
profile reaches approximately 0.00572 pu rectifier voltage. During the interior
of the second fault, terminal-voltage maximum errors are below 1.2e-8 pu at the
rectifier and 8e-6 pu at the inverter, with zero active power at both terminals.
Voltage/current transducer errors remain below 42 V / 0.06 A for modified Euler
and 3 V / 0.003 A for staggered integration. These are fault-on gates; the complete
untrimmed recovery comparisons remain diagnostic. Near-zero recovery still has
approximately 0.09325 pu maximum rectifier-voltage error and 6.8 kV voltage-state
error, so full protected-trajectory acceptance is not established.

Validation passes 150 focused core tests and 354 plugin/model/adjacent tests,
without failures, errors or skips. All 130 preceding complete trajectories are
byte-identical. Lifecycle tests cover enabled/disabled events, every solver
stage, rejected trials and accepted-history commit. Complete repository suites,
other severe-fault protection paths, production intervals and long runs remain
outstanding.

Private immutable paired captures:

- `verification-artifacts/cdc6t-deep-recovery-20261102/captures.zip`, SHA-256
  `9249ae52bd557f2e4f4f8262d1111f825adbaf5aac2c7617e92e1860c33bd0fb`.
- `verification-artifacts/cdc6t-nearzero-recovery-20261102/captures.zip`, SHA-256
  `eaca1970bb56dd385bb18ba4722694448d5173ddaebff83da6aba34d59dcf242`.

Complete comparisons, prior-trajectory parity, failure evidence, reports and
source snapshots: `verification-artifacts/cdc6t-external-event-20261102/validation.zip`,
SHA-256 `875ad10b8f03b793e7f54b1841b9bc79e08d5bd1d7f6daf49282377ba121eeb7`.

### Recovery timer phase discriminator

The recorded timer channels localize the repeated-fault recovery peak to an
observation-phase difference, beyond accumulated floating-clock roundoff. The
reference's minimum blocking deadline starts at the preceding midpoint. Its
recovery deadline starts at the following midpoint after clearing and is first
published at the next endpoint. In this profile, clearing is recorded at
0.249998554587 s, the recovery deadline at 0.260248541832 s, and release at
0.260498434305 s. The local endpoint-timer implementation releases at 0.260 s.

The reference regression checks both timer formulas against the imported source
delays, using 30 ns for serialized timer/clock arithmetic. It also checks the
first eligible release endpoint, both actual event sides and unchanged
differential storage between them. This distinguishes phase behavior from a
universal time offset; neither trajectories nor event samples are shifted.
Production timer-phase semantics still need implementation and independent
qualification across pickup, recovery, bypass and temporary-current controls
under both integration methods. Existing full-recovery error gates remain open.

All four coupled profile/method tests pass with these additional reference
checks, and their complete trajectories remain byte-identical to the preceding
validation. Private report, source and integrity manifest:
`verification-artifacts/cdc6t-recovery-clock-20261102/validation.zip`, SHA-256
`d12fbb020be79aefadc072b2f2cb97416b07df658ea2e5e4cbf89bad48de0115`.

## Sampled blocking timers with physical endpoint ownership

Source preparation now selects `BLOCK_INTERVAL_MIDPOINT` for rectifier- and
inverter-origin blocking timers. The owner prepares the outer network interval
once through `IntegrationStepAware`. Timer origins use the preceding midpoint;
physical accepted-history times, event timestamps and recovery ramps continue
to use the actual endpoint. Controller substeps do not redefine the interval.
The explicit typed coordinator constructor retains its endpoint-observation
contract, including standalone replay without a configured network interval.

An INITIAL event solve can immediately block the link, but cannot start a
sampled recovery timer. Recovery observation begins with the next completed
interval. Predictor preparation cannot commit history; a corrector or accepted
staggered endpoint owns the observation and commit. Repeated zero-duration
staggered completions do not start recovery. Rejected trials discard pending
deadlines. Reinitialization resets histories and retains the prepared interval.
The runtime continues to use scalar coordinates and reusable history buffers;
the sampled-clock allocation check reports zero bytes over warmed trials.

The severe repeated-fault terminal trajectories now pass across every reference
row and event side under both integration methods. Across the two fault depths:

| Maximum absolute error | Modified Euler | Staggered |
| --- | ---: | ---: |
| Rectifier voltage (pu) | 0.00002876 | 0.00001546 |
| Inverter voltage (pu) | 0.00001523 | 0.00001121 |
| Rectifier angle (degrees) | 0.001658 | 0.001061 |
| Inverter angle (degrees) | 0.003240 | 0.002483 |
| Voltage measurement states (V) | 1367.96 | 29.49 |
| Current measurement state (A) | 2.45 | 0.05053 |

The former approximately 0.09325 pu voltage peak is removed without changing
source delays, shifting traces or discarding samples. Full measurement-state
and converter-output qualification remains open; the terminal gates do not
establish complete model acceptance.

Do not apply the same sampling rule automatically to bypass or temporary-current
control. An all-timer experiment regressed a previously accepted bypass profile.
Its reference publishes the recovery timer at clearing with a preceding-midpoint
origin, whereas other bypass captures first publish it at the following endpoint.
Those controls retain their existing behavior pending a separate investigation
of electrical-input observation and network stages. The unsuccessful experiment
and failing reports are retained in private evidence.

The scoped model/adjacent matrix passes 356 tests; the final coordinator test
expansion passes 12 tests, giving 357 distinct passing results with no failures,
errors or skips. All existing strict gates remain unchanged, and complete
terminal gates were added for the severe repeated faults. Of 134 prior trajectory
exports, 102 are byte-identical and 32 change only in the blocking profiles;
all bypass and temporary-current exports are unchanged. Other protected-profile
diagnostics retain unresolved event-side/trajectory errors. Full repository
validation, production intervals, long runs and automatic attachment remain open.

Private complete trajectories, reports, source snapshots, prior comparisons and
integrity manifest: `verification-artifacts/cdc6t-block-midpoint-20261102/validation.zip`,
SHA-256 `cac20a8c03224c1e994721765e0a3649aea813b89fdcca5b16306a4d643b4802`.

## Converged local-event derivatives and interval qualification

The staggered solver previously averaged a newly accepted local input with its
preceding-interval derivative. An internal unblock instead requires the
converged left-endpoint derivative for that event observation. That derivative
is distinct from the two-step integration history, which must remain unchanged
until the next state update.

`StaggeredEulerParticipant.readStaggeredInputDiscontinuityLeftRates` supplies this
optional, read-only contract. CDC6T captures the first converged left regime in
reusable provisional storage and publishes it only after the network action
commits. Rejected actions do not replace the accepted rates; right-side getters
cannot overwrite them; reinitialization clears them. No left-side nonlinear
convergence is inferred for an external frozen-injection boundary.

The solver reads the snapshot only for a changed local revision outside a
scheduled/global discontinuity. It reuses the participant's trial buffer for
the copy, averages the observed event derivative and retains its separate
two-step history. Models that return false keep their existing behavior.
Scheduled events retain their existing qualified policy. Modified-Euler
equations and event treatment are unchanged.

Paired independent captures now also cover a half interval and the quarter-cycle
network interval. Cold repetitions are byte-identical. Complete terminal-voltage
and angle gates pass for both integration methods at all three intervals;
complete staggered measurement-state gates also enforce the recorded bounds.
Both sides of external and internal events and every reference row are retained.

| Network interval | Worst voltage-state error, modified Euler | Worst voltage-state error, staggered |
| --- | ---: | ---: |
| 0.25 ms | 688.86 V | 4.73 V |
| 0.5 ms | 1367.96 V | 15.47 V |
| Quarter cycle at 60 Hz | 11081.83 V | 1036.78 V |

These are half-second repeated-fault profiles, not long-duration qualification.
The first blocked interval independently distinguishes the integration methods.
For a current transducer initially at `x0`, with `r = h / T`, modified Euler
produces `x0 * (1 - r + r*r/2)`. The recorded event-averaged two-step response is
`x0 * (1 - 0.75*r)`. Tests verify both formulas against actual coupled samples.
Their event-step differences must not be removed by changing the selected
integration method or adjusting source parameters. The remaining state errors,
initial electrical-boundary differences and wider protection matrix still need
qualification before declaring the full model supported.

Validation passes 361 scoped model/adjacent tests, followed by eight coupled
interval tests with tightened state gates. No failures, errors or skips remain.
All 64 preceding modified-Euler trajectory exports are byte-identical; across
all methods, 101 prior exports are unchanged and 33 staggered exports change.
Existing strict acceptance gates remain in place. Complete repository suites
were not rerun, and no core source changed in this slice.

Private immutable paired interval captures:

- `verification-artifacts/cdc6t-nearzero-half-20261102/captures.zip`, SHA-256
  `52f57bc31e8a3ff661a2f75e7a6b44ad5fe5d472b6c6601b3b962f325cc2f547`.
- `verification-artifacts/cdc6t-nearzero-quarter-20261102/captures.zip`, SHA-256
  `0fbc19b9f7e6d9c65c732ae523ff36da680c872d4b4fed9e725772ed55f003b1`.

Complete comparisons, lifecycle reports, source snapshots and integrity manifest:
`verification-artifacts/cdc6t-local-left-20261102/validation.zip`, SHA-256
`dc8a8d8b494e8a89f262741cd0d3a149c77f83edd82a1ce170f1c21f27bd793d`.

## Initial source balance and ten-second terminal qualification

The ten-second fixed-tap control-transfer runs exposed a source initialization
error. The converter's initial control transfer settled a new electrical
operating point, but classical machines retained shaft power from before that
transfer. Independent generator power channels show that the reference starts
with shaft power balanced to the settled electrical power. The local mismatch
was already present before the first fault and produced artificial acceleration.

`InitialEndpointAction.isInitialOperatingPointPreparation()` now distinguishes
this preparation from an ordinary physical initial event. The shared solver
first applies declared control preparations, converges their network and
initializes their measurement states. Qualified classical sources then update
shaft power from the converged air-gap power, retaining internal emf, angle and
speed. Ordinary zero-time load or protection actions follow this phase and do
not rebase shaft power. Running faults likewise cannot rebase it. Existing
operating-point compensation is retained rather than recomputed to absorb the
control change.

This source reconciliation is currently qualified only for imported classical
machines without active governors, exciters or stabilizers. Other active source
types or active source controls cause an explicit rejection before consuming
the pending transfer. Their state/control initialization requires further
implementation and independent qualification. The restriction applies to the
declared startup-transfer path, not ordinary dynamic initialization.

Both integration methods use the same preparation and source equations. The
change adds initialization work only; controller states and derivatives retain
their shared numerical storage and existing integration stages.

Paired cold reference runs retain all terminal, converter and source-machine
samples, including duplicate event times and the last complete interval beyond
the requested stop time. Adding source diagnostics leaves all previously
recorded reference channels numerically identical. The comparison retains both
original clocks, checks integration ordinals and bounds the accumulated
single-precision clock difference; it neither shifts curves nor drops samples.

| Ten-second profile | Integration | Peak bus voltage error, pu | Peak bus angle error, degrees | Worst bus angle RMSE, degrees |
| --- | --- | ---: | ---: | ---: |
| Current order | Modified Euler | 0.000007244 | 0.004626 | 0.002687 |
| Current order | Staggered | 0.000007242 | 0.004263 | 0.002478 |
| Inverter power order | Modified Euler | 0.000011628 | 0.003989 | 0.002205 |
| Inverter power order | Staggered | 0.000020065 | 0.004318 | 0.002395 |

Before the correction, the current-order peak bus-angle difference was about
0.119 degrees. The corrected complete voltage trajectories have range-normalized
RMSE below 0.000080; the worst converter P/Q difference is 0.000689 pu. Tests
also check independent initial mechanical power, unchanged shaft power through
faults, recovery completion and the existing network-iteration budget.

Recorded-speed replay independently identifies source-angle precision as a
remaining contributor. Unrounded trapezoidal angle integration differs from
the recorded source angles by up to 0.00581 degrees. Rounding each accumulated
radian state to binary32 reduces that maximum below 0.000020 degrees, and
improves every source by more than a factor of ten. This is a diagnostic replay,
not the reference's complete integration implementation. Production machine
states remain unrounded, and raw reference angles remain the comparison target.

Some previous short-run limits benefited from cancellation by the incorrect
mechanical-power drift. The following raw-channel regression bounds were
re-established explicitly after correcting that initialization:

| Check | Previous bound | New bound | Observed maximum |
| --- | ---: | ---: | ---: |
| Limited-gamma rectifier angle, degrees | 0.000400 | 0.000450 | 0.0004087 |
| Staggered power-order rectifier angle, degrees | 0.000070 / 0.000072 | 0.000150 | 0.0001405 |
| Staggered mode-transfer rectifier angle, degrees | 0.000060 | 0.000140 | 0.0001247 |
| Half-interval severe-fault rectifier angle, degrees | 0.002400 | 0.002700 | 0.002520 |
| Deep-fault rectifier voltage during blocking, pu | 0.000000012 | 0.000000020 | 0.0000000171 |

Other existing gates are unchanged. The complete long-run gates additionally
require peak voltage error below 0.000022 pu, voltage NRMSE below 0.0005, peak
raw bus-angle error below 0.006 degrees, angle RMSE below 0.003 degrees, and
converter P/Q peak error below 0.0008 pu with NRMSE below 0.0002.

The expanded model/adjacent matrix passes 456 tests with no failures, errors or
skips. This includes initial physical-load separation, rejection of unqualified
controlled sources, both integration methods and adjacent compressor initial
protection behavior. Of 138 prior complete trajectory exports, 92 remain
byte-identical; the 46 changed exports are startup-transfer profiles. Full
repository/system suites were not rerun.

Full CDC6T acceptance remains open. At quarter-cycle intervals, the largest
voltage-transducer differences still reach about 11082 V under modified Euler
and 1039 V under staggered integration. These method/event differences are
reported in complete channel metrics rather than hidden by terminal acceptance.
Other source families, active source controllers, remaining protected variants,
strict state qualification and automatic production attachment need further work.

Private paired source-power captures:
`verification-artifacts/cdc6t-long-power-20261102/captures.zip`, SHA-256
`5d39961f2d005a44d41ce075c8c5b440260d3feff108b90a7734692f9e1e42f3`.

Complete comparisons, uncorrected controls, temporary diagnostic rebase,
failed-check logs, final reports, source snapshots and integrity manifest:
`verification-artifacts/cdc6t-initial-balance-20261102/validation.zip`, SHA-256
`ca119ac9971abc467b34993041ebe163f87b2bb5ccd10ef53ec39068339ef91a`.

## Governed classical sources and internal network boundaries

Classical sources with TGOV1 now participate in CDC6T initial operating-point
preparation through `InitialMechanicalPowerPreparation`. After the startup
control transfer settles electrically, shaft power is balanced and the governor
uses its existing initialization equations to reset its reference and storage.
This happens before physical initial actions. Running actions cannot rebase
shaft power. Other active source-control families remain unqualified.

Modified-Euler controller compilation now follows this final preparation, so
compiled bindings refer to the final governor blocks. The qualified classical
TGOV1 owner uses the existing optimized graph. Staggered integration binds those
same machine and governor states to its existing numerical lifecycle. Neither
path introduces a second set of governor equations. Compiled and generic
modified-Euler source trajectories agree within 1e-12 throughout the checks.

An internal converter action exposed a missing machine-input boundary. The
converter retained its left derivative, but classical machines immediately used
the changed electrical power. The core now provides a provisional notification
after a converged action changes injections, while bus voltage still represents
the left boundary. Staggered integration samples classical mechanical rates
using the machine's existing zero-time equations, restores scratch derivatives
and electrical power, and retains the sample only after the whole solve succeeds.
The next interval averages the left and right rates without replacing the
separate two-step history. Only exact qualified classical machine classes use
this behavior; controller/electrical-state event rules are unchanged.

The notification runs only on changed action passes. Reusable arrays are bound
once; ordinary network iterations do no additional derivative evaluation.
Multiple changes retain the first left boundary. External event averaging takes
precedence, and failed solves discard provisional samples. Tests cover callback
ordering, unchanged actions, the shared iteration budget, callback failure,
unchanged machine/governor storage and discarded pending history.

Paired independent moderate-fault captures use finite-inertia classical sources
with active TGOV1 governors. Both current and inverter-power control run for two
seconds at 0.5 ms and quarter-cycle intervals. Comparisons retain all raw rows,
event-side duplicates and clock overshoot, with no angle offsets or time shifts.

| Grid and control | Modified-Euler peak source-angle error, degrees | Staggered before boundary fix, degrees | Staggered after fix, degrees |
| --- | ---: | ---: | ---: |
| 0.5 ms, current | 0.001531 | 0.035521 | 0.001451 |
| 0.5 ms, inverter power | 0.006592 | 0.024967 | 0.006656 |
| Quarter cycle, current | 0.010970 | 0.283408 | 0.005737 |
| Quarter cycle, inverter power | 0.063964 | 0.203980 | 0.067451 |

At the first quarter-cycle endpoint after internal blocking, the current-control
source-speed discrepancy drops from about 1.97e-5 to 7.33e-9 pu. Worst speed
error over that complete profile drops from 3.58e-5 to 9.22e-7 pu. The remaining
power-control discrepancy develops later during recovery and is visible under
both methods; full CDC6T state and trajectory acceptance remains open. The
per-channel regression bounds in `Cdc6tGovernedSourceTest` preserve that
distinction rather than imply the strict full-model accuracy target is met.

Private paired captures: `verification-artifacts/cdc6t-governed-20261102/captures.zip`,
SHA-256 `d23ec47224466c084a37cb8ba9ceead9d22656f7f789e277a35cf030096ad9c1`.

Validation: 491 scoped plugin/model tests and 16 core network-lifecycle tests
pass with no failures, errors or skips. The matrix includes both integration
methods, compiled/generic governor parity, initial physical-action separation,
CDC6T protected/control variants and adjacent controller/HVDC checks. Full
repository and large-system suites were not rerun. The required core hook is
committed as `44ca49d85`.

Complete comparisons, before-change controls, failure and final logs, scoped
reports, source snapshots and an integrity manifest are retained in
`verification-artifacts/cdc6t-governed-20261102/validation.zip`, SHA-256
`e0abe94f2ed527116a5b20f02951d062a3e261893ce048cd1f292049363012a7`.

## Recovery completion preserves derivative history

Independent recovery-rate controls separate two effects: a floating-clock
boundary and an incorrect derivative-history reset. The probe now accepts
explicit voltage/current recovery rates without changing its existing defaults.
Paired captures retain the original source/governor setup and change only the
current ramp from 2.0 to 1.9 or 2.1 per second. Both integration intervals and
all reference samples are retained.

At 1.9 per second, recovery completes between grid points and both solvers
change mode on the same integration ordinal. Replaying the independently
recorded current-transducer state at that handoff reproduces the next state with
the ordinary two-step formula to 0.000003 A at 0.5 ms and 0.000039 A at quarter
cycle. Averaging the left and right derivatives instead creates an error above
0.2 A. This distinguishes a completed recovery program from blocking or release.

`ConvergedNetworkAction.requiresIntegrationInputDiscontinuity()` now separates
electrical equation changes from derivative-history changes. Unknown actions
retain the conservative default. A qualified CDC6T `RECOVERING` to `NORMAL`
handoff still requests a network solve, records both sides and commits or
discards transactionally, but preserves transducer and classical-machine
derivative history. Blocking, release and other mode changes retain their
previous discontinuity handling. The implementation reuses existing states and
buffers and adds no per-iteration allocation.

| Slower-current recovery | Modified-Euler peak source-angle error, degrees | Staggered before, degrees | Staggered after, degrees |
| --- | ---: | ---: | ---: |
| 0.5 ms | 0.009110 | 0.009054 | 0.001652 |
| Quarter cycle | 0.059928 | 0.056871 | 0.005710 |

This correction does not remove the separate clock discrepancy. The reference
uses an accumulated single-precision clock; at the original grid-aligned
endpoint its voltage factor is slightly below one. The local double-precision
clock completes that ramp one ordinal earlier. Increasing only the current rate
to 2.1 leaves the voltage ramp as the same limiting boundary and reproduces this
behavior. The test explicitly requires the one-ordinal difference and the raw
near-one reference factors. It does not shift, interpolate away or omit samples.

The earlier incorrect averaging partially cancelled that timing difference.
Correcting history therefore increases the original staggered power-control
angle peak from 0.006656 to 0.014069 degrees at 0.5 ms and from 0.067451 to
0.129181 degrees at quarter cycle. These boundary-aligned profiles remain
diagnostic controls, not strict full-model trajectory acceptance. Their raw
regression envelopes are updated explicitly:

| Source channel | Previous 0.5 ms bound | New 0.5 ms bound | Previous quarter-cycle bound | New quarter-cycle bound |
| --- | ---: | ---: | ---: | ---: |
| Rotor angle, degrees | 0.008 | 0.017 | 0.075 | 0.145 |
| Speed deviation, pu | 1.1e-6 | 2.1e-6 | 9e-6 | 1.8e-5 |
| Mechanical power, pu | 5.8e-5 | 4e-5 | 8.5e-5 | 1.45e-4 |
| Governor valve state, pu | 5.9e-5 | 4.7e-5 | 1.25e-4 | 2.05e-4 |
| Governor reheater storage, pu | 1.8e-5 | 1.2e-5 | 2.4e-5 | 4e-5 |

The slower-current staggered profiles instead require source-angle peaks below
0.002/0.007 degrees and speed peaks below 1e-7/1.1e-6 pu. All original
modified-Euler bounds remain unchanged; its 32 retained comparison exports are
byte-identical to the preceding checkpoint. New modified-Euler rate variants
retain their separate finite-stage envelopes.

Validation passes 502 scoped plugin/model tests and 17 core lifecycle tests,
including independent recurrence discrimination, paired complete trajectories,
generic/compiled equality, unchanged states at handoff, rollback and fresh
electrical solves without derivative reset. Full repository/system suites were
not rerun. Core contract commit: `ff422a744`. Full CDC6T state accuracy, other
protected variants and automatic attachment remain open.

Private paired captures: `verification-artifacts/cdc6t-governed-ramp-20261102/captures.zip`,
SHA-256 `8a27303c2a1f54b1ceb78cc02541c71ddb5dfd25f497e807346d95c4f2149c7f`.

Complete final comparisons, before-handoff controls, clock diagnostics, failed
and passing test logs, source snapshots and integrity manifest:
`verification-artifacts/cdc6t-governed-ramp-20261102/validation.zip`, SHA-256
`d162c97d924172648cace04e13b7609895a1bd09f8a631828c2d025abd65c775`.

## Atomic network-trial entry

The terminal owner validates the network stage, prospective protection clock and
pending corrector endpoint before changing network-trial bookkeeping. Invalid
entry previously left the trial marked open, preventing a valid retry; a
mismatched corrector also discarded its valid predictor protection history.
Rejected entry now preserves that history and leaves the owner available for a
matching retry. The coordinator and terminal owner share the same finite,
nondecreasing clock validation without allocating a history snapshot.

Regression controls cover unknown stages, nonfinite/backward times, a mismatched
modified-Euler corrector, and replacement of a rejected staggered endpoint. All
three controls fail against the previous implementation because valid retries
encounter a spurious nested-trial error. Normal entry, accepted histories and
electrical equations are unchanged. This lifecycle fix does not qualify the
remaining strict state trajectories, other protection variants or automatic
model attachment.

Validation passes all 278 scoped CDC6T checks. The three added regression tests
fail against the original runtime; after the fix, all 446 generated CSV outputs
match the baseline byte-for-byte, including complete both-method coupled
trajectories and error tables. Existing comparisons that report reference
discrepancies diagnostically retain that status; passing this suite does not
promote those diagnostics into trajectory acceptance.

## Bypass observation-stage counterexamples

New cold pairs change only bypass recovery delay or recovery voltage in the
base small-network source. Each pair has identical complete output. A separate
probe option sets the bypass recovery delay without changing the existing
defaults or conflating it with the inverter-block recovery delay.

| Source/control | Network interval | Recovery delay | First timer observation | Release |
| --- | ---: | ---: | --- | --- |
| Base power source | 0.5 ms | 10.4 ms | first endpoint after clearing | 0.111000136 s |
| Base power source | 0.25 ms | 10.4 ms | first endpoint after clearing | 0.110749461 s |
| Base power source | 0.5 ms | zero | release at first endpoint after clearing | 0.100500114 s |
| Base power source | 0.25 ms | zero | release at first endpoint after clearing | 0.100249596 s |
| Existing transferred source, all three control modes | 0.5 ms | 10 ms | clearing event itself | 0.110000134 s |

For the nonzero-delay base profiles, the deadline is the first observation time
minus half the network interval plus the entered delay. Zero delay still waits
until the following endpoint. Changing only the base recovery threshold from
0.92 pu to 0.90 or 0.93 pu leaves that schedule unchanged, although the converged
clearing voltage already exceeds each threshold. This excludes a simple rule
based only on the recorded converged voltage and delay.

The transferred-source captures provide a counterexample: their recovery timer
is already published on the second clearing observation, with a deadline of
clearing time minus half an interval plus the delay. This occurs in current,
inverter-power and rectifier-power control, so the control selector alone does
not explain the difference. All original timestamps and duplicate event sides
remain present. These observations do not identify the reference implementation's
internal event-solve measurement or call sequence.

A trial implementation that applied completed-interval sampling universally to
bypass recovery was rejected. It delayed the already qualified transferred-current
release and raised its event-side rectifier-voltage error to about 0.142 pu,
violating the unchanged 0.000034 pu gate. The trial patch and failing comparisons
are retained privately. Production protection equations and scheduling remain
unchanged; neither a universal midpoint rule nor an added fitted delay is
justified. The next causal discriminator must isolate the source-preparation
and event-solve boundary before changing accepted protection history.

`Cdc6tBypassSamplingReferenceTest` checks the paired captures, exact timer
relationships, zero-delay behavior, unchanged states between release sides and
the transferred-source counterexamples. These are independent observation
contracts, not complete runtime trajectory acceptance.

All 285 scoped CDC6T checks pass after restoring the original runtime, including
the seven new reference-contract checks. Existing strict limits are unchanged.

Private complete paired captures:
`verification-artifacts/cdc6t-bypass-sampling-20261104/captures.zip`, SHA-256
`b6fb32f608e3559068910af93bbbb0cc53bf326f95fd8558ad28d2386fc3cba5`.

Rejected candidate patch, complete comparison samples, test logs/reports and
source snapshots:
`verification-artifacts/cdc6t-bypass-sampling-20261104/validation.zip`, SHA-256
`38edd35eb05a5c75431a86bbffff5d8a241d8bb99aa3388ef4493c4c5616ab15`.

### Network-convergence discriminator

The probe can now set network tolerance independently, preserving the original
1e-6 default. With the base source, unchanged physical parameters and a 0.5 ms
interval, cold pairs at 1e-4 and 1e-7 both pass the original capture-quality
checks and retain the same delayed timer publication. Both event-side clearing
samples still have no recovery timer; the next endpoint publishes the deadline
at clearing time plus half an interval plus the entered delay.

A 1e-8 attempt changed that observation schedule, but reported repeated network
nonconvergence starting during startup. Its manifest is explicitly
`rejected-quality`, the cold pair was stopped, and it is excluded from numerical
accuracy acceptance. A failed solve cannot establish a causal timing rule or
justify changing production protection history. The two valid convergence
controls therefore do not resolve the source-dependent observation discrepancy.

The installed model data sheet defines the recovery voltage threshold and delay,
but does not specify the internal callback sequence responsible for these
observations. Production equations and timing remain unchanged. Tests verify the
valid paired clocks and prevent the rejected attempt from being treated as an
accepted reference.

Private convergence captures, including the rejected attempt and its log:
`verification-artifacts/cdc6t-bypass-convergence-20261104/captures.zip`, SHA-256
`f3ad33a2cc827504c9c00822ef6d538461a7c018466aca625e50a9831afa33e3`.

### Static inverter preparation changes the recovery observation side

Paired small-network controls retain identical source RAW, dynamic records,
fault, 0.5 ms interval and bypass settings. Only the static inverter-angle
record differs across the three profiles. Every output row repeats byte for
byte in its cold run, both clearing sides are retained, and all six runs pass
the initial-conditions and network-convergence checks. All three continuous DC
states are unchanged between the two clearing observations.

| Static inverter angle | Clearing-right inverter voltage (pu) | Timer on clearing right | First timer observation |
| --- | ---: | --- | ---: |
| Source RAW setting | 0.932210 | absent | 0.100500 s |
| 20 degrees | 0.936949 | absent | 0.100500 s |
| 15 degrees | 0.940745 | present | 0.100000 s |

The entered recovery threshold is 0.92 pu, so every published clearing-right
voltage exceeds it. The 15-degree control starts its recovery deadline on that
event side, while the other two start at the following endpoint. The 15-degree
deadline equals clearing time minus half an interval plus the entered delay;
the other deadline equals clearing time plus half an interval plus the delay.
The same protection trigger with the source RAW angle retains the later schedule.
Changing only the dynamic angle, static rectifier angle minimum or static DC
tap adjustment in exploratory runs also retained the later schedule. Thus the
earlier base/transferred discrepancy is sensitive to the static inverter
operating point, and the published voltage alone does not reveal the event-solve
observation that made the decision. An intermediate or internal measurement
must be identified before changing the production recovery clock. No timing
rule or tolerance was changed.

`Cdc6tBypassSamplingReferenceTest` pins the complete cold pairs, unchanged
dynamic records, one changed static converter record, event-side voltages,
continuous states and timer publication. Private inputs, outputs, settings and
source scripts are in `verification-artifacts/cdc6t-static-gamma-event-20261105/captures.zip`,
SHA-256 `2829705bf4c0182539f961cdb937571fe0d1616dd22d6747cbcc3b20210b204a`.

### Initially binding current ceiling

An initial voltage-dependent ceiling below the solved static current now requests
an explicit operating-point transfer. Previously, only the constant-gamma transfer
requested this lifecycle: the runtime retained the old measured current while its
current injection immediately used the lower ceiling, allowing network compensation
to absorb the physical power change. Preparation now preserves the static injection
until compensation is captured, then solves the limited physical network and settles
all measurement states through the existing shared initial-endpoint contract.

The decision is made once during model preparation. Entered constants, static
schedule and protection recovery base are unchanged. An equal or inactive ceiling
does not request this transfer. Both modified-Euler and staggered integration use
the same prepared runtime; there is no additional per-step calculation.

Independent cold pairs cover a 900 A initial ceiling against a 1,000 A static
schedule, with flat and terminal-fault runs. Each pair retains all 1,005 samples
and event-side duplicates. Both methods initialize to 500,000 V / 900 A /
504,500 V with zero derivatives. The worst terminal-voltage difference decreases
from 0.018415 pu to 0.000005004 pu. Complete trajectories pass the existing ordinary
conducting per-channel envelope and an additional 1e-5 pu terminal-voltage gate.
The largest fault DC-voltage-state difference is 6.54 V with modified Euler and
1.37 V with staggered integration; flat DC measurement states match exactly.

All 295 model checks pass, including active/equal/inactive ceiling selection and
existing initial physical-event/source-compatibility checks. Removing only the
new preparation trigger reproduces five expected failures: four reference startup
checks and the active-ceiling preparation check. All 446 preceding CSV artifacts
are byte-identical between the controlled runs; only the eight new ceiling
trajectory/error files change. This qualifies the
initial ceiling preparation for these source/control fixtures, not arbitrary
startup transfers or complete protected trajectories. Source-dependent bypass
recovery timing, severe-fault intermediate feasibility, other equipment variants
and automatic attachment remain open.

Private cold-pair captures:
`verification-artifacts/cdc6t-initial-ceiling-20261104/captures.zip`, SHA-256
`9132db7969c3510d50e4efccf9152edcc8eebb5234e6d273dd0872393a54d5d5`.

Complete samples, controlled pre-fix comparison, source snapshots and test reports:
`verification-artifacts/cdc6t-initial-ceiling-20261104/validation.zip`, SHA-256
`bb809df9200422fa91e75a8fe7ea4606368131349a222c9d12aae52d9c71ef9a`.

### Zero-time continuation discriminator

A fresh cold pair inserts exactly one RUN continuation at the current time
immediately after fault clearing. Source preparation, model constants, network
interval and physical event times are unchanged from the retained base
noninteger-delay bypass control. Both new complete outputs are byte-identical
to each other **and** to that original uninterrupted recording: all 1,006 rows,
including every event-side duplicate. The added before/after snapshots retain
identical time, three states, three flags and all 18 published variables.
The continuation does not add an output row or advance the clock.

This rules out zero-time API entry alone as a sufficient cause of the delayed
recovery-timer publication. It does not establish that the native engine
actually invoked the protection model during that call. Production timer
origins, equations and integration history remain unchanged. The useful missing
evidence is still the voltage supplied to protection and the model evaluation
phase inside the clearing solve, rather than another recorded endpoint or a
new fitted delay. The published variable table contains timers and electrical
outputs but no such callback-stage sensing coordinate. Existing strict
trajectory failures remain open.

`Cdc6tCallBoundaryReferenceTest` checks the entire original-output hash and all
added snapshot coordinates; this is an observation/lifecycle discriminator,
not an accuracy acceptance test. Private complete paired archive SHA-256:
`137a2a509fb94fadeda7c9ffd96abbd15c1a0c3d01549bf09e8fd39fbe2c50eb`.


## Overlapping terminal blocking causes

Independent controls apply a first terminal fault from 0.05 to 0.10 seconds
and an opposite-terminal fault from 0.07 to 0.16 seconds. Both blocking causes
are enabled and bypass is disabled. Both application orders have exact cold
repeat channel files with accepted initialization. A rejected harness attempt
used an invalid remaining-fault index and is retained separately; the corrected
captures use the actual remaining fault index after the first removal.

For rectifier-first blocking, the link resumes current at about 0.110 seconds
while the inverter fault remains. Its inverter communication timer starts only
at that release, and current is blocked again at about 0.130 seconds. Final
recovery occurs near 0.1705 seconds. This contradicts an independently retained
inverter request starting during the rectifier block: the existing coordinator's
cause-specific suppression/reset is supported by this control. Inverter-first
blocking remains continuous until approximately 0.1705 seconds as the cause
transfers to the rectifier. No production change is justified.

`Cdc6tOverlappingProtectionTest` reproduces both current transition sequences
under both integration methods, checks blocked terminal powers, and checks the
independent absence of an inverter timer during the first rectifier block.
The same sampled lifecycle envelope as the existing protected-network tests is
used. Every reference sample and event-side duplicate remains in the exported
comparison; full state and terminal-trajectory acceptance is separate.

The generic nearest-side diagnostic is ambiguous where internal transitions
and external events supply different numbers of same-time observations. Its
retained voltage maxima include approximately 0.1116 pu at the first internal
restart and 0.1857 pu at final rectifier-fault clearing. These numbers are not
claimed as aligned physical trajectory errors or removed from the evidence.
An event-owner-aware comparison is required before interpreting those rows as
full waveform agreement. No sample shifts, collapsed duplicates, adjusted
parameters or weakened existing accuracy gates are introduced.

Private cold captures and full Java comparisons are retained in
`cdc6t-overlap-20261104`. This closes the tested cross-cause lifecycle uncertainty,
not complete protected model qualification or automatic attachment.


### Event-owner comparison of overlapping causes

The overlap recorder now receives all four external event times and captures
immutable internal before/after transition boundaries through the existing
observer. Previously its two-event default omitted pre-event rows at 0.07 and
0.16 seconds, and internal transition observations were disabled. Enabling
these observations preserves every existing regular sample exactly; it adds
observations without changing the simulation.

`compare_cdc6t_overlap_owners.py` identifies external owners from the supplied
schedule and internal owners from the accepted transition callback. It pairs
reference rows only when the same integration interval has a unique owner and
matching side counts. It never chooses a side by numerical closeness. Coincident
external/internal events, multiple internal actions or unmatched observation
counts remain explicitly ambiguous, with candidate owners and a reason in the
export. Missing, extra and future observations have negative controls. All
1,011 reference rows remain accounted for; ambiguous rows receive no invented
numerical pairing. Therefore the classified subset cannot establish complete
trajectory acceptance.

For rectifier-first controls, 1,006 rows have unambiguous ownership and five
remain ambiguous; inverter-first has 1,002 classified rows and nine ambiguous.
Across classified rows, staggered terminal-voltage error stays below
0.00000749 pu. Modified Euler reaches 0.0000416 pu in the inverter-first control.
The previous large event-side voltage comparisons are not reproduced among
these independently paired observations. Real measurement-state differences
remain: maximum voltage-state errors are approximately 21.1/25.5 V under
staggered integration and 1,125/1,237 V under modified Euler for inverter-first /
rectifier-first respectively. Measurement voltage is in volts, current in
amperes, terminal voltage/power in pu and angle channels in degrees.

These are reported errors with incomplete event-owner coverage, not new or
relaxed accuracy limits. Raw nearest-side comparisons, every reference duplicate,
all actual regular samples and all internal transitions remain retained in
`cdc6t-overlap-owners-20261104` private evidence. Production code is unchanged.


The classifier rejects malformed channel/row shapes, nonfinite values,
nonmonotonic observations and clocks outside the existing 10-microsecond
half-second reference envelope. Integer ticks are an explicit sample-index
mapping, not a claim of exact clock equality: original times and their grid
residuals are exported. Actual regular rows and internal transitions that lack a
unique reference pairing are also retained with counts, identities and reasons.
Seven classifier tests cover these guards, multiple internal events and
unmatched actual observations; they do not replace physical accuracy gates.


## Finite commutating resistance

Explicit CDC6T preparation now accepts finite nonnegative converter resistance
for standard LCC equipment. Capacitor commutation remains rejected; automatic
model attachment remains unqualified. The shared electrical boundary adds an
overload while preserving its existing zero-resistance constructor and arithmetic.
No new differential state, integration history or per-step allocation is added.
Adjacent converter models retain their own preparation restrictions.

With `k = 3*N*X/pi` and `r = 2*N*R`, where R and X are ohms per bridge,
positive terminal DC magnitudes obey:

- Rectifier: `Vr = V0r*cos(alpha) - (kr + rr)*I`.
- Inverter: `Vi = V0i*cos(gamma) - (ki - ri)*I`.
- AC powers into the converters: `Pr = Vr*I + rr*I^2` and
  `Pi = -Vi*I + ri*I^2`.

The firing-angle interface uses signed bridge voltage
`Vbridge = V0*cos(alpha) - (k+r)*I`; the terminal orientation is applied only
when converting to terminal DC voltage. Its AC power is `Vbridge*I+r*I^2`.
Angle inversion, conducting ceilings and power evaluation use these same terms.
Reactive power retains the overlap integral at the corrected angle. Thus the
inverter magnitude equation reverses the resistive voltage-drop sign while
physical dissipation remains positive. The existing analytic zero-power reactive
limit is preserved.

Six independent cold pairs cover zero resistance, rectifier-only resistance and
inverter-only resistance at 0.5 ohm per bridge, each with flat and fault profiles.
The existing active current-ceiling curve is retained. Every pair has identical
channel files, accepted initialization and unchanged constants after their
recorded binary32 representation. The new zero-resistance flat/fault files also
match the earlier independent current-ceiling controls exactly. Fault controls
exercise the binding ceiling; recorded current satisfies its existing
0.000039 A envelope and total converter-plus-line loss satisfies the existing
0.000002 pu power-balance envelope.

The complete 364-test focused matrix has 363 passes, one failure, no errors or
skips. All measured-input converter P/Q/angle gates pass. Both methods retain
all 1,005 rows, including flat-profile continuation duplicates at 0.05 and
0.10 seconds. Eleven of twelve complete trajectory comparisons pass the existing
conducting envelopes. The rectifier-resistance modified-Euler fault comparison
still fails its 10 V DC-terminal limits: maximum rectifier/inverter errors are
10.1973/10.6411 V. Zero-resistance errors in that method are 8.6461/9.0384 V.
The failing comparison remains explicit; no thresholds or model parameters are
adjusted to compensate for it. All staggered finite-resistance trajectories
pass those unchanged envelopes. This is partial dynamic qualification, not
complete model acceptance.

Analytic tests cover signed/magnitude coordinate equivalence, positive losses
at either terminal, conducting feasibility, invalid-input atomicity and exact
zero-resistance arithmetic. Existing zero-resistance overlap samples and internal
transition snapshots remain exactly unchanged. Adjacent converter boundary,
coupled-runtime and preparation tests pass with their existing restrictions.
Private source, captures, full samples, classes and reports are retained in
`cdc6t-resistance-20261104`.

Combined integration with the adjacent converter's auxiliary-command extension
executes 120 checks: 119 pass, the same modified-Euler resistance comparison
fails, and there are no errors or skips. All 78 adjacent converter controls
pass. The failing DC-voltage values and all selected test identities match the
separate model validations. Actual XML, bounded output, source and settings
are retained privately in `hvdc-resistance-aux-integrated-20261104/validation.zip`,
SHA-256 `945e34277dda16d98aedc06f8d315cdaf2c836ec9e0693df350001f55bab2d01`.
This focused integration result does not replace full registered validation or
close the remaining independent trajectory gate.

### First-interval origin of the finite-resistance voltage gate

A retained-data audit localizes the modified-Euler DC-voltage peaks to the first
accepted interval after fault application: reference time 0.0505000129 seconds.
The measurement states are continuous across the two fault-application samples.
The preceding and accepted-left derivatives are zero. Using the independently
established event-rate average and separate older two-step history,
`f_event=(f_left+f_right)/2` and
`x_next=x_event+H*(3*f_event-f_previous)/2`, predicts the reference's next
measured-voltage states within their binary32 observation bound. Here this
reduces algebraically to `0.75*H*f_right`; that coefficient is derived from the
existing integration contract, not fitted to the trace.

For the inverter-voltage measurement, zero resistance gives a reference
increment of -121.25 V and a modified-Euler increment of -161.0586 V. Rectifier
resistance increases those increments to -136.875 V and -181.8149 V. The same
method difference therefore grows with the physical fault-input jump. The
resulting modified-Euler terminal errors grow from 8.6461/9.0384 V to
10.1973/10.6411 V. The staggered rectifier-resistance trajectory remains within
2.3741/2.3985 V for the two DC-terminal channels.

`cdc6t_resistance_first_interval_audit.py` checks the pinned full evidence and
retains both event sides, preceding and following observations, original clocks,
step sizes, evaluation inputs and derivative-history reconstruction. Six
method/profile diagnostics pass without another simulation. The actual
modified-Euler predictor-stage boundary is not present in these exports; its
Heun contract is covered by the existing runtime tests, not reconstructed from
endpoint values alone. This supports a method-origin explanation for this
selected first interval. It does not qualify every later observation or close
the unchanged 10 V acceptance gate. No production correction is justified by
this discriminator.

`Cdc6tMeasurementsTest.faultBoundaryRetainsModifiedEulerPredictorCorrectorEquation`
now makes that method distinction executable. It holds the continuous state
through an input jump, checks the right-side predictor and corrector separately,
and compares their first-interval result with the independently observed
left/right-rate two-step rule. The focused measurement suite passes seven tests.
The separate full resistance comparison retains its strict 10 V gate and its
known modified-Euler fault failure. An accepted production change needs a
predictor-stage boundary capture or another independent discriminator showing
an incorrect event handoff within the selected integration method.

### Current-limit voltage selection

The normal runtime currently evaluates the DC current-limit curve using the
measured inverter DC voltage. A supplied DC-model overview describes a
lower-of-measured-rectifier-and-inverter selection in a different model dialect.
The exact-model reference discriminator selects the inverter measurement.

`Cdc6tVdcolDiscriminatorTest` establishes a small-network stimulus for an
independent comparison. The measured rectifier voltage falls about 6.83 kV
below the inverter measurement. Across all 82 samples where the candidate
orders differ by more than 1 A, the reference current order matches the
inverter-measurement rule within 0.00005 A; the other rule differs by at least
1 A and up to 48.28 A. Two complete reference captures are byte identical,
including both sides of each event. Their inputs and manifests are retained in
the private SHA-pinned evidence archive. `Cdc6tVoltageCurrentLimitTest`
checks every distinguishing row, and `Cdc6tVdcolDiscriminatorTest` exercises
both integration methods. The existing production selector is accepted for
this regime; other CDC6T qualification work remains separate.

### Current-margin crossover discriminator (not qualified)

The probe now accepts a dynamic rectifier minimum angle independently of its
static setting; the existing default remains five degrees. A synthetic
constant-gamma/current-order discriminator used a 15-degree dynamic rectifier
minimum, a 20-degree static minimum and a 0.1 current margin, with fixed taps.
Its purpose was to distinguish a characteristic intersection from an immediate
transfer to the lower current order.

The independent run failed network convergence starting at initialization and
reported 1,007 nonconverged solves. Its manifest is `rejected-quality` despite a
separate successful initial-conditions message. The retained complete samples
are diagnostic only; the cold repeat and fault expansion were stopped. No
production equation, margin-selection threshold or acceptance gate changed.
The existing valid current-margin recovery capture does not contain a sample
with both minimum-angle characteristics active, so it cannot independently
qualify a new crossover branch either. A converged independent discriminator
or an exact model-specific characteristic definition is still required.

The probe now permits an explicit current order and up to three unchanged
load-flow passes so that a near-converged first pass is not mistaken for an
infeasible operating point. At a higher order, the second pass met the same
load-flow tolerance and the complete fault run finished; two cold captures
match byte for byte over every sample and event side. Delivered current stayed
at the requested value throughout, so this valid control never entered the
characteristic crossover. A still higher order did not converge after three
passes. These runs bracket this small network's usable range but do not
qualify a new current-margin equation. The complete valid pair is retained at
`verification-artifacts/cdc6t-current-order-bracket-20261105/captures.zip`,
SHA-256 `f72b27f6db37309cce87fe18b700f056fb6873de8c7fbbcccf109d54b854f951`.
`Cdc6tVoltageCurrentLimitTest` preserves its source quality and nonbinding
current-order result.

A second converged, cold-paired control lowered the scheduled DC voltage while
holding the current order and model constants fixed. Its delivered current fell
below the request, but all recorded current orders follow the entered
voltage-dependent ceiling within `0.000062` A. The rectifier angle remains
more than 40 degrees above its entered minimum, and setting current margin to
zero leaves the complete trace byte-identical. This is a valid limiting
profile, but it exercises the ordinary voltage-dependent ceiling rather than
the minimum-angle/current-margin crossover. The source pair and zero-margin
control are retained at
`verification-artifacts/cdc6t-margin-vdcol-negative-20261105/captures.zip`,
SHA-256 `cfd01930af03dc83b133ee4bb280343d7c2123c44acb95aaed1f3452c3b277b1`.

A converged minimum-angle control now isolates the actual current-margin
transfer. At fault application the accepted rectifier angle reaches its
entered five-degree minimum and the current order changes from 1,000 A to
900 A; it returns to 1,000 A at clearing. The zero-margin control holds
1,000 A through the fault. Cold repeats are byte-identical and the complete
event-side capture is retained at
`verification-artifacts/cdc6t-rectifier-margin-crossover-20261105/captures.zip`,
SHA-256 `c64c8cd24a846eb55b69052c92717e09b34f23815c411abb8f981445dfc7c386`.
`Cdc6tRectifierMarginCrossoverTest` pins these source transitions and runs
the coupled SMIB comparison under both integration methods. Both bus-voltage
channels pass the earlier strict limits. The earlier state limits remain
unmet: modified Euler peaks at about 146 V in the first fault interval and
0.247 A after clearing, while staggered integration peaks near 2.17 V.
The full crossover trajectory is therefore still unqualified; no margin
equation, integration stage, default or tolerance changed.

Private failed capture, exact probe inputs and logs:
`verification-artifacts/cdc6t-margin-intersection-20261105/rejected-capture.zip`,
SHA-256 `acf0eed717238ec030691bcdeb37d08b8a5bd6a38cce3ae41ee887db140a31ad`.
