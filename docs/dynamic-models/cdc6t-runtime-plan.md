# CDC6T implementation contract

The source record starts with a quoted DC-link name, followed by the model name
and 32 constants. It has no generator identifier or bus-number prefix. The
record reader now preserves that name and the constant order, including names
containing commas, escaped quotes and slashes. A quoted numeric name remains a
link name. Adjacent machine records are retained.

Automatic import provides record-decoding support only. Strict import continues to report CDC6T
as unsupported, rather than allowing an incomplete dynamic network to appear
fully supported. The typed conducting runtime described below is tested separately;
it does not establish complete model support.

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
recovery or command limits. Only positive measurement time constants are admitted at this
stage; zero-time handling requires its own source and reference qualification.

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
Automatic attachment, constant-gamma variants and complete protected trajectory
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
inverter angle limits select constant-gamma control; this variant is rejected
because it must not silently use the ordinary dynamic inverter angle.

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
step shrinks its time contribution, not its amplitude. The short mode-return
peak likewise remains near 0.002306 pu at an internal transition with duplicate
reference observations. Our recorder does not yet expose both observations of
that internally generated transition. This is distinct from the bypass's
one-interval release difference. No duplicate rows, event windows, or channels
are removed to obtain these metrics, and no trajectory is shifted.

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
