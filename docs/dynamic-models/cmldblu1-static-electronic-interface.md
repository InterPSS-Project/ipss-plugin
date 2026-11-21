# Mixed static and electronic current interface

The omitted-distribution legacy composite now applies its independently
established low-voltage static rule when a full-recovery electronic portion is
also present. Motor fractions must be zero and both static frequency
coefficients must be zero. The represented-distribution extension now also
supports this mixture with both elements present or either element omitted.
Complete deep-fault reference
qualification remains open as detailed below.

Previously, the mixed configuration selected the generic static voltage
polynomial, bypassing low-voltage G/B preparation. Independent repeated-fault
SMIB controls exposed rotor-angle RMSE up to 0.323 degrees. The corrected
configuration uses the existing static current port and the existing
algebraic electronic kernel through both integration methods.

## Numerical interface and initialization

The static port cancels only the owned composite shunt numerically, retaining
its physical current. The electronic leaf uses a zero numerical admittance
and a fresh voltage-dependent current. Its removed initial admittance is a
numerical pair, not a physical component.

With owned physical shunt `Y_c`, the combined interface is

`(Y_rest + Y_c - Y_c)V = I_other - Y_c*V - I_static(V) - I_electronic(V)`.

Thus the converged physical equation retains the capacitor and both loads.
Unrelated parent shunts and load allocations are preserved. Preparation uses
the owned component references directly, without rescanning buses or devices.

This realization matters before convergence because static G/B is frozen from
the first low-voltage iterate. An independent reconstruction of that iterate
matches reference G within `3.51e-7` with the electronic numerical pair absent.
Retaining that pair changes G materially. Merely setting the electronic
admittance to zero once is insufficient: leaf initialization can restore it.
The prepared current-interface choice therefore persists through subsequent
leaf initialization. It adds no per-step integration stages or new buffers.

## Verification and remaining scope

Cold-repeated half-second SMIB controls at 0.5 ms exercise ramp and deep faults,
each with repeated recovery. Every source field repeats exactly. Both methods
preserve all samples and event sides, and explicitly check the accumulated
reference clock. Regression gates include both component P/Q values, external
and internal voltages, machine P/Q, speed, angle and the static G/B history.

The existing 0.001 range-normalized RMSE, 0.0002-degree angle RMSE and
`5e-6` G/B maximum-error gates remain unchanged. The isolated corrected runs
reduce worst angle RMSE below 0.000096 degrees and static active-power NRMSE
below 0.000224. The production regression executes the same controls.

Unit checks cover repeated leaf initialization, numerical-pair cancellation,
unrelated parent shunts and load allocations, and rejection of the optimized
selection for frequency-dependent static loads.

The original mixed represented-distribution baseline had worst angle RMSE of
approximately 0.532 degrees. Moderate-fault reference comparisons now pass with
the prepared boundary and numerical stamp; the deep profile remains diagnostic
because of the physical-balance discrepancy below. Raw evidence is retained
separately from the originally accepted profiles. Motor mixtures, frequency dependence, restoration,
tap actions and production-step/long-duration acceptance remain open; the
complete composite remains `LOADABLE`.

The deep represented-distribution reference also needs a physical-balance
audit before it can serve as an acceptance target. At first fault entry,
independent branch KCL gives active power into the load of `-0.346660 pu`;
its published current gives `-0.346644 pu`. The initial value is `0.400000 pu`.
The fixture has only passive static/electronic loads at that bus, positive
feeder resistance and purely reactive shunts/fault impedance. Its reported
static demand is positive at the same sample. This independently observed
export is not explained by a current-sign convention and is not reproduced by
changing physical load parameters. The raw profile and KCL calculation are
retained for further investigation; the moderate profile does not show this
negative-active-power behavior.

Private reference archive SHA-256:
`b96d98e706628220ea5d598feb6c3fe5afaa0d0c58d515c755c94a7ed7e38d16`.

## Represented-distribution boundary diagnosis

A recorded-input experiment supplies the reference's first prepared static
G/B, retains it through repeated faults, and evaluates the static current
freshly at each trial voltage. This is a localization experiment, not an
independent implementation: production must derive G/B without reference data.

The initial experiment changed observed P/Q but left the pure-impedance
current shortcut active, so its unchanged trajectories are invalid evidence
about load injection. Bypassing that shortcut exposed failure of the deep
profile to converge within the unchanged 50-iteration limit. A matching fixed
numerical admittance with compensating current then allowed both profiles to
complete. That stamp preserves the converged physical equation; it changes
the iteration conditioning.

At the moderate fault, this corrected replay yields the following full-record
errors at 0.5 ms, including event-side samples:

| Integration | Bus voltage NRMSE | Static P NRMSE | Rotor-angle RMSE (degrees) |
| --- | ---: | ---: | ---: |
| Modified Euler | 0.00000792 | 0.0001724 | 0.00008198 |
| Staggered | 0.000000844 | 0.00005937 | 0.00001192 |

This localizes the moderate discrepancy to G/B preparation and its network
interface. It does not justify a reference-fed production implementation or
extending the optimized selection yet. The deep profile still disagrees:
voltage NRMSE is approximately 0.125 and angle RMSE approximately 2.837 degrees
under both methods, despite supplying the recorded G/B.

Independent cold repeats at 0.25 ms match across every field and retain all
2,005 nonnegative-time samples, including four duplicated event timestamps.
The deep profile's first-entry KCL active power remains `-0.346660 pu`, and its
published-current active power remains `-0.346644 pu`, exactly as at 0.5 ms.
Thus reducing the integration interval does not resolve this boundary
inconsistency. The moderate profile retains positive active demand throughout.
Further work must establish the deep profile's internal-to-terminal current
mapping before treating it as a coupled acceptance target; physical parameters
and acceptance tolerances remain unchanged.

Private diagnostic archive SHA-256:
`1b19d241668c2037984ca15b633c134ae6de3d80552aeab80e08debd26271f15`.

### Independent first-entry prototype

The next isolated prototype removes the recorded-G/B input. It retains the
accepted terminal current `I_previous` and reconstructs the preparation voltage
from the first terminal iterate using the initialized transformer and feeder:

```
V_low = (V_terminal - Z_transformer * I_previous) * tap
I_feeder = I_previous / tap - Y_low * V_low
V_prepare = V_low - Z_feeder * I_feeder
G = P_previous / |V_prepare|^2
B = -Q_previous / |V_prepare|^2
```

Current history is refreshed only after an accepted network solve. Trial
voltages do not overwrite it. The prototype cancels the low-side shunt's
numerical stamp with a matching current term, retaining the physical shunt;
this is additional to the far-end cancellation. Using the expanded load-bus
iterate directly instead produces G of `0.432221057`, compared with reference
`0.430018574`. The reconstructed boundary produces `0.430018587` under both
methods without reference-fed constants.

The moderate repeated-fault prototype completes under both methods with every
sample retained. Modified Euler yields bus-voltage NRMSE `0.00000791`, static-P
NRMSE `0.0001132` and angle RMSE `0.00007570` degrees. Staggered integration
yields `0.000000844`, `0.00005937` and `0.00001195` degrees respectively.

At this stage the isolated prototype still exhausted the unchanged
50-iteration limit for the deep fault. The conditioning experiment below
resolved that iteration failure; the reference physical-balance issue remains
separate. The diagnostic shunt port also required explicit
registration for staggered integration: arbitrary algebraic subclasses must
not be silently accepted. These findings motivated the owned production
components and lifecycle gates described below. Tap and general restoration
qualification remain open.

Private independently prepared prototype archive SHA-256:
`5b2e1d04c7cd9bb141eb27e63c5df53a603d48c7d5006b2710384bace7657e96`.

### Prepared-admittance conditioning experiment

A subsequent isolated prototype resolves the deep-profile iteration failure.
Once G/B is independently prepared, it replaces the static leaf's numerical
admittance with `G + jB`, updates the corresponding matrix diagonal by the
difference from the previous stamp, and marks the factorization dirty. The
current injection remains `Y_numerical * V - conjugate(S(V) / V)`; therefore
the physical load equation and capacitor are unchanged. The prototype saves
the preceding stamp at solve entry and restores it if the solve fails.

Both repeated-fault profiles now complete under both integration methods at
the unchanged 50-iteration limit and `1e-4` voltage-correction tolerance. No
reference-fed values are used. Deep-profile G differs from the reference by
less than `3.8e-7`. Moderate-profile angle RMSE remains below `0.0000802`
degrees; deep-profile angle RMSE remains approximately `2.837` degrees.
Convergence and reference agreement are separate results.

Additional full-record diagnostics independently check transformer and feeder
currents, shunt currents, component P/Q and resistive losses from the recorded
internal phasors. All 1,005 samples per profile/method, including event sides,
are retained. Deep-profile terminal active demand stays above `0.05054 pu`;
the maximum active-power balance residual is `5.51e-8 pu` for modified Euler
and `7.73e-10 pu` for staggered integration. Across both profiles and methods,
the largest active-power residual is `5.68e-6 pu` and the largest load-current
residual is `2.53e-5 pu`. These checks support the physical consistency of the
converged prototype, without resolving the deep reference's terminal mapping.

This experiment preceded the production components below. Production tests
now cover stamp rollback, repeated leaf initialization, matrix rebuilds and
accepted-current history, including omitted-element configurations. Tap
changes and general restoration remain open. Numerical conditioning must not
silently change the first-entry voltage used to prepare G/B.

Private conditioning and physical-balance archive SHA-256:
`0e75cdf02a658f9441d982415225076cb9837b339dc62f9f84007040d73d61f1`.

## Production components and lifecycle gates

`LegacyDistributedStaticLoadPort` now implements the independent boundary
calculation and prepared numerical stamp through the existing shared network
callbacks. `LegacyDistributionShuntPort` retains the physical low-side shunt
current while cancelling only its numerical stamp. Both are explicitly
recognized algebraic types under staggered integration; custom subclasses are
not implicitly accepted. The omitted-distribution static port retains its
existing behavior. No additional differential states or integration stages are
introduced, and owned component references replace repeated discovery.

The distributed static port exposes accepted terminal-current real and
imaginary parts as named diagnostics. Trial evaluations and failed solves do
not commit this history. Matrix factorization is invalidated only when its
numerical stamp changes, including rollback; ordinary current queries do not
change the matrix. Rebuilding the matrix uses the leaf's accepted stamp once.

Registered lifecycle tests exercise both methods on the synthetic network:
failed first-entry rollback, failure after an accepted entry, matrix rebuilding,
repeated leaf initialization, and absence of duplicate leaf registration.
The moderate repeated-fault reference is checked with the existing unchanged
voltage, power, speed, angle and G/B thresholds. Deep repeated-fault tests
retain every event side and assert convergence, positive terminal active
demand, internal current balance and demand-plus-losses balance. They are
physical-equation tests, not a substitute for independent deep-fault trajectory
acceptance. The complete composite remains `LOADABLE`; tap actions, general
restoration, motor mixtures and broader production-step qualification remain
open.

## Partial distribution elements

The shared boundary calculation also supports an omitted transformer or feeder.
With no transformer, terminal current is reconstructed from feeder current plus
the composite's owned low-side shunt current. With no feeder, the preparation
voltage is the transformer low-side voltage and no duplicate low-side shunt
port is created. Both-elements-omitted configurations retain their existing
collapsed current port.

Low-side shunt ownership is captured during distribution initialization.
Cancellation excludes unrelated parent-bus shunts, and registering the owned
current-only shunt does not overwrite an existing parent load allocation.
The same accepted-current history, prepared G/B and stamp rollback serve both
integration methods without additional differential states or integration
stages.

Paired independent half-second references cover transformer omission and two
feeder-omitted fault strengths. The stronger feeder-omitted control is needed
because the moderate control's power trajectory was already close even with
the old polynomial fallback. Across both methods:

| Control | Previous worst angle RMSE (degrees) | Corrected worst angle RMSE (degrees) |
| --- | ---: | ---: |
| Transformer omitted | 0.116277 | 0.00009818 |
| Feeder omitted, moderate fault | 0.00008687 | 0.00009813 |
| Feeder omitted, stronger fault | 0.192821 | 0.00005176 |

Every cold-captured field repeats exactly. The registered comparisons retain
all samples and event sides and preserve the existing voltage, component P/Q,
machine P/Q, speed, angle and G/B gates. Lifecycle tests now cover both methods
for fully represented and either partially omitted topology. A separate
ownership test checks unrelated parent load/shunt preservation and repeated
shunt-leaf initialization. General restoration, tap actions, motor mixtures
and deep-fault reference qualification remain open.

Private partial-distribution reference archive SHA-256:
`01e6b5659580189d8c6928ec74245791bba0588f0699572335af616f4b966546`.

## Ten-second moderate-fault controls

Cold-repeated independent captures now cover the represented static/electronic
mixture for ten seconds at quarter-cycle and 0.5 ms intervals. Every captured
field repeats exactly. Comparisons retain both sides of all events and the
reference clock's final overshoot; no samples are discarded or angle offsets
applied. The registered SMIB test verifies each timestamp against the
accumulated reference clock.

| Step | Integration | Worst external voltage NRMSE | Worst static P/Q NRMSE | Angle RMSE (degrees) |
| --- | --- | ---: | ---: | ---: |
| Quarter cycle | Modified Euler | 0.00022077 | 0.00045714 | 0.00068034 |
| Quarter cycle | Staggered | 0.00001846 | 0.00004886 | 0.00025146 |
| 0.5 ms | Modified Euler | 0.00011595 | 0.00027818 | 0.00121398 |
| 0.5 ms | Staggered | 0.00011554 | 0.00027743 | 0.00121131 |

External/internal voltages, machine P/Q and speed, and both load components'
P/Q pass the existing 0.001 range-normalized RMSE gate. G/B retains its `5e-6`
maximum-error gate. The ten-second controls use the separately requested
0.01-degree angle RMSE limit; the half-second controls retain their unchanged
0.0002-degree limit. The longer-run allowance does not replace the short-run
gate or qualify the unresolved deep-fault reference.

Internal source-machine states are retained as diagnostics rather than being
claimed as fully matched: fine-step field-state NRMSE reaches approximately
0.00229. Independent replay of the recorded field-voltage/current boundary
reduces peak field-state error from `0.00023846` to `0.00002170` pu when each
accepted replay state is rounded to binary32. This supports a precision
contribution, but does not prove that rounding alone explains every coupled
trajectory difference. Production arithmetic remains unchanged.

Private ten-second reference archive SHA-256:
`edc9467107056197587b69c791ab793042ce3d9802c58a985c7c0cfc84803bb3`.
