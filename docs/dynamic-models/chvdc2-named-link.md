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
