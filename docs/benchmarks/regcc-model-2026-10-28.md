# REGCC converter implementation

This is the original checkpoint. The later
[local-integration and PI-limit audit](regcc-substeps-2026-10-30.md) separates
prepared/local intervals, adds configurable local steps and corrects PI output
limits. Its evidence supersedes the implementation gaps discussed in this
historical review; complete model acceptance remains open.

REGCC now has its own parser, builder and nine-state runtime. It was absent from
the earlier impedance-contract change because that slice implemented REGCAU2 and
preserved REGCB1; a voltage-source model with a PLL cannot be substituted by
renaming either model.

This checkpoint establishes **loadability and bounded trajectory evidence**.
It does not establish complete trajectory or system acceptance. The stress
profiles described below remain explicit diagnostics.

## Model contract

The native flat record has two selectors and thirteen constants, in this order:

```text
IBUS 'REGCC' ID RateFlag PQFlag
 Iqrmax Iqrmin Tfltr rrpwr Imax Te Kip Kii Kppll Kipll Wmax Wmin Vpllfrz /
```

Foreign flat names and guessed user-model aliases are not accepted. `RegccData`
validates finite parameters, selector values, limit signs and time constants.
`DStabNetworkBuilder.addRegcc` attaches the model without changing the imported
source impedance. An existing dynamic device cannot be overwritten. An implicit
generator transformer or zero source impedance is rejected.

The source resistance and reactance come from the generator record, on its MVA
base. They remain in the network interface and in the internal-voltage equations:

\[
E_d=V_d+R_s i_d+X_s i_q,\qquad
E_q=V_q+X_s i_d-R_s i_q.
\]

Here positive `iq` denotes reactive injection. The physical current is obtained
from the internal voltage behind that impedance. The algebraic current limit
uses `Imax` and P/Q priority. Blocking cancels the Norton stamp without resetting
controller states. No compensation-removal capability is advertised.

The published state order is:

1. Reactive-current PI integral.
2. Active-current PI integral.
3. Measured voltage.
4. Reactive-current rate-limit lag.
5. Active-current or active-power rate-limit lag.
6. Internal q-axis voltage.
7. Internal d-axis voltage.
8. PLL frequency integral, in per-unit frequency.
9. PLL angle, in radians.

The PI current feedback and voltage transforms use the PLL frame. The PLL
integral freezes below the **filtered** voltage threshold; its stored frequency
and output rate retain their respective bounds. Conversion between rad/s and
per-unit frequency uses the network frequency. The internal-voltage filter uses
`max(Te, 2*dt)`. Auxiliary recovery lags use `4*dt`; their directional limits
depend on the initialized operating direction. Positive and negative reactive
recovery, charging, P/Q priority, predictor/corrector stages, blocking and
inactive/resumed-device history have focused equation checks.

Ordinary modified Euler is supported. Other integration methods fail explicitly.
The opt-in staggered solver is not extended by this change.

`RenewableConverterHost` supplies only bus/generator metadata for measurement
binding. Existing controller equations and public constructors are retained.
REGCC can attach REEC_A/B/C/D/E electrical controls and REPCA1 plant controls.
The electrical bridge converts the existing signed reactive-current command to
REGCC's positive-injection coordinate. Named electrical-controller states remain
available alongside the converter states.

## Independent reference matrix

The installed exact model contract and diagrams were inspected. Exact document,
engine and model-library provenance stays in the private manifest. The public
[REGC_C description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_C.htm)
is a separate dialect; its parameter list and timestep corrections are not
silently substituted for the native record above.

The final matrix contains fifteen profiles, each generated cold twice. Every
run retains all nine converter states, ten converter variables, terminal P/Q and
voltage/angle at all three buses. The electrical-controller diagnostic also
retains its six states and nine variables. Startup samples and duplicate
run/fault boundaries remain intact. Outputs are retained losslessly as compact
test resources; full raw artifacts, logs and runnable inputs remain private.

The bounded trajectory envelope covers flat operation, the standard fault at
two steps, active-power rate selection at two steps, charging, negative Q,
P-priority limiting, constrained recovery, PLL freezing and PLL frequency bounds.
The standard fixed-`Te` fault's maximum terminal-voltage difference is
**0.001031 pu at 0.5 ms**, falling to **0.000532 pu at 0.25 ms**. Maximum Q
difference falls from **0.007494 to 0.003875 pu**. These largest differences occur
at the first endpoint following a discontinuity. No time offsets, sample
exclusions or fitted parameters are used. Gates cover each of the nine states
and eight terminal channels, with timestep-scaled ceilings.

## Unqualified accuracy profiles

These are retained and compared, but are not counted as passing trajectories:

- **Q-priority at `Imax=0.26`:** the reference active PI state abruptly changes
  from about 0.260000 to 0.026737 pu at 0.0695 s. Both cold runs reproduce it.
  This is larger than its current-error derivative can explain and needs an
  exact active-boundary contract. The implementation does not copy a neighboring
  state to fit that trace. Maximum active-power difference is about 0.14595 pu.
- **Step-limited `Te`:** terminal-voltage differences reach 0.00788 pu and Q
  differences 0.05018 pu. Halving the step also halves the effective time constant,
  so it does not hold the continuous model fixed. This requires further
  integration-method assessment.
- **REEC_A coupling:** the reactive rate-limit coordinate differs by about
  0.08125 pu around the electrical command transition. Upstream command-history
  alignment requires further isolation; converter-only evidence does not
  qualify every complete control chain.

`RegccReferenceTest` prints every diagnostic error and its time. Set
`requireAllTrajectories` to `true` in its `regcc/settings.json` test fixture to
require these profiles as well;
that strict gate currently fails. The ordinary registered test gates the declared
bounded profiles and checks finite complete diagnostic comparisons.

Direct writes to converter VAR commands were tried during diagnosis. The native
fixed-command provider overwrote them. Those attempts are preserved privately
and excluded from stimulus acceptance; they are not evidence for a command step.

Full-network, bidirectional operating-mode transitions and complete lifecycle
trajectory acceptance remain open. No performance improvement is claimed.

## Regression validation and checkpoint

The final focused run passes **55 tests**, including the generated catalog,
configuration guard, converter equations, neighboring converter trajectories,
electrical-controller checks and modified-Euler preparation. All thirty final
reference artifacts pass integrity and cold-pair identity checks. Eleven
profiles meet the bounded trajectory gates; four remain diagnostics. A separate
strict run explicitly fails on those four profiles, as documented above.

The full reactor ran **4,274 tests, with 86 skips and four failures**. Plugin-core
and three-phase tests passed. The integration failures are the endpoint snapshot
fault-edge check, the REECC1 coupled fault, Case 3's three-cycle fault and Case
5's generator trip. Running the affected classes with the exact pre-change
frozen jar reproduces the same four failing methods. They remain unresolved;
the full reactor is not reported as passing. The final encoded fixtures and
tighter ceilings were checked by the later focused run.

Texas2k Cases 1 and 2 retain byte-identical full recordings against the frozen
baseline: 2,405 samples and 6,190 / 4,810 channels. These checks establish
trajectory non-regression; they are not warmed timing measurements.

The [evidence checkpoint](checkpoints/regcc-model-2026-10-28.json) indexes the
exact source patch, plugin runtime, complete references, tests, strict failures
and recording hashes. Its private manifest SHA-256 is
`687b87e50e3dbd439b3364869117f302612efc2ecd3620d8dc8932cd4c16442a`.

## Public implementation and substep review

Public-source inspection on 2026-10-28 supports evaluating local substeps for
this explicit solver. The count should follow the closed-loop dynamics and
accuracy evidence.

The inspected Dynawo source is pinned to commit `b1d50a690a9e5b3a76210b353a7cfd0aa495e664`:

- [REGCc](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/dynawo/sources/Models/Modelica/Dynawo/Electrical/Controls/WECC/REGC/REGCc.mo)
  uses two current PI blocks with variable output bounds and anti-windup.
- [PVVoltageSource4](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/dynawo/sources/Models/Modelica/Dynawo/Electrical/Photovoltaics/WECC/PVVoltageSource4.mo)
  connects a separate PLL, converter, electrical control and plant control.
- Its [example job](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/examples/DynaSwing/WECC/PV/WECCPVVoltageSource4/WECCPVVSource.jobs)
  selects IDA. The [solver settings](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/examples/DynaSwing/WECC/PV/WECCPVVoltageSource4/WECCPVVSource.par)
  set maximum order 2 and adaptive error tolerances. IDA uses
  [implicit variable-step BDF](https://sundials.readthedocs.io/en/latest/ida/Mathematics_link.html).
  This provides a different numerical treatment from one fixed explicit step.

The public model has dialect differences: its auxiliary recovery lags use
1 microsecond in this source revision, and the PLL is a separate component.
Its anti-windup wiring is useful audit evidence; exact native limiter behavior
still needs independent qualification. The current Java current-PI integrals
advance without equivalent anti-windup feedback. That difference deserves a
separate audit in the unresolved Q-priority profile.

The published [subinterval integration description](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Integration.htm)
holds algebraic network variables fixed during selected local model steps.
The [configuration reference](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Integration_Subinterval_Models.htm)
allows per-model counts of 1, 2, 4, 8 and higher powers of two. REGC_C is absent
from its published automatic-subinterval model list, so that list does not
establish a mandatory REGC_C default.

### Why the existing lag floor is insufficient by itself

For one unsaturated current axis with fixed terminal voltage and PLL angle,
the Java equations reduce to the characteristic polynomial

\[
T_e s^2+(1+K_{ip})s+K_{ii}=0.
\]

This is a local screening calculation. For example, with `Kip=5`, `Kii=70`
and effective `Te=0.01 s`, the fast pole is approximately -588.10 per second.
At a 1/240 s network interval, modified Euler's amplification
`1 + h*s + (h*s)^2/2` exceeds one for that pole with one step
(about 1.552). Four local steps give stable amplification for both roots in
this reduced system. The original low-gain reference profile, `Kip=0.5` and
`Kii=5`, has a slower fast pole of about -146.59 per second and passes this
one-step stability screen.

The `Te >= 2*H` correction screens an isolated lag. Current-loop proportional
feedback also affects the fastest pole, so that lag rule alone cannot establish
closed-loop stability. Coupled network, limiter and PLL behavior require their
own checks.

### Controlled substep implementation boundary

The present Java `step` field controls parameter preparation and integration.
Calling `nextStepWithSignals(H/N, ...)` repeatedly would reconfigure the
`Te` floor and the `4*step` recovery lags. First separate the configured
network interval `H` from the local integration interval `h=H/N`.

A controlled 1/2/4/8 experiment should keep the prepared coefficients,
network interval, tolerance, commands and event timestamps fixed. The nine
coupled converter coordinates should advance coherently within each local
predictor/corrector pair. With held network voltage, recompute source current,
PLL transforms and current-limit feedback from each trial state. Upstream
controller history must advance only at its declared outer stages.

At a quarter-cycle network interval, four and eight local steps are about
1.042 ms and 0.521 ms. Compare those counts against the retained independent
reference with all states and event sides, then measure full recording-inclusive
runtime before selecting a default. The eleven bounded profiles and four
unqualified diagnostics above retain their current status; this review adds
analytical and source evidence.
