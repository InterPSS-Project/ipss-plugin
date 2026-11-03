# CHVDC2 named-link preparation and shared components

The named-link reader now recognizes standard `CHVDC2` and legacy
`USRDCL` / `CHVDC2U1` records. It preserves their distinct identities, quoted
link names, source locations and original parameters. The legacy allocation
header is checked before reading the flags and constants. Invalid records are
rejected with source locations; correctly decoded records remain explicitly
unsupported for automatic dynamic attachment.

`Chvdc2Parameters` owns immutable source arrays and named constant slots.
Validation checks finite values, documented positive quantities, nonnegative
times/physical line coefficients, ordered limits and voltage breakpoints.
Zero-valued optional coefficients and the nonpositive commutation-failure
disable setting are preserved. No timestep correction or dialect alias is
silently applied.

## Shared electrical component

`Chvdc2DcLine` implements the physical DC T-circuit using SI units. With line
capacitance present, its coordinates are rectifier current, capacitor voltage
and inverter current. With zero capacitance it stores one series current.
With line dynamics disabled, current is evaluated algebraically from the latest
terminal voltages.

For positive capacitance the equations are:

```text
dIr/dt = (Vr - Vc - R*Ir/2) / (L/2)
dVc/dt = (Ir - Ii) / C
dIi/dt = (Vc - Vi - R*Ii/2) / (L/2)
```

For zero capacitance, `dI/dt = (Vr - Vi - R*I)/L`. These equations satisfy
terminal input power minus resistive loss equals the rate of stored magnetic
and electric energy. The source diagram's printed first-order gain requires
care: a resistance-valued current/voltage gain is dimensionally inconsistent.
The implementation uses the physical circuit equations above; exact native
line-state equivalence remains a separate qualification requirement.

Effective inductance includes entered line/smoothing inductances and the
documented commutation contribution. Millihenries, microfarads and commutation
reactances are converted once during preparation. Coefficients remain fixed
across local integration stages. The network-interval overload described below
implements source capacitance suppression. Additional fast-time preparation and
local-step selection are not inferred from that suppression decision.

Modified Euler and shared staggered integration use the same state storage and
RHS. Numerical arrays are reused. Explicit prediction cancellation and ownership
guards prevent mixing a pending local predictor with shared integration.
Measurements refresh at network trials without advancing states. Named-state
maps and derivative snapshots are optional observations outside the hot path.

## Evidence and remaining work

The synthetic reference probe loads both record forms with the expected
allocation. The original coupled profiles fail native initialization and are
retained as rejected diagnostics, not trajectory acceptance. Fixed inverter
gamma and current-control discriminators did not resolve that failure. A
captured pre-start converted boundary retains its solved DC operating point;
the large deviation appears during dynamic startup.

A separate stiff-source network, using unchanged controller constants, passes
native initialization. Both record forms have repeatable flat controls; the
legacy form also has repeatable rectifier-fault and inverter-fault captures.
The earlier direct-VAR modulation command was overwritten by the reference
model: its auxiliary input remained zero. That capture is a negative control,
not evidence of auxiliary-path excitation. The probe now rejects unexcited
modulation attempts explicitly. All channels and event-side duplicates are preserved.
This provides usable independent evidence without claiming that the original
coupled startup failure is fixed. The probe selects this control explicitly
with `--network stiff`. Full controller/network equivalence is not established
by reference generation alone.

Component tests cover exact record fields and malformed input, source-array
ownership, integration lifecycle, analytic second-order convergence of RL and
RLC responses under both methods, instantaneous energy balance, algebraic
voltage refresh and coefficient units. These do not establish complete SMIB
qualification. The public [HVDC benchmark report](https://restservice.epri.com/publicdownload/000000003002010755/0/Product)
provides an additional independent test-system specification for that next step.

Still required: exact legacy auxiliary routing; complete current-controller
trajectory qualification, VDCOL, calculated inverter angle bounds and RAML;
commutation-failure histories; line-state/source preparation equivalence;
network ownership and attachment; complete both-method SMIB states, boundary
trajectories, events and longer-duration gates. Neither form is classified as
loadable or verified by this change.

Exact source provenance, rejected captures, valid stiff-source captures and
validation evidence are retained in the private `chvdc2-foundation-20261102`
evidence directory. The rejected and valid reference archives are separate.

## Shared current-controller component

`Chvdc2CurrentControls` supplies four primitive numerical coordinates: rectifier
PI integral, rectifier firing angle, inverter PI integral and inverter firing
angle. These map to source states 4, 6, 5 and 7 respectively. It shares one RHS
and reusable state/rate buffers between modified Euler and the staggered
participant contract. Parameter reciprocals are prepared once; warmed component
tests observe no per-step allocation. It is not yet attached to the network.

Measured currents are on rated-current base. Current orders and auxiliary
inputs enter in amperes and are converted once per input refresh. The inverter
error includes the entered current margin. For either terminal the PI output
is bounded before calculating the firing-angle lag error; that error is rate
limited before division by the angle time constant. Both integrals enforce
their position limits with outward derivative blocking. Tests distinguish
this sum clamp from clamping only the PI state or final firing angle.

Input and calculated-bound refresh do not commit states. The caller owns
measurement filters, current orders, auxiliary routing and calculated angle
bounds. With RAML disabled, the rectifier uses its entered minimum; the RAML
diagnostic VAR is not a replacement for that minimum. Integration ownership
guards reject shared stepping during a pending local predictor. Initialization
uses solved angles without cancelling a nonzero measured current error.

### Independent replay and precision discriminator

Twelve stiff-source fault profiles cover both terminals, nominal parameters,
narrow rectifier angle limits and active rate limits at 0.5 and 0.25 ms. Each
was cold-generated twice with identical complete CSV output. All source states,
boundary channels, startup rows and both event-side duplicates remain in the
immutable private `chvdc2-controller-20261102/captures.zip` archive. The archive
hash is enforced by the registered replay test. Entered and initialized
constants are checked for equality.

Each profile is replayed through both methods using the recorded measured
currents, current orders and inverter angle bound. This is a prescribed-input
component experiment, not a coupled simulation. The provisional 0.002-degree
per-coordinate gate remains unchanged: all 24 full replays fail on rectifier
coordinates. Inverter coordinates remain below that gate. The worst rectifier
PI error is 0.014922 degrees; halving the step increases several discrepancies.

The reference rectifier PI state has long stationary intervals despite a
nonzero input error. Most increments occur in multiples of ten binary32 ULPs.
A separate diagnostic applies the published PI equation with ten binary32
sub-updates and linearly interpolated recorded measurements. Across all twelve
profiles it reproduces the reference PI within eight ULPs (the largest observed
error is 0.000026703 degrees); a single binary32 update remains a negative
control. This strongly supports an internal stepping/roundoff explanation,
but does not establish the full reference execution algorithm or validate
unobserved internal inputs.

Production precision, entered gains and integration equations are unchanged by
this diagnostic. Neither the precision discriminator nor passing component
tests replace the unresolved full-replay gates. Complete attachment, all
controller/protection states, coupled SMIB trajectories and long-duration
qualification remain required.

## Shared measurement and current-order component

`Chvdc2OrderControls` implements the measured DC currents and AC voltages,
asymmetric DC-voltage filters, piecewise DC VDCOL curve, optional AC-voltage
current-limit integrators, inverter order/error lags and calculated inverter
maximum firing angle. Its ten coordinates correspond to source states
0, 1, 2, 3, 8, 9, 10, 11, 12 and 13. Both integration methods use the same
primitive state buffers and RHS; reciprocals, unit conversions and the VDCOL
segment slope are prepared once. Warmed state and output kernels allocate no
per-step objects in the component test.

Terminal currents and orders enter in amperes, DC voltages in kV, and AC
voltages on bus base. The entered ratings establish DC current/voltage pu.
The inverter angle calculation scales its measured AC voltage by converter
transformer ratio divided by tap, then applies the documented denominator
floor and cosine limits. The error lag excludes the inverter current margin;
that margin belongs to the downstream current PI.

The AC current-limit states initialize at their upper limits. The prepared AC
voltage reference is the minimum of the entered reference and the two initial
terminal voltages, with entered data unchanged. The low selector combines the
DC curve, current reference and enabled AC limit separately for each terminal.
Upward and downward DC-voltage filtering use their respective entered times.

Fully zero-time measurement/order filters expose algebraic observations without
committing numerical storage. A zero down-time does not remove the asymmetric
DC filter's dynamic coordinate: its positive up-time still owns state. Its
published state therefore remains stored until an explicit integration/apply
operation accepts the downward projection. Predictor cancellation restores
numerical storage; input refresh and getters cannot perform that commit.

### Independent order-control checks and remaining timing work

Separate cold-repeated captures activate DC VDCOL and both AC-voltage limiters.
Zero-time filter captures preserve their entered zeros through native
initialization. These extend the earlier nominal and PI-limit profiles to
twenty-two complete recorded-boundary controls. Every sample/channel and both
event-side duplicates remain in the private archives; reference hashes and cold
equality are enforced by the registered test.

The analytic unit checks pass for both methods, including exponential
convergence, asymmetric voltage filtering, piecewise current orders, active
AC limits, algebraic observations, invalid-input atomicity, predictor ownership
and warmed allocation. Thirteen of the forty-four full prescribed-input
replays pass all unchanged channel gates. This includes both methods for the
rectifier zero-filter fault and staggered integration for the inverter
zero-filter fault. The latter modified-Euler run retains a maximum DC-filter
voltage discrepancy of 0.00013113 pu against the 0.0001 pu gate.

Finite-lag profiles still expose reference timing and precision differences.
In the active inverter DC-limit control, applying the documented static curve
to the reference's same-row voltage-filter state differs from its recorded
current-order VAR by up to 50.33 A at 0.5 ms, and 25.85 A at 0.25 ms. Thus those
two channels cannot be assumed to represent the same internal evaluation
stage. A separate source-only diagnostic retains this distinction and checks
the initial static bases. Observation-stage ownership still requires
qualification; no production time shift is applied to improve the comparison.
Full replay errors remain recorded without relaxing gates or changing precision.

This component does not supply RAML, commutation-failure histories, converter
network injection or complete model attachment. Coupled SMIB, local substep
and observation-stage qualification remain necessary. The private
`chvdc2-orders-20261102` archives retain the active-limit and zero-filter
captures separately.

## Signed conducting-converter boundary

`LccConverterBoundary` prepares bridge/transformer geometry once and exposes
the firing-angle electrical boundary needed by the shared CHVDC2 runtime.
For positive line current, its terminal voltages are

```
Vr = E0r * cos(alphaR) - Rcr * Ir
Vi = -E0i * cos(alphaI) + Rci * Ii
```

Here `E0` includes the live AC voltage, bridge count, transformer ratio and tap;
`Rc = 3 * bridges * Xcomm / pi`. AC active power into each terminal is `Vr*Ir`
and `-Vi*Ii`. Signed DC voltage allows temporary rectifier inversion. The
overlap equation supplies reactive power and inverter extinction angle. An
infeasible overlap is rejected; the conducting kernel does not invent a
commutation-failure or blocking circuit.

The existing `Cdc6tConverterBoundary` delegates its unchanged positive-voltage
API to this shared kernel. Both interfaces use the same reactive-power equation,
including the analytic cancellation that keeps reactive power finite at zero
DC active power. All numerical calls use caller-owned buffers, validate before
publishing outputs, and refresh voltage-dependent quantities at every call.
No controller state, clock, history or integration step is advanced here.

The bridge-plus-inductive-line test feeds trial line current back through both
converters at each integration stage. Modified Euler and staggered integration
both converge at second order to the independent RL analytic response. The
test also checks instantaneous AC active-power balance against line resistance
and the derivative of stored magnetic energy. Signed voltage, zero-current,
zero-active-power, lossless power factor, infeasible-trial atomicity and warmed
allocation checks pass. Existing current-order converter reference gates remain
unchanged and pass after sharing the equations.

### Independent electrical observations

All complete samples from the earlier cold-repeated controller/order captures
are evaluated with recorded firing angles, currents and terminal voltages.
Initial boundaries at both terminals pass, as do rectifier P/Q trajectories
under the 0.00001 pu gates. The full comparison files retain both startup
history and fault-side duplicate rows, including the unresolved inverter errors.

The instantaneous-angle comparison shows that inverter reference channels do not always satisfy simultaneous
`P = -Vi*Ii`: the largest same-row discrepancy is approximately 0.38334 MW.
The firing-angle kernel's largest recorded-input inverter P/Q differences are
approximately 0.38331 MW and 0.54103 Mvar. This is evidence that channel
publication and network evaluation stages require investigation; it does not
establish the correct coupled trajectory. The accepted-extinction-history
experiment below resolves the measured-input AC power discrepancy through an
explicit evaluation/commit order. Complete coupled SMIB acceptance remains open.

Targeted protection probes now exercise RAML and commutation-failure settings
with explicit fault reactance and static extinction angle recorded in each
manifest. The RAML controls cold-repeat at both reference steps. The initial
high-threshold commutation profiles lose native network convergence. A separate
default-threshold control with lower initial extinction margin also activates
commutation failure but loses network convergence later. All are retained as
rejected evidence in the private `chvdc2-converter-20261102/protection-captures.zip`
archive, alongside the complete valid RAML controls. These observations do not
yet qualify a production protection state machine or automatic attachment.

The focused electrical-boundary run passes all 45 checks. The registered suite
runs 4,413 checks with the same 115 pre-existing failures, no errors and 13 skips;
failure identities match the retained preceding checkpoint. The core-plugin
module runs 486 checks with no failures or errors and four skips. The private
`chvdc2-converter-20261102/validation.zip` retains executable/source hashes,
complete comparison traces, logs and reports. These regression results preserve
the unresolved model gates rather than treating them as accepted behavior.

## Accepted protection and extinction-angle history

`Chvdc2Protection` adds one shared RAML washout coordinate, prepared reciprocal
time, bounded minimum-angle output, and explicitly committed RAML and
commutation-failure histories. Modified Euler and staggered integration use the
same scalar RHS. The numerical coordinate is the washout lag; after the RAML
switch releases, that memory decays while the output returns to the entered
minimum. Entered settings remain unchanged when preparing the RAML voltage
threshold from the initial rectifier voltage.

The owner supplies filtered rectifier voltage, terminal inverter voltage and
the newly calculated extinction angle. Only `acceptBoundary(time, sequence)`
may update the protection modes or clocks. Sequence numbers distinguish accepted
events sharing a timestamp; repeated acceptance requires identical inputs.
Falling below the RAML threshold triggers its hold. Release requires both a
strictly expired hold and voltage strictly above the threshold. Commutation
failure uses a strict angle trigger, strict minimum duration and inclusive
voltage-recovery threshold; nonpositive failure angle disables it. One accepted
boundary makes at most one short/release transition.

Trial input refresh, derivative evaluation, zero-duration integration and
observation do not commit histories. A preallocated transaction checkpoint
restores states, inputs, flags, timestamps, accepted extinction angle and event
revision together after a rejected boundary. Predictor cancellation restores
the numerical coordinate. The event owner supplies a stored left-side RHS for
staggered discontinuities; this is distinct from the solver's older two-step
rate history. Named state/derivative/history maps allocate only when requested;
the warmed numerical and transactional update checks allocate no per-step objects.

### Inverter AC power uses committed extinction history

The retained controls identify a specific evaluation order: inverter AC power
uses the previously accepted extinction angle with the current terminal voltage
and current. The newly calculated extinction angle is then committed for the
next boundary. `acceptedInverterExtinctionDegrees()` exposes that history;
ordinary input refresh leaves it fixed through all network trials. Acceptance
advances it exactly once, including a separately accepted event-side boundary
at unchanged physical time.

This order reconstructs every inverter P/Q sample in the normal controller and
current-order controls under the unchanged 0.00001 pu electrical gates. The
same order passes those gates during the valid short-duration commutation
control under both integration methods. The contemporaneous-angle calculation
remains a negative control. There is no trace interpolation, timestamp offset,
changed model coefficient or fitted filter. These are recorded-input boundary
checks; the complete runtime still must calculate and own its fresh extinction
angle and network state.

### Independent protection checks and remaining qualification

Both methods reproduce every short/release flag and transition timestamp in the
cold-repeated valid 0.5 ms commutation control. That control uses an explicitly
entered short hold. The default-duration control remains nonconvergent even
with an explicit network ceiling of 50; its tolerance is unchanged. The refined
short-hold control also fails native network convergence. These failed runs
remain rejected, so neither default-duration nor refinement acceptance is claimed.

The valid source shorting flag sets line-side inverter voltage to zero while
retaining line current. Its recorded AC injection remains nonzero and agrees
with the adjacent AC source powers. Therefore a complete electrical owner must
qualify the distinction between the imposed line-side short and the accepted-
angle AC interface; resetting current or replacing AC power with zero is not
supported by these captures.

All four full RAML replays still fail the strict angle/state gates. The largest
washout-state discrepancy is about 0.71461 degrees and the sampled minimum-angle
discrepancy reaches 35 degrees around switch activation. At the source's
activation row the published filtered-voltage state has not yet crossed its
prepared threshold. This identifies a stage-ownership question; no anticipatory
threshold, fitted delay or shifted trace is used to hide it. Both complete
samples and event-side duplicates remain in the comparisons. Joint controller,
protection and network execution, local substeps, RAML observation timing and
complete model attachment remain required.

The private `chvdc2-protection-20261102/captures.zip` retains the valid repeated
commutation control and the rejected iteration/refinement controls. The earlier
RAML archive remains immutable. Source/kernel fingerprints, complete replays
and regression reports are retained in the accompanying validation archive.

The focused protection/converter run executes 70 checks with four RAML replay
failures and no errors. The registered suite executes 4,452 checks with 119
failures, no errors and 13 skips: all 115 preceding failure identities remain,
and only the four explicit RAML replay gates are new. The core-plugin module
executes 486 checks with no failures or errors and four skips. These results
qualify the accepted-history and electrical reconstruction scope above; they
do not qualify the complete protected model.

## Joint controller state ownership and RAML limit separation

`Chvdc2ControlAssembly` connects the existing measurement/current-order, current-PI
and protection components directly. Its fifteen numerical coordinates follow
the source controller-state order. Modified-Euler prediction/correction and
the shared staggered participant use the same equations, dependency order,
prepared constants and underlying state storage. There is no second set of
controller equations, expression lookup or per-step object allocation.

The electrical owner supplies current, voltage, current reference, the common
auxiliary signal and fresh extinction angle. Current controls receive the
measurement and order values from the same numerical stage. A modified-Euler
corrector therefore sees predicted measurements rather than stale values from
before the predictor. State projection applies measurements and the RAML
coordinate before evaluating dependent firing-angle bounds.

Protection acceptance remains explicit. `acceptBoundary` accepts a converged
boundary; `evaluatePredictorProtection` evaluates a protection branch inside an
open predictor trial. Canceling that predictor restores its numerical states,
inputs, protection clocks and modes, accepted extinction history and event
revision together. A pending predictor does not publish its discontinuity
revision or fresh extinction angle to the electrical boundary. An independent
outer transaction can roll back completed local stages and a pending predictor.
Exact rollback bypasses limiter projection, since a saved state may precede
the first numerical application of a newly accepted bound. Trial state and
history diagnostics remain inspectable; observation does not accept a trial.

The assembly does not choose a default number of local steps or advance a
network clock implicitly. Its caller selects the integration interval and
acceptance sequence. Both-method analytic tests verify second-order convergence
of the connected measurement/current-integral equations. Separate tests cover
simultaneous correction, equilibrium, dependent-write failure rollback,
predictor and transaction ownership, moving bounds and warmed zero-allocation
updates.

### RAML changes the angle floor, not the PI memory

Independent RAML captures show the rectifier PI state remaining near 32.7 degrees
while the moving firing-angle floor rises above it. The PI continues to integrate
current error under its entered bounds. The firing-angle coordinate follows the
RAML floor. Applying that moving floor to both coordinates incorrectly rebased
the PI state by approximately seven degrees and retained that error after RAML
released.

`Chvdc2CurrentControls` now uses the entered rectifier minimum for the PI
coordinate and its anti-windup test. The moving RAML minimum still constrains
the PI sum sent to the angle lag and the firing-angle coordinate. The two cold-
repeated independent controls check the PI increments and the angle-floor
response; both integration methods also exercise activation and release in
focused equation tests. The inverter limits and entered constants are unchanged.

### Complete joint replays remain diagnostic

The joint reference tests evolve all fifteen controller states continuously
from initialization. They use recorded physical terminal inputs, held during
each explicitly configured ten-step local cycle, and retain every startup and
event-side sample. A candidate publication order records the final local
derivative evaluation before its state update; an event-left observation has
zero duration. No recorded controller state is reset after initialization.
This is a test of stage ordering, not an accepted network execution contract.

The PI-limit correction reduces the largest persistent rectifier PI discrepancy
from about 7.27 degrees to 0.00450 degrees. All four joint replays still fail
strict state/output gates. The first post-event voltage-filter state differs
from the reference, changing RAML pickup/release timing; at the coarser step
this produces a large one-sample firing-angle difference. The small PI remainder
also needs qualification without changing production precision. Internal
electrical-stage inputs, event integration and publication ownership must be
resolved before full coupled SMIB acceptance or automatic attachment.

The focused checkpoint executes 82 checks with 28 failures: 24 preceding
current-controller replay gates and the four new joint replay gates. The
equation, ownership, allocation and independent PI-limit controls pass. The
registered suite executes 4,471 checks with 123 failures, no errors and 13 skips;
all 119 preceding failure identities remain unchanged, and only the four new
joint replay gates are added. The core-plugin module executes 486 checks with
no failures or errors and four skips. The private
`chvdc2-joint-controls-20261102/validation.zip` retains the source/executable
fingerprints, complete before/after diagnostic traces, reports and reference
archive hashes. Full model qualification remains open.

## Coupled controller and DC-line runtime

`Chvdc2CoupledRuntime` owns the controller coordinates, DC-line coordinates and
electrical acceptance history together. Both modified Euler and staggered
integration use the same right-hand side. The prepared converter geometry
calculates firing-angle terminal voltages and fresh extinction margin from live
AC voltages and DC currents. Terminal current injections refresh with every
network trial. Reusable primitive buffers cover numerical stages, boundary
evaluation and nested rollback; warmed numerical and electrical updates allocate
no objects in the focused tests.

The runtime supports the algebraic line, the inductive line and the three-state
capacitive T-circuit. Tests check a flat initialized boundary, conversion of
power to signed current injections, line loss/energy balance, both-method
responses, commutation shorting without deletion of stored current, and atomic
failure/predictor/outer-transaction rollback. Zero terminal AC voltage and reverse
DC current remain rejected rather than assigned an unqualified conducting
equivalent. Source capacitance preparation is described below; full capacitive
trajectory qualification remains open.

### Electrical publication is distinct from a numerical protection stage

A predictor can evaluate a protection transition without publishing a new
extinction angle to the AC interface. Finishing its corrector retains that
electrical history until explicit boundary acceptance. Protection mode and its
clock can therefore participate in the numerical trial without advancing the
AC publication twice.

`readAcceptedBoundary` returns the power sample evaluated using the held angle,
even after fresh extinction margin has been accepted for the next boundary.
Repeated acceptance of an unchanged sequence is exactly idempotent. Changing
phasors, coordinates or time requires a new sequence; an older electrical epoch
cannot rewind a completed protection stage. All acceptance snapshots are
included in rollback. Regression tests first reproduced changed inverter power
on repeated acceptance and now preserve the complete published sample exactly.

### Coupled independent trajectories remain unaccepted

The new comparisons integrate all controller and line states continuously from
the initialized operating point. The synthetic AC system is reduced to two
source equivalents with its specified source/branch impedances. Reference source
angles are checked to stay fixed. Fault admittance and live converter injections
determine terminal voltage; no recorded voltage or state trajectory is imposed
after initialization. Raw negative startup samples and both event-side duplicates
remain in every comparison.

Both rectifier- and inverter-side faults are checked at 0.5 and 0.25 ms under
both integration methods. The eight comparisons still fail unchanged strict
state and power gates. Across these profiles, bus-voltage error is below
0.0000024 pu, line-current error reaches 0.520 A, and rectifier firing-angle error
reaches 0.00542 degrees. Voltage agreement alone does not qualify the converter.

The first post-event reference sample retains its previous current-measurement
state while line current and AC-voltage measurement have begun moving. The
coupled numerical schemes produce different first increments. Reducing the
network interval does not remove every discrepancy. Event sampling, internal
controller/line sequencing and published-state timing remain to be established;
no coefficient, precision, time shift or gate was fitted to these traces.

This runtime is not automatically attached by the importer. Full production
network attachment, protected/capacitive coupled trajectories, startup and
event sequencing, and complete independent SMIB acceptance remain required.

The registered checkpoint executes 4,493 tests with 131 failures, no errors and
13 skips. All 123 preceding failure identities remain; the only additions are
the eight new coupled-reference comparisons. All fourteen coupled-runtime tests
pass, including repeated-acceptance negative controls and rollback. The
core-plugin module executes 486 tests with no failures or errors and four skips.
The private `chvdc2-coupled-runtime-20261102/validation.zip` retains complete
coupled traces, the reference-only event-increment diagnostic, source/executable
fingerprints, regression reports and immutable reference-archive hashes.

## Network-interval capacitance preparation

The `Chvdc2Parameters.prepareDcLine` overload with `networkIntervalSeconds`
applies the source rule once, before the line state storage is allocated:

```text
fn = 1 / (2*pi*sqrt(L*C))
ignore capacitor dynamics only when line dynamics are enabled, C > 0,
and 3*fn*H > 10
```

Here `H` is the outer network interval, and `L` includes the prepared commutation
inductance. Equality retains the capacitor. Suppression changes the numerical
line from three coordinates to one series-current coordinate; it retains the
inductive dynamics and leaves all entered parameters unchanged. Invalid network
intervals and capacitance unit conversion underflow are rejected. The original
overload without an interval remains physical-only preparation for explicit
equation use; it does not assume a network interval.

Local modified-Euler or staggered steps use the resulting immutable coefficients.
Refining those local steps cannot silently restore a capacitor that was
suppressed for the outer interval. A changed network interval requires explicit
new preparation. The documented frequency criterion is a model-preparation
rule, not a stability certificate for the chosen integration interval.

Independent cold repeats check the suppressed and retained modes. The tiny-
capacitance control at the coarser network interval matches the complete earlier
zero-capacitance reference byte-for-byte, including every channel and event-side
sample. With the finer network interval, that same entered capacitor remains
in the initialized state vector, but the reference subsequently becomes
nonfinite after the fault. Its raw output, full CSV and rejection manifest are
retained; only its initialization/preparation decision is used as evidence.
The capture tool now records nonfinite results before returning failure, rather
than losing the diagnostic at its pre-export assertion.

A larger retained capacitor completes independent fault runs at both network
intervals with unchanged entered data. Coupled tests now evolve all eighteen
controller/line coordinates and compare capacitor voltage and both line
currents. All fourteen coupled comparisons remain unaccepted: the six additional
mode comparisons fail strict trajectory gates, while the four independent
preparation checks pass. The retained-capacitor profiles reach 0.631 A current
error, 0.00592 degrees rectifier-angle error and 119.6 V internal capacitor-voltage
error. These are separate from AC bus-voltage error. The original eight coupled
trajectories remain numerically unchanged.

The private `chvdc2-capacitor-20261102/captures.zip` preserves both cold repeats,
the rejected controls and exact probe provenance. Full coupled state/event
sequencing, protected trajectories and production network attachment remain
required before either record form can be classified as fully supported.

The focused run executes 54 checks with fourteen coupled-reference failures and
no errors. The registered suite executes 4,508 checks with 137 failures, no
errors and 13 skips: all 131 preceding failure identities remain, and only the
six additional coupled-mode comparisons are new. The core-plugin module
executes 486 checks with no failures or errors and four skips. The accompanying
private `chvdc2-capacitor-20261102/validation.zip` retains source/executable
fingerprints, complete new traces, regression reports and exact equality checks
against all eight preceding coupled trajectories.

## Independent run-boundary clock checks

The capture tool can now change the entered measurement lag and record public
state, variable, voltage and time snapshots at explicit run boundaries. A
separate split-run control pauses immediately after the first faulted step and
the first cleared step. Every original sample, event side and additional pause
observation remains in the immutable private capture archive. The installed
scripting environment cannot import its documented derivative module; derivative
access is explicitly unavailable, rather than inferred from finite differences.
The failed API probes are retained separately from completed captures.

With a 1 ms measurement lag, independent cold repeats establish these properties
at 0.5 and 0.25 ms network intervals:

- Reading public snapshots leaves the complete ordinary-run CSV byte-identical.
- Public state arrays at run return match the recorded states at the same
  physical time, including event-side duplicates. Fault application and clearing
  alone do not advance those states or refresh the published variables and bus
  measurements before the next run call.
- Pausing after the first faulted step leaves all previously recorded states
  unchanged, but changes the states after resuming. The maximum change in the
  rectifier AC-voltage measurement state is 0.0002797842 pu at the coarser step
  and 0.00009000301 pu at the finer step. These are controller-filter differences,
  not bus-voltage accuracy errors.

`Chvdc2RunBoundaryReferenceTest` checks these properties against both cold
repeats, verifies all retained file hashes, and compares common samples by time
and occurrence. Extra pause observations stay in the archive. The small summary
reports use bounded writers in unique run directories, so repeating the test
preserves earlier evidence without an output collision. These reports do not
substitute for the complete captures.

This evidence rules out a simple CSV state-publication delay at run return and
shows that run segmentation affects numerical history. It does not identify the
internal integration recurrence or justify introducing a fitted half-step, input
interpolation, precision change or extra filter into the Java equations. A faster
measurement-lag discriminator also fails to support the earlier input-ramp
hypothesis. Reconcile accepted input/history stages before changing either shared
integration path; a pause/resume diagnostic must not silently replace the
continuous reference run used for trajectory acceptance.

The private `chvdc2-boundary-clock-20261102/captures.zip` contains the uninstrumented,
observation-only and split-run controls, exact scripts, manifests and failed API
attempts. Both new clock checks pass. No production equation or accuracy gate is
changed, and complete coupled trajectories remain unaccepted.

## Live published measurements and numerical coordinates

`Chvdc2ControlAssembly.readPublishedStates` and
`Chvdc2CoupledRuntime.readPublishedStates` now expose the low-level order
controller's algebraic observations through the joint owners. With zero entered
measurement time, measured rectifier/inverter currents and AC voltages follow
their current terminal inputs immediately, including zero-duration event sides.
The coupled accessor refreshes voltage-dependent inputs and appends the line's
numerical coordinates. Both accessors write into caller-owned reusable arrays.

`readStaggeredState`, `getIntegrationStates` and rollback checkpoints continue to
represent numerical storage. Reading published values does not project or
advance that storage, accept a protection/electrical epoch, change the frozen
accepted power sample, or replace derivative history. Observations are also pure
during a modified-Euler predictor and after transaction rollback. The warmed
both-method integration/allocation checks now include published-state reads and
still allocate no per-step objects. Recording remains explicit; numerical updates
do not create an observation map or array when diagnostics are disabled.

Independent zero-measurement-time captures at both network intervals verify all
four algebraic measurement channels under both Java integration methods. The
comparison prescribes the recorded terminal inputs, retains every sample and
event side, and checks that repeated observations leave coordinates and histories
unchanged. Maximum observation error is below 0.0000001 pu. This qualifies the
memoryless observation boundary, not the remaining controller or coupled-network
trajectories. The complete coupled comparison now uses published observations
for recording and continues to use numerical coordinates for integration.

Separate independent controls disable DC-line dynamics while retaining a positive
measurement lag. The first rectifier AC-voltage measurement increment is exactly
the same as in the dynamic-line control, although the DC-current response differs
substantially. The first current-measurement state remains unchanged in both
controls. Therefore the filter's first-step timing cannot be attributed to line
inductance. The algebraic line's electrical publication and accepted input/history
stages still need reconciliation; do not replace them with an inferred delay.

An equation-only reconstruction using the previous recorded terminal voltages
and current firing angles matches the algebraic DC current within 0.003 A on
positive-time intervals. Using same-time voltages instead reaches about 0.58 A
error. Both original time grids and duplicate observations are retained. This
localizes an electrical-input sampling boundary; it does not establish the full
controller integration recurrence or the update policy at zero-time event sides.

The private `chvdc2-measurements-20261102/captures.zip` retains both cold repeats of
the bypass and line-mode controls. `Chvdc2MeasurementReferenceTest` separates the
four both-method observation checks from the two reference-only line-mode
discriminators. Full protected/capacitive trajectories and automatic network
attachment remain open.

## Coupled line-mode and numerical-refinement controls

The coupled comparison now reads line-dynamics flags from each reference manifest
and exercises the algebraic line as well as the dynamic line with bypassed
measurements. Recorded inputs do not drive the evolving Java states or terminal
voltages. Both methods retain all reference samples and event-side duplicates.
A separate control uses ten numerical steps per recorded interval, solves the AC
circuit at each stage, and preserves the outer interval for coefficient
preparation. This is whole-system refinement, not a claim that the reference uses
the same local clock.

- With line dynamics disabled, coarse single-step staggered integration becomes
  unstable before the fault and reaches the unsupported reverse-current boundary
  at about 0.0375 s. Ten steps complete that trajectory under both methods. The
  failed run remains an error; its partial trace, completed-sample count, stopping
  time and exception are retained explicitly.
- Refinement does not remove the algebraic line's event-side mismatch: maximum
  current difference remains about 65.3 A and voltage difference about 0.0003664
  pu. Away from event times, the refined current difference still reaches roughly
  9.4 to 15.8 A. Acceptance includes every event sample. Electrical input and
  publication stages need investigation separately from numerical stability.
- With dynamic line current and zero measurement lag, both methods complete the
  refined runs, but current differences remain about 0.356 A at the coarser
  interval and 0.481 A at the finer interval. Bypassing the measurement lag does
  not establish coupled agreement.

A retained equation diagnostic also locates a late-time precision limitation in
the original fine-interval references. Both fault directions end with unchanged
measured-current, rectifier PI/angle and line-current coordinates for more than
0.2 s, despite an interior rectifier PI derivative of approximately 0.07 degrees
per second. These samples are not equilibria of the continuous equations. A
binary32 increment using one tenth of the outer interval is absorbed by rounding
at those stored PI values; an outer-interval increment is representable. This is
consistent with the earlier precision discriminator, but does not identify the
complete internal recurrence. Production arithmetic and gates are unchanged.

The existing numerical/history and allocation checks pass. All new coupled
trajectory checks remain unaccepted. The private
`chvdc2-coupled-modes-20261102/validation.zip` retains comparisons, the explicitly
incomplete trajectory, refinement settings, plateau diagnostic and regression
evidence. Resolve electrical sampling, local integration ownership, protected
and capacitive trajectories, and automatic attachment before full qualification.

## Sampled algebraic current and accepted integration intervals

The coupled owner now retains algebraic DC current as accepted numerical history.
Network trials and observations refresh the AC power and phasor-current
transformations using live terminal voltages, but do not implicitly resample that
DC current. On completion of a numerical interval, the owner uses the interval's
sampled AC voltage magnitudes and the updated firing angles to publish the next
current. The dynamic-line equations and storage are unchanged.

Modified Euler publishes current after the corrector, using the predictor-start
voltage sample. Shared staggered state proposals remain history-pure:
`applyStaggeredState` may be used for trial states and derivative probes, while
`completeStaggeredStep` publishes current once for the accepted step. Zero-duration
completion does not advance history. Repeated or mismatched positive completion
is rejected. Numerical coordinates, sampled current, pending proposals and
interval progress all participate in transaction rollback.

`beginIntegrationInterval(H)` and `completeIntegrationInterval()` let local
integration steps retain one outer electrical sample. Completion requires local
durations to sum to H and occurs before the final network solve. Without this
explicit bracket, each completed numerical step is one electrical interval.
These calls do not alter coefficient preparation or choose a default substep
count. Production attachment and local-step orchestration remain unfinished.

Independent prescribed-input/state component replays retain every observation
and both event-side duplicates. At outer intervals of 0.5 and 0.25 ms, maximum
current errors are 0.008035 and 0.007552 A, respectively, within the existing
0.01 A current gate. A separate raw-angle reconstruction stays below 0.0031 A;
the component owner additionally projects firing angles to its calculated
limits. These checks qualify the sampled-current component boundary, not the
evolving coupled trajectories.

The complete coupled comparisons use no prescribed reference states or boundary
inputs. With ten local steps and one outer electrical sample, their results are:

| Outer interval | Integration method | Maximum voltage error (pu) | Maximum current error (A) |
| --- | --- | --- | --- |
| 0.5 ms | Modified Euler | 0.000004266 | 0.8923 |
| 0.5 ms | Staggered | 0.000003194 | 0.5532 |
| 0.25 ms | Modified Euler | 0.000003603 | 0.6682 |
| 0.25 ms | Staggered | 0.000003093 | 0.6682 |

These replace the preceding checkpoint's roughly 65.3 A algebraic-line mismatch.
The refined voltage gate passes, but the unchanged 0.01 A coupled-current gate
still fails. Single-step coarse staggered integration still reaches an
unsupported reverse-current boundary before the fault; its partial trajectory
and exception are retained. All 22 dynamic-line comparison traces, including
zero-measurement-time refinement controls, remain byte-identical to the preceding
checkpoint. Numerical/history checks cover both integration methods and show
zero warmed per-step allocation, including explicit interval completion.

The largest remaining normalized internal-state error in these refined controls
is the rectifier AC-voltage measurement immediately after fault application.
It persists under both integration methods. Resolve that measurement/integration
boundary separately from the independently checked algebraic-current sampling;
the late-time current discrepancy must also retain the reference plateau
diagnostic described above.

The private `chvdc2-sampled-line-20261102/validation.zip` retains the component
replays, complete and explicitly incomplete coupled comparisons, executable and
source snapshots, and regression evidence. Full trajectory qualification,
protected algebraic-line behavior, production local-step ownership and automatic
network attachment remain open. No acceptance threshold or production precision
was changed.

Registered regression validation ran 4,545 tests: 152 failures, one error and 13
skips, with exactly the same failing/error test identities as the preceding
checkpoint. All eleven added checks pass. The core plugin module ran 486 tests
with no failures or errors and four skips. An earlier broad attempt hit the
generated-output free-space guard; its reports are retained separately and are
not used as regression evidence. Inactive generated outputs were relocated with
hash verification and original-path junctions before the completed retry.

## Same-time event calls and the measurement equation

Independent cold controls now make one or two `RUN` calls with the current
physical time immediately after fault application and clearing. At both retained
network intervals, every complete output CSV is byte-identical to the ordinary
continuous baseline. The calls preserve time, all public states and variables,
and bus voltages. They neither add observations nor perform the post-event
network evaluation. Cold repeats also reproduce the boundary snapshots exactly.

These calls therefore cannot be used to repair the remaining first-step filter
discrepancy. They are distinct from pausing after a positive integration interval,
which the preceding run-boundary controls show can change subsequent history.
The source operation manual describes pre-event and post-event derivative/network
stages at pause/resume boundaries. That description does not expose the complete
internal controller recurrence, and does not justify a fitted time shift.

Review of the installed model diagram confirms a first-order terminal-voltage
measurement with the entered measurement time constant. No additional input
delay is shown. The current shared measurement equation is consistent with that
diagram. Investigation must retain the distinction between equation conformance
and the still-unaccepted event/integration trajectory; no extra delay, gain,
precision rule or parameter correction was introduced.

`Chvdc2RunBoundaryReferenceTest` checks both refresh counts, both cold repeats and
both time grids against the unchanged baseline, using complete CSV equality and
public boundary snapshots. The private
`chvdc2-event-refresh-20261103/captures.zip` retains the raw outputs, exact capture
scripts, manifests, diagram review and document fingerprints. These are reference
lifecycle discriminators, not acceptance of either Java integration method or of
the complete model. Production local integration, protected trajectories and
automatic attachment remain open.

The focused lifecycle, component-measurement and run-boundary selection passes
all 37 checks with no skips. The new checks belong to the existing registered
run-boundary test class. Production Java is unchanged in this slice; the preceding
registered-suite failure baseline is retained, not represented as a fresh full
suite run. Capture and validation evidence are retained separately in the private
event-refresh artifact directory.
