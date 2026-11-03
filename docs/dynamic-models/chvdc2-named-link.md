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
across local integration stages. Source-specific capacitance suppression and
fast-time preparation are not yet implemented or inferred from local steps.

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

The inverter reference channels themselves do not always satisfy simultaneous
`P = -Vi*Ii`: the largest same-row discrepancy is approximately 0.38334 MW.
The firing-angle kernel's largest recorded-input inverter P/Q differences are
approximately 0.38331 MW and 0.54103 Mvar. This is evidence that channel
publication and network evaluation stages require investigation; it does not
establish the correct coupled trajectory. No delay, offset, fitted impedance,
changed coefficient or production precision adjustment is applied. The passing
boundary tests qualify initialization and rectifier P/Q only; inverter and
complete coupled SMIB acceptance remain open.

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
