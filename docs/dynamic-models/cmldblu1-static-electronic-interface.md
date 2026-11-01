# Mixed static and electronic current interface

The omitted-distribution legacy composite now applies its independently
established low-voltage static rule when a full-recovery electronic portion is
also present. Motor fractions must be zero and both static frequency
coefficients must be zero. Represented distribution remains outside this
extension.

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

The mixed represented-distribution controls remain diagnostic: worst angle
RMSE is approximately 0.532 degrees. Their raw evidence is retained separately
from the accepted profiles. Motor mixtures, frequency dependence, restoration,
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

This remains an isolated prototype, not production support. The deep fault
still exhausts the unchanged 50-iteration limit. Its numerical conditioning
must be resolved before broadening selection; the reference physical-balance
issue above remains separate. The diagnostic shunt port also required explicit
registration for staggered integration: arbitrary algebraic subclasses must
not be silently accepted. Production work must preserve owned-device discovery,
reinitialization and commit/discard semantics, and validate omitted-element,
tap and restoration cases before adopting the boundary calculation.

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

Production eligibility is still unchanged. Before adoption, replace the
diagnostic shunt subclass with an explicitly owned algebraic component and
test failed-solve stamp rollback, repeated initialization, matrix rebuilds,
tap changes and restoration. Verify that accepted-current history remains
unchanged on a failed solve and that omitted-element configurations retain
their existing path. Numerical conditioning must not silently change the
first-entry voltage used to prepare G/B.

Private conditioning and physical-balance archive SHA-256:
`0e75cdf02a658f9441d982415225076cb9837b339dc62f9f84007040d73d61f1`.
