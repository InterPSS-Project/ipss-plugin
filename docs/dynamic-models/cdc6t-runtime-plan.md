# CDC6T implementation contract

The source record starts with a quoted DC-link name, followed by the model name
and 32 constants. It has no generator identifier or bus-number prefix. The
record reader now preserves that name and the constant order, including names
containing commas, escaped quotes and slashes. A quoted numeric name remains a
link name. Adjacent machine records are retained.

This is record-decoding support only. Strict import continues to report CDC6T
as unsupported, rather than allowing an incomplete dynamic network to appear
fully supported. No controller, network injection or integration support is
claimed by this change.

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

These tests validate the reference contract only. CDC6T remains unsupported by
the Java dynamic runtime. Implement and compare the controller, state/history
integration and network coupling before changing that status.

## Measurement component

`Cdc6tMeasurements` implements the three first-order voltage/current measurement
equations with shared primitive state, input and derivative buffers. It provides
explicit modified-Euler predictor/corrector stages and the common staggered
participant interface. Initialization sets measurements from the solved boundary;
reading diagnostic maps does not advance history. Predictors can be cancelled,
zero-duration local steps do not advance state, and nonfinite updates fail before
changing stored coordinates. Mixed local/staggered trial ownership is rejected.

Analytic exponential tests verify second-order convergence for local modified
Euler and accepted two-step rates. This component is not yet attached to a DC
network device, and does not implement blocking, bypass, recovery or current
command limits. Only positive measurement time constants are admitted at this
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

This component requires strictly increasing voltage coordinates and nondecreasing
nonnegative current coordinates. Unordered or coincident input points are not
silently reordered. Their source-specific interpretation remains to be qualified,
including the unordered plateau points in the earlier baseline fixture. The
restart minimum, recovery ramps and blocking/bypass overrides are not part of
this normal-operation ceiling. Neither component is yet attached to a runnable
Java DC-link device, so full CDC6T support remains unavailable.

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
