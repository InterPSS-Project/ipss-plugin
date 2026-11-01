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
