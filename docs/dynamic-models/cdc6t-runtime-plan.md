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

The component currently requires explicit nonnegative delays. Interpretation of
negative source sentinel delays, inverter-origin blocking, bypass, concurrent
protection causes and full event ownership remain to be qualified before import
and attachment can use it. Its API is shared by integration methods; complete
coupled verification under both methods remains outstanding.

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
