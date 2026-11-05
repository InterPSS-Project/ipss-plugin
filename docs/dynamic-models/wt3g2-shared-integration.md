# WT3G2 shared converter integration

The bare WT3G2 converter exposes its five existing numerical coordinates through
`StaggeredEulerParticipant`: active-current lag, internal-voltage lag, PLL
integral, PLL angle and LVPL voltage sensor. Modified Euler and the shared solver
evaluate the same derivative implementation. The converter's Norton source and
physical equivalent reactance remain unchanged.

Evaluation does not advance numerical state or controller history. Trial state
publication validates every coordinate before changing the model, then applies
the existing LVPL and PLL projections. An enclosing solver can restore a saved
admissible state without hidden history changes. Ordinary predictor/corrector
ownership rejects orphan correctors, repeated predictors and mismatched intervals;
reinitialization clears pending ordinary integration history.

The staggered solver binds `Wt3g2IntegrationBundle`, which owns the complete
converter, WT3E1, WT3T1 and WT3P1 state vector. Attachment identity is checked
before initialization and subsequent operations. The standalone converter API
still rejects attached controls; the bundle integrates every attached state
without calling nested ordinary predictor/corrector methods.

At each RHS boundary, the bundle refreshes physical P/Q from the current voltage
and converter state. It uses `updateAttributes(false)`, the algebraic branch;
the measurement-averaging branch is not used. All leaf derivatives therefore see
one live network/state boundary. Mechanical speed and pitch feed their dependent
controls before derivative evaluation; electrical commands refresh before the
converter RHS. A failed evaluation restores measurements and commands and does
not partly publish the derivative vector.

Complete trial projection validates all coordinates before publication and rolls
back successful leaf projections if a later leaf rejects the operation. The
mechanical leaf's explicit managed-pitch overload requires the exact pitch owner;
its standalone API continues to reject an attached unowned pitch controller.

## Validation boundary

`Wt3g2SharedStateTest` checks identical ordinary/shared modified-Euler stages,
pure derivative observations, trial restoration, atomic rejection of nonfinite
states, limiter projection, initialization and stage ownership. It also runs a
bare-converter SMIB fault under both solvers. The final five-state method
difference decreases from approximately 3.79e-6 to 9.03e-7 when the interval is
halved from 0.5 to 0.25 ms. This is an integration consistency check, not an
independent reference acceptance claim.

The existing converter import/limit tests and independent modified-Euler
trajectory gate remain passing. That older trajectory gate excludes event-adjacent
samples; it must not be represented as full event-boundary qualification.
The complete-stack consistency test advances every attached model and halves
the maximum final-state method difference from about 0.00521 to 0.00261 when the
interval is halved. Trial-voltage tests prevent a regression to stale initial
power measurements and verify rejected-boundary rollback.

The converted-interface diagnostic now runs both solvers and retains all 303
boundary samples, including both event-side duplicates. Strict independent
comparison remains a separate gate and must retain failures; completing the
network run does not establish parity. Independent full-trajectory acceptance
and production-step qualification remain open.

## Converted-interface event diagnostic

The complete converted-interface comparison remains rejected under both methods:
maximum errors are approximately 0.02949 pu terminal voltage, 5.3564 MW and
33.153 Mvar. The infinite-side voltage passes its existing gate. Modified Euler
and staggered results have nearly identical errors; changing integration order
does not explain this discrepancy. These are synthetic converted-model results,
not evidence that the original legacy models are equivalent.

The [event-format documentation](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Event_Dialog.htm)
defines the entered impedance in per unit. Both runs request a balanced fault
through `j0.2`, corresponding to an added admittance of `-j5`. For the synthetic
lossless two-bus fixture, the two parallel `j0.2` lines give `Xline=0.1`.
Using measured magnitudes and terminal power on system base,

```
sin(delta) = P * Xline / (V1 * V2)
Bfault = [Q - (V1^2 - V1*V2*cos(delta))/Xline] / V1^2
```

The small-angle branch gives inferred fault susceptance magnitudes 5.00000030
for the reference and 4.99997377 for InterPSS at fault application. This supports
matching fault-network admittance, rather than an impedance-unit discrepancy.

Conditionally using the entered converter reactance `0.33403`, reconstructed
prefault Norton currents in the infinite-bus frame are approximately
`0.649687-j2.963788` and `0.649686-j2.961183`. Immediately after fault application,
they become `0.454830-j2.442264` and `0.551226-j2.966117`, respectively. The mismatch
is already present before a positive-time integration step.

That Norton reconstruction assumes the same converter admittance in both tools;
it is not a directly recorded reference source current. The available terminal
channels do not uniquely separate a different Norton admittance from a different
converter source-current publication. Reference source current, actual stamped
converter admittance, internal voltage/current states and PLL angle at both event
sides are needed to resolve that remaining distinction. No parameter adjustment
or fitted correction has been applied.

## Bypassed-PLL terminal-angle current axes

The published [WT3G2 block diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_PDF/Generator/Machine/Machine%20Model%20WT3G2.pdf)
selects the angle of terminal voltage when both PLL gains are zero. The stored
PLL angle remains a numerical state but does not rotate the current source in
that branch. The runtime now applies this selector directly at each network
evaluation, using the live terminal phasor and a reusable scalar calculation.
Nonzero-PLL current publication follows the existing stored-angle path.

The complete converted generic WT3 SMIB shows that this selector removes the
large clearing-side active-power difference without changing model parameters
or sample alignment. A separate bypassed voltage-controller initialization
correction is required for its reactive-power gate; the
[converted comparison](legacy-ge-import-verification.md#converted-terminal-angle-and-bypass-reference-correction)
records both effects and their separate implementation boundaries. Neither
generic result establishes exact support for a legacy wind-model identifier.
