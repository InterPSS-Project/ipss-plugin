# CHVDC2 named-link preparation and DC-line component

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
legacy form also has repeatable rectifier-fault, inverter-fault and auxiliary
modulation captures. All channels and event-side duplicates are preserved.
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

Still required: exact legacy auxiliary routing; current PI controls, VDCOL, angle limits and RAML;
commutation-failure histories; line-state/source preparation equivalence;
network ownership and attachment; complete both-method SMIB states, boundary
trajectories, events and longer-duration gates. Neither form is classified as
loadable or verified by this change.

Exact source provenance, rejected captures, valid stiff-source captures and
validation evidence are retained in the private `chvdc2-foundation-20261102`
evidence directory. The rejected and valid reference archives are separate.
